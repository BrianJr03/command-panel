# Fork Changes: Detailed Breakdown

Fork `BrianJr03/better-dual-screen-assistant` compared with upstream `stormpanda/better-dual-screen-assistant`. Merge base `6b388b5`, fork head `1e681d5`.

Paths are shortened: `app/src/main/java/com/stormpanda/betterdualscreenassistant/` is dropped, and the test root is shown as `test/`.

## Totals
- **Commits:** 43 ahead, 0 behind (40 commits + 3 merges)
- **Files:** 115 changed: 76 added, 39 modified, 0 deleted
- **Lines:** +18,375 / -1,750
- **Dates:** 2026-11-13 to 2026-11-15

## Commits by type

| Type | Commits |
|---|---:|
| `feat(theme)` | 16 |
| `feat(settings)` | 5 |
| `feat(overlay)` | 3 |
| `feat(telemetry)` | 2 |
| `build` | 2 |
| `style(theme)` | 1 |
| `fix(privileged)` | 1 |
| `feat(replay)` | 1 |
| `fix(telemetry)` | 1 |
| `feat(recording)` | 1 |
| `other` | 1 |
| `refactor(theme)` | 1 |
| `fix(theme)` | 1 |
| `fix(overlay)` | 1 |
| `chore` | 1 |
| `docs` | 1 |
| `feat` | 1 |

## Commits (oldest first)

### `0552a41` build: rename app to BDSM, add release signing and side-by-side debug variant
*2026-11-13, 6 files, +37 / -5*

- `CLAUDE.md` (+23/-0)
- `app/src/debug/res/values/strings.xml` (+5/-0)
- `app/build.gradle.kts` (+3/-0)
- `privileged/PrivilegedHelperMain.kt` (+3/-2)
- `app/src/main/res/values/strings.xml` (+2/-2)
- `app/src/main/AndroidManifest.xml` (+1/-1)

### `35a492b` feat: add Thor RGB accent sync and tap-to-wake for dark-on-TV mode
*2026-11-13, 8 files, +426 / -2*

- `overlay/ExternalDisplayTapWaker.kt` (+183/-0)
- `system/SystemSettingsRepository.kt` (+98/-0)
- `privileged/ThorRgbController.kt` (+95/-0)
- `MainActivity.kt` (+15/-2)
- `ui/overlay/AssistantOverlayScreen.kt` (+14/-0)
- `gradle/gradle-daemon-jvm.properties` (+12/-0)
- `service/AssistantForegroundService.kt` (+8/-0)
- `app/src/main/res/values/strings.xml` (+1/-0)

### `3cd7c83` Merge branch 'main' of ../BDSManager App Origin
*2026-11-13, merge commit*

### `ef5b038` docs: check upstream for new releases at session start and explain patch notes
*2026-11-13, 1 files, +13 / -0*

- `CLAUDE.md` (+13/-0)

### `bfea99c` chore: add SessionStart hook to flag unmerged upstream releases with patch notes
*2026-11-13, 3 files, +70 / -12*

- `.claude/hooks/check-upstream-release.sh` (+50/-0)
- `.claude/settings.json` (+17/-0)
- `CLAUDE.md` (+3/-12)

### `a38cef7` feat(overlay): add arrange mode to drag-and-drop overlay cards and nav bar with persisted layout
*2026-11-13, 9 files, +1290 / -382*

- `ui/overlay/AssistantOverlayScreen.kt` (+541/-381)
- `ui/overlay/arrange/ArrangeMode.kt` (+434/-0)
- `system/OverlayLayout.kt` (+137/-0)
- `test/system/OverlayLayoutTest.kt` (+106/-0)
- `test/system/SystemSettingsRepositoryTest.kt` (+23/-0)
- `MainActivity.kt` (+22/-0)
- `system/SystemSettingsRepository.kt` (+19/-0)
- `app/src/main/res/values/strings.xml` (+6/-0)
- `app/src/test/java/com/stormpanda/megingiard/ui/GamepadScaffoldTouchNavigationTest.kt` (+2/-1)

### `198a9b2` feat(overlay): add quick launch nav button with app picker, label mode, and target screen settings
*2026-11-13, 9 files, +402 / -18*

