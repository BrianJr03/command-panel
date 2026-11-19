#!/usr/bin/env bash
# Verifies Panel plugin swaps (#19, #21) with the fake plugin (:fakeplugin), so no ThorKBM is needed.
#   scripts/verify-plugin.sh [static|device|all]
#
# static  Builds the debug app and the fake plugin, runs the unit tests, and checks the fake plugin: its contract file
#         matches the app's, its manifest (three plugin services, versions 1 / 2 / none, the overlay permission), no
#         library dependencies, no release variant, and no release workflow that builds or uploads it. No device needed.
# device  On the connected Thor, against the debug build: installs the debug app and the fake plugin, grants the fake
#         plugin's overlay permission, then drives each mode through the debug-only DEBUG_PLUGIN_START / _END
#         broadcasts (allow_untrusted: the fake plugin is debug-signed):
#           normal       swap in; a second start while active; the debug end; a toggle (the AYN single press's
#                        path: the shell can't send the AYN button's protected broadcast); `am force-stop`
#           never_shown  the 3 s shown timeout keeps the panel and hides the plugin
#           late         same, then the late MSG_SHOWN (about 4 s) is ignored
#           crash        the plugin dies on MSG_SHOW: the panel stays, no MSG_HIDE to a dead plugin
#           close_after  the plugin closes itself after 3 s: the panel shows before MSG_HIDE
#           opened_elsewhere  the plugin says it was opened elsewhere after 3 s: the panel stays hidden
#           v2 / none    services declaring a future or no version aren't listed (debug builds list debuggable
#                        plugins, so the v1 service is) and the debug start refuses them
#         At each steady step: exactly one overlay (the panel or the plugin) on the bottom display, and the top screen
#         keeps focus. Screencaps of both displays go to OUT_DIR.
# What it can't check: flicker during the handoff, feel, typing into a game, the real AYN button, lid close/open and
# sleep mid-swap. Those
# need eyes and hands. Aloy's scripts/verify-plugin-swap.sh does the same swap checks against a real plugin (ThorKBM).
set -uo pipefail
cd "$(dirname "$0")/.."

stage="all"
for arg in "$@"; do
  case "$arg" in
    static|device|all) stage="$arg" ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
FAKE_PKG="jr.brian.commandpanel.fakeplugin"
FAKE_SERVICE="$FAKE_PKG/.FakePluginService"
ACTION_PREFIX="jr.brian.commandpannel.action"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
FAKE_APK="fakeplugin/build/outputs/apk/debug/fakeplugin-debug.apk"
APP_CONTRACT="app/src/main/java/jr/brian/commandpannel/plugin/PanelPluginContract.kt"
FAKE_CONTRACT="fakeplugin/src/main/java/jr/brian/commandpanel/fakeplugin/PanelPluginContract.kt"
OUT_DIR="${OUT_DIR:-/private/tmp/claude-501/-Users-brianjr-Desktop-Projects-command-panel/9023feb8-49a8-4907-9a19-1460598c4702/scratchpad/verify-plugin}"
SDK_DIR="$(grep '^sdk.dir=' local.properties 2>/dev/null | cut -d= -f2)"
SDK_DIR="${SDK_DIR:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
export ANDROID_HOME="$SDK_DIR"
BUILD_TOOLS="$(ls -d "$SDK_DIR"/build-tools/* 2>/dev/null | sort -V | tail -1)"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

# --- static ---------------------------------------------------------------------------------------------------------

# The fake plugin's compiled manifest, as aapt2 prints it.
fake_manifest() { "$BUILD_TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$FAKE_APK" 2>/dev/null; }

# The META_VERSION value on service $1 in the compiled manifest ("none" when it has no such meta-data).
service_version() {
  fake_manifest | awk -v svc="$FAKE_PKG.$1" '
    /E: service/ { insvc = 0 }
    /A: http:\/\/schemas.android.com\/apk\/res\/android:name/ && index($0, "\"" svc "\"") { insvc = 1 }
    insvc && /E: meta-data/ { inmeta = 1 }
    insvc && inmeta && /jr.brian.commandpanel.plugin.VERSION/ { want = 1 }
    insvc && want && /android:value/ { v = $0; sub(/.*=/, "", v); gsub(/[^0-9]/, "", v); print v; found = 1; exit }
    END { if (!found) print "none" }'
}

