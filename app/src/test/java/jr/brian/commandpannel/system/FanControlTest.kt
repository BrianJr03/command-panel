package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.FanControl.CUSTOM
import jr.brian.commandpannel.system.FanControl.OFF
import jr.brian.commandpannel.system.FanControl.QUIET
import jr.brian.commandpannel.system.FanControl.SMART
import jr.brian.commandpannel.system.FanControl.SPORT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FanControlTest {

    @Test
    fun optionsMatchStockPerPerformanceMode() {
        assertEquals(listOf(OFF, QUIET, SMART, SPORT), FanControl.options(0))
        assertEquals(listOf(QUIET, SMART, SPORT), FanControl.options(1))
        assertEquals(listOf(SMART, SPORT, CUSTOM), FanControl.options(2))
    }

    @Test
    fun customOnlyAtHighPerformance() {
        assertEquals(listOf(2), (0..2).filter { CUSTOM in FanControl.options(it) })
    }

    @Test
    fun hiddenModesFallBackToSmart() {
        assertEquals(SMART, FanControl.modeAfterPerformanceChange(1, CUSTOM))
        assertEquals(SMART, FanControl.modeAfterPerformanceChange(0, CUSTOM))
        assertEquals(SMART, FanControl.modeAfterPerformanceChange(1, OFF))
        assertEquals(SMART, FanControl.modeAfterPerformanceChange(2, OFF))
        assertEquals(SMART, FanControl.modeAfterPerformanceChange(2, QUIET))
    }

    @Test
    fun allowedModesAreKept() {
        assertEquals(CUSTOM, FanControl.modeAfterPerformanceChange(2, CUSTOM))
        assertEquals(OFF, FanControl.modeAfterPerformanceChange(0, OFF))
        assertEquals(QUIET, FanControl.modeAfterPerformanceChange(1, QUIET))
        assertEquals(SPORT, FanControl.modeAfterPerformanceChange(2, SPORT))
    }

    @Test
    fun speedRangeTakesEitherOrder() {
        assertEquals(25000..50000, FanControl.speedRange(50000, 25000))
        assertEquals(25000..50000, FanControl.speedRange(25000, 50000))
    }

    @Test
    fun unusableBoundsGiveNoRange() {
        assertNull(FanControl.speedRange(null, 25000))
        assertNull(FanControl.speedRange(50000, null))
        assertNull(FanControl.speedRange(0, 0))
        assertNull(FanControl.speedRange(-1, 100))
    }

    @Test
    fun speedAndFractionMapAcrossTheRange() {
        val r = 25000..50000
        assertEquals(0f, FanControl.speedToFraction(25000, r), 0.0001f)
        assertEquals(0.5f, FanControl.speedToFraction(37500, r), 0.0001f)
        assertEquals(1f, FanControl.speedToFraction(50000, r), 0.0001f)
        assertEquals(25000, FanControl.fractionToSpeed(0f, r))
        assertEquals(37500, FanControl.fractionToSpeed(0.5f, r))
        assertEquals(50000, FanControl.fractionToSpeed(1f, r))
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val r = 25000..50000
        assertEquals(0f, FanControl.speedToFraction(1000, r), 0.0001f)
        assertEquals(1f, FanControl.speedToFraction(90000, r), 0.0001f)
        assertEquals(25000, FanControl.fractionToSpeed(-0.5f, r))
        assertEquals(50000, FanControl.fractionToSpeed(1.5f, r))
    }

    @Test
    fun mappingRoundTrips() {
        val r = 25000..50000
        for (speed in listOf(25000, 30000, 41234, 50000)) {
            assertEquals(speed.toFloat(), FanControl.fractionToSpeed(FanControl.speedToFraction(speed, r), r).toFloat(), 1f)
        }
    }

    @Test
    fun percentIsSpeedOverTheRangeTop() {
        val r = 25000..50000
        assertEquals(50, FanControl.speedPercent(25000, r))
        assertEquals(75, FanControl.speedPercent(37500, r))
        assertEquals(100, FanControl.speedPercent(50000, r))
        assertEquals(100, FanControl.speedPercent(70000, r))
    }

    @Test
    fun fallbackRangeMatchesTheThor() {
        assertEquals(25000..50000, FanControl.FALLBACK_SPEED_RANGE)
    }
}