- `MainActivity.kt` (+143/-2)
- `system/QuickLaunchApps.kt` (+79/-0)
- `ui/overlay/AssistantOverlayScreen.kt` (+58/-5)
- `system/SystemSettingsRepository.kt` (+44/-0)
- `overlay/ScreenOverlayManager.kt` (+29/-0)
- `test/system/SystemSettingsRepositoryTest.kt` (+24/-0)
- `ui/components/TccWidgets.kt` (+22/-9)
- `app/src/test/java/com/stormpanda/megingiard/ui/GamepadScaffoldTouchNavigationTest.kt` (+2/-2)
- `app/src/main/res/values/strings.xml` (+1/-0)

### `0335e21` feat(theme): add aurora glass, cartridge and tactical hud overlay looks
*2026-11-13, 20 files, +1536 / -114*

- `ui/components/TccWidgets.kt` (+390/-33)
- `ui/components/TccLookSurfaces.kt` (+389/-0)
- `ui/theme/TccLookSpec.kt` (+195/-1)
- `ui/theme/TccTheme.kt` (+180/-27)
- `ui/overlay/AssistantOverlayScreen.kt` (+132/-28)
- `ui/theme/TccLookColors.kt` (+108/-0)
- `test/ui/theme/TccThemeAndOverlayFormatTest.kt` (+60/-0)
- `THIRD_PARTY_NOTICES.md` (+22/-4)
- `docs/UI_AND_THEMING.md` (+22/-0)
- `app/src/main/assets/THIRD_PARTY_NOTICES.txt` (+14/-3)
- `ui/overlay/OverlayDialogs.kt` (+14/-10)
- `system/SystemSettingsRepository.kt` (+5/-4)
- `AGENTS.md` (+2/-1)
- `README.md` (+2/-2)
- `MainActivity.kt` (+1/-1)
- and 5 more

### `847c469` feat(theme): add aurora gradient presets with oled black, brighten tactical hud lines, remove ko-fi button
*2026-11-13, 11 files, +411 / -29*

- `ui/theme/AuroraGradientPresets.kt` (+94/-0)
- `MainActivity.kt` (+86/-13)
- `ui/overlay/OverlayDialogs.kt` (+75/-0)
- `ui/theme/TccLookColors.kt` (+51/-8)
- `test/ui/theme/TccThemeAndOverlayFormatTest.kt` (+39/-0)
- `ui/theme/TccTheme.kt` (+22/-1)
- `system/SystemSettingsRepository.kt` (+19/-0)
- `ui/overlay/AssistantOverlayScreen.kt` (+10/-2)
- `ui/components/TccLookSurfaces.kt` (+9/-5)
- `docs/UI_AND_THEMING.md` (+5/-0)
- `CLAUDE.md` (+1/-0)

### `95c3380` feat(settings): add general toggle to hide network speed in overlay status bar
*2026-11-13, 4 files, +37 / -5*

- `MainActivity.kt` (+15/-0)
- `system/SystemSettingsRepository.kt` (+13/-0)
- `ui/statusbar/StatusBarRow.kt` (+6/-4)
- `ui/overlay/AssistantOverlayScreen.kt` (+3/-1)

### `2639a13` feat(overlay): reorder nav bar, rename settings tab to home and edit to settings with matching icons
*2026-11-13, 6 files, +64 / -21*

- `app/src/main/res/drawable/ic_nav_home.xml` (+22/-0)
- `ui/overlay/AssistantOverlayScreen.kt` (+21/-21)
- `app/src/main/res/drawable/rp_ic_nav_home.xml` (+11/-0)
- `app/src/main/res/values/strings_local.xml` (+6/-0)
- `CLAUDE.md` (+3/-0)
- `ui/theme/TccTheme.kt` (+1/-0)

### `03e520d` fix(overlay): dismiss assistant before dark on tv so tap-to-wake is not blocked
*2026-11-13, 2 files, +26 / -1*

- `overlay/ScreenOverlayManager.kt` (+21/-0)
- `ui/overlay/AssistantOverlayScreen.kt` (+5/-1)

### `c097c39` feat(theme): add foundation for twelve new overlay looks
*2026-11-13, 31 files, +2525 / -414*

