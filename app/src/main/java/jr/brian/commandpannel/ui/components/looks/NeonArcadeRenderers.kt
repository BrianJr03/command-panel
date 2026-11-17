package jr.brian.commandpannel.ui.components.looks

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.components.RingGaugeLayout
import jr.brian.commandpannel.ui.theme.aynDp

/*
 * Neon Arcade renderers:
 *   - `CardStyle.NEON_TUBE`
 *   - `TileStyle.NEON`
 *   - `GaugeStyle.NEON_RING`
 *   - `SliderStyle.NEON_TUBE`
 *   - `NavStyle.NEON_UNDERLINE`
 *   - `BackdropStyle.SYNTH_GRID`
 *
 * Glowing tube outlines over a synthwave horizon grid. Two tube colors: `neonTube` (the palette
 * accent) for cards, `neonTube2` (the complementary `accent2`) for selected / active elements.
 * Glow strength follows `neonGlowAlpha`, which is low on light palettes so tubes read as colored
 * outlines rather than smudges.
 */

private const val GRID_HORIZON = 0.55f
private const val GRID_HORIZONTAL_LINES = 5
private const val GRID_CONVERGING_LINES = 11

/**
 * Synthwave grid segments `[x0, y0, x1, y1, ...]` for a [width] × [height] viewport: the horizon
 * at [GRID_HORIZON], [horizontal] lines spaced quadratically closer toward it, and [converging]
 * lines from the center vanishing point fanning out past the bottom corners.
 */
internal fun synthGridSegments(
    width: Float,
    height: Float,
    horizontal: Int = GRID_HORIZONTAL_LINES,
    converging: Int = GRID_CONVERGING_LINES
): FloatArray {
    val horizonY = height * GRID_HORIZON
    val depth = height - horizonY
    val out = FloatArray((horizontal + 1 + converging) * 4)
    var k = 0
    fun seg(x0: Float, y0: Float, x1: Float, y1: Float) {
        out[k++] = x0; out[k++] = y0; out[k++] = x1; out[k++] = y1
    }
    seg(0f, horizonY, width, horizonY)
    for (i in 1..horizontal) {
        val t = i.toFloat() / horizontal
        val y = horizonY + depth * t * t
        seg(0f, y, width, y)
    }
    val cx = width / 2f
    val spread = width * 1.6f
    for (j in 0 until converging) {
        val t = if (converging == 1) 0.5f else j.toFloat() / (converging - 1)
        seg(cx, horizonY, cx - spread / 2f + spread * t, height)
    }
    return out
}

/** Stroke paint whose shadow layer glows both outward and inward around a tube outline. */
private fun neonGlowPaint(color: Color, glowAlpha: Float, strokePx: Float, blurPx: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        this.color = color.toArgb()
        setShadowLayer(blurPx, 0f, 0f, color.copy(alpha = glowAlpha).toArgb())
    }

/**
 * Neon tube outline of [shape]: optional [panel] fill, then a [tube] stroke with a cached
 * glowing shadow layer. Paths and paints are built once per size in `drawWithCache`.
 */
private fun Modifier.neonTube(shape: Shape, tube: Color, glowAlpha: Float, stroke: Dp, blur: Dp, panel: Color?): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)
        val path = Path().apply { addOutline(outline) }
        val native = path.asAndroidPath()
        val glow = neonGlowPaint(tube, glowAlpha, stroke.toPx(), blur.toPx())
        onDrawBehind {
            if (panel != null) drawPath(path, panel)
            drawIntoCanvas { it.nativeCanvas.drawPath(native, glow) }
        }
    }

/** FPS readout: the number is drawn with a soft accent glow. */
private class NeonReadoutPainter(private val glowArgb: Int, private val blurPx: Float) : ReadoutPainter {
    override fun drawNumber(canvas: NativeCanvas, number: String, x: Float, baseline: Float, paint: Paint) {
        paint.setShadowLayer(blurPx, 0f, 0f, glowArgb)
        canvas.drawText(number, x, baseline, paint)
        paint.clearShadowLayer()
    }
}