no_fake_libraries() {
  ! ./gradlew -q :fakeplugin:dependencies --configuration debugRuntimeClasspath | grep -E '^[+\\]--- ' | grep -v 'org.jetbrains'
}

static_checks() {
  step "Build and unit tests"
  check ":app:assembleDebug, :fakeplugin:assembleDebug, :app:testDebugUnitTest" \
    ./gradlew -q :app:assembleDebug :fakeplugin:assembleDebug :app:testDebugUnitTest

  step "Contract copy"
  check "fake plugin's PanelPluginContract.kt matches the app's except the package line" \
    diff <(tail -n +2 "$APP_CONTRACT") <(tail -n +2 "$FAKE_CONTRACT")

  step "Fake plugin APK"
  if [ ! -f "$FAKE_APK" ]; then fail "no APK at $FAKE_APK"; return; fi
  local badging; badging="$("$BUILD_TOOLS/aapt2" dump badging "$FAKE_APK")"
  check "package is $FAKE_PKG" sh -c "echo \"\$0\" | grep -q \"package: name='$FAKE_PKG'\"" "$badging"
  check "requests SYSTEM_ALERT_WINDOW and nothing else" test \
    "$(echo "$badging" | grep '^uses-permission' | sort)" = "uses-permission: name='android.permission.SYSTEM_ALERT_WINDOW'"
  check "three exported services answer the plugin action" \
    test "$(fake_manifest | grep -c '"jr.brian.commandpanel.plugin.action.PANEL_PLUGIN"')" = 3
  check "FakePluginService declares version 1" test "$(service_version FakePluginService)" = 1
  check "FutureVersionService declares version 2" test "$(service_version FutureVersionService)" = 2
  check "NoVersionService declares no version" test "$(service_version NoVersionService)" = none
  check "fake plugin has no library dependencies beyond the Kotlin stdlib" no_fake_libraries

  step "Never shipped"
  check "fake plugin has no release variant" \
    sh -c "! ./gradlew -q :fakeplugin:tasks --all 2>/dev/null | grep -qE '^(assemble|package)Release '"
  check "release workflow doesn't build or upload the fake plugin" sh -c "
    ! grep -q fakeplugin .github/workflows/build-release.yml &&
    ! grep -E 'gradlew' .github/workflows/build-release.yml | grep -vE ':app:|:trigger:' | grep -q 'assemble'"
  check "CI uploads no fake plugin APK" sh -c "! grep -q fakeplugin .github/workflows/android-ci.yml"
}

# --- device ---------------------------------------------------------------------------------------------------------

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
device_now() { sh_dev date "'+%m-%d %H:%M:%S.000'"; }
now_ms() { perl -MTime::HiRes=time -e 'printf "%d", time * 1000'; }
logs_since() { adb logcat -d -v brief -T "$1" -s PanelPlugin:I AssistantService:I FakePlugin:I 2>/dev/null | tr -d '\r'; }
# logs_since_has <time> <pattern>: reads the whole log first (with pipefail, `grep -q` closing a pipe early can fail).
logs_since_has() { local l; l="$(logs_since "$1")"; echo "$l" | grep -qE "$2"; }
service_running() { sh_dev dumpsys activity services "$PKG" | grep -q "ServiceRecord.*$PKG.*AssistantService"; }
# wait_for <tries> <command string>: polls every 0.25 s until the command succeeds.
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 0.25; done; return 1; }
broadcast() { sh_dev am broadcast -a "$ACTION_PREFIX.$1" -p "$PKG" "${@:2}" >/dev/null; }
set_mode() {
  local out; out="$(sh_dev am broadcast -n "$FAKE_PKG/.ModeReceiver" -f 0x20 --es mode "$1" ${2:+--el delay_ms "$2"})"
  echo "$out" | grep -q "data=\"$1\""
}

