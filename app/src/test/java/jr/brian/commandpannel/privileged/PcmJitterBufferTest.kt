package jr.brian.commandpannel.privileged

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmJitterBufferTest {
    private val frame = 4

    private fun samples(from: Int, count: Int) = ShortArray(count) { (from + it).toShort() }

    @Test
    fun `gives silence until primed, then the mic in order`() {
        val buf = PcmJitterBuffer(targetSamples = 2 * frame, maxSamples = 5 * frame)
        val out = ShortArray(frame)
        buf.offer(samples(1, frame), frame)
        assertEquals(0, buf.take(out, frame))
        assertArrayEquals(ShortArray(frame), out)
        buf.offer(samples(5, frame), frame)
        assertEquals(frame, buf.take(out, frame))
        assertArrayEquals(samples(1, frame), out)
        assertEquals(frame, buf.take(out, frame))
        assertArrayEquals(samples(5, frame), out)
    }

    @Test
    fun `running dry pads with silence and re-primes`() {
        val buf = PcmJitterBuffer(targetSamples = 2 * frame, maxSamples = 5 * frame)
        val out = ShortArray(frame)
        buf.offer(samples(1, 2 * frame + 2), 2 * frame + 2)
        buf.take(out, frame)
        buf.take(out, frame)
        assertEquals(2, buf.take(out, frame))
        assertArrayEquals(shortArrayOf(9, 10, 0, 0), out)
        assertEquals(1, buf.underflows)
        // One frame isn't enough to restart.
        buf.offer(samples(20, frame), frame)
        assertEquals(0, buf.take(out, frame))
    }

    @Test
    fun `overflow drops the oldest down to the target`() {
        val buf = PcmJitterBuffer(targetSamples = 2 * frame, maxSamples = 5 * frame)
        buf.offer(samples(0, 5 * frame), 5 * frame)
        buf.offer(samples(100, frame), frame)
        assertEquals(2 * frame, buf.available)
        assertEquals(1, buf.overflows)
        val out = ShortArray(frame)
        buf.take(out, frame)
        // The newest audio is kept: the end of the first batch, then the new frame.
        assertArrayEquals(samples(5 * frame - frame, frame), out)
        buf.take(out, frame)
        assertArrayEquals(samples(100, frame), out)
    }

    @Test
    fun `a single huge offer keeps only the newest target`() {
        val buf = PcmJitterBuffer(targetSamples = 2 * frame, maxSamples = 5 * frame)
        buf.offer(samples(0, 10 * frame), 10 * frame)
        assertEquals(2 * frame, buf.available)
        val out = ShortArray(2 * frame)
        buf.take(out, 2 * frame)
        assertArrayEquals(samples(8 * frame, 2 * frame), out)
    }

    /** Feeds [ticks] loopback frames, with the mic delivering [micPerTick] samples on average in bursts of [burst]. */
    private fun simulate(micPerTick: Double, ticks: Int, burst: Int): PcmJitterBuffer {
        val f = 1920
        val buf = PcmJitterBuffer(targetSamples = 2 * f, maxSamples = 5 * f)
        val out = ShortArray(f)
        var owed = 0.0
        var pending = 0
        repeat(ticks) {
            owed += micPerTick
            val whole = owed.toInt() / 2 * 2
            owed -= whole
            pending += whole
            while (pending >= burst) {
                buf.offer(ShortArray(burst), burst)
                pending -= burst
            }
            buf.take(out, f)
            assertTrue(buf.available <= 5 * f)
        }
        return buf
    }

    @Test
    fun `matched clocks with bursty delivery never glitch after priming`() {
        // 40 ms bursts against 20 ms frames.
        val buf = simulate(micPerTick = 1920.0, ticks = 10_000, burst = 3840)
        assertEquals(0, buf.overflows)
        assertEquals(0, buf.underflows)
    }

    @Test
    fun `a fast mic is caught up rarely and latency stays bounded`() {
        // 0.5% fast: well beyond real crystal drift, to exercise it.
        val buf = simulate(micPerTick = 1920 * 1.005, ticks = 10_000, burst = 960)
        assertTrue(buf.overflows in 1..50)
        assertEquals(0, buf.underflows)
    }

    @Test
    fun `a slow mic gets a rare short gap instead of stalling the loopback`() {
        val buf = simulate(micPerTick = 1920 * 0.995, ticks = 10_000, burst = 960)
        assertTrue(buf.underflows in 1..50)
        assertEquals(0, buf.overflows)
    }

    @Test
    fun `sound note covers a missing helper and a capture that lost its sound`() {
        assertNull(ReplaySoundStatus.note(soundOn = false, helperDown = true, armed = true, videoOnly = true))
        assertEquals(ReplaySoundStatus.Note.NO_HELPER, ReplaySoundStatus.note(soundOn = true, helperDown = true, armed = false, videoOnly = false))
        assertEquals(ReplaySoundStatus.Note.VIDEO_ONLY, ReplaySoundStatus.note(soundOn = true, helperDown = false, armed = true, videoOnly = true))
        assertNull(ReplaySoundStatus.note(soundOn = true, helperDown = false, armed = false, videoOnly = true))
        assertNull(ReplaySoundStatus.note(soundOn = true, helperDown = false, armed = true, videoOnly = false))
    }
}
