#!/usr/bin/env bash
# Verifies the companion's grouped settings rail with the gamepad (#29), on the top screen (display 0).
#   scripts/verify-rail-groups.sh [--no-build]
#
# Against the debug build only (it's the one with this branch's code, and run-as can read its prefs). It changes the
# debug build's rail prefs (rail_expanded, the group section orders) and puts them back at the end. Checks:
#   - every group starts collapsed (after clearing rail_expanded), and collapsed sections aren't on screen;
#   - D-pad Down from General lands on the first header, and focusing a header doesn't change the pane;
#   - D-pad Right on a collapsed header expands it, and the state is saved in rail_expanded;
#   - D-pad Down moves into its sections and selects them; D-pad Left goes back to the header, Left again collapses;
#   - A on a header toggles it; Right on an open header moves into the pane, Left comes back to the selected section;
#   - expanded state survives a force-stop and relaunch;
#   - a touch tap on a header expands it;
#   - A / D-pad / A moves a section inside its group (rail_sections_panel), and B drops without leaving the app.
# Screenshots of each step go to $OUT_DIR for the PM's eyes (look and feel).
#   --no-build  Installs the debug APK already built instead of building it.
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
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-rail-groups)}"

passes=0; fails=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
key() { sh_dev input -d 0 keyevent "$1" >/dev/null; sleep 0.6; }
shot() { adb exec-out screencap -p > "$OUT_DIR/$1.png" 2>/dev/null && echo "  screenshot: $OUT_DIR/$1.png"; }

