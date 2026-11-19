#!/usr/bin/env bash
# Automated "hand test" for Panel plugin swaps (#18/#19) on the Thor: the real AYN button, real touches, the lid and a
# flicker check, against the debug build and a real (debug) ThorKBM. Every step ends in PASS/FAIL with screenshots of
# both displays.
#   scripts/verify-plugin-handtest.sh [--lid]
#
# --lid  Also runs the lid step. Off by default: on the Thor a lid close locks the device at once (no lock-after
#        delay), so with a PIN set the run stops at the lock screen and a person has to unlock it.
#
# Needs:
# - The debug build installed and in control of the AYN button ("Replace stock assistant" on in the debug companion,
#   so `command_panel_stock_owner` is jr.brian.commandpannel.debug). Otherwise the stock assistant answers the button
#   too and its panel covers ours.
# - The debug ThorKBM installed (PLUGIN_PKG / PLUGIN_COMPONENT to change), and ffmpeg/ffprobe plus Python 3 with numpy on
#   the Mac for the flicker step (skipped without them).
# - No lock screen showing. If one shows (the lid step), the script stops; it never tries to get past one.
#
# How it drives the device:
# - AYN button: KEY_F24 (194) on gpio-keys (/dev/input/event0) via sendevent. The firmware turns it into
#   `action.tcc.button.key.event` (checked in step 1). It counts a hold of ~400 ms as a long press (300 ms is short,
#   450 ms long); LONG_HOLD defaults to 1 s.
# - Lid: SW_LID on hall_switch (/dev/input/event4). A trap always sends SW_LID 0, so the lid is never left closed.
# - Touch: `input -d <bottom> tap` (it reaches the overlay). TILE_X/TILE_Y is the ThorKBM nav button, HIDE_X/HIDE_Y
#   ThorKBM's Hide key, in the bottom screen's 1240x1080 logical coordinates. The tile moves with the number of
#   installed plugins: check the first screenshot if step 2 fails.
#
# Eye-only: how a handoff feels, the panel's own fade, and anything about typing (needs ThorKBM's setup).
set -uo pipefail
cd "$(dirname "$0")/.."

with_lid=false
for arg in "$@"; do
  case "$arg" in
    --lid) with_lid=true ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
PLUGIN_PKG="${PLUGIN_PKG:-jr.brian.thorkbm.debug}"
PLUGIN_COMPONENT="${PLUGIN_COMPONENT:-$PLUGIN_PKG/jr.brian.thorkbm.feature.panel.plugin.PanelPluginService}"
PLUGIN_ACTIVITY="${PLUGIN_ACTIVITY:-$PLUGIN_PKG/jr.brian.thorkbm.MainActivity}"
ACTION_PREFIX="jr.brian.commandpannel.action"
AYN_DEV="/dev/input/event0"; AYN_CODE=194
LID_DEV="/dev/input/event4"; SW_LID=0
TILE_X="${TILE_X:-1025}"; TILE_Y="${TILE_Y:-220}"
HIDE_X="${HIDE_X:-1118}"; HIDE_Y="${HIDE_Y:-60}"
LONG_HOLD="${LONG_HOLD:-1.0}"
OUT="${OUT:-$(mktemp -d -t plugin-handtest)}"
mkdir -p "$OUT"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
device_now() { sh_dev date "'+%m-%d %H:%M:%S.000'"; }
logs_since() { adb logcat -d -v brief -T "$1" -s PanelPlugin:I AssistantService:D 2>/dev/null | tr -d '\r'; }
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 0.5; done; return 1; }
broadcast() { sh_dev am broadcast -a "$ACTION_PREFIX.$1" -p "$PKG" "${@:2}" >/dev/null; }

# --- device input -----------------------------------------------------------------------------------------------

ayn_press() { sh_dev "sendevent $AYN_DEV 1 $AYN_CODE 1; sendevent $AYN_DEV 0 0 0; sleep 0.1; sendevent $AYN_DEV 1 $AYN_CODE 0; sendevent $AYN_DEV 0 0 0"; }
ayn_hold() { sh_dev "sendevent $AYN_DEV 1 $AYN_CODE 1; sendevent $AYN_DEV 0 0 0; sleep $1; sendevent $AYN_DEV 1 $AYN_CODE 0; sendevent $AYN_DEV 0 0 0"; }
lid() { sh_dev "sendevent $LID_DEV 5 $SW_LID $1; sendevent $LID_DEV 0 0 0"; }
tap() { sh_dev input -d "$BOTTOM" tap "$1" "$2"; }

LID_TOUCHED=false
on_exit() {
  if $LID_TOUCHED; then lid 0; echo "  (lid reported open again)"; fi
}
trap on_exit EXIT

