package jr.brian.commandpannel.ui.components.looks

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.components.hudHeaderTypeface
import jr.brian.commandpannel.ui.theme.applyLabelCase
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccDrawable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/*
 * Terminal renderers:
 *   - `CardStyle.TERMINAL_FRAME`
 *   - `TileStyle.TERMINAL_ROW`
 *   - `GaugeStyle.ASCII_BAR`
 *   - `SliderStyle.ASCII`
 *   - `NavStyle.PROMPT_INVERT`
 *   - `HeaderStyle.TITLED_FRAME`
 *
 * Text-mode console in JetBrains Mono: dashed frames with the card title knocked out of the top
 * border (`┌─ fps ───┐`), `[x] label` tile cells, `[#####---]` meters, `[====---] 62%` sliders,
 * a `>prompt` nav and a blinking `_` after the FPS readout. Everything is `terminalInk` (the accent
 * clamped to body-text contrast) on the palette background. No scanlines (unlike Tactical HUD).
 */

/** Title text size; the dashed frame's top edge runs through the middle of the title. */
private val TitleSize = aynDp(7f)
private val TitleStart = aynDp(10f)
private const val GAUGE_METER_CELLS = 8
private const val CURSOR_BLINK_MILLIS = 1000L

/** JetBrains Mono advance width relative to the font size. */
private const val MONO_ADVANCE = 0.6f

/** `[#####---]`: [cells] characters filled with [fill] in proportion to |[fraction]|. */
internal fun asciiMeter(fraction: Float, cells: Int = GAUGE_METER_CELLS, fill: Char = '#', empty: Char = '-'): String {
    val n = cells.coerceAtLeast(1)
    val lit = (abs(fraction).coerceIn(0f, 1f) * n).roundToInt().coerceIn(0, n)
    return buildString(n + 2) {
        append('[')
        repeat(lit) { append(fill) }
        repeat(n - lit) { append(empty) }
        append(']')
    }
}

/** `[=========-----] 62%`: the slider bar with a right-aligned percentage. */
internal fun asciiSliderBar(fraction: Float, cells: Int, percent: Int): String =
    asciiMeter(fraction, cells, fill = '=', empty = '-') + " " + percent.coerceIn(0, 100).toString().padStart(3) + "%"

/** Bar cells that fit [widthChars] monospaced characters next to brackets and ` 100%`. */
internal fun asciiSliderCells(widthChars: Int): Int = (widthChars - 2 - 5).coerceIn(4, 40)

/** `[x] label` when on, `[ ] label` when off; [label] already carries the look's casing. */
internal fun terminalTileLabel(label: String, on: Boolean): String = (if (on) "[x] " else "[ ] ") + label

/** Nav prompt: the selected item reads `>settings`, others keep the prompt column blank. */
internal fun terminalNavLabel(label: String, selected: Boolean): String = (if (selected) ">" else " ") + label

private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }

/** Dashed frame whose top edge sits [TitleSize] / 2 below the top so the title fits inside bounds. */
private fun Modifier.terminalFrame(ink: Color, fill: Color?): Modifier = drawWithCache {
    val sw = 1.dp.toPx()
    val top = TitleSize.toPx() / 2f
    val dash = aynDp(3f).toPx()
    val stroke = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f)))
    val topLeft = Offset(sw / 2f, top)
    val frameSize = Size(size.width - sw, size.height - top - sw / 2f)
    onDrawBehind {
        if (fill != null) drawRect(fill, topLeft, frameSize)
        drawRect(ink, topLeft, frameSize, style = stroke)
    }
}

/** FPS readout followed by a blinking `_`; [visible] flips once per second. */
private class TerminalReadoutPainter(private val visible: State<Boolean>) : ReadoutPainter {
    override fun drawAfter(canvas: NativeCanvas, endX: Float, baseline: Float, paint: Paint) {
        if (visible.value) canvas.drawText("_", endX + paint.textSize * 0.12f, baseline, paint)
    }
}

internal object TerminalCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val ink = p.lookColors.terminalInk
        return base.terminalFrame(ink.copy(alpha = 0.75f), fill ?: if (focused) ink.copy(alpha = 0.08f) else null)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier =
        base.background(p.colors.dialogBackground).terminalFrame(p.lookColors.terminalInk.copy(alpha = 0.75f), null)

    override fun content(p: LookPaint): Color = p.lookColors.terminalInk

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier =
        Modifier.background(p.lookColors.terminalInk.copy(alpha = if (highRate) 1f else 0.14f))

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color =
        if (highRate) p.colors.primaryBackground else p.lookColors.terminalInk

    @Composable
    override fun rememberReadoutPainter(p: LookPaint): ReadoutPainter {
        // A once-per-second toggle instead of an infinite transition, which would tick the frame
        // clock at 120 Hz for a single character. Leaving composition (overlay hidden) stops it.
        val visible = remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(CURSOR_BLINK_MILLIS)
                visible.value = !visible.value
            }
        }
        return remember(visible) { TerminalReadoutPainter(visible) }
    }
}

