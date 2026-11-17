package jr.brian.commandpannel.ui.components.looks

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.abs

/*
 * Jelly renderers:
 *   - `CardStyle.JELLY`
 *   - `TileStyle.GEL`
 *   - `GaugeStyle.CAPSULE`
 *   - `SliderStyle.GEL`
 *   - `NavStyle.GEL_PILL`
 *
 * Classic glossy gel: buttons are vertical gradients (`gelTop` → `gelBottom` for the accent,
 * `gelTonalTop` → `gelTonalBottom` when off) with a white gloss ellipse (`gelHighlight`) across the
 * top ~45%, and progress bars are candy capsules with a 2dp highlight line. On pure black the
 * tonal gel is lifted (`TccLookColors`) so off tiles still read as gel.
 */

private val GlossInset = aynDp(5f)
private const val GLOSS_HEIGHT = 0.45f

/**
 * Gloss ellipse bounds for a gel of [width] × [height]: inset [inset] from the sides and a little
 * from the top, covering the top [GLOSS_HEIGHT] of the shape.
 */
internal fun gelGlossRect(width: Float, height: Float, inset: Float): Rect {
    val side = inset.coerceAtMost(width / 3f)
    return Rect(side, side * 0.5f, width - side, height * GLOSS_HEIGHT)
}

/** Holder that rebuilds its gradient only when the bar height changes (bars resize per frame). */
private class GelBrushes {
    private var height = -1f
    private var top = Color.Unspecified
    private var bottom = Color.Unspecified
    var fill: Brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        private set

    fun update(h: Float, topColor: Color, bottomColor: Color): Brush {
        if (h != height || topColor != top || bottomColor != bottom) {
            height = h
            top = topColor
            bottom = bottomColor
            fill = Brush.verticalGradient(listOf(topColor, bottomColor), startY = 0f, endY = h)
        }
        return fill
    }
}

/** Gel capsule at [topLeft] / [size] in [brush] with a 2dp highlight line along the inner top. */
private fun DrawScope.drawGelCapsule(topLeft: Offset, size: Size, brush: Brush, highlight: Color) {
    if (size.width <= 0f || size.height <= 0f) return
    val r = size.height / 2f
    drawRoundRect(brush, topLeft, size, CornerRadius(r, r))
    val line = 2.dp.toPx()
    val inset = r * 0.8f
    if (size.width > inset * 2f) {
        drawRoundRect(
            highlight,
            Offset(topLeft.x + inset, topLeft.y + line * 0.8f),
            Size(size.width - inset * 2f, line),
            CornerRadius(line / 2f, line / 2f)
        )
    }
}

/** Glossy gel fill of [shape] with an optional soft drop glow; all brushes cached per size. */
private fun Modifier.gel(top: Color, bottom: Color, highlight: Color, glow: Color?): Modifier = drawWithCache {
    val fill = Brush.verticalGradient(listOf(top, bottom))
    val gloss = gelGlossRect(size.width, size.height, GlossInset.toPx())
    val glossBrush = Brush.verticalGradient(listOf(highlight, highlight.copy(alpha = 0f)), startY = gloss.top, endY = gloss.bottom)
    val glowPaint = glow?.let { c ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = c.toArgb()
            setShadowLayer(aynDp(6f).toPx(), 0f, aynDp(3f).toPx(), c.copy(alpha = 0.45f).toArgb())
        }
    }
    onDrawBehind {
        val r = size.minDimension / 2f
        if (glowPaint != null) drawIntoCanvas { it.nativeCanvas.drawCircle(size.width / 2f, size.height / 2f, r * 0.96f, glowPaint) }
        drawCircle(fill, r)
        drawOval(glossBrush, gloss.topLeft, gloss.size)
    }
}

private fun jellyTrack(p: LookPaint): Color = lerp(p.lookColors.gelTonalBottom, Color.Black, if (p.colors.isDark) 0.35f else 0.12f)

internal object JellyCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier {
        val rim = if (p.colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
        return base.clip(shape)
            .background(fill ?: if (focused) p.colors.panelBackgroundSelected else p.colors.panelBackground)
            .border(1.dp, rim, shape)
    }

