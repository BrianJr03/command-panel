package jr.brian.commandpannel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import jr.brian.commandpannel.ui.components.looks.GaugeReading
import jr.brian.commandpannel.ui.components.looks.renderer
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec

/**
 * Hardware telemetry gauge. The stock look draws the original full-circle ring with a top dot; other
 * looks delegate to their [GaugeStyle] renderer (glow ring, LCD bar, needle dial, ...).
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
    val spec = LocalTccLookSpec.current
    val reading = GaugeReading(
        title = title,
        valueText = value,
        unitText = unit,
        color = color,
        bidirectional = false,
        fraction = { fraction },
        target = fraction,
    )
    spec.traits.gauge.renderer().Gauge(reading, modifier)
}