- `ui/theme/TccLookSpec.kt` (+413/-11)
- `ui/components/TccWidgets.kt` (+404/-221)
- `ui/components/looks/LookRenderers.kt` (+328/-0)
- `ui/components/TccLookSurfaces.kt` (+269/-105)
- `test/ui/theme/NewLooksFoundationTest.kt` (+268/-0)
- `ui/theme/TccLookColors.kt` (+228/-6)
- `ui/theme/TccTheme.kt` (+151/-16)
- `THIRD_PARTY_NOTICES.md` (+57/-3)
- `app/src/main/assets/THIRD_PARTY_NOTICES.txt` (+36/-3)
- `ui/components/looks/TerminalRenderers.kt` (+35/-0)
- `ui/components/looks/ComicPanelRenderers.kt` (+32/-0)
- `ui/components/looks/NeonArcadeRenderers.kt` (+32/-0)
- `ui/components/looks/SegmentLcdRenderers.kt` (+32/-0)
- `ui/components/looks/BloomRenderers.kt` (+27/-0)
- `ui/components/looks/CockpitRenderers.kt` (+27/-0)
- and 16 more

### `2886ea2` feat(theme): add soft console overlay look
*2026-11-13, 7 files, +331 / -12*

- `ui/components/looks/SoftConsoleRenderers.kt` (+266/-11)
- `test/ui/components/looks/SoftConsoleRenderersTest.kt` (+32/-0)
- `docs/UI_AND_THEMING.md` (+20/-0)
- `ui/components/looks/LookRenderers.kt` (+7/-0)
- `ui/theme/TccLookColors.kt` (+4/-0)
- `AGENTS.md` (+1/-0)
- `ui/theme/TccTheme.kt` (+1/-1)

### `717de8d` feat(settings): fold the device-native look into auto in the look pickers
*2026-11-13, 4 files, +50 / -16*

- `ui/theme/TccTheme.kt` (+25/-5)
- `test/ui/theme/NewLooksFoundationTest.kt` (+20/-6)
- `MainActivity.kt` (+3/-3)
- `ui/overlay/OverlayDialogs.kt` (+2/-2)

### `82eef88` feat(theme): add segment lcd overlay look
*2026-11-13, 4 files, +355 / -14*

- `ui/components/looks/SegmentLcdRenderers.kt` (+310/-13)
- `test/ui/components/looks/SegmentLcdRenderersTest.kt` (+28/-0)
- `docs/UI_AND_THEMING.md` (+16/-0)
- `ui/theme/TccTheme.kt` (+1/-1)

### `dd9835a` feat(theme): add bloom overlay look
*2026-11-13, 4 files, +335 / -12*

- `ui/components/looks/BloomRenderers.kt` (+274/-11)
- `test/ui/components/looks/BloomRenderersTest.kt` (+45/-0)
- `docs/UI_AND_THEMING.md` (+15/-0)
- `ui/theme/TccTheme.kt` (+1/-1)

### `655158a` feat(theme): add neon arcade overlay look
*2026-11-13, 5 files, +373 / -16*

- `ui/components/looks/NeonArcadeRenderers.kt` (+326/-13)
- `test/ui/components/looks/NeonArcadeRenderersTest.kt` (+28/-0)
- `docs/UI_AND_THEMING.md` (+16/-0)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `fb66395` feat(theme): add terminal overlay look
*2026-11-13, 9 files, +395 / -31*

- `ui/components/looks/TerminalRenderers.kt` (+296/-14)
- `test/ui/components/looks/TerminalRenderersTest.kt` (+51/-0)
- `docs/UI_AND_THEMING.md` (+17/-0)
- `ui/overlay/AssistantOverlayScreen.kt` (+12/-8)
- `ui/components/TccLookSurfaces.kt` (+6/-3)
- `ui/theme/TccLookSpec.kt` (+6/-1)
- `ui/components/TccWidgets.kt` (+4/-2)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `df63b79` feat(theme): add cockpit overlay look
*2026-11-13, 7 files, +353 / -16*

- `ui/components/looks/CockpitRenderers.kt` (+285/-11)
- `test/ui/components/looks/CockpitRenderersTest.kt` (+45/-0)
- `docs/UI_AND_THEMING.md` (+15/-0)
- `ui/components/looks/LookRenderers.kt` (+3/-1)
- `README.md` (+2/-2)
- `ui/components/TccWidgets.kt` (+2/-1)
- `ui/theme/TccTheme.kt` (+1/-1)

### `6079973` feat(theme): add sticker book overlay look
*2026-11-13, 7 files, +283 / -14*

