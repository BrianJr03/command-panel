#!/usr/bin/env bash
# Verifies the What's new dialog's Tour button (#62) with the gamepad, on the top screen (display 0).
#   scripts/verify-whats-new-tour.sh [static|device|all] [--no-build] [--no-install]
#
# static  Compiles and runs the unit tests (WhatsNewTourTest covers the step lists, the version key and the Tour
#         button's condition).
# device  Debug build only (jr.brian.commandpannel.debug). Opens What's new through the debug-only
#         `--ez debug_whats_new true` extra, then:
#           - the dialog shows Got it and Tour, and Got it has the default focus;
#           - A closes it without starting a tour;
#           - opened again, D-pad Right moves to Tour and A starts the what's-new tour on "What's new in <version>";
#           - every stop: "i of N" is shown, a screenshot is taken, A moves on; B on the second stop goes back;
#           - A on the last stop ends it; the full tour's seen flag (companion_tour_seen) is unchanged and What's new
#             is marked seen for this version.
#         Before every key it checks that the debug companion is the resumed activity on display 0 and stops if not.
#         It never presses Back. Screenshots go to OUT_DIR.
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
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-whats-new-tour)}"

passes=0; fails=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

# --- static ---------------------------------------------------------------------------------------------------------

static_checks() {
  step "Build and unit tests"
  check ":app:compileDebugKotlin :app:testDebugUnitTest" ./gradlew -q :app:compileDebugKotlin :app:testDebugUnitTest
}

# --- device ---------------------------------------------------------------------------------------------------------

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

# The debug companion is the resumed activity on the top screen (display 0).
in_front() {
  sh_dev dumpsys activity activities | awk '/^Display #/{d=$2} d == "#0" && /topResumedActivity=/ && !p {print; p=1}' | grep -q "$PKG"
}
# Every key goes through here: it stops the run if anything but the debug companion would get it.
key() {
  if ! in_front; then
    fail "focus guard: the debug companion isn't in front on display 0 before $1; stopping"
    exit 1
  fi
  sh_dev input -d 0 keyevent "$1" >/dev/null
  sleep 0.9
}
shot() { adb exec-out screencap -d 0 -p > "$OUT_DIR/$1.png" 2>/dev/null || adb exec-out screencap -p > "$OUT_DIR/$1.png"; }

dump_ui() { sh_dev uiautomator dump /sdcard/verify-whats-new.xml >/dev/null; adb pull /sdcard/verify-whats-new.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
# Some node's text or content description contains $1 (uiautomator reads display 0; the guard keeps us on the debug app).
ui_has() { dump_ui; grep -q "\(text\|content-desc\)=\"[^\"]*$1" "$OUT_DIR/ui.xml"; }
ui_lacks() { ! ui_has "$1"; }
# Text inside the focused node (Compose reports focus on the focusable element).
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
wait_ui() { local i; for i in $(seq 1 "$2"); do ui_has "$1" && return 0; sleep 1; done; return 1; }

tour_seen() { sh_dev run-as "$PKG" cat "$PREFS" | grep -o 'name="companion_tour_seen" value="[a-z]*"' | grep -o 'value="[a-z]*"' | cut -d'"' -f2; }
whats_new_seen() { sh_dev run-as "$PKG" cat "$PREFS" | grep -o '<string name="whats_new_seen_version">[^<]*' | sed 's/.*>//'; }

open_whats_new() {
  sh_dev am start -W --display 0 -n "$COMPANION" --ez debug_whats_new true >/dev/null
  wait_ui "new in $VERSION" 10
}

device_checks() {
  step "Device"
  if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; return; fi
  mkdir -p "$OUT_DIR"
  echo "  screenshots: $OUT_DIR"

  step "Debug build"
  if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; return; }; fi
  if $install; then adb install -r "$APP_APK" >/dev/null || { fail "install"; return; }; fi
  VERSION="$(sh_dev dumpsys package "$PKG" | grep -m1 -o 'versionName=[^ ]*' | cut -d= -f2 | sed 's/-.*//')"
  [ -n "$VERSION" ] || { fail "debug build isn't installed"; return; }
  echo "  debug versionName base: $VERSION"
  local seen_before wn_before
  seen_before="$(tour_seen)"; wn_before="$(whats_new_seen)"
  echo "  before: companion_tour_seen=${seen_before:-unset} whats_new_seen_version=${wn_before:-unset}"

  step "What's new dialog"
  if ! open_whats_new; then fail "What's new didn't open"; return; fi
  if ui_has "Skip tour"; then fail "a tour is already running in the debug companion; finish it and run again"; return; fi
  shot whats-new-dialog
  check "dialog shows Got it" ui_has "Got it"
  check "dialog shows Tour" ui_has "Tour"
  check "Got it has the default focus" focus_has "Got it"

  key KEYCODE_BUTTON_A
  check "A on Got it closes the dialog" ui_lacks "new in $VERSION"
  check "and starts no tour" ui_lacks "Skip tour"

  step "Tour button"
  if ! open_whats_new; then fail "What's new didn't open again"; return; fi
  key KEYCODE_DPAD_RIGHT
  check "D-pad Right moves to Tour" focus_has "Tour"
  shot whats-new-tour-focused
  key KEYCODE_BUTTON_A
  if ! wait_ui "1 of " 5; then fail "the what's-new tour didn't start"; return; fi
  check "dialog closed" sh -c "! grep -q 'Got it' '$OUT_DIR/ui.xml'"
  check "first stop is What's new in $VERSION" ui_has "new in $VERSION"
  local total
  total="$(grep -oE 'text="1 of [0-9]+"' "$OUT_DIR/ui.xml" | grep -oE '[0-9]+"$' | tr -d '"')"
  [ -n "$total" ] || { fail "couldn't read the step count"; return; }
  echo "  the tour has $total stops"

  step "Stepping through"
  local i
  for i in $(seq 1 "$total"); do
    check "stop $i shows \"$i of $total\"" ui_has "$i of $total"
    shot "tour-stop-$i"
    if [ "$i" = 2 ]; then
      key KEYCODE_BUTTON_B
      check "B on stop 2 goes back to stop 1" ui_has "1 of $total"
      key KEYCODE_BUTTON_A
      check "A returns to stop 2" ui_has "2 of $total"
    fi
    key KEYCODE_BUTTON_A
    sleep 1.2   # the section switch and scroll for the next stop
  done
  check "A on the last stop ends the tour" ui_lacks "of $total"
  shot after-tour

  step "Prefs"
  local seen_after wn_after
  seen_after="$(tour_seen)"; wn_after="$(whats_new_seen)"
  echo "  after: companion_tour_seen=${seen_after:-unset} whats_new_seen_version=${wn_after:-unset}"
  check "the full tour's seen flag is unchanged" test "$seen_after" = "$seen_before"
  check "What's new is marked seen for $VERSION" test "$wn_after" = "$VERSION"
}

case "$stage" in
  static) static_checks ;;
  device) device_checks ;;
  all)    static_checks; device_checks ;;
esac

echo
echo "$passes passed, $fails failed"
[ "$fails" -eq 0 ]
