package jr.brian.commandpannel.ui.components.looks

import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.components.RingGaugeLayout
import jr.brian.commandpannel.ui.theme.TccLookColors
import jr.brian.commandpannel.ui.theme.aynDp

/*
 * Y2K Chrome renderers:
 *   - `CardStyle.CHROME_RIM`
 *   - `TileStyle.CHROME`
 *   - `GaugeStyle.CHROME_RING`
 *   - `SliderStyle.CHROME`
 *   - `NavStyle.FOIL_PILL`
 *
 * Turn-of-the-millennium liquid chrome: unselected controls are polished metal (`chromeBrush`, a
 * vertical gradient with a sharp dark horizon, its grays tinted <= 6% toward the accent) and
 * selected controls switch to an iridescent foil (`foilBrush`, a 135° sweep through the accent,
 * two hue-rotated accents and `accent2`, clamped against `onFoil`). Michroma is used only for
 * numerals and the FPS badge.
 */

private const val FOIL_THRESHOLD = 0.8f

/** Chrome gradient stops: highlight, light metal, dark horizon, dark metal, mid metal, highlight. */
internal fun chromeStops(lc: TccLookColors): Array<Pair<Float, Color>> = arrayOf(
    0f to lc.chromeHighlight,
    0.46f to lc.chromeLight,
    0.50f to lc.chromeHorizon,
    0.52f to lc.chromeDark,
    0.75f to lc.chromeMid,
    1f to lc.chromeHighlight
)

/** Vertical chrome brush spanning [top]..[top] + [height]. */
internal fun chromeBrush(lc: TccLookColors, height: Float, top: Float = 0f): Brush =
    Brush.verticalGradient(*chromeStops(lc), startY = top, endY = top + height)

/** 135° iridescent foil brush across [size] (top-left to bottom-right) starting at [origin]. */
internal fun foilBrush(lc: TccLookColors, size: Size, origin: Offset = Offset.Zero): Brush =
    Brush.linearGradient(lc.foilColors, start = origin, end = Offset(origin.x + size.width, origin.y + size.height))

/** The gauge arc switches from chrome to foil above [FOIL_THRESHOLD]. */
internal fun chromeRingUsesFoil(fraction: Float): Boolean = kotlin.math.abs(fraction) > FOIL_THRESHOLD

/** Rebuilds chrome / foil brushes only when the drawn size changes (canvases that animate). */
private class ChromeBrushes(private val lc: TccLookColors) {
    private var size = Size.Unspecified
    lateinit var chrome: Brush
        private set
    lateinit var foil: Brush
        private set

    fun update(s: Size) {
        if (s == size) return
        size = s
        chrome = chromeBrush(lc, s.height)
        foil = foilBrush(lc, s)
    }
}

private fun chromePanel(p: LookPaint, base: Color): Color =
    if (p.colors.isDark) lerp(base, Color.Black, 0.25f) else base

/** Dark tonal panel with a 1dp chrome rim and a 1dp top inner highlight, cached per size. */
private fun Modifier.chromeRim(p: LookPaint, shape: Shape, fill: Color): Modifier {
    val lc = p.lookColors
    return drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val rim = chromeBrush(lc, size.height)
        val stroke = Stroke(1.dp.toPx())
        val inset = aynDp(4f).toPx()
        onDrawBehind {
            drawPath(path, fill)
            drawLine(lc.chromeHighlight.copy(alpha = 0.55f), Offset(inset * 2f, inset * 0.5f + 1f), Offset(size.width - inset * 2f, inset * 0.5f + 1f), 1.dp.toPx())
            drawPath(path, rim, style = stroke)
        }
    }.clip(shape)
}

/** FPS readout filled with the chrome gradient through a cached `LinearGradient` shader. */
private class ChromeReadoutPainter(private val lc: TccLookColors) : ReadoutPainter {
    private val colors = chromeStops(lc).map { it.second.toArgb() }.toIntArray()
    private val positions = chromeStops(lc).map { it.first }.toFloatArray()
    private var shader: Shader? = null
    private var key = Float.NaN

    override fun drawNumber(canvas: NativeCanvas, number: String, x: Float, baseline: Float, paint: Paint) {
        val fm = paint.fontMetrics
        val top = baseline + fm.ascent
        val k = top * 31f + paint.textSize
        if (shader == null || k != key) {
            key = k
            shader = LinearGradient(0f, top, 0f, baseline + fm.descent, colors, positions, Shader.TileMode.CLAMP)
        }
        // A thin dark outline keeps the light highlights legible on light palettes.
        val ink = paint.color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = paint.textSize * 0.025f
        paint.color = lc.onChrome.copy(alpha = 0.55f).toArgb()
        canvas.drawText(number, x, baseline, paint)
        paint.style = Paint.Style.FILL
        paint.shader = shader
        canvas.drawText(number, x, baseline, paint)
        paint.shader = null
        paint.color = ink
    }
}