# --- device state -----------------------------------------------------------------------------------------------

# Surfaced windows on display $1, one per line: "<title> package=<pkg>".
windows_on() {
  sh_dev dumpsys window windows | awk -v d="mDisplayId=$1" '
    function flush() { if (title != "" && disp && surf) print title " " pkg }
    /^  Window #/ { flush(); title = $5; sub(/}:?$/, "", title); disp = surf = 0; pkg = ""; next }
    index($0, d " ") { disp = 1 }
    /package=/ { for (i = 1; i <= NF; i++) if ($i ~ /^package=/) pkg = $i }
    /mHasSurface=true/ { surf = 1 }
    END { flush() }'
}
panel_windows() { windows_on "$BOTTOM" | grep -c "^$PKG package=$PKG$"; }
# The plugin's windows on the bottom screen, matched by package and by title (ThorKBM titles its overlay ThorKbmPanel).
plugin_windows() { windows_on "$BOTTOM" | grep -cE "package=$PLUGIN_PKG$|^ThorKbm"; }
stock_windows() { windows_on "$BOTTOM" | grep -c "com.odin.dualscreen.assistant"; }
display_mode() { sh_dev settings get system dual_screen_display_mode; }
# Each reads the whole dump first: with pipefail, a grep that stops early (-q, -m1) would fail the check at random.
keyguard_showing() { local d; d="$(sh_dev dumpsys window)"; echo "$d" | grep 'isKeyguardShowing=' | grep -q 'isKeyguardShowing=true'; }
interactive() { local d; d="$(sh_dev dumpsys power)"; [ -n "$(echo "$d" | grep 'mWakefulness=Awake')" ]; }
log_has() { echo "$LOG" | grep -q "$1"; }
log_order() {
  local a b
  a="$(echo "$LOG" | grep -n "$1" | head -1 | cut -d: -f1)"; b="$(echo "$LOG" | grep -n "$2" | head -1 | cut -d: -f1)"
  [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]
}
shots() {
  adb exec-out screencap -p -d "$TOP_PHYS" > "$OUT/$1-top.png" 2>/dev/null
  adb exec-out screencap -p -d "$BOTTOM_PHYS" > "$OUT/$1-bottom.png" 2>/dev/null
  echo "  shots: $OUT/$1-top.png, $OUT/$1-bottom.png"
}
stop_if_locked() {
  if keyguard_showing; then
    fail "a lock screen is showing; stopping (never bypassed)"
    shots "locked"
    exit 1
  fi
}

# Panel up (the AYN press if it isn't), no session, no plugin window.
panel_up() {
  broadcast DEBUG_PLUGIN_END
  [ "$(panel_windows)" -ge 1 ] || ayn_press
  wait_for 8 '[ "$(panel_windows)" -ge 1 ]'
}
# Taps the ThorKBM tile and waits for the plugin to take the bottom screen.
tile_swap() {
  local t; t="$(device_now)"
  tap "$TILE_X" "$TILE_Y"
  wait_for 12 'logs_since "$t" | grep -q "shown: panel hidden"' || return 1
  sleep 1
}

# --- flicker ----------------------------------------------------------------------------------------------------

FLICKER_OK=false
if command -v ffmpeg >/dev/null && command -v ffprobe >/dev/null && python3 -c 'import numpy' 2>/dev/null; then
  FLICKER_OK=true
fi
REC_PID=""
rec_start() {
  $FLICKER_OK || return 0
  adb shell screenrecord --display-id "$BOTTOM_PHYS" --time-limit "${2:-5}" "/sdcard/Download/handtest-$1.mp4" >/dev/null 2>&1 &
  REC_PID=$!
  sleep 1.2
}
# Waits for the recording, pulls it and prints the flicker numbers; keeps "<name> <gap_ms>" in FLICKER_RESULTS.
FLICKER_RESULTS=""
rec_finish() {
  $FLICKER_OK || return 0
  wait "$REC_PID" 2>/dev/null
  adb pull "/sdcard/Download/handtest-$1.mp4" "$OUT/" >/dev/null 2>&1 && sh_dev rm "/sdcard/Download/handtest-$1.mp4"
  [ "$1" = bare ] && return 0
  local res; res="$(python3 -I scripts/plugin-flicker.py "$OUT/handtest-$1.mp4" "$OUT/handtest-bare.mp4")"
  echo "$res" | grep -v '^labels=' | sed 's/^/      /'
  echo "      frames: $(echo "$res" | grep '^labels=' | cut -d= -f2)  (B before, A after, _ bare, # black)"
  local gap black; gap="$(echo "$res" | grep '^gap_ms=' | cut -d= -f2)"; black="$(echo "$res" | grep '^black_frames=' | cut -d= -f2)"
  FLICKER_RESULTS="$FLICKER_RESULTS$1 gap_ms=$gap black_frames=$black"$'\n'
  check "$1: no black frames" test "${black:-1}" = 0
  check "$1: no frame with neither UI drawn" test "$(echo "$res" | grep '^gap_frames=' | cut -d= -f2)" = 0
}