- `ui/components/looks/StickerBookRenderers.kt` (+238/-11)
- `test/ui/components/looks/StickerBookRenderersTest.kt` (+23/-0)
- `docs/UI_AND_THEMING.md` (+15/-0)
- `ui/components/looks/LookRenderers.kt` (+3/-0)
- `README.md` (+2/-2)
- `ui/overlay/AssistantOverlayScreen.kt` (+1/-0)
- `ui/theme/TccTheme.kt` (+1/-1)

### `2f6b0f5` fix(theme): keep sticker book gauge rims clear of the gauge labels
*2026-11-13, 2 files, +33 / -19*

- `ui/components/looks/StickerBookRenderers.kt` (+23/-19)
- `test/ui/components/looks/StickerBookRenderersTest.kt` (+10/-0)

### `6696d90` feat(theme): add industrial overlay look
*2026-11-13, 5 files, +286 / -14*

- `ui/components/looks/IndustrialRenderers.kt` (+231/-11)
- `test/ui/components/looks/IndustrialRenderersTest.kt` (+37/-0)
- `docs/UI_AND_THEMING.md` (+15/-0)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `5b8246b` refactor(theme): extend look renderer hooks for card titles and text styles
*2026-11-13, 12 files, +33 / -14*

- `ui/components/looks/LookRenderers.kt` (+12/-2)
- `ui/components/TccLookSurfaces.kt` (+3/-2)
- `ui/components/looks/BloomRenderers.kt` (+2/-1)
- `ui/components/looks/CockpitRenderers.kt` (+2/-1)
- `ui/components/looks/IndustrialRenderers.kt` (+2/-1)
- `ui/components/looks/NeonArcadeRenderers.kt` (+2/-1)
- `ui/components/looks/SegmentLcdRenderers.kt` (+2/-1)
- `ui/components/looks/SoftConsoleRenderers.kt` (+2/-1)
- `ui/components/looks/StickerBookRenderers.kt` (+2/-1)
- `ui/components/looks/TerminalRenderers.kt` (+2/-1)
- `ui/components/TccWidgets.kt` (+1/-1)
- `ui/overlay/AssistantOverlayScreen.kt` (+1/-1)

### `63e74b1` feat(theme): add jelly overlay look
*2026-11-13, 5 files, +313 / -14*

- `ui/components/looks/JellyRenderers.kt` (+271/-11)
- `test/ui/components/looks/JellyRenderersTest.kt` (+23/-0)
- `docs/UI_AND_THEMING.md` (+16/-0)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `a525734` feat(theme): add comic panel overlay look
*2026-11-13, 5 files, +393 / -16*

- `ui/components/looks/ComicPanelRenderers.kt` (+344/-13)
- `test/ui/components/looks/ComicPanelRenderersTest.kt` (+31/-0)
- `docs/UI_AND_THEMING.md` (+15/-0)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `c5af7f0` feat(theme): add y2k chrome overlay look
*2026-11-13, 5 files, +341 / -14*

- `ui/components/looks/Y2kChromeRenderers.kt` (+279/-11)
- `test/ui/components/looks/Y2kChromeRenderersTest.kt` (+42/-0)
- `docs/UI_AND_THEMING.md` (+17/-0)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `f8cc0d4` feat(theme): add e-ink overlay look
*2026-11-13, 5 files, +232 / -14*

- `ui/components/looks/EInkRenderers.kt` (+192/-11)
- `test/ui/components/looks/EInkRenderersTest.kt` (+21/-0)
- `docs/UI_AND_THEMING.md` (+16/-0)
- `README.md` (+2/-2)
- `ui/theme/TccTheme.kt` (+1/-1)

### `13eaa5c` feat(settings): add settings backup and restore
*2026-11-13, 16 files, +1975 / -37*

- `system/backup/SettingsBackupManager.kt` (+422/-0)
- `test/system/backup/SettingsBackupTest.kt` (+378/-0)
- `ui/backup/BackupRestoreScreens.kt` (+356/-0)
- `privileged/BackupFileStore.kt` (+166/-0)
- `test/privileged/BackupFileStoreTest.kt` (+129/-0)
- `system/backup/SettingsBackupCodec.kt` (+112/-0)
- `system/backup/BackupSchema.kt` (+102/-0)
- `privileged/PrivilegedBridge.kt` (+95/-0)
- `MainActivity.kt` (+62/-3)
- `privileged/PrivilegedHelperMain.kt` (+47/-7)
- `docs/TECHNICAL_ARCHITECTURE.md` (+43/-26)
- `system/SystemSettingsRepository.kt` (+24/-0)
- `docs/SYSTEM_INTEGRATION_REFERENCE.md` (+19/-0)
- `docs/FUNCTIONAL_SPECIFICATION.md` (+17/-0)
- `docs/SECURITY.md` (+2/-1)
- and 1 more