internal object Y2kChromeCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val panel = fill ?: chromePanel(p, if (focused) p.colors.panelBackgroundSelected else p.colors.panelBackground)
        return base.chromeRim(p, shape, panel)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier =
        base.chromeRim(p, p.spec.dialog.dialogShape, chromePanel(p, p.colors.dialogBackground))

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier {
        val lc = p.lookColors
        return Modifier.drawWithCache {
            val foil = foilBrush(lc, size)
            onDrawBehind { drawRect(foil) }
        }
    }

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.onFoil

    override fun fpsBadgeStyle(p: LookPaint, base: TextStyle): TextStyle =
        p.spec.traits.displayFontFamily?.let { base.copy(fontFamily = it) } ?: base

    @Composable
    override fun rememberReadoutPainter(p: LookPaint): ReadoutPainter {
        val lc = p.lookColors
        return remember(lc) { ChromeReadoutPainter(lc) }
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
        val brush = remember(lc) { Brush.verticalGradient(*chromeStops(lc)) }
        Text(text = text, fontSize = fontSize, style = style.copy(brush = brush), fontWeight = fontWeight, maxLines = 1, modifier = modifier)
    }
}

internal object Y2kChromeTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val lc = p.lookColors
        return base.drawWithCache {
            val brush = if (selected) foilBrush(lc, size) else chromeBrush(lc, size.height)
            val edge = Stroke(1.dp.toPx())
            onDrawBehind {
                val r = size.minDimension / 2f
                drawCircle(brush, r)
                if (!selected) drawCircle(lc.chromeDark, r - edge.width / 2f, style = edge)
            }
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.onFoil else p.lookColors.onChrome
}

internal object Y2kChromeGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val c = p.lookColors.chromeMid
        return GaugeRingColors(c, c, c, c)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val lc = currentLookPaint().lookColors
        val density = LocalDensity.current
        val stroke = remember(density) { Stroke(with(density) { aynDp(5f).toPx() }, cap = StrokeCap.Round) }
        val brushes = remember(lc) { ChromeBrushes(lc) }
        val track = lc.chromeHorizon.copy(alpha = 0.35f)
        RingGaugeLayout(reading, modifier) { fraction ->
            brushes.update(size)
            val inset = stroke.width / 2f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = stroke)
            val start = if (reading.bidirectional) 270f else 135f
            val sweep = if (reading.bidirectional) fraction * 135f else fraction.coerceIn(0f, 1f) * 270f
            if (sweep != 0f) {
                drawArc(if (chromeRingUsesFoil(fraction)) brushes.foil else brushes.chrome, start, sweep, false, topLeft, arcSize, style = stroke)
            }
        }
    }
}

internal object Y2kChromeSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier =
        Modifier.background(lerp(chromePanel(p.look, p.look.colors.panelBackground), Color.Black, 0.30f))

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val brushes = remember(lc) { ChromeBrushes(lc) }
        val thumbBrushes = remember(lc) { ChromeBrushes(lc) }
        Canvas(modifier = Modifier.fillMaxSize()) {
            brushes.update(size)
            val inset = aynDp(3f).toPx()
            val h = size.height - inset * 2f
            val r = h / 2f
            val thumbR = size.height / 2f
            val cx = (p.fraction * size.width).coerceIn(thumbR, size.width - thumbR)
            if (p.fraction > 0f) {
                val w = (cx - inset).coerceAtLeast(h)
                if (p.dimmed) {
                    drawRoundRect(p.fillColor, Offset(inset, inset), Size(w, h), CornerRadius(r, r))
                } else {
                    drawRoundRect(brushes.foil, Offset(inset, inset), Size(w, h), CornerRadius(r, r))
                }
            }
            thumbBrushes.update(Size(thumbR * 2f, size.height))
            drawCircle(thumbBrushes.chrome, thumbR - 1.dp.toPx(), Offset(cx, size.height / 2f))
            drawCircle(lc.chromeDark, thumbR - 1.dp.toPx(), Offset(cx, size.height / 2f), style = Stroke(1.dp.toPx()))
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.onFoil else p.look.colors.primary
}

internal object Y2kChromeNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(16f))

    override fun highlight(p: LookPaint): Modifier {
        val tile = p.spec.tile
        val lc = p.lookColors
        return Modifier.drawWithCache {
            val w = aynDp(56f).toPx()
            val h = aynDp(32f).toPx()
            val cy = (tile.compactTopGap + tile.navTileSize / 2f).toPx()
            val topLeft = Offset((size.width - w) / 2f, cy - h / 2f)
            val foil = foilBrush(lc, Size(w, h), topLeft)
            onDrawBehind { drawRoundRect(foil, topLeft, Size(w, h), CornerRadius(h / 2f, h / 2f)) }
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.onFoil else p.colors.secondary

    override fun textColor(p: LookPaint, selected: Boolean): Color =
        if (selected) p.colors.primary else p.colors.secondary
}