# --- preflight --------------------------------------------------------------------------------------------------

step "Preflight"
if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; exit 1; fi
ids="$(sh_dev dumpsys display | grep -oE "displayId=[0-9]+, uniqueId='local:[0-9]+'" | sort -u)"
BOTTOM="$(echo "$ids" | grep -v 'displayId=0,' | head -1 | sed -E 's/displayId=([0-9]+).*/\1/')"
BOTTOM_PHYS="$(echo "$ids" | grep "displayId=$BOTTOM," | sed -E "s/.*local:([0-9]+)'/\1/")"
TOP_PHYS="$(echo "$ids" | grep 'displayId=0,' | sed -E "s/.*local:([0-9]+)'/\1/")"
echo "  bottom display $BOTTOM (physical $BOTTOM_PHYS), top physical $TOP_PHYS, output $OUT"
check "debug build owns the AYN button (stock owner is $PKG)" test "$(sh_dev settings get global command_panel_stock_owner)" = "$PKG"
check "$PLUGIN_PKG installed" sh -c "adb shell pm path $PLUGIN_PKG | grep -q package:"
check "screens on" interactive
stop_if_locked
$FLICKER_OK || skip "flicker: ffmpeg, ffprobe or numpy missing"
TOP_APP="$(sh_dev dumpsys window displays | awk '/Display: mDisplayId=/{d=$2} d == "mDisplayId=0" && /mFocusedApp=/{sub(/}$/, "", $3); print $3; exit}')"
broadcast DEBUG_PLUGIN_END; broadcast HIDE_OVERLAY; sleep 1
[ "$(display_mode)" = 0 ] || { ayn_press; sleep 1; }
rec_start bare 2; rec_finish bare
shots "00-start"

step "1. The AYN button is KEY_F24 on gpio-keys"
t="$(device_now)"
ayn_press
wait_for 8 '[ "$(panel_windows)" -ge 1 ]'
LOG="$(logs_since "$t")"
check "F24 reaches Command Panel as an AYN press" log_has "AYN button down=true long=false"
check "AYN key-up toggles the panel open" test "$(panel_windows)" = 1
check "the stock assistant didn't answer" test "$(stock_windows)" = 0
shots "01-ayn-opens-panel"

step "2. Tap the ThorKBM tile (real touch)"
t="$(device_now)"
rec_start swap-in
tap "$TILE_X" "$TILE_Y"
wait_for 12 'logs_since "$t" | grep -q "shown: panel hidden"'
sleep 1
rec_finish swap-in
LOG="$(logs_since "$t")"
check "the tile started the session (not the debug broadcast)" sh -c "echo \"\$0\" | grep -q 'start $PLUGIN_COMPONENT' && ! echo \"\$0\" | grep -q 'debug plugin start'" "$LOG"
check "MSG_SHOWN, then the panel hid" log_order "MSG_SHOWN" "shown: panel hidden"
check "no panel during the swap" test "$(panel_windows)" = 0
check "ThorKBM on the bottom screen" test "$(plugin_windows)" -ge 1
shots "02-tile-swap"

step "3. ThorKBM's Hide key brings the panel back"
t="$(device_now)"
rec_start hide-return
tap "$HIDE_X" "$HIDE_Y"
wait_for 8 '[ "$(panel_windows)" = 1 ]'
wait_for 6 '[ "$(plugin_windows)" = 0 ]'
rec_finish hide-return
LOG="$(logs_since "$t")"
check "MSG_CLOSED with CLOSED_BY_USER" log_has "MSG_CLOSED .*reason=0"
check "panel shown before MSG_HIDE" log_order "panel shown" "MSG_HIDE sent"
check "one panel" test "$(panel_windows)" = 1
check "ThorKBM gone" test "$(plugin_windows)" = 0
shots "03-hide-return"

step "4. Real AYN single press during a swap"
if tile_swap; then
  t="$(device_now)"
  rec_start ayn-return
  ayn_press
  wait_for 8 '[ "$(panel_windows)" = 1 ]'
  wait_for 6 '[ "$(plugin_windows)" = 0 ]'
  rec_finish ayn-return
  LOG="$(logs_since "$t")"
  check "session ended by the AYN press" log_has "end .*AYN_PRESS"
  check "panel shown before MSG_HIDE" log_order "panel shown" "MSG_HIDE sent"
  check "one panel" test "$(panel_windows)" = 1
  check "ThorKBM gone" test "$(plugin_windows)" = 0
