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

### 2. Overlay host and input — done 2026-11-16
`DisplayTopology` resolves top (0) / bottom (4). `OverlayHost` attaches a `ComposeView` (with
`OverlayLifecycleOwner` supplying the Lifecycle/ViewModelStore/SavedStateRegistry) to the bottom display's
`WindowManager` as `TYPE_APPLICATION_OVERLAY` (2038), full real size, no `FLAG_LAYOUT_NO_LIMITS`, 200 ms view-driven
slide+fade. `AssistantService` is the `specialUse` foreground service: inits the privileged layer, hosts the
overlay, registers the AYN broadcast dynamically, and runs `AynButtonMachine`. `BootReceiver` starts it on boot.
`AynButtonMachine` is pure and unit-tested (down no-op, long-press hide+blank + swallow next up, up wakes-if-blanked
else toggles with stock's debounce guard); blank/wake are no-ops until phase 4.
Verified on the Thor: service runs; overlay attaches to display 4 at 1240×1080 (no clipping); the Close button
dismisses by touch; app-action and broadcast show/hide/toggle work; 5 state-machine tests pass.
**Open:** whether the firmware's own `action.tcc.button.key.event` reaches a user app — needs a physical AYN press
(an `am broadcast` stand-in did not visibly fire; it may be a protected broadcast). If it doesn't, fall back to the
mapping service or an accessibility key hook (as Brian's Mjolnir does for HOME). HOME dismiss and volume keys deferred.
**Coexistence note:** the stock overlay (type 2021) renders above ours (2038) when both are up — stock suppression
(phase 5) is what resolves this.

### 3. Stock UI 1:1 — done 2026-11-16
Overlay rebuilt in Compose from the stock layout: `TccColors` (Light + Pure Black palettes verbatim from the
decompiled themes, `LocalTccColors`), `StockIcon` resolver (dark uses the `_black` variants), `OverlayRoot`
(status bar / active tab / nav bar), `StatusBar`, `ModeTab` (screen-mode buttons, brightness + volume cards),
`SettingsTab` (frame-rate pill + FPS, 2×4 quick tiles with page dots, TEMP/FAN cards, CPU/GPU/PWR/RAM rings),
`NavBar` (Mode/Task/Settings), and reusable `TccCard`, `TccIconButton`, `RingGauge`, `TccSliderRow`, `BatteryIcon`.
Driven by `OverlayUiState` sample values that match the stock screenshots (phase 4 feeds it live state). Wired into
`AssistantService`'s `OverlayHost` in place of the placeholder.
Verified on the Thor: both tabs render as a faithful match of `device/shots/` (status bar, frame-rate pill, the ten
stock tiles with the active purple glow, ring colours, TEMP/FAN, mode buttons with the dual-screen highlight,
brightness/volume sliders, nav labels). 3 new unit tests. Frame-rate pill is drawn in Compose (its stock drawable is
a gradient shape, not a vector). Dialogs, pager swipe, theme switching and live data are phase 4.

### 4. Stock features — in progress (data layer + core controls done 2026-11-16)

Done and verified on the Thor:
- `SystemSettings` (typed vendor-key access: reads via resolver, writes via root shell), `FanProvider` binder,
  `TelemetryReader` (pure, tested) + `TelemetryMonitor` (1.5 s poll of sysfs temp/cpu/gpu/power, RAM, fan, and
  SurfaceFlinger FPS via root), and `SettingsRepository` (reactive `StateFlow<OverlayUiState>` over settings
  observers + telemetry + battery + clock + audio + net/wifi/bt; implements `OverlayActions`).
- Overlay now shows **live** data: clock, net speed, battery + charging, FPS, TEMP/FAN, CPU/GPU/PWR/RAM rings, and
  tile titles/active states derived from the real settings (e.g. "Smart Mode", "Top screen").
- Working controls: screen-mode select (writes `dual_screen_display_mode`), brightness sliders (temporary while
  dragging via the helper, commit on release, per display + dual), volume sliders (top `STREAM_MUSIC`, bottom
  `secondary_screen_volume_level`), 60/120 Hz toggle (writes `min`/`peak_refresh_rate`), instant-toggle tiles
  (vibration, charging separation, force landscape). Verified: pill 60↔120 and vibration 1↔0 write through and the
  UI reacts.

Part 2 done and verified on the Thor (2026-11-16):
- Modal option dialogs (`OverlayDialogs`): Focus / Performance / Fan (perf-dependent options) / Controller / Theme,
  rendered over a scrim with the stock icons and a radio check; each writes through and the UI reacts (verified
  Performance 0→1→0). Ambient LED broadcast toggle and Clean Memory wired. Theme change applies the palette live
  (`OverlayRoot` picks from `tcc_theme_mode`).
- `ScreenModeController`: observes `dual_screen_display_mode` and applies the stock blanking — focus-lock +
  system-key-lock save/restore, `sys.temp.disabled.touch` via root, and a black cover window on the bottom display.
  Verified: mode 1 → touch `0,1`, focus 1, bottom fully black; mode 0 → restored. AYN long-press (blank) / press
  (wake) are wired to it.

Part 3 done (2026-11-16): quick-tile `HorizontalPager` (page 2 shows the remaining tiles, synced dots — verified);
the "Only top screen" tip dialog with "Do not show again" (stock text, persisted suppression — verified).

Remaining (robustness, revisit later):
- Top-screen cover for mode 2 (needs the helper's type-2021 window; currently applies focus + touch only), keyguard
  handling and crash/exit recovery, the vendor pin-screen-mode save/restore.
- Fan custom-speed slider, Clean Memory exclusion logic (currently a blunt root kill), the volume-key overflow rule.

### 5. Stock coexistence (opt-in) — done 2026-11-16
`StockSuppressor` runs the ephemeral suppression (transiently disable `TccServices` → `kill -9` → wait for respawn →
`SIGSTOP` the windowless process → re-enable on disk, never left disabled) and the clean restore; pure pid/stat
helpers are unit-tested. `StockCoexistence` owns the opt-in `replace_stock_assistant` pref (default off), applies it
on start, exposes `enabled` as a `StateFlow`, restores on service stop, and does the fork's 5000 ms `SCREEN_OFF`
unfreeze/re-freeze to avoid the stock-process ANR. Wired into `AssistantService` (screen receiver + a
`SET_REPLACE_STOCK` action on the exported dynamic receiver).
Verified on the Thor: enabling froze the stock process (respawned, state `T`); disabling restored it (new pid,
state `S`, package enabled, `TccServices` running). Left off, so the device is clean.
Remaining (robustness): restore stock on *ungraceful* app death and on uninstall via a helper-side watchdog (the
root helper survives app death but doesn't yet restore stock); crash-loop circuit breaker. The real toggle UI lands
in the phase-6 settings screen (today it's driven by the service action).

### 6. Companion settings screen (top display) — done 2026-11-16
`SettingsScreen` (two-pane: category rail + content) with `SettingsViewModel`. General category hosts the
**Replace Stock Assistant** switch (drives `StockCoexistence` via the service) and an overlay-preview button;
Appearance sets the overlay theme (`tcc_theme_mode`); Diagnostics runs the privileged-layer check. Replaces the
phase-1 diagnostics-only screen; themed with the overlay's `TccColors`.
Verified on the Thor: two-pane renders, categories switch, diagnostics all pass (helper uid=0).
Remaining: full gamepad navigation + sticky section labels (fork feature 13, arrives with the Megingiard-style
framework in phase 7); the fork features' own settings get added here as each is ported.

### 7. Fork features — in progress
Each is repackaged from `fork-port/` (Brian's own code), upstream-origin lines rewritten, tests ported.

Done (2026-11-16):
- **5000 ms screen-off unfreeze (14)** — landed with phase 5.
- **Hide network speed (4)** — `hide_net_speed` pref + General toggle; status bar gates the speed. Verified.
- **Battery time-remaining estimate (5)** — ported `BatteryEstimator` (fork-authored, 217 lines, verbatim but
  repackaged) + new `BatteryEstimateMonitor` feeding it from `BatteryManager`/sysfs; General toggle; shown after the
  battery % as "· full in 47m". Ported the fork's estimator test (mode-enum test dropped; `flowFor` helper kept).
  Verified on device.

Done (cont.):
- **Quick Launch (2)** — `QuickLaunch` system, Launch nav button, settings app picker. Verified.
- **Thor RGB accent sync (6)** — ported `ThorRgbController`, Appearance toggle. Verified (nodes writable).
- **Customizable quick-tile roster + Android system toggles** — `QuickSettingTileId` (19→20 tiles, 3 categories,
  stock/Material icons), enable/disable in a "Quick Toggles" settings category; new actions for Wi-Fi/BT/DND/Airplane
  and the simple Tools toggles; L2/R2 trigger dialog. Repository reacts to app-pref changes live. Verified.
- **Screenshot Tools tile** — `Screenshotter` (resolve display ids from SurfaceFlinger, `screencap` via root,
  media-scan), Top/Bottom/Both dialog. Verified: captured a valid 1920×1080 top-screen PNG.

- **Clear All Tasks (10)** — ported `ClearTasksPolicy` (pure, 290 lines, with its `dumpsys` parsers) + its test;
  helper `removeTask` (IActivityTaskManager reflection, protocol v2); repository gathers recents/activities/home via
  root dumps, applies the policy, removes via the helper; Tools tile + "Keep Apps On Screen" toggle. Verified on
  device: recents 15→4, kept Command Panel + home, removed background tasks, foreground survived.

- **Settings Backup & Restore (9)** — `SettingsBackup` (allow-listed JSON encode/decode, pure + tested; export via
  root to `/sdcard/Documents/CommandPanel/backups`, restore latest). General card. Verified on device: exported
  valid JSON, "Restored 6 settings".

- **Overlay look system (8)** — full 16-look system ported (your code; fonts are OFL 1.1, bundled with
  THIRD_PARTY_NOTICES). CP-native `TccLookSpec` + the 5 trait-style enums + `LookTraits`, `TccLookColors`
  (palette-derived), 13 renderer files + built-in card/tile/gauge/slider/nav renderers, look-aware text/casing.
  Overlay widgets delegate to the active look; `Appearance → Overlay look` picker (live). Verified on device:
  stock unchanged; Aurora Glass, Comic Panel, E-Ink, Tactical HUD, Terminal, Neon Arcade, Segment LCD all render
  faithfully. Known gap: the "60 FPS MODE" pill keeps its stock gradient across looks (not routed through the
  fpsBadge renderer yet).

Backlog (remaining fork features, roughly easiest → hardest):
- **Dark-on-TV tap-to-wake (7)** — done: ported ExternalDisplayTapWaker (adapted to SystemSettings/root), wired
  into the service; 1x1 FLAG_WATCH_OUTSIDE_TOUCH windows per display arm only when close_screen_when_output_video=1
  and DisplayPort is connected, tap writes the setting to 0 to wake. Verified wiring (arms/disarms, no crash, DP
  node read); the tap->wake path needs an external display to fully exercise.
- Quick-toggle drag-reorder (12) — rewrite the 160 upstream lines flagged in the manifest; drag-drop UI.
- Arrange mode (3) — needs the overlay cards made addressable.
- Replay buffer (11) — largest; needs Command Panel's own screen-capture pipeline in the helper first.
- Nav bar Home/Launch reorder (1) — BDSA-specific; likely not applicable.

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
