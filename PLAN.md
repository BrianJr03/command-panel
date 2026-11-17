# Command Panel — build plan

A user-installable replacement for the AYN Thor's stock bottom-screen assistant
(`com.odin.dualscreen.assistant`): stock UI and behaviour 1:1, plus the features Brian built in his BDSA fork.

## Decisions

| Item | Decision |
|---|---|
| Name / package | **Command Panel**, `jr.brian.commandpannel` (debug variant `.debug`, installs side by side) |
| Location | `~/Desktop/Projects/command-panel` |
| Stock assets | Reuse AYN's icons (vectors, `_black` variants) and strings incl. ja/ko/zh locales, from `~/thor-re/DualScreenAssistant/apktool/res/` |
| Stock coexistence | **Opt-in** "Replace stock assistant" setting, off by default. When on: ephemeral freeze of the stock process, 5000 ms unfreeze window around `SCREEN_OFF`, full restore when turned off or Command Panel is uninstalled |
| Fork features | Ported from `fork-port/` (Brian's own code only — see `fork-port/PORT_MANIFEST.md`) |
| Stack | Kotlin, Jetpack Compose, MVVM, Hilt; AGP 9.0.1, Kotlin 2.3.10, KSP 2.3.6, Hilt 2.59.2, Compose BOM 2026.02.01, Gradle 9.1.0; minSdk 33, compile/targetSdk 37 (all matching Jarngreipr) |
| Modules | `:app`, `:probe` (the capability probe from `~/Desktop/Projects/thor-probe`, debug-only, never shipped) |

## Reference material

- Stock teardown: `~/thor-re/DualScreenAssistant/NOTES.md` (keys, binders, timings, layout numbers, colours) and
  `device/shots/` (live screenshots).
- Probe results: `NOTES.md` §10 and `thor-probe/README.md`.
- Fork features: `fork-port/PORT_MANIFEST.md`.

## Architecture (from the probe)

| Need | Route |
|---|---|
| Settings read/write, fan binder, mapping service, sysfs telemetry | direct, after a startup self-grant through `PServerBinder` |
| Bottom-screen panel | `TYPE_APPLICATION_OVERLAY` window on display 4, view-driven 200 ms slide/fade (no `FLAG_LAYOUT_NO_LIMITS`) |
| FPS (SurfaceFlinger 1013), `sys.temp.disabled.touch`, `settings put` fallback | `PServerBinder` root shell |
| Top-screen black cover (above status bar), per-display brightness, task listing/removal, screen capture, backup file I/O | root `app_process` helper spawned through `PServerBinder`, Binder handshake back to the app |
| Recents on the bottom screen | **open** — user app may not bind Quickstep; try binding from the root helper first |

## Phases

### 0. Scaffold — done 2026-11-16
Gradle project, `:app` + `:probe`, Hilt, Compose, `.debug` variant, `scripts/dev.sh`.
Imported from the stock APK: 106 app drawables (with `_black` variants; three apktool-split gradients re-inlined as
`aapt:attr`), 12 colours (+7 night), 67 strings in 8 locales (`values`, `ja`, `ko`, `zh`, `zh-rCN/HK/MO/TW`), the
launcher icon. Library resources and RxTool crash strings were left out.
Verified on the Thor: placeholder screen renders stock icons/strings; `:probe` reproduces the recorded results
(15 PASS / 3 expected FAIL); 2 unit tests guard the resource import.

### 1. Privileged layer — done 2026-11-16
`VendorRootShell` (AYN `PServerBinder`, root shell), `HiddenApi` (exemptions), `PermissionGranter` (startup
self-grant, each permission checked before granting), and the root `app_process` helper: `RootHelperMain`
publishes its `IRootHelper` binder via `ServiceManager.addService` under a per-applicationId name, `RootHelper`
adopts it with `getService` and a death-watch, and the helper exits when the app's client token dies. `IRootHelper`
(AIDL) exposes `exec`, per-display brightness, identity, attachClient, shutdown. `PrivilegedAccess` is the Hilt
`@Singleton` facade. R8 keep rules protect the reflectively-launched helper.
Verified on the Thor via the Diagnostics screen: helper up as `uid=0 ctx=u:r:pservice:s0`; shell `id -u => 0` and
`getBrightness(0)` round-trip through it; all five grants succeed; `WRITE_SECURE_SETTINGS granted=true`; helper
service gone after the app is force-stopped; stock assistant untouched. Release (minified) build passes.
Not yet wired: `setDisplayBrightness` exercised for real (used in phase 4), helper auto-respawn on unexpected death
(added with the foreground service in phase 2).

### 2. Overlay host and input
Foreground service owns the display-4 overlay and the AYN button state machine
(`action.tcc.button.key.event`: down no-op, long-press → top only, up → wake or toggle, 400 ms debounce, ignored on
keyguard). HOME via the mapping service. Volume keys via `action.volume.key.event`.

### 3. Stock UI 1:1
Status bar, Mode tab, Settings tab, nav bar, dialogs, both stock palettes (Light / Pure Black, `tcc_theme_mode`),
sizes derived from display height exactly as stock (`0.065h`, `0.135h`, 1.148 aspect clamp). Built as addressable
cards so Arrange mode (fork feature 3) drops in later. Check each screen against `device/shots/`.

### 4. Stock features
- Screen modes 0/1/2: cover windows, touch disable, focus-lock + system-key-lock + pin-mode save/restore, tip dialog
  before "top only", keyguard handling, crash/exit recovery.
- Brightness sliders (temporary while dragging, commit on release, per display), dual slider.
- Volume: top `STREAM_MUSIC`, bottom `secondary_screen_volume_level`, volume-key overflow rule.
- 60/120 Hz toggle, FPS counter.
- Hardware row: temp (colour thresholds), fan, CPU/GPU/PWR/RAM rings.
- 10 quick tiles + option dialogs (performance-dependent fan options, focus tile only in dual mode).
- Clean Memory (tile and clock tap).

### 5. Stock coexistence (opt-in)
"Replace stock assistant" setting. Suppression sequence, 5000 ms `SCREEN_OFF` unfreeze (fork feature 14), restore
on opt-out / uninstall / crash-loop.

### 6. Companion settings screen (top display)
Stock has none (its activity is disabled). Needed to host the fork features' settings. Gamepad-navigable two-pane
screen in Command Panel's own code, with sticky section labels (fork feature 13).

### 7. Fork features, in this order
Each is repackaged from `fork-port/`, upstream-origin lines rewritten, tests ported.
1. Nav bar Home / Mode / Launch / Tasks / Settings, Home default (1)
2. Hide network speed (4)
3. Battery time-remaining estimate (5)
4. Quick Launch (2)
5. Arrange mode (3)
6. Thor RGB accent sync (6)
7. Dark-on-TV tap-to-wake (7)
8. Settings backup & restore (9)
9. Look system + all looks, with font license notices (8)
10. Clear All Tasks tile with keep-on-screen policy (10)
11. Quick-toggle reorder (12)
12. Replay buffer — after writing Command Panel's own capture pipeline in the helper (11)

## Verification

- Unit tests: ported fork tests (reorder math, overlay layout, battery estimator, backup codec/schema, ring buffer,
  clear-tasks policy, look renderers) plus new tests for the AYN button state machine and screen-mode save/restore.
- On device, per phase: side-by-side screenshots against `~/thor-re/DualScreenAssistant/device/shots/`; every
  `Settings.System` key in `NOTES.md` §4 read back after each control; stock-coexistence toggled on and off, then
  Command Panel uninstalled, confirming the stock assistant answers the AYN button again.
- Re-run `:probe` after any firmware update.

## Constraints

- Personal, non-commercial, unpublished: AYN assets are reused as-is, and ported fork code stays under the BDSA
  license terms until every derived part is rewritten (see `fork-port/PORT_MANIFEST.md`).
