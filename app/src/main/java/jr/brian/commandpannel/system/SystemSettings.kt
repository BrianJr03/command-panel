package jr.brian.commandpannel.system

import android.content.ContentResolver
import android.provider.Settings
import jr.brian.commandpannel.privileged.PrivilegedAccess

/**
 * Typed access to the vendor `Settings.System` keys the stock assistant uses (see the teardown, NOTES.md §4).
 *
 * Reads go through the `ContentResolver` directly. Writes go through the root shell (`settings put system …`): the
 * app's own uid may be refused on vendor keys even with `WRITE_SETTINGS`, but root always succeeds, and this keeps
 * one code path for every key.
 */
class SystemSettings(
    private val resolver: ContentResolver,
    private val privileged: PrivilegedAccess,
) {
    fun getInt(key: String, def: Int): Int =
        runCatching { Settings.System.getInt(resolver, key, def) }.getOrDefault(def)

    fun getFloat(key: String, def: Float): Float =
        runCatching { Settings.System.getFloat(resolver, key, def) }.getOrDefault(def)

    fun getString(key: String): String? =
        runCatching { Settings.System.getString(resolver, key) }.getOrNull()

    fun putInt(key: String, value: Int) {
        privileged.shell("settings put system $key $value")
    }

    fun putFloat(key: String, value: Float) {
        privileged.shell("settings put system $key $value")
    }

    fun uriFor(key: String) = Settings.System.getUriFor(key)

    object Keys {
        const val DISPLAY_MODE = "dual_screen_display_mode"       // 0 dual, 1 top only, 2 bottom only
        const val FOCUS_LOCK = "screen_focus_lock"               // 0 switch, 1 top, 2 bottom
        const val SYSTEM_KEY_FOCUS_LOCK = "enable_system_key_focus_lock"
        const val DUAL_BRIGHTNESS = "dual_screen_brightness_level" // 0..100
        const val SECONDARY_VOLUME = "secondary_screen_volume_level" // 0..15
        const val PERFORMANCE_MODE = "performance_mode"          // 0/1/2
        const val FAN_MODE = "fan_mode"                          // 0/1/4/5/6
        const val FAN_SPEED = "fan_speed"
        const val CONTROLLER_STYLE = "temp_abxy_layout_mode"     // 0 Xbox, 1 Odin, 2 disable
        const val FLIP_BUTTON_LAYOUT = "flip_button_layout"
        const val NO_CREATE_GAMEPAD = "no_create_gamepad_button_layout"
        const val CHARGING_SEPARATION = "is_charging_separation"
        const val VIBRATE_ON = "vibrate_on"
        const val FORCE_LANDSCAPE = "force_landscape"
        const val JOYSTICK_LIGHT = "joystick_light_enabled"      // "a,b"
        const val LED_BRIGHTNESS = "led_light_brightness_percent" // 0..1 float
        const val THEME_MODE = "tcc_theme_mode"                  // 1 system, 3 light, 4 dark
        const val MIN_REFRESH = "min_refresh_rate"
        const val PEAK_REFRESH = "peak_refresh_rate"
        const val SCREEN_BRIGHTNESS = "screen_brightness"        // 0..255 (top display)
    }
}
