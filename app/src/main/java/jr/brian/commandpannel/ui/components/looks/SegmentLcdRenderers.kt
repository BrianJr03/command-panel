package jr.brian.commandpannel.ui.components.looks

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/*
 * Segment LCD renderers:
 *   - `CardStyle.LCD_RECESSED`
 *   - `TileStyle.LCD`
 *   - `GaugeStyle.LCD_BAR`
 *   - `SliderStyle.LCD_SEGMENTS`
 *   - `NavStyle.LCD_INVERT`
 *   - `BackdropStyle.LCD_PIXEL_GRID`
 *
 * Recessed monochrome panels (`lcdPanel`) drawn in a single ink (`lcdInk`, the contrast-clamped
 * accent). Seven-segment numerals (DSEG7, `LookTraits.ghostSegments`) sit over faint unlit "8"s in
 * `lcdGhost`; unlit bar and slider segments use the same ghost color.
 */

private const val LCD_GAUGE_SEGMENTS = 10
private const val LCD_SLIDER_SEGMENTS = 20

/**
 * Unlit "ghost" pattern for a seven-segment readout: every digit (and `-`) becomes `8`, other
 * characters are kept so the ghost lines up with [text] in the monospaced segment font.
 */
internal fun ghostSegmentsFor(text: String): String =
    buildString(text.length) { text.forEach { append(if (it.isDigit() || it == '-') '8' else it) } }

/** Lit segment count for a bar of [segments] at [fraction] (sign ignored). */
internal fun lcdLitSegments(fraction: Float, segments: Int): Int =
    (abs(fraction).coerceIn(0f, 1f) * segments).roundToInt().coerceIn(0, segments)

private fun bevelDark(p: LookPaint) = lerp(p.lookColors.lcdPanel, p.lookColors.lcdInk, if (p.colors.isDark) 0.10f else 0.18f)
private fun bevelLight(p: LookPaint) = lerp(p.lookColors.lcdPanel, p.colors.primaryBackground, 0.6f)

/** Recessed panel: 1dp ink border plus a 2dp inner bevel (dark top-left, light bottom-right). */
private fun Modifier.lcdRecessed(p: LookPaint, shape: Shape, fill: Color): Modifier {
    val dark = bevelDark(p)
    val light = bevelLight(p)
    val border = p.lookColors.lcdInk.copy(alpha = 0.40f)
    return clip(shape)
        .background(fill)
        .drawBehind {
            val b = 1.dp.toPx()
            val w = aynDp(2f).toPx()
            val half = w / 2f
            val inset = b + half
            drawLine(dark, Offset(inset, inset), Offset(size.width - inset, inset), w)
            drawLine(dark, Offset(inset, inset), Offset(inset, size.height - inset), w)
            drawLine(light, Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset), w)
            drawLine(light, Offset(size.width - inset, inset), Offset(size.width - inset, size.height - inset), w)
        }
        .border(1.dp, border, shape)
}

/** Ghost "8"s at [ghost] behind [text], both in the active segment display font. */
@Composable
private fun GhostedText(
    text: String,
    color: Color,
    ghost: Color,
    fontSize: TextUnit,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(text = ghostSegmentsFor(text), color = ghost, fontSize = fontSize, style = style, fontWeight = fontWeight, maxLines = 1)
        Text(text = text, color = color, fontSize = fontSize, style = style, fontWeight = fontWeight, maxLines = 1)
    }
}

/** Draws [segments] rectangular cells, the first [lit] (from the right when [fromEnd]) in [on]. */
private fun DrawScope.drawLcdSegments(segments: Int, lit: Int, on: Color, off: Color, inset: Float, gap: Float, fromEnd: Boolean = false) {
    val segW = (size.width - inset * 2f - gap * (segments - 1)) / segments
    for (i in 0 until segments) {
        val isLit = if (fromEnd) i >= segments - lit else i < lit
        drawRect(
            color = if (isLit) on else off,
            topLeft = Offset(inset + i * (segW + gap), inset),
            size = Size(segW, size.height - inset * 2f)
        )
    }
}

/** FPS readout: ghost "8"s at `lcdGhost` behind the lit digits. */
private class LcdReadoutPainter(private val ghostArgb: Int) : ReadoutPainter {
    private val ghosts = Array(6) { "8".repeat(it) }

    override fun drawNumber(canvas: NativeCanvas, number: String, x: Float, baseline: Float, paint: Paint) {
        val lit = paint.color
        paint.color = ghostArgb
        canvas.drawText(ghosts.getOrNull(number.length) ?: ghostSegmentsFor(number), x, baseline, paint)
        paint.color = lit
        canvas.drawText(number, x, baseline, paint)
    }
}

internal object SegmentLcdCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val panel = fill ?: if (focused) lerp(p.lookColors.lcdPanel, p.lookColors.lcdInk, 0.10f) else p.lookColors.lcdPanel
        return base.lcdRecessed(p, shape, panel)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier =
        base.lcdRecessed(p, p.spec.dialog.dialogShape, lerp(p.colors.dialogBackground, p.colors.accent, 0.08f))

    override fun content(p: LookPaint): Color = p.lookColors.lcdInk

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier {
        val shape = p.spec.settingsTab.fpsBadgeShape
        return if (highRate) {
            Modifier.background(p.lookColors.lcdInk, shape)
        } else {
            Modifier.border(1.dp, p.lookColors.lcdInk, shape)
        }
    }

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color =
        if (highRate) p.lookColors.lcdPanel else p.lookColors.lcdInk

    @Composable
    override fun rememberReadoutPainter(p: LookPaint): ReadoutPainter {
        if (!p.spec.traits.ghostSegments) return PlainReadoutPainter
        val ghost = p.lookColors.lcdGhost
        return remember(ghost) { LcdReadoutPainter(ghost.toArgb()) }
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
        val p = currentLookPaint()
        if (!p.spec.traits.ghostSegments) {
            Text(text = text, color = color, fontSize = fontSize, style = style, fontWeight = fontWeight, maxLines = 1, modifier = modifier)
            return
        }
        GhostedText(text, color, p.lookColors.lcdGhost, fontSize, style, fontWeight, modifier)
    }
}

