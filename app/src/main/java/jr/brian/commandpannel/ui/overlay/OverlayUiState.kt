package jr.brian.commandpannel.ui.overlay

/**
 * Everything the overlay renders. Phase 3 fills it with sample values that match the stock screenshots; phase 4
 * wires each field to its real source (Settings observers, telemetry, audio, etc.).
 */
data class OverlayUiState(
    val clock: String = "9:58",
    val wifiLevel: Int = 3,          // 0..3, -1 hides
    val bluetoothOn: Boolean = false,
    val netSpeed: String = "7.3KB/S",
    val batteryPercent: Int = 35,
    val charging: Boolean = false,
    val batteryEstimate: String? = null,   // fork feature: time-to-empty/full after the %
    val hideNetSpeed: Boolean = false,     // fork feature: hide the status-bar download speed

    val displayMode: Int = 0,        // 0 dual, 1 top only, 2 bottom only
    val focusLockTitle: String = "Top screen",
    val focusLock: Int = 1,          // screen_focus_lock
    val performanceMode: Int = 0,
    val fanMode: Int = 1,            // 0/1/4/5/6
    val fanSpeed: Int = 25000,
    val controllerStyle: Int = 1,    // temp_abxy_layout_mode
    val triggerMode: Int = 2,        // trigger_input_mode: 0 digital, 1 analog, 2 both

    val dualBrightness: Float = 0.5f,
    val topBrightness: Float = 0.5f,
    val bottomBrightness: Float = 0.5f,
    val topVolume: Float = 0.5f,
    val bottomVolume: Float = 0.73f,

    val is120Hz: Boolean = false,
    val fps: Int = 49,

    val tempC: Int = 60,
    val fanPercent: Int = 24,
    val cpuGhz: Float = 1.84f,
    val cpuFraction: Float = 0.58f,
    val gpuMhz: Int = 401,
    val gpuFraction: Float = 0.70f,
    val powerW: Float = -4.0f,
    val powerFraction: Float = 0.30f,
    val ramGb: Float = 7.0f,
    val ramFraction: Float = 0.62f,

    val themeMode: Int = 4,          // tcc_theme_mode: 1 system, 3 light, 4 dark
    val suppressBottomTip: Boolean = false,
    val quickLaunchLabel: String? = null,  // fork feature: null = no Launch nav button
    val tiles: List<QuickTile> = QuickTile.STOCK_DEFAULTS,
    val tilePage: Int = 0,
    val layout: jr.brian.commandpannel.system.OverlayLayout = jr.brian.commandpannel.system.OverlayLayout.DEFAULT,
)

/** A quick-settings tile: its icon (stock drawable or Material vector) and the title for its current value. */
data class QuickTile(
    val id: String,
    val icon: jr.brian.commandpannel.ui.components.TileIcon,
    val title: String,
    val active: Boolean = false,
) {
    companion object {
        private fun stock(name: String) = jr.brian.commandpannel.ui.components.TileIcon.Stock(name)
        val STOCK_DEFAULTS = listOf(
            QuickTile("focus_lock", stock("ic_screen_focus_lock_mode"), "Top screen"),
            QuickTile("performance", stock("ic_qs_performance"), "Standard\nPerformance"),
            QuickTile("fan", stock("ic_qs_fan_control"), "Quiet Mode", active = true),
            QuickTile("clear_memory", stock("ic_qs_clean"), "Clean\nMemory"),
            QuickTile("controller", stock("ic_qs_controller_style"), "Standard\nStyle"),
            QuickTile("charging_sep", stock("ic_qs_charging_separation"), "Direct Power\nSupply"),
            QuickTile("vibration", stock("ic_qs_vibrate"), "Vibration", active = true),
            QuickTile("ambient_led", stock("ic_qs_ambient_light"), "Ambient LED", active = true),
            QuickTile("force_landscape", stock("ic_landscape"), "Force\nLandscape"),
            QuickTile("theme", stock("ic_qs_theme_mode"), "Dark Theme", active = true),
        )
    }
}

enum class OverlayTab { MODE, SETTINGS }
