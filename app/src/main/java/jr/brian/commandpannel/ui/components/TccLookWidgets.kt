package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import jr.brian.commandpannel.ui.components.looks.GaugeReading
import jr.brian.commandpannel.ui.components.looks.GaugeRingColors
import jr.brian.commandpannel.ui.components.looks.LookGaugeRenderer
import jr.brian.commandpannel.ui.components.looks.LookNavRenderer
import jr.brian.commandpannel.ui.components.looks.LookPaint
import jr.brian.commandpannel.ui.components.looks.LookSliderRenderer
import jr.brian.commandpannel.ui.components.looks.SliderPaint
import jr.brian.commandpannel.ui.components.looks.renderer
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.GaugeStyle
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.NavStyle
import jr.brian.commandpannel.ui.theme.SliderStyle
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle
import jr.brian.commandpannel.ui.theme.withHueShift
import androidx.compose.ui.unit.min
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which overlay surface a [TccIconButton] is painting (picks the renderer family). */
enum class TccTileVariant { QUICK_SETTING, MODE, NAV_BAR }

/** Stock tile circle background: a purple radial glow when selected, otherwise the flat fifth fill. */
fun Modifier.tccTileBackground(colors: jr.brian.commandpannel.ui.theme.TccColorPalette, isRetroid: Boolean, selected: Boolean): Modifier =
    this.background(
        if (selected) androidx.compose.ui.graphics.Brush.radialGradient(
            0f to colors.accent.copy(alpha = 0.40f),
            0.6f to colors.accent.copy(alpha = 0.18f),
            1f to Color.Transparent,
        ) else androidx.compose.ui.graphics.SolidColor(colors.fifth),
    )

fun tccTileIconTint(colors: jr.brian.commandpannel.ui.theme.TccColorPalette, isRetroid: Boolean, selected: Boolean): Color =
    if (selected) Color.White else colors.secondary

// ---------------------------------------------------------------------------------------------
// Nav bar renderers (stock + original looks)
// ---------------------------------------------------------------------------------------------

internal class BuiltInNavRenderer private constructor(private val style: NavStyle) : LookNavRenderer {
    override val columnHighlight: Boolean get() = style != NavStyle.TILE

    override val highlightShape: Shape = when (style) {
        NavStyle.GLASS_PILL -> RoundedCornerShape(aynDp(18f))
        else -> RectangleShape
    }

