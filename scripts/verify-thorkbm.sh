#!/usr/bin/env bash
# Verifies installing and opening ThorKBM from the companion's Keyboard section (#20).
#   scripts/verify-thorkbm.sh [static|device|all] [--uninstall-first] [--no-build]
#
# static  Builds, unit tests, and the merged manifest's <queries> for ThorKBM. No device needed.
# device  On the connected Thor, against the debug build (the only one with this branch's code): opens the companion
#         on the top screen, goes to Keyboard, presses Install, then checks that ThorKBM was installed by the root
#         shell, is the release package signed with ThorKBM's release key, matches GitHub's latest tag, that the card
#         now offers Open instead of Install, and that Open starts ThorKBM on the top screen.
#   --uninstall-first  Removes an installed jr.brian.thorkbm first so the install can run. This DELETES ThorKBM's
#                      settings on the device: only with the PM's OK. Without it, an installed ThorKBM gets the
#                      Open checks only.
#   --no-build         Installs the debug APK already built instead of building it.
#   --no-root          Installs through Android's installer instead of the root shell, as on a Thor without root:
#                      writes the debug build's `no_root=true` override (removed again on exit), allows "Install
#                      unknown apps" for the debug build when asked, and taps through Android's installer. Checks
#                      each card message, and that the APK handed over is ThorKBM signed with its release key.
#                      Stops if Play Protect shows up.
set -uo pipefail
cd "$(dirname "$0")/.."

stage="all"; uninstall_first=false; build=true; no_root=false
for arg in "$@"; do
  case "$arg" in
    static|device|all) stage="$arg" ;;
    --uninstall-first) uninstall_first=true ;;
    --no-build) build=false ;;
    --no-root) no_root=true ;;
    *) echo "unknown argument: $arg" >&2; exit 2 ;;
  esac
done

PKG="jr.brian.commandpannel.debug"
COMPANION="$PKG/jr.brian.commandpannel.MainActivity"
KBM="jr.brian.thorkbm"
# ThorKBM's release signing certificate (PanelPluginTrust.THORKBM_CERT_SHA256).
KBM_CERT="988a63897e69f0e080d6a9cb8acf5a0d1e01deb6fd8c6337a33778b5cbf44379"
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
OUT_DIR="${OUT_DIR:-$(mktemp -d -t verify-thorkbm)}"
SDK_DIR="$(grep '^sdk.dir=' local.properties | cut -d= -f2)"
BUILD_TOOLS="$(ls -d "$SDK_DIR"/build-tools/* | sort -V | tail -1)"

passes=0; fails=0; skips=0
pass() { echo "  PASS  $*"; passes=$((passes + 1)); }
fail() { echo "  FAIL  $*"; fails=$((fails + 1)); }
skip() { echo "  SKIP  $*"; skips=$((skips + 1)); }
step() { echo; echo "== $*"; }
check() { local what="$1"; shift; if "$@"; then pass "$what"; else fail "$what"; fi; }

# --- static ---------------------------------------------------------------------------------------------------------

static_checks() {
  step "Build and unit tests"
  check "assembleDebug, :app:testDebugUnitTest" ./gradlew -q :app:assembleDebug :app:testDebugUnitTest
  build=false

  step "Merged debug manifest"
  local merged="app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
  check "<queries> lists $KBM" grep -q "<package android:name=\"$KBM\"" "$merged"
  check "<queries> lists $KBM.debug" grep -q "<package android:name=\"$KBM.debug\"" "$merged"
}

# --- device ---------------------------------------------------------------------------------------------------------

