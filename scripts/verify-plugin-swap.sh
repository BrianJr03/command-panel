#!/usr/bin/env bash
# Verifies the Panel plugin swap (#19) on the connected Thor, against the debug build, using its debug-only
# DEBUG_PLUGIN_START / DEBUG_PLUGIN_END broadcasts.
#   scripts/verify-plugin-swap.sh [--install] [--untrusted] [--component <pkg/cls>]
#
# --install     Builds and installs the debug app first.
# --untrusted   Lets the debug start accept a plugin whose key isn't allowlisted (a debug-signed test plugin).
# --component   The plugin service to start (default: the first one Command Panel lists).
#
# Needs the debug build in control (not on standby) and a plugin installed (ThorKBM by default, PLUGIN_PKG to change).
# Checks: the swap starts and hides the panel; the bottom screen never has both or neither; one Command Panel overlay on
# the bottom display once the panel is back; the panel shows before MSG_HIDE; a second start mid-swap does nothing; a
# toggle ends the swap; the panel is back within 3 s of `am force-stop` on the plugin; the top screen keeps focus.
# The AYN button's broadcast is protected (the shell can't send it), so the toggle broadcast stands in for it: an AYN
# single press runs the same togglePanel path. The real button needs a hand.
# What it can't check: flicker during the handoff, feel, the real AYN button, and lid close/open.
set -uo pipefail
cd "$(dirname "$0")/.."

install=false; untrusted=false; component=""
while [ $# -gt 0 ]; do
  case "$1" in
    --install) install=true ;;
    --untrusted) untrusted=true ;;
    --component) shift; component="${1:-}" ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

PKG="jr.brian.commandpannel.debug"
PLUGIN_PKG="${PLUGIN_PKG:-jr.brian.thorkbm}"
[ -n "$component" ] && PLUGIN_PKG="${component%%/*}"
ACTION_PREFIX="jr.brian.commandpannel.action"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
device_now() { sh_dev date "'+%m-%d %H:%M:%S.000'"; }
plugin_log_since() { adb logcat -d -v brief -T "$1" -s PanelPlugin:I AssistantService:I 2>/dev/null | tr -d '\r'; }
service_running() { sh_dev dumpsys activity services "$PKG" | grep -q "ServiceRecord.*$PKG.*AssistantService"; }
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 0.5; done; return 1; }
broadcast() { sh_dev am broadcast -a "$ACTION_PREFIX.$1" -p "$PKG" "${@:2}" >/dev/null; }

# Windows with a surface on display $1 whose "package=" is $2; with $3 = overlay, only SYSTEM_ALERT_WINDOW ones (the
# panel; the black blanking cover counts too, but it's only up while a screen is blanked).
windows_on() {
  sh_dev dumpsys window windows | awk -v d="mDisplayId=$1" -v p="package=$2" -v overlay="${3:-}" '
    function flush() { if (inblk && disp && pkg && surf && (overlay == "" || saw)) n++ }
    /^  Window #/ { flush(); inblk = 1; disp = pkg = surf = saw = 0; next }
    inblk && index($0, d " ") { disp = 1 }
    inblk && index($0, p " ") { pkg = 1 }
    inblk && /appop=SYSTEM_ALERT_WINDOW/ { saw = 1 }
    inblk && /mHasSurface=true/ { surf = 1 }
    END { flush(); print n + 0 }'
}
panel_windows() { windows_on "$BOTTOM" "$PKG" overlay; }
plugin_windows() { windows_on "$BOTTOM" "$PLUGIN_PKG"; }
# Reads the whole dump first: with pipefail, `grep -m1` closing the pipe early would fail the check at random.
top_focus_is_0() {
  local dump; dump="$(sh_dev dumpsys window)"
  [ "$(echo "$dump" | grep -o 'mTopFocusedDisplayId=[0-9-]*' | head -1)" = "mTopFocusedDisplayId=0" ]
}
log_has() { echo "$LOG" | grep -q "$1"; }
# The first line matching $1 comes before the first matching $2.
log_order() {
  local a b
  a="$(echo "$LOG" | grep -n "$1" | head -1 | cut -d: -f1)"; b="$(echo "$LOG" | grep -n "$2" | head -1 | cut -d: -f1)"
  [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]
}

# Shows the panel, starts a swap and waits for the plugin to take the bottom screen. Returns non-zero if it didn't.
start_swap() {
  local t extras=()
  broadcast SHOW_OVERLAY
  wait_for 6 '[ "$(panel_windows)" = 1 ]' || { fail "panel didn't show before the swap"; return 1; }
  [ -n "$component" ] && extras+=(--es component "$component")
  $untrusted && extras+=(--ez allow_untrusted true)
  t="$(device_now)"
  broadcast DEBUG_PLUGIN_START ${extras[@]+"${extras[@]}"}
  if ! wait_for 12 'plugin_log_since "$t" | grep -q "shown: panel hidden"'; then
    LOG="$(plugin_log_since "$t")"; echo "$LOG" | sed 's/^/      /'
    fail "swap didn't start (no MSG_SHOWN within the timeout)"; return 1
  fi
  sleep 1  # the panel fades out over 200 ms before its window goes
  return 0
}

