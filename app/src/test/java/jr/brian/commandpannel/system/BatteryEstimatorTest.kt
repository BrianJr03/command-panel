package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryEstimatorTest {

    // AYN Thor values observed over adb: charge_full 5,835,000 µAh, 80% -> 4,668,000 µAh
    private val fullUah = 5_835_000L

    private fun sample(
        t: Long,
        flow: BatteryFlow? = BatteryFlow.DISCHARGING,
        level: Int = 80,
        counter: Long = fullUah * level / 100,
        full: Long = fullUah,
        current: Long = -1_000_000L,
        unit: BatteryCurrentUnit = BatteryCurrentUnit.MICROAMPS,
        limit: Boolean = false,
        systemMs: Long = -1L
    ) = BatteryEstimator.Sample(
        elapsedMs = t,
        flow = flow,
        levelPercent = level,
        chargeCounterUah = counter,
        chargeFullUah = full,
        currentRaw = current,
        currentUnit = unit,
        chargeLimitEnabled = limit,
        systemChargeTimeRemainingMs = systemMs
    )

    /** Feeds samples every 10 s from [from] to [to] (inclusive) and returns the last estimate. */
    private fun BatteryEstimator.feed(from: Long, to: Long, build: (Long) -> BatteryEstimator.Sample): BatteryEstimate {
        var last: BatteryEstimate = BatteryEstimate.Hidden
        var t = from
        while (t <= to) {
            last = update(build(t))
            t += 10_000L
        }
        return last
    }

    @Test
    fun `discharging divides remaining charge by smoothed current`() {
        val est = BatteryEstimator()
        // 4,668,000 µAh / 2,000,000 µA = 2.334 h = 140 min
        val result = est.feed(0L, 60_000L) { sample(it, current = -2_000_000L) }
        assertEquals(BatteryEstimate.TimeToEmpty(140L), result)
        assertEquals("~2h 20m", BatteryEstimator.formatShort(result))
    }

    @Test
    fun `sign of current is ignored and direction comes from flow`() {
        val positive = BatteryEstimator().feed(0L, 60_000L) { sample(it, current = 2_000_000L) }
        val negative = BatteryEstimator().feed(0L, 60_000L) { sample(it, current = -2_000_000L) }
        assertEquals(positive, negative)
    }

    @Test
    fun `charging to 100 percent uses full capacity as target`() {
        val est = BatteryEstimator()
        // level 50: remaining = 5,835,000 - 2,917,500 = 2,917,500 µAh at 3 A = 0.9725 h = 58 min
        val result = est.feed(0L, 60_000L) {
            sample(it, flow = BatteryFlow.CHARGING, level = 50, current = 3_000_000L)
        }
        assertEquals(BatteryEstimate.TimeToFull(58L, 100), result)
        assertEquals("full in 58m", BatteryEstimator.formatShort(result))
    }

    @Test
    fun `charging with 80 percent limit targets 80 percent of full`() {
        val est = BatteryEstimator()
        // target 4,668,000 - 2,917,500 = 1,750,500 µAh at 3 A = 0.5835 h = 35 min
        val result = est.feed(0L, 60_000L) {
            sample(it, flow = BatteryFlow.CHARGING, level = 50, current = 3_000_000L, limit = true)
        }
        assertEquals(BatteryEstimate.TimeToFull(35L, 80), result)
    }

    @Test
    fun `system charge estimate is preferred only when the limit is off`() {
        val noLimit = BatteryEstimator().feed(0L, 60_000L) {
            sample(it, flow = BatteryFlow.CHARGING, level = 50, current = 3_000_000L, systemMs = 45L * 60_000L)
        }
        assertEquals(BatteryEstimate.TimeToFull(45L, 100), noLimit)

        val withLimit = BatteryEstimator().feed(0L, 60_000L) {
            sample(it, flow = BatteryFlow.CHARGING, level = 50, current = 3_000_000L, limit = true, systemMs = 45L * 60_000L)
        }
        assertEquals(BatteryEstimate.TimeToFull(35L, 80), withLimit)
    }

    @Test
    fun `at the limit or full reports the target state`() {
        // Thor at the 80% limit on AC: status discharging, plugged, current_now 0
        val atLimit = BatteryEstimator().update(
            sample(0L, flow = BatteryFlow.PLUGGED_IDLE, level = 80, current = 0L, limit = true)
        )
        assertEquals(BatteryEstimate.AtTarget(chargeLimitReached = true), atLimit)
        assertEquals("Limit reached", BatteryEstimator.formatShort(atLimit))

        val full = BatteryEstimator().update(sample(0L, flow = BatteryFlow.PLUGGED_IDLE, level = 100, current = 0L))
        assertEquals("Full", BatteryEstimator.formatShort(full))

        val chargingPastLimit = BatteryEstimator().feed(0L, 60_000L) {
            sample(it, flow = BatteryFlow.CHARGING, level = 80, current = 500_000L, limit = true)
        }
        assertEquals(BatteryEstimate.AtTarget(chargeLimitReached = true), chargingPastLimit)

        // Plugged, not charging, not full (charging separation bypass): nothing to estimate
        val bypass = BatteryEstimator().update(sample(0L, flow = BatteryFlow.PLUGGED_IDLE, level = 60, current = 0L))
        assertEquals(BatteryEstimate.Hidden, bypass)
    }

    @Test
    fun `implausible inputs hide the estimate`() {
        val tooSmall = BatteryEstimator().feed(0L, 60_000L) { sample(it, current = 4_000L) } // 4 mA
        assertEquals(BatteryEstimate.Hidden, tooSmall)
        val tooLarge = BatteryEstimator().feed(0L, 60_000L) { sample(it, current = 12_000_000L) } // 12 A
        assertEquals(BatteryEstimate.Hidden, tooLarge)
        val noCounter = BatteryEstimator().feed(0L, 60_000L) { sample(it, counter = 0L) }
        assertEquals(BatteryEstimate.Hidden, noCounter)
        val unknownFlow = BatteryEstimator().update(sample(0L, flow = null))
        assertEquals(BatteryEstimate.Hidden, unknownFlow)
        assertNull(BatteryEstimator.formatShort(BatteryEstimate.Hidden))
    }

    @Test
    fun `no estimate until warm-up has elapsed`() {
        val est = BatteryEstimator()
        assertEquals(BatteryEstimate.WarmingUp, est.update(sample(0L)))
        assertEquals(BatteryEstimate.WarmingUp, est.update(sample(10_000L)))
        assertEquals(BatteryEstimate.WarmingUp, est.update(sample(20_000L)))
        assertTrue(est.update(sample(30_000L)) is BatteryEstimate.TimeToEmpty)
        assertNull(BatteryEstimator.formatShort(BatteryEstimate.WarmingUp))
    }

    @Test
    fun `average resets and warms up again when the charging state changes`() {
        val est = BatteryEstimator()
        est.feed(0L, 120_000L) { sample(it, current = -1_000_000L) }
        assertEquals(1_000_000.0, est.averageCurrentMicroamps, 1.0)

        // Plug in: average restarts from the new current and the estimate warms up again
        val first = est.update(sample(130_000L, flow = BatteryFlow.CHARGING, level = 50, current = 3_000_000L))
        assertEquals(BatteryEstimate.WarmingUp, first)
        assertEquals(3_000_000.0, est.averageCurrentMicroamps, 1.0)

        // Unplug again
        est.update(sample(140_000L, current = -1_500_000L))
        assertEquals(1_500_000.0, est.averageCurrentMicroamps, 1.0)
    }

    @Test
    fun `exponential average follows a step change over the smoothing window`() {
        val est = BatteryEstimator()
        est.feed(0L, 60_000L) { sample(it, current = -1_000_000L) }
        // One window (90 s) after a step to 2 A the average should be ~63% of the way there
        est.feed(70_000L, 150_000L) { sample(it, current = -2_000_000L) }
        val avg = est.averageCurrentMicroamps
        assertTrue("avg=$avg", avg in 1_550_000.0..1_700_000.0)
    }

    @Test
    fun `long sampling gap restarts warm-up`() {
        val est = BatteryEstimator()
        est.feed(0L, 60_000L) { sample(it) }
        assertEquals(BatteryEstimate.WarmingUp, est.update(sample(60_000L + 10 * 60_000L)))
    }

    @Test
    fun `milliamp readings are normalized to microamps`() {
        assertEquals(2_000_000L, BatteryCurrentUnit.MILLIAMPS.toMicroamps(2_000L))
        assertEquals(-2_000_000L, BatteryCurrentUnit.MILLIAMPS.toMicroamps(-2_000L))
        assertEquals(2_000_000L, BatteryCurrentUnit.MICROAMPS.toMicroamps(2_000_000L))

        val ma = BatteryEstimator().feed(0L, 60_000L) {
            sample(it, current = -2_000L, unit = BatteryCurrentUnit.MILLIAMPS)
        }
        val ua = BatteryEstimator().feed(0L, 60_000L) { sample(it, current = -2_000_000L) }
        assertEquals(ua, ma)
        // A mA device misconfigured as µA reads as 2 mA and is hidden instead of showing nonsense
        val misconfigured = BatteryEstimator().feed(0L, 60_000L) { sample(it, current = -2_000L) }
        assertEquals(BatteryEstimate.Hidden, misconfigured)
    }

    @Test
    fun `full capacity is derived from level when charge_full is missing or inconsistent`() {
        val derived = BatteryEstimator().feed(0L, 60_000L) {
            sample(it, flow = BatteryFlow.CHARGING, level = 50, full = -1L, current = 3_000_000L)
        }
        assertEquals(BatteryEstimate.TimeToFull(58L, 100), derived)
    }

    @Test
    fun `results are clamped to 99 hours and durations format compactly`() {
        val huge = BatteryEstimator().feed(0L, 60_000L) {
            sample(it, counter = 9_000_000_000L, full = -1L, level = 100, current = -6_000L)
        }
        assertEquals(BatteryEstimate.TimeToEmpty(BatteryEstimator.MAX_MINUTES), huge)
        assertEquals("48m", BatteryEstimator.formatDuration(48L))
        assertEquals("2h", BatteryEstimator.formatDuration(120L))
        assertEquals("2h 15m", BatteryEstimator.formatDuration(135L))
        assertEquals("99h", BatteryEstimator.formatDuration(100_000L))
    }

    @Test
    fun `battery flow is derived from status and plug state`() {
        val charging = android.os.BatteryManager.BATTERY_STATUS_CHARGING
        val discharging = android.os.BatteryManager.BATTERY_STATUS_DISCHARGING
        assertEquals(BatteryFlow.CHARGING, BatteryEstimateMonitor.flowFor(charging, 1))
        assertEquals(BatteryFlow.PLUGGED_IDLE, BatteryEstimateMonitor.flowFor(discharging, 1))
        assertEquals(BatteryFlow.DISCHARGING, BatteryEstimateMonitor.flowFor(discharging, 0))
        assertNull(BatteryEstimateMonitor.flowFor(-1, 0))
    }

}
