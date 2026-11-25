#!/usr/bin/env bash
# Verifies the shared fonts row and the font file gate (#89).
#   scripts/verify-shared-fonts.sh [--release]
#
# Default: the debug build (jr.brian.commandpannel.debug), which can switch looks through the DEBUG_BACKGROUND
# broadcast (`--es look`, system/BackgroundImageDebug.kt). --release only reads the release build's row and checks the
# gate, since a release build has no debug broadcast and its look is left alone.
#
# Checks:
#   - /palette still answers with version 1 and its 16 columns;
#   - /fonts answers with version 2 and the seven contract columns, look_name included;
#   - switching looks changes the row: SEGMENT_LCD gives bundled:segment / bundled:mono, E_INK bundled:serif twice,
#     AURORA_GLASS null fonts;
#   - an applied saved look with an imported font shows user:<id> and a 64-char sha256 (only if one is applied);
#   - the shell (an untrusted caller) can't read an imported font file, and traversal paths are refused.
# Look switching needs AssistantService running (the script opens the companion if it isn't). The user's look comes
# back at the end. Whether Jarn FE draws the right font needs a person.
set -uo pipefail

PKG="jr.brian.commandpannel.debug"
[ "${1:-}" = "--release" ] && PKG="jr.brian.commandpannel"
AUTH="$PKG.theme"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
ACTION="jr.brian.commandpannel.action.DEBUG_BACKGROUND"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }

sh_dev() { adb shell "$@" 2>&1 | tr -d '\r'; }
query() { sh_dev content query --uri "content://$AUTH/$1"; }
# The value of column $2 in `content query` output $1 ("Row: 0 a=1, b=NULL, ...").
col() { echo "$1" | grep -oE "(^|, | )$2=[^,]*" | head -1 | sed -E "s/^(, | )?$2=//"; }
expect_col() {
  local row="$1" name="$2" want="$3" got
  got="$(col "$row" "$name")"
  if [ "$got" = "$want" ]; then pass "$name=$want"; else fail "$name: want $want, got '${got}'"; fi
}
current_look() { sh_dev run-as "$PKG" cat "$PREFS" | grep 'name="overlay_look"' | sed -E 's/.*>([^<]*)<.*/\1/'; }
set_look() { sh_dev am broadcast -a "$ACTION" -p "$PKG" --es look "$1" >/dev/null; sleep 1.5; }

[ "$(adb devices | grep -c 'device$')" -eq 1 ] || { echo "need exactly one adb device (set ANDROID_SERIAL)" >&2; exit 2; }
sh_dev pm path "$PKG" | grep -q package: || { echo "$PKG isn't installed" >&2; exit 2; }

step "palette (version 1) is unchanged"
palette="$(query palette)"
expect_col "$palette" version 1
cols="$(echo "$palette" | grep -oE '[a-z_]+=' | wc -l | tr -d ' ')"
[ "$cols" = 16 ] && pass "16 palette columns" || fail "palette has $cols columns"

step "fonts row (version 2)"
fonts="$(query fonts)"
echo "  info  $fonts"
expect_col "$fonts" version 2
for c in look_id look_name display_font display_font_sha256 label_font label_font_sha256; do
  echo "$fonts" | grep -qE "(^| )$c=" && pass "has $c" || fail "missing $c"
done

look="$(col "$fonts" look_id)"
case "$(col "$fonts" label_font)" in
  user:*)
    sha="$(col "$fonts" label_font_sha256)"
    [[ "$sha" =~ ^[0-9a-f]{64}$ ]] && pass "imported label font has a sha256" || fail "bad sha256 '$sha'"
    id="$(col "$fonts" label_font)"; id="${id#user:}"
    step "an untrusted caller can't read the font file"
    out="$(sh_dev content read --uri "content://$AUTH/fonts/$id")"
    echo "$out" | grep -qi "SecurityException\|not allowed" && pass "shell refused" || fail "shell read: ${out:0:120}"
    ;;
  *) skip "no imported label font applied (apply a saved look with an imported font to cover user: fonts)" ;;
esac

step "traversal paths are refused"
for p in "fonts/..%2Fshared_prefs%2Fcommand_panel.xml" "fonts/../shared_prefs/command_panel.xml" "fonts/x.ttf"; do
  out="$(sh_dev content read --uri "content://$AUTH/$p")"
  echo "$out" | grep -qiE "FileNotFound|Exception|Error" && pass "refused $p" || fail "$p returned: ${out:0:120}"
done

if [ "$PKG" = "jr.brian.commandpannel.debug" ]; then
  step "switching looks changes the row"
  # The debug broadcast is handled by AssistantService, so make sure it's up.
  if ! sh_dev dumpsys activity services "$PKG" | grep -q AssistantService; then
    sh_dev am start -W -n "$PKG/jr.brian.commandpannel.MainActivity" --display 0 >/dev/null; sleep 3
  fi
  original="$(current_look)"
  set_look SEGMENT_LCD
  row="$(query fonts)"
  expect_col "$row" look_id SEGMENT_LCD
  expect_col "$row" look_name "Segment LCD"
  expect_col "$row" display_font bundled:segment
  expect_col "$row" label_font bundled:mono
  set_look E_INK
  row="$(query fonts)"
  expect_col "$row" display_font bundled:serif
  expect_col "$row" label_font bundled:serif
  set_look AURORA_GLASS
  row="$(query fonts)"
  expect_col "$row" display_font NULL
  expect_col "$row" label_font NULL
  set_look "${original:-$look}"
  [ "$(col "$(query fonts)" look_id)" = "$look" ] && pass "look restored ($look)" || fail "look not restored"
else
  skip "look switching (release build has no debug broadcast)"
fi

echo
echo "passed $passes, failed $fails, skipped $skips"
[ "$fails" -eq 0 ]
