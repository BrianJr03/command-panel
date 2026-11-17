package jr.brian.commandpannel.ui.components.looks

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.clampContrast
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.cos
import kotlin.math.sin

/*
 * Cockpit renderers:
 *   - `CardStyle.BEZEL`
 *   - `TileStyle.SWITCH_LED`
 *   - `GaugeStyle.NEEDLE_DIAL`
 *   - `SliderStyle.FADER`
 *   - `NavStyle.LED_SWITCH`
 *
 * Instrument cluster: bezelled panels (`cockpitPanel`, `bezel`) with four screw heads, switch
 * tiles with an indicator LED (`ledOff` / `ledOn`), 180° needle dials with a `dangerZone` red
 * zone, and faders. Card readouts are accent mono numerals, like an amber instrument display.
 */

private const val DIAL_TICKS = 9
private const val DIAL_RED_TICKS = 2

/** Needle angle in degrees (180° = left, 360° = right); bidirectional values center at 270°. */
internal fun cockpitNeedleAngle(fraction: Float, bidirectional: Boolean): Float {
    val t = if (bidirectional) (fraction.coerceIn(-1f, 1f) + 1f) / 2f else fraction.coerceIn(0f, 1f)
    return 180f + t * 180f
}

/** Angle of tick [index] of [count] spread evenly over the top semicircle. */
internal fun cockpitTickAngle(index: Int, count: Int = DIAL_TICKS): Float =
    180f + 180f * index / (count - 1).coerceAtLeast(1)

/** Whether tick [index] belongs to the red zone (the last [DIAL_RED_TICKS] ticks). */
internal fun cockpitIsRedTick(index: Int, count: Int = DIAL_TICKS): Boolean = index >= count - DIAL_RED_TICKS

/** Screw head centers inset [inset] from each corner of [size]. */
internal fun cockpitScrewCenters(size: Size, inset: Float): List<Offset> = listOf(
    Offset(inset, inset),
    Offset(size.width - inset, inset),
    Offset(inset, size.height - inset),
    Offset(size.width - inset, size.height - inset)
)

/** Bezelled panel: fill, 2dp bezel stroke, a faint top highlight line and four slotted screws. */
private fun Modifier.cockpitBezel(p: LookPaint, shape: Shape, fill: Color, screws: Boolean): Modifier {
    val lc = p.lookColors
    val highlight = Color.White.copy(alpha = if (p.colors.isDark) 0.08f else 0.55f)
    val slot = lerp(lc.screw, lc.cockpitPanel, 0.55f)
    return drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val bezelStroke = Stroke(aynDp(2f).toPx())
        val bezelInset = aynDp(2f).toPx()
        val screwR = aynDp(1.5f).toPx()
        val centers = cockpitScrewCenters(size, aynDp(6f).toPx())
        onDrawBehind {
            drawPath(path, fill)
            drawLine(highlight, Offset(bezelInset * 2f, bezelInset + 0.5f), Offset(size.width - bezelInset * 2f, bezelInset + 0.5f), 1.dp.toPx())
            drawPath(path, lc.bezel, style = bezelStroke)
            if (screws) {
                centers.forEach { c ->
                    drawCircle(lc.screw, screwR, c)
                    drawLine(slot, Offset(c.x - screwR * 0.7f, c.y + screwR * 0.7f), Offset(c.x + screwR * 0.7f, c.y - screwR * 0.7f), screwR * 0.45f)
                }
            }
        }
    }.clip(shape)
}

/** Indicator LED in the top-right corner; lit LEDs get a small cached glow. */
private fun DrawScope.drawLed(center: Offset, radius: Float, color: Color, glow: Paint?) {
    if (glow != null) drawIntoCanvas { it.nativeCanvas.drawCircle(center.x, center.y, radius, glow) }
    drawCircle(color, radius, center)
}

private fun ledGlowPaint(color: Color, radiusPx: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    setShadowLayer(radiusPx, 0f, 0f, color.copy(alpha = 0.8f).toArgb())
}

internal object CockpitCard : LookCardRenderer {
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
        val panel = fill ?: if (focused) lerp(lc.cockpitPanel, lc.ledOn, 0.06f) else lc.cockpitPanel
        return base.cockpitBezel(p, shape, panel, screws = true)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier =
        base.cockpitBezel(p, p.spec.dialog.dialogShape, lerp(p.colors.dialogBackground, p.lookColors.cockpitPanel, 0.5f), screws = true)

    override fun content(p: LookPaint): Color = p.lookColors.ledOn

    private fun plate(p: LookPaint) = lerp(p.lookColors.cockpitPanel, Color.Black, 0.6f)

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier {
        val shape = p.spec.settingsTab.fpsBadgeShape
        return Modifier.background(plate(p), shape).border(1.dp, p.lookColors.bezel, shape)
    }

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color =
        p.lookColors.ledOn.clampContrast(plate(p), 4.5f)
}

internal object CockpitTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val lc = p.lookColors
        val shape = p.spec.tile.buttonShape
        val top = lerp(lc.cockpitPanel, p.colors.primary, if (p.colors.isDark) 0.10f else 0.04f)
        val bottom = lerp(lc.cockpitPanel, Color.Black, if (p.colors.isDark) 0.18f else 0.08f)
        val led = if (selected) lc.ledOn else lc.ledOff
        return base.drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val brush = Brush.verticalGradient(listOf(top, bottom))
            val stroke = Stroke(1.dp.toPx())
            val ledR = aynDp(2.5f).toPx()
            val ledCenter = Offset(size.width - aynDp(6f).toPx(), aynDp(6f).toPx())
            val glow = if (selected) ledGlowPaint(led, aynDp(4f).toPx()) else null
            onDrawBehind {
                drawPath(path, brush)
                drawPath(path, lc.bezel, style = stroke)
                drawLed(ledCenter, ledR, led, glow)
            }
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.ledOn else p.colors.primary
}