/** Soft text glow for Compose numerals (TEMP / FAN values, gauge values). */
@Composable
private fun rememberNeonTextGlow(color: Color, glowAlpha: Float): Shadow {
    val density = LocalDensity.current
    return remember(color, glowAlpha, density) {
        Shadow(color = color.copy(alpha = glowAlpha), blurRadius = with(density) { aynDp(5f).toPx() })
    }
}

internal object NeonArcadeCard : LookCardRenderer {
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
        val panel = fill ?: if (focused) lerp(lc.neonPanel, lc.neonTube, 0.10f) else lc.neonPanel
        return base
            .neonTube(shape, lc.neonTube, lc.neonGlowAlpha * 0.8f, aynDp(1.5f), aynDp(8f), panel)
            .clip(shape)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        val lc = p.lookColors
        return base
            .neonTube(shape, lc.neonTube, lc.neonGlowAlpha * 0.8f, aynDp(1.5f), aynDp(8f), lerp(p.colors.dialogBackground, lc.neonTube, 0.05f))
            .clip(shape)
    }

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier =
        Modifier.border(aynDp(1.5f), p.lookColors.neonTube2, p.spec.settingsTab.fpsBadgeShape)

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.neonTube2

    @Composable
    override fun rememberReadoutPainter(p: LookPaint): ReadoutPainter {
        val density = LocalDensity.current
        val glow = p.lookColors.neonTube.copy(alpha = p.lookColors.neonGlowAlpha).toArgb()
        return remember(glow, density) { NeonReadoutPainter(glow, with(density) { aynDp(6f).toPx() }) }
    }

    @Composable
    override fun ValueText(
        text: String,
        color: Color,
        fontSize: TextUnit,
        style: TextStyle,
        fontWeight: FontWeight,
        modifier: Modifier
    ) {
        val lc = currentLookPaint().lookColors
        val glow = rememberNeonTextGlow(lc.neonTube, lc.neonGlowAlpha)
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            style = style.copy(shadow = glow),
            fontWeight = fontWeight,
            maxLines = 1,
            modifier = modifier
        )
    }
}

internal object NeonArcadeTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val lc = p.lookColors
        return if (selected) {
            base.neonTube(CircleShape, lc.neonTube2, lc.neonGlowAlpha, aynDp(1.5f), aynDp(7f), lc.neonTube2.copy(alpha = 0.10f))
        } else {
            base.clip(CircleShape).border(1.dp, lc.neonTube.copy(alpha = 0.40f), CircleShape)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.neonTube2 else p.lookColors.neonTube

    override fun labelColor(p: LookPaint, selected: Boolean): Color? =
        if (selected) p.lookColors.neonTube2 else null
}

private class NeonRingStrokes(val core: Stroke, val halo: Stroke, val wideHalo: Stroke, val track: Stroke)

internal object NeonArcadeGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val c = p.lookColors.neonTube2
        return GaugeRingColors(c, c, c, c)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val density = LocalDensity.current
        val lc = currentLookPaint().lookColors
        val strokes = remember(density) {
            with(density) {
                val core = aynDp(2.5f).toPx()
                NeonRingStrokes(
                    core = Stroke(core, cap = StrokeCap.Round),
                    halo = Stroke(core * 2.2f, cap = StrokeCap.Round),
                    wideHalo = Stroke(core * 4f, cap = StrokeCap.Round),
                    track = Stroke(core, cap = StrokeCap.Round)
                )
            }
        }
        val glowAlpha = lc.neonGlowAlpha
        val color = reading.color
        val track = color.copy(alpha = 0.14f)
        val halo = color.copy(alpha = 0.30f * glowAlpha)
        val wideHalo = color.copy(alpha = 0.14f * glowAlpha)
        val textGlow = rememberNeonTextGlow(color, glowAlpha)
        RingGaugeLayout(
            reading,
            modifier,
            valueText = { text, textColor, fontSize, style ->
                Text(text = text, color = textColor, fontSize = fontSize, style = style.copy(shadow = textGlow), maxLines = 1)
            }
        ) { fraction ->
            val inset = strokes.wideHalo.width / 2f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = strokes.track)
            val start = if (reading.bidirectional) 270f else 135f
            val sweep = if (reading.bidirectional) fraction * 135f else fraction.coerceIn(0f, 1f) * 270f
            if (sweep != 0f) {
                drawArc(wideHalo, start, sweep, false, topLeft, arcSize, style = strokes.wideHalo)
                drawArc(halo, start, sweep, false, topLeft, arcSize, style = strokes.halo)
                drawArc(color, start, sweep, false, topLeft, arcSize, style = strokes.core)
            }
        }
    }
}

