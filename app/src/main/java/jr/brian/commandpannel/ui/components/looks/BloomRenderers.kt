package jr.brian.commandpannel.ui.components.looks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import jr.brian.commandpannel.ui.components.RingGaugeLayout
import jr.brian.commandpannel.ui.theme.aynDp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Bloom renderers:
 *   - `CardStyle.BLOOM_TONAL`
 *   - `TileStyle.BLOOM_BLOB`
 *   - `GaugeStyle.WAVY_ARC`
 *   - `SliderStyle.WAVY`
 *   - `NavStyle.EXPRESSIVE_PILL`
 *
 * Material 3 Expressive-inspired: tonal containers (`bloomContainer` / `bloomContainerHigh`),
 * large radii, a selected tile that morphs from a rounded square into a scalloped "cookie", and
 * sine-wave progress strokes.
 */

private const val BLOB_LOBES = 7
private const val BLOB_DEPTH = 0.13f
private const val BLOB_ROTATION = -8f
private const val MORPH_SAMPLES = 72
private const val MORPH_MILLIS = 250
private const val WAVES_PER_CIRCLE = 10f

/**
 * Normalized outline radius (`0..1` of the half-size) at angle [theta] (radians) for the tile
 * morph: a squircle (`|x|^4 + |y|^4 = 1`, the unselected rounded square) at [progress] `0`, and
 * a [BLOB_LOBES]-lobe scallop with valleys [BLOB_DEPTH] deep at `1`.
 */
internal fun bloomMorphRadius(theta: Float, progress: Float): Float {
    val c = abs(cos(theta)).pow(4)
    val s = abs(sin(theta)).pow(4)
    val square = (c + s).pow(-0.25f)
    // Lobe peaks at theta = -90° (12 o'clock) so the cookie reads upright before rotation.
    val blob = 1f - BLOB_DEPTH * (1f - cos(BLOB_LOBES * (theta + (PI / 2).toFloat()))) / 2f
    val t = progress.coerceIn(0f, 1f)
    // The squircle reaches past the inscribed circle on the diagonals; scale it so it fits the box.
    return square * 0.98f * (1f - t) + blob * t
}

/**
 * Track pieces left after removing the filled range of a 270° arc that starts at [arcStart]:
 * angular ranges `(start, sweep)` before and after the fill, each kept [gap] degrees away from it.
 */
internal fun bloomTrackSegments(arcStart: Float, fillStart: Float, fillSweep: Float, gap: Float): List<Pair<Float, Float>> {
    if (fillSweep == 0f) return listOf(arcStart to 270f)
    val lo = min(fillStart, fillStart + fillSweep)
    val hi = max(fillStart, fillStart + fillSweep)
    val arcEnd = arcStart + 270f
    val before = lo - gap - arcStart
    val after = arcEnd - (hi + gap)
    return buildList {
        if (before > 0.5f) add(arcStart to before)
        if (after > 0.5f) add(arcEnd - after to after)
    }
}

/**
 * Fill start and sweep of the 270° wavy gauge (135°..405°); bidirectional values grow from
 * 12 o'clock, expressed as 270° so it stays inside that winding.
 */
internal fun bloomArcSweep(fraction: Float, bidirectional: Boolean): Pair<Float, Float> =
    if (bidirectional) 270f to fraction.coerceIn(-1f, 1f) * 135f else 135f to fraction.coerceIn(0f, 1f) * 270f

