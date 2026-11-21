#!/usr/bin/env bash
# Verifies the Replay Buffer's clips folder on the debug build:
#   #75: a folder picked on the SD card is accepted, a clip saved with the normal save broadcast lands on
#        /storage/<card>/<folder>/<Top|Bottom|Both>/, the media store indexes it (so it shows in the gallery), Replay
#        Studio lists it, and the "Saving to" line names the card;
#   #81: a folder picked under the picker's Documents shortcut is accepted as internal Documents/<folder>;
#   #74: a refused folder's message sits right under the Save location chips and focus stays on "Choose folder…".
#   scripts/verify-replay-folder.sh [--no-build] [--card 3A0D-5000]
#
# Half manual: the folder picker is system UI, so a person picks the folders (the script says when). No gamepad keys
# are sent by the script. It turns the debug app's Replay Buffer on to save test clips, deletes the clips it made, and
# puts the debug app's clips folder pref back at the end. It never unmounts the card: see "Card removed" at the end for
# the manual check. Screenshots go to $OUT_DIR.
set -uo pipefail
cd "$(dirname "$0")/.."

build=true
CARD=""
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) build=false ;;
    --card) CARD="$2"; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

BG_SCRIPT_NAME=verify-replay-folder
# Constants (debug package, prefs path), pass/fail counters, the focus guards and the UI dump helpers.
source scripts/lib/background-helpers.sh

summary() { echo; echo "$passes passed, $fails failed, $skips skipped (output: $OUT_DIR)"; }
ask() { echo; read -r -p "  >> $* (Enter when done) " _; }
screen_has() { dump_ui; grep -qF "$1" "$OUT_DIR/ui.xml"; }
log_tail() { adb logcat -d -s ReplayBuffer:* AssistantService:* 2>/dev/null | tr -d '\r'; }
# Saves a clip with the app's own save broadcast and prints the saved path (empty on failure).
save_clip() {
  adb logcat -c
  sh_dev am broadcast -a "$ACTION_PREFIX.REPLAY_SAVE" -p "$PKG" >/dev/null
  wait_for 20 'log_tail | grep -qE "saved replay clip -> |replay save: error"'
  log_tail | grep -oE 'saved replay clip -> .*' | tail -1 | sed 's/^saved replay clip -> //'
}
made=()

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; summary; exit 1; fi
[ -n "$CARD" ] || CARD="$(sh_dev ls /storage | grep -E '^[0-9A-F]{4}-[0-9A-F]{4}$' | head -1)"
if [ -n "$CARD" ] && sh_dev sm list-volumes public | grep -q "$CARD"; then
  pass "card $CARD is mounted ($(sh_dev sm list-volumes public | grep "$CARD" | xargs))"
else
  fail "no SD card mounted (looked for ${CARD:-any XXXX-XXXX} under /storage): put one in and run again"; summary; exit 1
fi
echo "  output: $OUT_DIR"
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"

if $build; then
  step "Build and install the debug app"
  ./gradlew -q :app:assembleDebug && adb install -r "$APP_APK" >/dev/null && pass "installed" || { fail "build or install"; summary; exit 1; }
fi

before="$(pref replay_clips_dir)"
was_enabled="$(pref replay_enabled)"
cleanup() {
  step "Cleanup"
  for f in "${made[@]}"; do sh_dev rm -f "'$f'"; echo "  removed $f"; done
  # The app is stopped first so it can't write the prefs file back over the edit; then the old value goes back.
  [ "$was_enabled" = "true" ] || sh_dev am broadcast -a "$ACTION_PREFIX.REPLAY_SET_ENABLED" -p "$PKG" --ez enable false >/dev/null
  sleep 1
  sh_dev am force-stop "$PKG"
  if [ -z "$before" ]; then
    sh_dev run-as "$PKG" sed -i '/name="replay_clips_dir"/d' "$PREFS"
  else
    sh_dev run-as "$PKG" sed -i "s|<string name=\"replay_clips_dir\">[^<]*</string>|<string name=\"replay_clips_dir\">$before</string>|" "$PREFS"
  fi
  echo "  clips folder pref back to: ${before:-<default TCP Clips>}"
  summary
}
trap cleanup EXIT

step "Open Replay"
echo "  save location before: ${before:-<default TCP Clips>}"
sh_dev am start -W -n "$COMPANION" --display 0 --es section replay >/dev/null
sleep 2
guard_focus
check "the debug companion is in front" companion_in_front
top_shot replay-before

