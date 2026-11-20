package jr.brian.commandpannel.system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Wifi
import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.TileIcon

enum class TileCategory(@StringRes val titleRes: Int) {
    STOCK(R.string.tile_category_stock), ANDROID(R.string.tile_category_android), TOOLS(R.string.tile_category_tools)
}

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
    CLEAR_ALL_TASKS("clear_all_tasks", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.DeleteSweep), TileInteraction.ACTION),
    REPLAY_BUFFER("replay_buffer", TileCategory.TOOLS, false, TileIcon.Stock("ic_qs_replay_buffer"), TileInteraction.TOGGLE),
    /** Opens the companion on Replay Studio (#49). Next to Replay Buffer; a saved order gets it at the end. */
    REPLAY_STUDIO("replay_studio", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.VideoLibrary), TileInteraction.ACTION),
    STICK_LED("stick_led", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.SportsEsports), TileInteraction.TOGGLE),
    /** Opens ThorKBM: a plugin swap, its app, or the companion's Keyboard section ([KeyboardTile]). */
    KEYBOARD("keyboard", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.Keyboard), TileInteraction.ACTION),
    /** Shows or hides the look's background image ([BackgroundImageTile], #58); grayed out when the look has none. */
    BACKGROUND_IMAGE("background_image", TileCategory.TOOLS, false, TileIcon.Vector(Icons.Filled.Wallpaper), TileInteraction.TOGGLE);

    /** Display name for the companion app's enable/disable list. */
    @get:StringRes
    val labelRes: Int get() = when (this) {
        FOCUS_LOCK -> R.string.key_focus_mode_title_text
        PERFORMANCE -> R.string.text_performance_mode_title
        FAN -> R.string.text_fan_control_title
        CLEAR_MEMORY -> R.string.text_clear_memory_title
        CONTROLLER -> R.string.text_handle_style_title
        CHARGING_SEP -> R.string.text_charging_separation_title
        VIBRATION -> R.string.text_vibration_open_title
        AMBIENT_LED -> R.string.text_led_lights_open_title
        FORCE_LANDSCAPE -> R.string.force_landscape_mode_text
        THEME -> R.string.text_theme_mode_title
        WIFI -> R.string.tile_name_wifi
        BLUETOOTH -> R.string.tile_name_bluetooth
        DO_NOT_DISTURB -> R.string.tile_name_do_not_disturb
        AIRPLANE -> R.string.tile_name_airplane
        KEEP_SCREEN_ON -> R.string.tile_name_keep_screen_on
        DOUBLE_TAP_HOME -> R.string.tile_name_double_tap_home
        CHARGE_LIMIT_80 -> R.string.tile_name_charge_limit_80
        VIRTUAL_MOUSE -> R.string.tile_name_virtual_mouse
        TRIGGER_MODE -> R.string.tile_name_trigger_mode
        SCREENSHOT -> R.string.tile_name_screenshot
        CLEAR_ALL_TASKS -> R.string.tile_name_clear_all_tasks
        REPLAY_BUFFER -> R.string.tile_name_replay_buffer
        REPLAY_STUDIO -> R.string.tile_name_replay_studio
        STICK_LED -> R.string.text_joystick_lights
        KEYBOARD -> R.string.virtual_keyboard_text
        BACKGROUND_IMAGE -> R.string.tile_name_background_image
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

        /**
         * Applies a new order of the enabled tiles (from drag-and-drop); disabled tiles keep their slots in the full
         * order. Returns the new full-order csv, or the current one if [enabledOrder] isn't a permutation of the
         * enabled tiles.
         */
        fun reorderEnabled(orderCsv: String?, enabledCsv: String?, enabledOrder: List<String>): String {
            val order = orderedKeys(orderCsv)
            val enabled = enabledKeys(enabledCsv, orderCsv)
            if (enabledOrder.size != enabled.size || enabledOrder.toSet() != enabled.toSet()) return order.joinToString(",")
            val next = enabledOrder.iterator()
            val enabledSet = enabled.toSet()
            return order.map { if (it in enabledSet) next.next() else it }.joinToString(",")
        }
    }
}
