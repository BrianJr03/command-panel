package jr.brian.commandpannel.ui.components.looks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.components.RingGaugeLayout
import jr.brian.commandpannel.ui.components.drawShapeGlow
import jr.brian.commandpannel.ui.theme.CustomRenderParams
import jr.brian.commandpannel.ui.theme.FillKind
import jr.brian.commandpannel.ui.theme.GaugeColorMode
import jr.brian.commandpannel.ui.theme.GaugeKind
import jr.brian.commandpannel.ui.theme.LineRole
import jr.brian.commandpannel.ui.theme.NavIndicator
import jr.brian.commandpannel.ui.theme.RingColors
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.withHueShift
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Theme Studio's parametric renderers:
 *   - `CardStyle.CUSTOM`, `TileStyle.CUSTOM`, `GaugeStyle.CUSTOM`, `SliderStyle.CUSTOM`, `NavStyle.CUSTOM`
 *
 * Instead of one fixed design, each reads its block of [CustomRenderParams] from the spec (fill kind, opacity, border,
 * shadow, glow, gauge shape, nav indicator). Fills and lines are palette roles, so a custom look still follows the
 * accent and the contrast-clamped look colors on light, dark and pure-black palettes.
 */

private fun LookPaint.params(): CustomRenderParams = spec.custom ?: CustomRenderParams()

/** The brush for a fill role; [emphasized] is the focused / selected variant. `null` for [FillKind.NONE]. */
private fun LookPaint.fillBrush(kind: FillKind, emphasized: Boolean): Brush? = when (kind) {
    FillKind.SURFACE -> SolidColor(if (emphasized) colors.panelBackgroundSelected else colors.panelBackground)
    FillKind.TONAL -> SolidColor(if (emphasized) lookColors.bloomContainerHigh else lookColors.bloomContainer)
    FillKind.ACCENT -> SolidColor(colors.accent)
    FillKind.GLASS -> SolidColor(if (emphasized) lookColors.glassFillFocused else lookColors.glassFill)
    FillKind.GRADIENT -> Brush.verticalGradient(listOf(lerp(colors.accent, Color.White, 0.12f), lookColors.accent2))
    FillKind.NONE -> null
}

/** Content color on a fill role, when it needs one (accent-colored fills). */
private fun LookPaint.onFill(kind: FillKind): Color? = when (kind) {
    FillKind.ACCENT, FillKind.GRADIENT -> lookColors.onAccent
    else -> null
}

private fun LookPaint.line(role: LineRole): Color = when (role) {
    LineRole.SUBTLE -> lerp(colors.panelBackground, colors.primary, 0.28f)
    LineRole.TEXT -> colors.primary
    LineRole.ACCENT -> lookColors.lineAccent
    // The second accent clamped against the background (it's the Neon Arcade second tube color).
    LineRole.ACCENT2 -> lookColors.neonTube2
}

/** Hard drop shadow following [shape], offset by [offset] down-right into space reserved with padding. */
private fun Modifier.hardShadow(shape: Shape, color: Color, offset: Dp): Modifier =
    if (offset <= 0.dp) this else padding(end = offset, bottom = offset).drawBehind {
        val o = offset.toPx()
        translate(o, o) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
    }

private fun Modifier.fill(brush: Brush?, alpha: Float = 1f): Modifier = if (brush == null) this else background(brush, alpha = alpha)

private fun Modifier.outline(width: Float, color: Color, shape: Shape): Modifier =
    if (width <= 0f) this else border(Dp(width), color, shape)

internal object CustomCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val c = p.params().card
        return base
            .hardShadow(shape, p.lookColors.shadow, Dp(c.shadow))
            .then(if (c.glow) Modifier.drawBehind { drawShapeGlow(shape, p.colors.accent.copy(alpha = 0.35f), 10.dp) } else Modifier)
            .clip(shape)
            .fill(fill?.let(::SolidColor) ?: p.fillBrush(c.fill, focused), c.fillOpacity)
            .outline(c.borderWidth, p.line(c.border), shape)
    }

    // Dialogs stay opaque so the content behind never bleeds through, whatever the card fill.
    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val c = p.params().card
        val shape = p.spec.dialog.dialogShape
        return base.clip(shape).background(p.colors.dialogBackground).outline(c.borderWidth, p.line(c.border), shape)
    }

    override fun content(p: LookPaint): Color? {
        val c = p.params().card
        return if (c.fillOpacity >= 0.6f) p.onFill(c.fill) else null
    }
}

