package jr.brian.commandpannel.ui.components

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.theme.AuroraGradientPreset
import jr.brian.commandpannel.ui.theme.BackdropStyle
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.HeaderStyle
import jr.brian.commandpannel.ui.components.looks.LookCardRenderer
import jr.brian.commandpannel.ui.components.looks.LookPaint
import jr.brian.commandpannel.ui.components.looks.gradientWashBackdrop
import jr.brian.commandpannel.ui.components.looks.honeycombBackdrop
import jr.brian.commandpannel.ui.components.looks.starfieldBackdrop
import jr.brian.commandpannel.ui.components.looks.topographicBackdrop
import jr.brian.commandpannel.ui.components.looks.LookTileRenderer
import jr.brian.commandpannel.ui.components.looks.halftoneBackdrop
import jr.brian.commandpannel.ui.components.looks.lcdPixelGridBackdrop
import jr.brian.commandpannel.ui.components.looks.renderer
import jr.brian.commandpannel.ui.components.looks.synthGridBackdrop
import jr.brian.commandpannel.ui.components.looks.terminalTitledFrameHeader
import jr.brian.commandpannel.ui.theme.TccColorPalette
import jr.brian.commandpannel.ui.theme.TccLookColors
import jr.brian.commandpannel.ui.theme.TccLookSpec
import jr.brian.commandpannel.ui.theme.TileStyle
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.withHueShift
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/** Rectangle with the top-left and bottom-right corners cut at 45° (Tactical HUD frames). */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceIn(0f, min(size.width, size.height) * 0.4f)
        if (c < 1f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(0f, size.height)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Superellipse ("squircle") outline with continuous curvature (Aurora Glass tiles).
 * [radiusFraction] is the corner extent relative to the shorter side (`0.5f` ≈ full squircle); [maxRadius], when set,
 * caps it so the corners stop growing on large surfaces.
 */
class SquircleShape(private val radiusFraction: Float = 0.30f, private val maxRadius: Dp = Dp.Unspecified) : Shape {
    /** The same squircle with its corners resolved as if the shorter side were at most [shortSide]. */
    fun capped(shortSide: Dp): SquircleShape = SquircleShape(radiusFraction, shortSide * radiusFraction.coerceIn(0f, 0.5f))

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val cap = if (maxRadius == Dp.Unspecified) Float.MAX_VALUE else with(density) { maxRadius.toPx() }
        val r = (min(w, h) * radiusFraction.coerceIn(0f, 0.5f)).coerceAtMost(cap)
        if (r < 1f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val path = Path()
        val steps = 12
        val exponent = 2f / 4f
        var first = true
        fun corner(cx: Float, cy: Float, startDeg: Float) {
            for (i in 0..steps) {
                val t = Math.toRadians((startDeg + 90f * i / steps).toDouble())
                val cos = kotlin.math.cos(t).toFloat()
                val sin = kotlin.math.sin(t).toFloat()
                val x = cx + r * sign(cos) * abs(cos).pow(exponent)
                val y = cy + r * sign(sin) * abs(sin).pow(exponent)
                if (first) path.moveTo(x, y) else path.lineTo(x, y)
                first = false
            }
        }
        // Corners walked clockwise, starting at the right end of the top edge.
        corner(w - r, r, 270f)
        corner(w - r, h - r, 0f)
        corner(r, h - r, 90f)
        corner(r, r, 180f)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Organic "cookie" blob (Bloom selected tiles): [lobes] soft scallops whose valleys sit
 * [depth] of the radius inside the outline, joined by Catmull-Rom cubic curves so the edge stays
 * continuous. [rotationDegrees] turns the lobes.
 */
class BloomBlobShape(
    private val lobes: Int = 7,
    private val depth: Float = 0.12f,
    private val rotationDegrees: Float = 0f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(bloomBlobPath(size, lobes, depth, rotationDegrees))
}

/**
 * Alternating lobe-peak / valley vertices of a [BloomBlobShape] as `[x0, y0, x1, y1, ...]`.
 * Peaks sit on the inscribed circle, valleys [depth] of the radius inside it.
 */
internal fun bloomBlobVertices(size: Size, lobes: Int, depth: Float, rotationDegrees: Float): FloatArray =
    radialVertices(size, lobes.coerceAtLeast(3) * 2, 1f - depth.coerceIn(0f, 0.5f), rotationDegrees)

/** Starburst spike / notch vertices as `[x0, y0, x1, y1, ...]`, first spike at 12 o'clock. */
internal fun starburstVertices(size: Size, points: Int, innerRadiusRatio: Float): FloatArray =
    radialVertices(size, points.coerceAtLeast(3) * 2, innerRadiusRatio.coerceIn(0.1f, 1f), 0f)

private fun radialVertices(size: Size, count: Int, innerRatio: Float, rotationDegrees: Float): FloatArray {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = min(size.width, size.height) / 2f
    val out = FloatArray(count * 2)
    for (i in 0 until count) {
        val a = Math.toRadians((rotationDegrees - 90f + 360f * i / count).toDouble())
        val radius = if (i % 2 == 0) r else r * innerRatio
        out[i * 2] = cx + radius * kotlin.math.cos(a).toFloat()
        out[i * 2 + 1] = cy + radius * kotlin.math.sin(a).toFloat()
    }
    return out
}

internal fun bloomBlobPath(size: Size, lobes: Int, depth: Float, rotationDegrees: Float): Path {
    val v = bloomBlobVertices(size, lobes, depth, rotationDegrees)
    val n = v.size / 2
    val xs = FloatArray(n) { v[it * 2] }
    val ys = FloatArray(n) { v[it * 2 + 1] }
    val path = Path()
    path.moveTo(xs[0], ys[0])
    for (i in 0 until n) {
        val p0 = (i - 1 + n) % n
        val p2 = (i + 1) % n
        val p3 = (i + 2) % n
        path.cubicTo(
            xs[i] + (xs[p2] - xs[p0]) / 6f, ys[i] + (ys[p2] - ys[p0]) / 6f,
            xs[p2] - (xs[p3] - xs[i]) / 6f, ys[p2] - (ys[p3] - ys[i]) / 6f,
            xs[p2], ys[p2]
        )
    }
    path.close()
    return path
}

/**
 * Comic starburst: [points] spikes alternating between the outer radius and
 * [innerRadiusRatio] of it, first spike at 12 o'clock.
 */
class StarburstShape(
    private val points: Int = 8,
    private val innerRadiusRatio: Float = 0.62f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val v = starburstVertices(size, points, innerRadiusRatio)
        val path = Path()
        path.moveTo(v[0], v[1])
        for (i in 1 until v.size / 2) path.lineTo(v[i * 2], v[i * 2 + 1])
        path.close()
        return Outline.Generic(path)
    }
}

/** HUD card fill: background tinted by the line accent. */
private fun hudPanel(colors: TccColorPalette, lookColors: TccLookColors, focused: Boolean): Color =
    lerp(colors.primaryBackground, lookColors.lineAccent, if (focused) 0.14f else 0.045f)

/**
 * Draws a soft colored glow behind [shape] using a hardware-accelerated shadow layer.
 * Kept to small elements (selected tiles, gauge arcs, slider fills) to stay cheap.
 */
fun DrawScope.drawShapeGlow(shape: Shape, color: Color, radius: Dp = 10.dp) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val radiusPx = radius.toPx()
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            setShadowLayer(radiusPx, 0f, radiusPx * 0.25f, color.copy(alpha = 0.75f).toArgb())
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/**
 * Paints an overlay panel card for the active look through its [CardStyle] renderer, then the
 * optional [HeaderStyle] decoration. [CardStyle.SOLID] is byte-for-byte the original
 * `clip(panelCardShape).background(panelBackground)` path used by AYN and Retroid. [fill]
 * overrides the fill color (Cartridge accent-filled cards); [header] is an indexed `[01] LABEL`
 * caption (Tactical HUD) and [frameTitle] a short title (Terminal titled frames); neither affects
 * layout.
 */
fun Modifier.tccCard(
    spec: TccLookSpec,
    colors: TccColorPalette,
    lookColors: TccLookColors,
    focused: Boolean = false,
    fill: Color? = null,
    shape: Shape = spec.panelCardShape,
    header: String? = null,
    // Short card title (`fps`, `temp`, ...) drawn only by `HeaderStyle.TITLED_FRAME`.
    frameTitle: String? = null
): Modifier {
    val paint = LookPaint(spec, colors, lookColors)
    val card = spec.traits.card.renderer().card(this, paint, focused, fill, shape, header, frameTitle)
    return when (spec.traits.header) {
        // Drawn by their card renderers (Cartridge strip, HUD caption).
        HeaderStyle.NONE, HeaderStyle.INK_STRIP, HeaderStyle.INDEXED_MONO -> card
        HeaderStyle.TITLED_FRAME -> card.terminalTitledFrameHeader(paint, frameTitle)
    }
}

/** Card renderers of the original looks; newer looks live in `ui/components/looks/`. */
internal class BuiltInCardRenderer private constructor(private val style: CardStyle) : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val spec = p.spec
        val colors = p.colors
        val lookColors = p.lookColors
        val solid = fill ?: if (focused) colors.panelBackgroundSelected else colors.panelBackground
        return when (style) {
            CardStyle.GLASS -> base.clip(shape)
                .background(fill ?: if (focused) lookColors.glassFillFocused else lookColors.glassFill)
                .border(1.dp, lookColors.glassRim, shape)
                .drawBehind {
                    val band = aynDp(2.5f).toPx()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to lookColors.glassHighlight,
                            1f to Color.Transparent,
                            startY = 0f,
                            endY = band
                        ),
                        size = Size(size.width, band)
                    )
                }
            CardStyle.BRUTALIST -> {
                val offset = spec.traits.shadowOffset
                base.padding(end = offset, bottom = offset)
                    .drawBehind {
                        val o = offset.toPx()
                        drawRect(color = lookColors.shadow, topLeft = Offset(o, o), size = size)
                    }
                    .background(solid, shape)
                    .border(spec.traits.outlineWidth, lookColors.outline, shape)
            }
            CardStyle.CHAMFER_HUD -> base.clip(shape)
                .background(fill ?: hudPanel(colors, lookColors, focused))
                .border(1.dp, lookColors.lineAccent.copy(alpha = 0.38f), shape)
                .drawWithContent {
                    drawContent()
                    drawHudBrackets(lookColors.lineAccent)
                    if (header != null) drawHudHeader(header, lookColors.lineAccent)
                }
            else -> base.clip(shape).background(solid)
        }
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val spec = p.spec
        val colors = p.colors
        val lookColors = p.lookColors
        val shape = spec.dialog.dialogShape
        return when (style) {
            CardStyle.GLASS -> base.clip(shape)
                .background(colors.dialogBackground.copy(alpha = 0.94f))
                .border(1.dp, lookColors.glassRim, shape)
            CardStyle.BRUTALIST -> base.padding(end = spec.traits.shadowOffset, bottom = spec.traits.shadowOffset)
                .drawBehind {
                    val o = spec.traits.shadowOffset.toPx()
                    drawRect(color = lookColors.shadow, topLeft = Offset(o, o), size = size)
                }
                .background(colors.dialogBackground, shape)
                .border(spec.traits.outlineWidth, lookColors.outline, shape)
            CardStyle.CHAMFER_HUD -> base.clip(shape)
                .background(lerp(colors.dialogBackground, lookColors.lineAccent, 0.05f))
                .border(1.dp, lookColors.lineAccent.copy(alpha = 0.6f), shape)
                .drawWithContent {
                    drawContent()
                    drawHudBrackets(lookColors.lineAccent)
                }
            else -> base.clip(shape).background(colors.dialogBackground)
        }
    }

    override fun content(p: LookPaint): Color? =
        if (style == CardStyle.CHAMFER_HUD) p.lookColors.lineAccent else null

    companion object {
        val Solid = BuiltInCardRenderer(CardStyle.SOLID)
        val Glass = BuiltInCardRenderer(CardStyle.GLASS)
        val Brutalist = BuiltInCardRenderer(CardStyle.BRUTALIST)
        val ChamferHud = BuiltInCardRenderer(CardStyle.CHAMFER_HUD)
    }
}

