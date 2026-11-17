package jr.brian.commandpannel.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The fork's look code sizes its chrome with `aynDp` / `aynSp`, authored against the AYN Thor's
 * bottom-screen density. Command Panel targets the same panel, so these map straight to dp / sp.
 */
fun aynDp(value: Float): Dp = value.dp
fun aynSp(value: Float): TextUnit = value.sp

internal val DEFAULT_CONTRAST_DARK_TEXT = Color(0xFF141414)

/** Whichever of [dark] / [light] contrasts better against this color (used for on-accent content). */
fun Color.contrastingContentColor(
    dark: Color = DEFAULT_CONTRAST_DARK_TEXT,
    light: Color = Color.White,
): Color {
    fun ratio(a: Color, b: Color): Float {
        val l1 = a.luminance() + 0.05f
        val l2 = b.luminance() + 0.05f
        return maxOf(l1, l2) / minOf(l1, l2)
    }
    return if (ratio(dark, this) >= ratio(light, this)) dark else light
}
