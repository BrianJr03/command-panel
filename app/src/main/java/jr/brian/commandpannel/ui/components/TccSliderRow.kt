package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import jr.brian.commandpannel.ui.theme.LocalTccColors

/**
 * Stock horizontal slider row (`x0/C3192a`): a two-line label, a rounded bar (track `fifthColor`, fill `tertiary`,
 * r=15dp) with the state icon drawn inside at the left, and a bottom icon at the right. Visual only in phase 3;
 * drag handling is wired in phase 4.
 */
@Composable
fun TccSliderRow(
    label: String,
    fraction: Float,
    iconBase: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val colors = LocalTccColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = colors.primary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(44.dp),
        )
        Box(modifier = Modifier.weight(1f).height(40.dp)) {
            val fillAlpha = if (dimmed) 0.38f else 1f
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val r = CornerRadius(15.dp.toPx())
                drawRoundRect(color = colors.fifth, size = size, cornerRadius = r)
                val w = (size.width * fraction.coerceIn(0f, 1f))
                if (w > 0f) {
                    drawRoundRect(
                        color = colors.tertiary.copy(alpha = fillAlpha),
                        size = Size(w, size.height),
                        cornerRadius = r,
                    )
                }
            }
            androidx.compose.foundation.Image(
                painter = rememberStockPainter(iconBase),
                contentDescription = label,
                colorFilter = stockIconColorFilter(colors.primary),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .height(22.dp)
                    .width(22.dp),
            )
        }
    }
}