private fun DrawScope.drawHudBrackets(color: Color) {
    val len = aynDp(13f).toPx()
    val sw = aynDp(1.8f).toPx()
    val h = sw / 2f
    // Top-right
    drawLine(color, Offset(size.width - len, h), Offset(size.width, h), sw)
    drawLine(color, Offset(size.width - h, 0f), Offset(size.width - h, len), sw)
    // Bottom-left
    drawLine(color, Offset(0f, size.height - h), Offset(len, size.height - h), sw)
    drawLine(color, Offset(h, size.height - len), Offset(h, size.height), sw)
}

private val hudHeaderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    typeface = Typeface.MONOSPACE
    letterSpacing = 0.06f
}

/** Bundled JetBrains Mono typeface for canvas card headers (HUD, Terminal); `null` until loaded. */
internal var hudHeaderTypeface: Typeface? = null
    private set

/** Bundled JetBrains Mono for HUD card headers, loaded once; falls back to the system monospace. */
fun setHudHeaderTypeface(typeface: Typeface?) {
    hudHeaderTypeface = typeface
    hudHeaderPaint.typeface = typeface ?: Typeface.MONOSPACE
}

private fun DrawScope.drawHudHeader(text: String, color: Color) {
    val paint = hudHeaderPaint
    paint.color = color.copy(alpha = 0.62f).toArgb()
    paint.textSize = aynDp(5.6f).toPx()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(text, aynDp(13f).toPx(), aynDp(9f).toPx(), paint)
    }
}