else
  fail "swap didn't start"
fi
shots "04-ayn-return"

step "5. Real AYN long press during a swap (hold ${LONG_HOLD}s)"
if tile_swap; then
  t="$(device_now)"
  ayn_hold "$LONG_HOLD"
  sleep 1.5
  LOG="$(logs_since "$t")"
  check "the firmware sent a long press" log_has "AYN button down=true long=true"
  check "session ended by the long press" log_has "end .*AYN_LONG_PRESS"
  check "MSG_HIDE sent, panel not brought back first" sh -c "echo \"\$0\" | grep -q 'MSG_HIDE sent' && ! echo \"\$0\" | grep -q 'panel shown'" "$LOG"
  check "bottom screen blanked (display mode 1)" test "$(display_mode)" = 1
  check "ThorKBM gone" wait_for 6 '[ "$(plugin_windows)" = 0 ]'
  shots "05a-long-press-blanked"
  ayn_press
  check "an AYN press wakes the bottom screen (display mode 0)" wait_for 8 '[ "$(display_mode)" = 0 ]'
  sleep 1
  shots "05b-woken"
else
  fail "swap didn't start"
fi

step "6. Lid closed and opened during a swap"
if ! $with_lid; then
  skip "lid: run with --lid (it locks the device; a person unlocks it after)"
else
panel_up
if tile_swap; then
  t="$(device_now)"
  LID_TOUCHED=true
  lid 1
  sleep 2.5
  lid 0
  LID_TOUCHED=false
  sleep 1.5
  interactive || { sh_dev input keyevent KEYCODE_WAKEUP; sleep 1.5; }
  stop_if_locked
  wait_for 8 '[ "$(panel_windows)" = 1 ]'
  LOG="$(logs_since "$t")"
  check "screens back on" interactive
  check "screen off ended the session" log_has "end .*SCREEN_OFF"
  check "panel is there on reopen" test "$(panel_windows)" = 1
  check "ThorKBM is not" test "$(plugin_windows)" = 0
  check "lid reported open" sh -c "adb shell dumpsys window | grep -q 'mLidState=LID_OPEN'"
else
  fail "swap didn't start"
fi
shots "06-lid-reopen"
fi

step "7. ThorKBM opened elsewhere mid-swap, then a real AYN press"
panel_up
if tile_swap; then
  t="$(device_now)"
  sh_dev am start -W --display 0 -n "$PLUGIN_ACTIVITY" >/dev/null
  sleep 2
  LOG="$(logs_since "$t")"
  check "MSG_CLOSED with CLOSED_OPENED_ELSEWHERE" log_has "MSG_CLOSED .*reason=1"
  check "panel stays hidden" test "$(panel_windows)" = 0
  check "ThorKBM's own panel is up on the bottom screen" test "$(plugin_windows)" -ge 1
  shots "07a-opened-elsewhere"
  ayn_press
  wait_for 8 '[ "$(panel_windows)" = 1 ]'
  check "next AYN press shows the panel (over ThorKBM's standalone panel, expected)" test "$(panel_windows)" = 1
  echo "  (ThorKBM windows on the bottom screen now: $(plugin_windows))"
  shots "07b-ayn-over-standalone"
  # Clean up: the panel off, ThorKBM's standalone panel and bubble gone, the top screen back to what it was.
  ayn_press; sleep 1
  sh_dev am force-stop "$PLUGIN_PKG"
  [ -n "$TOP_APP" ] && sh_dev am start -W --display 0 -n "$TOP_APP" >/dev/null
else
  fail "swap didn't start"
fi

step "8. Flicker (worst gap per handoff)"
if $FLICKER_OK; then
  echo "${FLICKER_RESULTS%$'\n'}" | sed 's/^/  /'
  echo "  recordings: $OUT/handtest-*.mp4"
fi

step "End state"
broadcast DEBUG_PLUGIN_END
broadcast HIDE_OVERLAY
sleep 1
check "lid open" sh -c "adb shell dumpsys window | grep -q 'mLidState=LID_OPEN'"
check "screens on" interactive
check "bottom screen in dual mode" test "$(display_mode)" = 0
check "no panel showing" test "$(panel_windows)" = 0
check "no ThorKBM window anywhere" sh -c "! adb shell dumpsys window windows | grep -qE 'ThorKbm|u0 $PLUGIN_PKG/'"
shots "99-end"

echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