dump_ui() { sh_dev uiautomator dump /sdcard/verify-rail.xml >/dev/null; adb pull /sdcard/verify-rail.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
ui_has() { dump_ui; grep -q "text=\"$1\"\|content-desc=\"$1\"" "$OUT_DIR/ui.xml"; }
ui_lacks() { ! ui_has "$1"; }
# Taps the center of the first node whose text or content description is exactly $1.
tap_node() {
  dump_ui
  local b
  b="$(grep -oE "<node [^>]*(text|content-desc)=\"$1\"[^>]*>" "$OUT_DIR/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  [ -n "$b" ] || return 1
  set -- $b
  sh_dev input -d 0 tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
  sleep 0.8
}
# Text and content descriptions inside the focused node (Compose reports focus on the focusable row).
focused_text() {
  dump_ui
  python3 -I - "$OUT_DIR/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if n.get("focused") == "true":
        parts = [d.get("text") or d.get("content-desc") for d in n.iter("node")]
        print(" | ".join(p for p in parts if p))
        break
PY
}
focus_has() { focused_text | grep -q "$1"; }
pref() { sh_dev run-as "$PKG" cat "$PREFS" | grep -o "<string name=\"$1\">[^<]*" | sed 's/.*>//'; }
pref_has() { pref "$1" | tr ',' '\n' | grep -qx "$2"; }
pref_lacks() { ! pref_has "$1" "$2"; }
focus_lacks() { ! focus_has "$1"; }

launch() {
  sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null
  sleep 2
  # A fresh debug install opens with What's New, then the companion tour: close both.
  local i
  for i in 1 2 3; do
    if ui_has "Got it"; then tap_node "Got it"; sleep 1
    elif ui_has "Skip tour"; then tap_node "Skip tour"; sleep 1
    else break; fi
  done
}

# --- setup ----------------------------------------------------------------------------------------------------------

adb get-state >/dev/null 2>&1 || { echo "No device connected." >&2; exit 1; }
step "Install the debug build"
if $build; then check "assembleDebug" ./gradlew -q :app:assembleDebug; fi
check "install $APP_APK" adb install -r "$APP_APK"

# Saved so the end of the run can put the user's rail back.
saved_prefs="$OUT_DIR/prefs-before.xml"
sh_dev run-as "$PKG" cat "$PREFS" > "$saved_prefs"
restore_prefs() {
  sh_dev am force-stop "$PKG"
  adb push "$saved_prefs" /data/local/tmp/verify-rail-prefs.xml >/dev/null 2>&1
  sh_dev run-as "$PKG" cp /data/local/tmp/verify-rail-prefs.xml "$PREFS"
  sh_dev rm /data/local/tmp/verify-rail-prefs.xml
}
trap restore_prefs EXIT

# Start with every group collapsed: drop rail_expanded from the prefs file.
sh_dev am force-stop "$PKG"
sh_dev run-as "$PKG" sed -i '/name="rail_expanded"/d' "$PREFS"

# --- gamepad --------------------------------------------------------------------------------------------------------

step "Start: General selected, every group collapsed"
launch
check "General has focus" focus_has "General"
check "Panel header shows Expand" ui_has "Expand Panel"
check "Nav Bar (in collapsed Panel) isn't on screen" ui_lacks "Nav Bar"
shot 01-start

step "D-pad Down to the Panel header"
key KEYCODE_DPAD_DOWN
check "Panel header has focus" focus_has "Panel"
check "the pane still shows General" ui_has "Core behavior and companion features"
shot 02-header-focused

step "D-pad Right expands Panel"
key KEYCODE_DPAD_RIGHT
check "Panel header shows Collapse" ui_has "Collapse Panel"
check "Nav Bar is on screen" ui_has "Nav Bar"
check "focus stays on the header" focus_has "Panel"
check "rail_expanded has panel" pref_has rail_expanded panel
shot 03-expanded

step "D-pad Down into the group selects Nav Bar"
key KEYCODE_DPAD_DOWN
check "Nav Bar has focus" focus_has "Nav Bar"
check "the pane shows Nav Bar" ui_has "Arrange the overlay nav bar and its Quick Launch button"
shot 04-section

step "D-pad Left to the header, Left again collapses"
key KEYCODE_DPAD_LEFT
check "Panel header has focus" focus_has "Panel"
key KEYCODE_DPAD_LEFT
check "Panel header shows Expand" ui_has "Expand Panel"
check "Nav Bar row is gone (the pane title still reads it once)" test "$(grep -o 'text="Nav Bar"' "$OUT_DIR/ui.xml" | wc -l | tr -d ' ')" -le 1
check "rail_expanded lacks panel" pref_lacks rail_expanded panel
shot 05-collapsed-selected-inside

step "A on the header toggles it"
key KEYCODE_BUTTON_A
check "A expands Panel" ui_has "Collapse Panel"
key KEYCODE_BUTTON_A
check "A collapses Panel" ui_has "Expand Panel"
key KEYCODE_BUTTON_A

step "Right on an open header goes to the pane, Left comes back to the selected section"
key KEYCODE_DPAD_RIGHT
check "focus left the rail" focus_lacks "Collapse Panel"
shot 06-pane
key KEYCODE_DPAD_LEFT
check "Nav Bar has focus again" focus_has "Nav Bar"

step "Expanded state survives a relaunch"
sh_dev am force-stop "$PKG"
launch
check "Panel still expanded" ui_has "Collapse Panel"
check "Look still collapsed" ui_has "Expand Look"
shot 07-relaunch

step "Touch: tap the Look header"
check "tapped Look's chevron" tap_node "Expand Look"
check "Look expanded" ui_has "Collapse Look"
check "Themes is on screen" ui_has "Themes"
shot 08-touch

# --- reorder inside a group -----------------------------------------------------------------------------------------

step "Move Nav Bar below Tiles inside Panel"
launch
key KEYCODE_DPAD_DOWN   # Panel header
key KEYCODE_DPAD_DOWN   # Nav Bar
check "Nav Bar has focus" focus_has "Nav Bar"
key KEYCODE_BUTTON_A
shot 09-picked-up
key KEYCODE_DPAD_DOWN
key KEYCODE_BUTTON_A
check "rail_sections_panel is tiles,nav_bar" test "$(pref rail_sections_panel)" = "tiles,nav_bar"
shot 10-moved

step "B drops a picked-up section and keeps the companion open"
key KEYCODE_BUTTON_A
key KEYCODE_DPAD_UP
key KEYCODE_BUTTON_B
check "rail_sections_panel back to nav_bar,tiles" test "$(pref rail_sections_panel)" = "nav_bar,tiles"
check "companion still in front on display 0" sh -c "adb shell dumpsys window displays | grep -q 'mFocusedApp=.*$PKG'"

echo
echo "Done: $passes passed, $fails failed. Screenshots in $OUT_DIR"
[ "$fails" -eq 0 ]
