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
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telemetry = TelemetryMonitor(context, privileged, refreshRateCap = { if (uiState.value.is120Hz) 120 else 60 })

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private val observed = listOf(
        Keys.DISPLAY_MODE, Keys.FOCUS_LOCK, Keys.PERFORMANCE_MODE, Keys.FAN_MODE, Keys.CONTROLLER_STYLE,
        Keys.CHARGING_SEPARATION, Keys.VIBRATE_ON, Keys.FORCE_LANDSCAPE, Keys.JOYSTICK_LIGHT, Keys.THEME_MODE,
        Keys.SECONDARY_VOLUME, Keys.DUAL_BRIGHTNESS, Keys.PEAK_REFRESH, Keys.SCREEN_BRIGHTNESS,
    )
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshFromSettings()
    }

    private var fastJob: Job? = null
    private var lastRxBytes = TrafficStats.getTotalRxBytes()
    private var lastRxTime = System.currentTimeMillis()

    fun start(scope: CoroutineScope) {
        observed.forEach {
            context.contentResolver.registerContentObserver(settings.uriFor(it), false, observer)
        }
        refreshFromSettings()
        telemetry.start(scope)
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
        telemetry.stop()
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
                displayMode = mode,
                focusLockTitle = focusTitle(focus),
                is120Hz = peak >= 90f,
                themeMode = themeMode,
                bottomVolume = settings.getInt(Keys.SECONDARY_VOLUME, 7) / 15f,
                dualBrightness = settings.getInt(Keys.DUAL_BRIGHTNESS, 50) / 100f,
                topBrightness = settings.getInt(Keys.SCREEN_BRIGHTNESS, 128) / 255f,
                bottomBrightness = (privileged.getDisplayBrightness(4) ?: it.bottomBrightness).coerceIn(0f, 1f),
                topVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                    audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
                tiles = buildTiles(mode, focus),
            )
        }
    }

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

    private fun buildTiles(mode: Int, focus: Int): List<QuickTile> {
        val perf = settings.getInt(Keys.PERFORMANCE_MODE, 0)
        val fan = settings.getInt(Keys.FAN_MODE, 1)
        val style = settings.getInt(Keys.CONTROLLER_STYLE, 1)
        val theme = settings.getInt(Keys.THEME_MODE, 4)
        val light = settings.getString(Keys.JOYSTICK_LIGHT).orEmpty().split(",").any { it.trim() == "1" }
        return buildList {
            if (mode == 0) add(QuickTile("focus_lock", "ic_screen_focus_lock_mode", focusTitle(focus)))
            add(QuickTile("performance", "ic_qs_performance", perfTitle(perf)))
            add(QuickTile("fan", "ic_qs_fan_control", fanTitle(fan), active = fan != 0))
            add(QuickTile("clear_memory", "ic_qs_clean", "Clean\nMemory"))
            add(QuickTile("controller", "ic_qs_controller_style", styleTitle(style)))
            add(QuickTile("charging_sep", "ic_qs_charging_separation", "Direct Power\nSupply", active = settings.getInt(Keys.CHARGING_SEPARATION, 0) == 1))
            add(QuickTile("vibration", "ic_qs_vibrate", "Vibration", active = settings.getInt(Keys.VIBRATE_ON, 1) == 1))
            add(QuickTile("ambient_led", "ic_qs_ambient_light", "Ambient LED", active = light))
            add(QuickTile("force_landscape", "ic_landscape", "Force\nLandscape", active = settings.getInt(Keys.FORCE_LANDSCAPE, 0) == 1))
            add(QuickTile("theme", "ic_qs_theme_mode", themeTitle(theme), active = true))
        }
    }

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
            "clear_memory" -> cleanMemory()
            // focus_lock, performance, fan, controller, theme, ambient_led → modal dialogs (next sub-step)
        }
    }

    private fun toggleIntKey(key: String, default: Int) {
        val cur = settings.getInt(key, default)
        settings.putInt(key, if (cur == 0) 1 else 0)
    }

    private fun cleanMemory() {
        // Full stock exclusion logic is a later refinement; the root kill is the core action.
        privileged.shell("am kill-all")
    }
}
