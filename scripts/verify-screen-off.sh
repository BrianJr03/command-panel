#!/usr/bin/env bash
# Verifies #35: screen off is a black cover of Command Panel's own, with the vendor's display mode left at 0 (the
# firmware force-dims app windows on the screen its mode marks "off", so no cover could be black there).
#   scripts/verify-screen-off.sh [--no-build]
#
# Debug build only (jr.brian.commandpannel.debug), and only while it owns the AYN button. It presses the real button
# (KEY_F24 through sendevent; the firmware's broadcast is protected) and picks "only top" / "only bottom" through the
# debug-only DEBUG_SELECT_MODE broadcast, which takes the Mode tab's own path. The screens must be in dual when it
# starts; on exit it goes back to dual (and says so if a cover is left).
#
# For each case (long press, only top screen, only bottom screen):
#   - the vendor mode stays 0, and our blank state says which screen is covered;
#   - the cover window (CommandPanelScreenOff) is on the right display with no forced alpha (no 0.8);
#   - a screencap of that display is black: mean RGB about 0 (the top screen's status bar is measured on its own);
#   - focus lock points at the visible screen;
#   - a tap on the covered screen changes nothing (the focused app there stays the same);
#   - a press brings it back: cover gone, vendor mode 0, focus lock restored, frame not black.
# Then: a service restart while covered (the cover comes back), and a sleep/wake (KEYCODE_SLEEP / KEYCODE_WAKEUP,
# never the power key) while covered (it stays).
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
ACTION_SELECT_MODE="jr.brian.commandpannel.action.DEBUG_SELECT_MODE"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
COVER_TITLE="CommandPanelScreenOff"
FFMPEG="${FFMPEG:-/opt/homebrew/bin/ffmpeg}"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-screen-off)}"
# The top screen's status bar height in pixels, measured on its own in the top screen's frame.
STATUS_BAR_PX=55

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 1; done; return 1; }
vendor_mode() { sh_dev settings get system dual_screen_display_mode; }
focus_lock() { sh_dev settings get system screen_focus_lock; }
pref() { sh_dev run-as "$PKG" cat "$PREFS" | grep "name=\"$1\"" | grep -o 'value="[^"]*"' | cut -d'"' -f2; }
blank_state() { local v; v="$(pref screen_blank)"; echo "${v:-0}"; }
service_up() { sh_dev dumpsys activity services "$PKG" | grep -q AssistantService; }
start_service() { sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null; wait_for 15 service_up; }

# The real AYN button: KEY_F24 (194) on gpio-keys; a hold of about 400 ms counts as long.
AYN_DEV="/dev/input/event0"; AYN_CODE=194
button() { sh_dev "sendevent $AYN_DEV 1 $AYN_CODE 1; sendevent $AYN_DEV 0 0 0; sleep $1; sendevent $AYN_DEV 1 $AYN_CODE 0; sendevent $AYN_DEV 0 0 0" >/dev/null; }
long_press() { button 1; }
press() { button 0.1; }
select_mode() { sh_dev am broadcast -a "$ACTION_SELECT_MODE" -p "$PKG" --ei mode "$1" >/dev/null; }

TOP_ID=0; BOTTOM_ID=""; TOP_PHYSICAL=""; BOTTOM_PHYSICAL=""
display_of() { if [ "$1" = top ]; then echo "$TOP_ID"; else echo "$BOTTOM_ID"; fi; }
physical_of() { if [ "$1" = top ]; then echo "$TOP_PHYSICAL"; else echo "$BOTTOM_PHYSICAL"; fi; }

# The cover window's block in `dumpsys window windows` (empty when there's none).
cover_block() { sh_dev dumpsys window windows | awk -v t="$COVER_TITLE" '/^  Window #/ { show = index($0, t) > 0 } show'; }
cover_on() { cover_block | grep -q "mDisplayId=$(display_of "$1")"; }
cover_gone() { [ -z "$(cover_block)" ]; }
cover_not_dimmed() { local b; b="$(cover_block)"; [ -n "$b" ] && ! echo "$b" | grep -qE "alpha=0\.|mShownAlpha=0\."; }

# Screencap of a screen to $OUT_DIR/<name>.png; mean R G B of the whole frame, or of a crop ("w:h:x:y").
shot() { adb exec-out screencap -d "$(physical_of "$1")" -p > "$OUT_DIR/$2.png" 2>/dev/null; }
mean_rgb() {
  local crop="${2:+crop=$2,}"
  "$FFMPEG" -v error -i "$1" -vf "${crop}scale=1:1:flags=area" -f rawvideo -pix_fmt rgb24 - | od -An -tu1 | xargs
}
is_black() { local r g b; read -r r g b <<< "$1"; [ "$r" -le 2 ] && [ "$g" -le 2 ] && [ "$b" -le 2 ]; }
not_black() { ! is_black "$1"; }

# The focused app on a display (from `dumpsys window displays`).
focused_on() {
  sh_dev dumpsys window displays | awk -v d="mDisplayId=$1" '/Display: mDisplayId=/ { cur = $2 } cur == d && /mFocusedApp=/ { print $3; exit }'
}

