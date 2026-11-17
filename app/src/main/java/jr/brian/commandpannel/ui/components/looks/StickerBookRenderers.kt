package jr.brian.commandpannel.ui.components.looks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.components.RingGaugeLayout
import jr.brian.commandpannel.ui.theme.TccColorPalette
import jr.brian.commandpannel.ui.theme.TccLookColors
import jr.brian.commandpannel.ui.theme.aynDp

/*
 * Sticker Book renderers:
 *   - `CardStyle.STICKER_NOTE`
 *   - `TileStyle.STICKER`
 *   - `GaugeStyle.STICKER_DONUT`
 *   - `SliderStyle.STICKER`
 *   - `NavStyle.STICKER`
 *
 * Craft look in Baloo 2: tonal cards with a strip of translucent washi `tape` across the top,
 * die-cut stickers (`stickerBorder` rim, `stickerShadow` drop shadow) for tiles, gauges, sliders
 * and the nav selection, alternating `accent` / `accent2` fills and per-index tilts.
 */

private val DieCut = aynDp(3f)
private val ShadowDrop = aynDp(1.5f)
private const val POP_SCALE = 1.08f
private const val POP_MILLIS = 180
private const val NAV_TILT = -4f
private const val BADGE_TILT = -3f

/** Deterministic sticker tilt for grid position [index]: alternating about −7° and +6°. */
internal fun stickerTilt(index: Int): Float = if (index % 2 == 0) -7f else 6f

/** Whether sticker [index] uses the second accent (alternates per index). */
internal fun stickerUsesAccent2(index: Int): Boolean = index % 2 == 1

private fun stickerFill(colors: TccColorPalette, lookColors: TccLookColors, index: Int): Color =
    if (stickerUsesAccent2(index)) lookColors.accent2 else colors.accent

private fun stickerTonal(p: LookPaint): Color = lerp(p.colors.panelBackground, p.colors.primary, if (p.colors.isDark) 0.08f else 0.05f)

/** Die-cut sticker: drop shadow, `stickerBorder` rim of [DieCut], then [fill] inside. */
private fun DrawScope.drawSticker(fill: Color, border: Color, shadow: Color, radius: Float) {
    val drop = ShadowDrop.toPx()
    val cut = DieCut.toPx()
    drawRoundRect(shadow, Offset(0f, drop), size, CornerRadius(radius, radius))
    drawRoundRect(border, Offset.Zero, size, CornerRadius(radius, radius))
    val inner = (radius - cut).coerceAtLeast(0f)
    drawRoundRect(fill, Offset(cut, cut), Size(size.width - cut * 2f, size.height - cut * 2f), CornerRadius(inner, inner))
}

internal object StickerBookCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val tonal = lerp(p.colors.panelBackground, p.colors.accent, if (focused) 0.12f else 0.05f)
        val tape = p.lookColors.tape
        // Washi tape over the top edge, drawn after (and outside) the clipped card content.
        return base.drawWithContent {
            drawContent()
            val w = aynDp(30f).toPx()
            val h = aynDp(9f).toPx()
            val center = Offset(size.width / 2f, h * 0.35f)
            rotate(-4f, center) {
                drawRect(tape, Offset(center.x - w / 2f, center.y - h / 2f), Size(w, h))
            }
        }.clip(shape).background(fill ?: tonal)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        return base.clip(shape).background(p.colors.dialogBackground).border(aynDp(2f), p.lookColors.stickerBorder, shape)
    }

    override fun fpsBadgeTransform(p: LookPaint): Modifier = Modifier.graphicsLayer { rotationZ = BADGE_TILT }

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier {
        val lc = p.lookColors
        return Modifier.drawBehind {
            val r = size.height / 2f
            val cut = aynDp(2.5f).toPx()
            drawRoundRect(lc.stickerBorder, cornerRadius = CornerRadius(r, r))
            drawRoundRect(lc.accent2, Offset(cut, cut), Size(size.width - cut * 2f, size.height - cut * 2f), CornerRadius(r - cut, r - cut))
        }
    }

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.onAccent2
}

