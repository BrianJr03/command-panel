package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryReaderTest {

    @Test
    fun tempDividesByThousandWhenOutOfRange() {
        assertEquals(49, TelemetryReader.parseTempC("49700"))   // millidegrees
        assertEquals(49, TelemetryReader.parseTempC("49"))       // already °C
    }

    @Test
    fun cpuGhzAndGpuMhz() {
        assertEquals(1.84f, TelemetryReader.kHzToGhz("1843200")!!, 0.01f)
        assertEquals(401, TelemetryReader.hzToMhz("401000000"))
    }

    @Test
    fun powerWattsAndFraction() {
        // -1.485 A * 3.9 V ≈ -5.79 W
        val w = TelemetryReader.powerWatts("-1485022", "3900000")!!
        assertEquals(-5.79f, w, 0.1f)
        assertEquals(0.5f, TelemetryReader.powerFraction(2.5f), 0.02f) // ~midpoint of -20..25
    }

    @Test
    fun surfaceFlingerFrameParse() {
        assertEquals(0x00024f0eL, TelemetryReader.parseSurfaceFlingerFrames("Result: Parcel(00024f0e    '.O..')"))
        assertNull(TelemetryReader.parseSurfaceFlingerFrames("Result: Parcel(Error: 0xffffffff)"))
    }

    @Test
    fun fpsIsCeiledAndCapped() {
        assertEquals(60, TelemetryReader.fps(deltaFrames = 100, deltaMs = 1000, cap = 60)) // 100fps capped to 60
        assertEquals(46, TelemetryReader.fps(deltaFrames = 68, deltaMs = 1500, cap = 120)) // ceil(45.3)
    }
}
