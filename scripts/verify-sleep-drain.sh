#!/usr/bin/env bash
# Verifies #91 and #92: with the screen off, Command Panel stops recording and polling, so the Thor can reach deep
# sleep; on wake, both come back.
#   scripts/verify-sleep-drain.sh [--install] [--sleep-seconds N]
#
# Debug build only (jr.brian.commandpannel.debug). It never builds or installs unless --install is given, which builds
# the debug APK and installs it with `adb install -r` (data kept). Sleep and wake use KEYCODE_SLEEP / KEYCODE_WAKEUP
# (never the power key, which toggles), each after a focus guard: the release Command Panel must not have focus on
# either screen when a key goes out.
#
# What it checks:
#   before sleep  the debug Replay Buffer is armed (its `replay-drain` thread is up) and the pollers tick;
#   asleep        after N seconds (default 60): no `replay-drain` / `replay-audio-en` / `replay-compose` thread in the
#                 debug app, no `replay-audio` thread in the debug root helper, no `AudioIn` wake lock or active
#                 REMOTE_SUBMIX recording that the release build doesn't account for, no poller ticks, and the
#                 service logged `replay-pause: screen off -> DISARM` and `pollers paused`;
#   after wake    `replay capture armed` in the debug app's log, the drain thread back, `pollers running` and ticks.
#
# The release build (jr.brian.commandpannel) has its own Replay Buffer and root helper, and keeps recording while
# asleep until it updates. Both helpers run as root, so `dumpsys power` and `dumpsys audio` can't tell them apart by
# uid; the script tells them apart by process (each helper's command line names its package) and reports the release
# build's state on its own lines. A wake lock or REMOTE_SUBMIX recording held while the release helper is recording is
# reported as info (the release build's), never as a pass for the debug build: the debug build's proof is its own
# threads (no capture threads in the app, no `replay-audio` thread in its helper), which stay hard PASS/FAIL checks.
#
# Per-sample poller lines come from the debug-only PanelPoll tag, which logs only while
# `log.tag.PanelPoll` is VERBOSE; the script sets that and clears it on exit. If the debug Replay Buffer is off, it
# turns it on through the debug build's control broadcast and turns it off again on exit.
#
# Note: on USB the Thor charges, so it won't actually enter Doze during the run; the checks are about what would keep
# it awake (wake locks, capture, polling), which don't depend on that.
set -uo pipefail
cd "$(dirname "$0")/.."

install=false; sleep_s=60
while [ $# -gt 0 ]; do
  case "$1" in
    --install) install=true ;;
    --sleep-seconds) shift; sleep_s="${1:?--sleep-seconds needs a number}" ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

PKG="jr.brian.commandpannel.debug"
RELEASE_PKG="jr.brian.commandpannel"
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
PREFS="/data/user_de/0/$PKG/shared_prefs/command_panel.xml"
ACTION_SET_ENABLED="jr.brian.commandpannel.action.REPLAY_SET_ENABLED"
HELPER_CLASS="jr.brian.commandpannel.privileged.helper.RootHelperMain"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-sleep-drain)}"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
info() { echo "  info  $*"; }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
# `grep -qE` that reads all of its input first. Under pipefail, a plain `grep -q` that exits on its first match makes
# the producer (`adb shell | tr`) die of SIGPIPE, and the pipeline fails even though the line was there.
has() { local in; in="$(cat)"; grep -qE -- "$1" <<< "$in"; }
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 1; done; return 1; }
pref() { sh_dev run-as "$PKG" cat "$PREFS" | grep "name=\"$1\"" | grep -o 'value="[^"]*"' | cut -d'"' -f2; }
service_up() { sh_dev dumpsys activity services "$PKG" | has AssistantService; }
interactive() { sh_dev dumpsys power | has 'mWakefulness=Awake|Display Power: state=ON'; }
keyguard_up() { sh_dev dumpsys window | has 'isKeyguardShowing=true'; }

# ---- processes and threads -------------------------------------------------------------------------------------------

app_pid() { sh_dev pidof "$1" | awk '{ print $1 }'; }
# The root helper of a build: app_process running RootHelperMain with that build's package as its first argument.
helper_pid() {
  sh_dev ps -A -o PID,ARGS | awk -v c="$HELPER_CLASS" -v p="$1" '{ for (i = 1; i < NF; i++) if ($i == c && $(i + 1) == p) { print $1; exit } }'
}
# Thread names of a process (comm, at most 15 characters). This toybox has no TNAME; with -T, CMD is the thread name.
threads_of() { [ -n "$1" ] && sh_dev ps -T -p "$1" -o CMD | tail -n +2; }
has_thread() { threads_of "$1" | has "^$2"; }
# The capture threads: app side (drain, audio encoder, both-screens compositor) and the helper's audio pump.
app_capture_threads() { threads_of "$1" | grep -E '^(replay-drain|replay-audio-en|replay-compose)' | sort | uniq -c | xargs; }
helper_recording() { [ -n "$1" ] && has_thread "$1" "replay-audio"; }

