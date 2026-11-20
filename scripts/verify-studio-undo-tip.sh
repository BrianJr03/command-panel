#!/usr/bin/env bash
# Verifies #63 on the debug build: Theme Studio's Undo and Redo are icon buttons, the first time Studio opens a tip
# explains L3 / R3, A dismisses it, it never shows again, and L3 / R3 still undo and redo.
#   scripts/verify-studio-undo-tip.sh [--no-build]
#
# Debug build only (jr.brian.commandpannel.debug), on the top screen (display 0). Every key is sent only after checking
# that the debug companion is the resumed activity there (never the release app's); it never sends KEYCODE_BACK, and B
# only with nothing to discard. It changes the debug build's prefs (the tip's seen flag, a Studio draft) and puts the
# whole prefs file back at the end, then reopens the debug companion so its service runs; put the top screen back
# afterwards if something else was there.
# Checks:
#   - with the seen flag cleared, opening Studio (Themes > Mix and match) shows the tip, with focus on Got it;
#   - A dismisses it, B's release doesn't close Studio, and the seen flag is saved;
#   - the top bar has Undo and Redo buttons (by their descriptions) and no "Undo" / "Redo" text labels;
#   - after a change (a different base look), L3 undoes it (the unsaved draft goes away) and R3 redoes it;
#   - leaving and reopening Studio doesn't show the tip again.
# Screenshots go to $OUT_DIR; how the icons and the name look on each look needs a person.
set -uo pipefail
cd "$(dirname "$0")/.."

build=true
for arg in "$@"; do
  case "$arg" in
    --no-build) build=false ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-studio-undo-tip)}"
TIP_TITLE="Undo and redo"

passes=0; fails=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 1; done; return 1; }
shot() { adb exec-out screencap -d "$TOP_PHYSICAL" -p > "$OUT_DIR/$1.png" 2>/dev/null && echo "  screenshot: $OUT_DIR/$1.png"; }
dump_ui() { sh_dev uiautomator dump /sdcard/verify-tip.xml >/dev/null; adb pull /sdcard/verify-tip.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
ui_has() { dump_ui; grep -q "text=\"$1\"\|content-desc=\"$1\"" "$OUT_DIR/ui.xml"; }
ui_has_text() { dump_ui; grep -q "text=\"$1\"" "$OUT_DIR/ui.xml"; }
ui_has_desc() { dump_ui; grep -q "content-desc=\"$1\"" "$OUT_DIR/ui.xml"; }
ui_lacks() { ! ui_has "$1"; }
# Text and content descriptions inside the focused node (Compose reports focus on the focusable control).
focused_text() {
  dump_ui
  python3 -I - "$OUT_DIR/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("focused") == "true":
        print(" | ".join(p for p in (d.get("text") or d.get("content-desc") for d in n.iter("node")) if p))
        break
PY
}
focus_has() { focused_text | grep -q "$1"; }
pref_bool() { sh_dev run-as "$PKG" cat "$PREFS" | grep "name=\"$1\"" | grep -o 'value="[^"]*"' | cut -d'"' -f2; }
has_draft() { sh_dev run-as "$PKG" cat "$PREFS" | grep -q 'name="studio_draft"'; }
no_draft() { ! has_draft; }
service_up() { sh_dev dumpsys activity services "$PKG" | grep -q AssistantService; }

# The debug companion is the resumed activity on the top screen (display 0).
companion_in_front() {
  local d; d="$(sh_dev dumpsys activity activities)"
  echo "$d" | awk '/^Display #/{d=$2} d == "#0" && /topResumedActivity=/ && !p {print; p=1}' | grep -q "$PKG/"
}
# One key on the top screen, only while the debug companion has focus there.
key() {
  if ! companion_in_front; then fail "the debug companion isn't in front on the top screen: no key sent, stopping"; exit 1; fi
  sh_dev input -d 0 keyevent "$1" >/dev/null; sleep 0.7
}
# Taps the center of the first node whose text is exactly $1 (a tap, not a key: used for the companion's own
# What's new dialog and tour, which a fresh debug install opens with).
tap_text() {
  companion_in_front || return 1
  dump_ui
  local b
  b="$(grep -oE "<node [^>]*text=\"$1\"[^>]*>" "$OUT_DIR/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  [ -n "$b" ] || return 1
  set -- $b
  sh_dev input -d 0 tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
  sleep 1
}
# From the Themes section's rail item into the pane, then down to Mix and match, and A: a new look in Studio.
open_studio() {
  sh_dev am start -W -n "$COMPANION" --display 0 --es section themes >/dev/null
  sleep 2
  # What's new or the companion tour first (only before Studio is open, so this "Got it" isn't the tip's).
  local j
  for j in 1 2 3; do
    if ui_has "Skip tour"; then tap_text "Skip tour"
    elif ui_has_text "Got it" && ! ui_has "$TIP_TITLE"; then tap_text "Got it"
    else break; fi
  done
  key KEYCODE_DPAD_RIGHT
  local i
  for i in $(seq 1 12); do
    if focus_has "Mix and match"; then key KEYCODE_BUTTON_A; sleep 1.5; return 0; fi
    key KEYCODE_DPAD_DOWN
  done
  return 1
}

# --- setup ----------------------------------------------------------------------------------------------------------

step "Device"
adb get-state >/dev/null 2>&1 || { echo "No device connected." >&2; exit 1; }
echo "  output: $OUT_DIR"
if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; exit 1; }; fi
check "debug app installed" sh -c "adb install -r '$APP_APK' >/dev/null"
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"