### `4d064d2` feat(telemetry): add battery time remaining estimate
*2026-11-14, 17 files, +834 / -24*

- `test/system/BatteryEstimatorTest.kt` (+228/-0)
- `system/BatteryEstimator.kt` (+217/-0)
- `system/BatteryEstimateMonitor.kt` (+193/-0)
- `ui/overlay/AssistantOverlayScreen.kt` (+58/-13)
- `ui/statusbar/StatusBarRow.kt` (+32/-1)
- `MainActivity.kt` (+29/-1)
- `docs/FUNCTIONAL_SPECIFICATION.md` (+17/-0)
- `system/TelemetryMonitor.kt` (+16/-0)
- `system/SystemSettingsRepository.kt` (+15/-0)
- `system/backup/SettingsBackupManager.kt` (+9/-0)
- `service/AssistantForegroundService.kt` (+5/-1)
- `docs/SYSTEM_INTEGRATION_REFERENCE.md` (+5/-1)
- `system/DeviceProfile.kt` (+3/-1)
- `app/src/test/java/com/stormpanda/megingiard/ui/GamepadScaffoldTouchNavigationTest.kt` (+3/-3)
- `README.md` (+2/-2)
- and 2 more

### `67818fa` Add notice regarding repository derivation and usage
*2026-11-14, 1 files, +12 / -0*

- `README.md` (+12/-0)

### `8a37f8f` feat(recording): add replay buffer with save-last-n-seconds clips
*2026-11-14, 35 files, +3885 / -527*

- `privileged/ScreenRecorderEngine.kt` (+624/-136)
- `privileged/ClearTasksPolicy.kt` (+290/-0)
- `test/privileged/ClearTasksPolicyTest.kt` (+265/-0)
- `privileged/PrivilegedHelperMain.kt` (+258/-70)
- `test/privileged/ReplayRingBufferTest.kt` (+250/-0)
- `system/SystemSettingsRepository.kt` (+243/-2)
- `MainActivity.kt` (+231/-166)
- `test/system/ReplayBufferRepositoryTest.kt` (+197/-0)
- `privileged/ReplayRingBuffer.kt` (+196/-0)
- `test/privileged/PrivilegedBridgeTest.kt` (+160/-6)
- `privileged/PrivilegedBridge.kt` (+159/-28)
- `ui/replay/ReplayBufferSettingsCards.kt` (+158/-0)
- `test/privileged/PrivilegedBinderProviderAndHelperTest.kt` (+142/-44)
- `service/AssistantForegroundService.kt` (+117/-8)
- `overlay/ScreenOverlayManager.kt` (+107/-4)
- and 20 more

### `d44c738` feat(settings): make remaining section labels sticky
*2026-11-14, 2 files, +130 / -128*

- `MainActivity.kt` (+113/-112)
- `ui/backup/BackupRestoreScreens.kt` (+17/-16)

### `ffbeb7a` feat(settings): add long-press drag and select-key quick toggle reordering
*2026-11-14, 5 files, +1036 / -561*

- `ui/settings/QuickTogglesReorderCard.kt` (+870/-0)
- `ui/settings/QuickTileReorderMath.kt` (+82/-0)
- `test/ui/settings/QuickTileReorderMathTest.kt` (+78/-0)
- `docs/FUNCTIONAL_SPECIFICATION.md` (+5/-4)
- `MainActivity.kt` (+1/-557)

### `ac39ffa` build: run unit tests on a jdk 21 toolchain
*2026-11-14, 2 files, +14 / -0*

- `app/build.gradle.kts` (+10/-0)
- `settings.gradle.kts` (+4/-0)

### `d00a4e4` fix(telemetry): hide limit reached label under the pwr ring
*2026-11-14, 2 files, +7 / -2*

- `ui/overlay/AssistantOverlayScreen.kt` (+6/-1)
- `docs/FUNCTIONAL_SPECIFICATION.md` (+1/-1)

