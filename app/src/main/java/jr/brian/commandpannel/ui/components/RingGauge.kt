package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import jr.brian.commandpannel.ui.theme.LocalTccColors

/**
 * Stock hardware ring (`B0/C0024d`): a full-circle track in `fourthColor`, a progress arc from the top in the ring's
 * colour at ~59% alpha, a white dot at the arc start, and centered title / value / unit text.
 */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTccColors.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2f
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset(
                (size.width - arcSize.width) / 2f,
                (size.height - arcSize.height) / 2f,
            )
            // track
            drawArc(
                color = colors.fourth,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // progress from top (-90°)
            val sweep = fraction.coerceIn(0f, 1f) * 360f
            drawArc(
                color = color.copy(alpha = 150f / 255f),
                startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // white dot at the start (top)
            drawCircle(
                color = Color.White,
                radius = stroke * 0.85f / 2f,
                center = Offset(size.width / 2f, inset),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = colors.tertiary, fontSize = 9.sp)
            Text(value, color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(unit, color = colors.tertiary, fontSize = 9.sp)
        }
    }
}