internal object SegmentLcdTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val shape = p.spec.tile.buttonShape
        return if (selected) {
            base.clip(shape).background(p.lookColors.lcdInk)
        } else {
            base.clip(shape)
                .background(p.lookColors.lcdPanel)
                .border(1.dp, p.lookColors.lcdInk.copy(alpha = 0.55f), shape)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.lcdPanel else p.lookColors.lcdInk

    override fun labelColor(p: LookPaint, selected: Boolean): Color =
        p.lookColors.lcdInk.copy(alpha = if (selected) 1f else 0.65f)
}

internal object SegmentLcdGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val ink = p.lookColors.lcdInk
        return GaugeRingColors(ink, ink, ink, ink)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val settingsSpec = p.spec.settingsTab
        val labelStyle = tccTextStyle()
        val displayStyle = tccDisplayTextStyle()
        val ink = reading.color
        val ghost = p.lookColors.lcdGhost
        val error = p.colors.error
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * settingsSpec.ringDiameterRatio
            val density = LocalDensity.current
            val titleSp = with(density) { (side * settingsSpec.ringTitleScale).toSp() }
            val valueSp = with(density) { (side * settingsSpec.ringValueScale * 0.95f).toSp() }
            val unitSp = with(density) { (side * settingsSpec.ringUnitScale).toSp() }
            Column(
                modifier = Modifier.width(side),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(side * 0.05f)
            ) {
                Text(text = reading.title, color = ink.copy(alpha = 0.7f), fontSize = titleSp, style = labelStyle, maxLines = 1)
                if (p.spec.traits.ghostSegments) {
                    GhostedText(reading.valueText, ink, ghost, valueSp, displayStyle, FontWeight.Normal)
                } else {
                    Text(text = reading.valueText, color = ink, fontSize = valueSp, style = displayStyle, maxLines = 1)
                }
                Canvas(
                    modifier = Modifier
                        .width(side * 0.92f)
                        .height(side * 0.12f)
                ) {
                    val fraction = reading.fraction()
                    drawLcdSegments(
                        segments = LCD_GAUGE_SEGMENTS,
                        lit = lcdLitSegments(fraction, LCD_GAUGE_SEGMENTS),
                        on = if (fraction < 0f) error else ink,
                        off = ghost,
                        inset = 0f,
                        gap = aynDp(1.5f).toPx(),
                        fromEnd = fraction < 0f
                    )
                }
                Text(text = reading.unitText, color = ink.copy(alpha = 0.7f), fontSize = unitSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal object SegmentLcdSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier
        .background(p.look.lookColors.lcdPanel)
        .border(1.dp, p.look.lookColors.lcdInk.copy(alpha = 0.45f), p.look.spec.slider.trackShape)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val ink = p.look.lookColors.lcdInk
        val on = if (p.dimmed) ink.copy(alpha = 0.35f) else ink
        val off = p.look.lookColors.lcdGhost
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLcdSegments(
                segments = LCD_SLIDER_SEGMENTS,
                lit = lcdLitSegments(p.fraction, LCD_SLIDER_SEGMENTS),
                on = on,
                off = off,
                inset = aynDp(2f).toPx(),
                gap = aynDp(1.5f).toPx()
            )
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.lcdPanel else p.look.lookColors.lcdInk

    override fun labelColor(p: SliderPaint): Color = p.look.lookColors.lcdInk
}

internal object SegmentLcdNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(2f))

    override fun highlight(p: LookPaint): Modifier = Modifier.clip(highlightShape).background(p.lookColors.lcdInk)

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.lcdPanel else p.lookColors.lcdInk.copy(alpha = 0.65f)

    // The label sits below the ink pill on the panel, so the selected one needs ink (not the on-pill panel colour).
    override fun textColor(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.lcdInk else p.lookColors.lcdInk.copy(alpha = 0.65f)
}

/**
 * [jr.brian.commandpannel.ui.theme.BackdropStyle.LCD_PIXEL_GRID]: a faint
 * dot-matrix of `lcdInk` cells (≤ 4% alpha) tiled from one cached cell bitmap.
 */
internal fun Modifier.lcdPixelGridBackdrop(p: LookPaint): Modifier {
    val ink = p.lookColors.lcdInk.copy(alpha = if (p.colors.isDark) 0.035f else 0.04f)
    return drawWithCache {
        val cell = aynDp(3f).toPx().roundToInt().coerceAtLeast(3)
        val bitmap = ImageBitmap(cell, cell)
        ComposeCanvas(bitmap).drawRect(
            0f,
            0f,
            (cell - 1).toFloat(),
            (cell - 1).toFloat(),
            androidx.compose.ui.graphics.Paint().apply { color = ink }
        )
        val brush = ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
        onDrawBehind { drawRect(brush) }
    }
}
