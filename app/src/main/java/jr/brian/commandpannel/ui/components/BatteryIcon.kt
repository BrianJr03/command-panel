package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import jr.brian.commandpannel.ui.theme.LocalTccColors

/**
 * Stock status-bar battery (`BatteryView`): a horizontal body with a cap and a fill whose colour lerps toward red
 * below 50%, plus a charging bolt. Drawn horizontally (the stock view rotates a vertical drawing).
 */
@Composable
fun BatteryIcon(
    percent: Int,
    charging: Boolean,
    lightning: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTccColors.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = h * 0.08f
        val capW = w * 0.06f
        val bodyW = w - capW
        // body outline
        drawRoundRect(
            color = colors.primary,
            topLeft = Offset(0f, 0f),
            size = Size(bodyW, h),
            cornerRadius = CornerRadius(stroke * 1.5f),
            style = Stroke(width = stroke),
        )
        // cap
        drawRoundRect(
            color = colors.primary,
            topLeft = Offset(bodyW, h * 0.3f),
            size = Size(capW, h * 0.4f),
            cornerRadius = CornerRadius(stroke),
        )
        // fill
        val inset = stroke * 1.6f
        val level = (percent.coerceIn(0, 100)) / 100f
        val fillColor = if (percent < 50) {
            lerp(Color(0xFFF44336), colors.primary, percent / 50f)
        } else colors.primary
        val fillW = (bodyW - inset * 2) * level
        if (fillW > 0f) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(inset, inset),
                size = Size(fillW, h - inset * 2),
                cornerRadius = CornerRadius(stroke),
            )
        }
        if (charging) {
            val cx = bodyW / 2f
            val cy = h / 2f
            val s = h * 0.32f
            val bolt = Path().apply {
                moveTo(cx + s * 0.1f, cy - s)
                lineTo(cx - s * 0.5f, cy + s * 0.15f)
                lineTo(cx, cy + s * 0.15f)
                lineTo(cx - s * 0.1f, cy + s)
                lineTo(cx + s * 0.5f, cy - s * 0.15f)
                lineTo(cx, cy - s * 0.15f)
                close()
            }
            drawPath(bolt, color = lightning)
        }
    }
}
