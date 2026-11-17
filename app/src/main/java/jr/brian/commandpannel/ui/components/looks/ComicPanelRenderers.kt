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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.components.StarburstShape
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Paint as ComposePaint

/*
 * Comic Panel renderers:
 *   - `CardStyle.INK_PANEL`
 *   - `TileStyle.STARBURST`
 *   - `GaugeStyle.PIE`
 *   - `SliderStyle.INK`
 *   - `NavStyle.STARBURST`
 *   - `BackdropStyle.HALFTONE`
 *
 * A comic page in Bangers: `comicPaper` panels with thick `comicInk` borders, hand-placed tilts,
 * starburst selection, pie gauges, a speech-bubble FPS badge, accent numerals with an ink outline,
 * and a halftone dot backdrop (`halftone`).
 */

private val PanelInk = aynDp(3f)
private val LineInk = aynDp(2.5f)
private val BurstInk = aynDp(2f)
private val HalftoneStep = aynDp(6f)
private val HalftoneDot = aynDp(1f)
private const val CARD_TILT = 0.6f
private const val BURST_SCALE = 1.25f
private val Burst = StarburstShape(points = 8, innerRadiusRatio = 0.62f)

/** Deterministic hand-placed tilt for a card titled [title]: ±0.6°, untitled cards stay level. */
internal fun comicCardTilt(title: String?): Float = when {
    title.isNullOrEmpty() -> 0f
    (title.hashCode() and 1) == 0 -> -CARD_TILT
    else -> CARD_TILT
}

/** Pie wedge sweep from 12 o'clock, clockwise; bidirectional values may sweep backwards. */
internal fun comicPieSweep(fraction: Float, bidirectional: Boolean): Float =
    (if (bidirectional) fraction.coerceIn(-1f, 1f) else fraction.coerceIn(0f, 1f)) * 360f

/** One halftone cell bitmap ([step] px square, centered dot of [dot] px) tiled as a brush. */
private fun halftoneBrush(step: Float, dot: Float, color: Color): ShaderBrush {
    val cell = step.roundToInt().coerceAtLeast(2)
    val bitmap = ImageBitmap(cell, cell)
    ComposeCanvas(bitmap).drawCircle(Offset(cell / 2f, cell / 2f), dot, ComposePaint().apply { this.color = color })
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** Starburst outline [scale] times the size of [size], centered on it. */
private fun burstPath(size: Size, scale: Float, density: Density): Path {
    val burst = Size(size.width * scale, size.height * scale)
    return Path().apply {
        addOutline(Burst.createOutline(burst, LayoutDirection.Ltr, density))
        translate(Offset((size.width - burst.width) / 2f, (size.height - burst.height) / 2f))
    }
}

/** FPS readout: an ink outline pass under the accent fill pass. */
private class ComicReadoutPainter(private val fillArgb: Int, private val strokePx: Float) : ReadoutPainter {
    override fun drawNumber(canvas: NativeCanvas, number: String, x: Float, baseline: Float, paint: Paint) {
        val ink = paint.color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokePx
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawText(number, x, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.color = fillArgb
        canvas.drawText(number, x, baseline, paint)
        paint.color = ink
    }
}

@Composable
private fun InkedText(
    text: String,
    fill: Color,
    ink: Color,
    fontSize: TextUnit,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val stroke = remember(density, fontSize) {
        Stroke(width = with(density) { (fontSize.toPx() * 0.12f).coerceAtLeast(2f) }, join = StrokeJoin.Round)
    }
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(text = text, color = ink, fontSize = fontSize, style = style.copy(drawStyle = stroke), fontWeight = fontWeight, maxLines = 1)
        Text(text = text, color = fill, fontSize = fontSize, style = style, fontWeight = fontWeight, maxLines = 1)
    }
}

internal object ComicPanelCard : LookCardRenderer {
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
        val tilt = comicCardTilt(title)
        val paper = fill ?: if (focused) lerp(lc.comicPaper, p.colors.accent, 0.10f) else lc.comicPaper
        // Rotating the layer also rotates the clip, so content stays inside the tilted panel.
        return base
            .graphicsLayer { rotationZ = tilt }
            .clip(shape)
            .background(paper)
            .border(PanelInk, lc.comicInk, shape)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        return base.clip(shape).background(p.lookColors.comicPaper).border(PanelInk, p.lookColors.comicInk, shape)
    }

    override fun content(p: LookPaint): Color = p.lookColors.comicInk

    /** Speech bubble: one path (rounded body + bottom-left tail) so the ink outline is continuous. */
    override fun fpsBadgeTransform(p: LookPaint): Modifier {
        val lc = p.lookColors
        return Modifier.drawWithCache {
            val r = aynDp(10f).toPx()
            val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
            val tailH = aynDp(7f).toPx()
            val tail = Path().apply {
                moveTo(r * 1.2f, size.height - 1f)
                lineTo(r * 0.6f, size.height + tailH)
                lineTo(r * 2.6f, size.height - 1f)
                close()
            }
            val bubble = Path().apply { op(body, tail, PathOperation.Union) }
            val stroke = Stroke(LineInk.toPx(), join = StrokeJoin.Round)
            onDrawBehind {
                drawPath(bubble, lc.comicPaper)
                drawPath(bubble, lc.comicInk, style = stroke)
            }
        }
    }

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier = Modifier

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.comicInk

    @Composable
    override fun rememberReadoutPainter(p: LookPaint): ReadoutPainter {
        val density = LocalDensity.current
        val fill = p.colors.accent.toArgb()
        return remember(fill, density) { ComicReadoutPainter(fill, with(density) { aynDp(3f).toPx() }) }
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
        InkedText(text, p.colors.accent, p.lookColors.comicInk, fontSize, style, fontWeight, modifier)
    }
}

