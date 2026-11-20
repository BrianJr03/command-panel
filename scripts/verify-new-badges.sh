#!/usr/bin/env bash
# Verifies the "new" bubbles (#42) on the companion's top screen (display 0), against the debug build.
#   scripts/verify-new-badges.sh [--no-build]
#
# Changes the debug build's prefs (the bubble baseline and seen set, the rail's open groups) and puts the whole prefs
# file back at the end. Checks:
#   - as an upgrader from 0.9.1 (baseline 14): bubbles on every group header (the grouped rail is new), on Keyboard and
#     on the ThorKBM card, and none on General;
#   - focus clears them bottom-up: Extras' header clears its own item but keeps its bubble while Keyboard is new;
#     Keyboard keeps its bubble until the ThorKBM card gets focus; then Keyboard and Extras clear;
#   - the seen set is saved, and survives a relaunch;
#   - the Keyboard tile (#41): bubbles on Panel, Tiles and the tile's row in Tiles settings, clearing bottom-up once
#     the row gets focus;
#   - as a fresh install (baseline 15): no bubbles at all.
# Screenshots of each step go to $OUT_DIR for the PM (bubble style).
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
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-new-badges)}"
BUBBLE="is new since the last update"

passes=0; fails=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
key() { sh_dev input -d 0 keyevent "$1" >/dev/null; sleep 0.7; }
shot() { adb exec-out screencap -p > "$OUT_DIR/$1.png" 2>/dev/null && echo "  screenshot: $OUT_DIR/$1.png"; }
dump_ui() { sh_dev uiautomator dump /sdcard/verify-new.xml >/dev/null; adb pull /sdcard/verify-new.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
ui_has() { dump_ui; grep -q "text=\"$1\"\|content-desc=\"$1\"" "$OUT_DIR/ui.xml"; }

# The labels of the items that carry a bubble, one per line (from each bubble's "<label> is new since..." description).
bubbled() {
  dump_ui
  grep -oE "content-desc=\"[^\"]* $BUBBLE\"" "$OUT_DIR/ui.xml" | sed -E "s/content-desc=\"(.*) $BUBBLE\"/\1/"
}
has_bubble() { bubbled | grep -qx "$1"; }
no_bubble() { ! has_bubble "$1"; }
bubble_count() { bubbled | grep -c . ; }
focus_has() {
  dump_ui
  python3 -I - "$OUT_DIR/ui.xml" <<'PY' | grep -q "$1"
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    if n.get("focused") == "true":
        print(" | ".join(d.get("text") or d.get("content-desc") or "" for d in n.iter("node")))
        break
PY
}
pref_str() { sh_dev run-as "$PKG" cat "$PREFS" | grep -o "<string name=\"$1\">[^<]*" | sed 's/.*>//'; }
in_front() {
  sh_dev dumpsys activity activities | awk '/^Display #/{d=$2} d == "#0" && /topResumedActivity=/ && !p {print; p=1}' | grep -q "$PKG"
}

# Sets the bubble baseline to $1, clears the seen set and the rail's open groups, with the app stopped.
set_baseline() {
  sh_dev am force-stop "$PKG"
  sleep 1
  sh_dev "run-as $PKG sed -i -e '/name=\"new_badge_baseline\"/d' -e '/name=\"new_badge_seen\"/d' -e '/name=\"rail_expanded\"/d' -e 's|</map>|    <int name=\"new_badge_baseline\" value=\"$1\" />\n</map>|' $PREFS"
  sh_dev run-as "$PKG" cat "$PREFS" | grep -q "name=\"new_badge_baseline\" value=\"$1\""
}

launch() {
  sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null
  sleep 3
  local i
  for i in 1 2 3; do
    if ui_has "Got it"; then key KEYCODE_BUTTON_B; sleep 1
    elif ui_has "Skip tour"; then key KEYCODE_BUTTON_Y; sleep 1
    else break; fi
  done
}

# --- setup ----------------------------------------------------------------------------------------------------------

adb get-state >/dev/null 2>&1 || { echo "No device connected." >&2; exit 1; }
step "Install the debug build"
if $build; then check "assembleDebug" ./gradlew -q :app:assembleDebug; fi
check "install $APP_APK" adb install -r "$APP_APK"

saved_prefs="$OUT_DIR/prefs-before.xml"
sh_dev run-as "$PKG" cat "$PREFS" > "$saved_prefs"
restore_prefs() {
  sh_dev am force-stop "$PKG"
  adb push "$saved_prefs" /data/local/tmp/verify-new-prefs.xml >/dev/null 2>&1
  sh_dev run-as "$PKG" cp /data/local/tmp/verify-new-prefs.xml "$PREFS"
  sh_dev rm /data/local/tmp/verify-new-prefs.xml
  # Put the home frontend back on top of the top screen.
  sh_dev am start -a android.intent.action.MAIN -c android.intent.category.HOME --display 0 >/dev/null
}
trap restore_prefs EXIT

# --- upgrader -------------------------------------------------------------------------------------------------------

step "Upgrader from 0.9.1 (baseline 14): bubbles on the groups, Keyboard and the ThorKBM card"
check "baseline set to 14" set_baseline 14
launch
check "companion in front" in_front
check "General has no bubble" no_bubble "General"
for g in Panel Look Extras Advanced; do check "$g header has a bubble" has_bubble "$g"; done
shot 01-upgrader-start

step "Extras: its header clears its own item but keeps the bubble while Keyboard is new"
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN
check "Extras header has focus" focus_has "Extras"
check "group:extras saved as seen" sh -c "adb shell run-as $PKG cat $PREFS | grep -q 'group:extras'"
check "Extras still has a bubble" has_bubble "Extras"
check "Panel keeps its bubble (the Keyboard tile in Tiles is still new)" has_bubble "Panel"
check "Look cleared on the way down" no_bubble "Look"
key KEYCODE_DPAD_RIGHT   # open Extras
check "Keyboard has a bubble" has_bubble "Keyboard"
check "Replay Buffer has no bubble" no_bubble "Replay Buffer"
shot 02-extras-open

step "Keyboard: focusing it keeps its bubble while the ThorKBM card is new"
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN
check "Keyboard has focus" focus_has "Keyboard"
check "section:keyboard saved as seen" sh -c "adb shell run-as $PKG cat $PREFS | grep -q 'section:keyboard'"
check "Keyboard still has a bubble" has_bubble "Keyboard"
check "the ThorKBM card title has a bubble" has_bubble "ThorKBM"
shot 03-keyboard-focused

step "The ThorKBM card: focus inside it clears it, then Keyboard and Extras"
key KEYCODE_DPAD_RIGHT   # into the pane (a standby banner may come first: move down into the card)
for i in 1 2 3 4 5 6; do
  sh_dev run-as "$PKG" cat "$PREFS" | grep -q 'card:thorkbm' && break
  key KEYCODE_DPAD_DOWN
done
check "card:thorkbm saved as seen" sh -c "adb shell run-as $PKG cat $PREFS | grep -q 'card:thorkbm'"
check "the ThorKBM card has no bubble" no_bubble "ThorKBM"
check "Keyboard has no bubble" no_bubble "Keyboard"
check "Extras has no bubble" no_bubble "Extras"
shot 04-cleared

step "Seen bubbles stay cleared after a relaunch"
sh_dev am force-stop "$PKG"
launch
check "Extras has no bubble after relaunch" no_bubble "Extras"
check "Advanced (never focused) still has a bubble" has_bubble "Advanced"
shot 05-relaunch

# --- the Keyboard tile (#41) ---------------------------------------------------------------------------------------

step "The Keyboard tile row: Panel, then Tiles, then the row, clearing bottom-up"
sh_dev am force-stop "$PKG"
launch
key KEYCODE_DPAD_DOWN   # Panel header
check "Panel header has focus" focus_has "Panel"
check "Panel has a bubble" has_bubble "Panel"
key KEYCODE_DPAD_RIGHT  # open Panel
check "Tiles has a bubble" has_bubble "Tiles"
check "Nav Bar has no bubble" no_bubble "Nav Bar"
shot 07-panel-open
key KEYCODE_DPAD_DOWN; key KEYCODE_DPAD_DOWN
check "Tiles has focus" focus_has "Tiles"
check "Tiles keeps its bubble while the tile is new" has_bubble "Tiles"
key KEYCODE_DPAD_RIGHT  # into the Tiles pane
seen_row=false; on_row=false
for i in $(seq 1 45); do
  if sh_dev run-as "$PKG" cat "$PREFS" | grep -q 'card:tile_keyboard'; then on_row=true; break; fi
  # Kept from the step just before the row gets focus (the bubble clears as it does).
  if has_bubble "Keyboard"; then seen_row=true; shot 08-tile-row-bubble; fi
  key KEYCODE_DPAD_DOWN
done
check "the Keyboard tile row showed a bubble" $seen_row
check "the Keyboard tile row got focus" $on_row
check "focus is on the Keyboard tile row" focus_has "Keyboard"
check "card:tile_keyboard saved as seen" sh -c "adb shell run-as $PKG cat $PREFS | grep -q 'card:tile_keyboard'"
check "the tile row has no bubble" no_bubble "Keyboard"
check "Tiles has no bubble" no_bubble "Tiles"
check "Panel has no bubble" no_bubble "Panel"
shot 09-tile-cleared

# --- fresh install --------------------------------------------------------------------------------------------------

step "Fresh install (baseline 15): no bubbles"
check "baseline set to 15" set_baseline 15
launch
check "no bubbles on the rail" test "$(bubble_count)" -eq 0
shot 06-fresh

echo
echo "Done: $passes passed, $fails failed. Screenshots in $OUT_DIR"
[ "$fails" -eq 0 ]
