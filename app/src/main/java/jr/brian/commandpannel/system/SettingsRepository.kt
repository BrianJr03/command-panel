package jr.brian.commandpannel.system

import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
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
import jr.brian.commandpannel.ui.overlay.OverlayOnboarding
import jr.brian.commandpannel.ui.overlay.OverlayOnboardingStep
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
import kotlinx.coroutines.withContext
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
    private val prefs = Prefs.of(context)
    private val screenshotter = Screenshotter(context, privileged)

    /** Set by the service so screenshot/launch actions can dismiss the overlay first. */
    var onHideOverlay: () -> Unit = {}
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val telemetry = TelemetryMonitor(
        context, privileged,
        refreshRateCap = { if (uiState.value.is120Hz) 120 else 60 },
        taskFpsEnabled = { prefs.getBoolean(TelemetryMonitor.PREF_TASK_FPS, false) },
    )
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
    private val main = Handler(Looper.getMainLooper())

    // Bottom volume (#6): a drag only writes the setting when its 0..15 step changes, at most every
    // BOTTOM_WRITE_INTERVAL_MS, off the main thread and in order; the slider ignores its own echoes meanwhile.
    private val bottomThrottle = SteppedWriteThrottle(BOTTOM_WRITE_INTERVAL_MS)
    private val bottomGuard = VolumeEchoGuard(ECHO_HOLD_MS)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val bottomWrites = Dispatchers.IO.limitedParallelism(1)
    private var bottomWritesInFlight = 0
    private val bottomFlush = Runnable { bottomThrottle.flush(now())?.let(::writeBottomLevel) }
    private val bottomCatchUp = Runnable { refreshBottomVolume() }
    private val volumeKeys = VolumeKeyChain()

    // Top volume (#5): music volume changes (keys, other apps) reach the open panel through VOLUME_CHANGED_ACTION.
    private val topGuard = VolumeEchoGuard(ECHO_HOLD_MS)
    private val topCatchUp = Runnable { refreshTopVolume() }
    private var volumeReceiverRegistered = false
    private val volumeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_VOLUME_CHANGED) return
            if (intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1) != AudioManager.STREAM_MUSIC) return
            refreshTopVolume()
        }
    }
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
        scope.launch { uiState.collect { OverlayOnboarding.activeStep.value = it.onboardingStep } }
        scope.launch {
            telemetry.state.collect { t ->
                _uiState.update {
                    it.copy(
                        tempC = t.tempC, fanPercent = t.fanPercent,
                        cpuGhz = t.cpuGhz, cpuFraction = t.cpuFraction,
                        gpuMhz = t.gpuMhz, gpuFraction = t.gpuFraction,
                        powerW = t.powerW, powerFraction = t.powerFraction, powerCharging = t.powerCharging,
                        ramGb = t.ramGb, ramFraction = t.ramFraction,
                        fps = t.fps, fpsCompositor = t.fpsCompositor,
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
        main.removeCallbacks(bottomFlush)
        main.removeCallbacks(bottomCatchUp)
        main.removeCallbacks(topCatchUp)
        unregisterVolumeReceiver()
    }

    private fun now() = android.os.SystemClock.uptimeMillis()

    /** Called by the service on the main thread when the overlay is shown or hidden. */
    fun onOverlayVisibilityChanged(visible: Boolean) {
        if (visible) {
            registerVolumeReceiver()
            refreshTopVolume()
            refreshBottomVolume()
        } else {
            unregisterVolumeReceiver()
            endBottomDragIfAny()
            // A drag cut short by the hide never sends its release.
            if (topGuard.dragging) topGuard.onRelease(now())
        }
    }

    private fun registerVolumeReceiver() {
        if (volumeReceiverRegistered) return
        // A protected system broadcast, so the receiver needn't be exported.
        context.registerReceiver(volumeReceiver, IntentFilter(ACTION_VOLUME_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        volumeReceiverRegistered = true
    }

    private fun unregisterVolumeReceiver() {
        if (!volumeReceiverRegistered) return
        runCatching { context.unregisterReceiver(volumeReceiver) }
        volumeReceiverRegistered = false
    }

    /** The music volume as a 0..1 fraction, unless the top slider is ignoring its own echoes (then [current]). */
    private fun topVolumeFor(current: Float): Float =
        if (topGuard.ignoring(now())) current
        else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    private fun refreshTopVolume() {
        val shown = uiState.value.topVolume
        val next = topVolumeFor(shown)
        if (next != shown) _uiState.update { it.copy(topVolume = next) }
    }

    /**
     * The bottom volume to show: the setting, unless the slider is ignoring its own echoes (then [current]). Reading
     * the setting also re-bases the throttle, so a level changed elsewhere is written again by the next drag.
     */
    private fun bottomVolumeFor(current: Float): Float {
        if (bottomGuard.ignoring(now())) return current
        val level = settings.getInt(Keys.SECONDARY_VOLUME, 7).coerceIn(0, BOTTOM_VOLUME_MAX)
        if (bottomWritesInFlight == 0) bottomThrottle.sync(level)
        return level / BOTTOM_VOLUME_MAX.toFloat()
    }

    private fun refreshBottomVolume() {
        val shown = uiState.value.bottomVolume
        val next = bottomVolumeFor(shown)
        if (next != shown) _uiState.update { it.copy(bottomVolume = next) }
    }

    // ---- reads ----

    /**
     * Re-reads everything, for changes nothing observes: the bottom panel's brightness has no setting behind it, so
     * the screen-mode controller calls this after restoring a panel it had blanked.
     */
    fun refresh() = refreshFromSettings()

    private fun refreshFromSettings() {
        // Our own screen-off state, not the vendor's mode (which stays 0, #35).
        val mode = ScreenModeController.blankState(prefs)
        val focus = settings.getInt(Keys.FOCUS_LOCK, 0)
        val peak = settings.getFloat(Keys.PEAK_REFRESH, 60f)
        val themeMode = settings.getInt(Keys.THEME_MODE, 4)
        val activeLook = CustomLookStore.resolve(prefs)
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
                quickLaunchApp = if (prefs.getBoolean("quick_launch_show_app", true)) QuickLaunch.savedComponent(context) else null,
                navOrder = NavBarButton.ordered(prefs.getString("nav_bar_order", null)),
                bottomVolume = bottomVolumeFor(it.bottomVolume),
                dualBrightness = settings.getInt(Keys.DUAL_BRIGHTNESS, 50) / 100f,
                topBrightness = settings.getInt(Keys.SCREEN_BRIGHTNESS, 128) / 255f,
                bottomBrightness = (privileged.getDisplayBrightness(4) ?: it.bottomBrightness).coerceIn(0f, 1f),
                topVolume = topVolumeFor(it.topVolume),
                tiles = buildTiles(mode, focus),
                layout = OverlayLayout.decode(prefs.getString("overlay_layout", null)),
                look = activeLook.first,
                customLook = activeLook.second,
                navTab = readNavTab(),
                // Starts on its own until finished or skipped; keeps its step across refreshes.
                onboardingStep = if (prefs.getBoolean(OverlayOnboarding.KEY_SEEN, false)) null
                    else it.onboardingStep ?: OverlayOnboardingStep.WELCOME,
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
                hideTileLabels = prefs.getBoolean("qs_hide_labels", false),
                wifiLevel = wifiLevel(),
                bluetoothOn = bluetoothConnected(),
            )
        }
    }

    private fun focusTitle(v: Int) = context.getString(
        when (v) {
            1 -> R.string.key_focus_mode_lock_on_upper_screen
            2 -> R.string.key_focus_mode_lock_on_lower_screen
            else -> R.string.key_focus_mode_touch_switchable
        },
    )

    private fun enabledTileKeys(): List<String> =
        QuickSettingTileId.enabledKeys(prefs.getString("qs_enabled_tiles", null), prefs.getString("qs_tile_order", null))

    private fun buildTiles(mode: Int, focus: Int): List<QuickTile> {
        val perf = settings.getInt(Keys.PERFORMANCE_MODE, 0)
        val fan = settings.getInt(Keys.FAN_MODE, 1)
        val style = settings.getInt(Keys.CONTROLLER_STYLE, 1)
        val theme = settings.getInt(Keys.THEME_MODE, 4)
        val light = SystemSettings.StickLight.isOn(settings.getString(Keys.JOYSTICK_LIGHT))
        return enabledTileKeys().mapNotNull { key ->
            val id = QuickSettingTileId.fromKey(key) ?: return@mapNotNull null
            // Focus Lock is hidden outside dual mode, like stock.
            if (id == QuickSettingTileId.FOCUS_LOCK && mode != 0) return@mapNotNull null
            val (title, active) = when (id) {
                QuickSettingTileId.FOCUS_LOCK -> focusTitle(focus) to false
                QuickSettingTileId.PERFORMANCE -> perfTitle(perf) to false
                QuickSettingTileId.FAN -> fanTitle(fan) to (fan != 0)
                QuickSettingTileId.CLEAR_MEMORY -> str(R.string.tile_clean_memory) to false
                QuickSettingTileId.CONTROLLER -> styleTitle(style) to false
                QuickSettingTileId.CHARGING_SEP -> str(R.string.tile_direct_power_supply) to (settings.getInt(Keys.CHARGING_SEPARATION, 0) == 1)
                QuickSettingTileId.VIBRATION -> str(R.string.text_vibration_open_title) to (settings.getInt(Keys.VIBRATE_ON, 1) == 1)
                QuickSettingTileId.AMBIENT_LED -> str(R.string.text_led_lights_open_title) to light
                QuickSettingTileId.FORCE_LANDSCAPE -> str(R.string.tile_force_landscape) to (settings.getInt(Keys.FORCE_LANDSCAPE, 0) == 1)
                QuickSettingTileId.THEME -> themeTitle(theme) to true
                QuickSettingTileId.WIFI -> str(R.string.tile_name_wifi) to (wifiLevel() >= 0)
                QuickSettingTileId.BLUETOOTH -> str(R.string.tile_name_bluetooth) to bluetoothConnected()
                QuickSettingTileId.DO_NOT_DISTURB -> str(R.string.tile_do_not_disturb) to (settings.getInt("zen_mode", 0) != 0)
                QuickSettingTileId.AIRPLANE -> str(R.string.tile_airplane_mode) to (android.provider.Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0) == 1)
                QuickSettingTileId.KEEP_SCREEN_ON -> str(R.string.tile_keep_screen_on) to (settings.getInt("keep_screen_on", 0) == 1)
                QuickSettingTileId.DOUBLE_TAP_HOME -> str(R.string.tile_double_tap_home) to (settings.getInt("prevent_press_home_accidentally", 0) == 1)
                QuickSettingTileId.CHARGE_LIMIT_80 -> str(R.string.tile_charge_limit_80) to (settings.getInt("percent_80_charge_limit", 0) == 1)
                QuickSettingTileId.VIRTUAL_MOUSE -> str(R.string.tile_virtual_mouse) to (settings.getInt("global_gamepad_to_mouse_mode", 0) == 1)
                QuickSettingTileId.TRIGGER_MODE -> triggerTitle(settings.getInt("trigger_input_mode", 2)) to false
                QuickSettingTileId.SCREENSHOT -> str(R.string.tile_name_screenshot) to false
                QuickSettingTileId.CLEAR_ALL_TASKS -> str(R.string.tile_clear_all_tasks) to false
                QuickSettingTileId.REPLAY_BUFFER -> str(R.string.tile_replay_buffer) to prefs.getBoolean("replay_enabled", false)
                QuickSettingTileId.REPLAY_STUDIO -> str(R.string.tile_replay_studio) to false
                QuickSettingTileId.STICK_LED -> str(R.string.text_joystick_lights) to light
                QuickSettingTileId.KEYBOARD -> str(R.string.virtual_keyboard_text) to false
            }
            QuickTile(key, id.icon, title, active)
        }
    }

    private fun str(@StringRes id: Int): String = context.getString(id)

    private fun triggerTitle(v: Int) = str(
        when (v) { 0 -> R.string.tile_trigger_digital; 1 -> R.string.tile_trigger_analog; else -> R.string.tile_trigger_both },
    )

    private fun perfTitle(v: Int) = str(
        when (v) { 1 -> R.string.tile_perf_medium; 2 -> R.string.tile_perf_high; else -> R.string.tile_perf_standard },
    )
    private fun fanTitle(v: Int) = str(
        when (v) {
            0 -> R.string.text_fan_control_off; 4 -> R.string.text_fan_control_smart; 5 -> R.string.text_fan_control_medium_speed
            6 -> R.string.text_fan_control_custom; else -> R.string.text_fan_control_low_speed
        },
    )
    private fun styleTitle(v: Int) = str(
        when (v) { 0 -> R.string.fan_option_xbox_style; 2 -> R.string.fan_option_disable; else -> R.string.tile_style_standard },
    )
    private fun themeTitle(v: Int) = str(
        when (v) { 1 -> R.string.tile_theme_system; 3 -> R.string.text_light_theme; else -> R.string.text_dark_theme },
    )

    private fun formatSpeed(bps: Double): String {
        val kb = bps / 1024.0
        return if (kb >= 1024) context.getString(R.string.unit_mb_per_sec, kb / 1024.0)
        else context.getString(R.string.unit_kb_per_sec, kb.coerceAtLeast(0.0))
    }

    private fun wifiLevel(): Int {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return -1
        if (!wm.isWifiEnabled) return -1
        @Suppress("DEPRECATION") val rssi = wm.connectionInfo?.rssi ?: return -1
        return WifiManager.calculateSignalLevel(rssi, 4).coerceIn(0, 3)
    }

    private fun bluetoothConnected(): Boolean {
        // BLUETOOTH_CONNECT is granted by PermissionGranter (root); until then report "not connected".
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return runCatching {
            adapter.isEnabled && adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        }.getOrDefault(false)
    }

    // ---- OverlayActions (writes) ----

    /**
     * "Only top screen" / "only bottom screen" / dual: Command Panel's own screen-off state, which [ScreenModeController]
     * applies with a black cover. The vendor's display mode stays 0, since its "off" screen dims every app window (#35).
     */
    override fun selectMode(mode: Int) {
        ScreenModeController.select(prefs, mode)
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
        val index = (fraction.coerceIn(0f, 1f) * max).toInt()
        if (index != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        if (committed) {
            topGuard.onRelease(now())
            main.removeCallbacks(topCatchUp)
            topGuard.holdEndsAt()?.let { main.postAtTime(topCatchUp, it + 1) }
        } else {
            topGuard.onDrag()
        }
        _uiState.update { it.copy(topVolume = fraction.coerceIn(0f, 1f)) }
    }

    override fun setBottomVolume(fraction: Float, committed: Boolean) {
        val level = bottomVolumeLevel(fraction)
        val t = now()
        if (committed) {
            main.removeCallbacks(bottomFlush)
            bottomGuard.onRelease(t)
            bottomThrottle.onCommit(level, t)?.let(::writeBottomLevel)
            // Settle on the step that was written, like the stock slider.
            _uiState.update { it.copy(bottomVolume = level / BOTTOM_VOLUME_MAX.toFloat()) }
            scheduleBottomCatchUp()
        } else {
            bottomGuard.onDrag()
            _uiState.update { it.copy(bottomVolume = fraction.coerceIn(0f, 1f)) }
            val write = bottomThrottle.onDrag(level, t)
            if (write != null) writeBottomLevel(write)
            main.removeCallbacks(bottomFlush)
            bottomThrottle.flushDelay(t)?.let { main.postDelayed(bottomFlush, it) }
        }
    }

    /** Writes the bottom level through the root shell, off the main thread and in the order requested. */
    private fun writeBottomLevel(level: Int) {
        val scope = scope ?: return
        bottomWritesInFlight++
        scope.launch {
            withContext(bottomWrites) { settings.putInt(Keys.SECONDARY_VOLUME, level) }
            bottomWritesInFlight--
            bottomGuard.onWriteDone(now())
            scheduleBottomCatchUp()
        }
    }

    /** Once the echo hold ends, reads the setting back once so a change made meanwhile (elsewhere) isn't missed. */
    private fun scheduleBottomCatchUp() {
        val at = bottomGuard.holdEndsAt() ?: return
        main.removeCallbacks(bottomCatchUp)
        main.postAtTime(bottomCatchUp, at + 1)
    }

    /**
     * A hardware volume key ([keyType] from `action.volume.key.event`) while Command Panel stands in for the stock
     * assistant (#7): past the music volume's max or min, the key moves the bottom level one step, as stock does.
     * A drag in progress wins over the key.
     */
    fun onVolumeKey(keyType: String?) {
        val t = now()
        val bottom = if (bottomWritesInFlight > 0) bottomThrottle.lastWritten else null
        val level = volumeKeys.onKey(
            keyType, t,
            music = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            musicMin = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
            musicMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            // A write still on its way counts, so quick presses don't read a stale setting and lose steps.
            bottom = bottom ?: settings.getInt(Keys.SECONDARY_VOLUME, 7).coerceIn(0, BOTTOM_VOLUME_MAX),
        ) ?: return
        if (bottomGuard.dragging) return
        bottomThrottle.sync(level)
        bottomGuard.onRelease(t)
        writeBottomLevel(level)
        _uiState.update { it.copy(bottomVolume = level / BOTTOM_VOLUME_MAX.toFloat()) }
    }

    /**
     * The overlay was hidden, possibly in the middle of a drag whose release never arrives: commit what the slider
     * shows so the guard doesn't keep ignoring the setting.
     */
    private fun endBottomDragIfAny() {
        if (bottomGuard.dragging) setBottomVolume(uiState.value.bottomVolume, committed = true)
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
            "stick_led" -> toggleStickLight()
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
            // keyboard → AssistantService (it owns the Panel plugin session)
            // replay_studio → AssistantService (opens the companion)
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
        val isOn = ambientLightShown() == true
        privileged.shell("am broadcast -a com.ro.external.request.light.adj.action --ez isShow ${!isOn}")
    }

    /** The vendor daemon's ambient-light switch (`sys.light.curr.is.show`), or `null` if it can't be read. */
    private fun ambientLightShown(): Boolean? = when (privileged.shell("getprop sys.light.curr.is.show")?.trim()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }

    /**
     * Stick LED tile (fork feature): both sticks on or off through `joystick_light_enabled`, which the vendor settings'
     * "Joystick Lights" switch uses. In case the LED daemon doesn't act on a write from outside, the SN3112 nodes are
     * then written directly: black for off; the saved color for on, unless Ambient LED is switched off.
     */
    private fun toggleStickLight() {
        val on = !SystemSettings.StickLight.isOn(settings.getString(Keys.JOYSTICK_LIGHT))
        settings.putString(Keys.JOYSTICK_LIGHT, SystemSettings.StickLight.valueFor(on))
        scope?.launch(Dispatchers.IO) {
            delay(STICK_LED_FALLBACK_DELAY_MS) // let the daemon apply the key first
            if (!on || ambientLightShown() != false) {
                jr.brian.commandpannel.privileged.ThorRgbController.setSticksLit(context, on)
            }
        }
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

    /**
     * Set by the service: what the panel does once Quick Launch opened the app (close, stay, or close and reopen
     * when the app closes; #9). Closes it by default, like Task and the stock panel's own launches.
     */
    var onQuickLaunched: () -> Unit = { onHideOverlay() }

    override fun quickLaunch() {
        if (QuickLaunch.launch(context)) onQuickLaunched()
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

    override fun setLook(look: jr.brian.commandpannel.ui.theme.TccUiLook) {
        // The pref listener restyles the overlay; the companion picks it up through its own listener.
        CustomLookStore.select(prefs, jr.brian.commandpannel.ui.theme.LookRef.BuiltIn(look))
    }

    override fun setOnboardingStep(step: OverlayOnboardingStep) {
        _uiState.update { if (it.onboardingStep == null) it else it.copy(onboardingStep = step) }
    }

    override fun endOnboarding() {
        prefs.edit().putBoolean(OverlayOnboarding.KEY_SEEN, true).apply()
        _uiState.update { it.copy(onboardingStep = null) }
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

    private companion object {
        const val BOTTOM_WRITE_INTERVAL_MS = 100L
        /** How long a slider keeps ignoring change notifications after its release or last write. */
        const val ECHO_HOLD_MS = 400L
        // AudioManager's hidden VOLUME_CHANGED_ACTION and its stream extra.
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }

    /**
     * When the "Sync overlay accent to stick LED" fork toggle is on, the Thor's ambient-LED color becomes the
     * overlay accent (inverted from the original theme→LED sync). Returns the opaque ARGB accent, or null when off.
     */
    private fun readAccentOverride(): Int? = jr.brian.commandpannel.privileged.ThorRgbController
        .accentOverride(context, prefs.getBoolean("rgb_accent_sync", false))
}

/** How long the Stick LED tile waits for the LED daemon before writing the nodes itself. */
private const val STICK_LED_FALLBACK_DELAY_MS = 400L