/** Appends a sine wave of [amplitude] that follows the circle of [radius] around [center]. */
private fun Path.wavyArc(center: Offset, radius: Float, startDeg: Float, sweepDeg: Float, amplitude: Float, phase: Float) {
    val steps = max(8, (abs(sweepDeg) / 2f).toInt())
    for (i in 0..steps) {
        val deg = startDeg + sweepDeg * i / steps
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        val r = radius + amplitude * sin(WAVES_PER_CIRCLE * rad + phase)
        val x = center.x + r * cos(rad)
        val y = center.y + r * sin(rad)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/** Fills [path] with the morphing tile outline for [progress], rotated by [rotationDeg]. */
private fun DrawScope.morphOutline(path: Path, progress: Float, rotationDeg: Float) {
    path.reset()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val half = min(size.width, size.height) / 2f
    val rot = Math.toRadians(rotationDeg.toDouble()).toFloat()
    for (i in 0 until MORPH_SAMPLES) {
        val theta = (2f * PI.toFloat() * i) / MORPH_SAMPLES
        val r = half * bloomMorphRadius(theta, progress)
        val x = cx + r * cos(theta + rot)
        val y = cy + r * sin(theta + rot)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
}

internal object BloomCard : LookCardRenderer {
    override fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier = base.clip(shape)
        .background(fill ?: if (focused) p.lookColors.bloomContainerHigh else p.lookColors.bloomContainer)

    override fun dialog(base: Modifier, p: LookPaint): Modifier = base
        .clip(p.spec.dialog.dialogShape)
        .background(lerp(p.colors.dialogBackground, p.colors.accent, 0.10f))

    override fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier = Modifier.background(p.colors.accent)

    override fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color = p.lookColors.onAccent
}

internal object BloomTile : LookTileRenderer {
    override fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier = base.composed {
        val progress by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tween(MORPH_MILLIS),
            label = "bloomBlobMorph"
        )
        val path = remember { Path() }
        val tonal = p.lookColors.bloomContainerHigh
        val accent = p.colors.accent
        Modifier.drawBehind {
            morphOutline(path, progress, BLOB_ROTATION * progress)
            drawPath(path, lerp(tonal, accent, progress))
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color =
        if (selected) p.lookColors.onAccent else p.colors.primary
}

internal object BloomGauge : LookGaugeRenderer {
    override fun ringColors(p: LookPaint): GaugeRingColors {
        val a = p.lookColors.bloomOnContainer
        return GaugeRingColors(a, a, a, a)
    }

    @Composable
    override fun Gauge(reading: GaugeReading, modifier: Modifier) {
        val density = LocalDensity.current
        val metrics = remember(density) {
            with(density) {
                BloomWaveMetrics(
                    wave = Stroke(aynDp(4f).toPx(), cap = StrokeCap.Round),
                    track = Stroke(aynDp(2.5f).toPx(), cap = StrokeCap.Round),
                    amplitude = aynDp(1.5f).toPx(),
                    gapPx = aynDp(5f).toPx()
                )
            }
        }
        val path = remember { Path() }
        val trackColor = reading.color.copy(alpha = 0.24f)
        RingGaugeLayout(reading, modifier) { fraction ->
            val inset = metrics.wave.width / 2f + metrics.amplitude
            val radius = size.minDimension / 2f - inset
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val (start, sweep) = bloomArcSweep(fraction, reading.bidirectional)
            // Gap in degrees: the requested spacing plus both round caps.
            val gapDeg = Math.toDegrees(((metrics.gapPx + metrics.wave.width) / radius).toDouble()).toFloat()
            bloomTrackSegments(135f, start, sweep, gapDeg).forEach { (s, sw) ->
                drawArc(trackColor, s, sw, false, topLeft, arcSize, style = metrics.track)
            }
            if (sweep != 0f) {
                path.reset()
                // Phase follows the value, so the wave ripples while the gauge animates.
                path.wavyArc(center, radius, start, sweep, metrics.amplitude, fraction * 6f * PI.toFloat())
                drawPath(path, reading.color, style = metrics.wave)
            }
        }
    }
}

private class BloomWaveMetrics(val wave: Stroke, val track: Stroke, val amplitude: Float, val gapPx: Float)

internal object BloomSlider : LookSliderRenderer {
    override fun track(p: SliderPaint): Modifier = Modifier.background(p.look.lookColors.bloomContainerHigh)

    @Composable
    override fun Fill(p: SliderPaint, trackWidth: Dp) {
        val density = LocalDensity.current
        val path = remember { Path() }
        val metrics = remember(density) {
            with(density) {
                BloomWaveMetrics(
                    wave = Stroke(aynDp(3.5f).toPx(), cap = StrokeCap.Round),
                    track = Stroke(aynDp(4f).toPx(), cap = StrokeCap.Round),
                    amplitude = aynDp(1.5f).toPx(),
                    gapPx = aynDp(4f).toPx()
                )
            }
        }
        val wave = if (p.dimmed) p.fillColor else p.look.colors.accent
        val stop = p.look.lookColors.bloomOnContainer
        val thumb = p.look.lookColors.bloomOnContainer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val thumbW = metrics.track.width
            val thumbX = (p.fraction * size.width).coerceIn(thumbW, size.width - thumbW)
            val waveEnd = thumbX - metrics.gapPx - thumbW / 2f
            val waveStart = size.height * 0.5f
            if (p.fraction > 0f && waveEnd > waveStart) {
                path.reset()
                val wavelength = aynDp(14f).toPx()
                val steps = max(4, ((waveEnd - waveStart) / 2f).toInt())
                for (i in 0..steps) {
                    val x = waveStart + (waveEnd - waveStart) * i / steps
                    val y = cy + metrics.amplitude * sin((x / wavelength) * 2f * PI.toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, wave, style = metrics.wave)
            }
            // Stop dot at the track end, then the tall rounded thumb bar.
            drawCircle(stop, radius = aynDp(2f).toPx(), center = Offset(size.width - size.height * 0.5f, cy))
            drawRoundRect(
                color = thumb,
                topLeft = Offset(thumbX - thumbW / 2f, size.height * 0.08f),
                size = Size(thumbW, size.height * 0.84f),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
            )
        }
    }

    override fun iconTint(p: SliderPaint): Color = p.look.colors.primary
}

internal object BloomNav : LookNavRenderer {
    override val columnHighlight: Boolean get() = true

    override val highlightShape: Shape = RoundedCornerShape(aynDp(16f))

    override fun highlight(p: LookPaint): Modifier {
        val tile = p.spec.tile
        val pill = lerp(p.lookColors.bloomContainerHigh, p.colors.accent, 0.30f)
        return Modifier.drawBehind {
            val w = aynDp(56f).toPx()
            val h = aynDp(32f).toPx()
            val cy = (tile.compactTopGap + tile.navTileSize / 2f).toPx()
            drawRoundRect(
                color = pill,
                topLeft = Offset((size.width - w) / 2f, cy - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2f, h / 2f)
            )
        }
    }

    override fun iconTint(p: LookPaint, selected: Boolean): Color =
        if (selected) p.colors.primary else p.colors.secondary

    override fun textColor(p: LookPaint, selected: Boolean): Color =
        if (selected) p.lookColors.bloomOnContainer else p.colors.secondary
}
