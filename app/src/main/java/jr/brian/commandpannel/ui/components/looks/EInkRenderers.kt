package jr.brian.commandpannel.ui.components.looks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Paint as ComposePaint

/*
 * E-Ink renderers:
 *   - `CardStyle.HAIRLINE`
 *   - `TileStyle.INK_OUTLINE`
 *   - `GaugeStyle.DITHER_BAR`
 *   - `SliderStyle.DITHER`
 *   - `NavStyle.UNDERLINE_ITALIC`
 *
 * An e-paper reader: strictly two-tone `einkInk` on `einkPaper` (the palette text and background,
 * never the accent), Literata serif, hairline frames and 2×2 ordered-dither fills. Dark palettes
 * invert naturally to light ink on dark paper. `LookTraits.animate = false` makes gauges snap.
 */

private val DitherCell = aynDp(1.5f)

/** 2×2 ordered-dither checker: cell ([x], [y]) is inked when x + y is even. */
internal fun eInkDitherOn(x: Int, y: Int): Boolean = (x + y) % 2 == 0

/** Tiled dither brush: a 2×2 checker scaled to [DitherCell] px per cell, built once per density. */
private fun ditherBrush(density: Density, ink: Color): ShaderBrush {
    val cell = with(density) { DitherCell.toPx() }.roundToInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(cell * 2, cell * 2)
    val canvas = ComposeCanvas(bitmap)
    val paint = ComposePaint().apply { color = ink }
    for (y in 0..1) for (x in 0..1) {
        if (eInkDitherOn(x, y)) canvas.drawRect((x * cell).toFloat(), (y * cell).toFloat(), ((x + 1) * cell).toFloat(), ((y + 1) * cell).toFloat(), paint)
    }
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

@Composable
private fun rememberDitherBrush(ink: Color): ShaderBrush {
    val density = LocalDensity.current
    return remember(density, ink) { ditherBrush(density, ink) }
}

internal object EInkCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val ink = p.lookColors.einkInk
        val surface = fill ?: if (focused) ink.copy(alpha = 0.06f) else Color.Transparent
        return base.clip(shape).background(surface).border(1.dp, ink, shape)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        return base.clip(shape).background(p.lookColors.einkPaper).border(1.dp, p.lookColors.einkInk, shape)
    }

    override fun content(p: LookPaint): Color = p.lookColors.einkInk

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier =
        Modifier.border(1.dp, p.lookColors.einkInk, p.spec.settingsTab.fpsBadgeShape)

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.einkInk

    override fun fpsBadgeStyle(p: LookPaint, base: TextStyle): TextStyle = base.copy(fontStyle = FontStyle.Italic)
}

internal object EInkTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val ink = p.lookColors.einkInk
        return if (selected) {
            base.clip(CircleShape).background(ink)
        } else {
            base.clip(CircleShape).border(1.dp, ink, CircleShape)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.einkPaper else p.lookColors.einkInk

    override fun labelColor(p: LookPaint, selected: Boolean): Color = p.lookColors.einkInk
}

internal object EInkGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val ink = p.lookColors.einkInk
        return GaugeRingColors(ink, ink, ink, ink)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val ink = p.lookColors.einkInk
        val labelStyle = tccTextStyle().copy(fontStyle = FontStyle.Italic)
        val displayStyle = tccDisplayTextStyle()
        val dither = rememberDitherBrush(ink)
        val density = LocalDensity.current
        val frame = remember(density) { Stroke(with(density) { 1.dp.toPx() }) }
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * p.spec.settingsTab.ringDiameterRatio
            val titleSp = with(density) { (side * p.spec.settingsTab.ringTitleScale).toSp() }
            val valueSp = with(density) { (side * p.spec.settingsTab.ringValueScale).toSp() }
            val unitSp = with(density) { (side * p.spec.settingsTab.ringUnitScale).toSp() }
            Column(
                modifier = Modifier.width(side),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(side * 0.05f)
            ) {
                Text(text = reading.title, color = ink, fontSize = titleSp, style = labelStyle, maxLines = 1)
                Text(text = reading.valueText, color = ink, fontSize = valueSp, style = displayStyle, maxLines = 1)
                Canvas(
                    modifier = Modifier
                        .width(side * 0.92f)
                        .height(side * 0.14f)
                ) {
                    val fraction = reading.fraction()
                    val sw = frame.width
                    val w = (size.width - sw * 2f) * abs(fraction).coerceIn(0f, 1f)
                    val x = if (fraction < 0f) size.width - sw - w else sw
                    if (w > 0f) drawRect(dither, Offset(x, sw), Size(w, size.height - sw * 2f))
                    drawRect(ink, Offset(sw / 2f, sw / 2f), Size(size.width - sw, size.height - sw), style = frame)
                }
                Text(text = reading.unitText, color = ink, fontSize = unitSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal object EInkSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier =
        Modifier.border(1.dp, p.look.lookColors.einkInk, p.look.spec.slider.trackShape)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val ink = p.look.lookColors.einkInk
        val dither = rememberDitherBrush(ink)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = 1.dp.toPx()
            val thumbW = aynDp(4f).toPx()
            val x = (p.fraction * size.width).coerceIn(thumbW / 2f, size.width - thumbW / 2f)
            if (p.fraction > 0f) {
                drawRect(dither, Offset(sw, sw), Size(x - sw, size.height - sw * 2f), alpha = if (p.dimmed) 0.4f else 1f)
            }
            drawRect(ink, Offset(x - thumbW / 2f, 0f), Size(thumbW, size.height))
        }
    }

    override fun iconTint(p: SliderPaint): Color = p.look.lookColors.einkInk

    override fun labelColor(p: SliderPaint): Color = p.look.lookColors.einkInk
}

internal object EInkNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RectangleShape

    override fun highlight(p: LookPaint): Modifier = Modifier

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        p.lookColors.einkInk.copy(alpha = if (selected) 1f else 0.6f)

    override fun labelStyle(base: TextStyle, selected: Boolean): TextStyle =
        if (selected) base.copy(fontStyle = FontStyle.Italic, textDecoration = TextDecoration.Underline) else base
}
