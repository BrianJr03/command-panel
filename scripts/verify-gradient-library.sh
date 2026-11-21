#!/usr/bin/env bash
# Verifies Appearance's "Create your own animated background" card and the gradient library (#67) on the debug build,
# driven with the gamepad's D-pad on the top screen.
#   scripts/verify-gradient-library.sh [--no-build]
#
# Seeds a background image and two gradients through DEBUG_BACKGROUND (the `path` and `gradient` extras,
# system/BackgroundImageDebug.kt), with Animated backgrounds off so the card's Experimental link shows. Then, on the
# companion (debug build only, jr.brian.commandpannel.debug):
#   - Appearance: the card shows which gradient is showing, the Experimental link, and the image card says its image
#     isn't showing; D-pad Down reaches "Open gradients";
#   - A opens the library: focus starts on the showing gradient's Applied button; the header says what's showing;
#   - Apply on the other gradient makes it the source (background_source pref);
#   - Duplicate saves "<name> copy" (gradient_backgrounds pref) and focus moves to the copy's Apply;
#   - Delete takes two presses ("Press again to delete"), removes the copy and focus stays in the library;
#   - "Use my image instead" makes the image the source again and focus lands on a tile's Apply;
#   - Gradient Studio (#68), from a tile's Edit: focus starts on the Colors tab; R2 / L2 walk the tabs; Add color and
#     the hue stepper change the stops; the speed stepper goes Steady to Lively, L3 undoes and R3 redoes it; Select
#     jumps to Save (prefs: 3 stops, speed 1.5, new colors, source unchanged); Start jumps to Save and apply (source is
#     that gradient, back in the library, no draft left in gradient_studio_draft);
#   - New gradient opens Studio on "My gradient 3"; after a change, B asks to discard (focus on Keep editing, the draft
#     is kept for Resume); Save and apply saves it as a third gradient and shows it;
#   - B goes back to Appearance with focus on "Open gradients", and the card names the new gradient.
# Every key goes through key() (helpers), which checks the debug companion is in front on the top screen first: the
# release Command Panel can catch stray presses. A fresh install's What's new dialog is dismissed with B only after
# that check and only when the dialog is on screen. Screenshots go to $OUT_DIR; looks need a person.
# At the end the user's prefs come back and the test gradients are deleted.
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

BG_SCRIPT_NAME=verify-gradient-library
# Constants, pass/fail counters and the adb, log, focus, key and screenshot helpers.
source scripts/lib/background-helpers.sh

GRADIENT_IDS=()
NAME_A="Library A"
NAME_B="Library B"

# GradientJson for <id> <name> <style> <motion> <speed> <stops "AARRGGBB@pos,...">, on one line with no spaces in
# keys and no single quotes (it goes through the device shell in single quotes).
lib_json() {
  local id="$1" name="$2" style="$3" motion="$4" speed="$5" stops="$6" js="" s parts
  IFS=',' read -ra parts <<< "$stops"
  for s in "${parts[@]}"; do js+="${js:+,}{\"color\":$((16#${s%@*})),\"position\":${s#*@}}"; done
  printf '{"format":"command-panel-gradient","version":1,"id":"%s","name":"%s","stops":[%s],"style":"%s","angle":0,"motion":"%s","speed":%s}' \
    "$id" "$name" "$js" "$style" "$motion" "$speed"
}
# Saves a gradient through the hook (which also makes it the active source); waits for the outcome.
seed_gradient() {
  local json; json="$(lib_json "$@")"
  GRADIENT_IDS+=("$1")
  log_clear
  broadcast DEBUG_BACKGROUND --ez animations false --es gradient "'$json'"
  wait_for 10 'log_has "debug gradient (set|failed)"' && echo "$LOG" | grep -q "debug gradient set $1 "
}

