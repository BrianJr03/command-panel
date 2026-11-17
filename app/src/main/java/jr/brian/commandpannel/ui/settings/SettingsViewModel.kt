package jr.brian.commandpannel.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.ThorRgbController
import android.content.ComponentName
import jr.brian.commandpannel.service.AssistantService
import jr.brian.commandpannel.system.QuickLaunch
import jr.brian.commandpannel.system.QuickLaunchApp
import jr.brian.commandpannel.system.QuickLaunchDisplay
import jr.brian.commandpannel.system.SystemSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SettingsCategory(val title: String) {
    GENERAL("General"),
    QUICK_TOGGLES("Quick Toggles"),
    APPEARANCE("Appearance"),
    DIAGNOSTICS("Diagnostics"),
}

data class SettingsState(
    val replaceStock: Boolean = false,
    val batteryEstimate: Boolean = false,
    val hideNetSpeed: Boolean = false,
    val keepOnScreen: Boolean = true,
    val backupMessage: String? = null,
    val themeMode: Int = 4,              // tcc_theme_mode
    val rgbAccentSync: Boolean = false,
    /** Ambient-LED accent (opaque ARGB) when [rgbAccentSync] is on; themes the companion when it follows the look. */
    val accentOverride: Int? = null,
    /** When on, the companion screen uses the overlay's look, theme mode and accent instead of its default style. */
    val companionFollowLook: Boolean = false,
    val enabledTiles: Set<String> = emptySet(),
    val orderedEnabledTiles: List<String> = emptyList(),
    val quickLaunchLabel: String? = null,
    val quickLaunchDisplay: QuickLaunchDisplay = QuickLaunchDisplay.TOP,
    val overlayLayout: jr.brian.commandpannel.system.OverlayLayout = jr.brian.commandpannel.system.OverlayLayout.DEFAULT,
    val overlayLook: jr.brian.commandpannel.ui.theme.TccUiLook = jr.brian.commandpannel.ui.theme.TccUiLook.AUTO,
    val replayEnabled: Boolean = false,
    val replayWindowSeconds: Int = 30,
    val replayPreset: jr.brian.commandpannel.privileged.ReplayBitratePreset = jr.brian.commandpannel.privileged.ReplayBitratePreset.BALANCED,
    val replayMessage: String? = null,
    val appPickerOpen: Boolean = false,
    val apps: List<QuickLaunchApp> = emptyList(),
    val diagnostics: List<Pair<String, Boolean?>> = emptyList(),
    val diagnosticsRunning: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    app: Application,
    private val privileged: PrivilegedAccess,
) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("command_panel", Application.MODE_PRIVATE)
    private val settings = SystemSettings(app.contentResolver, privileged)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val app get() = getApplication<Application>()

    private fun load() = SettingsState(
        replaceStock = prefs.getBoolean("replace_stock_assistant", false),
        batteryEstimate = prefs.getBoolean("battery_estimate", false),
        hideNetSpeed = prefs.getBoolean("hide_net_speed", false),
        keepOnScreen = prefs.getBoolean("clear_tasks_keep_on_screen", true),
        themeMode = settings.getInt(SystemSettings.Keys.THEME_MODE, 4),
        rgbAccentSync = prefs.getBoolean("rgb_accent_sync", false),
        accentOverride = ThorRgbController.accentOverride(app, prefs.getBoolean("rgb_accent_sync", false)),
        companionFollowLook = prefs.getBoolean("companion_follow_look", false),
        enabledTiles = jr.brian.commandpannel.system.QuickSettingTileId
            .enabledKeys(prefs.getString("qs_enabled_tiles", null)).toSet(),
        orderedEnabledTiles = jr.brian.commandpannel.system.QuickSettingTileId
            .enabledKeys(prefs.getString("qs_enabled_tiles", null), prefs.getString("qs_tile_order", null)),
        quickLaunchLabel = QuickLaunch.savedLabel(getApplication()),
        quickLaunchDisplay = QuickLaunch.savedDisplay(getApplication()),
        overlayLayout = jr.brian.commandpannel.system.OverlayLayout.decode(prefs.getString("overlay_layout", null)),
        overlayLook = jr.brian.commandpannel.ui.theme.TccUiLook.fromKey(prefs.getString("overlay_look", null)),
        replayEnabled = prefs.getBoolean("replay_enabled", false),
        replayWindowSeconds = jr.brian.commandpannel.privileged.ScreenRecorderEngine
            .normalizeReplayWindowSeconds(prefs.getInt("replay_window_seconds", 30)),
        replayPreset = jr.brian.commandpannel.privileged.ReplayBitratePreset.fromKey(prefs.getString("replay_bitrate", null)),
    )

    fun setReplayEnabled(enable: Boolean) {
        prefs.edit().putBoolean("replay_enabled", enable).apply()
        AssistantService.start(app)
        app.sendBroadcast(
            Intent(AssistantService.ACTION_REPLAY_SET_ENABLED).setPackage(app.packageName)
                .putExtra(AssistantService.EXTRA_ENABLE, enable),
        )
        _state.value = _state.value.copy(replayEnabled = enable, replayMessage = if (enable) "Replay buffer arming…" else "Replay buffer off")
    }

    fun setReplayWindowSeconds(seconds: Int) {
        prefs.edit().putInt("replay_window_seconds", seconds).apply()
        _state.value = _state.value.copy(replayWindowSeconds = seconds, replayMessage = "Takes effect next time the buffer arms")
    }

    fun setReplayPreset(preset: jr.brian.commandpannel.privileged.ReplayBitratePreset) {
        prefs.edit().putString("replay_bitrate", preset.key).apply()
        _state.value = _state.value.copy(replayPreset = preset, replayMessage = "Takes effect next time the buffer arms")
    }

    fun saveReplayClip() {
        app.sendBroadcast(Intent(AssistantService.ACTION_REPLAY_SAVE).setPackage(app.packageName))
        _state.value = _state.value.copy(replayMessage = "Saving clip…")
        // Surface the result the service broadcasts back.
        val filter = android.content.IntentFilter(AssistantService.ACTION_REPLAY_SAVED)
        app.registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) {
                val result = i.getStringExtra(AssistantService.EXTRA_REPLAY_RESULT).orEmpty()
                val msg = if (result.startsWith("error:")) "Save failed (${result.removePrefix("error:")})" else "Saved to $result"
                _state.value = _state.value.copy(replayMessage = msg)
                runCatching { c.unregisterReceiver(this) }
            }
        }, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    fun setLook(look: jr.brian.commandpannel.ui.theme.TccUiLook) {
        prefs.edit().putString("overlay_look", look.name).apply()
        _state.value = _state.value.copy(overlayLook = look)
    }

    private fun updateLayout(transform: (jr.brian.commandpannel.system.OverlayLayout) -> jr.brian.commandpannel.system.OverlayLayout) {
        val next = transform(_state.value.overlayLayout)
        prefs.edit().putString("overlay_layout", next.encode()).apply()
        _state.value = _state.value.copy(overlayLayout = next)
    }

    fun setNavBarTop(top: Boolean) = updateLayout {
        it.withNavBarPosition(
            if (top) jr.brian.commandpannel.system.NavBarPosition.TOP
            else jr.brian.commandpannel.system.NavBarPosition.BOTTOM
        )
    }
    fun toggleModeRows() = updateLayout { it.copy(modeRowsSwapped = !it.modeRowsSwapped) }
    fun toggleModeHalves() = updateLayout { it.copy(modeHalfOrder = it.modeHalfOrder.reversed()) }
    fun toggleSettingsRows() = updateLayout { it.copy(settingsRowsSwapped = !it.settingsRowsSwapped) }
    fun toggleSettingsCols() = updateLayout { it.copy(settingsColsSwapped = !it.settingsColsSwapped) }
    fun toggleQuarters() = updateLayout { it.copy(settingsQuarterOrder = it.settingsQuarterOrder.reversed()) }
    fun resetLayout() = updateLayout { jr.brian.commandpannel.system.OverlayLayout.DEFAULT }

    /** Toggles the inverted sync: when on, the Thor ambient-LED colour becomes the overlay accent. */
    fun setRgbAccentSync(enable: Boolean) {
        prefs.edit().putBoolean("rgb_accent_sync", enable).apply()
        // The overlay repository reads the LED colour on the next refresh (its pref listener fires here).
        _state.value = _state.value.copy(rgbAccentSync = enable, accentOverride = ThorRgbController.accentOverride(app, enable))
    }

    /** Toggles whether the companion screen follows the overlay look (off keeps the default companion style). */
    fun setCompanionFollowLook(enable: Boolean) {
        prefs.edit().putBoolean("companion_follow_look", enable).apply()
        _state.value = _state.value.copy(companionFollowLook = enable)
    }

    fun openAppPicker() {
        _state.value = _state.value.copy(appPickerOpen = true)
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { QuickLaunch.listApps(app) }
            _state.value = _state.value.copy(apps = apps)
        }
    }

    fun closeAppPicker() {
        _state.value = _state.value.copy(appPickerOpen = false)
    }

    fun pickApp(component: ComponentName) {
        QuickLaunch.save(app, component, _state.value.quickLaunchDisplay)
        _state.value = _state.value.copy(
            appPickerOpen = false,
            quickLaunchLabel = QuickLaunch.savedLabel(app),
        )
    }

    fun setQuickLaunchDisplay(display: QuickLaunchDisplay) {
        QuickLaunch.savedComponent(app)?.let { QuickLaunch.save(app, it, display) }
        _state.value = _state.value.copy(quickLaunchDisplay = display)
    }

    fun clearQuickLaunch() {
        QuickLaunch.clear(app)
        _state.value = _state.value.copy(quickLaunchLabel = null)
    }

    fun refresh() { _state.value = load() }

    fun setBatteryEstimate(enable: Boolean) {
        prefs.edit().putBoolean("battery_estimate", enable).apply()
        _state.value = _state.value.copy(batteryEstimate = enable)
    }

    fun setHideNetSpeed(enable: Boolean) {
        prefs.edit().putBoolean("hide_net_speed", enable).apply()
        _state.value = _state.value.copy(hideNetSpeed = enable)
    }

    fun exportBackup() {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                jr.brian.commandpannel.system.SettingsBackup.export(app, privileged)
            }
            _state.value = _state.value.copy(backupMessage = if (path != null) "Saved to $path" else "Export failed")
        }
    }

    fun restoreLatestBackup() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                val latest = jr.brian.commandpannel.system.SettingsBackup.list(privileged).firstOrNull()
                    ?: return@withContext -1
                jr.brian.commandpannel.system.SettingsBackup.import(app, privileged, latest)
            }
            val msg = when {
                n < 0 -> "No backup found"
                n == 0 -> "Restore failed"
                else -> "Restored $n settings"
            }
            _state.value = load().copy(backupMessage = msg)
        }
    }

    fun setKeepOnScreen(enable: Boolean) {
        prefs.edit().putBoolean("clear_tasks_keep_on_screen", enable).apply()
        _state.value = _state.value.copy(keepOnScreen = enable)
    }

    fun setTileEnabled(key: String, enable: Boolean) {
        val csv = jr.brian.commandpannel.system.QuickSettingTileId
            .toggle(prefs.getString("qs_enabled_tiles", null), key, enable)
        prefs.edit().putString("qs_enabled_tiles", csv).apply()
        _state.value = load()
    }

    /** Saves a drag-and-drop order of the enabled quick tiles. */
    fun reorderTiles(enabledOrder: List<String>) {
        val enabledCsv = prefs.getString("qs_enabled_tiles", null)
        val order = jr.brian.commandpannel.system.QuickSettingTileId.reorderEnabled(
            prefs.getString("qs_tile_order", null), enabledCsv, enabledOrder
        )
        prefs.edit().putString("qs_tile_order", order).apply()
        _state.value = _state.value.copy(
            orderedEnabledTiles = jr.brian.commandpannel.system.QuickSettingTileId.enabledKeys(enabledCsv, order),
        )
    }

    fun setReplaceStock(enable: Boolean) {
        AssistantService.start(getApplication())
        getApplication<Application>().startService(
            Intent(getApplication(), AssistantService::class.java)
                .setAction(AssistantService.ACTION_SET_REPLACE_STOCK)
                .putExtra(AssistantService.EXTRA_ENABLE, enable)
        )
        _state.value = _state.value.copy(replaceStock = enable)
    }

    fun setThemeMode(mode: Int) {
        settings.putInt(SystemSettings.Keys.THEME_MODE, mode)
        _state.value = _state.value.copy(themeMode = mode)
    }

    fun showOverlay() {
        AssistantService.start(getApplication())
        getApplication<Application>().startService(
            Intent(getApplication(), AssistantService::class.java).setAction(AssistantService.ACTION_SHOW_OVERLAY)
        )
    }

    fun runDiagnostics() {
        if (_state.value.diagnosticsRunning) return
        _state.value = _state.value.copy(diagnosticsRunning = true, diagnostics = emptyList())
        viewModelScope.launch {
            val r = privileged.initialize(getApplication())
            _state.value = _state.value.copy(
                diagnosticsRunning = false,
                diagnostics = buildList {
                    add("Hidden-API exemptions" to r.hiddenApi)
                    add("PServerBinder root shell" to r.rootShell)
                    add("Root helper connected" to r.helper)
                    r.helperIdentity?.let { add(it to true) }
                },
            )
        }
    }
}
