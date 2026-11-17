# Stock DualScreenAssistant teardown (AYN Thor)

Source: `com.odin.dualscreen.assistant` v1.3 (2026-0206-1614), versionCode 3, pulled from
`/system/app/DualScreenAssistant/DualScreenAssistant.apk` on build `Thor_V1.0.0.377_20260206`.
sha256 `b199e43eb8b3ff63045da955a996693103d7e6d2bc6b7830d079421f47729822`.
Signed by `CN=AYN, O=AYN, L=Shenzhen` (cert sha256 `9e1339d6…b5f6`) — see `device/signing-cert.txt`.

Layout of this folder:

| Path | Contents |
|---|---|
| `apk/` | pulled APK + odex/vdex |
| `decompiled/sources/` | jadx Java (`--deobf`, so obfuscated packages are `p0xx…`) |
| `decompiled/resources/` | jadx resources |
| `apktool/` | exact manifest, `res/`, smali |
| `device/` | `dumpsys package`, `settings list`, `service list`, `getprop` snapshots |

Paths below are relative to `decompiled/sources/` (`SRC`) or `apktool/res/` (`RES`).
App code is `SRC/com/odin/dualscreen/assistant/`; the vendor shared lib is `SRC/com/p090ro/…`.

jadx reported 19 errors in 3,078 classes. None are in AYN or vendor code (all in kotlin/androidx/RxTool libraries).
One readable warning in `QuickSettingsPanel.m9044A()` (string switch not restored).

Live captures (dark theme, dual mode, 2026-11-16) are in `device/shots/`: Settings tab pages 1–2, Mode tab, and the
Focus / Performance / Fan / Handle Style / Theme dialogs. `device/dumpsys-window-stock-overlay.txt` and
`device/dumpsys-window-stock-dialog.txt` confirm the overlay params in §2 and show the option dialog as a
wrap-content `BOOT_PROGRESS` window with `DIM_BEHIND | ALT_FOCUSABLE_IM | SPLIT_TOUCH`.

**Not captured:** light theme, the fan Custom seekbar (hidden at Standard performance), the "Only top screen" tip
dialog (closing it switches the mode), single-screen modes, toasts, and a view-hierarchy dump
(`uiautomator dump` on this build only dumps display 0).

---

## 1. Manifest and process model

- `sharedUserId="android.uid.system"`, `persistent="true"`, `directBootAware`, `defaultToDeviceProtectedStorage`, `hardwareAccelerated="false"`.
- `application.ThisApplication` — binds `TccServices` at startup (rebinds 1 s after disconnect / binder death).
- `services.TccServices` — not exported; the whole UI. Returns an in-process `ITccServices` binder.
- `activities.MainActivity` — **`enabled="false"`**. Same `main_layout` hosted in an Activity with Fragments; dead on this device.
- Bundled RxTool (`com.tamsiree`) contributes an exported `ActivityWebView`, a crash activity and a crash provider. Not needed for a clone.
- Declared permissions are ordinary (network, location, camera, storage, `KILL_BACKGROUND_PROCESSES`); everything privileged comes from the system uid.
- No `BootBroadcastReceiver` in this version's manifest (BDSA's scripts `pm enable` one; harmless no-op here).

## 2. Service lifecycle (`services/TccServices.java`)

`onCreate`:
1. Finds the first display with id ≠ 0 (`ScreenUtil.m9354e`), makes a display context, takes its `WindowManager`.
2. Registers: `TccKeyReceiver` (dynamic, `action.tcc.button.key.event`), `VolumeKeyReceiver` (`action.volume.key.event`), a `DisplayListener` (logging only), a `ContentObserver` on `Settings.System dual_screen_display_mode`.
3. Binds Quickstep: action `android.intent.action.QUICKSTEP_SERVICE`, package from framework string `config_recentsComponentName`.
4. Registers a `HOME` input listener with the mapping service.
5. Fires the display-mode observer once (so cover windows match the stored mode after a restart).

Overlay window (`m8937u`):

| Field | Value |
|---|---|
| size | bottom display real size (1240 × 1080) |
| type | `2021` (`TYPE_BOOT_PROGRESS`) |
| flags | `0x90020600` = `DRAWS_SYSTEM_BAR_BACKGROUNDS \| LOCAL_FOCUS_MODE \| ALT_FOCUSABLE_IM \| FULLSCREEN \| LAYOUT_NO_LIMITS` |
| format | `TRANSLUCENT` |
| softInputMode | 48 |
| gravity | `CENTER` |
| windowAnimations | `@style/tcc_anim` |