/** Dialog surface for the active look (more opaque than cards so content behind never bleeds). */
fun Modifier.tccDialogSurface(
    spec: TccLookSpec,
    colors: TccColorPalette,
    lookColors: TccLookColors
): Modifier = spec.traits.card.renderer().dialog(this, LookPaint(spec, colors, lookColors))

/** Fill color of a selected Cartridge tile: every third tile alternates to the second accent. */
fun cartridgeSelectedFill(colors: TccColorPalette, lookColors: TccLookColors, index: Int): Color =
    if (index % 3 == 2) lookColors.accent2 else colors.accent

/**
 * Paints a tile button for the active look. [TileStyle.STOCK] / [TileStyle.PIXEL] reproduce the
 * original AYN / Retroid `clip(buttonShape).tccTileBackground(...)` exactly. [bare] renders no
 * tile chrome (nav items whose selection is drawn around the whole column instead).
 */
fun Modifier.tccTile(
    spec: TccLookSpec,
    colors: TccColorPalette,
    lookColors: TccLookColors,
    selected: Boolean,
    index: Int = 0,
    bare: Boolean = false
): Modifier {
    if (bare) return this
    return spec.traits.tile.renderer().tile(this, LookPaint(spec, colors, lookColors), selected, index)
}

/** Icon tint matching [tccTile]. */
fun lookTileIconTint(
    spec: TccLookSpec,
    colors: TccColorPalette,
    lookColors: TccLookColors,
    selected: Boolean,
    index: Int = 0
): Color = spec.traits.tile.renderer().iconTint(LookPaint(spec, colors, lookColors), selected, index)