### `96b965e` Merge pull request #1 from BrianJr03/feat/backup-battery-replay
*2026-11-14, merge commit*

### `14f0ce1` feat(replay): add auto re-arm, double-press arm dialog and ok/cancel arm buttons
*2026-11-14, 13 files, +305 / -32*

- `service/AssistantForegroundService.kt` (+92/-16)
- `ui/overlay/OverlayDialogs.kt` (+76/-4)
- `test/service/AssistantForegroundServiceTest.kt` (+53/-4)
- `system/SystemSettingsRepository.kt` (+23/-0)
- `ui/replay/ReplayBufferSettingsCards.kt` (+21/-0)
- `overlay/ScreenOverlayManager.kt` (+15/-2)
- `ui/overlay/AssistantOverlayScreen.kt` (+11/-0)
- `docs/FUNCTIONAL_SPECIFICATION.md` (+6/-5)
- `CLAUDE.md` (+2/-0)
- `system/backup/BackupSchema.kt` (+2/-0)
- `system/backup/SettingsBackupManager.kt` (+2/-0)
- `app/src/main/res/values/strings_local.xml` (+1/-0)
- `app/src/test/java/com/stormpanda/megingiard/ui/GamepadScaffoldTouchNavigationTest.kt` (+1/-1)

### `7cb5b2f` feat(telemetry): remove battery estimate under the pwr ring
*2026-11-14, 6 files, +33 / -78*

- `ui/overlay/AssistantOverlayScreen.kt` (+14/-54)
- `system/BatteryEstimateMonitor.kt` (+8/-6)
- `MainActivity.kt` (+5/-14)
- `docs/FUNCTIONAL_SPECIFICATION.md` (+3/-3)
- `test/system/BatteryEstimatorTest.kt` (+2/-0)
- `docs/SYSTEM_INTEGRATION_REFERENCE.md` (+1/-1)

### `97765ce` fix(privileged): stop stock assistant anr respawn after screen-off thaw
*2026-11-14, 2 files, +6 / -1*

- `privileged/PrivilegedHelperMain.kt` (+5/-1)
- `CLAUDE.md` (+1/-0)

### `eb4b399` feat(theme): add soft console oled look and widen soft console card gaps
*2026-11-14, 7 files, +56 / -8*

- `ui/theme/TccLookSpec.kt` (+19/-1)
- `test/ui/theme/NewLooksFoundationTest.kt` (+19/-4)
- `ui/components/TccLookSurfaces.kt` (+7/-1)
- `ui/theme/TccTheme.kt` (+5/-0)
- `docs/UI_AND_THEMING.md` (+4/-0)
- `CLAUDE.md` (+1/-0)
- `MainActivity.kt` (+1/-2)

### `4752174` style(theme): balance soft console spacing and keep ring titles and mode labels clear
*2026-11-14, 3 files, +31 / -9*

- `ui/theme/TccLookSpec.kt` (+20/-6)
- `test/ui/theme/NewLooksFoundationTest.kt` (+10/-2)
- `ui/components/TccWidgets.kt` (+1/-1)

### `1e681d5` Merge pull request #2 from BrianJr03/feat/replay-auto-arm
*2026-11-15, merge commit*

## Files added (76)