Show = inflate `main_layout` (cached `TccView`), restore last tab, `addView`. Hide = dismiss dialogs, `removeView`.
`onConfigurationChanged` drops the cached view if it is not attached. A theme change removes the overlay and re-adds it 500 ms later.

### AYN button (`receivers/TccKeyReceiver.java` → `TccServices`)

Extras `key_action_down` and `key_isLongPress` (both default **true** when absent).

| Event | Behavior |
|---|---|
| down, not long | no-op |
| down, long | if bottom cover is not showing: switch to mode 1 (top only) via `ScreenModeControlPanel.m8970e`, set "swallow next up" |
| up | swallowed once after a long press; ignored while a pending re-attach timer runs; if the bottom cover is showing → back to mode 0 (unless the panel's 500 ms click guard blocks); ignored on the keyguard; otherwise toggle overlay with a **400 ms** debounce |

### HOME key
Listener returns `true` (consumed) after hiding the overlay; ignored when the bottom cover is showing.

### Nav bar (`views/navigationbar/NavigationBar.java`, `RES/layout/navigation_bar_layout.xml`)
Three buttons: **Mode**, **Task**, **Settings**. Last tab persisted in DataStore `current_navigation_interface_name` (1 = Mode, 4 = Settings).
Task: 400 ms debounce, skipped while the top cover is showing; calls `IOverviewProxy` txn 6 `(displayId, packageName)` oneway and hides the overlay; if the call can't be made it rebinds Quickstep.
A fourth "Application" handler (launches `com.android.launcher3/.secondarydisplay.SecondaryDisplayLauncher`) exists but has no button.

## 3. Screen modes

`Settings.System dual_screen_display_mode`: 0 both, 1 top only, 2 bottom only.

Writers (`views/mainpanel/screencontrol/screenmode/ScreenModeControlPanel.java` companion):

| Target | Steps |
|---|---|
| → 1 or 2 | if coming from 0, save `screen_focus_lock`, `enable_system_key_focus_lock` and the pin-screen mode to DataStore (`storage_in_screen_focus_lock`, `storage_in_enable_system_key_focus_lock`, `storage_in_ime_pin_mode`); set mode; `screen_focus_lock` = 1 or 2; `enable_system_key_focus_lock` = true; `setPinScreenMode(0)`; (→1 also dismisses dialogs) |
| → 0 | set mode 0; restore the three saved values |

`getPinScreenMode` / `setPinScreenMode` are vendor additions on the WindowManager, called by reflection.

Reaction (`TccServices` observer → `com/p090ro/dualscreen/assistant/utils/ScreenUtil.java`):

| Mode | Top cover | Bottom cover | `sys.temp.disabled.touch` |
|---|---|---|---|
| 0 | removed | removed | `0,0` |
| 1 | removed | added | `0,1` |
| 2 | added | removed | `1,0` |

Cover window: black view (`p058T0/C0271c`, animated show/hide), type `2021`, flags `0x20600` (`ALT_FOCUSABLE_IM | FULLSCREEN | LAYOUT_NO_LIMITS`), translucent, title `primaryScreenTopLayout`. Top one is `MATCH_PARENT` on display 0; bottom one is real-size on the bottom display.
**Stock does not change brightness when a screen is "off".** Then refreshes the quick-settings tiles.

Tip before "Only top screen": shown while DataStore `is_show_close_secondary_screen_tip` is true (default); "Do not show again" clears it; mode switches 100 ms after the dialog closes.

Keyguard (`application/ThisApplication.java`, `receivers/ScreenReceiver`):
- At startup or `SCREEN_ON` with keyguard up: save pin mode to DataStore `ime_mode_record` and set pin mode 0. In mode 2, also remove the top cover and set `screen_focus_lock` = 1.
- `USER_PRESENT`: in mode 2 re-add the top cover and set focus lock 2; restore the saved pin mode.
- `SCREEN_OFF`: hide the overlay.

Crash handler and `onTerminate`: touch back to `0,0`, mode back to 0 with saved values restored, then the process exits.

## 4. Settings keys (all `Settings.System`, user -2; `SRC/p034L0/C0118a.java`)

Used by this app:

| Key | Type / values | Default | Used for |
|---|---|---|---|
| `dual_screen_display_mode` | 0/1/2 | 0 | screen mode |
| `screen_focus_lock` | 0 touch-switch, 1 top, 2 bottom | 0 | focus tile + modes |
| `enable_system_key_focus_lock` | bool | false | set true in single-screen modes |
| `dual_screen_brightness_level` | 0–100 | 0 | dual slider's own position |
| `secondary_screen_volume_level` | 0–15 | 7 | bottom volume |
| `secondary_screen_volume_level_for_headphones` | 0–15 | 7 | observed only, never read/written |
| `performance_mode` | 0/1/2 | 0 | performance tile |
| `fan_mode` | 0 off, 1 quiet, 4 smart, 5 sport, 6 custom | 1 on Thor | fan tile |
| `fan_speed` | int | 25000 | custom fan seekbar |
| `temp_abxy_layout_mode` | 0 Xbox, 1 Odin, 2 disabled | 1 on Thor | controller tile |
| `flip_button_layout`, `no_create_gamepad_button_layout` | bool | false | written alongside controller style (Xbox: flip=1; disabled: no_create=1) |
| `is_charging_separation` | bool | false | tile |
| `vibrate_on` | 0/1 | 1 | tile |
| `joystick_light_enabled` | string `"a,b"` | `"0,0"` | LED tile state (active if either is 1) |
| `force_landscape` | bool | false | tile |
| `tcc_theme_mode` | 1 system, 3 light, 4 dark (2 = dark) | 4 | theme tile |
| `min_refresh_rate`, `peak_refresh_rate` | float | — | 60/120 toggle |
| `screen_brightness` | — | — | observed to refresh sliders (and written on SDK < 33) |
| `app_whiteList` | comma list | "" | clear-memory soft-kill list |
| `tcc_display_command` | `open-<ts>` / `close-…` | `close-` | MainActivity only |

Declared in the same file but unused here (shared with `com.odin.settings`): `trigger_input_mode`, `percent_80_charge_limit`, `global_gamepad_to_mouse_mode`, `key_adapter_enable`, `is_show_floatingIcon`, `keep_screen_on`, `prevent_press_home_accidentally`, `led_light_brightness_percent` (float, default 0.5), `is_display_navi_secondary_home`, `status_bar_network_speed`, and the rest of the remap / dead-zone / equalizer keys.

App-private DataStore (`<pkg>_preferences`): `current_navigation_interface_name`, `is_show_close_secondary_screen_tip`, `ime_mode_record`, `storage_in_screen_focus_lock`, `storage_in_enable_system_key_focus_lock`, `storage_in_ime_pin_mode`.

System properties: `sys.temp.disabled.touch` (read/write, `"top,bottom"`), `sys.light.curr.is.show` (read), `ro.product.model` (device checks in `SRC/p028J0/C0104d.java`; `"AYN Thor"` / `"AYN Thor Lite"`), `persist.com.ro.dualscreen.assistant.debug` (logging).

## 5. Broadcasts and binders

| What | Details |
|---|---|
| AYN button | `action.tcc.button.key.event`; bool `key_action_down`, bool `key_isLongPress` |
| Volume keys | `action.volume.key.event`; string `key_type` = `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` |
| LED toggle | implicit `com.ro.external.request.light.adj.action`; bool `isShow` = !`sys.light.curr.is.show`; 300 ms debounce |
| FPS calibration | receives `user.enter.fps.calibration.action`; bool `calibration.state` |
| Root shell | `ServiceManager "PServerBinder"`, txn 0, no interface token, `writeStringArray([cmd, "0"\|"1"])` → `createByteArray()` (`SRC/p028J0/C0103c.java`). Stock only sends `chmod 444 <sysfs path>` after a failed read |
| Fan | `ServiceManager "SettingsController"`, descriptor `com.ro.settings.IExternalControlManager`, txn 1 `writeString("FanProvider")` → binder `com.ro.settings.IFanControlProvider`: txn 1 → int, txn 2 → int (used as the custom seekbar range), txn 3 → float (×100 = fan %), txn 10 (int) |
| Recents | bound Quickstep service, descriptor `com.android.systemui.shared.recents.IOverviewProxy`, txn 6 oneway `(int displayId, String pkg)` |
| Key mapping | bind action `com.ro.mapping.action.MAPPING_SERVICE` (first `queryIntentServices` match); descriptor `com.ro.mapping.sdk.IServerApi`; txn 2 add listener `(binder, String[])`, txn 3 remove; listener descriptor `com.ro.mapping.sdk.listener.OnInputEventListener` txn 1 `(int flag, InputEvent)` → int. Stock calls `System.exit(0)` if this binder dies |
| In-process | `com.ro.dualscreen.assistant.ITccServices`: show, hide, refresh view, set theme |

Shell via `Runtime.exec` (works because of system uid): `service call SurfaceFlinger 1013`, `cat proc/meminfo`.

## 6. Features

**Brightness** (`views/mainpanel/screencontrol/sliding/controlview2/screen/`, base `SRC/p176y0/AbstractC3212c.java`, calls in `SRC/p031K0/C0114c.java`)
- Sliders 0–100. Primary = display 0, External = the overlay's display.
- Drag: `DisplayManager.setTemporaryBrightness(displayId, f)`; release: `setBrightness(displayId, f)`; read: `Display.getBrightnessInfo()` normalized by min/max; listener with event mask 8.
- Dual slider stores `dual_screen_brightness_level` and pushes the same progress to both. Its end icon calls `DevicePolicyManager.lockNow()`.
- L1/R1 (keycodes 102/103) step ±10 %.

**Volume** (`…/controlview2/volume/`, `SRC/p049Q0/C0197a.java`)
- Top: `AudioManager` `STREAM_MUSIC`. Bottom: `secondary_screen_volume_level` 0–15.
- Volume keys (30 ms debounce): UP raises the bottom level only when music is already at max; DOWN lowers it only when music is at min.

**Frame rate** (`views/mainpanel/systemset/framerate/`)
- Toggle between 60 and the highest supported mode of display 0 by writing `min_refresh_rate` + `peak_refresh_rate`. Blocked during FPS calibration (toast).
- FPS: `service call SurfaceFlinger 1013` every 1500 ms, frame-counter delta, rounded up, capped at peak refresh rate.

**Hardware panel** (`views/mainpanel/systemset/hardware/`, monitors in `SRC/p055S0/`), starts 500 ms after attach

| Item | Source | Interval |
|---|---|---|
| TEMP | `/sys/devices/virtual/thermal/thermal_zone48/temp` (÷1000 if out of −30..250) | 1500 ms |
| FAN | FanProvider txn 3 × 100 | 1500 ms |
| CPU | `/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq` / `cpuinfo_max_freq` | 1500 ms |
| GPU | `/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq` / `max_freq` | 1500 ms |
| PWR | `/sys/class/power_supply/battery/current_now` × `voltage_now`, ring −20..25 W | 2000 ms |
| RAM | meminfo used vs `ActivityManager` total | 1500 ms |

Temp icon color: ≤50 `#818184`, 51–70 `#f6a6a6`, 71–85 `#f67474`, ≥86 `#f64646`.

**Quick tiles** (`views/mainpanel/systemset/quicksettings/`), in order:

| # | Tile | Behavior |
|---|---|---|
| 1 | Focus Mode | dialog → `screen_focus_lock`; hidden unless mode 0 |
| 2 | Performance Mode | dialog → `performance_mode` |
| 3 | Fan Control | dialog → `fan_mode`; custom seekbar → `fan_speed`. Options by performance mode: 0 hides Custom; 1 hides Off + Custom; 2 hides Off + Quiet |
| 4 | Clean Memory | see below; active 1500 ms, result 1000 ms |
| 5 | Handle Style | dialog → `temp_abxy_layout_mode` + the two layout flags |
| 6 | Direct Power Supply | toggles `is_charging_separation` |
| 7 | Vibration | toggles `vibrate_on` |
| 8 | Ambient LED | broadcast above |
| 9 | Force Landscape | toggles `force_landscape` |
| 10 | Theme Mode | dialog → `tcc_theme_mode`; recolors the overlay only |

Option dialogs close 300 ms after a pick. Grid is computed from panel size (tile 145 × 175 px at 1080 high); L1/R1 page.

**Clean memory** (`SRC/p040N0/AsyncTaskC0170a.java`): for each running process, skip self, system/updated-system apps and the top activity's package; `forceStopPackage`, except `killBackgroundProcesses` for `app_whiteList` entries, the live wallpaper and enabled IMEs. Toast "Cleaned %d processes, remaining memory %s". Tapping the status-bar clock runs the same task.

**Status bar** (`views/statusbar/`): clock (1 s tick, 12/24 h), Bluetooth (ACL connect/disconnect), download speed (`TrafficStats` every 2 s), Wi-Fi (4 levels), battery (level from `BATTERY_CHANGED`; charging from `sys/class/power_supply/usb/current_now > 1000`).

## 7. UI

Root `TccView` (`RES/layout/main_layout.xml`), vertical, background = theme primary background:

```
status_bar        marginTop 15dp        clock left | Bluetooth, speed, Wi-Fi, battery right
MainView          weight 1, 15dp sides  ScreenControlView | SystemSetView (alpha 0.1→1, translateY 150→0, 200 ms)
NavigationBar     marginBottom 15dp     Mode | Task | Settings between 15 % and 85 % guidelines
```

- Sizes derive from display height h = 1080: status unit `0.065h` = 70 px (icons 42 px); nav/tile button base `0.135h` = 145 px (icon circle 101 px, label box 58 px).
- Width is clamped to `1.148 × h` when the display is wider than that ratio (1239 px here).
- Only one `@dimen` is used (`status_view_margin` 2.5dp); everything else is literal dp or computed in code. No size-qualifier overrides apply.
- Panels: `SRC/p156t0/C3137a.java` ConstraintLayout, 20dp corners, panel color, focused = panel-select color.

Mode tab (`screen_control_layout`): top half = three mode buttons (15dp padding, 15dp corners, 500 ms click guard); bottom half split 50/50 = brightness card (Dual / Top / Bottom) and volume card (Top / Bottom).
The "60Hz" pill row inside each mode button is never populated.

Settings tab (`system_set_layout`): guidelines at 70 % horizontal and 40 % vertical. Frame-rate card (mode pill 0–30 %, FPS number 30–70 %, "CURRENT FPS" label), quick-settings pager with dot indicator (4dp dots, 13dp spacing), hardware row (TEMP 20 %, FAN 20 %, four rings 60 %).
Ring colors: CPU `#ee188c`, GPU `#70e7ff`, PWR `#e26e01`, RAM `#76ca40`; stroke `0.05 × min(w,h)`, arc alpha 150, 300 ms decelerate.

Theme colors (`SRC/p148r0/C3104a.java`; styles `TccColorsThemeLight` / `TccColorsThemePureBlack`):

| Token | Light | Pure black |
|---|---|---|
| primary background | `#ffffff` | `#000000` |
| panel background | `#f4f4f4` | `#161616` |
| panel select | `#d5d5d5` | `#3a3a3a` |
| primary | `#434343` | `#ffffff` |
| secondary | `#939393` | `#e0e0e0` |
| tertiary | `#d0d0d0` | `#b0b0b0` |
| fourth | `#ebebeb` | `#282828` |
| fifth | `#fdfdfd` | `#282828` |
| accent | `#8e84ff` | `#cb6a0d83` |
| dialog bg | `#e7e7e7` | `#1e1e1e` |
| lightning | `#04ad29` | `#03c82e` |
| panel text | `#666666` | `#ffffff` |
| view selected bg | `#454545` | `#1a1a1a` |

`TccColorsThemeDark` exists but is never selected. Icons are all vectors in `RES/drawable/`; pure-black variants use a `_black` suffix.

Animations: `tcc_anim` = `pop_win_enter_anim` / `pop_win_exit_anim` (translateY 90 %p ↔ 0 plus alpha, 200 ms). Press effect scale 0.9 over 50 ms. Tile active color 200 ms.

Dialogs:
- Tip dialogs (`SRC/p144q0/C3098b.java`): own window, type 2021, same flags as the overlay, `#BB000000` scrim, card 15dp corners; 200 ms morph from the source view (`dialog/DialogAnimator.java`).
- Tile option dialogs (`dialog/DialogInflater.java`): `AlertDialog` with window type 2021 and transparent background; widths 330dp (controller 350dp), tip 300dp.

Strings: English in `RES/values/strings.xml`; full translations in `values-ja`, `values-ko`, `values-zh`, `values-zh-rCN`; partial in `zh-rHK/MO/TW`.

## 8. BDSA claims checked against stock

| BDSA says | Stock |
|---|---|
| PServerBinder protocol | confirmed |
| `action.tcc.button.key.event` + extras | confirmed |
| Never `FLAG_LAYOUT_NO_LIMITS` on display 4 | stock sets it (as a system window) |
| Overlay type 2021 | confirmed; stock gets it natively |
| Blanking sets brightness to −1 | not in stock — cover window + touch disable only |
| `sub_screen_brightness` | not in stock |
| `fan_Service_mode` / `fan_custom_speed_percentage` | wrong; `fan_mode` / `fan_speed` |
| `controller_style_type` | wrong; `temp_abxy_layout_mode` |
| `ISettingsService`, `IMappingService` | wrong; `IExternalControlManager`, `IServerApi` (txn 2/3) |
| Fan `getSpeedPercentage` txn 3 | confirmed (float × 100) |
| IOverviewProxy txn 6 | confirmed |
| Stock overlay debounce 200 ms | 400 ms |
| FPS via SurfaceFlinger 1013 | confirmed (via `service call`) |
| 10 stock tiles in that order | confirmed |

## 9. What a user-app clone needs per feature

| Feature | Route |
|---|---|
| Receive AYN / volume-key broadcasts | plain receiver |
| Read `Settings.System` keys, observers | plain API |
| Write vendor `Settings.System` keys | `WRITE_SETTINGS` rejects non-AOSP keys for a targetSdk ≥ 23 app → shell `settings put system …` or a helper |
| `sys.temp.disabled.touch` | shell `setprop` |
| Overlay window type 2021 + `windowAnimations` | needs `INTERNAL_SYSTEM_WINDOW`; a user app uses `TYPE_APPLICATION_OVERLAY` and animates the view itself, or routes through a privileged helper |
| Cover window above status bar on display 0 | same limitation |
| `setBrightness` / `setTemporaryBrightness` per display | needs `CONTROL_DISPLAY_BRIGHTNESS` → helper, or shell `cmd display set-brightness` (default display only) |
| `setPinScreenMode` | vendor WindowManager call; permission requirement untested |
| Fan provider, SurfaceFlinger 1013, sysfs reads | may be callable directly; otherwise shell |
| Quickstep bind, mapping-service bind | untested from a user app (BDSA does both) |
| `forceStopPackage`, `lockNow` | shell `am force-stop`, `input keyevent 223` or helper |
| Stock coexistence | stock must be suppressed or both apps answer the button |

Rows marked "untested" are inferences from the code, not something tried on the device.

## 10. Probe results (measured, 2026-11-16)

Ran a throwaway user app (`jr.brian.thorprobe`, uid 10255, non-system, non-platform-signed; source in
`../thor-probe/`, full log in `device/probe-report.txt`). Headline: **`PServerBinder` gives a normal app a root
shell** — `id` through it reports `uid=0(root) … context=u:r:pservice:s0`, and SELinux `getenforce` = **Permissive**.
That one fact means a user-app clone can reach everything the stock app does.

| Capability | In-process (app uid) | Via `PServerBinder` root | Verdict for the clone |
|---|---|---|---|
| Hidden-API exemptions | PASS (`setHiddenApiExemptions(["L"])`) | — | hidden APIs reachable in-process |
| `Settings.System` read | PASS | — | direct |
| `Settings.System` write (vendor keys) | PASS after self-granting `WRITE_SETTINGS` appop | PASS (`settings put system`) | direct after self-grant |
| Self-grant appops / `pm grant` | — | PASS (`appops set`, `pm grant WRITE_SECURE_SETTINGS` all accepted) | run once at startup |
| Fan provider binder | PASS (`IFanControlProvider` txn3 = 0.24) | — | direct |
| Mapping service (HOME) | PASS (binds `com.odin.mapping/com.ro.mapping.service.ApiService` once intent is explicit) | — | direct |
| sysfs telemetry (temp/cpu/gpu/batt) | PASS (all four readable) | — | direct |
| Overlay `TYPE_APPLICATION_OVERLAY` (2038) on display 4 | PASS after self-granting `SYSTEM_ALERT_WINDOW` | — | direct; use 2038, not 2021 |
| Overlay `TYPE_BOOT_PROGRESS` (2021) | FAIL (`permission denied for window type 2021`) | — | needs root `app_process` helper (top-screen cover only) |
| SurfaceFlinger 1013 (FPS) | FAIL (`Operation not permitted`) | PASS (`Result: Parcel(…)`) | via root shell |
| `setprop sys.temp.disabled.touch` | — | PASS (round-trip ok) | via root shell |
| `DisplayManager.setBrightness(id, f)` per display | FAIL (`InvocationTargetException`, needs `CONTROL_DISPLAY_BRIGHTNESS`) | not retried | via a root `app_process` helper (BDSA pattern) or `cmd display` |
| Quickstep bind (recents on bottom screen) | FAIL (`SecurityException: Not allowed to bind`) | not retried | **open** — needs a root route or an `am`/overlay alternative |

Two things a user app can't do directly and that still need the root `app_process` helper (as BDSA uses): a window
above the status bar (type 2021, for the top-screen cover) and per-display `setBrightness`. The recents-on-bottom
bind is the one feature with no proven route yet. Everything else is reachable directly or through the root shell.
