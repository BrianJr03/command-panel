package jr.brian.commandpannel.ui.components.looks

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import jr.brian.commandpannel.ui.components.RingGaugeLayout
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.contrastingContentColor

/*
 * Soft Console renderers:
 *   - `CardStyle.SOFT_EXTRUDED`
 *   - `TileStyle.SOFT_PRESS`
 *   - `GaugeStyle.INSET_RING`
 *   - `SliderStyle.GROOVE`
 *   - `NavStyle.PRESSED`
 *
 * Neumorphic "putty": every surface is filled with `TccLookColors.softSurface` (the backdrop
 * color, via `LookTraits.liftedBackdrop`) and separated only by a dark bottom-right / light
 * top-left soft shadow pair. Selection presses the element into the surface (inner shadows).
 */

private val CardShadowOffset = aynDp(4f)
private val CardShadowBlur = aynDp(9f)
private val TileShadowOffset = aynDp(2.5f)
private val TileShadowBlur = aynDp(5f)
private val InsetOffset = aynDp(2f)
private val InsetBlur = aynDp(4f)

/**
 * Raised surface: [shape] filled with [surface], casting a [dark] shadow toward the bottom-right
 * and a [light] highlight toward the top-left. Paths and paints are cached per size.
 */
internal fun Modifier.softExtruded(
    shape: Shape,
    surface: Color,
    dark: Color,
    light: Color,
    offset: Dp,
    blur: Dp
): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        .asAndroidPath()
    val o = offset.toPx()
    val b = blur.toPx()
    val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surface.toArgb()
        setShadowLayer(b, -o, -o, light.toArgb())
    }
    val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surface.toArgb()
        setShadowLayer(b, o, o, dark.toArgb())
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawPath(path, lightPaint)
            canvas.nativeCanvas.drawPath(path, darkPaint)
        }
    }
}

/**
 * Pressed-in surface: inner shadows inside [shape] (dark from the top-left, light from the
 * bottom-right). Compose has no inset shadow, so a frame around the shape casts its shadow
 * inward while the canvas is clipped to the shape.
 */
internal fun Modifier.softInset(
    shape: Shape,
    surface: Color,
    dark: Color,
    light: Color,
    offset: Dp = InsetOffset,
    blur: Dp = InsetBlur
): Modifier = drawWithCache {
    val shapePath = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val o = offset.toPx()
    val b = blur.toPx()
    val spread = b * 2f + o
    val frame = Path().apply {
        addRect(Rect(-spread, -spread, size.width + spread, size.height + spread))
        addPath(shapePath)
        fillType = PathFillType.EvenOdd
    }.asAndroidPath()
    val clip = shapePath.asAndroidPath()
    val surfaceArgb = surface.toArgb()
    val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surfaceArgb
        setShadowLayer(b, o, o, dark.toArgb())
    }
    val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surfaceArgb
        setShadowLayer(b, -o, -o, light.toArgb())
    }
    onDrawBehind {
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.clipPath(clip)
            nc.drawColor(surfaceArgb)
            nc.drawPath(frame, darkPaint)
            nc.drawPath(frame, lightPaint)
            nc.restore()
        }
    }
}

private fun Modifier.softRaised(p: LookPaint, shape: Shape, offset: Dp, blur: Dp, surface: Color = p.lookColors.softSurface) =
    softExtruded(shape, surface, p.lookColors.softShadowDark, p.lookColors.softShadowLight, offset, blur)

private fun Modifier.softPressed(p: LookPaint, shape: Shape) =
    softInset(shape, p.lookColors.softSurface, p.lookColors.softShadowDark, p.lookColors.softShadowLight)

/** Start angle and sweep of the 270° Soft Console ring; bidirectional values grow from 12 o'clock. */
internal fun softRingSweep(fraction: Float, bidirectional: Boolean): Pair<Float, Float> =
    if (bidirectional) -90f to fraction.coerceIn(-1f, 1f) * 135f else 135f to fraction.coerceIn(0f, 1f) * 270f

/** Slider thumb center: follows the fill end but never leaves the track. */
internal fun softThumbCenterX(fraction: Float, trackWidth: Float, radius: Float): Float =
    (fraction.coerceIn(0f, 1f) * trackWidth).coerceIn(radius, (trackWidth - radius).coerceAtLeast(radius))

