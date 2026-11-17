package jr.brian.commandpannel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.TccColors

/**
 * Resolves a stock drawable by base name, applying the stock app's theme-variant rule (`r0/C3104a.m16489e`):
 * in a dark/pure-black palette it prefers the `<base>_black` resource, otherwise the plain `<base>`.
 */
@Composable
fun rememberStockPainter(baseName: String): Painter {
    val context = LocalContext.current
    val dark = LocalTccColors.current === TccColors.PureBlack
    val resName = if (dark) "${baseName}_black" else baseName
    var id = context.resources.getIdentifier(resName, "drawable", context.packageName)
    if (id == 0) id = context.resources.getIdentifier(baseName, "drawable", context.packageName)
    // Never crash on a missing drawable: fall back to a blank painter.
    if (id == 0) return androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Transparent)
    return painterResource(id)
}

@Composable
fun stockIconColorFilter(color: Color): ColorFilter = ColorFilter.tint(color)