# ---- what keeps the device awake --------------------------------------------------------------------------------------

audio_in_locks() { sh_dev dumpsys power | grep -E "PARTIAL_WAKE_LOCK +'AudioIn'"; }
# Active REMOTE_SUBMIX recordings, as "<count> <riids>". On this firmware `dumpsys audio` lists the live recordings
# under "RecordActivityMonitor dump time" as `riid 95; active? true` (no source there), and the event log below it
# has `rec update riid:95 uid:0 session:73 src:REMOTE_SUBMIX ...`, which gives each riid's source. Only distinct riids
# that the monitor lists as active count; the event log's history doesn't. The dump is saved to OUT_DIR.
submix_active() {
  local f="$OUT_DIR/dumpsys-audio-$1.txt"
  sh_dev dumpsys audio > "$f"
  awk '
    /^RecordActivityMonitor dump time/ { mon = 1; next }
    /^Audio event log/ { mon = 0 }
    mon && /^riid [0-9]+; active\? true/ { r = $2; sub(/;/, "", r); active[r] = 1 }
    /riid:[0-9]+/ && /src:/ {
      match($0, /riid:[0-9]+/); r = substr($0, RSTART + 5, RLENGTH - 5)
      match($0, /src:[A-Z_0-9]+/); src[r] = substr($0, RSTART + 4, RLENGTH - 4)
    }
    END {
      n = 0; list = ""
      for (r in active) if (src[r] == "REMOTE_SUBMIX") { n++; list = list " " r }
      print n list
    }' "$f"
}

# ---- logs (per process, so the release build's lines never count) -----------------------------------------------------

LOG_PID=""
log_clear() { adb logcat -c 2>/dev/null; }
log_dump() { [ -n "$LOG_PID" ] && adb logcat -d --pid="$LOG_PID" 2>/dev/null | tr -d '\r'; }
log_has() { log_dump | has "$1"; }
ticks() { log_dump | grep -cE 'PanelPoll.*tick '; }

# ---- focus guard -----------------------------------------------------------------------------------------------------

# Every display's focused window, as "displayId window".
focus_by_display() {
  sh_dev dumpsys window displays | awk '/Display: mDisplayId=/ { split($2, a, "="); cur = a[2] } /mCurrentFocus=/ { sub(/^ +/, ""); print cur, $0 }'
}
# False if the release Command Panel has focus on any display: a stray key there could change a real setting.
focus_safe() {
  local f; f="$(focus_by_display)"
  echo "$f" | sed 's/^/        focus d/'
  [ -z "$(grep -F "$RELEASE_PKG/" <<< "$f" | grep -vF "$PKG/")" ]
}
# Sends a key only after the focus guard; stops the run (without sending it) if the guard fails.
key() {
  if ! focus_safe; then
    fail "the release Command Panel has focus: $1 not sent, stopping"
    finish
  fi
  sh_dev input keyevent "$1" >/dev/null
}

# ---- setup and cleanup -----------------------------------------------------------------------------------------------

enabled_by_us=false
finishing=false
finish() {
  $finishing && exit 1
  finishing=true
  sh_dev setprop log.tag.PanelPoll "''" >/dev/null
  if ! interactive; then
    if focus_safe; then sh_dev input keyevent KEYCODE_WAKEUP >/dev/null; else echo "  (screen left off: release build has focus)"; fi
  fi
  if $enabled_by_us; then
    sh_dev am broadcast -a "$ACTION_SET_ENABLED" -p "$PKG" --ez enable false >/dev/null
    echo "  (debug Replay Buffer turned off again)"
  fi
  echo
  echo "$passes passed, $fails failed, $skips skipped  (dumps in $OUT_DIR)"
  [ "$fails" -eq 0 ]; exit $?
}

step "Setup"
[ "$(adb get-state 2>/dev/null)" = device ] || { echo "no device"; exit 2; }
sh_dev pm list packages "$PKG" | has "^package:$PKG\$" || { $install || { echo "debug build not installed (use --install)"; exit 2; }; }
if $install; then
  ./gradlew -q :app:assembleDebug || { echo "build failed"; exit 2; }
  adb install -r "$APP_APK" >/dev/null || { echo "install failed"; exit 2; }
  info "debug build installed"
fi
if ! interactive; then key KEYCODE_WAKEUP; sleep 2; fi
if ! service_up; then
  sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null
  wait_for 15 service_up || { fail "debug service didn't start"; finish; }
fi
PID="$(app_pid "$PKG")"; LOG_PID="$PID"
REL_PID="$(app_pid "$RELEASE_PKG")"
info "debug pid ${PID:-none}, release pid ${REL_PID:-not running}"
[ -n "$PID" ] || { fail "no debug process"; finish; }
sh_dev setprop log.tag.PanelPoll VERBOSE >/dev/null

