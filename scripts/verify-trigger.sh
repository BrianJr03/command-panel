#!/usr/bin/env bash
# Verifies TCP Save Clip (the Replay Buffer shortcut) end to end.
#   scripts/verify-trigger.sh [static|device|all] [--take-control] [--record]
#
# static  Builds, unit tests, lint (fails only on errors in files this branch changed), the merged manifest and the
#         trigger APK's permissions. No device needed.
# device  On the connected Thor, against the debug build (the only one with this branch's code): installs the debug
#         app and trigger, then checks the permission grant, shell and wrong-key rejection, a launch on the bottom
#         screen (focus, audio focus, recents), standby, the "not running" path and the double press.
#   --take-control  Hands "Replace stock assistant" to the debug build so the arm/save and double-press checks can
#                   run, then gives it back to the build that had it and restores the debug Replay Buffer state.
#                   Without it, a debug build on standby only gets the standby checks.
#   --record        Screen-records the bottom screen during the first launch, to check by eye that nothing flashes.
set -uo pipefail
cd "$(dirname "$0")/.."

stage="all"; take_control=false; record=false
for arg in "$@"; do
  case "$arg" in
    static|device|all) stage="$arg" ;;
    --take-control) take_control=true ;;
    --record) record=true ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
RELEASE_PKG="jr.brian.commandpannel"
TRIGGER_PKG="jr.brian.tcp.saveclip"
TRIGGER_COMPONENT="$TRIGGER_PKG/.SaveClipActivity"
ACTION="$PKG.action.SAVE_OR_ARM_REPLAY"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TRIGGER_APK="trigger/build/outputs/apk/debug/trigger-debug.apk"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-trigger)}"
SDK_DIR="$(grep '^sdk.dir=' local.properties | cut -d= -f2)"
BUILD_TOOLS="$(ls -d "$SDK_DIR"/build-tools/* | sort -V | tail -1)"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

# Top-level runtime dependencies of the trigger, other than what AGP's built-in Kotlin adds.
no_trigger_libraries() {
  ! ./gradlew -q :trigger:dependencies --configuration releaseRuntimeClasspath | grep -E '^[+\\]--- ' | grep -v 'org.jetbrains'
}

# --- static ---------------------------------------------------------------------------------------------------------

static_checks() {
  step "Build, unit tests, trigger lint"
  check "assembleDebug, :trigger:assembleRelease, :app:testDebugUnitTest, :trigger:lintDebug" \
    ./gradlew -q assembleDebug :trigger:assembleRelease :app:testDebugUnitTest :trigger:lintDebug

  step "App lint (errors on lines this branch changed)"
  ./gradlew -q :app:lintDebug >/dev/null 2>&1
  local report="app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt"
  if [ ! -f "$report" ]; then
    fail "no lint report at $report"
  else
    # "<path>:<line>:" for every line added or changed since main, matched against the report's "<path>:<line>: Error".
    local changed ours
    changed="$(git diff -U0 "$(git merge-base HEAD main)" -- app | awk -v root="$PWD" '
      /^\+\+\+ b\//{ f = root "/" substr($0, 7) }
      /^@@/{ split($3, a, ","); start = substr(a[1], 2); n = (a[2] == "" ? 1 : a[2]); for (i = 0; i < n; i++) print f ":" start + i ":" }')"
    ours="$(grep -E '^/.*: Error:' "$report" | grep -F -f <(echo "$changed") || true)"
    echo "  (app lint total: $(tail -1 "$report"))"
    if [ -z "$ours" ]; then pass "no lint errors on changed app lines"; else fail "lint errors on changed lines:"; echo "$ours"; fi
  fi

  step "Merged debug manifest"
  local merged="app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
  check "declares $PKG.permission.TRIGGER as signature-level" \
    sh -c "grep -A2 'android:name=\"$PKG.permission.TRIGGER\"' '$merged' | grep -q 'protectionLevel=\"signature\"'"
  check "receiver requires the permission and filters $ACTION" \
    sh -c "grep -A6 'ExternalTriggerReceiver' '$merged' | grep -q 'android:permission=\"$PKG.permission.TRIGGER\"' &&
           grep -A6 'ExternalTriggerReceiver' '$merged' | grep -q '$ACTION'"

  step "Trigger APKs"
  local apk perms
  for apk in "$TRIGGER_APK" trigger/build/outputs/apk/release/trigger-release*.apk; do
    perms="$("$BUILD_TOOLS/aapt2" dump badging "$apk" | grep '^uses-permission' | sort)"
    check "$(basename "$apk") requests only the two TRIGGER permissions" test "$perms" = "$(printf '%s\n' \
      "uses-permission: name='jr.brian.commandpannel.debug.permission.TRIGGER'" \
      "uses-permission: name='jr.brian.commandpannel.permission.TRIGGER'")"
  done
  check "trigger has no library dependencies beyond the Kotlin stdlib" no_trigger_libraries
}

