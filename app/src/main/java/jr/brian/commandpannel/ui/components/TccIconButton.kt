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
    labelSize: TextUnit = 14.sp,
    iconInset: Dp = circleSize * 0.27f,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalTccColors.current
    val base = if (onClick != null) modifier.clickable { onClick() } else modifier
    Column(
        modifier = base,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(
                    if (active) activeGlow(colors.accent) else androidx.compose.ui.graphics.SolidColor(colors.fifth)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = rememberStockPainter(iconBase),
                contentDescription = label,
                colorFilter = stockIconColorFilter(if (active) Color.White else colors.secondary),
                modifier = Modifier.padding(iconInset).size(circleSize - iconInset * 2),
            )
        }
        Text(
            text = label,
            color = if (active) colors.primary else colors.secondary,
            fontSize = labelSize,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun activeGlow(accent: Color): Brush = Brush.radialGradient(
    0f to accent.copy(alpha = 0.40f),
    0.6f to accent.copy(alpha = 0.18f),
    1f to Color.Transparent,
)
