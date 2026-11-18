package jr.brian.commandpannel.ui.components.looks

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import jr.brian.commandpannel.ui.theme.aynDp
import jr.brian.commandpannel.ui.theme.withHueShift
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Backdrops no built-in look uses, offered in Theme Studio under their own names. All are static (drawn once per
 * size through drawWithCache), tinted from the look's palette, scaled by the look's pattern strength
 * (backdropPatternAlpha), and kept faint on the pure-black palette so OLED black stays black.
 */

/** How strong a pattern draws: the look's pattern strength, toned down on pure black. */
private fun LookPaint.strength(): Float =
    spec.traits.backdropPatternAlpha * if (lookColors.isPureBlack) 0.5f else 1f

/** [BackdropStyle.GRADIENT_WASH][jr.brian.commandpannel.ui.theme.BackdropStyle]: the accent washing corner to corner into a hue-shifted partner. */
internal fun Modifier.gradientWashBackdrop(p: LookPaint): Modifier = drawWithCache {
    val alpha = (if (p.colors.isDark) 0.34f else 0.22f) * p.strength()
    val accent = p.colors.accent
    val brush = Brush.linearGradient(
        0f to accent.copy(alpha = alpha),
        0.55f to accent.withHueShift(30f).copy(alpha = alpha * 0.45f),
        1f to accent.withHueShift(55f).copy(alpha = alpha * 0.8f),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * [BackdropStyle.TOPOGRAPHIC][jr.brian.commandpannel.ui.theme.BackdropStyle]: map-style contour rings around an
 * off-center point, each wobbling with two summed sines (fixed phases, so it never shifts). Every fourth ring is
 * an index contour, a little stronger.
 */
internal fun Modifier.topographicBackdrop(p: LookPaint): Modifier = drawWithCache {
    val center = Offset(size.width * 0.3f, size.height * 0.4f)
    val spacing = aynDp(16f).toPx()
    val reach = sqrt(size.width * size.width + size.height * size.height)
    val rings = (reach / spacing).toInt().coerceAtMost(60)
    val points = 96
    val paths = (1..rings).map { ring ->
        val base = ring * spacing
        val wobble = spacing * 0.35f
        Path().apply {
            for (i in 0..points) {
                val t = i * 2 * PI / points
                val r = base + wobble * (sin(3 * t + ring * 0.7) + 0.6 * sin(5 * t - ring * 0.4)).toFloat()
                val x = center.x + r * cos(t).toFloat()
                val y = center.y + r * sin(t).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
    val alpha = (if (p.colors.isDark) 0.07f else 0.09f) * p.strength()
    val line = p.colors.primary.copy(alpha = alpha)
    val index = p.colors.primary.copy(alpha = (alpha * 2f).coerceAtMost(1f))
    val thin = Stroke(aynDp(0.8f).toPx())
    val thick = Stroke(aynDp(1.3f).toPx())
    onDrawBehind {
        paths.forEachIndexed { i, path ->
            if ((i + 1) % 4 == 0) drawPath(path, index, style = thick) else drawPath(path, line, style = thin)
        }
    }
}

/** [BackdropStyle.HONEYCOMB][jr.brian.commandpannel.ui.theme.BackdropStyle]: a fine flat-top hexagon mesh in an accent tint. */
internal fun Modifier.honeycombBackdrop(p: LookPaint): Modifier = drawWithCache {
    val r = aynDp(7f).toPx() // corner radius: hexes are 14 dp across
    val h = sqrt(3f) * r
    // Each hex draws only its three lower edges (right, bottom, left); its neighbors' lower edges are its upper ones,
    // so the mesh is complete with no edge drawn twice. One line-segment batch: a single huge Path with thousands of
    // contours can be dropped by the GPU renderer.
    val corner = List(4) { i -> Offset(r * cos(PI / 3 * i).toFloat(), r * sin(PI / 3 * i).toFloat()) }
    val segments = ArrayList<Offset>()
    var col = 0
    var x = 0f
    while (x < size.width + r * 2) {
        var y = if (col % 2 == 0) -h else -h / 2
        while (y < size.height + h) {
            val c = Offset(x, y)
            for (i in 0..2) { segments += c + corner[i]; segments += c + corner[i + 1] }
            y += h
        }
        x += r * 1.5f
        col++
    }
    val color = p.colors.accent.copy(alpha = (if (p.colors.isDark) 0.18f else 0.20f) * p.strength())
    val width = aynDp(2f).toPx()
    onDrawBehind { drawPoints(segments, PointMode.Lines, color, strokeWidth = width) }
}

/**
 * [BackdropStyle.STARFIELD][jr.brian.commandpannel.ui.theme.BackdropStyle]: scattered stars in three sizes from a
 * fixed seed, so the field is the same every time and never flickers. The brightest few get a soft glow.
 */
internal fun Modifier.starfieldBackdrop(p: LookPaint): Modifier = drawWithCache {
    val rnd = Random(0x7CF)
    val cell = aynDp(30f).toPx() // about one star per 900 dp²
    val count = ((size.width / cell) * (size.height / cell)).toInt().coerceIn(0, 2000)
    val tint = lerp(p.colors.primary, p.colors.accent, 0.2f)
    val strength = p.strength()
    val small = aynDp(0.6f).toPx()
    val mid = aynDp(1.0f).toPx()
    val big = aynDp(1.6f).toPx()
    data class Star(val at: Offset, val radius: Float, val color: Color, val glow: Boolean)
    val stars = List(count) {
        val at = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
        when (rnd.nextInt(100)) {
            in 0..69 -> Star(at, small, tint.copy(alpha = 0.25f * strength), false)
            in 70..93 -> Star(at, mid, tint.copy(alpha = 0.45f * strength), false)
            else -> Star(at, big, tint.copy(alpha = 0.8f * strength), true)
        }
    }
    onDrawBehind {
        stars.forEach { s ->
            if (s.glow) {
                val reach = s.radius * 5
                drawCircle(
                    Brush.radialGradient(
                        0f to s.color.copy(alpha = s.color.alpha * 0.35f),
                        1f to Color.Transparent,
                        center = s.at, radius = reach,
                    ),
                    reach, s.at,
                )
            }
            drawCircle(s.color, s.radius, s.at)
        }
    }
}