sh_dev() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
# wait_for <seconds> <command string>: polls once a second until the command succeeds.
wait_for() { local n="$1" i; shift; for i in $(seq 1 "$n"); do if eval "$*"; then return 0; fi; sleep 1; done; return 1; }
installed() { sh_dev pm path "$1" | grep -q '^package:'; }
version_of() { sh_dev dumpsys package "$1" | grep -m1 'versionName=' | sed 's/.*versionName=//'; }
cert_digest() { "$BUILD_TOOLS/apksigner" verify --print-certs "$1" | grep -m1 'SHA-256' | awk '{print $NF}'; }
top_focus() {
  sh_dev dumpsys window displays | awk '/Display: mDisplayId=/{d=$2} d == "mDisplayId=0" && /mFocusedApp=/{print $3; exit}'
}

# The top screen's UI tree, saved to $OUT_DIR/ui.xml.
dump_ui() { sh_dev uiautomator dump /sdcard/verify-thorkbm.xml >/dev/null; adb pull /sdcard/verify-thorkbm.xml "$OUT_DIR/ui.xml" >/dev/null 2>&1; }
ui_has() { dump_ui; grep -q "text=\"$1\"" "$OUT_DIR/ui.xml"; }
# Taps the center of the first node whose text matches $1 exactly (Compose exposes text through the dump).
tap_text() {
  dump_ui
  local b
  b="$(grep -o "<node [^>]*text=\"$1\"[^>]*>" "$OUT_DIR/ui.xml" | head -1 | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | tr '\n' ' ')"
  [ -n "$b" ] || return 1
  set -- $b
  sh_dev input -d 0 tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )) >/dev/null
}
# Some node's text matches the extended regex $1 (e.g. "Installed \([^\"]+\)").
ui_matches() { dump_ui; grep -qE "text=\"$1\"" "$OUT_DIR/ui.xml"; }

# Screenshot of the top screen to $OUT_DIR/<name>.png.
shot() { adb exec-out screencap -p > "$OUT_DIR/$1.png" 2>/dev/null && echo "  screenshot: $OUT_DIR/$1.png"; }

open_keyboard_section() {
  sh_dev am start -W -n "$COMPANION" --display 0 >/dev/null
  sleep 2
  # A fresh debug install opens with What's New, then the companion tour: close both.
  local i
  for i in 1 2 3; do
    if ui_has "Got it"; then tap_text "Got it"; sleep 2
    elif ui_has "Skip tour"; then tap_text "Skip tour"; sleep 2
    else break; fi
  done
  tap_text "Keyboard" && sleep 1 && ui_has "ThorKBM"
}

# The debug build's ThorKBM overrides file (ThorKbmInstaller.DebugOverrides), in its own storage.
OVERRIDES="files/thorkbm_debug.properties"
set_no_root() {
  local tmp; tmp="$(mktemp)"; echo "no_root=true" > "$tmp"
  adb push "$tmp" /data/local/tmp/thorkbm_debug.properties >/dev/null 2>&1; rm -f "$tmp"
  sh_dev run-as "$PKG" cp /data/local/tmp/thorkbm_debug.properties "$OVERRIDES"
  sh_dev rm -f /data/local/tmp/thorkbm_debug.properties
  [ "$(sh_dev run-as "$PKG" cat "$OVERRIDES")" = "no_root=true" ]
}
clear_overrides() { $no_root && sh_dev run-as "$PKG" rm -f "$OVERRIDES" >/dev/null; }
trap clear_overrides EXIT

