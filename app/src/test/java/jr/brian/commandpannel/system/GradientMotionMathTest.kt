package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class GradientMotionMathTest {
    private fun spec(motion: GradientMotion, style: GradientStyle = GradientStyle.LINEAR, speed: Float = 1f, angle: Float = 30f) =
        GradientSpec(
            id = "g", name = "g",
            stops = listOf(GradientStop(0xFFFF0000.toInt(), 0f), GradientStop(0xFF0000FF.toInt(), 1f)),
            style = style, angle = angle, motion = motion, speed = speed,
        )

    private val allSpecs = GradientStyle.entries.flatMap { st -> GradientMotion.entries.map { spec(it, st) } }

    /** What the shader sees: angles only through sin and cos. */
    private fun visual(f: GradientFrame) = floatArrayOf(
        cos(f.angle), sin(f.angle), f.shiftX, f.shiftY, f.scale, cos(f.hue), sin(f.hue),
        cos(f.wavePhase), sin(f.wavePhase), f.waveAmp, cos(f.blobPhase), sin(f.blobPhase),
    )

    private fun distance(a: GradientFrame, b: GradientFrame): Float {
        val va = visual(a); val vb = visual(b)
        return va.indices.maxOf { abs(va[it] - vb[it]) }
    }

    @Test
    fun timeZeroIsTheStillFrame() {
        for (s in allSpecs) {
            val f = GradientMotionMath.still(s)
            assertEquals(Math.toRadians(30.0).toFloat(), f.angle, 1e-5f)
            assertEquals(0f, f.shiftX, 1e-6f)
            assertEquals(0f, f.shiftY, 1e-6f)
            assertEquals(1f, f.scale, 1e-6f)
            assertEquals(0f, f.hue, 1e-6f)
            assertEquals(0f, f.blobPhase, 1e-6f)
        }
    }

    @Test
    fun speedZeroNeverMoves() {
        for (m in GradientMotion.entries) {
            val s = spec(m, speed = 0f)
            for (t in listOf(1L, 5_000L, 123_456_789L)) assertEquals(GradientMotionMath.still(s), GradientMotionMath.frame(s, t))
        }
    }

    @Test
    fun loopsRepeatExactly() {
        for (speed in listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)) for (m in GradientMotion.entries) for (st in GradientStyle.entries) {
            val s = spec(m, st, speed)
            val loop = GradientMotionMath.loopMs(speed)!!
            for (t in listOf(0L, 777L, 10_001L)) {
                val d = distance(GradientMotionMath.frame(s, t), GradientMotionMath.frame(s, t + (loop * 3).toLong()))
                assertTrue("$m $st speed $speed at $t: $d", d < 1e-3f)
            }
        }
    }

    @Test
    fun noJumpAtTheLoopSeamOrAnywhereElse() {
        // At 30 fps every step is small (calm at preset speeds, never a flash at the top speed), and the step across
        // the loop's end is no bigger than an ordinary one.
        for ((speed, limit) in listOf(1f to 0.04f, 4f to 0.15f)) for (s in allSpecs.map { it.copy(speed = speed) }) {
            val loop = GradientMotionMath.loopMs(speed)!!.toLong()
            var maxStep = 0f
            var prev = GradientMotionMath.frame(s, 0)
            for (step in 1..(loop / 33).toInt()) {
                val f = GradientMotionMath.frame(s, step * 33L)
                maxStep = maxOf(maxStep, distance(prev, f))
                prev = f
            }
            assertTrue("${s.motion} ${s.style} speed $speed: max step $maxStep", maxStep < limit)
            var t = loop * 5 - 330
            prev = GradientMotionMath.frame(s, t)
            while (t < loop * 5 + 330) {
                t += 33
                val f = GradientMotionMath.frame(s, t)
                assertTrue("${s.motion} ${s.style} at $t", distance(prev, f) <= maxStep * 1.01f + 1e-4f)
                prev = f
            }
        }
    }

    @Test
    fun everyMotionMoves() {
        for (s in allSpecs) {
            val quarter = GradientMotionMath.LOOP_MS / 4
            assertTrue("${s.motion} ${s.style}", distance(GradientMotionMath.still(s), GradientMotionMath.frame(s, quarter)) > 0.05f)
        }
    }

    @Test
    fun longUptimeStaysFiniteAndInLoop() {
        val f = GradientMotionMath.loopFraction(Long.MAX_VALUE / 4, 4f)
        assertTrue(f in 0.0..1.0 && f < 1.0)
        val far = 90L * 24 * 3600 * 1000 // 90 days of uptime
        val s = spec(GradientMotion.ROTATE)
        visual(GradientMotionMath.frame(s, far)).forEach { assertTrue(it.isFinite()) }
        val loop = GradientMotionMath.LOOP_MS
        assertTrue(distance(GradientMotionMath.frame(s, far), GradientMotionMath.frame(s, far + loop)) < 1e-3f)
    }

    @Test
    fun badSpeedsAreStill() {
        assertEquals(0.0, GradientMotionMath.loopFraction(5_000, Float.NaN), 0.0)
        assertEquals(0.0, GradientMotionMath.loopFraction(5_000, -1f), 0.0)
        assertEquals(0.0, GradientMotionMath.loopFraction(5_000, Float.POSITIVE_INFINITY), 0.0)
        assertNull(GradientMotionMath.loopMs(0f))
    }

    @Test
    fun thermalSlowsThenHolds() {
        assertEquals(BackgroundImageRules.MIN_FRAME_MS, GradientMotionMath.frameIntervalMs(0))
        assertEquals(BackgroundImageRules.MIN_FRAME_MS, GradientMotionMath.frameIntervalMs(1))
        assertEquals(BackgroundImageRules.MIN_FRAME_MS * 2, GradientMotionMath.frameIntervalMs(2))
        for (status in 3..6) assertNull(GradientMotionMath.frameIntervalMs(status))
    }

    @Test
    fun surfaceIsAQuarterOfTheLayout() {
        assertEquals(PxSize(310, 270), GradientMotionMath.surfaceSize(PxSize(1240, 1080)))
        assertEquals(PxSize(64, 64), GradientMotionMath.surfaceSize(PxSize(100, 200)))
        assertEquals(PxSize(40, 64), GradientMotionMath.surfaceSize(PxSize(40, 120)))
        assertEquals(PxSize(0, 0), GradientMotionMath.surfaceSize(PxSize(0, 500)))
    }
}