/** Tile renderers of the original looks; newer looks live in `ui/components/looks/`. */
internal class BuiltInTileRenderer private constructor(private val style: TileStyle) : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val spec = p.spec
        val colors = p.colors
        val lookColors = p.lookColors
        val shape = spec.tile.buttonShape
        return when (style) {
            TileStyle.PIXEL -> base.clip(shape).tccTileBackground(colors, true, selected)
            TileStyle.GLASS_GLOW -> if (selected) {
                base.drawBehind { drawShapeGlow(shape, colors.accent.copy(alpha = 0.55f), 9.dp) }
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            0f to lerp(colors.accent, Color.White, 0.18f),
                            1f to lerp(colors.accent, colors.accent.withHueShift(-35f), 0.45f)
                        )
                    )
            } else {
                base.clip(shape)
                    .background(lookColors.glassFill)
                    .border(1.dp, lookColors.glassRim, shape)
            }
            TileStyle.BRUTALIST -> {
                val offset = spec.traits.shadowOffset * 0.66f
                base.drawBehind {
                    val o = offset.toPx()
                    drawRect(color = lookColors.shadow, topLeft = Offset(o, o), size = size)
                }
                    .background(if (selected) cartridgeSelectedFill(colors, lookColors, index) else colors.panelBackground)
                    .border(spec.traits.outlineWidth, lookColors.outline, RectangleShape)
            }
            TileStyle.HUD -> if (selected) {
                base.drawBehind { drawShapeGlow(shape, lookColors.lineAccent.copy(alpha = 0.5f), 7.dp) }
                    .clip(shape)
                    .background(lookColors.lineAccent)
            } else {
                base.clip(shape)
                    .background(colors.primaryBackground)
                    .border(1.dp, lookColors.lineAccent.copy(alpha = 0.50f), shape)
            }
            else -> base.clip(shape).tccTileBackground(colors, false, selected)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color {
        val colors = p.colors
        val lookColors = p.lookColors
        return when (style) {
            TileStyle.PIXEL -> tccTileIconTint(colors, true, selected)
            TileStyle.GLASS_GLOW -> if (selected) lookColors.onAccent else colors.primary
            TileStyle.BRUTALIST -> if (selected) {
                if (index % 3 == 2) lookColors.onAccent2 else lookColors.onAccent
            } else {
                colors.primary
            }
            TileStyle.HUD -> if (selected) colors.primaryBackground else lookColors.lineAccent
            else -> tccTileIconTint(colors, false, selected)
        }
    }

    override fun labelColor(p: LookPaint, selected: Boolean): Color? =
        if (style == TileStyle.HUD) p.lookColors.lineAccent.copy(alpha = if (selected) 1f else 0.62f) else null

    companion object {
        val Stock = BuiltInTileRenderer(TileStyle.STOCK)
        val Pixel = BuiltInTileRenderer(TileStyle.PIXEL)
        val GlassGlow = BuiltInTileRenderer(TileStyle.GLASS_GLOW)
        val Brutalist = BuiltInTileRenderer(TileStyle.BRUTALIST)
        val Hud = BuiltInTileRenderer(TileStyle.HUD)
    }
}