step "Device"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; echo; echo "$passes passed, $fails failed, $skips skipped"; exit 1; fi
BOTTOM="$(sh_dev dumpsys display | grep -oE "type=EXTERNAL, valid=true, isActive=true, displayId=[0-9]+" | head -1 | sed -E 's/.*displayId=//')"
[ -n "$BOTTOM" ] || { fail "couldn't find the bottom display"; exit 1; }
echo "  bottom display $BOTTOM, plugin package $PLUGIN_PKG"

if $install; then
  step "Install"
  ./gradlew -q :app:assembleDebug && adb install -r "$APP_APK" >/dev/null
  check "debug app installed and its service running" wait_for 30 service_running
fi
check "debug service running" service_running
check "$PLUGIN_PKG installed" sh -c "adb shell pm path $PLUGIN_PKG | grep -q package:"
if [ "$(sh_dev settings get global command_panel_stock_owner)" != "$PKG" ]; then
  echo "  NOTE  the debug build isn't the stock owner, so it may be on standby and refuse to swap; turn on"
  echo "        \"Replace stock assistant\" in the debug companion if the start fails"
fi

step "Swap in, then the debug end"
T0="$(device_now)"
if start_swap; then
  check "no Command Panel overlay on display $BOTTOM during the swap" test "$(panel_windows)" = 0
  check "the plugin has a window on display $BOTTOM" test "$(plugin_windows)" -ge 1
  check "top screen kept focus during the swap" top_focus_is_0
  t="$(device_now)"
  broadcast DEBUG_PLUGIN_END
  wait_for 6 '[ "$(panel_windows)" = 1 ]'
  LOG="$(plugin_log_since "$t")"
  check "exactly one Command Panel overlay on display $BOTTOM after the swap" test "$(panel_windows)" = 1
  check "panel shown before MSG_HIDE" log_order "panel shown" "MSG_HIDE sent"
  check "plugin window gone from display $BOTTOM" wait_for 6 '[ "$(plugin_windows)" = 0 ]'
fi

step "Second start mid-swap, then a toggle ends the swap (the AYN single press's path)"
if start_swap; then
  extras=()
  [ -n "$component" ] && extras+=(--es component "$component")
  $untrusted && extras+=(--ez allow_untrusted true)
  broadcast DEBUG_PLUGIN_START ${extras[@]+"${extras[@]}"}
  sleep 1.5
  check "second start: no panel over the plugin" test "$(panel_windows)" = 0
  check "second start: plugin still on display $BOTTOM" test "$(plugin_windows)" -ge 1
  t="$(device_now)"
  broadcast TOGGLE_OVERLAY
  wait_for 6 '[ "$(panel_windows)" = 1 ]'
  LOG="$(plugin_log_since "$t")"
  check "toggle: session ended" log_has "end .*PANEL_REQUESTED"
  check "toggle: one Command Panel overlay on display $BOTTOM" test "$(panel_windows)" = 1
  check "toggle: panel shown before MSG_HIDE" log_order "panel shown" "MSG_HIDE sent"
  check "toggle: plugin window gone from display $BOTTOM" wait_for 6 '[ "$(plugin_windows)" = 0 ]'
fi

step "Plugin killed mid-swap"
if start_swap; then
  t="$(device_now)"
  start_ms="$(date +%s)"
  sh_dev am force-stop "$PLUGIN_PKG"
  if wait_for 10 '[ "$(panel_windows)" = 1 ]'; then
    took=$(( $(date +%s) - start_ms ))
    check "panel back within 3 s of force-stop (took ~${took}s)" test "$took" -le 3
  else
    fail "panel not back after force-stop"
  fi
  LOG="$(plugin_log_since "$t")"
  check "force-stop: the binder death ended the session" log_has "disconnected\|binding died"
  check "force-stop: no MSG_HIDE to a dead plugin" sh -c "! echo \"\$0\" | grep -q 'MSG_HIDE sent'" "$LOG"
fi

step "Focus"
# A killed plugin's display can hold top focus for a moment while its task goes; give it a few seconds.
if wait_for 8 top_focus_is_0; then pass "top screen has focus at the end"
else fail "top screen has focus at the end"; sh_dev dumpsys window | grep -m1 -o 'mTopFocusedDisplayId=[0-9-]*' | sed 's/^/      /'; fi

step "Log"
plugin_log_since "$T0" | grep PanelPlugin | sed 's/^/      /'

broadcast HIDE_OVERLAY
echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