internal object ComicPanelTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val lc = p.lookColors
        val accent = p.colors.accent
        return base.drawWithCache {
            val stroke = Stroke(LineInk.toPx())
            val burst = if (selected) burstPath(size, BURST_SCALE, this) else null
            val burstStroke = Stroke(BurstInk.toPx(), join = StrokeJoin.Miter)
            onDrawBehind {
                if (burst != null) {
                    drawPath(burst, accent)
                    drawPath(burst, lc.comicInk, style = burstStroke)
                } else {
                    val r = size.minDimension / 2f
                    drawCircle(lc.comicPaper, r)
                    drawCircle(lc.comicInk, r - stroke.width / 2f, style = stroke)
                }
            }
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.onAccent else p.lookColors.comicInk

    override fun labelColor(p: LookPaint, selected: Boolean): Color =
        p.lookColors.comicInk.copy(alpha = if (selected) 1f else 0.75f)
}

internal object ComicPanelGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors =
        GaugeRingColors(p.colors.accent, p.lookColors.accent2, p.colors.accent, p.lookColors.accent2)

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val lc = p.lookColors
        val labelStyle = tccTextStyle()
        val displayStyle = tccDisplayTextStyle()
        val density = LocalDensity.current
        val stroke = remember(density) { Stroke(with(density) { LineInk.toPx() }, join = StrokeJoin.Round) }
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * p.spec.settingsTab.ringDiameterRatio
            val valueSp = with(density) { (side * p.spec.settingsTab.ringValueScale * 0.9f).toSp() }
            val titleSp = with(density) { (side * p.spec.settingsTab.ringTitleScale).toSp() }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(side * 0.03f)) {
                Canvas(modifier = Modifier.size(side * 0.56f)) {
                    val inset = stroke.width / 2f
                    val pieSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)
                    drawCircle(lc.comicPaper, size.minDimension / 2f - inset)
                    val sweep = comicPieSweep(reading.fraction(), reading.bidirectional)
                    if (sweep != 0f) {
                        drawArc(reading.color, -90f, sweep, true, topLeft, pieSize)
                        drawArc(lc.comicInk, -90f, sweep, true, topLeft, pieSize, style = stroke)
                    }
                    drawCircle(lc.comicInk, size.minDimension / 2f - inset, style = stroke)
                }
                InkedText("${reading.valueText} ${reading.unitText}", reading.color, lc.comicInk, valueSp, displayStyle, FontWeight.Normal)
                Text(text = reading.title, color = lc.comicInk, fontSize = titleSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal object ComicPanelSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier {
        val lc = p.look.lookColors
        return Modifier.drawBehind {
            val r = size.height / 2f
            val sw = LineInk.toPx()
            drawRoundRect(lc.comicPaper, cornerRadius = CornerRadius(r, r))
            drawRoundRect(lc.comicInk, Offset(sw / 2f, sw / 2f), Size(size.width - sw, size.height - sw), CornerRadius(r, r), style = Stroke(sw))
        }
    }

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val fill = if (p.dimmed) p.fillColor else p.look.colors.accent
        val density = LocalDensity.current
        val dots = remember(density, lc.comicInk) {
            with(density) { halftoneBrush(HalftoneStep.toPx() * 0.6f, HalftoneDot.toPx(), lc.comicInk.copy(alpha = 0.25f)) }
        }
        val stroke = remember(density) { Stroke(with(density) { LineInk.toPx() }) }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = stroke.width
            val h = size.height - sw * 2f
            val r = h / 2f
            val thumbR = size.height / 2f - sw / 2f
            val cx = (p.fraction * size.width).coerceIn(size.height / 2f, size.width - size.height / 2f)
            if (p.fraction > 0f) {
                val w = (cx - sw).coerceAtLeast(h)
                drawRoundRect(fill, Offset(sw, sw), Size(w, h), CornerRadius(r, r))
                if (!p.dimmed) drawRoundRect(dots, Offset(sw, sw), Size(w, h), CornerRadius(r, r))
            }
            drawCircle(lc.comicPaper, thumbR, Offset(cx, size.height / 2f))
            drawCircle(lc.comicInk, thumbR, Offset(cx, size.height / 2f), style = stroke)
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.onAccent else p.look.lookColors.comicInk

    override fun labelColor(p: SliderPaint): Color = p.look.lookColors.comicInk
}

internal object ComicPanelNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(12f))

    override fun highlight(p: LookPaint): Modifier {
        val tile = p.spec.tile
        val lc = p.lookColors
        return Modifier.drawWithCache {
            val box = tile.navTileSize.toPx()
            val burst = burstPath(Size(box, box), 1.1f, this).apply {
                translate(Offset((size.width - box) / 2f, (size.height - box) / 2f))
            }
            val stroke = Stroke(BurstInk.toPx(), join = StrokeJoin.Miter)
            onDrawBehind {
                drawPath(burst, lc.accent2)
                drawPath(burst, lc.comicInk, style = stroke)
            }
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.onAccent2 else p.lookColors.comicInk.copy(alpha = 0.75f)

    override fun textColor(p: LookPaint, selected: Boolean): Color =
        p.lookColors.comicInk.copy(alpha = if (selected) 1f else 0.75f)
}

/**
 * [jr.brian.commandpannel.ui.theme.BackdropStyle.HALFTONE]: a dot grid every
 * `aynDp(6f)` (dot radius `aynDp(1f)`) in `halftone`, tiled from one cached cell bitmap.
 */
internal fun Modifier.halftoneBackdrop(p: LookPaint): Modifier {
    val color = p.lookColors.halftone
    return drawWithCache {
        val brush = halftoneBrush(HalftoneStep.toPx(), HalftoneDot.toPx(), color)
        onDrawBehind { drawRect(brush) }
    }
}
