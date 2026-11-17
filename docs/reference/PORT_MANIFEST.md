# Fork port manifest

Code Brian wrote in his BDSA fork (`BrianJr03/better-dual-screen-assistant`), staged for integration into
Command Panel. **Only the fork's own changes are here**: the fork is 43 commits ahead / 0 behind upstream, so
`git diff 6b388b5..1e681d5` is exactly the fork's work (verified: 40 non-merge commits, 115 files, +18,375 / −1,750 —
matches `~/Downloads/FORK_CHANGES.md`).

Staged copies are byte-identical to the fork (original paths and `com.stormpanda.*` packages kept, so they stay
diffable against the fork). They are **not compiled**; each feature is repackaged to `jr.brian.commandpannel` when it
is integrated.

| Folder | Contents |
|---|---|
| `added/` | the 71 files the fork created (54 Kotlin/test, 17 res incl. 12 fonts), copied whole |
| `patches/` | the fork's hunks in 30 upstream files, as `git diff 6b388b5 HEAD -- <file>` (context lines are upstream code; `+` lines are the fork's) |
| `_upstream_origin_lines/` | line numbers of upstream-authored code found inside fork-created files (see below) |
| `_blame.tsv`, `_patched.tsv`, `_skipped.tsv` | raw data behind this manifest |

## Provenance check

`git blame -C -C -M` was run on every added file to find lines whose origin predates the fork. After discarding
boilerplate (`package`/`import` lines, braces, vector-XML headers), three files contain real upstream-authored code:

| File | Upstream-origin lines | Why |
|---|---:|---|
| `ui/settings/QuickTogglesReorderCard.kt` | 160 of 870 | quick-toggle list code moved out of upstream `MainActivity` during the fork's refactor |
| `test/system/ReplayBufferRepositoryTest.kt` | 21 of 197 | test setup copied from upstream test patterns |
| `test/system/backup/SettingsBackupTest.kt` | 17 of 378 | same |

Those lines are listed in `_upstream_origin_lines/` and get **rewritten**, not ported, during integration.
Every other added file is the fork's own code. In `patches/`, the large refactors (`MainActivity.kt` +623/−772,
`AssistantOverlayScreen.kt` +787/−413) also carry moved/re-indented upstream lines among the `+` lines; use them as a
map of what changed, not as code to paste.

## Not ported

| Item | Reason |
|---|---|
| `.claude/hooks/*`, `.claude/settings.json`, `CLAUDE.md` | BDSA-fork tooling (upstream release tracking) |
| `gradle/gradle-daemon-jvm.properties`, `app/build.gradle.kts`, `settings.gradle.kts` hunks | fork build config (BDSM signing, JDK 21 test toolchain) |
| `app/src/debug/res/values/strings.xml` | "BDSM Debug" label; Command Panel gets its own `.debug` side-by-side variant (same pattern) |
| `docs/*`, `README.md`, `AGENTS.md` hunks | BDSA documentation |

## Features

Commit ids are the fork's. "Needs" = Command Panel infrastructure the feature depends on that is **not** the fork's
code (it comes from upstream BDSA), so Command Panel builds its own version.

### 1. Nav bar: Home / Mode / Launch / Tasks / Settings — `2639a13`
Reordered nav, the Settings tab renamed **Home** (house icon) and opened by default, the Edit button renamed
**Settings** (sliders icon).
Added: `res/drawable/ic_nav_home.xml`, `rp_ic_nav_home.xml`, `res/values/strings_local.xml`.
Patches: `AssistantOverlayScreen.kt`, `TccTheme.kt`.

### 2. Quick Launch nav button — `198a9b2`
Nav button that opens a chosen app; app picker, label mode, target screen (top/bottom).
Added: `system/QuickLaunchApps.kt`. Patches: `MainActivity.kt`, `AssistantOverlayScreen.kt`,
`SystemSettingsRepository.kt`, `ScreenOverlayManager.kt`, `TccWidgets.kt`.
Needs: companion settings screen (picker lives there).

