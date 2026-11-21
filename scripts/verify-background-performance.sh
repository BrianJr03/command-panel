#!/usr/bin/env bash
# Verifies the Performance details under Experimental's Animated backgrounds switch (#80) on the debug build, driven
# with the gamepad's D-pad on the top screen.
#   scripts/verify-background-performance.sh [--no-build]
#
# Opens the companion on Experimental, walks D-pad Down to the Performance row, checks it says it's hidden, presses A
# and checks: focus stays on the row and it now says it's shown; the chart's description reads the title and the three
# values (Still image 3%, Moving gradient 12%, Animated GIF 13%); the note and the four points are on screen. A again
# closes it. Screenshots go to $OUT_DIR. Every key goes through key() (helpers), which checks the debug companion is
# in front on the top screen first; a fresh install's What's new dialog is dismissed with B only when it's on screen.
# Dark and light looks need a person: run it once with the companion following a dark look and once a light one, and
# compare the screenshots (bars in the accent, values and labels in the text color).
set -uo pipefail
cd "$(dirname "$0")/.."

build=true
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) build=false ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

BG_SCRIPT_NAME=verify-background-performance
source scripts/lib/background-helpers.sh

# The chart is one accessibility node: its content-desc carries the title and the values.
desc_has() { dump_ui; grep -q "content-desc=\"[^\"]*$1" "$OUT_DIR/ui.xml"; }
screen_has() { dump_ui; grep -q "text=\"[^\"]*$1" "$OUT_DIR/ui.xml"; }

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; echo; echo "$passes passed, $fails failed, $skips skipped"; exit 1; fi
echo "  output: $OUT_DIR"
if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; exit 1; }; fi
check "debug app installed" sh -c "adb install -r '$APP_APK' >/dev/null"
check "service running" start_service
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"
trap 'echo; echo "$passes passed, $fails failed, $skips skipped (output: $OUT_DIR)"' EXIT

step "Experimental: the Performance row"
sh_dev am start -W -n "$COMPANION" --display 0 --es section experimental >/dev/null
sleep 2
if ! companion_in_front; then fail "the debug companion didn't come to the front on the top screen: stopping"; exit 1; fi
if screen_has "What.s new in"; then
  key KEYCODE_BUTTON_B
  sleep 1
  screen_has "What.s new in" && { fail "the What's new dialog is still up: stopping"; exit 1; }
  pass "What's new dismissed"
fi
key KEYCODE_DPAD_RIGHT
found=false
for i in $(seq 1 10); do
  if focus_has "Performance details"; then found=true; break; fi
  key KEYCODE_DPAD_DOWN
done
check "D-pad Down reaches the Performance row" $found
$found || exit 1
check "the row says it's hidden" focus_has "Performance details, hidden"
desc_has "Processor use while the panel is open" && fail "the chart shows while closed" || pass "closed: no chart"
top_shot performance-closed

step "A opens it"
key KEYCODE_BUTTON_A
sleep 1
check "focus stays on the row, which says it's shown" focus_has "Performance details, shown"
check "chart title in its description" desc_has "Processor use while the panel is open"
check "Still image, about 3%" desc_has "Still image, about 3%"
check "Moving gradient, about 12%" desc_has "Moving gradient, about 12%"
check "Animated GIF, about 13%" desc_has "Animated GIF, about 13%"
check "the measurement note" screen_has "Measured on the AYN Thor"
check "point: only while the panel is open" screen_has "Backgrounds only move while the panel is open"
check "point: the 30 fps cap" screen_has "Capped at 30 frames per second"
check "point: still costs the same as an image" screen_has "costs the same as a still image"
check "point: memory" screen_has "no more memory than a still image"
top_shot performance-open

step "A closes it"
key KEYCODE_BUTTON_A
sleep 1
check "focus stays on the row, which says it's hidden" focus_has "Performance details, hidden"
desc_has "Processor use while the panel is open" && fail "the chart is still showing" || pass "closed again"
top_shot performance-closed-again