/**
 * Overlay root backdrop for the active look: solid background plus an optional static aurora,
 * paper-dot or HUD-grid layer, and (Tactical HUD) a draw-only scanline overlay above content.
 * [windowBlur] is true when the overlay window is blurring what's behind it (a [jr.brian.commandpannel.ui.theme.LookTraits.blurBehind] look); the
 * background and aurora then turn translucent so the blurred screen shows through. [scanlines] false leaves the
 * scanline overlay to [tccLookScanlines] (the overlay draws it on another layer when a background image sits between).
 */
fun Modifier.tccLookBackdrop(
    spec: TccLookSpec,
    colors: TccColorPalette,
    lookColors: TccLookColors,
    // Aurora blob colors (top-left, right, bottom); `null` derives them from the palette accent.
    auroraColors: List<Color>? = null,
    windowBlur: Boolean = false,
    scanlines: Boolean = true,
): Modifier {
    val paint = LookPaint(spec, colors, lookColors)
    val seeThrough = windowBlur && spec.traits.blurBehind
    val fill = when {
        spec.traits.oledBackdrop && colors.isDark -> Color.Black
        spec.traits.liftedBackdrop -> lookColors.softSurface
        else -> colors.primaryBackground
    }
    // A tint over the blurred screen keeps text readable on any wallpaper.
    val base = background(if (seeThrough) fill.copy(alpha = spec.traits.blurTint) else fill)
    val withLayer = when (spec.traits.backdrop) {
        BackdropStyle.SOLID -> base
        BackdropStyle.AURORA -> base.drawBehind {
            val blobAlpha = when {
                lookColors.isPureBlack -> 0.30f
                colors.isDark -> 0.62f
                else -> 0.40f
            } * if (seeThrough) 0.6f else 1f
            val blobColors = auroraColors?.takeIf { it.size >= 3 }
                ?: AuroraGradientPreset.MATCH_ACCENT.resolveColors(colors.accent)
            val blobs = listOf(
                Triple(Offset(size.width * 0.18f, size.height * 0.12f), blobColors[0], 0.55f),
                Triple(Offset(size.width * 0.88f, size.height * 0.55f), blobColors[1], 0.50f),
                Triple(Offset(size.width * 0.38f, size.height * 1.02f), blobColors[2], 0.48f)
            )
            blobs.forEach { (center, color, radiusFrac) ->
                val radius = size.minDimension * radiusFrac * 1.4f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = blobAlpha),
                        0.55f to color.copy(alpha = blobAlpha * 0.35f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
        BackdropStyle.PAPER_DOTS -> base.drawBehind {
            if (lookColors.isPureBlack) return@drawBehind
            val step = aynDp(10f).toPx()
            val dot = aynDp(0.7f).toPx()
            val dotColor = colors.primary.copy(
                alpha = (if (colors.isDark) 0.10f else 0.12f) * spec.traits.backdropPatternAlpha
            )
            var y = step / 2f
            while (y < size.height) {
                var x = step / 2f
                while (x < size.width) {
                    drawCircle(dotColor, dot, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        BackdropStyle.HUD_GRID -> base.drawBehind {
            val step = aynDp(17f).toPx()
            val sw = 1.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(lookColors.gridLine, Offset(x, 0f), Offset(x, size.height), sw)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lookColors.gridLine, Offset(0f, y), Offset(size.width, y), sw)
                y += step
            }
        }
        BackdropStyle.LCD_PIXEL_GRID -> base.lcdPixelGridBackdrop(paint)
        BackdropStyle.SYNTH_GRID -> base.synthGridBackdrop(paint)
        BackdropStyle.HALFTONE -> base.halftoneBackdrop(paint)
        BackdropStyle.GRADIENT_WASH -> base.gradientWashBackdrop(paint)
        BackdropStyle.TOPOGRAPHIC -> base.topographicBackdrop(paint)
        BackdropStyle.HONEYCOMB -> base.honeycombBackdrop(paint)
        BackdropStyle.STARFIELD -> base.starfieldBackdrop(paint)
    }
    return if (scanlines) withLayer.tccLookScanlines(spec, colors) else withLayer
}

/** The look's scanline overlay (Tactical HUD), drawn over the content; a no-op for looks without scanlines. */
fun Modifier.tccLookScanlines(spec: TccLookSpec, colors: TccColorPalette): Modifier {
    if (!spec.traits.scanlines) return this
    return drawWithContent {
        drawContent()
        val pitch = 3.dp.toPx()
        val line = Color.Black.copy(alpha = if (colors.isDark) 0.16f else 0.05f)
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f, cap = StrokeCap.Butt)
            y += pitch
        }
    }
}