# SYSTEM_ALERT_WINDOW windows with a surface on display $1 whose "package=" is $2. Reads `dumpsys window windows`
# block by block; if the Thor prints these fields differently, this is the place to adjust.
windows_on() {
  sh_dev dumpsys window windows | awk -v d="mDisplayId=$1" -v p="package=$2" '
    function flush() { if (inblk && disp && pkg && surf && saw) n++ }
    /^  Window #/ { flush(); inblk = 1; disp = pkg = surf = saw = 0; next }
    inblk && index($0, d " ") { disp = 1 }
    inblk && index($0, p " ") { pkg = 1 }
    inblk && /appop=SYSTEM_ALERT_WINDOW/ { saw = 1 }
    inblk && /mHasSurface=true/ { surf = 1 }
    END { flush(); print n + 0 }'
}
panel_windows() { windows_on "$BOTTOM" "$PKG"; }
plugin_windows() { windows_on "$BOTTOM" "$FAKE_PKG"; }
# "<panel> <plugin>" overlay counts on the bottom display.
overlays() { echo "$(panel_windows) $(plugin_windows)"; }
# Reads the whole dump first: with pipefail, `grep -m1` closing the pipe early would fail the check at random.
top_focus_is_0() {
  local dump; dump="$(sh_dev dumpsys window)"
  [ "$(echo "$dump" | grep -o 'mTopFocusedDisplayId=[0-9-]*' | head -1)" = "mTopFocusedDisplayId=0" ]
}
top_focus() { local dump; dump="$(sh_dev dumpsys window)"; echo "$dump" | grep -o 'mTopFocusedDisplayId=[0-9-]*' | head -1; }
log_has() { echo "$LOG" | grep -q "$1"; }
log_lacks() { ! echo "$LOG" | grep -q "$1"; }
# The first line matching $1 comes before the first matching $2.
log_order() {
  local a b
  a="$(echo "$LOG" | grep -n "$1" | head -1 | cut -d: -f1)"; b="$(echo "$LOG" | grep -n "$2" | head -1 | cut -d: -f1)"
  [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]
}

# Ends the plugin drives (the shown timeout, a binder death, MSG_CLOSED) don't log an EndReason, only the event and
# the unbind, so those checks read the event line. Ends Command Panel drives log "end <key> (<phase>): <reason>".
# expect_overlays <label> <panel> <plugin>: the bottom display shows exactly what's expected, and the top keeps focus.
expect_overlays() {
  local got; got="$(overlays)"
  if [ "$got" = "$2 $3" ]; then pass "$1: panel $2, plugin $3 on display $BOTTOM"
  else fail "$1: expected panel $2, plugin $3 on display $BOTTOM, got panel ${got% *}, plugin ${got#* }"; fi
  # A killed plugin's display can hold top focus for a moment while its task goes; give it a couple of seconds.
  if wait_for 8 top_focus_is_0; then pass "$1: top screen has focus"; else fail "$1: top screen has focus ($(top_focus))"; fi
}

# Screencaps of both displays into OUT_DIR, named after the step.
snap() {
  local name="$1" id
  for id in top:"$TOP_PHYSICAL" bottom:"$BOTTOM_PHYSICAL"; do
    if [ -n "${id#*:}" ]; then adb exec-out screencap -d "${id#*:}" -p > "$OUT_DIR/$name-${id%%:*}.png" 2>/dev/null
    else adb exec-out screencap -p > "$OUT_DIR/$name-${id%%:*}.png" 2>/dev/null; fi
  done
}

# Puts the panel up, then starts the fake plugin service $1 (default the v1 one), as its tile would: no
# allow_untrusted, so only a listed plugin starts. Sets T to the start's log time.
start_plugin() {
  broadcast SHOW_OVERLAY
  wait_for 24 '[ "$(panel_windows)" = 1 ]' || fail "panel didn't show before the start"
  T="$(device_now)"
  broadcast DEBUG_PLUGIN_START --es component "${1:-$FAKE_SERVICE}"
}