# --- device ---------------------------------------------------------------------------------------------------------

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
device_now() { sh_dev date "'+%m-%d %H:%M:%S.000'"; }
logs_since() { adb logcat -d -v brief -T "$1" 2>/dev/null | tr -d '\r'; }
service_running() { sh_dev dumpsys activity services "$PKG" | grep -q "ServiceRecord.*$PKG.*AssistantService"; }
# wait_for <seconds> <command string>: polls once a second until the command succeeds.
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 1; done; return 1; }
owner() { sh_dev settings get global command_panel_stock_owner; }
debug_pref() { sh_dev run-as "$PKG" cat "/data/user_de/0/$PKG/shared_prefs/command_panel.xml" | grep "name=\"$1\"" | grep -o 'value="[a-z]*"' | cut -d'"' -f2; }
granted() { sh_dev dumpsys package "$TRIGGER_PKG" | grep -q "$PKG.permission.TRIGGER: granted=true"; }
cert_digest() { "$BUILD_TOOLS/apksigner" verify --print-certs "$1" | grep -m1 'SHA-256' | awk '{print $NF}'; }

# Focus per display (sorted: dumpsys doesn't list displays in a fixed order), which display has top focus, and the
# audio focus holder.
snapshot() {
  sh_dev dumpsys window displays | awk '/Display: mDisplayId=/{d=$2} /mFocusedApp=/{print "focus " d " " $3 " " $4}' | sort
  sh_dev dumpsys window | grep -m1 -o 'mTopFocusedDisplayId=[0-9-]*'
  sh_dev dumpsys audio | awk '/Audio Focus stack entries/{f=1; next} f && /source:/{top=$0} f && /^$/{exit}
    END{n=split(top, a, " -- "); for (i = 1; i <= n; i++) if (a[i] ~ /^pack:/) print "audio-focus " a[i]}'
}

# Launches the trigger on the bottom screen; prints the log lines it caused. Sets LAUNCH_LOG and checks focus/audio.
launch_trigger() {
  local label="$1" before after t
  before="$(snapshot)"
  if ! echo "$before" | grep -q 'mTopFocusedDisplayId=0$'; then
    echo "  NOTE  the top screen didn't have focus before this launch, so the focus check is weak; for the real test,"
    echo "        put a game on the top screen and touch it before running"
  fi
  t="$(device_now)"
  sh_dev am start -W --display "$BOTTOM" -n "$TRIGGER_COMPONENT" >/dev/null
  sleep 3
  LAUNCH_LOG="$(logs_since "$t" | grep -E 'ExternalTrigger|save-or-arm|replay save \(|Permission Denial.*SAVE_OR_ARM_REPLAY')"
  after="$(snapshot)"
  echo "  log ($label):"; echo "$LAUNCH_LOG" | sed 's/^/      /'
  if [ "$before" = "$after" ]; then
    pass "$label: focus on every display, top-focused display and audio focus unchanged"
  else
    fail "$label: focus or audio changed"; diff <(echo "$before") <(echo "$after") | sed 's/^/      /'
  fi
  check "$label: activity is gone" sh -c "! adb shell dumpsys activity activities | grep -q SaveClipActivity"
  check "$label: not in recents" sh -c "! adb shell dumpsys activity recents | grep -q $TRIGGER_PKG"
}

log_has() { echo "$LAUNCH_LOG" | grep -q "$1"; }

install_app() {
  adb install -r "$APP_APK" >/dev/null && wait_for 15 service_running
}

install_trigger() {
  local apk="$1"
  adb install -r "$apk" >/dev/null 2>&1 || { adb uninstall "$TRIGGER_PKG" >/dev/null; adb install "$apk" >/dev/null; }
}