internal object CustomTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val t = p.params().tile
        val shape = p.spec.tile.buttonShape
        return base
            .then(if (selected && t.glow) Modifier.drawBehind { drawShapeGlow(shape, p.colors.accent.copy(alpha = 0.5f), 9.dp) } else Modifier)
            .clip(shape)
            .fill(p.fillBrush(if (selected) t.selectedFill else t.fill, selected))
            .outline(t.borderWidth, p.line(if (selected) LineRole.ACCENT else t.border), shape)
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color {
        val t = p.params().tile
        if (!selected) return p.onFill(t.fill) ?: p.colors.primary
        // A selected tile without an accent fill shows its state through an accent icon instead.
        return p.onFill(t.selectedFill) ?: p.lookColors.lineAccent
    }
}

internal object CustomGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors = when (p.params().gauge.colors) {
        GaugeColorMode.CLASSIC -> GaugeRingColors(RingColors.Cpu, RingColors.Gpu, RingColors.Pwr, RingColors.Ram)
        GaugeColorMode.ACCENT -> p.lookColors.lineAccent.let { GaugeRingColors(it, it, it, it) }
        GaugeColorMode.SPECTRUM -> p.colors.accent.let {
            GaugeRingColors(it, it.withHueShift(40f), it.withHueShift(-40f), it.withHueShift(80f))
        }
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val g = p.params().gauge
        val track = p.colors.primary.copy(alpha = 0.12f)
        // The telemetry card hands every look the classic CPU/GPU/PWR/RAM colors, so map back to the gauge
        // and apply the chosen color mode here.
        val color = ringColors(p).let { c ->
            when (reading.color) {
                RingColors.Cpu -> c.cpu
                RingColors.Gpu -> c.gpu
                RingColors.Pwr -> c.pwr
                RingColors.Ram -> c.ram
                else -> reading.color
            }
        }
        RingGaugeLayout(reading, modifier) { f ->
            val stroke = size.minDimension * g.thickness
            val cap = if (g.roundCaps) StrokeCap.Round else StrokeCap.Butt
            when (g.kind) {
                GaugeKind.RING -> drawSweep(-90f, 360f, -90f, f * 360f, stroke, cap, color, track, g.glow)
                GaugeKind.ARC -> {
                    val (start, sweep) = if (reading.bidirectional) 270f to f * 135f else 135f to f * 270f
                    drawSweep(135f, 270f, start, sweep, stroke, cap, color, track, g.glow)
                }
                GaugeKind.SEGMENTS -> drawSegments(f, stroke, color, track)
                GaugeKind.BAR -> drawBar(f, reading.bidirectional, stroke, cap, color, track, g.glow)
            }
        }
    }
}

/** A track arc ([trackStart], [trackSweep]) with a value arc ([start], [sweep]) on top, inset by half the stroke. */
private fun DrawScope.drawSweep(
    trackStart: Float, trackSweep: Float, start: Float, sweep: Float,
    stroke: Float, cap: StrokeCap, color: Color, track: Color, glow: Boolean,
) {
    val half = stroke / 2f
    val topLeft = Offset(half, half)
    val arcSize = Size(size.width - stroke, size.height - stroke)
    drawArc(track, trackStart, trackSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
    if (sweep == 0f) return
    if (glow) drawArc(color.copy(alpha = 0.22f), start, sweep, false, topLeft, arcSize, style = Stroke(stroke * 2.4f, cap = cap))
    drawArc(color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = cap))
}

private fun DrawScope.drawSegments(fraction: Float, stroke: Float, color: Color, track: Color) {
    val segments = 24
    val step = 360f / segments
    val half = stroke / 2f
    val topLeft = Offset(half, half)
    val arcSize = Size(size.width - stroke, size.height - stroke)
    val lit = (abs(fraction) * segments).roundToInt()
    for (i in 0 until segments) {
        // Negative (bidirectional) readings light counter-clockwise from 12 o'clock.
        val index = if (fraction < 0f) segments - 1 - i else i
        drawArc(
            if (i < lit) color else track, -90f + index * step + 2f, step - 4f, false, topLeft, arcSize,
            style = Stroke(stroke),
        )
    }
}