# Starts a normal swap and waits for the plugin to own the bottom screen. Non-zero if it didn't.
swap_in() {
  start_plugin
  if ! wait_for 32 'logs_since_has "$T" "shown: panel hidden"'; then
    logs_since "$T" | sed 's/^/      /'
    fail "swap didn't start (no MSG_SHOWN)"; return 1
  fi
  sleep 1  # the panel fades out over 200 ms before its window goes
}

# After a session ended with the panel coming back: the panel's back, the plugin's window is gone, and the plugin
# removed it on MSG_HIDE (not only when its service was destroyed).
expect_panel_back() {
  wait_for 24 '[ "$(overlays)" = "1 0" ]'
  LOG="$(logs_since "$T")"
  expect_overlays "$1" 1 0
  if [ "${2:-hide}" = hide ]; then
    check "$1: panel shown before MSG_HIDE" log_order "panel shown" "MSG_HIDE sent"
    check "$1: plugin removed its window on MSG_HIDE" log_has "MSG_HIDE: window removed"
  else
    check "$1: no MSG_HIDE to a dead plugin" log_lacks "MSG_HIDE sent"
  fi
  check "$1: plugin never left its window up until unbind" log_lacks "window removed on destroy"
}

device_checks() {
  step "Device"
  if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; return; fi
  local ids top
  ids="$(sh_dev dumpsys display | grep -oE "type=EXTERNAL, valid=true, isActive=true, displayId=[0-9]+, uniqueId='local:[0-9]+'" | head -1)"
  top="$(sh_dev dumpsys display | grep -oE "type=INTERNAL, valid=true, isActive=true, displayId=0, uniqueId='local:[0-9]+'" | head -1)"
  BOTTOM="$(echo "$ids" | sed -E 's/.*displayId=([0-9]+).*/\1/')"
  BOTTOM_PHYSICAL="$(echo "$ids" | sed -E "s/.*local:([0-9]+)'/\1/")"
  TOP_PHYSICAL="$(echo "$top" | sed -E "s/.*local:([0-9]+)'/\1/")"
  [ -n "$BOTTOM" ] || { fail "couldn't find the bottom display"; return; }
  [ "$BOTTOM" = 4 ] || echo "  NOTE  bottom display is $BOTTOM, not the usual 4"
  mkdir -p "$OUT_DIR"
  echo "  bottom display $BOTTOM (physical $BOTTOM_PHYSICAL), top physical ${TOP_PHYSICAL:-?}, screencaps: $OUT_DIR"

  step "Install (debug app, fake plugin, overlay permission)"
  ./gradlew -q :app:assembleDebug :fakeplugin:assembleDebug || { fail "debug build"; return; }
  local t_install; t_install="$(device_now)"
  adb install -r "$APP_APK" >/dev/null
  check "debug app installed and its service running" wait_for 60 service_running
  adb install -r "$FAKE_APK" >/dev/null
  check "fake plugin installed" sh -c "adb shell pm path $FAKE_PKG | grep -q package:"
  sh_dev appops set "$FAKE_PKG" SYSTEM_ALERT_WINDOW allow
  check "fake plugin may draw overlays" sh -c "adb shell appops get $FAKE_PKG SYSTEM_ALERT_WINDOW | grep -q allow"
  if [ "$(sh_dev settings get global command_panel_stock_owner)" != "$PKG" ]; then
    echo "  NOTE  the debug build isn't the stock owner, so it may be on standby and refuse every start; turn on"
    echo "        \"Replace stock assistant\" in the debug companion if the starts fail"
  fi
  check "top screen has focus before the checks" top_focus_is_0

  step "normal: swap in"
  check "mode normal" set_mode normal
  if swap_in; then
    LOG="$(logs_since "$T")"
    check "normal: fake plugin got contract version 1" log_has "MSG_SHOW display=$BOTTOM version=1 mode=normal"
    expect_overlays "normal, active" 0 1
    snap normal-active

    step "normal: a second start while active is ignored"
    T="$(device_now)"
    broadcast DEBUG_PLUGIN_START --es component "$FAKE_SERVICE"
    sleep 1.5
    LOG="$(logs_since "$T")"
    check "second start: ignored while a session is busy" log_has "start ignored: a session is busy\|ignored: session"
    expect_overlays "second start" 0 1
    snap normal-second-start

    step "normal: the debug end"
    T="$(device_now)"
    broadcast DEBUG_PLUGIN_END
    expect_panel_back "debug end"
    check "debug end: session ended for DEBUG" log_has "end .*: DEBUG"
    snap normal-debug-end
  fi

  step "normal: a toggle ends the swap (the AYN single press's path)"
  if swap_in; then
    T="$(device_now)"
    broadcast TOGGLE_OVERLAY
    expect_panel_back "toggle"
    check "toggle: session ended for PANEL_REQUESTED" log_has "end .*PANEL_REQUESTED"
    snap normal-toggle
  fi

  step "normal: plugin force-stopped mid-swap"
  if swap_in; then
    local start took
    T="$(device_now)"
    start="$(now_ms)"
    sh_dev am force-stop "$FAKE_PKG"
    if wait_for 40 '[ "$(overlays)" = "1 0" ]'; then
      took=$(( $(now_ms) - start ))
      check "force-stop: panel back within 3 s (took ${took} ms, adb round trips included)" test "$took" -le 3000
    else
      fail "force-stop: panel not back"
    fi
    LOG="$(logs_since "$T")"
    expect_overlays "force-stop" 1 0
    check "force-stop: the binder death ended the session" log_has "disconnected\|binding died"
    check "force-stop: no MSG_HIDE to a dead plugin" log_lacks "MSG_HIDE sent"
    snap normal-force-stop
  fi

  step "never_shown: the shown timeout"
  check "mode never_shown" set_mode never_shown
  start_plugin
  if wait_for 32 'logs_since_has "$T" "never_shown mode: not replying"'; then
    # The plugin's window is up under or over the panel while Command Panel waits; the panel must not have left.
    check "never_shown, waiting: panel still up" test "$(panel_windows)" = 1
    check "never_shown, waiting: top screen has focus" top_focus_is_0
    snap never-shown-waiting
    wait_for 24 'logs_since_has "$T" "timed out while SHOWING"'
    expect_panel_back "never_shown"
    check "never_shown: session ended by the shown timeout" log_has "timed out while SHOWING"
    check "never_shown: unbound, panel owns the bottom screen" log_has "unbound $FAKE_PKG, owner=Panel"
    check "never_shown: panel never hidden" log_lacks "shown: panel hidden"
    snap never-shown-after
  else
    fail "never_shown: the fake plugin never got MSG_SHOW"; logs_since "$T" | sed 's/^/      /'
  fi

  step "late: MSG_SHOWN after the timeout"
  check "mode late (4000 ms)" set_mode late 4000
  start_plugin
  if wait_for 32 'logs_since_has "$T" "first frame drawn"'; then
    wait_for 24 'logs_since_has "$T" "timed out while SHOWING"'
    expect_panel_back "late, timed out"
    wait_for 24 'logs_since_has "$T" "late MSG_SHOWN sent"'
    sleep 1
    LOG="$(logs_since "$T")"
    check "late: the late MSG_SHOWN went out" log_has "late MSG_SHOWN sent"
    check "late: Command Panel didn't hide the panel for it" log_lacks "shown: panel hidden"
    expect_overlays "late, after the late reply" 1 0
    snap late-after
  else
    fail "late: the fake plugin never drew"; logs_since "$T" | sed 's/^/      /'
  fi

  step "crash: the plugin dies on MSG_SHOW"
  check "mode crash" set_mode crash
  start_plugin
  if wait_for 32 'logs_since_has "$T" "disconnected|binding died|null binding"'; then
    expect_panel_back "crash" died
    check "crash: session ended by the binder death" log_has "$FAKE_PKG disconnected\|$FAKE_PKG binding died"
    check "crash: unbound, panel owns the bottom screen" log_has "unbound $FAKE_PKG, owner=Panel"
    check "crash: panel never hidden" log_lacks "shown: panel hidden"
    snap crash
  else
    fail "crash: no binder death seen"; logs_since "$T" | sed 's/^/      /'
  fi

  step "close_after: the plugin closes itself after 3 s"
  check "mode close_after (3000 ms)" set_mode close_after 3000
  if swap_in; then
    expect_overlays "close_after, active" 0 1
    wait_for 32 'logs_since_has "$T" "MSG_CLOSED from"'
    expect_panel_back "close_after"
    check "close_after: session ended by MSG_CLOSED (by user)" log_has "MSG_CLOSED from $FAKE_PKG .* reason=0"
    check "close_after: unbound, panel owns the bottom screen" log_has "unbound $FAKE_PKG, owner=Panel"
    snap close-after
  fi

  step "opened_elsewhere: the plugin's UI was opened outside Command Panel"
  check "mode opened_elsewhere (3000 ms)" set_mode opened_elsewhere 3000
  if swap_in; then
    wait_for 32 'logs_since_has "$T" "MSG_CLOSED from"'
    sleep 1.5
    LOG="$(logs_since "$T")"
    check "opened_elsewhere: session ended by MSG_CLOSED (opened elsewhere)" log_has "MSG_CLOSED from $FAKE_PKG .* reason=1"
    check "opened_elsewhere: unbound" log_has "unbound $FAKE_PKG, owner=Panel"
    check "opened_elsewhere: panel not brought back" log_lacks "panel shown"
    check "opened_elsewhere: MSG_HIDE sent and ignored by the plugin" log_has "MSG_HIDE ignored: opened elsewhere"
    expect_overlays "opened_elsewhere" 0 1
    snap opened-elsewhere
  fi
  # Leaves normal mode (which removes the leftover window), then the panel comes back as on the next AYN press.
  check "mode normal (clears the leftover window)" set_mode normal
  wait_for 12 '[ "$(plugin_windows)" = 0 ]'
  check "no fake window left" test "$(plugin_windows)" = 0

  step "Listed plugins (debug builds list debuggable plugins)"
  start_plugin "$FAKE_SERVICE"
  if wait_for 32 'logs_since_has "$T" "shown: panel hidden"'; then
    pass "v1 service is listed: the tile's start path found it"
    T="$(device_now)"; broadcast DEBUG_PLUGIN_END; expect_panel_back "listed v1, ended"
  else
    fail "v1 service isn't listed"; logs_since "$T" | sed 's/^/      /'
  fi

  step "Future and missing versions"
  local svc
  for svc in FutureVersionService NoVersionService; do
    start_plugin "$FAKE_PKG/.$svc"
    sleep 2
    LOG="$(logs_since "$T")"
    check "$svc: debug start refused" log_has "debug plugin start: .*$svc.* found=null"
    check "$svc: no session started" log_lacks "PanelPlugin.*start .*$svc"
    check "$svc: never bound" log_lacks "bound $svc"
    expect_overlays "$svc" 1 0
    snap "version-$svc"
  done
  # Also with allow_untrusted: the version check must refuse them even when the key check is skipped.
  for svc in FutureVersionService NoVersionService; do
    T="$(device_now)"
    broadcast DEBUG_PLUGIN_START --es component "$FAKE_PKG/.$svc" --ez allow_untrusted true
    sleep 2
    LOG="$(logs_since "$T")"
    check "$svc: refused with allow_untrusted too" log_has "debug plugin start: .*$svc.* found=null"
  done
  # The registry logs its list whenever it changes (the installs above): the v1 service may show, the others never.
  LOG="$(logs_since "$t_install" | grep 'plugins:')"
  echo "$LOG" | sed 's/^/      /'
  check "no tile for the future or missing version services" sh -c "! echo \"\$0\" | grep -qE 'FutureVersionService|NoVersionService'" "$LOG"

  step "Focus"
  if wait_for 8 top_focus_is_0; then pass "top screen has focus at the end"; else fail "top screen has focus at the end ($(top_focus))"; fi
  set_mode normal >/dev/null

  broadcast HIDE_OVERLAY
  echo "  screencaps: $OUT_DIR"
}

case "$stage" in
  static) static_checks ;;
  device) device_checks ;;
  all)    static_checks; device_checks ;;
esac

echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
