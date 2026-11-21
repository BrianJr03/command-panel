#!/usr/bin/env bash
# Verifies that What's new renders links, quotes and inline code (#53), on the top screen (display 0).
#   scripts/verify-whats-new-links.sh [static|device|all] [--no-build] [--no-install]
#
# static  Compiles and runs the unit tests (MarkdownParseTest parses the real patch_notes.md).
# device  Debug build only (jr.brian.commandpannel.debug). Opens What's new through the debug-only
#         `--ez debug_whats_new true` extra, then:
#           - no text node shows raw markup: no "[", "](", "**", backtick, or a leading ">";
#           - the previous-release link ("... notes") and the TCP SC quote ("About tcp-sc-") are shown;
#           - the link's node is focusable;
#           - D-pad Up from Got it reaches the link (scrolling as needed), and D-pad Down returns to Got it;
#           - A on Got it closes the dialog.
#         It never presses A on the link (that would leave for the browser) and never presses Back. Before every key
#         it checks that the debug companion is the resumed activity on display 0 and stops if not.
#         Screenshots go to OUT_DIR.
set -uo pipefail
cd "$(dirname "$0")/.."

stage="all"; build=true; install=true
for arg in "$@"; do
  case "$arg" in
    static|device|all) stage="$arg" ;;
    --no-build) build=false ;;
    --no-install) install=false; build=false ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-whats-new-links)}"

passes=0; fails=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

static_checks() {
  step "Build and unit tests"
  check ":app:compileDebugKotlin :app:testDebugUnitTest" ./gradlew -q :app:compileDebugKotlin :app:testDebugUnitTest
}

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
in_front() {
  sh_dev dumpsys activity activities | awk '/^Display #/{d=$2} d == "#0" && /topResumedActivity=/ && !p {print; p=1}' | grep -q "$PKG"
}
key() {
  if ! in_front; then
    fail "focus guard: the debug companion isn't in front on display 0 before $1; stopping"
    exit 1
  fi
  sh_dev input -d 0 keyevent "$1" >/dev/null
  sleep 0.9
}
# screencap -d takes SurfaceFlinger's physical display id, not the logical 0: use the first one listed (the top screen).
TOP_SF_ID=""
shot() {
  [ -n "$TOP_SF_ID" ] || TOP_SF_ID="$(sh_dev dumpsys SurfaceFlinger --display-id | awk 'NR==1 {print $2}')"
  adb exec-out screencap -d "$TOP_SF_ID" -p > "$OUT_DIR/$1.png" 2>/dev/null
  [ -s "$OUT_DIR/$1.png" ] || adb exec-out screencap -p > "$OUT_DIR/$1.png"
  [ -s "$OUT_DIR/$1.png" ] || echo "  (screenshot $1 is empty)"
}
dump_ui() { sh_dev uiautomator dump /sdcard/verify-whats-new-links.xml >/dev/null; adb pull /sdcard/verify-whats-new-links.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
ui_has() { dump_ui; grep -q "\(text\|content-desc\)=\"[^\"]*$1" "$OUT_DIR/ui.xml"; }
wait_ui() { local i; for i in $(seq 1 "$2"); do ui_has "$1" && return 0; sleep 1; done; return 1; }

# Runs a small check over the last dump: $1 is the mode, $2 an optional argument.
ui_py() {
  python3 -I - "$OUT_DIR/ui.xml" "$@" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
mode = sys.argv[2]
arg = sys.argv[3] if len(sys.argv) > 3 else ""
nodes = list(root.iter("node"))
texts = [(n.get("text") or "") for n in nodes] + [(n.get("content-desc") or "") for n in nodes]
if mode == "raw":
    bad = [t for t in texts if t and ("[" in t or "](" in t or "**" in t or "`" in t or t.lstrip().startswith(">"))]
    for t in bad: print("raw markup:", t[:120])
    sys.exit(1 if bad else 0)
def subtree(n): return " | ".join(filter(None, [d.get("text") or d.get("content-desc") for d in n.iter("node")]))
if mode == "focusable":
    # A focusable node whose own subtree holds the text (Compose reports focusability on the link's box).
    ok = any(n.get("focusable") == "true" and arg in subtree(n) and len(subtree(n)) < 200 for n in nodes)
    sys.exit(0 if ok else 1)
if mode == "focused":
    for n in nodes:
        if n.get("focused") == "true":
            print(subtree(n)); break
PY
}
focus_has() { dump_ui; ui_py focused | grep -q "$1"; }

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

  step "What's new dialog"
  sh_dev am start -W --display 0 -n "$COMPANION" --ez debug_whats_new true >/dev/null
  if ! wait_ui "new in $VERSION" 10; then fail "What's new didn't open"; return; fi
  shot whats-new-top
  check "Got it has the default focus" focus_has "Got it"

  step "Rendering"
  # The notes scroll: check the top, then scroll to the end and check again.
  dump_ui
  check "no raw markup at the top" ui_py raw
  local i
  for i in 1 2 3 4 5 6 7 8; do key KEYCODE_DPAD_DOWN; done
  shot whats-new-bottom
  dump_ui
  check "no raw markup at the bottom" ui_py raw
  check "the previous-release link shows as text" ui_has "notes for the previous release"
  check "the TCP SC note shows without its >" ui_has "About tcp-sc-"
  check "the link is focusable" ui_py focusable "previous release"
  check "Got it still has focus after scrolling" focus_has "Got it"

  step "Link focus"
  local found=false
  for i in 1 2 3 4 5 6 7 8 9 10; do
    key KEYCODE_DPAD_UP
    if focus_has "previous release"; then found=true; break; fi
  done
  check "D-pad Up reaches the link" $found
  shot whats-new-link-focused
  # From the link, Down should land on Got it within a press or two (it may scroll or step to another link first).
  local presses=0 back=false
  if $found; then
    for i in 1 2 3 4 5; do
      key KEYCODE_DPAD_DOWN
      presses=$i
      if focus_has "Got it"; then back=true; break; fi
    done
  fi
  echo "  Down presses from the link to Got it: $presses"
  check "D-pad Down returns to Got it within 2 presses" sh -c "$back && [ $presses -le 2 ]"
  # A only ever goes to Got it: on a link it would leave for the browser.
  if ! focus_has "Got it"; then
    fail "focus isn't on Got it; not pressing A (it could open a link). Close the dialog by hand."
    return
  fi
  key KEYCODE_BUTTON_A
  dump_ui
  check "A on Got it closes the dialog" sh -c "! grep -q 'Got it' '$OUT_DIR/ui.xml'"
}

case "$stage" in
  static) static_checks ;;
  device) device_checks ;;
  all)    static_checks; device_checks ;;
esac

echo
echo "$passes passed, $fails failed"
[ "$fails" -eq 0 ]