# The raw gradient_backgrounds pref (a JSON array, XML-escaped).
gradients_raw() { sh_dev run-as "$PKG" cat "$PREFS" | grep 'name="gradient_backgrounds"'; }
gradients_have() { gradients_raw | grep -q "$1"; }
gradient_count() { gradients_raw | grep -o 'command-panel-gradient' | wc -l | xargs; }
source_is() { [ "$(pref background_source)" = "$1" ]; }
# One field of saved gradient <id> from the gradient_backgrounds pref: speed, stop_count, or stops (colors@positions).
gradient_field() {
  gradients_raw | python3 -I -c '
import html, json, re, sys
raw = sys.stdin.read()
m = re.search(r">(.*)</string>", raw, re.S)
specs = json.loads(html.unescape(m.group(1))) if m else []
g = next((s for s in specs if s.get("id") == sys.argv[1]), None)
if g is None: sys.exit(0)
f = sys.argv[2]
if f == "speed": print(("%g" % g["speed"]))
elif f == "stop_count": print(len(g["stops"]))
elif f == "stops": print(",".join("%d@%g" % (s["color"], s["position"]) for s in g["stops"]))
' "$1" "$2"
}
# The focused control's text is exactly $1.
focus_is() { [ "$(focused_text)" = "$1" ]; }
# Any text on screen contains $1.
screen_has() { dump_ui; grep -q "text=\"[^\"]*$1" "$OUT_DIR/ui.xml"; }
# Presses $2 up to $3 times until the focused control's text is exactly $1.
walk_to() {
  local want="$1" k="$2" max="$3" i
  for i in $(seq 0 "$max"); do
    focus_is "$want" && return 0
    [ "$i" -lt "$max" ] && key "$k"
  done
  return 1
}

# --- setup ----------------------------------------------------------------------------------------------------------

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; echo; echo "$passes passed, $fails failed, $skips skipped"; exit 1; fi
echo "  output: $OUT_DIR"
[ -x "$FFMPEG" ] || { fail "ffmpeg not found at $FFMPEG (set FFMPEG)"; exit 1; }
if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; exit 1; }; fi
check "debug app installed" sh -c "adb install -r '$APP_APK' >/dev/null"
check "service running" start_service
top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
TOP_PHYSICAL="$(echo "$top" | grep -oE "local:[0-9]+" | cut -d: -f2)"
echo "  top: ${TOP_PHYSICAL:-?}"

saved_prefs="$OUT_DIR/prefs-before.xml"
sh_dev run-as "$PKG" cat "$PREFS" > "$saved_prefs"
echo "  source before: $(pref background_source)"
cleanup() {
  step "Restore"
  local id; for id in $(printf '%s\n' "${GRADIENT_IDS[@]}" | sort -u); do broadcast DEBUG_BACKGROUND --es gradient_delete "$id"; done
  broadcast DEBUG_BACKGROUND --ez clear true
  sleep 2
  sh_dev am force-stop "$PKG"
  adb push "$saved_prefs" /data/local/tmp/verify-glib-prefs.xml >/dev/null 2>&1
  sh_dev run-as "$PKG" cp /data/local/tmp/verify-glib-prefs.xml "$PREFS"
  sh_dev rm -rf /data/local/tmp/verify-glib-prefs.xml "$DEV_DIR"
  start_service && echo "  service restarted, source: $(pref background_source)"
  echo; echo "$passes passed, $fails failed, $skips skipped (output: $OUT_DIR)"
}
trap cleanup EXIT

step "Seed an image and two gradients (Animated backgrounds off)"
check "test images made and pushed" make_images
name="$(seed red.png off)"
check "background image stored ($name)" test -n "$name"
check "$NAME_A saved" seed_gradient glib-a "$NAME_A" linear drift 1 "FFFF0000@0,FF0000FF@1"
check "$NAME_B saved" seed_gradient glib-b "$NAME_B" radial wave 0 "FF00FF00@0,FFFFFF00@1"
check "two gradients in the library ($(gradient_count))" test "$(gradient_count)" -eq 2
check "$NAME_B is the source ($(pref background_source))" source_is gradient:glib-b
check "the switch is off" test "$(anim_pref)" = false

# --- the card in Appearance -----------------------------------------------------------------------------------------

step "Appearance card (gamepad, top screen)"
sh_dev am start -W -n "$COMPANION" --display 0 --es section appearance >/dev/null
sleep 2
if ! companion_in_front; then
  fail "the debug companion didn't come to the front on the top screen: stopping"
  exit 1
fi
# A fresh install opens with What's new over the companion, which would catch the D-pad. B closes it; only send B
# when the dialog is up (B with nothing to close goes Back and leaves the app).
if screen_has "What.s new in"; then
  key KEYCODE_BUTTON_B
  sleep 1
  screen_has "What.s new in" && { fail "the What's new dialog is still up: stopping"; exit 1; }
  pass "What's new dismissed"
