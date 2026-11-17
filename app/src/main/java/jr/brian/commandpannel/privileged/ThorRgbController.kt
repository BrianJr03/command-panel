package jr.brian.commandpannel.privileged

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Direct writer for the AYN Thor's SN3112 LED kernel nodes, routed through [VendorRootShell] (fork feature).
 * Mirrors the vendor daemon's wire format — brightness attenuates the RGB channels, sysfs
 * brightness byte stays 255 — because on this driver the brightness byte doesn't dim.
 */
object ThorRgbController {
    private const val LEFT_NODE = "/sys/class/sn3112l/led/brightness"
    private const val RIGHT_NODE = "/sys/class/sn3112r/led/brightness"
    const val PICKER_COLOR_KEY = "joystick_led_light_picker_color"
    const val BRIGHTNESS_PERCENT_KEY = "led_light_brightness_percent"
    private const val SYSFS_BRIGHTNESS = 255

    data class Snapshot(val leftArgb: Int, val rightArgb: Int, val brightnessPercent: Int)

    val isAynThor: Boolean by lazy {
        Build.MANUFACTURER.lowercase().contains("ayn") &&
            Build.MODEL.lowercase().contains("thor")
    }

    fun applyAccent(argb: Int, brightnessPercent: Int) {
        if (!isAynThor) return
        val b = percentTo255(brightnessPercent)
        VendorRootShell.exec(dualCommand(argb, argb, b), wait = false)
    }

    fun applySnapshot(snapshot: Snapshot) {
        if (!isAynThor) return
        val b = percentTo255(snapshot.brightnessPercent)
        VendorRootShell.exec(dualCommand(snapshot.leftArgb, snapshot.rightArgb, b), wait = false)
    }

    fun readSystemSnapshot(context: Context): Snapshot {
        val cr = context.contentResolver
        val rawColor = runCatching { Settings.System.getString(cr, PICKER_COLOR_KEY) }.getOrNull()
        val colors = parseColors(rawColor) ?: (0 to 0)
        val brightnessPct = runCatching {
            Settings.System.getFloat(cr, BRIGHTNESS_PERCENT_KEY, 0.5f)
        }.getOrDefault(0.5f)
        val pct = (brightnessPct.coerceIn(0f, 1f) * 100).toInt().coerceIn(0, 100)
        return Snapshot(colors.first, colors.second, pct)
    }

    fun formatPickerColor(leftArgb: Int, rightArgb: Int): String =
        "${argbToHex(leftArgb)},${argbToHex(rightArgb)}"

    /** The overlay palette's accent to mirror onto the sticks: light (`tcc_theme_mode` 3) vs dark. */
    fun accentForThemeMode(mode: Int): Int = if (mode == 3) 0xFF8E84FF.toInt() else 0xFF6A0D83.toInt()

    private fun argbToHex(argb: Int): String {
        val forced = argb or 0xFF000000.toInt()
        return "#%08X".format(forced)
    }

    private fun percentTo255(pct: Int): Int =
        ((pct.coerceIn(0, 100) / 100f) * 255).toInt().coerceIn(0, 255)

    private fun dualCommand(leftArgb: Int, rightArgb: Int, brightness: Int): String {
        val lr = scale((leftArgb shr 16) and 0xFF, brightness)
        val lg = scale((leftArgb shr 8) and 0xFF, brightness)
        val lb = scale(leftArgb and 0xFF, brightness)
        val rr = scale((rightArgb shr 16) and 0xFF, brightness)
        val rg = scale((rightArgb shr 8) and 0xFF, brightness)
        val rb = scale(rightArgb and 0xFF, brightness)
        return buildString(240) {
            append("echo 1-").append(lr).append(':').append(lg).append(':').append(lb)
                .append(':').append(SYSFS_BRIGHTNESS).append(" > ").append(LEFT_NODE)
            append(" && echo 2-").append(lr).append(':').append(lg).append(':').append(lb)
                .append(':').append(SYSFS_BRIGHTNESS).append(" > ").append(LEFT_NODE)
            append(" && echo 1-").append(rr).append(':').append(rg).append(':').append(rb)
                .append(':').append(SYSFS_BRIGHTNESS).append(" > ").append(RIGHT_NODE)
            append(" && echo 2-").append(rr).append(':').append(rg).append(':').append(rb)
                .append(':').append(SYSFS_BRIGHTNESS).append(" > ").append(RIGHT_NODE)
        }
    }

    private fun scale(channel: Int, brightness: Int): Int = (channel * brightness) / 255

    private fun parseColors(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val left = parseArgb(parts[0]) ?: return null
        val right = parseArgb(parts[1]) ?: return null
        return left to right
    }

    private fun parseArgb(token: String): Int? {
        val t = token.trim()
        if (!t.startsWith('#') || t.length != 9) return null
        return runCatching { t.substring(1).toLong(16).toInt() }.getOrNull()
    }
}
