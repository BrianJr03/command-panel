package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    }

    @Test
    fun powerFractionIsTheMagnitudeOverZeroToTwentyFive() {
        assertEquals(0f, TelemetryReader.powerFraction(0f), 0.001f)          // idle: empty ring, not ~44%
        assertEquals(8f / 25f, TelemetryReader.powerFraction(8f), 0.001f)    // 8 W charging
        assertEquals(8f / 25f, TelemetryReader.powerFraction(-8f), 0.001f)   // 8 W draw fills the same, not less
        assertEquals(1f, TelemetryReader.powerFraction(25f), 0.001f)
        assertEquals(1f, TelemetryReader.powerFraction(-40f), 0.001f)        // clamped
    }

    @Test
    fun powerFractionGrowsWithDrawOnBattery() {
        assertTrue(TelemetryReader.powerFraction(-8f) > TelemetryReader.powerFraction(-4f))
    }

    @Test
    fun chargingIsPositivePowerAboveTheNoiseFloor() {
        assertTrue(TelemetryReader.isCharging(5f))
        assertFalse(TelemetryReader.isCharging(-5.79f))
        assertFalse(TelemetryReader.isCharging(0f))
        assertFalse(TelemetryReader.isCharging(0.05f)) // noise around idle
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

    @Test
    fun chooseFpsPrefersAFreshTaskSample() {
        assertEquals(30, TelemetryReader.chooseFps(taskFps = 29.6f, taskAgeMs = 400, globalFps = 120, cap = 120))
        assertEquals(60, TelemetryReader.chooseFps(taskFps = 60.2f, taskAgeMs = 0, globalFps = 120, cap = 120))
        assertEquals(0, TelemetryReader.chooseFps(taskFps = 0f, taskAgeMs = 400, globalFps = 120, cap = 120)) // paused game
    }

    @Test
    fun chooseFpsFallsBackToGlobal() {
        assertEquals(90, TelemetryReader.chooseFps(taskFps = null, taskAgeMs = null, globalFps = 90, cap = 120)) // helper down
        assertEquals(90, TelemetryReader.chooseFps(taskFps = -1f, taskAgeMs = -1, globalFps = 90, cap = 120)) // no game
        assertEquals(90, TelemetryReader.chooseFps(taskFps = 30f, taskAgeMs = 5_000, globalFps = 90, cap = 120)) // stale
        assertEquals(90, TelemetryReader.chooseFps(taskFps = 30f, taskAgeMs = -1, globalFps = 90, cap = 120)) // no report yet
    }

    @Test
    fun chooseFpsIsCapped() {
        assertEquals(60, TelemetryReader.chooseFps(taskFps = 75f, taskAgeMs = 100, globalFps = 0, cap = 60))
        assertEquals(60, TelemetryReader.chooseFps(taskFps = null, taskAgeMs = null, globalFps = 120, cap = 60))
    }
}