    override fun dialog(base: Modifier, p: LookPaint): Modifier {
        val shape = p.spec.dialog.dialogShape
        val rim = if (p.colors.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
        return base.clip(shape).background(p.colors.dialogBackground).border(1.dp, rim, shape)
    }

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier {
        val lc = p.lookColors
        return Modifier.drawWithCache {
            val brush = Brush.verticalGradient(listOf(lc.gelTop, lc.gelBottom))
            onDrawBehind { drawGelCapsule(Offset.Zero, size, brush, lc.gelHighlight) }
        }
    }

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.onAccent
}

internal object JellyTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier {
        val lc = p.lookColors
        return if (selected) {
            base.gel(lc.gelTop, lc.gelBottom, lc.gelHighlight, glow = lc.gelBottom)
        } else {
            base.gel(lc.gelTonalTop, lc.gelTonalBottom, lc.gelHighlight, glow = null)
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.onAccent else p.colors.primary
}

internal object JellyGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val a = p.lookColors.gelBottom
        return GaugeRingColors(a, a, a, a)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val p = currentLookPaint()
        val lc = p.lookColors
        val labelStyle = tccTextStyle()
        val displayStyle = tccDisplayTextStyle()
        val brushes = remember { GelBrushes() }
        val track = jellyTrack(p)
        val negative = p.colors.error
        val top = lerp(reading.color, Color.White, 0.35f)
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth, maxHeight) * p.spec.settingsTab.ringDiameterRatio
            val density = LocalDensity.current
            val titleSp = with(density) { (side * p.spec.settingsTab.ringTitleScale).toSp() }
            val valueSp = with(density) { (side * p.spec.settingsTab.ringValueScale).toSp() }
            val unitSp = with(density) { (side * p.spec.settingsTab.ringUnitScale).toSp() }
            Column(
                modifier = Modifier.width(side),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(side * 0.05f)
            ) {
                Text(text = reading.title, color = p.colors.tertiary, fontSize = titleSp, style = labelStyle, maxLines = 1)
                Text(text = reading.valueText, color = p.colors.primary, fontSize = valueSp, style = displayStyle, maxLines = 1)
                Canvas(
                    modifier = Modifier
                        .width(side * 0.92f)
                        .height(side * 0.16f)
                ) {
                    val r = size.height / 2f
                    drawRoundRect(track, cornerRadius = CornerRadius(r, r))
                    val fraction = reading.fraction()
                    val inset = 2.dp.toPx()
                    val h = size.height - inset * 2f
                    val w = ((size.width - inset * 2f) * abs(fraction).coerceIn(0f, 1f)).coerceAtLeast(if (fraction != 0f) h else 0f)
                    if (w > 0f) {
                        val brush = if (fraction < 0f) {
                            brushes.update(h, lerp(negative, Color.White, 0.35f), negative)
                        } else {
                            brushes.update(h, top, reading.color)
                        }
                        val x = if (fraction < 0f) size.width - inset - w else inset
                        translate(top = inset) { drawGelCapsule(Offset(x, 0f), Size(w, h), brush, lc.gelHighlight) }
                    }
                }
                Text(text = reading.unitText, color = p.colors.tertiary, fontSize = unitSp, style = labelStyle, maxLines = 1)
            }
        }
    }
}

internal object JellySlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier.background(jellyTrack(p.look))

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val lc = p.look.lookColors
        val brushes = remember { GelBrushes() }
        val thumbBrushes = remember { GelBrushes() }
        val top = if (p.dimmed) p.fillColor else lc.gelTop
        val bottom = if (p.dimmed) p.fillColor else lc.gelBottom
        val rim = lc.gelTonalBottom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val inset = aynDp(3f).toPx()
            val h = size.height - inset * 2f
            val thumbR = size.height / 2f
            val cx = (p.fraction * size.width).coerceIn(thumbR, size.width - thumbR)
            if (p.fraction > 0f) {
                val brush = brushes.update(h, top, bottom)
                translate(top = inset) { drawGelCapsule(Offset(inset, 0f), Size((cx - inset).coerceAtLeast(h), h), brush, lc.gelHighlight) }
            }
            // Glossy tonal gel thumb.
            val thumb = thumbBrushes.update(size.height, lc.gelTonalTop, lc.gelTonalBottom)
            drawCircle(rim, thumbR, Offset(cx, size.height / 2f))
            translate(left = cx - thumbR) {
                drawCircle(thumb, thumbR - 1.dp.toPx(), Offset(thumbR, size.height / 2f))
                val gloss = gelGlossRect(thumbR * 2f, size.height, thumbR * 0.35f)
                drawOval(lc.gelHighlight, gloss.topLeft, gloss.size)
            }
        }
    }

    override fun iconTint(p: SliderPaint): Color =
        if (p.overFill) p.look.lookColors.onAccent else p.look.colors.primary
}

internal object JellyNav : LookNavRenderer {
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
            val brush = Brush.verticalGradient(listOf(lc.gelTop, lc.gelBottom), startY = topLeft.y, endY = topLeft.y + h)
            onDrawBehind { drawGelCapsule(topLeft, Size(w, h), brush, lc.gelHighlight) }
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.onAccent else p.colors.secondary

    override fun textColor(p: LookPaint, selected: Boolean): Color =
        if (selected) p.colors.primary else p.colors.secondary
}