step "#75: pick a folder on the SD card"
ask "On the top screen, go to Save location > Choose folder…, press A, open the SD card ($CARD) in the picker and use a folder on it (make one called CP Test if you like)"
wait_for 10 companion_in_front
sleep 1.5
top_shot replay-after-sd-pick
picked="$(pref replay_clips_dir)"
echo "  stored location: $picked"
check "the stored location is on the card ($CARD:<folder>)" bash -c "echo '$picked' | grep -qE '^$CARD:[^:]+$'"
check "no refusal message under the chips" bash -c "! grep -qF \"can't hold clips\" '$OUT_DIR/ui.xml' && ! grep -qF \"isn't available right now\" '$OUT_DIR/ui.xml'"
check "the Saving to line names the folder on a card" screen_has "Saving to ${picked#*:} on "

step "#75: save a clip to the card"
sh_dev am broadcast -a "$ACTION_PREFIX.REPLAY_SET_ENABLED" -p "$PKG" --ez enable true >/dev/null
sleep 6   # let the buffer fill a few seconds
path="$(save_clip)"
echo "  saved: ${path:-<nothing>}"
[ -n "$path" ] && made+=("$path")
check "the clip path is under /storage/$CARD/${picked#*:}/" bash -c "echo '$path' | grep -qF '/storage/$CARD/${picked#*:}/'"
check "the file exists on the card" bash -c "adb shell \"[ -s '$path' ] && echo yes\" | tr -d '\r' | grep -q yes"
vol="$(echo "$CARD" | tr 'A-F' 'a-f')"
wait_for 10 "adb shell content query --uri content://media/$vol/video/media --projection _data 2>/dev/null | grep -qF '$(basename "$path")'"
check "the media store indexed it on the card's volume ($vol)" bash -c \
  "adb shell content query --uri content://media/$vol/video/media --projection _data 2>/dev/null | grep -qF '$(basename "$path")'"
# The debug-only STUDIO_LIST broadcast logs Replay Studio's library, one CLIP line per clip.
check "Replay Studio lists it" bash -c "adb logcat -c; adb shell am broadcast -a '$PKG.action.STUDIO_LIST' -p '$PKG' >/dev/null; sleep 4; adb logcat -d -s ReplayStudioDebug:* | grep -F 'CLIP|' | grep -qF '$path'"

step "#81: pick a folder under Documents"
ask "Go to Save location > Choose folder… again, open Documents in the picker's menu (the Documents shortcut, not Internal storage > Documents) and use a folder inside it"
wait_for 10 companion_in_front
sleep 1.5
top_shot replay-after-documents-pick
docs="$(pref replay_clips_dir)"
echo "  stored location: $docs"
check "stored as internal Documents/<folder>" bash -c "echo '$docs' | grep -qE '^Documents/[^:]+$'"
check "the Saving to line shows /storage/emulated/0/Documents/..." screen_has "Saving to /storage/emulated/0/Documents/"

step "#74: a refused folder keeps its message under the chips"
ask "Go to Choose folder… once more and pick the storage root (Internal storage itself, or Android/)"
wait_for 10 companion_in_front
sleep 1.5
top_shot replay-after-refused-pick
check "the refusal message is shown" screen_has "That folder can't hold clips"
check "the message is under the chips, above the Saving to line" python3 -I - "$OUT_DIR/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).getroot().iter("node"))
def top(match):
    for n in nodes:
        if match in (n.get("text") or ""):
            return int(n.get("bounds").split("][")[0].strip("[").split(",")[1])
    return None
msg, chip, where = top("can't hold clips"), top("Choose folder"), top("Saving to ")
sys.exit(0 if msg is not None and chip is not None and chip < msg and (where is None or msg < where) else 1)
PY
check "focus stays on Choose folder…" focus_has "Choose folder"
check "the location didn't change" [ "$(pref replay_clips_dir)" = "$docs" ]

echo
echo "  Card removed (manual, not scripted; 'sm unmount' can leave the card in a bad state, so the script never runs it):"
echo "    1. Pick a folder on the card again, then take the card out (or Settings > Storage > Eject)."
echo "    2. Replay section: a red line under Saving to says the card isn't available and clips go to TCP Clips."
echo "    3. Save a clip (double-press or Save clip now): the toast says it went to TCP Clips; the file is in"
echo "       /storage/emulated/0/TCP Clips/<screen>/; nothing new appears under /storage/ (no stray folder)."
echo "    4. Put the card back: the red line goes away and the next clip lands on the card again."
echo "  Needs your eyes: the Saving to line and messages read well (screenshots in $OUT_DIR)."
