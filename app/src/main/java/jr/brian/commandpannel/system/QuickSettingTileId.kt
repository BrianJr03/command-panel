package jr.brian.commandpannel.system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import jr.brian.commandpannel.ui.components.TileIcon

enum class TileCategory(val title: String) { STOCK("Stock"), ANDROID("Android System"), TOOLS("Tools") }

enum class TileInteraction { TOGGLE, DIALOG, ACTION }

/**
 * The full quick-setting tile roster. The 10 STOCK tiles (enabled by default, stock drawables) match the stock app;
 * the ANDROID and TOOLS tiles (fork features, off by default, Material icons) are opt-in in the companion app.
 */
enum class QuickSettingTileId(
    val key: String,
    val category: TileCategory,
    val defaultEnabled: Boolean,
    val icon: TileIcon,
    val interaction: TileInteraction,
) {
    FOCUS_LOCK("focus_lock", TileCategory.STOCK, true, TileIcon.Stock("ic_screen_focus_lock_mode"), TileInteraction.DIALOG),
    PERFORMANCE("performance", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_performance"), TileInteraction.DIALOG),
    FAN("fan", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_fan_control"), TileInteraction.DIALOG),
    CLEAR_MEMORY("clear_memory", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_clean"), TileInteraction.ACTION),
    CONTROLLER("controller", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_controller_style"), TileInteraction.DIALOG),
    CHARGING_SEP("charging_sep", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_charging_separation"), TileInteraction.TOGGLE),
    VIBRATION("vibration", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_vibrate"), TileInteraction.TOGGLE),
    AMBIENT_LED("ambient_led", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_ambient_light"), TileInteraction.TOGGLE),
    FORCE_LANDSCAPE("force_landscape", TileCategory.STOCK, true, TileIcon.Stock("ic_landscape"), TileInteraction.TOGGLE),
    THEME("theme", TileCategory.STOCK, true, TileIcon.Stock("ic_qs_theme_mode"), TileInteraction.DIALOG),

    WIFI("wifi", TileCategory.ANDROID, false, TileIcon.Vector(Icons.Filled.Wifi), TileInteraction.TOGGLE),
    BLUETOOTH("bluetooth", TileCategory.ANDROID, false, TileIcon.Vector(Icons.Filled.Bluetooth), TileInteraction.TOGGLE),
    DO_NOT_DISTURB("do_not_disturb", TileCategory.ANDROID, false, TileIcon.Vector(Icons.Filled.DoNotDisturbOn), TileInteraction.TOGGLE),
    AIRPLANE("airplane_mode", TileCategory.ANDROID, false, TileIcon.Vector(Icons.Filled.AirplanemodeActive), TileInteraction.TOGGLE),

    KEEP_SCREEN_ON("keep_screen_on", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.Visibility), TileInteraction.TOGGLE),
    DOUBLE_TAP_HOME("prevent_accidental_home", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.Home), TileInteraction.TOGGLE),
    CHARGE_LIMIT_80("charge_limit_80", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.BatteryChargingFull), TileInteraction.TOGGLE),
    VIRTUAL_MOUSE("gamepad_mouse_mode", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.Mouse), TileInteraction.TOGGLE),
    TRIGGER_MODE("trigger_mode", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.Tune), TileInteraction.DIALOG),
    SCREENSHOT("screenshot", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.PhotoCamera), TileInteraction.DIALOG),
    CLEAR_ALL_TASKS("clear_all_tasks", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.DeleteSweep), TileInteraction.ACTION);

    /** Display name for the companion app's enable/disable list. */
    val label: String get() = when (this) {
        FOCUS_LOCK -> "Focus Mode"
        PERFORMANCE -> "Performance Mode"
        FAN -> "Fan Control"
        CLEAR_MEMORY -> "Clean Memory"
        CONTROLLER -> "Handle Style"
        CHARGING_SEP -> "Direct Power Supply"
        VIBRATION -> "Vibration"
        AMBIENT_LED -> "Ambient LED"
        FORCE_LANDSCAPE -> "Force Landscape"
        THEME -> "Theme Mode"
        WIFI -> "Wi-Fi"
        BLUETOOTH -> "Bluetooth"
        DO_NOT_DISTURB -> "Do Not Disturb"
        AIRPLANE -> "Airplane Mode"
        KEEP_SCREEN_ON -> "Keep Screen On"
        DOUBLE_TAP_HOME -> "Double-Tap HOME"
        CHARGE_LIMIT_80 -> "80% Charge Limit"
        VIRTUAL_MOUSE -> "Virtual Mouse"
        TRIGGER_MODE -> "L2/R2 Trigger Mode"
        SCREENSHOT -> "Screenshot"
        CLEAR_ALL_TASKS -> "Clear All Tasks"
    }

    companion object {
        fun fromKey(key: String): QuickSettingTileId? = entries.firstOrNull { it.key == key }
        val defaultEnabledKeys: List<String> get() = entries.filter { it.defaultEnabled }.map { it.key }

        /** All keys in the saved display order (unknown/new keys appended in roster order). */
        fun orderedKeys(orderCsv: String?): List<String> {
            val saved = orderCsv?.split(",")?.filter { fromKey(it) != null } ?: emptyList()
            return saved + entries.map { it.key }.filter { it !in saved }
        }

        private fun enabledSet(enabledCsv: String?): Set<String> =
            enabledCsv?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: defaultEnabledKeys.toSet()

        /** Enabled tiles in the saved display order. */
        fun enabledKeys(enabledCsv: String?, orderCsv: String? = null): List<String> {
            val set = enabledSet(enabledCsv)
            return orderedKeys(orderCsv).filter { it in set }
        }

        /** Apply an enable/disable to the csv, keeping roster order and at least one tile enabled. */
        fun toggle(csv: String?, key: String, enable: Boolean): String {
            val current = enabledSet(csv).toMutableSet()
            if (enable) current.add(key) else if (current.size > 1) current.remove(key)
            return entries.map { it.key }.filter { it in current }.joinToString(",")
        }

        /** Moves [key] one slot earlier/later among the enabled tiles; returns the new full-order csv. */
        fun moveEnabled(orderCsv: String?, enabledCsv: String?, key: String, up: Boolean): String {
            val order = orderedKeys(orderCsv).toMutableList()
            val enabled = enabledKeys(enabledCsv, orderCsv)
            val pos = enabled.indexOf(key)
            val target = pos + if (up) -1 else 1
            if (pos < 0 || target !in enabled.indices) return order.joinToString(",")
            val i = order.indexOf(key)
            val j = order.indexOf(enabled[target])
            order[i] = order[j].also { order[j] = order[i] }
            return order.joinToString(",")
        }
    }
}
