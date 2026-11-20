#!/usr/bin/env bash
# Verifies #59: the Fan dialog's Custom mode has a speed slider, and the options per performance mode match stock.
#   scripts/verify-fan-slider.sh [static|device|all] [--no-build] [--no-install]
#
# static  Compiles and runs the unit tests (FanControlTest covers the option filter, the fallback and the mapping).
# device  Debug build only (jr.brian.commandpannel.debug). The panel window never takes focus, so there are no key
#         events here: it sets performance through the debug-only DEBUG_SET_PERFORMANCE broadcast (the Performance
#         dialog's own path), opens the Fan dialog with DEBUG_OPEN_DIALOG, and taps the bounds the debug build logs for
#         each option and the slider (tag OverlayDebug). Before every tap it checks that the debug panel is the one on
#         the bottom display and that the release panel isn't showing there.
#           - performance 0: Off, Quiet, Smart, Sport; no Custom
#           - performance 1: Quiet, Smart, Sport; no Off, no Custom
#           - performance 2: Smart, Sport, Custom; picking Custom keeps the dialog open and shows the slider
#           - taps at 10 % and 90 % write the matching fan_speed; a slow drag writes nothing until it's released
#           - lowering performance while Custom is set leaves a mode that level offers
#         With FAN_DUTY_CMD set (a shell command that prints the live FanProvider duty, e.g. Mario's app_process
#         reader), it also records the duty at the low and high end. Screenshots of the bottom screen go to OUT_DIR.
#         On exit it hides the panel and restores performance_mode first (the firmware resets fan_mode when that's
#         written), then fan_mode and fan_speed, and reads them back.
set -uo pipefail
cd "$(dirname "$0")/.."

stage="all"; build=true; install=true
for arg in "$@"; do
  case "$arg" in
    static|device|all) stage="$arg" ;;
    --no-build) build=false ;;
    --no-install) install=false; build=false ;;   # use the debug build already on the device
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
RELEASE_PKG="jr.brian.commandpannel"
ACTION_PREFIX="jr.brian.commandpannel.action"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-fan-slider)}"
RANGE_LOW=25000; RANGE_HIGH=50000   # what a Thor reports; the app reads it from FanProvider

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

# --- static ---------------------------------------------------------------------------------------------------------

static_checks() {
  step "Build and unit tests"
  check ":app:compileDebugKotlin :app:testDebugUnitTest" ./gradlew -q :app:compileDebugKotlin :app:testDebugUnitTest
}

# --- device ---------------------------------------------------------------------------------------------------------

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
device_now() { sh_dev date "'+%m-%d %H:%M:%S.000'"; }
sys_get() { sh_dev settings get system "$1"; }
broadcast() { sh_dev am broadcast -a "$ACTION_PREFIX.$1" -p "$PKG" "${@:2}" >/dev/null; }
service_running() { sh_dev dumpsys activity services "$PKG" | grep -q "ServiceRecord.*$PKG.*AssistantService"; }
# wait_for <tries> <command string>: polls every 0.25 s until the command succeeds.
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 0.25; done; return 1; }

# SYSTEM_ALERT_WINDOW windows with a surface on display $1 whose "package=" is $2.
windows_on() {
  sh_dev dumpsys window windows | awk -v d="mDisplayId=$1" -v p="package=$2" '
    function flush() { if (inblk && disp && pkg && surf && saw) n++ }
    /^  Window #/ { flush(); inblk = 1; disp = pkg = surf = saw = 0; next }
    inblk && index($0, d " ") { disp = 1 }
    inblk && index($0, p " ") { pkg = 1 }
    inblk && /appop=SYSTEM_ALERT_WINDOW/ { saw = 1 }
    inblk && /mHasSurface=true/ { surf = 1 }
    END { flush(); print n + 0 }'
}

