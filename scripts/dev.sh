#!/usr/bin/env bash
# Build, install and launch Command Panel (or the probe) on the connected Thor.
#   scripts/dev.sh build|install|run|test [app|probe]
set -euo pipefail
cd "$(dirname "$0")/.."

action="${1:-run}"
module="${2:-app}"

case "$module" in
  app)   apk="app/build/outputs/apk/debug/app-debug.apk";     component="jr.brian.commandpannel.debug/jr.brian.commandpannel.MainActivity" ;;
  probe) apk="probe/build/outputs/apk/debug/probe-debug.apk"; component="jr.brian.thorprobe/.MainActivity" ;;
  *) echo "unknown module: $module (app|probe)" >&2; exit 1 ;;
esac

build()   { ./gradlew ":$module:assembleDebug"; }
install() { adb install -r "$apk"; }
launch()  { adb shell am start -n "$component"; }

case "$action" in
  build)   build ;;
  install) install ;;
  run)     build && install && launch ;;
  test)    ./gradlew ":$module:testDebugUnitTest" ;;
  *) echo "unknown action: $action (build|install|run|test)" >&2; exit 1 ;;
esac
