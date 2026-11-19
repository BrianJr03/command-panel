package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import jr.brian.commandpannel.ui.components.looks.LookPaint
import jr.brian.commandpannel.ui.components.looks.SliderPaint
import jr.brian.commandpannel.ui.components.looks.renderer
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle

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
    onChange: (fraction: Float, committed: Boolean) -> Unit = { _, _ -> },
) {
    val colors = LocalTccColors.current
    val spec = LocalTccLookSpec.current
    val lookColors = tccLookColors()
    val sliderRenderer = spec.traits.slider.renderer()
    // The gesture handlers below live across recompositions; read the latest value and callback through these.
    val currentFraction by rememberUpdatedState(fraction)
    val currentOnChange by rememberUpdatedState(onChange)
    val displayProgress = (fraction.coerceIn(0f, 1f) * 100f).toInt()
    val stockFill = if (dimmed) colors.tertiary.copy(alpha = 0.38f) else colors.tertiary
    val sliderPaint = SliderPaint(
        look = LookPaint(spec, colors, lookColors),
        fraction = fraction.coerceIn(0f, 1f),
        dimmed = dimmed,
        fillColor = stockFill,
        displayProgress = displayProgress,
        overFill = !dimmed && displayProgress >= 12,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = lookLabel(label),
            color = sliderRenderer.labelColor(sliderPaint),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(44.dp),
            style = tccTextStyle(),
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(spec.slider.trackShape)
                .then(sliderRenderer.track(sliderPaint))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        currentOnChange((offset.x / size.width).coerceIn(0f, 1f), true)
                    }
                }
                .pointerInput(Unit) {
                    var last = currentFraction
                    detectDragGestures(
                        onDragStart = { last = currentFraction },
                        onDrag = { change, _ ->
                            last = (change.position.x / size.width).coerceIn(0f, 1f)
                            currentOnChange(last, false)
                        },
                        onDragEnd = { currentOnChange(last, true) },
                        // A canceled drag still has to commit, or the value is left mid-drag.
                        onDragCancel = { currentOnChange(last, true) },
                    )
                },
        ) {
            sliderRenderer.Fill(sliderPaint, maxWidth)
            if (sliderRenderer.showIcon) {
                androidx.compose.foundation.Image(
                    painter = rememberStockPainter(iconBase),
                    contentDescription = label,
                    colorFilter = stockIconColorFilter(sliderRenderer.iconTint(sliderPaint)),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .height(22.dp)
                        .width(22.dp),
                )
            }
        }
    }
}