| Lines | File |
|---:|---|
| 870 | `ui/settings/QuickTogglesReorderCard.kt` |
| 567 | `ui/components/TccLookSurfaces.kt` |
| 472 | `system/backup/SettingsBackupManager.kt` |
| 434 | `ui/overlay/arrange/ArrangeMode.kt` |
| 378 | `test/system/backup/SettingsBackupTest.kt` |
| 377 | `ui/theme/TccLookColors.kt` |
| 363 | `ui/components/looks/ComicPanelRenderers.kt` |
| 359 | `ui/backup/BackupRestoreScreens.kt` |
| 350 | `ui/components/looks/LookRenderers.kt` |
| 346 | `ui/components/looks/NeonArcadeRenderers.kt` |
| 330 | `ui/components/looks/SegmentLcdRenderers.kt` |
| 318 | `ui/components/looks/TerminalRenderers.kt` |
| 305 | `test/ui/theme/NewLooksFoundationTest.kt` |
| 302 | `ui/components/looks/CockpitRenderers.kt` |
| 295 | `ui/components/looks/Y2kChromeRenderers.kt` |
| 291 | `ui/components/looks/BloomRenderers.kt` |
| 290 | `privileged/ClearTasksPolicy.kt` |
| 287 | `ui/components/looks/JellyRenderers.kt` |
| 283 | `ui/components/looks/SoftConsoleRenderers.kt` |
| 265 | `test/privileged/ClearTasksPolicyTest.kt` |
| 259 | `ui/components/looks/StickerBookRenderers.kt` |
| 250 | `test/privileged/ReplayRingBufferTest.kt` |
| 248 | `ui/components/looks/IndustrialRenderers.kt` |
| 230 | `test/system/BatteryEstimatorTest.kt` |
| 217 | `system/BatteryEstimator.kt` |
| 208 | `ui/components/looks/EInkRenderers.kt` |
| 197 | `test/system/ReplayBufferRepositoryTest.kt` |
| 196 | `privileged/ReplayRingBuffer.kt` |
| 195 | `system/BatteryEstimateMonitor.kt` |
| 183 | `overlay/ExternalDisplayTapWaker.kt` |
| 179 | `ui/replay/ReplayBufferSettingsCards.kt` |
| 166 | `privileged/BackupFileStore.kt` |
| 137 | `system/OverlayLayout.kt` |
| 129 | `test/privileged/BackupFileStoreTest.kt` |
| 114 | `system/backup/BackupSchema.kt` |
| 112 | `system/backup/SettingsBackupCodec.kt` |
| 106 | `test/system/OverlayLayoutTest.kt` |
| 95 | `privileged/ThorRgbController.kt` |
| 94 | `ui/theme/AuroraGradientPresets.kt` |
| 82 | `ui/settings/QuickTileReorderMath.kt` |
| 79 | `system/QuickLaunchApps.kt` |
| 78 | `test/ui/settings/QuickTileReorderMathTest.kt` |
| 51 | `test/ui/components/looks/TerminalRenderersTest.kt` |
| 50 | `.claude/hooks/check-upstream-release.sh` |
| 45 | `test/ui/components/looks/BloomRenderersTest.kt` |
| 45 | `test/ui/components/looks/CockpitRenderersTest.kt` |
| 42 | `test/ui/components/looks/Y2kChromeRenderersTest.kt` |
| 37 | `test/ui/components/looks/IndustrialRenderersTest.kt` |
| 35 | `CLAUDE.md` |
| 33 | `test/ui/components/looks/StickerBookRenderersTest.kt` |
| 32 | `test/ui/components/looks/SoftConsoleRenderersTest.kt` |
| 31 | `test/ui/components/looks/ComicPanelRenderersTest.kt` |
| 28 | `test/ui/components/looks/NeonArcadeRenderersTest.kt` |
| 28 | `test/ui/components/looks/SegmentLcdRenderersTest.kt` |
| 23 | `test/ui/components/looks/JellyRenderersTest.kt` |
| 22 | `app/src/main/res/drawable/ic_nav_home.xml` |
| 21 | `test/ui/components/looks/EInkRenderersTest.kt` |
| 19 | `app/src/main/res/drawable/rp_ic_qs_replay_buffer.xml` |
| 17 | `.claude/settings.json` |
| 15 | `app/src/main/res/drawable/ic_qs_replay_buffer.xml` |
| 12 | `gradle/gradle-daemon-jvm.properties` |
| 11 | `app/src/main/res/drawable/rp_ic_nav_home.xml` |
| 7 | `app/src/main/res/values/strings_local.xml` |
| 5 | `app/src/debug/res/values/strings.xml` |
| 0 | `app/src/main/res/font/tcc_chrome_wide.ttf` |
| 0 | `app/src/main/res/font/tcc_comic.ttf` |
| 0 | `app/src/main/res/font/tcc_condensed_bold.ttf` |
| 0 | `app/src/main/res/font/tcc_condensed_extrabold.ttf` |
| 0 | `app/src/main/res/font/tcc_condensed_semibold.ttf` |
| 0 | `app/src/main/res/font/tcc_mono_bold.ttf` |
| 0 | `app/src/main/res/font/tcc_mono_regular.ttf` |
| 0 | `app/src/main/res/font/tcc_rounded_bold.ttf` |
| 0 | `app/src/main/res/font/tcc_segment_bold.ttf` |
| 0 | `app/src/main/res/font/tcc_serif_italic.ttf` |
| 0 | `app/src/main/res/font/tcc_serif_regular.ttf` |
| 0 | `app/src/main/res/font/tcc_stencil.ttf` |