play_protect() { dump_ui; grep -qiE 'Play Protect|Unsafe app blocked|Scan app' "$OUT_DIR/ui.xml"; }
stop_on_play_protect() {
  if play_protect; then
    shot play-protect
    fail "Play Protect showed up; stopping here so a person can look"
    echo; echo "$passes passed, $fails failed, $skips skipped"; exit 1
  fi
}
card_message_is() { open_keyboard_section >/dev/null; grep -qF "text=\"$1\"" "$OUT_DIR/ui.xml"; }
# Card messages (strings.xml msg_thorkbm_*), with `'` as Android shows it.
MSG_ALLOW="Allow Command Panel to install apps in the screen that just opened, then press Install again."
MSG_FINISH="Finish installing ThorKBM in Android's installer."
MSG_INSTALLED="ThorKBM is installed."
# The APK the debug build handed to Android's installer (its updates cache), pulled for a signer check.
handed_apk_ok() {
  local apk="$OUT_DIR/thorkbm-handed.apk"
  adb exec-out run-as "$PKG" cat cache/updates/thorkbm.apk > "$apk" 2>/dev/null
  # Badging is read whole first: with pipefail, grep -q closing the pipe early would fail the check.
  local badging; badging="$("$BUILD_TOOLS/aapt2" dump badging "$apk" 2>/dev/null)"
  [ -s "$apk" ] && [ "$(cert_digest "$apk")" = "$KBM_CERT" ] && echo "$badging" | grep -q "^package: name='$KBM' "
}

# Android's installer labels its buttons in capitals on this firmware.
tap_install() { tap_text "INSTALL" || tap_text "Install"; }

# Install through Android's installer: allow "Install unknown apps" if asked, then confirm the install.
install_without_root() {
  check "debug build's no_root override written" set_no_root
  check "pressed Install" tap_text "Install"
  if wait_for 30 "top_focus | grep -q ManageAppExternalSources"; then
    shot no-root-allow-installs
    pass "Install opened \"Install unknown apps\" for the debug build"
    check "allowed installs from the debug build" tap_text "Allow from this source"
    sleep 1
    check "debug build may now install apps" sh -c "adb shell appops get $PKG REQUEST_INSTALL_PACKAGES | grep -q allow"
    sh_dev input -d 0 keyevent KEYCODE_BACK >/dev/null; sleep 2
    check "card says: $MSG_ALLOW" card_message_is "$MSG_ALLOW"
    shot no-root-card-allow-message
    check "pressed Install again" tap_text "Install"
  else
    skip "\"Install unknown apps\" wasn't asked (already allowed for the debug build)"
  fi
  if ! wait_for 60 "top_focus | grep -qiE 'packageinstaller|ResolverActivity'"; then
    fail "Android's installer didn't open"; return
  fi
  if top_focus | grep -q ResolverActivity; then
    # Other apps on the device also open APKs, so Android asks which one; a direct handoff would skip this.
    shot no-root-chooser
    fail "Android asked which app opens the APK instead of going straight to its installer"
    if ui_has "Open with Package installer" && tap_text "Just once" && wait_for 10 "top_focus | grep -qi packageinstaller"; then
      echo "  picked Package installer, just once"
    else
      return
    fi
  fi
  pass "Android's installer opened"
  stop_on_play_protect
  shot no-root-installer
  # uiautomator only sees the installer's window, so the card's "$MSG_FINISH" behind it is checked by eye.
  echo "  check by eye: the card behind the installer says \"$MSG_FINISH\" ($OUT_DIR/no-root-installer.png)"
  check "the APK handed over is ThorKBM, signed with its release key" handed_apk_ok
  check "confirmed in Android's installer" tap_install
  if wait_for 60 "installed $KBM"; then pass "$KBM installed"; else stop_on_play_protect; fail "$KBM not installed after 60 s"; return; fi
  sleep 2
  stop_on_play_protect
  shot no-root-installer-done
  tap_text "DONE" || tap_text "Done"
  sleep 1
  check "card says: $MSG_INSTALLED" card_message_is "$MSG_INSTALLED"
}

