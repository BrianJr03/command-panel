package jr.brian.commandpannel.ui.components.looks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.abs
import kotlin.math.floor

/*
 * Industrial renderers:
 *   - `CardStyle.HAZARD_PLATE`
 *   - `TileStyle.PLATE`
 *   - `GaugeStyle.RULER_BAR`
 *   - `SliderStyle.RULER`
 *   - `NavStyle.HAZARD_UNDERLINE`
 *
 * Machinery control panel: `metalFill` plates with a `metalStroke` edge and a band of 135°
 * `hazard` stripes across each card top, stencil (Black Ops One) numerals, mono uppercase labels,
 * and ruler-ticked bar meters. `hazard` is the accent clamped to 3:1 against the background, so it
 * follows the palette accent instead of a hardcoded safety yellow.
 */

private val StripeBand = aynDp(5f)
private val StripeWidth = aynDp(6f)
private val TickSpacing = aynDp(6f)
private const val MAJOR_TICK_EVERY = 5

/** Number of ruler ticks (including both ends) every [spacing] px across [width] px. */
internal fun rulerTickCount(width: Float, spacing: Float): Int =
    if (spacing <= 0f || width <= 0f) 0 else floor(width / spacing).toInt() + 1

/** Every [MAJOR_TICK_EVERY]th ruler tick is drawn taller. */
internal fun rulerIsMajorTick(index: Int): Boolean = index % MAJOR_TICK_EVERY == 0

/**
 * Hazard stripe parallelograms `[x0..x3, y0..y3]` per stripe, as flat `(x, y)` quads leaning at
 * 135° across a band [band] px tall and [width] px wide; stripes are [stripe] px wide with equal
 * gaps, starting one band-width left of the edge so the first stripe is cut, not missing.
 */
internal fun hazardStripeQuads(width: Float, band: Float, stripe: Float): List<FloatArray> {
    if (stripe <= 0f || band <= 0f) return emptyList()
    val quads = ArrayList<FloatArray>()
    var x = -band
    while (x < width) {
        quads += floatArrayOf(x, band, x + band, 0f, x + band + stripe, 0f, x + stripe, band)
        x += stripe * 2f
    }
    return quads
}

/** Hazard stripe band across the top [StripeBand] of the receiver, path built once per size. */
private fun Modifier.hazardBand(hazard: Color, background: Color): Modifier = drawWithCache {
    val band = StripeBand.toPx()
    val path = Path().apply {
        hazardStripeQuads(size.width, band, StripeWidth.toPx()).forEach { q ->
            moveTo(q[0], q[1])
            lineTo(q[2], q[3])
            lineTo(q[4], q[5])
            lineTo(q[6], q[7])
            close()
        }
    }
    onDrawBehind {
        clipRect(bottom = band) {
            drawRect(background, size = Size(size.width, band))
            drawPath(path, hazard)
        }
    }
}

/** Ruler tick row filling the receiver scope: 1dp ticks every [TickSpacing], majors full height. */
private fun DrawScope.drawRulerTicks(color: Color, top: Float, height: Float) {
    val spacing = TickSpacing.toPx()
    val sw = 1.dp.toPx()
    val count = rulerTickCount(size.width - sw, spacing)
    for (i in 0 until count) {
        val x = sw / 2f + i * spacing
        val h = if (rulerIsMajorTick(i)) height else height * 0.55f
        drawLine(color, Offset(x, top), Offset(x, top + h), sw)
    }
}

internal object IndustrialCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val lc = p.lookColors
        val plate = fill ?: if (focused) lerp(lc.metalFill, lc.hazard, 0.08f) else lc.metalFill
        return base.clip(shape)
            .background(plate)
            .hazardBand(lc.hazard, p.colors.primaryBackground)
            .border(1.dp, lc.metalStroke, shape)
            // Content starts below the stripe band.
            .padding(top = StripeBand)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        val lc = p.lookColors
        return base.clip(shape)
            .background(lerp(p.colors.dialogBackground, lc.metalFill, 0.5f))
            .hazardBand(lc.hazard, p.colors.primaryBackground)
            .border(1.dp, lc.metalStroke, shape)
            .padding(top = StripeBand)
    }

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier = Modifier.background(p.lookColors.hazard)

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.colors.primaryBackground
}

internal object IndustrialTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val shape = p.spec.tile.buttonShape
        val lc = p.lookColors
        return if (selected) {
            base.clip(shape).background(lc.hazard)
        } else {
            base.clip(shape).background(lc.metalFill).border(1.dp, lc.metalStroke, shape)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.colors.primaryBackground else p.colors.primary
}

internal object IndustrialGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val h = p.lookColors.hazard
        return GaugeRingColors(h, h, h, h)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val lc = p.lookColors
        val labelStyle = tccTextStyle()
        val displayStyle = tccDisplayTextStyle()
        val error = p.colors.error
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * p.spec.settingsTab.ringDiameterRatio
            val density = LocalDensity.current
            val titleSp = with(density) { (side * p.spec.settingsTab.ringTitleScale).toSp() }
            val valueSp = with(density) { (side * p.spec.settingsTab.ringValueScale).toSp() }
            val unitSp = with(density) { (side * p.spec.settingsTab.ringUnitScale).toSp() }
            Column(
                modifier = Modifier.width(side),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(side * 0.04f)
            ) {
                Text(text = reading.title, color = p.colors.tertiary, fontSize = titleSp, style = labelStyle, maxLines = 1)
                Text(text = reading.valueText, color = p.colors.primary, fontSize = valueSp, style = displayStyle, maxLines = 1)
                Canvas(
                    modifier = Modifier
                        .width(side * 0.92f)
                        .height(side * 0.20f)
                ) {
                    val fraction = reading.fraction()
                    val barH = size.height * 0.55f
                    val sw = 1.dp.toPx()
                    val fillW = (size.width - sw * 2f) * abs(fraction).coerceIn(0f, 1f)
                    val fillX = if (fraction < 0f) size.width - sw - fillW else sw
                    drawRect(if (fraction < 0f) error else reading.color, Offset(fillX, sw), Size(fillW, barH - sw * 2f))
                    drawRect(lc.metalStroke, Offset(sw / 2f, sw / 2f), Size(size.width - sw, barH - sw), style = Stroke(sw))
                    drawRulerTicks(lc.metalStroke, barH + sw, size.height - barH - sw)
                }
                Text(text = reading.unitText, color = p.colors.tertiary, fontSize = unitSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal object IndustrialSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier
        .background(p.look.lookColors.metalFill)
        .border(1.dp, p.look.lookColors.metalStroke, RectangleShape)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val fill = if (p.dimmed) p.fillColor else lc.hazard
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = 1.dp.toPx()
            val fillH = size.height * 0.62f
            if (p.fraction > 0f) drawRect(fill, Offset(sw, sw), Size((size.width - sw * 2f) * p.fraction, fillH - sw))
            drawLine(lc.metalStroke, Offset(0f, fillH), Offset(size.width, fillH), sw)
            drawRulerTicks(lc.metalStroke, fillH, size.height - fillH - sw)
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.onHazard else p.look.colors.primary
}

internal object IndustrialNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RectangleShape

    override fun highlight(p: LookPaint): Modifier {
        val hazard = p.lookColors.hazard
        return Modifier.drawBehind {
            val h = aynDp(3f).toPx()
            val half = size.width * 0.32f
            drawRect(hazard, Offset(size.width / 2f - half, size.height - h), Size(half * 2f, h))
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.colors.primary else p.colors.secondary
}