@Composable
private fun TerminalTileCell(state: TileContentState) {
    val p = state.paint
    val ink = p.lookColors.terminalInk
    val bg = p.colors.primaryBackground
    val fg = if (state.selected) bg else ink
    val style = state.labelStyle.copy(fontSize = p.spec.tile.qsLabelFontSize)
    val text = terminalTileLabel(state.label, state.selected)
    val glyph = if (state.selected) "[x]" else "[ ]"
    val measurer = rememberTextMeasurer()
    val textWidthPx = remember(text, style) { measurer.measure(text, style, maxLines = 1).size.width }
    val iconSize = state.iconBoxSize * 0.6f
    val gap = aynDp(3f)
    val pad = aynDp(3f)
    BoxWithConstraints(
        modifier = Modifier
            .width(state.columnWidth)
            .height(state.tileBoxSize * 0.62f)
            .background(if (state.selected) ink else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        val availablePx = with(LocalDensity.current) { (maxWidth - iconSize - gap - pad * 2).toPx() }
        Row(
            modifier = Modifier.padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            if (state.iconBitmap != null) {
                Image(bitmap = state.iconBitmap, contentDescription = state.label, modifier = Modifier.size(iconSize), contentScale = ContentScale.Fit)
            } else {
                Image(
                    painter = painterResource(id = tccDrawable(state.iconRes)),
                    contentDescription = state.label,
                    colorFilter = ColorFilter.tint(fg),
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit
                )
            }
            Text(text = if (textWidthPx <= availablePx) text else glyph, color = fg, style = style, maxLines = 1)
        }
    }
}

internal object TerminalTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier = base

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.colors.primaryBackground else p.lookColors.terminalInk

    override fun labelColor(p: LookPaint, selected: Boolean): Color = p.lookColors.terminalInk

    override val content: (@Composable (TileContentState) -> Unit) = { TerminalTileCell(it) }
}

internal object TerminalGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val ink = p.lookColors.terminalInk
        return GaugeRingColors(ink, ink, ink, ink)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val style = tccDisplayTextStyle()
        val negative = p.colors.error
        // Only the discrete fill count recomposes, not every animation frame.
        val meter by remember(reading) { derivedStateOf { asciiMeter(reading.fraction()) } }
        val isNegative by remember(reading) { derivedStateOf { reading.fraction() < 0f } }
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            // Fit the 10-character meter across the cell and three lines down it.
            val fontDp = min(maxWidth * 0.94f / ((GAUGE_METER_CELLS + 2) * MONO_ADVANCE), maxHeight * 0.24f)
            val fontSize = with(density) { fontDp.toSp() }
            val ink = reading.color
            Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(fontDp * 0.15f)) {
                Text(text = reading.title, color = ink.copy(alpha = 0.7f), fontSize = fontSize, style = style, maxLines = 1)
                Text(text = meter, color = if (isNegative) negative else ink, fontSize = fontSize, style = style, maxLines = 1)
                Text(text = "${reading.valueText} ${reading.unitText}", color = ink, fontSize = fontSize, style = style, maxLines = 1)
            }
        }
    }
}

internal object TerminalSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val ink = p.look.lookColors.terminalInk
        val style = tccDisplayTextStyle()
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            val density = LocalDensity.current
            val fontDp = maxHeight * 0.5f
            val chars = floor(maxWidth / (fontDp * MONO_ADVANCE)).toInt()
            Text(
                text = asciiSliderBar(p.fraction, asciiSliderCells(chars), p.displayProgress),
                color = if (p.dimmed) ink.copy(alpha = 0.45f) else ink,
                fontSize = with(density) { fontDp.toSp() },
                style = style,
                maxLines = 1
            )
        }
    }

    override fun iconTint(p: SliderPaint): Color = p.look.lookColors.terminalInk

    override fun labelColor(p: SliderPaint): Color = p.look.lookColors.terminalInk

    override val showIcon: Boolean get() = false
}

internal object TerminalNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RectangleShape

    override fun highlight(p: LookPaint): Modifier = Modifier.background(p.lookColors.terminalInk)

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.colors.primaryBackground else p.lookColors.terminalInk.copy(alpha = 0.7f)

    // Nav shows labels only (no icon / no highlight fill), so the selected label must read on the dark backdrop.
    override fun textColor(p: LookPaint, selected: Boolean): Color =
        if (selected) Color.White else p.lookColors.terminalInk.copy(alpha = 0.7f)

    override fun label(text: String, selected: Boolean): String = terminalNavLabel(text, selected)

    override val showIcon: Boolean get() = false

    override val labelScale: Float get() = 1.6f
}

/**
 * [jr.brian.commandpannel.ui.theme.HeaderStyle.TITLED_FRAME]: [title] knocked
 * out of the frame's top border (background-colored box behind the text), like `┌─ fps ───┐`.
 * Uses the HUD header typeface (bundled JetBrains Mono) once it is loaded.
 */
internal fun Modifier.terminalTitledFrameHeader(p: LookPaint, title: String?): Modifier {
    if (title.isNullOrEmpty()) return this
    val text = " ${p.spec.traits.applyLabelCase(title)} "
    val ink = p.lookColors.terminalInk.toArgb()
    val knockout = p.colors.primaryBackground
    return drawWithContent {
        drawContent()
        val paint = titlePaint
        paint.typeface = hudHeaderTypeface ?: Typeface.MONOSPACE
        paint.textSize = TitleSize.toPx()
        paint.color = ink
        val x = TitleStart.toPx()
        val width = paint.measureText(text)
        drawRect(knockout, Offset(x, 0f), Size(width, TitleSize.toPx()))
        val fm = paint.fontMetrics
        val baseline = TitleSize.toPx() / 2f - (fm.ascent + fm.descent) / 2f
        drawIntoCanvas { it.nativeCanvas.drawText(text, x, baseline, paint) }
    }
}