# Covers [screen] with [how], checks it, taps it, then brings it back with a press.
case_check() {
  local name="$1" screen="$2" how="$3" expect_blank="$4" expect_focus="$5"
  step "$name"
  local focus_before; focus_before="$(focus_lock)"
  case "$how" in
    long) long_press ;;
    *) select_mode "$how" ;;
  esac
  check "cover up on the $screen screen" wait_for 5 "cover_on $screen"
  sleep 1
  check "vendor mode stays 0" test "$(vendor_mode)" = 0
  check "blank state is $expect_blank" test "$(blank_state)" = "$expect_blank"
  check "cover not dimmed (no forced alpha)" cover_not_dimmed
  check "focus lock on the visible screen ($expect_focus)" test "$(focus_lock)" = "$expect_focus"
  shot "$screen" "$name-covered"
  local rgb; rgb="$(mean_rgb "$OUT_DIR/$name-covered.png")"
  check "$screen frame is black (mean RGB $rgb)" is_black "$rgb"
  if [ "$screen" = top ]; then
    local w; w="$(sh_dev wm size -d "$TOP_ID" | grep -oE '[0-9]+x[0-9]+' | tail -1 | cut -dx -f1)"
    local bar rest
    bar="$(mean_rgb "$OUT_DIR/$name-covered.png" "${w:-1920}:$STATUS_BAR_PX:0:0")"
    rest="$(mean_rgb "$OUT_DIR/$name-covered.png" "${w:-1920}:ih-$STATUS_BAR_PX:0:$STATUS_BAR_PX")"
    echo "  status bar strip mean RGB: $bar; below it: $rest"
    if is_black "$bar"; then pass "status bar covered too"; else skip "status bar shows through the cover (mean $bar): reported, not hidden"; fi
    check "top frame below the status bar is black (mean RGB $rest)" is_black "$rest"
  fi
  local app_before; app_before="$(focused_on "$(display_of "$screen")")"
  sh_dev input -d "$(display_of "$screen")" tap 300 400 >/dev/null
  sleep 1
  check "tap on the covered screen changed nothing ($app_before)" test "$(focused_on "$(display_of "$screen")")" = "$app_before"
  check "cover still up after the tap" cover_on "$screen"
  echo "  screenshot: $OUT_DIR/$name-covered.png"

  press
  check "a press brings it back (cover gone)" wait_for 5 cover_gone
  sleep 1
  check "blank state back to 0" test "$(blank_state)" = 0
  check "vendor mode still 0" test "$(vendor_mode)" = 0
  check "focus lock restored ($focus_before)" test "$(focus_lock)" = "$focus_before"
  shot "$screen" "$name-back"
  rgb="$(mean_rgb "$OUT_DIR/$name-back.png")"
  check "$screen frame shows content again (mean RGB $rgb)" not_black "$rgb"
}

cleanup() {
  service_up || start_service
  if [ "$(blank_state)" != 0 ]; then select_mode 0; sleep 2; fi
  cover_gone || echo "  WARNING: a cover is still up"
  echo "  final: vendor mode $(vendor_mode), blank state $(blank_state), focus lock $(focus_lock)"
}

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; echo; echo "$passes passed, $fails failed, $skips skipped"; exit 1; fi
echo "  output: $OUT_DIR"
if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; exit 1; }; fi
check "debug app installed" sh -c "adb install -r '$APP_APK' >/dev/null"
check "service running" start_service
check "debug build owns the AYN button" test "$(sh_dev settings get global command_panel_stock_owner)" = "$PKG"
sh_dev input -d 0 keyevent KEYCODE_HOME >/dev/null
bottom="$(sh_dev dumpsys display | grep -oE "type=EXTERNAL, valid=true, isActive=true, displayId=[0-9]+, uniqueId='local:[0-9]+'" | head -1)"
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
BOTTOM_ID="$(echo "$bottom" | grep -oE 'displayId=[0-9]+' | cut -d= -f2)"
BOTTOM_PHYSICAL="$(echo "$bottom" | grep -oE "local:[0-9]+" | cut -d: -f2)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"
echo "  top: display $TOP_ID ($TOP_PHYSICAL), bottom: display $BOTTOM_ID ($BOTTOM_PHYSICAL)"
if [ "$(vendor_mode)" != 0 ] || [ "$(blank_state)" != 0 ]; then
  fail "not in dual (vendor mode $(vendor_mode), blank state $(blank_state)): stopping"; exit 1
fi
trap cleanup EXIT

case_check "long-press" bottom long 1 1
case_check "only-top" bottom 1 1 1
case_check "only-bottom" top 2 2 2

step "Service restart while covered"
long_press
check "cover up" wait_for 5 "cover_on bottom"
sh_dev am force-stop "$PKG"
check "service stopped, cover gone with it" wait_for 5 "! service_up && cover_gone"
check "service started again" start_service
sh_dev input -d 0 keyevent KEYCODE_HOME >/dev/null
check "cover back after the restart" wait_for 5 "cover_on bottom"
check "vendor mode still 0" test "$(vendor_mode)" = 0
shot bottom restart-covered
rgb="$(mean_rgb "$OUT_DIR/restart-covered.png")"
check "bottom frame black after the restart (mean RGB $rgb)" is_black "$rgb"

step "Sleep and wake while covered"
sh_dev input keyevent KEYCODE_SLEEP >/dev/null
sleep 3
sh_dev input keyevent KEYCODE_WAKEUP >/dev/null
sleep 3
check "cover still up after the wake" cover_on bottom
shot bottom wake-covered
rgb="$(mean_rgb "$OUT_DIR/wake-covered.png")"
check "bottom frame black after the wake (mean RGB $rgb)" is_black "$rgb"
press
check "a press brings it back" wait_for 5 cover_gone

echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
