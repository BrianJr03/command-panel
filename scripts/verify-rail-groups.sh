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
#   - A / D-pad / A moves a section inside its group (rail_sections_panel), and B drops without leaving the app;
#   - Y / D-pad / A moves a group (rail_group_order), every group folds while it's lifted, its sections keep their
#     order, B drops, A on a header still toggles it, and dragging a header's handle moves it by touch;
#   - the tour's Groups step spotlights a group header and lists the A / Y / A controls, and each section step opens
#     its group first (Panel, Look, Advanced); Y during the tour skips it and never moves a group.
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

# Command Panel is the focused app on the top screen (display 0).
in_front() {
  sh_dev dumpsys activity activities | awk '/^Display #/{d=$2} d == "#0" && /topResumedActivity=/ && !p {print; p=1}' | grep -q "$PKG"
}
# Drops rail_expanded so every group starts collapsed, checking the prefs file and retrying (after a force-stop) if
# the edit didn't stick.
clear_expanded() {
  local i
  for i in 1 2 3; do
    sh_dev am force-stop "$PKG"
    sleep 1
    sh_dev "run-as $PKG sed -i '/name=\"rail_expanded\"/d' $PREFS"
    [ -z "$(pref rail_expanded)" ] && ! sh_dev run-as "$PKG" cat "$PREFS" | grep -q 'name="rail_expanded"' && return 0
  done
  return 1
}
# Walks the tour with A until a step showing $1, stopping if the tour ends (so it never runs past the last step).
tour_to() {
  local i
  for i in $(seq 1 "${2:-20}"); do
    ui_has "$1" && return 0
    ui_has "Skip tour" || return 1
    key KEYCODE_BUTTON_A
  done
  ui_has "$1"
}

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
# Start from the default rail: no saved group or section order (the checks below expect Panel first, Nav Bar above
# Tiles). The user's prefs come back at the end either way.
sh_dev am force-stop "$PKG"
sh_dev "run-as $PKG sed -i -e '/name=\"rail_group_order\"/d' -e '/name=\"rail_sections_/d' -e '/name=\"rail_order\"/d' $PREFS"
check "rail starts in the default order" test -z "$(pref rail_group_order)"
check "rail_expanded cleared (every group collapsed)" clear_expanded

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
sh_dev am force-stop "$PKG"   # fresh start: General selected, focus on it
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
check "companion still in front on display 0" in_front

# --- moving groups (#30) --------------------------------------------------------------------------------------------

step "Y picks up Panel, every group folds while it's lifted, D-pad Down moves it below Look"
sh_dev am force-stop "$PKG"   # fresh start: General selected, focus on it
launch
key KEYCODE_DPAD_DOWN   # Panel header (Panel and Look are open from the steps above)
check "Panel header has focus" focus_has "Panel"
key KEYCODE_BUTTON_Y
check "while lifted, Panel shows collapsed" ui_has "Expand Panel"
check "while lifted, Look shows collapsed" ui_has "Expand Look"
check "while lifted, no section rows from Panel" test "$(grep -o 'text="Tiles"' "$OUT_DIR/ui.xml" | wc -l | tr -d ' ')" -eq 0
shot 11-group-lifted
key KEYCODE_DPAD_DOWN
key KEYCODE_BUTTON_A
check "rail_group_order starts look,panel" test "$(pref rail_group_order | cut -d, -f1-2)" = "look,panel"
check "Panel's own section order kept" test "$(pref rail_sections_panel)" = "nav_bar,tiles"
check "Panel and Look open again after the drop" ui_has "Collapse Panel"
check "focus stays on the Panel header" focus_has "Panel"
shot 12-group-moved

step "B drops a lifted group; Up can't move it above General"
key KEYCODE_BUTTON_Y
key KEYCODE_DPAD_UP
key KEYCODE_DPAD_UP     # already first: stays put, never above General
key KEYCODE_BUTTON_B
check "rail_group_order back to panel first" test "$(pref rail_group_order | cut -d, -f1)" = "panel"
check "focus stays on the Panel header" focus_has "Panel"
check "companion still in front on display 0" in_front

step "A on a header still opens and closes it (doesn't pick it up)"
key KEYCODE_BUTTON_A
check "A collapses Panel" ui_has "Expand Panel"
check "group order unchanged by A" test "$(pref rail_group_order | cut -d, -f1)" = "panel"
key KEYCODE_BUTTON_A