device_checks() {
  step "Device"
  if ! adb get-state >/dev/null 2>&1; then fail "no device connected"; return; fi
  local latest; latest="$(gh api repos/BrianJr03/Thor-Ergo-Android/releases/latest --jq .tag_name 2>/dev/null | sed 's/^v//')"
  echo "  latest ThorKBM release: ${latest:-unknown}, output: $OUT_DIR"

  step "Debug app"
  if $build; then ./gradlew -q :app:assembleDebug || { fail "debug build"; return; }; fi
  check "debug app installed" sh -c "adb install -r '$APP_APK' >/dev/null"

  step "Before install"
  if installed "$KBM"; then
    if $uninstall_first; then
      echo "  removing $KBM $(version_of "$KBM") (--uninstall-first)"
      sh_dev pm uninstall "$KBM" >/dev/null
      check "$KBM removed" sh -c "! adb shell pm path $KBM | grep -q package:"
    else
      skip "install: $KBM $(version_of "$KBM") is already installed (rerun with --uninstall-first, PM's OK needed)"
    fi
  fi

  local had_debug=false
  installed "$KBM.debug" && had_debug=true
  check "companion opens on Keyboard with the ThorKBM card" open_keyboard_section
  if $had_debug; then
    check "card notes the debug build ($(version_of "$KBM.debug"))" ui_matches "Debug build also installed \\([^\"]+\\)"
  fi
  if ! installed "$KBM"; then
    check "card says Not installed" ui_has "Not installed"
    shot card-before-install
    $had_debug && check "card offers Install next to Open for the debug build" \
      sh -c "grep -q 'text=\"Install\"' '$OUT_DIR/ui.xml' && grep -q 'text=\"Open\"' '$OUT_DIR/ui.xml'"

    step "Install"
    if $no_root; then
      install_without_root
    else
      check "pressed Install" tap_text "Install"
    fi
    if $no_root; then
      :
    elif wait_for 90 "installed $KBM"; then
      pass "$KBM installed"
    else
      local focus; focus="$(top_focus)"
      if echo "$focus" | grep -q packageinstaller; then
        skip "no root shell: Android's installer is waiting on the top screen ($focus); finish by hand, then rerun"
      else
        fail "$KBM not installed after 90 s"
        dump_ui; grep -o 'text="[^"]*"' "$OUT_DIR/ui.xml" | grep -iE "thorkbm|install|limit" | sed 's/^/      /'
      fi
    fi
    if installed "$KBM"; then
      sleep 2
      local apk="$OUT_DIR/thorkbm-installed.apk"
      adb pull "$(sh_dev pm path "$KBM" | grep -m1 base.apk | sed 's/^package://')" "$apk" >/dev/null 2>&1
      check "installed APK is signed with ThorKBM's release key" test "$(cert_digest "$apk")" = "$KBM_CERT"
      [ -n "$latest" ] && check "installed version is the latest release ($latest)" test "$(version_of "$KBM")" = "$latest"
      echo "  installer fields: $(sh_dev dumpsys package "$KBM" | grep -oE '(installerPackageName|installInitiatingPackageName|installOriginatingPackageName|updateOwnerPackageName)=[^ ]*' | tr '\n' ' ')"
      $no_root || check "no staged copy left behind" sh -c "! adb shell ls /data/local/tmp/thorkbm.apk >/dev/null 2>&1"
      $had_debug && check "debug build still installed" installed "$KBM.debug"
    fi
  fi

  if installed "$KBM"; then
    step "Open"
    open_keyboard_section >/dev/null
    check "card shows Installed ($(version_of "$KBM"))" ui_has "Installed ($(version_of "$KBM"))"
    check "card offers Open, not Install" sh -c "grep -q 'text=\"Open\"' '$OUT_DIR/ui.xml' && ! grep -q 'text=\"Install\"' '$OUT_DIR/ui.xml'"
    shot card-installed
    check "pressed Open" tap_text "Open"
    check "ThorKBM is in front on the top screen" wait_for 10 "top_focus | grep -q '$KBM/'"
    sleep 1; shot thorkbm-opened
    sh_dev input keyevent KEYCODE_HOME >/dev/null
  fi
}

case "$stage" in
  static) static_checks ;;
  device) device_checks ;;
  all)    static_checks; device_checks ;;
esac

echo
echo "$passes passed, $fails failed, $skips skipped"
[ "$fails" -eq 0 ]