saved_prefs="$OUT_DIR/prefs-before.xml"
sh_dev run-as "$PKG" cat "$PREFS" > "$saved_prefs"
restore_prefs() {
  step "Restore"
  sh_dev am force-stop "$PKG"
  adb push "$saved_prefs" /data/local/tmp/verify-tip-prefs.xml >/dev/null 2>&1
  sh_dev run-as "$PKG" cp /data/local/tmp/verify-tip-prefs.xml "$PREFS"
  sh_dev rm /data/local/tmp/verify-tip-prefs.xml
  # The companion starts the service again.
  sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null
  wait_for 15 service_up && echo "  prefs restored, service running"
  echo; echo "$passes passed, $fails failed (output: $OUT_DIR)"
}
trap restore_prefs EXIT

# A first run of Studio: no seen flag, no leftover draft (with the app stopped, so the edit sticks).
sh_dev am force-stop "$PKG"
sh_dev "run-as $PKG sed -i -e '/name=\"studio_undo_tip_seen\"/d' -e '/name=\"studio_draft\"/d' $PREFS"
check "seen flag cleared" test -z "$(pref_bool studio_undo_tip_seen)"

# --- the tip --------------------------------------------------------------------------------------------------------

step "First open shows the tip"
check "Studio opened from Mix and match" open_studio
check "the tip shows" wait_for 3 'ui_has "$TIP_TITLE"'
shot tip
check "focus is on Got it" focus_has "Got it"
key KEYCODE_BUTTON_A
check "A dismissed it" wait_for 3 'ui_lacks "$TIP_TITLE"'
check "the seen flag is saved" test "$(pref_bool studio_undo_tip_seen)" = true
check "Studio is still open (the Undo button is there)" ui_has_desc "Undo"

step "Icon buttons"
shot studio-bar
dump_ui
check "Undo and Redo carry descriptions for screen readers" sh -c "grep -q 'content-desc=\"Undo\"' '$OUT_DIR/ui.xml' && grep -q 'content-desc=\"Redo\"' '$OUT_DIR/ui.xml'"

# A labeled chip would show "Undo" as text; the icons only carry it as a description.
check "no Undo / Redo text labels in the bar" sh -c "! grep -qE 'text=\"(Undo|Redo)\"' '$OUT_DIR/ui.xml'"

step "L3 undoes, R3 redoes"
# Category bar, then down into Start from (focus lands on the picked look), right to another look, A.
key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_RIGHT
key KEYCODE_BUTTON_A
check "a change makes an unsaved draft" wait_for 4 has_draft
key KEYCODE_BUTTON_THUMBL
check "L3 undid it (no draft left)" wait_for 4 no_draft
key KEYCODE_BUTTON_THUMBR
check "R3 redid it (the draft is back)" wait_for 4 has_draft
key KEYCODE_BUTTON_THUMBL
check "L3 again: back to no changes, so B closes without asking" wait_for 4 no_draft

step "Never again"
key KEYCODE_BUTTON_B
check "B closed Studio (no Undo button)" wait_for 3 '! ui_has_desc "Undo"'
check "Studio opened again" open_studio
sleep 1
check "the tip doesn't show the second time" ui_lacks "$TIP_TITLE"
shot studio-again
key KEYCODE_BUTTON_B