/** A horizontal bar along the bottom of the gauge box; bidirectional readings grow from the middle. */
private fun DrawScope.drawBar(fraction: Float, bidirectional: Boolean, stroke: Float, cap: StrokeCap, color: Color, track: Color, glow: Boolean) {
    val y = size.height - stroke / 2f - size.height * 0.04f
    val left = stroke / 2f
    val right = size.width - stroke / 2f
    drawLine(track, Offset(left, y), Offset(right, y), stroke, cap)
    val (from, to) = if (bidirectional) {
        val mid = size.width / 2f
        mid to mid + fraction.coerceIn(-1f, 1f) * (right - mid)
    } else {
        left to left + fraction.coerceIn(0f, 1f) * (right - left)
    }
    if (from == to) return
    if (glow) drawLine(color.copy(alpha = 0.22f), Offset(from, y), Offset(to, y), stroke * 2.4f, cap)
    drawLine(color, Offset(from, y), Offset(to, y), stroke, cap)
}

internal object CustomSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier {
        val s = p.look.params().slider
        return Modifier.fill(p.look.fillBrush(s.track, false))
            .outline(s.borderWidth, p.look.line(s.border), p.look.spec.slider.trackShape)
    }

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val s = p.look.params().slider
        val brush = if (p.dimmed) SolidColor(p.fillColor) else p.look.fillBrush(s.fill, true)
        Box(Modifier.width(trackWidth * p.fraction).fillMaxHeight().fill(brush))
        if (s.thumb) {
            val thumb = p.look.colors.primary
            Canvas(Modifier.fillMaxSize()) {
                val w = aynDp(4f).toPx()
                val x = (p.fraction * size.width).coerceIn(w, size.width - w)
                drawRoundRect(
                    thumb, topLeft = Offset(x - w / 2f, size.height * 0.14f), size = Size(w, size.height * 0.72f),
                    cornerRadius = CornerRadius(w / 2f, w / 2f),
                )
            }
        }
    }

    override fun iconTint(p: SliderPaint): Color {
        val s = p.look.params().slider
        return (if (p.overFill) p.look.onFill(s.fill) else p.look.onFill(s.track)) ?: p.look.colors.primary
    }
}

internal object CustomNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(14f))

    override fun highlight(p: LookPaint): Modifier = when (p.params().nav.indicator) {
        NavIndicator.PILL -> {
            val pill = lerp(p.colors.panelBackground, p.colors.accent, 0.35f)
            // Centered on the column like the other pill navs (see GUIDELINE.md on highlight centering).
            Modifier.drawBehind {
                val w = aynDp(56f).toPx()
                val h = aynDp(32f).toPx()
                drawRoundRect(pill, Offset((size.width - w) / 2f, size.height / 2f - h / 2f), Size(w, h), CornerRadius(h / 2f, h / 2f))
            }
        }
        NavIndicator.UNDERLINE -> Modifier.drawBehind {
            val w = size.width * 0.5f
            val y = size.height - aynDp(2f).toPx()
            drawLine(p.lookColors.lineAccent, Offset((size.width - w) / 2f, y), Offset((size.width + w) / 2f, y), aynDp(3f).toPx(), StrokeCap.Round)
        }
        NavIndicator.BLOCK -> Modifier.clip(highlightShape).background(p.colors.accent)
        NavIndicator.DOT -> Modifier.drawBehind {
            drawCircle(p.lookColors.lineAccent, aynDp(3f).toPx(), Offset(size.width / 2f, size.height - aynDp(3f).toPx()))
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color = when {
        !selected -> p.colors.secondary
        p.params().nav.indicator == NavIndicator.BLOCK -> p.lookColors.onAccent
        p.params().nav.indicator == NavIndicator.PILL -> p.colors.primary
        else -> p.lookColors.lineAccent
    }

    // The highlight only covers the icon box, so the label always sits on the panel and must contrast with it (not
    // reuse the on-accent icon tint; see GUIDELINE.md).
    override fun textColor(p: LookPaint, selected: Boolean): Color = if (selected) p.colors.primary else p.colors.secondary
}
