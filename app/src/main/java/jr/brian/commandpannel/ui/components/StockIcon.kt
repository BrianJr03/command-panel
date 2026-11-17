package jr.brian.commandpannel.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import jr.brian.commandpannel.privileged.StockResources
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.TccColors

/**
 * Resolves a stock drawable by base name, applying the stock app's theme-variant rule (`r0/C3104a.m16489e`):
 * in a dark/pure-black palette it prefers the `<base>_black` resource, otherwise the plain `<base>`.
 *
 * The art is **not bundled** — it is loaded at runtime from the installed AYN stock app (see [StockResources]) so
 * Command Panel never redistributes AYN's icons. CP-authored drawables (e.g. `ic_qs_replay_buffer`) still ship in our
 * own resources and are resolved as a fallback; if neither is available, a neutral placeholder is shown (never blank,
 * never AYN art).
 */
@Composable
fun rememberStockPainter(baseName: String): Painter {
    val context = LocalContext.current
    val dark = LocalTccColors.current === TccColors.PureBlack
    val resName = if (dark) "${baseName}_black" else baseName

    // Loaded once per process from the stock app; remember keyed on resName so a dark/light look switch re-resolves.
    val stock = remember(resName) {
        StockResources.drawable(context, resName) ?: StockResources.drawable(context, baseName)
    }
    if (stock != null) return remember(stock) { DrawablePainter(stock) }

    // CP-authored drawables (not present in the stock app) still ship in our own resources.
    val ownId = remember(baseName) { context.resources.getIdentifier(baseName, "drawable", context.packageName) }
    if (ownId != 0) return painterResource(ownId)

    // Neutral placeholder — never blank, never AYN art.
    return rememberVectorPainter(Icons.Rounded.Widgets)
}

@Composable
fun stockIconColorFilter(color: Color): ColorFilter = ColorFilter.tint(color)