    override fun highlight(p: LookPaint): Modifier {
        val colors = p.colors
        val lookColors = p.lookColors
        return when (style) {
            NavStyle.GLASS_PILL -> Modifier
                .drawBehindGlow(highlightShape, colors.accent.copy(alpha = 0.28f), 12.dp)
                .clip(highlightShape)
                .background(lookColors.glassFillFocused)
                .border(1.dp, lookColors.glassHighlight, highlightShape)
            NavStyle.INK_BLOCK -> Modifier.background(colors.accent)
            NavStyle.BRACKETS -> Modifier.drawWithContentBrackets(lookColors.lineAccent)
            else -> Modifier
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color = when (style) {
        NavStyle.INK_BLOCK -> if (selected) p.lookColors.onAccent else p.lookColors.navContent
        NavStyle.BRACKETS -> p.lookColors.lineAccent.copy(alpha = if (selected) 1f else 0.6f)
        else -> if (selected) p.colors.primary else p.colors.secondary
    }

    override fun label(text: String, selected: Boolean): String =
        if (selected && style == NavStyle.BRACKETS) "> $text" else text

    override fun barFill(p: LookPaint): Color? =
        if (style == NavStyle.INK_BLOCK) p.lookColors.navBackground else null

    companion object {
        val Tile = BuiltInNavRenderer(NavStyle.TILE)
        val GlassPill = BuiltInNavRenderer(NavStyle.GLASS_PILL)
        val InkBlock = BuiltInNavRenderer(NavStyle.INK_BLOCK)
        val Brackets = BuiltInNavRenderer(NavStyle.BRACKETS)
    }
}

private fun Modifier.drawBehindGlow(shape: Shape, color: Color, radius: androidx.compose.ui.unit.Dp): Modifier =
    this.drawBehind { drawShapeGlow(shape, color, radius) }

private fun Modifier.drawWithContentBrackets(c: Color): Modifier = this.drawWithContent {
    drawContent()
    val sw = aynDp(1.6f).toPx()
    val arm = aynDp(6f).toPx()
    for (x in listOf(sw / 2f, size.width - sw / 2f)) {
        val dir = if (x < size.width / 2f) 1f else -1f
        drawLine(c, Offset(x, 0f), Offset(x, size.height), sw)
        drawLine(c, Offset(x, sw / 2f), Offset(x + dir * arm, sw / 2f), sw)
        drawLine(c, Offset(x, size.height - sw / 2f), Offset(x + dir * arm, size.height - sw / 2f), sw)
    }
}

// ---------------------------------------------------------------------------------------------
// Slider renderers (stock + original looks)
// ---------------------------------------------------------------------------------------------

internal class BuiltInSliderRenderer private constructor(private val style: SliderStyle) : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier {
        val colors = p.look.colors
        val lookColors = p.look.lookColors
        val spec = p.look.spec
        val trackShape = spec.slider.trackShape
        return when (style) {
            SliderStyle.GLOW_GRADIENT -> Modifier
                .background(lookColors.glassFill)
                .border(1.dp, lookColors.glassRim, trackShape)
            SliderStyle.BRUTALIST -> Modifier
                .background(colors.panelBackground)
                .border(spec.traits.outlineWidth, lookColors.outline, trackShape)
            SliderStyle.SEGMENTED -> Modifier
                .background(colors.primaryBackground)
                .border(1.dp, lookColors.lineAccent.copy(alpha = 0.45f), trackShape)
            else -> Modifier.background(colors.fifth)
        }
    }

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: androidx.compose.ui.unit.Dp) {
        val colors = p.look.colors
        val lookColors = p.look.lookColors
        val spec = p.look.spec
        val fraction = p.fraction
        val fillColor = p.fillColor
        when (style) {
            SliderStyle.GLOW_GRADIENT -> if (fraction > 0f) {
                Box(
                    Modifier.width(trackWidth * fraction).fillMaxHeight().clip(spec.slider.trackShape).background(
                        if (p.dimmed) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(fillColor, fillColor))
                        else androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(colors.accent.copy(alpha = 0.55f), colors.accent)
                        )
                    )
                )
            }
            SliderStyle.BRUTALIST -> if (fraction > 0f) {
                Box(
                    Modifier.width(trackWidth * fraction).fillMaxHeight()
                        .background(if (p.dimmed) colors.fourth else colors.accent)
                        .border(spec.traits.outlineWidth, lookColors.outline, RectangleShape)
                )
            }
            SliderStyle.SEGMENTED -> Canvas(Modifier.fillMaxSize()) {
                val segments = 20
                val gap = aynDp(1.5f).toPx()
                val inset = aynDp(2f).toPx()
                val segW = (size.width - inset * 2f - gap * (segments - 1)) / segments
                val lit = (fraction * segments).roundToInt()
                val on = if (p.dimmed) lookColors.lineAccent.copy(alpha = 0.35f) else lookColors.lineAccent
                for (i in 0 until segments) {
                    drawRect(
                        color = if (i < lit) on else lookColors.lineAccent.copy(alpha = 0.10f),
                        topLeft = Offset(inset + i * (segW + gap), inset),
                        size = Size(segW, size.height - inset * 2f),
                    )
                }
            }
            else -> Box(Modifier.width(trackWidth * fraction).fillMaxHeight().background(fillColor))
        }
    }

    override fun iconTint(p: SliderPaint): Color {
        val colors = p.look.colors
        val lookColors = p.look.lookColors
        return when (style) {
            SliderStyle.GLOW_GRADIENT, SliderStyle.BRUTALIST -> if (p.overFill) lookColors.onAccent else colors.primary
            SliderStyle.SEGMENTED -> if (p.overFill) colors.primaryBackground else lookColors.lineAccent
            else -> colors.primary
        }
    }

    override fun labelColor(p: SliderPaint): Color =
        if (style == SliderStyle.SEGMENTED) p.look.lookColors.lineAccent else p.look.colors.primary

    companion object {
        val Stock = BuiltInSliderRenderer(SliderStyle.STOCK)
        val Pixel = BuiltInSliderRenderer(SliderStyle.PIXEL)
        val GlowGradient = BuiltInSliderRenderer(SliderStyle.GLOW_GRADIENT)
        val Brutalist = BuiltInSliderRenderer(SliderStyle.BRUTALIST)
        val Segmented = BuiltInSliderRenderer(SliderStyle.SEGMENTED)
    }
}

