package jr.brian.commandpannel.system

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.SystemSettings.Keys
import jr.brian.commandpannel.ui.overlay.OverlayActions
import jr.brian.commandpannel.ui.overlay.OverlayUiState
import jr.brian.commandpannel.ui.overlay.QuickTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central reactive store behind the overlay. Reads the vendor `Settings.System` keys (reacting to changes through
 * `ContentObserver`s), folds in live telemetry, battery, clock, audio and network, and implements [OverlayActions].
 * Mirrors the stock `SystemSettingsRepository`, scaled to the features wired in phase 4.
 */
class SettingsRepository(
    private val context: Context,
    private val privileged: PrivilegedAccess,
) : OverlayActions {

    private val settings = SystemSettings(context.contentResolver, privileged)
    private val prefs = context.getSharedPreferences("command_panel", Context.MODE_PRIVATE)
    private val screenshotter = Screenshotter(context, privileged)

    /** Set by the service so screenshot/launch actions can dismiss the overlay first. */
    var onHideOverlay: () -> Unit = {}
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telemetry = TelemetryMonitor(context, privileged, refreshRateCap = { if (uiState.value.is120Hz) 120 else 60 })
    private val batteryEstimate = BatteryEstimateMonitor(context, settings, enabled = { prefs.getBoolean("battery_estimate", false) })

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val observed = listOf(
        Keys.DISPLAY_MODE, Keys.FOCUS_LOCK, Keys.PERFORMANCE_MODE, Keys.FAN_MODE, Keys.CONTROLLER_STYLE,
        Keys.CHARGING_SEPARATION, Keys.VIBRATE_ON, Keys.FORCE_LANDSCAPE, Keys.JOYSTICK_LIGHT, Keys.THEME_MODE,
        Keys.SECONDARY_VOLUME, Keys.DUAL_BRIGHTNESS, Keys.PEAK_REFRESH, Keys.SCREEN_BRIGHTNESS,
        jr.brian.commandpannel.privileged.ThorRgbController.PICKER_COLOR_KEY,
    )
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshFromSettings()
    }

    // App-private prefs the companion app changes (tile roster, quick launch, tip, theme sync, …).
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshFromSettings()
    }

    private var fastJob: Job? = null
    private var scope: CoroutineScope? = null
    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastRxTime = System.currentTimeMillis()

    fun start(scope: CoroutineScope) {
        this.scope = scope
        observed.forEach {
            context.contentResolver.registerContentObserver(settings.uriFor(it), false, observer)
        }
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        refreshFromSettings()
        telemetry.start(scope)
        batteryEstimate.start(scope)
        scope.launch { batteryEstimate.text.collect { est -> _uiState.update { it.copy(batteryEstimate = est) } } }
        scope.launch {
            telemetry.state.collect { t ->
                _uiState.update {
                    it.copy(
                        tempC = t.tempC, fanPercent = t.fanPercent,
                        cpuGhz = t.cpuGhz, cpuFraction = t.cpuFraction,
                        gpuMhz = t.gpuMhz, gpuFraction = t.gpuFraction,
                        powerW = t.powerW, powerFraction = t.powerFraction,
                        ramGb = t.ramGb, ramFraction = t.ramFraction,
                        fps = t.fps,
                    )
                }
            }
        }
        fastJob = scope.launch {
            while (isActive) {
                refreshFast()
                delay(1000L)
            }
        }
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(observer)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        telemetry.stop()
        batteryEstimate.stop()
        fastJob?.cancel(); fastJob = null
    }

    // ---- reads ----

    private fun refreshFromSettings() {
        val mode = settings.getInt(Keys.DISPLAY_MODE, 0)
        val focus = settings.getInt(Keys.FOCUS_LOCK, 0)
        val peak = settings.getFloat(Keys.PEAK_REFRESH, 60f)
        val themeMode = settings.getInt(Keys.THEME_MODE, 4)
        _uiState.update {
            it.copy(
                accentOverride = readAccentOverride(),
                displayMode = mode,
                focusLockTitle = focusTitle(focus),
                focusLock = focus,
                performanceMode = settings.getInt(Keys.PERFORMANCE_MODE, 0),
                fanMode = settings.getInt(Keys.FAN_MODE, 1),
                fanSpeed = settings.getInt(Keys.FAN_SPEED, 25000),
                controllerStyle = settings.getInt(Keys.CONTROLLER_STYLE, 1),
                triggerMode = settings.getInt("trigger_input_mode", 2),
                is120Hz = peak >= 90f,
                themeMode = themeMode,
                suppressBottomTip = prefs.getBoolean("suppress_bottom_tip", false),
                quickLaunchLabel = QuickLaunch.savedLabel(context),
                bottomVolume = settings.getInt(Keys.SECONDARY_VOLUME, 7) / 15f,
                dualBrightness = settings.getInt(Keys.DUAL_BRIGHTNESS, 50) / 100f,
                topBrightness = settings.getInt(Keys.SCREEN_BRIGHTNESS, 128) / 255f,
                bottomBrightness = (privileged.getDisplayBrightness(4) ?: it.bottomBrightness).coerceIn(0f, 1f),
                topVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
                tiles = buildTiles(mode, focus),
                layout = OverlayLayout.decode(prefs.getString("overlay_layout", null)),
                look = jr.brian.commandpannel.ui.theme.TccUiLook.fromKey(prefs.getString("overlay_look", null)),
                navTab = readNavTab(),
            )
        }
    }

    private fun readNavTab(): jr.brian.commandpannel.ui.overlay.OverlayTab =
        if (prefs.getString("overlay_nav_tab", null) == jr.brian.commandpannel.ui.overlay.OverlayTab.MODE.name)
            jr.brian.commandpannel.ui.overlay.OverlayTab.MODE
        else jr.brian.commandpannel.ui.overlay.OverlayTab.SETTINGS

    private fun refreshFast() {
        val clock = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.getDefault())
            .format(Date())
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val percent = if (level >= 0) level * 100 / scale else _uiState.value.batteryPercent
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes()
        val dt = (now - lastRxTime).coerceAtLeast(1)
        val bps = (rx - lastRxBytes) * 1000.0 / dt
        lastRxBytes = rx; lastRxTime = now

        _uiState.update {
            it.copy(
                clock = clock,
                batteryPercent = percent,
                charging = charging,
                netSpeed = formatSpeed(bps),
                hideNetSpeed = prefs.getBoolean("hide_net_speed", false),
                wifiLevel = wifiLevel(),
                bluetoothOn = bluetoothConnected(),
            )
        }
    }

    private fun focusTitle(v: Int) = when (v) {
        1 -> "Top screen"
        2 -> "Bottom screen"
        else -> "Auto-lock"
    }

    private fun enabledTileKeys(): List<String> =
        QuickSettingTileId.enabledKeys(prefs.getString("qs_enabled_tiles", null), prefs.getString("qs_tile_order", null))

    private fun buildTiles(mode: Int, focus: Int): List<QuickTile> {
        val perf = settings.getInt(Keys.PERFORMANCE_MODE, 0)
        val fan = settings.getInt(Keys.FAN_MODE, 1)
        val style = settings.getInt(Keys.CONTROLLER_STYLE, 1)
        val theme = settings.getInt(Keys.THEME_MODE, 4)
        val light = settings.getString(Keys.JOYSTICK_LIGHT).orEmpty().split(",").any { it.trim() == "1" }
        return enabledTileKeys().mapNotNull { key ->
            val id = QuickSettingTileId.fromKey(key) ?: return@mapNotNull null
            // Focus Lock is hidden outside dual mode, like stock.
            if (id == QuickSettingTileId.FOCUS_LOCK && mode != 0) return@mapNotNull null
            val (title, active) = when (id) {
                QuickSettingTileId.FOCUS_LOCK -> focusTitle(focus) to false
                QuickSettingTileId.PERFORMANCE -> perfTitle(perf) to false
                QuickSettingTileId.FAN -> fanTitle(fan) to (fan != 0)
                QuickSettingTileId.CLEAR_MEMORY -> "Clean\nMemory" to false
                QuickSettingTileId.CONTROLLER -> styleTitle(style) to false
                QuickSettingTileId.CHARGING_SEP -> "Direct Power\nSupply" to (settings.getInt(Keys.CHARGING_SEPARATION, 0) == 1)
                QuickSettingTileId.VIBRATION -> "Vibration" to (settings.getInt(Keys.VIBRATE_ON, 1) == 1)
                QuickSettingTileId.AMBIENT_LED -> "Ambient LED" to light
                QuickSettingTileId.FORCE_LANDSCAPE -> "Force\nLandscape" to (settings.getInt(Keys.FORCE_LANDSCAPE, 0) == 1)
                QuickSettingTileId.THEME -> themeTitle(theme) to true
                QuickSettingTileId.WIFI -> "Wi-Fi" to (wifiLevel() >= 0)
                QuickSettingTileId.BLUETOOTH -> "Bluetooth" to bluetoothConnected()
                QuickSettingTileId.DO_NOT_DISTURB -> "Do Not\nDisturb" to (settings.getInt("zen_mode", 0) != 0)
                QuickSettingTileId.AIRPLANE -> "Airplane\nMode" to (android.provider.Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0) == 1)
                QuickSettingTileId.KEEP_SCREEN_ON -> "Keep\nScreen On" to (settings.getInt("keep_screen_on", 0) == 1)
                QuickSettingTileId.DOUBLE_TAP_HOME -> "Double-Tap\nHOME" to (settings.getInt("prevent_press_home_accidentally", 0) == 1)
                QuickSettingTileId.CHARGE_LIMIT_80 -> "80% Charge\nLimit" to (settings.getInt("percent_80_charge_limit", 0) == 1)
                QuickSettingTileId.VIRTUAL_MOUSE -> "Virtual\nMouse" to (settings.getInt("global_gamepad_to_mouse_mode", 0) == 1)
                QuickSettingTileId.TRIGGER_MODE -> triggerTitle(settings.getInt("trigger_input_mode", 2)) to false
                QuickSettingTileId.SCREENSHOT -> "Screenshot" to false
                QuickSettingTileId.CLEAR_ALL_TASKS -> "Clear All\nTasks" to false
                QuickSettingTileId.REPLAY_BUFFER -> "Replay\nBuffer" to prefs.getBoolean("replay_enabled", false)
            }
            QuickTile(key, id.icon, title, active)
        }
    }

    private fun triggerTitle(v: Int) = when (v) { 0 -> "Trigger:\nDigital"; 1 -> "Trigger:\nAnalog"; else -> "Trigger:\nBoth" }

    private fun perfTitle(v: Int) = when (v) { 1 -> "Medium\nPerformance"; 2 -> "High\nPerformance"; else -> "Standard\nPerformance" }
    private fun fanTitle(v: Int) = when (v) { 0 -> "Turned Off"; 4 -> "Smart Mode"; 5 -> "Sports Mode"; 6 -> "Custom"; else -> "Quiet Mode" }
    private fun styleTitle(v: Int) = when (v) { 0 -> "XBox Style"; 2 -> "Ban On Use"; else -> "Standard\nStyle" }
    private fun themeTitle(v: Int) = when (v) { 1 -> "System\nTheme"; 3 -> "Light Theme"; else -> "Dark Theme" }

    private fun formatSpeed(bps: Double): String {
        val kb = bps / 1024.0
        return if (kb >= 1024) "%.2fMB/S".format(kb / 1024.0) else "%.2fKB/S".format(kb.coerceAtLeast(0.0))
    }

    private fun wifiLevel(): Int {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return -1
        if (!wm.isWifiEnabled) return -1
        @Suppress("DEPRECATION") val rssi = wm.connectionInfo?.rssi ?: return -1
        return WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
    }

    private fun bluetoothConnected(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return runCatching {
            adapter.isEnabled && adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        }.getOrDefault(false)
    }

    // ---- OverlayActions (writes) ----

    override fun selectMode(mode: Int) {
        settings.putInt(Keys.DISPLAY_MODE, mode)
    }

    override fun setTopBrightness(fraction: Float, committed: Boolean) {
        privileged.setDisplayBrightness(0, fraction.coerceIn(0f, 1f), temporary = !committed)
        if (committed) settings.putInt(Keys.SCREEN_BRIGHTNESS, (fraction * 255).toInt())
        _uiState.update { it.copy(topBrightness = fraction) }
    }

    override fun setBottomBrightness(fraction: Float, committed: Boolean) {
        privileged.setDisplayBrightness(4, fraction.coerceIn(0f, 1f), temporary = !committed)
        _uiState.update { it.copy(bottomBrightness = fraction) }
    }

    override fun setDualBrightness(fraction: Float, committed: Boolean) {
        setTopBrightness(fraction, committed)
        setBottomBrightness(fraction, committed)
        if (committed) settings.putInt(Keys.DUAL_BRIGHTNESS, (fraction * 100).toInt())
        _uiState.update { it.copy(dualBrightness = fraction) }
    }

    override fun setTopVolume(fraction: Float, committed: Boolean) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).toInt(), 0)
        _uiState.update { it.copy(topVolume = fraction) }
    }

    override fun setBottomVolume(fraction: Float, committed: Boolean) {
        settings.putInt(Keys.SECONDARY_VOLUME, (fraction * 15).toInt())
        _uiState.update { it.copy(bottomVolume = fraction) }
    }

    override fun toggleRefreshRate() {
        val to = if (uiState.value.is120Hz) 60f else 120f
        settings.putFloat(Keys.MIN_REFRESH, to)
        settings.putFloat(Keys.PEAK_REFRESH, to)
    }

    override fun onTileClick(id: String) {
        when (id) {
            "vibration" -> toggleIntKey(Keys.VIBRATE_ON, default = 1)
            "charging_sep" -> toggleIntKey(Keys.CHARGING_SEPARATION, default = 0)
            "force_landscape" -> toggleIntKey(Keys.FORCE_LANDSCAPE, default = 0)
            "ambient_led" -> toggleAmbientLight()
            "clear_memory" -> cleanMemory()
            "wifi" -> privileged.shell("svc wifi ${if (wifiLevel() >= 0) "disable" else "enable"}")
            "bluetooth" -> privileged.shell("svc bluetooth ${if (bluetoothConnected()) "disable" else "enable"}")
            "do_not_disturb" -> privileged.shell("cmd notification set_dnd ${if (settings.getInt("zen_mode", 0) != 0) "off" else "priority"}")
            "airplane_mode" -> toggleAirplane()
            "keep_screen_on" -> toggleIntKey("keep_screen_on", 0)
            "prevent_accidental_home" -> toggleIntKey("prevent_press_home_accidentally", 0)
            "charge_limit_80" -> toggleIntKey("percent_80_charge_limit", 0)
            "gamepad_mouse_mode" -> toggleIntKey("global_gamepad_to_mouse_mode", 0)
            "clear_all_tasks" -> clearAllTasks()
            "replay_buffer" -> {
                val enable = !prefs.getBoolean("replay_enabled", false)
                prefs.edit().putBoolean("replay_enabled", enable).apply()
                // Let the service's replay controller arm/disarm to match the pref.
                context.sendBroadcast(
                    Intent(jr.brian.commandpannel.service.AssistantService.ACTION_REPLAY_SET_ENABLED)
                        .setPackage(context.packageName),
                )
            }
            // focus_lock, performance, fan, controller, theme, trigger_mode, screenshot → modal dialogs (in the overlay)
        }
        refreshFromSettings()
    }

    override fun setTriggerMode(value: Int) = settings.putInt("trigger_input_mode", value)

    override fun takeScreenshot(target: Int) {
        val t = Screenshotter.Target.entries.getOrElse(target) { Screenshotter.Target.BOTH }
        onHideOverlay()
        scope?.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(350) // let the overlay exit animation finish before capturing
            screenshotter.capture(t)
        }
    }

    private fun toggleAirplane() {
        val on = android.provider.Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0) == 1
        privileged.shell("cmd connectivity airplane-mode ${if (on) "disable" else "enable"}")
    }

    override fun setFocusLock(value: Int) = settings.putInt(Keys.FOCUS_LOCK, value)

    override fun setPerformance(value: Int) {
        settings.putInt(Keys.PERFORMANCE_MODE, value)
        // Stock interlock: High performance (2) forces the fan out of Off (0) into Smart (4).
        if (value == 2 && settings.getInt(Keys.FAN_MODE, 1) == 0) settings.putInt(Keys.FAN_MODE, 4)
    }

    override fun setFanMode(value: Int) = settings.putInt(Keys.FAN_MODE, value)
    override fun setFanSpeed(value: Int) = settings.putInt(Keys.FAN_SPEED, value)

    override fun setControllerStyle(value: Int) {
        settings.putInt(Keys.CONTROLLER_STYLE, value)
        when (value) {
            0 -> { settings.putInt(Keys.FLIP_BUTTON_LAYOUT, 1); settings.putInt(Keys.NO_CREATE_GAMEPAD, 0) } // Xbox
            2 -> { settings.putInt(Keys.FLIP_BUTTON_LAYOUT, 0); settings.putInt(Keys.NO_CREATE_GAMEPAD, 1) } // disable
            else -> { settings.putInt(Keys.FLIP_BUTTON_LAYOUT, 0); settings.putInt(Keys.NO_CREATE_GAMEPAD, 0) } // Odin
        }
    }

    override fun setThemeMode(value: Int) = settings.putInt(Keys.THEME_MODE, value)

    override fun toggleAmbientLight() {
        val show = privileged.shell("getprop sys.light.curr.is.show")?.trim()
        val isOn = show == "1" || show == "true"
        privileged.shell("am broadcast -a com.ro.external.request.light.adj.action --ez isShow ${!isOn}")
    }

    override fun cleanMemory() {
        // Full stock exclusion logic is a later refinement; the root kill is the core action.
        privileged.shell("am kill-all")
    }

    /**
     * Clear All Tasks (fork feature): build the recents/activities snapshots from root `dumpsys`, apply the pure
     * [jr.brian.commandpannel.privileged.ClearTasksPolicy], then remove the planned tasks via the helper. With
     * keep-on-screen (default) the on-screen apps, their companion tasks and the home app are kept.
     */
    private fun clearAllTasks() {
        onHideOverlay()
        scope?.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            val policy = jr.brian.commandpannel.privileged.ClearTasksPolicy
            val recents = policy.parseRecentsDump(privileged.shell("dumpsys activity recents").orEmpty()) ?: return@launch
            val onScreen = policy.parseActivitiesDump(privileged.shell("dumpsys activity activities").orEmpty()).orEmpty()
            val home = policy.filterHomePackages(
                Regex("""([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/""")
                    .findAll(privileged.shell("cmd package resolve-activity -c android.intent.category.HOME").orEmpty())
                    .map { it.groupValues[1] }.toList()
            )
            val plan = policy.selectTasksToClear(
                recents = recents,
                onScreenRootTasks = onScreen,
                keepOnScreen = prefs.getBoolean("clear_tasks_keep_on_screen", true),
                excludedPackages = setOf(
                    context.packageName, "com.odin.dualscreen.assistant",
                    "com.android.systemui", "com.android.launcher3",
                ),
                homePackages = home,
            )
            plan.remove.forEach { privileged.removeTask(it) }
        }
    }

    override fun setSuppressBottomTip(suppress: Boolean) {
        prefs.edit().putBoolean("suppress_bottom_tip", suppress).apply()
        _uiState.update { it.copy(suppressBottomTip = suppress) }
    }

    override fun quickLaunch() {
        QuickLaunch.launch(context)
    }

    override fun openRecents() {
        // Stock opens bottom-screen recents via IOverviewProxy txn 6, which a user app can't bind (SecurityException);
        // KEYCODE_APP_SWITCH through the root shell opens the system recents instead. Dismiss the panel first, as stock.
        onHideOverlay()
        privileged.shell("input keyevent 187")
    }

    override fun setOverlayLayout(layout: OverlayLayout) {
        prefs.edit().putString("overlay_layout", layout.encode()).apply()
        // Reflect immediately so the overlay re-renders and the FLIP animation can run.
        _uiState.update { it.copy(layout = layout) }
    }

    override fun setNavTab(tab: jr.brian.commandpannel.ui.overlay.OverlayTab) {
        prefs.edit().putString("overlay_nav_tab", tab.name).apply()
        _uiState.update { it.copy(navTab = tab) }
    }

    override fun launchCompanion() {
        runCatching {
            context.startActivity(
                Intent(context, jr.brian.commandpannel.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private fun toggleIntKey(key: String, default: Int) {
        val cur = settings.getInt(key, default)
        settings.putInt(key, if (cur == 0) 1 else 0)
    }

    /**
     * When the "Sync overlay accent to stick LED" fork toggle is on, the Thor's ambient-LED colour becomes the
     * overlay accent (inverted from the original theme→LED sync). Returns the opaque ARGB accent, or null when off.
     */
    private fun readAccentOverride(): Int? = jr.brian.commandpannel.privileged.ThorRgbController
        .accentOverride(context, prefs.getBoolean("rgb_accent_sync", false))
}
