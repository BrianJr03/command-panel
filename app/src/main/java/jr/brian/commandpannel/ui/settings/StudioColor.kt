package jr.brian.commandpannel.ui.settings

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A color as hue (0–360), saturation and value (0–1), for Theme Studio's fine-tune steppers. */
internal data class Hsv(val h: Float, val s: Float, val v: Float)

/** Opaque ARGB to [Hsv] (alpha ignored). */
internal fun argbToHsv(argb: Int): Hsv {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Hsv(h, if (mx == 0f) 0f else d / mx, mx)
}

/** [Hsv] to opaque ARGB. */
internal fun hsvToArgb(c: Hsv): Int {
    val h = ((c.h % 360f) + 360f) % 360f
    val s = c.s.coerceIn(0f, 1f)
    val v = c.v.coerceIn(0f, 1f)
    val chroma = v * s
    val x = chroma * (1 - abs((h / 60f) % 2f - 1))
    val m = v - chroma
    val (r, g, b) = when {
        h < 60 -> Triple(chroma, x, 0f)
        h < 120 -> Triple(x, chroma, 0f)
        h < 180 -> Triple(0f, chroma, x)
        h < 240 -> Triple(0f, x, chroma)
        h < 300 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** "#RRGGBB" (the # optional, any case) to opaque ARGB, or null when it isn't six hex digits. */
internal fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return (0xFF shl 24) or hex.toInt(16)
}

/** Opaque ARGB as "#RRGGBB". */
internal fun formatHexColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