ORIGINAL_OWNER=""; ORIGINAL_REPLAY=""; TOOK_CONTROL=false
restore() {
  if $TOOK_CONTROL; then
    step "Restoring"
    if [ "$ORIGINAL_REPLAY" != "true" ]; then
      sh_dev am broadcast -a jr.brian.commandpannel.action.REPLAY_SET_ENABLED -p "$PKG" --ez enable false >/dev/null
      echo "  debug Replay Buffer turned back off"
    fi
    if [ "$ORIGINAL_OWNER" = "$RELEASE_PKG" ]; then
      sh_dev am broadcast -a jr.brian.commandpannel.action.SET_REPLACE_STOCK -p "$RELEASE_PKG" --ez enable true >/dev/null
      if wait_for 15 '[ "$(owner)" = "$RELEASE_PKG" ]'; then echo "  stock replacement handed back to $RELEASE_PKG"
      else echo "  WARNING: $RELEASE_PKG didn't take the stock replacement back; turn it on in its companion"; fi
    fi
    TOOK_CONTROL=false
  fi
}
trap restore EXIT

device_checks() {
  step "Device"
  if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; return; fi
  local ids
  ids="$(sh_dev dumpsys display | grep -oE "type=EXTERNAL, valid=true, isActive=true, displayId=[0-9]+, uniqueId='local:[0-9]+'" | head -1)"
  BOTTOM="$(echo "$ids" | sed -E 's/.*displayId=([0-9]+).*/\1/')"
  BOTTOM_PHYSICAL="$(echo "$ids" | sed -E "s/.*local:([0-9]+)'/\1/")"
  [ -n "$BOTTOM" ] || { fail "couldn't find the bottom display"; return; }
  ORIGINAL_OWNER="$(owner)"
  echo "  bottom display $BOTTOM (physical $BOTTOM_PHYSICAL), stock owner: $ORIGINAL_OWNER, output: $OUT_DIR"

  step "Builds (wrong-key trigger first, then the normal debug pair)"
  local tmp; tmp="$(mktemp -d)"
  keytool -genkeypair -keystore "$tmp/wrong.jks" -storepass wrongkey -keypass wrongkey -alias wrong -keyalg RSA \
    -keysize 2048 -validity 1 -dname "CN=wrong key" >/dev/null 2>&1
  ./gradlew -q :trigger:assembleDebug -Pandroid.injected.signing.store.file="$tmp/wrong.jks" \
    -Pandroid.injected.signing.store.password=wrongkey -Pandroid.injected.signing.key.alias=wrong \
    -Pandroid.injected.signing.key.password=wrongkey && cp "$TRIGGER_APK" "$tmp/trigger-wrong-key.apk"
  ./gradlew -q :app:assembleDebug :trigger:assembleDebug || { fail "debug build"; return; }
  check "wrong-key trigger has a different certificate" \
    test "$(cert_digest "$tmp/trigger-wrong-key.apk")" != "$(cert_digest "$TRIGGER_APK")"

  step "Install (app first, then trigger)"
  check "debug app installed and its service running" install_app
  install_trigger "$TRIGGER_APK"
  check "trigger holds $PKG.permission.TRIGGER" granted
  ORIGINAL_REPLAY="$(debug_pref replay_enabled)"

  step "Shell can't send the trigger"
  local t; t="$(device_now)"
  sh_dev am broadcast -a "$ACTION" -p "$PKG" >/dev/null
  sleep 2
  LAUNCH_LOG="$(logs_since "$t" | grep -E 'ExternalTrigger|save-or-arm|Permission Denial.*SAVE_OR_ARM_REPLAY')"
  check "shell broadcast denied" log_has "Permission Denial"
  check "receiver never ran for the shell" sh -c "! echo \"\$0\" | grep -q '/ExternalTrigger('" "$LAUNCH_LOG"

  if $take_control && [ "$ORIGINAL_OWNER" != "$PKG" ]; then
    step "Taking control (debug build replaces stock)"
    sh_dev am broadcast -a jr.brian.commandpannel.action.SET_REPLACE_STOCK -p "$PKG" --ez enable true >/dev/null
    if wait_for 15 '[ "$(owner)" = "$PKG" ]'; then TOOK_CONTROL=true; sleep 3; pass "debug build owns stock"
    else fail "debug build didn't take control"; fi
  fi

  if [ "$(owner)" = "$PKG" ]; then
    step "Launch on the bottom screen (in control)"
    local rec=""
    if $record; then
      rec="/sdcard/Download/verify-trigger-bottom.mp4"
      adb shell screenrecord --display-id "$BOTTOM_PHYSICAL" --time-limit 6 "$rec" &
      sleep 1.5
    fi
    launch_trigger "first launch"
    if [ -n "$rec" ]; then wait; adb pull "$rec" "$OUT_DIR/" >/dev/null && adb shell rm "$rec"
      echo "  recording: $OUT_DIR/$(basename "$rec") (check by eye for a flash)"; fi
    check "forwarded to the running service" log_has "forwarded to the service"
    if log_has "save-or-arm (external): ARM"; then
      pass "unarmed buffer armed"
      sleep 4  # let a few seconds buffer before saving
      launch_trigger "second launch"
    fi
    check "armed buffer saved" log_has "save-or-arm (external): SAVE"
    check "save succeeded" sh -c "echo \"\$0\" | grep 'replay save (external)' | grep -vq 'error:'" "$LAUNCH_LOG"

    step "AYN double press (simulated)"
    if [ "$(debug_pref ayn_double_press_replay)" = "true" ]; then
      t="$(device_now)"
      sh_dev "for d in true false true false; do am broadcast -a action.tcc.button.key.event -p $PKG --ez key_action_down \$d --ez key_isLongPress false >/dev/null; done"
      sleep 3
      LAUNCH_LOG="$(logs_since "$t" | grep -E 'save-or-arm|replay save \(')"
      echo "$LAUNCH_LOG" | sed 's/^/      /'
      check "double press goes through save-or-arm" log_has "save-or-arm (double_press): "
    else
      skip "double press is off in the debug build's settings (Replay > double-press AYN)"
    fi
    restore
  else
    skip "arm/save and double press: $PKG is on standby (owner: $(owner)); rerun with --take-control"
  fi

  step "Standby"
  if [ "$(owner)" != "$PKG" ]; then
    launch_trigger "launch on standby"
    check "standby ignores the trigger" log_has "save-or-arm (external): NONE"
    check "no save on standby" sh -c "! echo \"\$0\" | grep -q 'replay save (external)'" "$LAUNCH_LOG"
  else
    skip "the debug build owns stock, so it isn't on standby"
  fi

  step "Trigger signed with a different key"
  adb uninstall "$TRIGGER_PKG" >/dev/null
  adb install "$tmp/trigger-wrong-key.apk" >/dev/null
  check "wrong-key trigger is not granted the permission" sh -c "! adb shell dumpsys package $TRIGGER_PKG | grep -q '$PKG.permission.TRIGGER: granted=true'"
  launch_trigger "wrong-key launch"
  check "wrong-key broadcast denied" log_has "Permission Denial"
  check "receiver never ran for the wrong key" sh -c "! echo \"\$0\" | grep -q '/ExternalTrigger('" "$LAUNCH_LOG"
  adb uninstall "$TRIGGER_PKG" >/dev/null
  install_trigger "$TRIGGER_APK"
  check "real trigger reinstalled and granted" granted

  step "Command Panel not running"
  local top_app
  top_app="$(sh_dev dumpsys window displays | awk '/Display: mDisplayId=/{d=$2} d == "mDisplayId=0" && /mFocusedApp=/{sub(/}$/, "", $3); print $3; exit}')"
  sh_dev am force-stop "$PKG"
  launch_trigger "launch with the service stopped"
  check "receiver reports it isn't running" log_has "service not running"
  check "service was not started" sh -c "! adb shell dumpsys activity services $PKG | grep -q AssistantService"
  # A force-stopped app gets no boot or package-replaced broadcast, so open its companion to start the service, then
  # bring back whatever had the top screen.
  sh_dev am start -W -n "$PKG/jr.brian.commandpannel.MainActivity" >/dev/null
  check "debug service running again" wait_for 15 service_running
  [ -n "$top_app" ] && sh_dev am start -W -n "$top_app" >/dev/null && echo "  top screen handed back to $top_app"

  rm -rf "$tmp"
}

case "$stage" in
  static) static_checks ;;
  device) device_checks ;;
  all)    static_checks; device_checks ;;
esac

echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