### 3. Arrange mode — `a38cef7`
Drag-and-drop the overlay cards and nav bar; cards keep their size, swaps by card/row/column; layout persisted.
Added: `system/OverlayLayout.kt`, `ui/overlay/arrange/ArrangeMode.kt`, `test/system/OverlayLayoutTest.kt`.
Patches: `AssistantOverlayScreen.kt`, `MainActivity.kt`, `SystemSettingsRepository.kt`.
Needs: overlay built from addressable cards (Command Panel's Compose overlay is designed this way from the start).

### 4. Hide network speed — `95c3380`
General toggle to hide the status-bar download speed.
Patches: `StatusBarRow.kt`, `MainActivity.kt`, `SystemSettingsRepository.kt`, `AssistantOverlayScreen.kt`.

### 5. Battery time-remaining estimate — `4d064d2`, `d00a4e4`, `7cb5b2f`
EMA-smoothed estimate (time to empty / to full / to the 80 % limit), shown after the battery % in the status bar
(final state; the PWR-ring placement was removed).
Added: `system/BatteryEstimator.kt`, `system/BatteryEstimateMonitor.kt`, `test/system/BatteryEstimatorTest.kt`.
Patches: `StatusBarRow.kt`, `TelemetryMonitor.kt`, `DeviceProfile.kt`, `AssistantOverlayScreen.kt`, `MainActivity.kt`.

### 6. Thor RGB accent sync — `35a492b`
Syncs the stick LEDs to the theme accent by writing the SN3112 LED sysfs nodes through the root shell, using the
vendor daemon's wire format (brightness attenuates RGB; the brightness byte stays 255).
Added: `privileged/ThorRgbController.kt`. Patches: `SystemSettingsRepository.kt`, `AssistantForegroundService.kt`,
`MainActivity.kt`. Needs: `PServerBinder` client (proven by the probe).

### 7. Dark-on-TV tap-to-wake — `35a492b`, `03e520d`
When `close_screen_when_output_video` = 1 and a DisplayPort display is connected, a tap on either handheld panel
writes the setting back to 0 to wake them. 1×1 `FLAG_WATCH_OUTSIDE_TOUCH` window per display; DP status from
`/sys/class/drm/card0-DP-1/status` plus `com.ro.action.*_STATUS_CHANGED`. The overlay is dismissed first so it
doesn't block the tap.
Added: `overlay/ExternalDisplayTapWaker.kt`. Patches: `ScreenOverlayManager.kt`, `AssistantOverlayScreen.kt`,
`AssistantForegroundService.kt`.

### 8. Overlay looks — 20 commits (`0335e21` … `4752174`)
Look system on top of the stock look: Aurora Glass (with gradient presets + OLED black), Cartridge, Tactical HUD,
Soft Console, Soft Console OLED, Segment LCD, Bloom, Neon Arcade, Terminal, Cockpit, Sticker Book, Industrial, Jelly,
Comic Panel, Y2K Chrome, E-Ink; device-native look folded into **Auto** in the pickers.
Added: `ui/components/TccLookSurfaces.kt`, `ui/theme/TccLookColors.kt`, `ui/theme/AuroraGradientPresets.kt`,
`ui/components/looks/*Renderers.kt` (13), their tests, `res/font/tcc_*.ttf` (12).
Patches: `TccLookSpec.kt` (+634, nearly all fork), `TccTheme.kt`, `TccWidgets.kt`, `AssistantOverlayScreen.kt`,
`OverlayDialogs.kt`, `THIRD_PARTY_NOTICES.md` + `assets/THIRD_PARTY_NOTICES.txt` (**font licenses — must ship with
the fonts**).
Needs: a look-spec abstraction (Command Panel's stock AYN look becomes the first spec).

### 9. Settings backup & restore — `13eaa5c`
Allowlisted JSON export to `/sdcard/Documents/…/backups/`, validation, automatic pre-import backup, restore through
the repository setters, gamepad-navigable backups list.
Added: `system/backup/{BackupSchema,SettingsBackupCodec,SettingsBackupManager}.kt`, `privileged/BackupFileStore.kt`,
`ui/backup/BackupRestoreScreens.kt`, tests. Patches: `PrivilegedBridge.kt`, `PrivilegedHelperMain.kt`,
`SystemSettingsRepository.kt`, `MainActivity.kt`.
Needs: root helper file I/O (or root shell), companion settings screen. Rename the backup format/folder for
Command Panel.

### 10. Clear All Tasks — keep apps on screen — `8a37f8f`
Pure policy that keeps on-screen tasks, their same-package/same-uid companion tasks (dual-screen frontends) and the
home app when clearing recents.
Added: `privileged/ClearTasksPolicy.kt`, `test/privileged/ClearTasksPolicyTest.kt`.
Needs: task listing/removal via root helper (`IActivityTaskManager`) or shell fallback. Stock has no Clear All Tasks
tile — Command Panel adds it as an extra tile.

### 11. Replay buffer — `8a37f8f`, `14f0ce1`
Save-last-N-seconds clips: preallocated ring buffer of encoded samples, keyframe-aligned save, status-bar REPLAY pill,
AYN double-press to save/arm, arm dialog with OK/Cancel, auto re-arm on boot and screen-on.
Added: `privileged/ReplayRingBuffer.kt`, `ui/replay/ReplayBufferSettingsCards.kt`, `res/drawable/*ic_qs_replay_buffer.xml`,
tests (`ReplayBufferRepositoryTest` has 21 upstream lines to rewrite).
Patches: `ScreenRecorderEngine.kt`, `PrivilegedHelperMain.kt`, `PrivilegedBridge.kt`, `AssistantForegroundService.kt`,
`ScreenOverlayManager.kt`, `OverlayDialogs.kt`, `SystemSettingsRepository.kt`, `QuickSettingTileId.kt`.
**Needs: a screen-capture pipeline** (display mirror → MediaCodec → sample sink). Upstream's `ScreenRecorderEngine`
is not the fork's code, so Command Panel writes its own capture in the root helper; the fork's ring buffer, ring
sink, save logic and UI plug into it.

### 12. Quick-toggle reorder — `ffbeb7a`
Long-press drag with live slide, edge-hold page flip, gamepad grab/drop (Select/A/B, L1/R1), single autosave on drop.
Added: `ui/settings/QuickTogglesReorderCard.kt` (**160 upstream-origin lines to rewrite**),
`ui/settings/QuickTileReorderMath.kt`, test. Needs: companion settings screen.

### 13. Sticky section labels — `d44c738`
Settings section headers stay pinned while scrolling. Patches: `MainActivity.kt`, `BackupRestoreScreens.kt`,
Megingiard `GamepadLayoutContainers.kt` / `GamepadScaffold.kt` hunks (the fork's additions to upstream's embedded
gamepad UI kit).

### 14. Stock-unfreeze timeout fix — `97765ce`
Unfreeze the suppressed stock process for **5000 ms** (not 350 ms) around `SCREEN_OFF`, or it ANRs, respawns with
`TccServices` bound and flashes its overlay. Patch: `PrivilegedHelperMain.kt`. Applies directly to Command Panel's
opt-in stock suppression.

## Licensing note

The fork remains under the BDSA Source-Available License (personal, non-commercial; no redistribution of modified
source or binaries). Code that is the fork's own authorship is ported here; anything that still derives from
upstream (the lines listed above, and the shape of the `patches/` hunks) is rewritten during integration. Keep
Command Panel personal and unpublished until that rewrite is done for every ported feature.
