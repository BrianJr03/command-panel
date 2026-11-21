package jr.brian.commandpannel.system

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * What the gradient shader (#66) draws at one moment: the uniforms that move. [angle], [hue], [wavePhase] and
 * [blobPhase] are radians and only ever reach the shader through sin and cos, so a whole turn draws the same picture.
 * [shiftX] / [shiftY] move the gradient's center (in panel heights), [scale] zooms it (1 is the spec as drawn),
 * [waveAmp] bends it (0 is straight).
 */
data class GradientFrame(
    val angle: Float,
    val shiftX: Float,
    val shiftY: Float,
    val scale: Float,
    val hue: Float,
    val wavePhase: Float,
    val waveAmp: Float,
    val blobPhase: Float,
)

/**
 * Time to shader uniforms for an animated gradient (#66), pure so it is unit-tested, and shared by the panel and the
 * editor preview so both show the same thing. Every motion is a loop of [LOOP_MS] at speed 1 ([GradientSpec.speed]
 * divides it), built from whole turns and whole harmonics, so the last frame of a loop runs into the first with no jump.
 * Time 0 (and any time at speed 0) is the still frame: the spec as drawn.
 */
object GradientMotionMath {
    /** One loop at speed 1. */
    const val LOOP_MS = 9_000L
    const val DRIFT_X = 0.35f
    const val DRIFT_Y = 0.25f
    /** How far a radial gradient's center circles when it rotates. */
    const val ORBIT = 0.3f
    /** The most a breathing gradient zooms in (1 + this). */
    const val BREATHE = 0.8f
    const val WAVE_AMP = 0.16f

    /** The surface the panel's gradient is drawn into is the layout's size divided by this (the display scales it up). */
    const val SURFACE_DIVISOR = 4
    /** The smallest surface side, so a tiny layout still gets a smooth gradient. */
    const val MIN_SURFACE_PX = 64

    /** Where [timeMs] falls in the loop at [speed], in 0 until 1 (0 for a still speed). */
    fun loopFraction(timeMs: Long, speed: Float): Double {
        if (!(speed > 0f) || !speed.isFinite()) return 0.0
        val cycles = timeMs.toDouble() * speed / LOOP_MS
        val f = cycles - floor(cycles)
        return if (f >= 1.0) 0.0 else f
    }

    /** One loop's length at [speed], or `null` when it doesn't move. */
    fun loopMs(speed: Float): Double? = if (speed > 0f && speed.isFinite()) LOOP_MS / speed.toDouble() else null

    /** The still frame of [spec]: what it shows at speed 0 or with Animated backgrounds off. */
    fun still(spec: GradientSpec): GradientFrame = frame(spec, 0L)

    /** [spec] at [timeMs] on the gradient clock (any origin; only the loop position matters). */
    fun frame(spec: GradientSpec, timeMs: Long): GradientFrame {
        val theta = 2.0 * PI * loopFraction(timeMs, spec.speed)
        val s = sin(theta).toFloat()
        val c = cos(theta).toFloat()
        val base = Math.toRadians(spec.angle.toDouble()).toFloat()
        var angle = base
        var shiftX = 0f
        var shiftY = 0f
        var scale = 1f
        var hue = 0f
        var waveAmp = 0f
        var wavePhase = 0f
        when (spec.motion) {
            GradientMotion.DRIFT -> {
                shiftX = DRIFT_X * s
                shiftY = DRIFT_Y * sin(2.0 * theta).toFloat()
            }
            GradientMotion.ROTATE ->
                if (spec.style == GradientStyle.RADIAL) {
                    // A circle is the same at every angle, so its center circles instead (starting where it's drawn).
                    shiftX = ORBIT * s
                    shiftY = ORBIT * (1f - c)
                } else {
                    angle = base + theta.toFloat()
                }
            GradientMotion.BREATHE -> scale = 1f + BREATHE * (1f - c) / 2f
            GradientMotion.HUE_CYCLE -> hue = theta.toFloat()
            GradientMotion.WAVE -> {
                waveAmp = WAVE_AMP
                wavePhase = theta.toFloat()
            }
        }
        // Aurora's blobs drift under every motion; the other styles ignore it.
        return GradientFrame(angle, shiftX, shiftY, scale, hue, wavePhase, waveAmp, theta.toFloat())
    }

    /**
     * Milliseconds between frames for a [android.os.PowerManager] thermal status: the 30 fps cap
     * ([BackgroundImageRules.MIN_FRAME_MS]) when cool, half that at MODERATE, and `null` (hold the current frame) at
     * SEVERE and above.
     */
    fun frameIntervalMs(thermalStatus: Int): Long? = when {
        thermalStatus <= THERMAL_LIGHT -> BackgroundImageRules.MIN_FRAME_MS
        thermalStatus == THERMAL_MODERATE -> BackgroundImageRules.MIN_FRAME_MS * 2
        else -> null
    }

    // PowerManager.THERMAL_STATUS_LIGHT / _MODERATE, kept here so this stays plain Kotlin.
    private const val THERMAL_LIGHT = 1
    private const val THERMAL_MODERATE = 2

    /** The surface size for a layout of [layout]: [SURFACE_DIVISOR] smaller, at least [MIN_SURFACE_PX], never larger. */
    fun surfaceSize(layout: PxSize): PxSize {
        if (layout.width <= 0 || layout.height <= 0) return PxSize(0, 0)
        fun side(px: Int) = minOf(px, max(MIN_SURFACE_PX, (px + SURFACE_DIVISOR - 1) / SURFACE_DIVISOR))
        return PxSize(side(layout.width), side(layout.height))
    }
}