fi
check "card shows what's showing (Showing: $NAME_B)" screen_has "Showing: $NAME_B"
check "card shows the Experimental link (switch off)" screen_has "Gradients move only with Animated backgrounds"
check "the image card says its image isn't showing" screen_has "Not showing now: your gradient $NAME_B"
key KEYCODE_DPAD_RIGHT
check "D-pad Down reaches Open gradients" walk_to "Open gradients" KEYCODE_DPAD_DOWN 16
top_shot card
# Use my image instead sits right of Open gradients while a gradient shows and an image is stored.
key KEYCODE_DPAD_RIGHT
check "Use my image instead is next to it" focus_is "Use my image instead"
key KEYCODE_DPAD_LEFT

# --- the library ----------------------------------------------------------------------------------------------------

step "Library"
key KEYCODE_BUTTON_A
sleep 1
check "the library opened" screen_has "Your gradients"
check "header says what's showing" screen_has "Showing your gradient $NAME_B"
check "header says gradients show still with the switch off" screen_has "so gradients show still"
check "focus starts on the showing gradient's Applied" walk_to "Applied" KEYCODE_DPAD_DOWN 0
top_shot library

step "Apply"
# $NAME_A's tile is left of $NAME_B's: Left walks its Delete, Duplicate, then Apply.
check "D-pad Left reaches $NAME_A's Apply" walk_to "Apply" KEYCODE_DPAD_LEFT 6
key KEYCODE_BUTTON_A
sleep 1
check "$NAME_A is the source ($(pref background_source))" source_is gradient:glib-a
check "its button now says Applied (focus stays)" focus_is "Applied"
check "header follows" screen_has "Showing your gradient $NAME_A"
top_shot applied

step "Duplicate"
check "D-pad Right reaches Duplicate" walk_to "Duplicate" KEYCODE_DPAD_RIGHT 3
key KEYCODE_BUTTON_A
sleep 1
check "a copy was saved ($(gradient_count) gradients)" test "$(gradient_count)" -eq 3
check "named \"$NAME_A copy\"" gradients_have "$NAME_A copy"
check "status line says so" screen_has "Copy saved as $NAME_A copy"
check "focus moved to the copy's Apply" focus_is "Apply"
check "the source didn't change" source_is gradient:glib-a
top_shot duplicated

step "Delete (two presses)"
check "D-pad Right reaches the copy's Delete" walk_to "Delete" KEYCODE_DPAD_RIGHT 3
key KEYCODE_BUTTON_A
check "first press asks to confirm" focus_is "Press again to delete"
check "nothing deleted yet ($(gradient_count))" test "$(gradient_count)" -eq 3
key KEYCODE_BUTTON_A
sleep 1
check "the copy is gone ($(gradient_count) gradients)" test "$(gradient_count)" -eq 2
gradients_have "$NAME_A copy" && fail "the copy is still in gradient_backgrounds" || pass "the copy is out of gradient_backgrounds"
f="$(focused_text)"
check "focus stayed in the library on a tile button ($f)" sh -c "[ '$f' = Apply ] || [ '$f' = Applied ]"
top_shot deleted

step "Use my image instead"
check "D-pad Up reaches Use my image instead" walk_to "Use my image instead" KEYCODE_DPAD_UP 8
key KEYCODE_BUTTON_A
sleep 1
check "the image is the source ($(pref background_source))" source_is image
screen_has "Use my image instead" && fail "Use my image instead is still on screen" || pass "Use my image instead is gone"
check "focus landed on the gradient that was showing ($NAME_A's Apply)" focus_is "Apply"
check "header says the image is showing" screen_has "Showing your background image"
check "the gradients are kept ($(gradient_count))" test "$(gradient_count)" -eq 2
top_shot image-back

# --- Gradient Studio (#68) ------------------------------------------------------------------------------------------

step "Gradient Studio: open from Edit"
before_a="$(gradient_field glib-a stops)"
check "D-pad Right reaches $NAME_A's Edit" walk_to "Edit" KEYCODE_DPAD_RIGHT 2
key KEYCODE_BUTTON_A
sleep 1.5
check "Gradient Studio opened (its tabs are on screen)" screen_has "Fine-tune"
check "focus starts on the Colors tab" focus_is "Colors"
check "the name field shows $NAME_A" screen_has "$NAME_A"
top_shot studio-open

step "Gradient Studio: L2 / R2 switch tabs"
for t in Motion Fine-tune Presets; do key KEYCODE_BUTTON_R2; check "R2 goes to $t" focus_is "$t"; done
key KEYCODE_BUTTON_R2; check "R2 wraps to Colors" focus_is "Colors"
key KEYCODE_BUTTON_L2; check "L2 goes back to Presets" focus_is "Presets"
key KEYCODE_BUTTON_R2