if [ "$(pref replay_enabled)" != true ]; then
  info "debug Replay Buffer is off: turning it on for the run"
  sh_dev am broadcast -a "$ACTION_SET_ENABLED" -p "$PKG" --ez enable true >/dev/null
  enabled_by_us=true
fi

step "Before sleep"
check "debug Replay Buffer armed (replay-drain thread)" wait_for 15 "has_thread $PID replay-drain"
info "debug capture threads: $(app_capture_threads "$PID")"
HELPER="$(helper_pid "$PKG")"; REL_HELPER="$(helper_pid "$RELEASE_PKG")"
info "debug helper pid ${HELPER:-none}, release helper pid ${REL_HELPER:-none}"
if helper_recording "$HELPER"; then info "debug helper is capturing audio"; else info "debug helper isn't capturing audio (sound off, or no helper)"; fi
log_clear; sleep 5
t="$(ticks)"; check "pollers tick while the screen is on ($t in 5 s)" test "$t" -ge 3

step "Screen off for ${sleep_s} s"
log_clear
key KEYCODE_SLEEP
check "screen is off" wait_for 5 "! interactive"
check "replay paused for sleep" wait_for 10 "log_has 'replay-pause: screen off -> (DISARM|NONE).*asleep=true'"
check "pollers paused" wait_for 5 "log_has 'PanelPoll.*pollers paused'"
# Ticks already in flight as the screen went off don't count.
sleep 3; log_clear
sleep "$sleep_s"
interactive && { fail "the screen came back on by itself during the wait (results below may not mean much)"; }

t="$(ticks)"; check "no poller ticks while asleep ($t in ${sleep_s} s)" test "$t" -eq 0
th="$(app_capture_threads "$PID")"
check "no capture threads in the debug app (${th:-none})" test -z "$th"
if [ -n "$HELPER" ]; then
  check "debug helper isn't capturing audio (no replay-audio thread)" eval "! helper_recording $HELPER"
else
  skip "debug helper not running: its audio thread can't be checked"
fi

# The release build, reported on its own: its buffer still records while asleep until it gets this fix.
rel_recording=false; rel_drain=false
REL_HELPER="$(helper_pid "$RELEASE_PKG")"
helper_recording "$REL_HELPER" && rel_recording=true
[ -n "$REL_PID" ] && has_thread "$REL_PID" replay-drain && rel_drain=true
echo "  release build: Replay Buffer armed=$rel_drain, helper capturing audio=$rel_recording"

# Both helpers record as uid 0, so these system-wide signals can't name a build. The debug build's hard proof is the
# thread checks above; these pass when clean, are info while the release helper records (they're its), and fail only
# when nothing we know of explains them.
locks="$(audio_in_locks)"; echo "$locks" > "$OUT_DIR/audioin-asleep.txt"
n_locks="$(grep -c . <<< "$locks")"
read -r submix submix_riids <<< "$(submix_active asleep)"
info "AudioIn wake locks: $n_locks, active REMOTE_SUBMIX recordings: ${submix:-?}${submix_riids:+ (riid $submix_riids)}"
[ -n "$locks" ] && echo "$locks" | sed 's/^ */        /'
if [ "$n_locks" -eq 0 ]; then
  pass "no AudioIn wake lock"
elif $rel_recording; then
  info "AudioIn wake lock held while the release helper records: the release build's (not a debug check)"
else
  fail "AudioIn wake lock held and the release helper isn't recording: the debug build (or something else) holds it"
fi
if [ "${submix:-0}" -eq 0 ]; then
  pass "no active REMOTE_SUBMIX recording"
elif $rel_recording; then
  info "$submix active REMOTE_SUBMIX recording(s) while the release helper records: the release build's (not a debug check)"
else
  fail "$submix active REMOTE_SUBMIX recording(s) and the release helper isn't recording (see $OUT_DIR/dumpsys-audio-asleep.txt)"
fi
check "nothing armed the buffer while asleep" eval "! log_has 'replay capture armed'"

step "Wake"
log_clear
key KEYCODE_WAKEUP
check "screen is on" wait_for 5 interactive
keyguard_up && info "lock screen is up: not bypassed (re-arming doesn't need it)"
check "replay re-armed on wake (replay capture armed)" wait_for 15 "log_has 'replay capture armed'"
check "replay-drain thread back" wait_for 5 "has_thread $PID replay-drain"
check "pollers running again" wait_for 5 "log_has 'PanelPoll.*pollers running'"
sleep 4
t="$(ticks)"; check "pollers tick again (${t} since wake)" test "$t" -ge 3
check "Replay Buffer still on in the debug prefs" test "$(pref replay_enabled)" = true

finish