internal object SoftConsoleCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val surface = fill ?: if (focused) lerp(p.lookColors.softSurface, p.lookColors.softAccent, 0.06f) else p.lookColors.softSurface
        return base.softRaised(p, shape, CardShadowOffset, CardShadowBlur, surface).clip(shape)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        return base.softRaised(p, shape, CardShadowOffset, CardShadowBlur).clip(shape)
    }

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier =
        Modifier.softPressed(p, p.spec.settingsTab.fpsBadgeShape)

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.softAccent
}

internal object SoftConsoleTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier =
        if (selected) {
            base.clip(CircleShape).softPressed(p, CircleShape)
        } else {
            base.softRaised(p, CircleShape, TileShadowOffset, TileShadowBlur).clip(CircleShape)
        }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.softAccent else p.colors.primary

    override fun labelColor(p: LookPaint, selected: Boolean): Color? =
        if (selected) p.colors.primary else p.colors.secondary
}

internal object SoftConsoleGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val a = p.lookColors.softAccent
        return GaugeRingColors(a, a, a, a)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val density = LocalDensity.current
        val p = currentLookPaint()
        val strokes = remember(density) {
            with(density) {
                val arc = aynDp(5f).toPx()
                Triple(
                    Stroke(arc, cap = StrokeCap.Round),
                    Stroke(arc * 1.8f, cap = StrokeCap.Round),
                    arc * 0.18f
                )
            }
        }
        val (arcStroke, grooveStroke, rimShift) = strokes
        val grooveDark = p.lookColors.softShadowDark
        val grooveLight = p.lookColors.softShadowLight
        val groove = lerp(p.lookColors.softSurface, grooveDark.copy(alpha = 1f), 0.35f)
        RingGaugeLayout(reading, modifier) { fraction ->
            val inset = grooveStroke.width / 2f + rimShift
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            // Groove: light rim offset down-right, then the darker channel on top.
            drawArc(grooveLight, 135f, 270f, false, Offset(inset + rimShift, inset + rimShift), arcSize, style = grooveStroke)
            drawArc(grooveDark, 135f, 270f, false, Offset(inset - rimShift, inset - rimShift), arcSize, style = grooveStroke)
            drawArc(groove, 135f, 270f, false, topLeft, arcSize, style = grooveStroke)
            val (start, sweep) = softRingSweep(fraction, reading.bidirectional)
            if (sweep != 0f) {
                drawArc(reading.color, start, sweep, false, topLeft, arcSize, style = arcStroke)
            }
        }
    }
}

internal object SoftConsoleSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier.softPressed(p.look, p.look.spec.slider.trackShape)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val look = p.look
        val fillColor = if (p.dimmed) p.fillColor else look.lookColors.softAccent
        val thumbSurface = look.lookColors.softSurface
        val shadowDark = look.lookColors.softShadowDark
        val shadowLight = look.lookColors.softShadowLight
        val density = LocalDensity.current
        val thumbPaints = remember(thumbSurface, shadowDark, shadowLight, density) {
            with(density) {
                val o = aynDp(1.5f).toPx()
                val b = aynDp(3f).toPx()
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = thumbSurface.toArgb()
                    setShadowLayer(b, -o, -o, shadowLight.toArgb())
                } to Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = thumbSurface.toArgb()
                    setShadowLayer(b, o, o, shadowDark.toArgb())
                }
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = aynDp(3f).toPx()
            val h = size.height - pad * 2f
            val r = h / 2f
            val cx = softThumbCenterX(p.fraction, size.width - pad * 2f, r) + pad
            if (p.fraction > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(pad, pad),
                    size = Size(cx - pad, h),
                    cornerRadius = CornerRadius(r, r)
                )
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawCircle(cx, size.height / 2f, r * 0.92f, thumbPaints.first)
                canvas.nativeCanvas.drawCircle(cx, size.height / 2f, r * 0.92f, thumbPaints.second)
            }
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.softAccent.contrastingContentColor() else p.look.colors.primary
}

internal object SoftConsoleNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(14f))

    override fun highlight(p: LookPaint): Modifier = Modifier.clip(highlightShape).softPressed(p, highlightShape)

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.softAccent else p.colors.secondary
}