# The latest logged bounds of $1 ("option_6", "fan_slider") since time $2, as "l t r b", or empty.
bounds_of() {
  adb logcat -d -v brief -T "$2" -s OverlayDebug:D 2>/dev/null | tr -d '\r' |
    grep -o "bounds $1=[0-9-]*,[0-9-]*,[0-9-]*,[0-9-]*" | tail -1 | sed -E 's/.*=//; s/,/ /g'
}
has_bounds() { [ -n "$(bounds_of "$1" "$2")" ]; }

# The tap guard: the debug panel is on the bottom display and the release panel isn't. Aborts the run otherwise.
guard() {
  local ours theirs
  ours="$(windows_on "$BOTTOM" "$PKG")"; theirs="$(windows_on "$BOTTOM" "$RELEASE_PKG")"
  if [ "$ours" -lt 1 ] || [ "$theirs" -ne 0 ]; then
    fail "tap guard: debug panel windows $ours, release panel windows $theirs on display $BOTTOM; stopping"
    exit 1
  fi
}

# tap_at <name> <since> <x fraction 0..1>: taps inside a logged node, at that fraction of its width, mid height.
tap_at() {
  local b; b=($(bounds_of "$1" "$2"))
  [ "${#b[@]}" = 4 ] || { fail "no bounds logged for $1"; return 1; }
  local x y
  x="$(awk -v l="${b[0]}" -v r="${b[2]}" -v f="$3" 'BEGIN { printf "%d", l + (r - l) * f }')"
  y=$(( (b[1] + b[3]) / 2 ))
  guard
  sh_dev input -d "$BOTTOM" tap "$x" "$y"
}

snap() { adb exec-out screencap -d "$BOTTOM_PHYSICAL" -p > "$OUT_DIR/$1.png" 2>/dev/null; }

# open_fan_dialog: shows the panel with the Fan dialog open; sets T to the time just before, for bounds_of.
open_fan_dialog() {
  broadcast HIDE_OVERLAY; sleep 0.5
  T="$(device_now)"
  broadcast DEBUG_OPEN_DIALOG --es tile fan
  wait_for 24 '[ "$(windows_on "$BOTTOM" "$PKG")" -ge 1 ]' || fail "panel didn't show"
  wait_for 24 'has_bounds option_4 "$T"' || { fail "Fan dialog didn't open (no option bounds logged); stopping"; exit 1; }
  # The dialog lays out more than once while the panel comes up (and again when the firmware resets fan_mode), so
  # wait until Smart's row has stayed put for a second before anything taps it.
  local prev="" cur i
  for i in $(seq 1 20); do
    cur="$(bounds_of option_4 "$T")"
    [ "$cur" = "$prev" ] && break
    prev="$cur"; sleep 1
  done
}

# expect_options <perf> <shown...> -- <hidden...>
expect_options() {
  local perf="$1" o; shift
  local shown=() hidden=() into=shown
  for o in "$@"; do if [ "$o" = "--" ]; then into=hidden; elif [ $into = shown ]; then shown+=("$o"); else hidden+=("$o"); fi; done
  for o in "${shown[@]}"; do check "performance $perf shows fan mode $o" has_bounds "option_$o" "$T"; done
  for o in "${hidden[@]}"; do check "performance $perf hides fan mode $o" sh -c "! adb logcat -d -v brief -T '$T' -s OverlayDebug:D | grep -q 'bounds option_$o='"; done
}

set_performance() {
  WANT_PERF="$1"
  broadcast DEBUG_SET_PERFORMANCE --ei mode "$1"
  wait_for 12 '[ "$(sys_get performance_mode)" = "$WANT_PERF" ]' || fail "performance_mode didn't become $1"
}

near() { local d=$(( $1 - $2 )); [ "${d#-}" -le "$3" ]; }

duty() { if [ -n "${FAN_DUTY_CMD:-}" ]; then eval "$FAN_DUTY_CMD"; fi; }