internal object StickerBookTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier = base.composed {
        val animate = p.spec.traits.animate
        val tilt by animateFloatAsState(
            targetValue = if (selected) stickerTilt(index) else 0f,
            animationSpec = tween(if (animate) POP_MILLIS else 0),
            label = "stickerTilt"
        )
        // "Stick" pop when the tile turns on (1.0 → 1.08 → 1.0); not on first composition.
        val pop = remember { Animatable(1f) }
        val composedOnce = remember { mutableStateOf(false) }
        LaunchedEffect(selected) {
            if (composedOnce.value && selected && animate) {
                pop.animateTo(POP_SCALE, tween(POP_MILLIS / 2))
                pop.animateTo(1f, tween(POP_MILLIS / 2))
            }
            composedOnce.value = true
        }
        val lc = p.lookColors
        val fill = if (selected) stickerFill(p.colors, lc, index) else stickerTonal(p)
        Modifier
            .graphicsLayer {
                rotationZ = tilt
                scaleX = pop.value
                scaleY = pop.value
            }
            .drawWithCache {
                val radius = aynDp(12f).toPx()
                onDrawBehind { drawSticker(fill, lc.stickerBorder, lc.stickerShadow, radius) }
            }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color = when {
        !selected -> p.colors.primary
        stickerUsesAccent2(index) -> p.lookColors.onAccent2
        else -> p.lookColors.onAccent
    }
}

/**
 * Donut stroke widths for a gauge of [side] px: the spec's 10dp rim / 6dp track, capped to a
 * share of the gauge so the rim never runs under the title and value text.
 */
internal fun stickerDonutWidths(side: Float, rimPx: Float, trackPx: Float): Pair<Float, Float> {
    val rim = kotlin.math.min(rimPx, side * 0.075f)
    return rim to kotlin.math.min(trackPx, rim * 0.6f)
}

internal object StickerBookGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors =
        GaugeRingColors(p.colors.accent, p.lookColors.accent2, p.colors.accent, p.lookColors.accent2)

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val lc = p.lookColors
        val track = stickerTonal(p)
        RingGaugeLayout(reading, modifier) { fraction ->
            val (rim, inner) = stickerDonutWidths(size.minDimension, aynDp(10f).toPx(), aynDp(6f).toPx())
            val drop = ShadowDrop.toPx()
            val inset = rim / 2f + drop
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            // Strokes are only reallocated when the gauge size changes the clamped widths.
            val rimStroke = donutStroke(rim)
            val innerStroke = donutStroke(inner)
            drawArc(lc.stickerShadow, 135f, 270f, false, Offset(inset, inset + drop), arcSize, style = rimStroke)
            drawArc(lc.stickerBorder, 135f, 270f, false, topLeft, arcSize, style = rimStroke)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = innerStroke)
            val start = if (reading.bidirectional) 270f else 135f
            val sweep = if (reading.bidirectional) fraction * 135f else fraction.coerceIn(0f, 1f) * 270f
            if (sweep != 0f) drawArc(reading.color, start, sweep, false, topLeft, arcSize, style = innerStroke)
        }
    }
}

/** Round-cap stroke cache keyed by width, so gauge frames reuse the same [Stroke] objects. */
private val donutStrokes = HashMap<Float, Stroke>()

private fun donutStroke(width: Float): Stroke = donutStrokes.getOrPut(width) { Stroke(width, cap = StrokeCap.Round) }

internal object StickerBookSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier {
        val look = p.look
        val tonal = stickerTonal(look)
        val border = look.lookColors.stickerBorder
        return Modifier.drawBehind {
            val r = size.height / 2f
            val cut = DieCut.toPx()
            drawRoundRect(border, cornerRadius = CornerRadius(r, r))
            drawRoundRect(tonal, Offset(cut, cut), Size(size.width - cut * 2f, size.height - cut * 2f), CornerRadius(r - cut, r - cut))
        }
    }

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val fill = if (p.dimmed) p.fillColor else p.look.colors.accent
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cut = DieCut.toPx()
            val h = size.height - cut * 2f
            val r = h / 2f
            val cx = (p.fraction * size.width).coerceIn(size.height / 2f, size.width - size.height / 2f)
            if (p.fraction > 0f) {
                drawRoundRect(fill, Offset(cut, cut), Size((cx - cut).coerceAtLeast(h), h), CornerRadius(r, r))
            }
            val thumbR = size.height / 2f - 1.dp.toPx()
            drawCircle(lc.stickerShadow, thumbR, Offset(cx, size.height / 2f + ShadowDrop.toPx() * 0.5f))
            drawCircle(lc.stickerBorder, thumbR, Offset(cx, size.height / 2f))
            drawCircle(lc.accent2, thumbR - cut, Offset(cx, size.height / 2f))
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.onAccent else p.look.colors.primary
}

internal object StickerBookNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(12f))

    override fun highlight(p: LookPaint): Modifier {
        val lc = p.lookColors
        return Modifier
            .graphicsLayer { rotationZ = NAV_TILT }
            .drawWithCache {
                val radius = aynDp(12f).toPx()
                onDrawBehind { drawSticker(lc.accent2, lc.stickerBorder, lc.stickerShadow, radius) }
            }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.onAccent2 else p.colors.secondary
}
