package jr.brian.commandpannel.privileged

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayAudioTest {
    @Test
    fun `mixing saturates at the 16-bit range`() {
        val a = shortArrayOf(32767, -32768, 1000, -5, 7)
        val b = shortArrayOf(1, -1, 2000, 5, 99)
        PcmMixer.mixInto(a, b, 4)
        assertArrayEquals(shortArrayOf(32767, -32768, 3000, 0, 7), a)
    }

    @Test
    fun `pcm is written little endian`() {
        val out = ByteArray(4)
        PcmMixer.toBytes(shortArrayOf(0x1234, -2), 2, out)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xFE.toByte(), 0xFF.toByte()), out)
    }

    @Test
    fun `timestamps step by frames and stay on the wall clock`() {
        val clock = AudioPtsClock(48_000)
        // 960 frames = 20 ms chunks, arriving on time.
        val first = clock.next(960, 1_020_000)
        assertEquals(1_000_000, first)
        assertEquals(1_020_000, clock.next(960, 1_040_000))
        // Small jitter doesn't move the frame-counted timestamps.
        assertEquals(1_040_000, clock.next(960, 1_065_000))
    }

    @Test
    fun `a stall re-anchors and timestamps never go backward`() {
        val clock = AudioPtsClock(48_000)
        clock.next(960, 1_020_000)
        // 500 ms gap: re-anchor to the wall clock.
        val afterStall = clock.next(960, 1_560_000)
        assertEquals(1_540_000, afterStall)
        // A burst that arrives early can't step backward.
        val burst = clock.next(960, 1_400_000)
        assertTrue(burst > afterStall)
    }
}