private class DialStrokes(val track: Stroke, val red: Stroke, val needle: Float, val tick: Float, val hub: Float)

internal object CockpitGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val a = p.lookColors.ledOn
        return GaugeRingColors(a, a, a, a)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val lc = p.lookColors
        val density = LocalDensity.current
        val labelStyle = tccTextStyle()
        val displayStyle = tccDisplayTextStyle()
        val strokes = remember(density) {
            with(density) {
                DialStrokes(
                    track = Stroke(aynDp(1.5f).toPx()),
                    red = Stroke(aynDp(4f).toPx()),
                    needle = aynDp(2f).toPx(),
                    tick = aynDp(1.5f).toPx(),
                    hub = aynDp(4f).toPx()
                )
            }
        }
        // The needle springs toward the target (dampingRatio 0.6) so it sweeps and settles.
        val needle = remember { Animatable(reading.target) }
        val animate = p.spec.traits.animate
        LaunchedEffect(reading.target, animate) {
            if (animate) {
                needle.animateTo(reading.target, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
            } else {
                needle.snapTo(reading.target)
            }
        }
        val color = reading.color
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * p.spec.settingsTab.ringDiameterRatio
            val valueSp = with(density) { (side * p.spec.settingsTab.ringValueScale * 0.8f).toSp() }
            val titleSp = with(density) { (side * p.spec.settingsTab.ringTitleScale).toSp() }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(side * 0.02f)) {
                Canvas(modifier = Modifier.width(side).height(side * 0.56f)) {
                    val r = kotlin.math.min(size.width / 2f, size.height) - strokes.red.width
                    val c = Offset(size.width / 2f, size.height - strokes.hub)
                    val arcTopLeft = Offset(c.x - r, c.y - r)
                    val arcSize = Size(r * 2f, r * 2f)
                    drawArc(lc.bezel, 180f, 180f, false, arcTopLeft, arcSize, style = strokes.track)
                    val redStart = cockpitTickAngle(DIAL_TICKS - DIAL_RED_TICKS)
                    drawArc(lc.dangerZone, redStart, 360f - redStart, false, arcTopLeft, arcSize, style = strokes.red)
                    for (i in 0 until DIAL_TICKS) {
                        val rad = Math.toRadians(cockpitTickAngle(i).toDouble())
                        val cx = cos(rad).toFloat()
                        val sy = sin(rad).toFloat()
                        drawLine(
                            if (cockpitIsRedTick(i)) lc.dangerZone else lc.screw,
                            Offset(c.x + cx * r * 0.78f, c.y + sy * r * 0.78f),
                            Offset(c.x + cx * r * 0.96f, c.y + sy * r * 0.96f),
                            strokes.tick
                        )
                    }
                    val rad = Math.toRadians(cockpitNeedleAngle(needle.value, reading.bidirectional).toDouble())
                    drawLine(
                        color,
                        c,
                        Offset(c.x + cos(rad).toFloat() * r * 0.86f, c.y + sin(rad).toFloat() * r * 0.86f),
                        strokes.needle,
                        StrokeCap.Round
                    )
                    drawCircle(lc.screw, strokes.hub, c)
                    drawCircle(color, strokes.hub * 0.45f, c)
                }
                Text(text = "${reading.valueText} ${reading.unitText}", color = color, fontSize = valueSp, style = displayStyle, maxLines = 1)
                Text(text = reading.title, color = p.colors.tertiary, fontSize = titleSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal object CockpitSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier
        .background(lerp(p.look.lookColors.cockpitPanel, Color.Black, if (p.look.colors.isDark) 0.35f else 0.10f))
        .border(1.dp, p.look.lookColors.bezel, p.look.spec.slider.trackShape)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val fill = if (p.dimmed) p.fillColor else lc.ledOn
        val cap = lerp(lc.bezel, p.look.colors.primary, 0.35f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val capW = aynDp(10f).toPx()
            val x = (p.fraction * size.width).coerceIn(capW / 2f, size.width - capW / 2f)
            val tickH = size.height * 0.18f
            val step = size.width / 20f
            var tx = step
            while (tx < size.width - 1f) {
                drawLine(lc.screw, Offset(tx, 0f), Offset(tx, tickH), 1.dp.toPx())
                drawLine(lc.screw, Offset(tx, size.height - tickH), Offset(tx, size.height), 1.dp.toPx())
                tx += step
            }
            val barH = size.height * 0.26f
            if (p.fraction > 0f) drawRect(fill, Offset(0f, (size.height - barH) / 2f), Size(x, barH))
            val inset = 1.dp.toPx()
            drawRect(cap, Offset(x - capW / 2f, inset), Size(capW, size.height - inset * 2f))
            drawRect(lc.bezel, Offset(x - capW / 2f, inset), Size(capW, size.height - inset * 2f), style = Stroke(1.dp.toPx()))
            drawLine(lc.ledOn, Offset(x, size.height * 0.22f), Offset(x, size.height * 0.78f), aynDp(1.2f).toPx())
        }
    }

    override fun iconTint(p: SliderPaint): Color = p.look.colors.primary
}

/** Nav items are switch tiles ([CockpitTile]); the selected item's LED is lit. */
internal object CockpitNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = false

    override val highlightShape: Shape = RoundedCornerShape(aynDp(8f))

    override fun highlight(p: LookPaint): Modifier = Modifier

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.ledOn else p.colors.primary
}