step "Touch: drag Look's handle below Extras"
# One slot is the gap between two collapsed headers (every group folds while one is dragged).
dump_ui
center_y() { grep -oE "<node [^>]*content-desc=\"$1\"[^>]*>" "$OUT_DIR/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ' | awk '{print int(($2 + $4) / 2)}'; }
tap_node "Collapse Panel" || true; tap_node "Collapse Look" || true
dump_ui
yp="$(center_y "Move Panel")"; yl="$(center_y "Move Look")"
if [ -n "$yp" ] && [ -n "$yl" ]; then
  slot=$(( yl - yp ))
  # Open both again, so Panel's sections fold away above Look as the drag starts.
  tap_node "Expand Panel" || true; tap_node "Expand Look" || true
  dump_ui
  b="$(grep -oE '<node [^>]*content-desc="Move Look"[^>]*>' "$OUT_DIR/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  set -- $b
  x=$(( ($1 + $3) / 2 )); y=$(( ($2 + $4) / 2 ))
  sh_dev input -d 0 swipe "$x" "$y" "$x" $(( y + slot * 5 / 4 )) 1200 >/dev/null
  sleep 1.5
  check "Look moved exactly one row, below Extras" test "$(pref rail_group_order)" = "panel,extras,look,advanced"
  shot 13-touch-group-drag
else
  fail "found the Panel and Look move handles"
fi

# --- the tour teaches the rail controls (#31) -----------------------------------------------------------------------

step "Tour: the Groups step spotlights a group header and lists A / Y / A"
# Collapse every group first, so the step has to open its own.
check "rail_expanded cleared (every group collapsed)" clear_expanded
launch
# "Take the tour" sits in General's pane; scroll the pane until it shows.
found=false
for i in 1 2 3 4 5 6; do
  if tap_node "Take the tour"; then
    # "Take the tour" first replays the overlay's onboarding on the bottom screen; the companion tour starts once
    # that ends. While it's up the companion forwards Y to it as Skip.
    for j in 1 2 3; do
      sleep 2
      ui_has "Skip tour" && { found=true; break; }
      key KEYCODE_BUTTON_Y
    done
    break
  fi
  sh_dev input -d 0 swipe 1200 900 1200 300 300 >/dev/null; sleep 0.6
done
check "started the tour (Skip shows)" $found
check "reached the Groups step" tour_to "Groups"
check "it lists A: open or close" ui_has "Open or close a group. D-pad Right and Left work too."
check "it lists Y: move a group" ui_has "On a group: pick it up, move it with the D-pad, then press A, Y or B to drop it."
check "it lists A: move a section" ui_has "On a section: pick it up and move it within its group, like Button Order."
# The tour overlay hides the rail from uiautomator, so the open groups are read from prefs.
check "the step opened Panel (its header is spotlit)" pref_has rail_expanded panel
shot 14-tour-groups-step

step "Tour: each section step opens its group before the spotlight"
key KEYCODE_BUTTON_A   # Nav Bar intro
check "reached Nav Bar's intro" ui_has "NAV BAR"
shot 15-tour-nav-bar
check "reached Appearance" tour_to "APPEARANCE"
check "reaching Appearance opened Look" pref_has rail_expanded look
shot 16-tour-look
check "reached Diagnostics" tour_to "DIAGNOSTICS" 30
check "reaching Diagnostics opened Advanced" pref_has rail_expanded advanced
shot 17-tour-diagnostics
order_before="$(pref rail_group_order)"
key KEYCODE_BUTTON_Y   # Skip ends the tour (Y is Skip while the tour is up, not a group pick-up)
check "tour closed" ui_lacks "Skip tour"
check "companion still in front after Skip" in_front
check "Y during the tour didn't move a group" test "$(pref rail_group_order)" = "$order_before"

step "Y where it has no job (on a section) does nothing"
sh_dev am force-stop "$PKG"
launch
check "General has focus" focus_has "General"
sh_dev logcat -c
key KEYCODE_BUTTON_Y
sleep 1
check "no moveTaskToBack" sh -c "! adb logcat -d | grep -q moveTaskToBack"
check "companion still in front" in_front
check "General still has focus" focus_has "General"

echo
echo "Done: $passes passed, $fails failed. Screenshots in $OUT_DIR"
[ "$fails" -eq 0 ]