## Upstream files modified (39)

| + | − | File |
|---:|---:|---|
| 787 | 413 | `ui/overlay/AssistantOverlayScreen.kt` |
| 686 | 130 | `ui/components/TccWidgets.kt` |
| 634 | 1 | `ui/theme/TccLookSpec.kt` |
| 624 | 136 | `privileged/ScreenRecorderEngine.kt` |
| 623 | 772 | `MainActivity.kt` |
| 503 | 6 | `system/SystemSettingsRepository.kt` |
| 370 | 34 | `ui/theme/TccTheme.kt` |
| 313 | 80 | `privileged/PrivilegedHelperMain.kt` |
| 245 | 19 | `privileged/PrivilegedBridge.kt` |
| 224 | 0 | `docs/UI_AND_THEMING.md` |
| 211 | 11 | `ui/overlay/OverlayDialogs.kt` |
| 206 | 9 | `service/AssistantForegroundService.kt` |
| 172 | 6 | `overlay/ScreenOverlayManager.kt` |
| 160 | 6 | `test/privileged/PrivilegedBridgeTest.kt` |
| 142 | 44 | `test/privileged/PrivilegedBinderProviderAndHelperTest.kt` |
| 112 | 4 | `ui/statusbar/StatusBarRow.kt` |
| 102 | 8 | `test/service/AssistantForegroundServiceTest.kt` |
| 99 | 0 | `test/ui/theme/TccThemeAndOverlayFormatTest.kt` |
| 76 | 4 | `THIRD_PARTY_NOTICES.md` |
| 63 | 0 | `app/src/main/java/com/stormpanda/megingiard/ui/GamepadLayoutContainers.kt` |
| 63 | 7 | `docs/FUNCTIONAL_SPECIFICATION.md` |
| 63 | 27 | `docs/TECHNICAL_ARCHITECTURE.md` |
| 47 | 3 | `app/src/main/assets/THIRD_PARTY_NOTICES.txt` |
| 47 | 0 | `test/system/SystemSettingsRepositoryTest.kt` |
| 32 | 1 | `docs/SYSTEM_INTEGRATION_REFERENCE.md` |
| 27 | 1 | `app/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt` |
| 19 | 6 | `README.md` |
| 16 | 0 | `system/TelemetryMonitor.kt` |
| 13 | 0 | `app/build.gradle.kts` |
| 11 | 9 | `app/src/test/java/com/stormpanda/megingiard/ui/GamepadScaffoldTouchNavigationTest.kt` |
| 10 | 2 | `app/src/main/res/values/strings.xml` |
| 7 | 0 | `system/QuickSettingTileId.kt` |
| 7 | 6 | `test/privileged/PServerClientTest.kt` |
| 4 | 0 | `settings.gradle.kts` |
| 3 | 1 | `AGENTS.md` |
| 3 | 1 | `system/DeviceProfile.kt` |
| 3 | 1 | `docs/SECURITY.md` |
| 2 | 1 | `test/overlay/ScreenOverlayManagerTest.kt` |
| 1 | 1 | `app/src/main/AndroidManifest.xml` |

## By area

| Area | Files | + | − |
|---|---:|---:|---:|
| `ui/components/looks` | 13 | 3,880 | 0 |
| `test` | 28 | 2,913 | 65 |
| `privileged` | 7 | 1,929 | 235 |
| `system` | 11 | 1,855 | 7 |
| `ui/theme` | 4 | 1,475 | 35 |
| `ui/overlay` | 3 | 1,432 | 424 |
| `ui/components` | 2 | 1,253 | 130 |
| `ui/settings` | 2 | 952 | 0 |
| `MainActivity.kt` + repo root files | 6 | 760 | 783 |
| `docs` | 5 | 385 | 36 |
| `ui/backup` | 1 | 359 | 0 |
| `overlay` | 2 | 355 | 6 |
| `service` | 1 | 206 | 9 |
| `ui/replay` | 1 | 179 | 0 |
| `app` (build files, Megingiard UI, other) | 7 | 167 | 14 |
| `ui/statusbar` | 1 | 112 | 4 |
| `res` | 18 | 84 | 2 |
| `.claude` | 2 | 67 | 0 |
| `gradle` | 1 | 12 | 0 |
