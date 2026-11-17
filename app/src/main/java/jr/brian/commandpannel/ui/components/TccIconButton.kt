package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import jr.brian.commandpannel.ui.components.looks.renderer
import jr.brian.commandpannel.ui.theme.LocalTccColors

/**
 * The stock icon-circle button (`TCCButtonView`): a circular icon with a two-line caption, used by the nav bar,
 * the quick-setting tiles and the screen-mode buttons.
 *
 *  - circle background: `fifthColor` normally, a purple radial glow when active (stock `bg_qs_item_selected`)
 *  - icon tint: `secondary` → white when active
 *  - caption: `secondary` → `primary` when active
 */
@Composable
fun TccIconButton(
    iconBase: String,
    label: String,
    circleSize: Dp,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    labelSize: TextUnit = 10.sp,
    iconInset: Dp = circleSize * 0.27f,
    variant: TccTileVariant = TccTileVariant.QUICK_SETTING,
    index: Int = 0,
    onClick: (() -> Unit)? = null,
) = TccIconButton(TileIcon.Stock(iconBase), label, circleSize, modifier, active, labelSize, iconInset, variant, index, onClick)

@Composable
fun TccIconButton(
    icon: TileIcon,
    label: String,
    circleSize: Dp,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    labelSize: TextUnit = 10.sp,
    iconInset: Dp = circleSize * 0.27f,
    variant: TccTileVariant = TccTileVariant.QUICK_SETTING,
    index: Int = 0,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalTccColors.current
    val spec = jr.brian.commandpannel.ui.theme.LocalTccLookSpec.current
    val lookColors = jr.brian.commandpannel.ui.theme.tccLookColors()
    val paint = jr.brian.commandpannel.ui.components.looks.LookPaint(spec, colors, lookColors)

    val isNav = variant == TccTileVariant.NAV_BAR
    val navRenderer = spec.traits.nav.renderer()
    // Column-highlight nav styles draw the selection around the whole item and show a bare icon.
    val columnHighlight = isNav && navRenderer.columnHighlight

    val base = if (onClick != null) modifier.clickable { onClick() } else modifier

    val iconTint = if (columnHighlight) navRenderer.iconTint(paint, active)
    else lookTileIconTint(spec, colors, lookColors, active, index)

    val labelText = jr.brian.commandpannel.ui.theme.lookLabel(label).let {
        if (isNav) navRenderer.label(it, active) else it
    }
    val labelColor = when {
        columnHighlight -> navRenderer.textColor(paint, active)
        isNav -> if (active) colors.primary else colors.secondary
        else -> spec.traits.tile.renderer().labelColor(paint, active) ?: (if (active) colors.primary else colors.secondary)
    }
    val showIcon = !isNav || navRenderer.showIcon

    Column(
        modifier = base,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (columnHighlight) 5.dp else 3.dp),
    ) {
        if (showIcon) {
            // Column-highlight nav (glass pill, etc.) boxes just the icon — with padding so the selection has room —
            // and lets the label sit below it, instead of cramming the whole icon+label column into the box.
            val iconContainer = when {
                columnHighlight && active ->
                    Modifier.then(navRenderer.highlight(paint)).padding(horizontal = 14.dp, vertical = 9.dp)
                columnHighlight -> Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                else -> Modifier.size(circleSize).tccTile(spec, colors, lookColors, selected = active, index = index)
            }
            val imageModifier = if (columnHighlight) Modifier.size(circleSize - iconInset * 2)
            else Modifier.padding(iconInset).size(circleSize - iconInset * 2)
            Box(modifier = iconContainer, contentAlignment = Alignment.Center) {
                Image(
                    painter = icon.painter(),
                    contentDescription = label,
                    colorFilter = stockIconColorFilter(iconTint),
                    modifier = imageModifier,
                )
            }
        }
        Text(
            text = labelText,
            color = labelColor,
            fontSize = labelSize,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = jr.brian.commandpannel.ui.theme.tccTextStyle(),
        )
    }
}