ORIG_PERF=""; ORIG_FAN=""; ORIG_SPEED=""
restore() {
  [ -n "$ORIG_PERF" ] || return
  step "Restoring"
  broadcast HIDE_OVERLAY
  # performance_mode first: writing it makes the firmware reset fan_mode, so fan_mode and fan_speed go after.
  sh_dev settings put system performance_mode "$ORIG_PERF"
  sleep 3
  sh_dev settings put system fan_mode "$ORIG_FAN"
  sh_dev settings put system fan_speed "$ORIG_SPEED"
  sleep 1
  local p f s; p="$(sys_get performance_mode)"; f="$(sys_get fan_mode)"; s="$(sys_get fan_speed)"
  echo "  now performance_mode=$p fan_mode=$f fan_speed=$s (was $ORIG_PERF / $ORIG_FAN / $ORIG_SPEED)"
  if [ "$p/$f/$s" = "$ORIG_PERF/$ORIG_FAN/$ORIG_SPEED" ]; then pass "settings restored"
  else fail "settings not fully restored; set them back by hand"; fi
  ORIG_PERF=""
}
trap restore EXIT

device_checks() {
  step "Device"
  if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; return; fi
  local ids
  ids="$(sh_dev dumpsys display | grep -oE "type=EXTERNAL, valid=true, isActive=true, displayId=[0-9]+, uniqueId='local:[0-9]+'" | head -1)"
  BOTTOM="$(echo "$ids" | sed -E 's/.*displayId=([0-9]+).*/\1/')"
  BOTTOM_PHYSICAL="$(echo "$ids" | sed -E "s/.*local:([0-9]+)'/\1/")"
  [ -n "$BOTTOM" ] || { fail "couldn't find the bottom display"; return; }
  mkdir -p "$OUT_DIR"
  echo "  bottom display $BOTTOM (physical $BOTTOM_PHYSICAL), screenshots: $OUT_DIR"

  step "Install the debug build"
  if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; return; }; fi
  if $install; then adb install -r "$APP_APK" >/dev/null; fi
  # A reinstall doesn't always bring the service back. Opening the debug companion starts it; then whatever had the top
  # screen is brought back.
  if ! wait_for 20 service_running; then
    local top_app
    top_app="$(sh_dev dumpsys window displays | awk '/Display: mDisplayId=/{d=$2} d == "mDisplayId=0" && /mFocusedApp=/{sub(/}$/, "", $3); print $3; exit}')"
    sh_dev am start -W --display 0 -n "$PKG/jr.brian.commandpannel.MainActivity" >/dev/null
    wait_for 60 service_running
    [ -n "$top_app" ] && sh_dev am start -W --display 0 -n "$top_app" >/dev/null && echo "  top screen handed back to $top_app"
  fi
  if service_running; then pass "debug app installed and its service running"
  else fail "debug service isn't running"; return; fi
  # Never touches the release build: if its panel is up, wait a while for it to close, then give up.
  if ! wait_for 120 '[ "$(windows_on "$BOTTOM" "$RELEASE_PKG")" -eq 0 ]'; then
    fail "the release panel is open on the bottom screen; close it and run again"; return
  fi

  ORIG_PERF="$(sys_get performance_mode)"; ORIG_FAN="$(sys_get fan_mode)"; ORIG_SPEED="$(sys_get fan_speed)"
  echo "  saved performance_mode=$ORIG_PERF fan_mode=$ORIG_FAN fan_speed=$ORIG_SPEED"

  step "Performance 0"
  set_performance 0; open_fan_dialog; snap perf0
  expect_options 0 0 1 4 5 -- 6

  step "Performance 1"
  set_performance 1; open_fan_dialog; snap perf1
  expect_options 1 1 4 5 -- 0 6

  step "Performance 2: Custom and its slider"
  set_performance 2; open_fan_dialog; snap perf2
  expect_options 2 4 5 6 -- 0 1
  local t; t="$T"
  tap_at option_6 "$t" 0.3
  check "Custom selected (fan_mode 6)" wait_for 12 '[ "$(sys_get fan_mode)" = 6 ]'
  if wait_for 12 'has_bounds fan_slider "$t"'; then pass "dialog stays open with the slider"
  else fail "dialog stays open with the slider"; return; fi
  snap custom-open

  # The logged bounds are the whole slider row; its track starts after the 44 dp label and 8 dp gap. row_fraction
  # turns a position on the track into a fraction of the row, for tap_at.
  local b; b=($(bounds_of fan_slider "$t"))
  local density; density="$(sh_dev wm density -d "$BOTTOM" | grep -oE '[0-9]+' | tail -1)"
  [ -n "$density" ] || density="$(sh_dev wm density | grep -oE '[0-9]+' | tail -1)"
  local track_start=$(( b[0] + 52 * density / 160 ))
  row_fraction() { awk -v l="${b[0]}" -v r="${b[2]}" -v s="$track_start" -v f="$1" 'BEGIN { printf "%.4f", (s + (r - s) * f - l) / (r - l) }'; }

  tap_at fan_slider "$t" "$(row_fraction 0.1)"; sleep 1
  local low; low="$(sys_get fan_speed)"
  check "tap at 10 % writes about $(( RANGE_LOW + (RANGE_HIGH - RANGE_LOW) / 10 )) (got $low)" near "$low" $(( RANGE_LOW + (RANGE_HIGH - RANGE_LOW) / 10 )) 1500
  snap slider-low
  if [ -n "${FAN_DUTY_CMD:-}" ]; then sleep 4; echo "  duty at fan_speed $low: $(duty)"; else skip "duty at the low end (set FAN_DUTY_CMD)"; fi

  tap_at fan_slider "$t" "$(row_fraction 0.9)"; sleep 1
  local high; high="$(sys_get fan_speed)"
  check "tap at 90 % writes about $(( RANGE_LOW + (RANGE_HIGH - RANGE_LOW) * 9 / 10 )) (got $high)" near "$high" $(( RANGE_LOW + (RANGE_HIGH - RANGE_LOW) * 9 / 10 )) 1500
  snap slider-high
  if [ -n "${FAN_DUTY_CMD:-}" ]; then sleep 4; echo "  duty at fan_speed $high: $(duty)"; else skip "duty at the high end (set FAN_DUTY_CMD)"; fi

  # A slow drag from 90 % down to 30 %: fan_speed must not move until it's released.
  local y=$(( (b[1] + b[3]) / 2 ))
  local x1 x2
  x1="$(awk -v s="$track_start" -v r="${b[2]}" 'BEGIN { printf "%d", s + (r - s) * 0.9 }')"
  x2="$(awk -v s="$track_start" -v r="${b[2]}" 'BEGIN { printf "%d", s + (r - s) * 0.3 }')"
  guard
  adb shell input -d "$BOTTOM" swipe "$x1" "$y" "$x2" "$y" 3000 &
  local swipe=$!
  sleep 1.5
  local mid; mid="$(sys_get fan_speed)"
  check "no write mid-drag (fan_speed still $high, got $mid)" test "$mid" = "$high"
  wait "$swipe"; sleep 1
  local after; after="$(sys_get fan_speed)"
  check "release writes about $(( RANGE_LOW + (RANGE_HIGH - RANGE_LOW) * 3 / 10 )) (got $after)" near "$after" $(( RANGE_LOW + (RANGE_HIGH - RANGE_LOW) * 3 / 10 )) 2000
  snap slider-dragged

  step "Lowering performance while Custom is set"
  broadcast HIDE_OVERLAY
  set_performance 1; sleep 3
  local f1; f1="$(sys_get fan_mode)"
  check "performance 1 leaves a mode it offers (fan_mode $f1)" sh -c "echo ' 1 4 5 ' | grep -q ' $f1 '"
  sh_dev settings put system performance_mode 2; sleep 3
  sh_dev settings put system fan_mode 6; sleep 1
  set_performance 0; sleep 3
  local f0; f0="$(sys_get fan_mode)"
  check "performance 0 leaves a mode it offers (fan_mode $f0)" sh -c "echo ' 0 1 4 5 ' | grep -q ' $f0 '"
  open_fan_dialog; snap perf0-after-custom
  broadcast HIDE_OVERLAY

  restore
}

case "$stage" in
  static) static_checks ;;
  device) device_checks ;;
  all)    static_checks; device_checks ;;
esac

echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
