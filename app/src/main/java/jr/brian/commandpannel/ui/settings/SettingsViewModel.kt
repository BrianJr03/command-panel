package jr.brian.commandpannel.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jr.brian.commandpannel.privileged.PrivilegedAccess
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
    APPEARANCE("Appearance"),
    DIAGNOSTICS("Diagnostics"),
}

data class SettingsState(
    val replaceStock: Boolean = false,
    val batteryEstimate: Boolean = false,
    val hideNetSpeed: Boolean = false,
    val themeMode: Int = 4,              // tcc_theme_mode
    val quickLaunchLabel: String? = null,
    val quickLaunchDisplay: QuickLaunchDisplay = QuickLaunchDisplay.TOP,
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
        themeMode = settings.getInt(SystemSettings.Keys.THEME_MODE, 4),
        quickLaunchLabel = QuickLaunch.savedLabel(getApplication()),
        quickLaunchDisplay = QuickLaunch.savedDisplay(getApplication()),
    )

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