step "Gradient Studio: change a color stop"
check "D-pad Down reaches Add color" walk_to "Add color" KEYCODE_DPAD_DOWN 6
key KEYCODE_BUTTON_A
check "a third color was added" screen_has "Color 2 of 3"
# Down to the hue stepper of the picked (new) color, and two steps up.
check "D-pad Down reaches the hue stepper" walk_to "−" KEYCODE_DPAD_DOWN 4
check "D-pad Right reaches its +" walk_to "+" KEYCODE_DPAD_RIGHT 2
key KEYCODE_BUTTON_A; key KEYCODE_BUTTON_A
top_shot studio-colors

step "Gradient Studio: speed, undo and redo"
key KEYCODE_BUTTON_R2
check "on the Motion tab" focus_is "Motion"
check "speed starts at Steady (speed 1)" screen_has "Steady"
check "D-pad Down reaches the speed stepper" walk_to "−" KEYCODE_DPAD_DOWN 6
check "D-pad Right reaches its +" walk_to "+" KEYCODE_DPAD_RIGHT 2
key KEYCODE_BUTTON_A
check "one step up is Lively" screen_has "Lively"
key KEYCODE_BUTTON_THUMBL
check "L3 undoes it (Steady again)" screen_has "Steady"
key KEYCODE_BUTTON_THUMBR
check "R3 redoes it (Lively again)" screen_has "Lively"
top_shot studio-motion

step "Gradient Studio: Save (Select), then Save and apply (Start)"
key KEYCODE_BUTTON_SELECT
check "Select jumps to Save" focus_is "Save"
key KEYCODE_BUTTON_A
sleep 1
check "saved: $NAME_A has 3 stops ($(gradient_field glib-a stop_count))" test "$(gradient_field glib-a stop_count)" = 3
check "saved: its speed is 1.5 ($(gradient_field glib-a speed))" test "$(gradient_field glib-a speed)" = 1.5
check "saved: its colors changed" test "$(gradient_field glib-a stops)" != "$before_a"
check "still in Gradient Studio after Save" screen_has "Fine-tune"
check "Save didn't change the source ($(pref background_source))" source_is image
key KEYCODE_BUTTON_START
check "Start jumps to Save and apply" focus_is "Save and apply"
key KEYCODE_BUTTON_A
sleep 1.5
check "$NAME_A is the source ($(pref background_source))" source_is gradient:glib-a
check "back in the library" screen_has "Your gradients"
check "no unsaved draft left behind" test -z "$(pref gradient_studio_draft)"
top_shot studio-applied

step "Gradient Studio: New gradient, discard prompt, Save and apply"
# Up to the header row (it may land on Back first, then Right to New gradient).
to_new() {
  local i f
  for i in $(seq 1 12); do
    f="$(focused_text)"
    [ "$f" = "New gradient" ] && return 0
    if [ "$f" = Back ]; then key KEYCODE_DPAD_RIGHT; else key KEYCODE_DPAD_UP; fi
  done
  return 1
}
check "D-pad Up reaches New gradient" to_new
key KEYCODE_BUTTON_A
sleep 1.5
check "Gradient Studio opened on a new gradient" screen_has "My gradient 3"
key KEYCODE_BUTTON_R2
walk_to "−" KEYCODE_DPAD_DOWN 6 && walk_to "+" KEYCODE_DPAD_RIGHT 2 && key KEYCODE_BUTTON_A
key KEYCODE_BUTTON_B
sleep 1
check "B with unsaved changes asks first" screen_has "Discard your changes"
check "focus is on Keep editing" focus_is "Keep editing"
key KEYCODE_BUTTON_A
sleep 1
check "Keep editing stays in Gradient Studio" screen_has "Fine-tune"
check "the unsaved draft is kept for Resume ($(pref gradient_studio_draft | head -c 40))" test -n "$(pref gradient_studio_draft)"
key KEYCODE_BUTTON_START
key KEYCODE_BUTTON_A
sleep 1.5
check "the new gradient was saved ($(gradient_count) gradients)" test "$(gradient_count)" -eq 3
check "and is the source ($(pref background_source))" sh -c "echo '$(pref background_source)' | grep -q '^gradient:' && [ '$(pref background_source)' != gradient:glib-a ]"
check "the draft was cleared" test -z "$(pref gradient_studio_draft)"
top_shot studio-new

step "Back"
key KEYCODE_BUTTON_B
sleep 1.5
check "back on Appearance with focus on Open gradients" focus_is "Open gradients"
check "the card says the new gradient is showing" screen_has "Showing: My gradient 3"
top_shot back
