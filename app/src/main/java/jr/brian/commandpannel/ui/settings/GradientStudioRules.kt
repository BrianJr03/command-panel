package jr.brian.commandpannel.ui.settings

import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.GradientMotion
import jr.brian.commandpannel.system.GradientPresets
import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientSpec
import jr.brian.commandpannel.system.GradientStop
import jr.brian.commandpannel.system.GradientStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Gradient Studio's editing rules (#68), pure so they're unit-tested: stop add / remove / move, the named speed steps,
 * angle steps, Shuffle's palettes, "From my look" and the readability check. The screen is [GradientStudioScreen].
 */
internal object GradientStudioRules {
    /** Angle stepper step, in degrees. */
    const val ANGLE_STEP = 15f
    /** Quick angle picks (0 runs left to right, turning clockwise, so 90 is top to bottom). */
    val ANGLE_PICKS = listOf(0f, 45f, 90f, 135f)
    /** Fine-tune steps: stop position, angle and speed. */
    const val POSITION_STEP = 0.05f
    const val FINE_ANGLE_STEP = 1f
    const val FINE_SPEED_STEP = 0.05f

    /**
     * Below this contrast between light tile text and the brightest stop, the editor warns that the panel's labels may
     * be hard to read (WCAG's bar for large text and UI parts: the panel's tiles sit on their own cards, so text right
     * on the gradient is the exception). Every built-in preset passes it.
     */
    const val MIN_TEXT_CONTRAST = 3f

    /** Shuffle aims higher (WCAG AA for body text), so a random palette never comes close to the warning. */
    const val SHUFFLE_TEXT_CONTRAST = 4.5f

    /** One named speed: [speed] is the multiplier stored in [GradientSpec.speed] (1 is a 9 s loop). */
    data class SpeedStep(val speed: Float, @StringRes val labelRes: Int)

    /** The speed stepper's steps, still first, then calm to lively (the presets sit at 0.6 to 1). */
    val SPEED_STEPS: List<SpeedStep> = listOf(
        SpeedStep(0f, R.string.gradient_speed_still),
        SpeedStep(0.25f, R.string.gradient_speed_drowsy),
        SpeedStep(0.5f, R.string.gradient_speed_calm),
        SpeedStep(0.75f, R.string.gradient_speed_relaxed),
        SpeedStep(1f, R.string.gradient_speed_steady),
        SpeedStep(1.5f, R.string.gradient_speed_lively),
        SpeedStep(2f, R.string.gradient_speed_brisk),
        SpeedStep(3f, R.string.gradient_speed_fast),
        SpeedStep(4f, R.string.gradient_speed_hyper),
    )

    /** Index of the named step closest to [speed]. */
    fun speedIndex(speed: Float): Int = SPEED_STEPS.indices.minBy { kotlin.math.abs(SPEED_STEPS[it].speed - speed) }

    /**
     * The next named speed from [speed] in [direction] (-1 or +1). From a speed between two steps (set in Fine-tune) it
     * goes to the nearest step that way; at either end it stays.
     */
    fun stepSpeed(speed: Float, direction: Int): Float {
        val steps = SPEED_STEPS.map { it.speed }
        return if (direction > 0) steps.firstOrNull { it > speed + 1e-4f } ?: steps.last()
        else steps.lastOrNull { it < speed - 1e-4f } ?: steps.first()
    }

    /** [angle] moved by [delta] degrees and wrapped into 0 until 360. */
    fun stepAngle(angle: Float, delta: Float): Float = GradientRules.wrapAngle(((angle + delta) / FINE_ANGLE_STEP).roundToInt() * FINE_ANGLE_STEP)

    /**
     * Adds a stop after [selected] (halfway to the next one, in a color between them; after the last one it goes
     * halfway back toward the one before). Returns the new stops and the new stop's index, or null at [GradientRules.MAX_STOPS].
     */
    fun addStop(stops: List<GradientStop>, selected: Int): Pair<List<GradientStop>, Int>? {
        if (stops.size >= GradientRules.MAX_STOPS || stops.isEmpty()) return null
        val i = selected.coerceIn(0, stops.lastIndex)
        val (a, b) = if (i < stops.lastIndex) stops[i] to stops[i + 1] else stops[i - 1].let { prev -> prev to stops[i] }
        val stop = GradientStop(GradientPresets.mix(a.color, b.color, 0.5f), (a.position + b.position) / 2f)
        val next = (stops + stop).sortedBy { it.position }
        return next to next.indexOf(stop)
    }

    /** Removes stop [index]; returns the stops and which to select next, or null at [GradientRules.MIN_STOPS]. */
    fun removeStop(stops: List<GradientStop>, index: Int): Pair<List<GradientStop>, Int>? {
        if (stops.size <= GradientRules.MIN_STOPS || index !in stops.indices) return null
        val next = stops.filterIndexed { i, _ -> i != index }
        return next to index.coerceAtMost(next.lastIndex)
    }

