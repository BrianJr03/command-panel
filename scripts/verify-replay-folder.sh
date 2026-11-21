#!/usr/bin/env bash
# Verifies #74 on the debug build: picking a clips folder on the SD card is refused with its own message, shown right
# under Save location in the Replay section, focus stays on "Choose folder…", and the save location doesn't change.
#   scripts/verify-replay-folder.sh [--no-build]
#
# Half manual: the folder picker is system UI, so a person picks the SD card folder (the script says when). The script
# opens the debug companion on Replay, then after the pick reads the screen (uiautomator), the focused node and the
# replay_clips_dir pref, and takes screenshots. No gamepad keys are sent and no settings are changed by the script.
# Needs an SD card in the device. Screenshots go to $OUT_DIR.
set -uo pipefail
cd "$(dirname "$0")/.."

build=true
for arg in "$@"; do
  case "$arg" in
    --no-build) build=false ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

BG_SCRIPT_NAME=verify-replay-folder
# Constants (debug package, prefs path), pass/fail counters, the focus guards and the UI dump helpers.
source scripts/lib/background-helpers.sh

SD_MESSAGE="Clips can't be saved to the SD card yet"
summary() { echo; echo "$passes passed, $fails failed, $skips skipped (output: $OUT_DIR)"; }
ask() { echo; read -r -p "  >> $* (Enter when done) " _; }
screen_has() { dump_ui; grep -q "$1" "$OUT_DIR/ui.xml"; }

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; summary; exit 1; fi
if adb shell ls /storage 2>/dev/null | tr -d '\r' | grep -qE '^[0-9A-F]{4}-[0-9A-F]{4}$'; then
  pass "an SD card is mounted"
else
  fail "no SD card mounted under /storage: put one in and run again"; summary; exit 1
fi
echo "  output: $OUT_DIR"
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"

if $build; then
  step "Build and install the debug app"
  ./gradlew -q :app:assembleDebug && adb install -r "$APP_APK" >/dev/null && pass "installed" || { fail "build or install"; summary; exit 1; }
fi

step "Open Replay"
before="$(pref replay_clips_dir)"
echo "  save location before: ${before:-<default TCP Clips>}"
sh_dev am start -W -n "$COMPANION" --display 0 --es section replay >/dev/null
sleep 2
guard_focus
check "the debug companion is in front" companion_in_front
top_shot replay-before

step "Pick a folder on the SD card"
ask "On the top screen, go to Save location > Choose folder…, press A, open the SD card in the picker and use any folder on it"
wait_for 10 companion_in_front
sleep 1.5
top_shot replay-after-sd-pick
check "the SD card message is shown" screen_has "$SD_MESSAGE"
check "the generic invalid-folder message is not shown" bash -c "! grep -q \"That folder can't hold clips\" \"$OUT_DIR/ui.xml\""
# The message sits right under the Save location chips: above the "Saving to" line.
check "the message is under the chips, above the current location line" python3 -I - "$OUT_DIR/ui.xml" "$SD_MESSAGE" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = [n for n in ET.parse(sys.argv[1]).getroot().iter("node")]
def top(match):
    for n in nodes:
        if match in (n.get("text") or ""):
            return int(n.get("bounds").split("][")[0].strip("[").split(",")[1])
    return None
msg, chip, where = top(sys.argv[2]), top("Choose folder"), top("Saving to ")
sys.exit(0 if msg is not None and chip is not None and chip < msg and (where is None or msg < where) else 1)
PY
check "focus stays on Choose folder…" focus_has "Choose folder"
after="$(pref replay_clips_dir)"
check "the save location didn't change (${after:-<default>})" [ "$after" = "$before" ]

step "Default clears it"
ask "Press A on TCP Clips (the default chip) next to Choose folder… (this sets the debug app back to the default folder)"
sleep 1
top_shot replay-after-default
check "the SD card message is gone" bash -c "! (adb shell uiautomator dump /sdcard/verify-bg-ui.xml >/dev/null 2>&1 && adb shell cat /sdcard/verify-bg-ui.xml | grep -q \"$SD_MESSAGE\")"

echo
echo "  Needs your eyes: the message reads well in the card (red, one or two lines) in $OUT_DIR/replay-after-sd-pick.png."
summary
