# Thor Probe

A throwaway capability probe for the **AYN Thor** (`kalama`, Android 13). It answers one
question before building a user-installable replacement for the stock
`com.odin.dualscreen.assistant`: **what can a normal, non-system, non-platform-signed app
actually reach on this firmware?**

It is a single Activity with a "Run probes" button. Each probe is non-destructive — writes
are restored, overlay windows are added and immediately removed, no vendor state is left
changed. Results print on screen, to logcat (`-s THORPROBE`), and to
`Android/data/jr.brian.thorprobe/files/thor-probe-report.txt`.

## Measured results (firmware `Thor_V1.0.0.377_20260206`, app uid 10255)

**Headline: `PServerBinder` hands a normal app a root shell.** `id` through it returns
`uid=0(root) … context=u:r:pservice:s0`, and `getenforce` = `Permissive`.

| Capability | Direct (app uid) | Via `PServerBinder` root |
|---|---|---|
| Hidden-API exemptions (`setHiddenApiExemptions`) | PASS | — |
| `Settings.System` read | PASS | — |
| `Settings.System` write (vendor keys) | PASS after self-granting `WRITE_SETTINGS` | PASS |
| Self-grant appops / `pm grant` | — | PASS |
| Fan provider binder (`IFanControlProvider`) | PASS | — |
| Mapping service bind (`com.odin.mapping`) | PASS (explicit intent) | — |
| sysfs telemetry (temp/cpu/gpu/batt) | PASS | — |
| Overlay type 2038 on display 4 | PASS after self-granting `SYSTEM_ALERT_WINDOW` | — |
| Overlay type 2021 (top-screen cover) | FAIL (`permission denied`) | needs root `app_process` helper |
| SurfaceFlinger 1013 (FPS) | FAIL (`Operation not permitted`) | PASS |
| `setprop sys.temp.disabled.touch` | — | PASS |
| `DisplayManager.setBrightness(id, f)` | FAIL (needs `CONTROL_DISPLAY_BRIGHTNESS`) | needs root helper / `cmd display` |
| Quickstep bind (recents on bottom screen) | FAIL (`Not allowed to bind`) | **open — no proven route yet** |

See `../../thor-re/DualScreenAssistant/device/probe-report.txt` for the raw log and
`NOTES.md` §10 in that folder for how each result maps onto the rebuild.

## Build / run

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n jr.brian.thorprobe/.MainActivity
# tap "Run probes", then:
adb logcat -d -s THORPROBE
```

Package `jr.brian.thorprobe`, minSdk 33 / targetSdk 35, no Compose (plain Views to keep the
dependency surface small). `Probes.kt` holds all the checks; `MainActivity.kt` is just the runner.

## Folding into the final repo

Drop this in as a standalone `:probe` Gradle module: move `app/src/main` under
`probe/src/main`, reuse the app module's `Probes.kt` verbatim, and add `include(":probe")` to
the final `settings.gradle.kts`. It depends on nothing from the main app, so it can also stay a
separate debug-only module that is never shipped in the release variant.