    /**
     * Moves stop [index] to [position] (clamped to 0..1, snapped to [POSITION_STEP]); the stops stay sorted, so the
     * moved stop's new index comes back with them.
     */
    fun moveStop(stops: List<GradientStop>, index: Int, position: Float): Pair<List<GradientStop>, Int> {
        if (index !in stops.indices) return stops to index
        val snapped = ((position / POSITION_STEP).roundToInt() * POSITION_STEP).coerceIn(0f, 1f)
        val moved = stops[index].copy(position = snapped)
        val others = stops.filterIndexed { i, _ -> i != index }
        // Stable: a stop moved onto another's position goes after it when moving right, before it when moving left.
        val right = snapped >= stops[index].position
        val at = if (right) others.indexOfLast { it.position <= snapped } + 1 else others.indexOfFirst { it.position >= snapped }.let { if (it < 0) others.size else it }
        val next = others.toMutableList().apply { add(at, moved) }
        return next to at
    }

    /** Stop [index] recolored to [color] (made opaque). */
    fun setStopColor(stops: List<GradientStop>, index: Int, color: Int): List<GradientStop> =
        stops.mapIndexed { i, s -> if (i == index) s.copy(color = GradientRules.opaque(color)) else s }

    /** A new, unsaved gradient: a calm two-color drift in dark and mid tones. */
    fun newGradient(id: String, name: String): GradientSpec = GradientSpec(
        id = id,
        name = name,
        stops = listOf(GradientStop(0xFF10183A.toInt(), 0f), GradientStop(0xFF3A2878.toInt(), 0.55f), GradientStop(0xFF0B5F94.toInt(), 1f)),
        style = GradientStyle.LINEAR,
        angle = 45f,
        motion = GradientMotion.DRIFT,
        speed = 0.75f,
    )

    /** [spec] with [preset]'s look (stops, style, angle, motion, speed); [spec]'s id and name stay. */
    fun applyPreset(spec: GradientSpec, preset: GradientSpec): GradientSpec =
        spec.copy(stops = preset.stops, style = preset.style, angle = preset.angle, motion = preset.motion, speed = preset.speed)

    /**
     * "From my look": the "Match my look" preset's stops (the look's background with bands of its accent) from the
     * look's [accent] and [background]. Style, motion and the rest stay as the user set them.
     */
    fun fromLook(spec: GradientSpec, accent: Int, background: Int): GradientSpec {
        val preset = GradientPresets.all.first { it.fromLook }
        return spec.copy(stops = preset.spec("", accent, background).stops)
    }

    /** The palette families Shuffle picks from: hue offsets from a random base hue. */
    private val HARMONIES: List<List<Float>> = listOf(
        listOf(0f, 25f, 50f),          // analogous
        listOf(0f, 30f, 180f),         // split toward the complement
        listOf(0f, 180f, 20f),         // complementary
        listOf(0f, 120f, 240f),        // triad
        listOf(0f, -30f, 30f, 60f),    // wide analogous, four colors
    )

    /**
     * A random harmonious palette of 3 or 4 stops, evenly spaced, in dark and mid tones (brightness 0.16 to 0.55,
     * saturation 0.45 to 0.8) like the presets, so the panel's labels stay readable on it ([isReadable] holds).
     */
    fun shuffle(random: Random): List<GradientStop> {
        val base = random.nextFloat() * 360f
        val offsets = HARMONIES[random.nextInt(HARMONIES.size)]
        val n = offsets.size
        return offsets.mapIndexed { i, off ->
            val v = 0.16f + random.nextFloat() * 0.39f
            val s = 0.45f + random.nextFloat() * 0.35f
            GradientStop(hsvToArgb(Hsv(((base + off) % 360f + 360f) % 360f, s, v)), i / (n - 1).toFloat())
        }.let { stops -> stops.map { it.copy(color = darkenToReadable(it.color)) } }
    }

    /** [color] darkened (keeping its hue) until light text on it meets [SHUFFLE_TEXT_CONTRAST]. */
    fun darkenToReadable(color: Int): Int {
        var c = GradientRules.opaque(color)
        var hsv = argbToHsv(c)
        while (contrast(WHITE, c) < SHUFFLE_TEXT_CONTRAST && hsv.v > 0.02f) {
            hsv = hsv.copy(v = hsv.v - 0.02f)
            c = hsvToArgb(hsv)
        }
        return c
    }

    private const val WHITE = 0xFFFFFFFF.toInt()

    /** WCAG relative luminance of an opaque ARGB color. */
    fun luminance(argb: Int): Float {
        fun ch(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return (0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)).toFloat()
    }

    /** WCAG contrast ratio between two opaque colors (1 to 21). */
    fun contrast(a: Int, b: Int): Float {
        val la = luminance(a) + 0.05f
        val lb = luminance(b) + 0.05f
        return max(la, lb) / min(la, lb)
    }

    /** The brightest stop's color (by luminance). */
    fun brightest(stops: List<GradientStop>): Int = stops.maxBy { luminance(it.color) }.color

    /** Whether light tile text stays readable over every stop: its contrast with the brightest is at least [MIN_TEXT_CONTRAST]. */
    fun isReadable(stops: List<GradientStop>): Boolean = stops.isEmpty() || contrast(WHITE, brightest(stops)) >= MIN_TEXT_CONTRAST
}