// ---------------------------------------------------------------------------------------------
// Ring gauge layout + stock gauge renderers
// ---------------------------------------------------------------------------------------------

/**
 * Shared circular gauge layout: a square canvas with centered title / value / unit text. [drawRing]
 * paints the ring for the current animated fraction; [textColor] overrides the palette text colors.
 */
@Composable
fun RingGaugeLayout(
    reading: GaugeReading,
    modifier: Modifier,
    textColor: Color? = null,
    valueText: (@Composable (text: String, color: Color, fontSize: TextUnit, style: TextStyle) -> Unit)? = null,
    drawRing: DrawScope.(fraction: Float) -> Unit,
) {
    val colors = LocalTccColors.current
    val spec = LocalTccLookSpec.current
    val settings = spec.settingsTab
    val labelStyle = tccTextStyle()
    val displayStyle = tccDisplayTextStyle()
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val sizeDp = minOf(maxWidth, maxHeight) * settings.ringDiameterRatio
        val titleSp = with(density) { (sizeDp * settings.ringTitleScale).toSp() }
        val valueSp = with(density) { (sizeDp * settings.ringValueScale).toSp() }
        val unitSp = with(density) { (sizeDp * settings.ringUnitScale).toSp() }
        Box(Modifier.size(sizeDp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val minFraction = if (reading.bidirectional) -1f else 0f
                drawRing(reading.fraction().coerceIn(minFraction, 1f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(lookLabel(reading.title), color = textColor?.copy(alpha = 0.7f) ?: colors.tertiary, fontSize = titleSp, style = labelStyle, maxLines = 1)
                if (valueText != null) {
                    valueText(reading.valueText, textColor ?: colors.primary, valueSp, displayStyle)
                } else {
                    Text(reading.valueText, color = textColor ?: colors.primary, fontSize = valueSp, style = displayStyle, fontWeight = FontWeight.Normal, maxLines = 1)
                }
                Text(lookLabel(reading.unitText), color = textColor?.copy(alpha = 0.7f) ?: colors.tertiary, fontSize = unitSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal class BuiltInGaugeRenderer private constructor(private val style: GaugeStyle) : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val colors = p.colors
        val lookColors = p.lookColors
        return when (style) {
            GaugeStyle.GLOW_RING -> GaugeRingColors(
                cpu = colors.accent, gpu = colors.accent.withHueShift(40f),
                pwr = colors.accent.withHueShift(-40f), ram = colors.accent.withHueShift(80f),
            )
            GaugeStyle.SEGMENT_METER -> GaugeRingColors(colors.accent, lookColors.accent2, lookColors.outline, colors.accent)
            GaugeStyle.SEGMENTED_ARC -> GaugeRingColors(lookColors.lineAccent, lookColors.lineAccent, lookColors.lineAccent, lookColors.lineAccent)
            else -> GaugeRingColors(Color(0xFFEE188C), Color(0xFF70E7FF), Color(0xFFE26E01), Color(0xFF76CA40))
        }
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val colors = LocalTccColors.current
        when (style) {
            GaugeStyle.SEGMENT_METER -> HardwareSegmentMeter(reading.title, reading.valueText, reading.unitText, reading.fraction(), reading.color, modifier)
            GaugeStyle.GLOW_RING -> RingGaugeLayout(reading, modifier) { f -> drawGlowRing(f, reading.color, colors.primary.copy(alpha = 0.12f)) }
            GaugeStyle.SEGMENTED_ARC -> RingGaugeLayout(reading, modifier, textColor = reading.color) { f -> drawSegmentedArc(f, reading.color) }
            else -> RingGaugeLayout(reading, modifier) { f -> drawStockRing(f, reading.color, colors) }
        }
    }

    companion object {
        val Ring = BuiltInGaugeRenderer(GaugeStyle.RING)
        val GlowRing = BuiltInGaugeRenderer(GaugeStyle.GLOW_RING)
        val SegmentMeter = BuiltInGaugeRenderer(GaugeStyle.SEGMENT_METER)
        val SegmentedArc = BuiltInGaugeRenderer(GaugeStyle.SEGMENTED_ARC)
    }
}

private fun DrawScope.drawStockRing(fraction: Float, ringColor: Color, colors: jr.brian.commandpannel.ui.theme.TccColorPalette) {
    val strokeWidth = size.minDimension * 0.055f
    val halfStroke = strokeWidth * 0.5f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(halfStroke, halfStroke)
    drawArc(colors.fourth, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    val sweep = fraction * 360f
    if (sweep != 0f) {
        drawArc(ringColor.copy(alpha = 150f / 255f), -90f, sweep, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    }
    val dotColor = if (colors.isDark) Color.White else colors.primary
    drawCircle(dotColor, (strokeWidth * 0.85f) * 0.5f, Offset(size.width * 0.5f, halfStroke))
}

private fun DrawScope.drawGlowRing(fraction: Float, color: Color, trackColor: Color) {
    val strokeWidth = size.minDimension * 0.06f
    val halfStroke = strokeWidth * 0.5f
    val haloWidth = strokeWidth * 2.4f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(halfStroke, halfStroke)
    drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
    val sweep = fraction * 360f
    if (sweep == 0f) return
    drawArc(color.copy(alpha = 0.22f), -90f, sweep, false, topLeft, arcSize, style = Stroke(haloWidth, cap = StrokeCap.Round))
    drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
}

private fun DrawScope.drawSegmentedArc(fraction: Float, color: Color) {
    val segments = 24
    val strokeWidth = size.minDimension * 0.075f
    val halfStroke = strokeWidth * 0.5f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(halfStroke, halfStroke)
    val step = 360f / segments
    val lit = (abs(fraction) * segments).roundToInt()
    val reverse = fraction < 0f
    for (i in 0 until segments) {
        val on = i < lit
        val start = if (reverse) -90f - (i + 1) * step + 2f else -90f + i * step + 2f
        drawArc(if (on) color else color.copy(alpha = 0.16f), start, step - 4f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
    }
}

@Composable
private fun HardwareSegmentMeter(title: String, valueText: String, unitText: String, fraction: Float, meterColor: Color, modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    val lookSpec = LocalTccLookSpec.current
    val lookColors = tccLookColors()
    val labelStyle = tccTextStyle()
    val displayStyle = tccDisplayTextStyle()
    val outline = lookSpec.traits.outlineWidth
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val h = maxHeight * 0.86f
        val meterW = min(maxWidth * 0.26f, h * 0.30f)
        val titleSp = with(density) { (h * 0.15f).toSp() }
        val valueSp = with(density) { (h * 0.24f).toSp() }
        val unitSp = with(density) { (h * 0.13f).toSp() }
        Row(Modifier.height(h), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(maxWidth * 0.06f)) {
            Canvas(Modifier.width(meterW).fillMaxHeight().border(outline, lookColors.outline, RectangleShape)) {
                val blocks = 6
                val pad = outline.toPx() + aynDp(1.5f).toPx()
                val gap = aynDp(1.5f).toPx()
                val blockH = (size.height - pad * 2f - gap * (blocks - 1)) / blocks
                val lit = (abs(fraction) * blocks).roundToInt()
                val litColor = if (fraction < 0f) colors.error else meterColor
                for (i in 0 until lit.coerceAtMost(blocks)) {
                    val top = size.height - pad - (i + 1) * blockH - i * gap
                    drawRect(litColor, Offset(pad, top), Size(size.width - pad * 2f, blockH))
                }
            }
            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight()) {
                Text(lookLabel(title), color = colors.primary, fontSize = titleSp, style = labelStyle, maxLines = 1)
                Text(valueText, color = colors.primary, fontSize = valueSp, style = displayStyle, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(lookLabel(unitText), color = colors.secondary, fontSize = unitSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}