internal object NeonArcadeSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier =
        Modifier.border(1.dp, p.look.lookColors.neonTube.copy(alpha = 0.55f), p.look.spec.slider.trackShape)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val tube = if (p.dimmed) p.fillColor else lc.neonTube
        val glowAlpha = if (p.dimmed) 0f else lc.neonGlowAlpha
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (p.fraction <= 0f) return@Canvas
            val pad = size.height * 0.30f
            val h = size.height - pad * 2f
            val w = ((size.width - pad * 2f) * p.fraction).coerceAtLeast(h)
            val r = CornerRadius(h / 2f, h / 2f)
            val halo1 = h * 0.45f
            val halo2 = h * 0.22f
            if (glowAlpha > 0f) {
                drawRoundRect(
                    tube.copy(alpha = 0.16f * glowAlpha),
                    Offset(pad - halo1, pad - halo1),
                    Size(w + halo1 * 2f, h + halo1 * 2f),
                    CornerRadius(h / 2f + halo1, h / 2f + halo1)
                )
                drawRoundRect(
                    tube.copy(alpha = 0.32f * glowAlpha),
                    Offset(pad - halo2, pad - halo2),
                    Size(w + halo2 * 2f, h + halo2 * 2f),
                    CornerRadius(h / 2f + halo2, h / 2f + halo2)
                )
            }
            drawRoundRect(tube, Offset(pad, pad), Size(w, h), r)
        }
    }

    override fun iconTint(p: SliderPaint): Color = p.look.colors.primary
}

internal object NeonArcadeNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(8f))

    override fun highlight(p: LookPaint): Modifier {
        val lc = p.lookColors
        val tube = lc.neonTube2
        val glowAlpha = lc.neonGlowAlpha
        return Modifier.drawBehind {
            val core = aynDp(2.5f).toPx()
            val y = size.height - core * 2f
            val half = size.width * 0.30f
            val cx = size.width / 2f
            drawLine(tube.copy(alpha = 0.16f * glowAlpha), Offset(cx - half, y), Offset(cx + half, y), core * 4f, StrokeCap.Round)
            drawLine(tube.copy(alpha = 0.32f * glowAlpha), Offset(cx - half, y), Offset(cx + half, y), core * 2.2f, StrokeCap.Round)
            drawLine(tube, Offset(cx - half, y), Offset(cx + half, y), core, StrokeCap.Round)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.neonTube2 else p.colors.secondary
}

/**
 * [jr.brian.commandpannel.ui.theme.BackdropStyle.SYNTH_GRID]: a perspective
 * horizon grid over the bottom ~45% in `neonGrid`, built once per size in `drawWithCache`.
 */
internal fun Modifier.synthGridBackdrop(p: LookPaint): Modifier {
    val grid = p.lookColors.neonGrid
    return drawWithCache {
        val segments = synthGridSegments(size.width, size.height)
        val path = Path().apply {
            for (i in segments.indices step 4) {
                moveTo(segments[i], segments[i + 1])
                lineTo(segments[i + 2], segments[i + 3])
            }
        }
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            clipRect(top = size.height * GRID_HORIZON - 1f) { drawPath(path, grid, style = stroke) }
        }
    }
}
