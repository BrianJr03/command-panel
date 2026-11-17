package jr.brian.commandpannel.system

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToLong

/** Unit of the raw battery `current_now` reading on a device ([DeviceProfile.batteryCurrentUnit]). */
enum class BatteryCurrentUnit {
    MICROAMPS,
    MILLIAMPS;

    fun toMicroamps(raw: Long): Long = when (this) {
        MICROAMPS -> raw
        MILLIAMPS -> raw * 1_000L
    }
}

/** Charging direction derived from `BatteryManager` status / plug state, never from the current's sign. */
enum class BatteryFlow {
    DISCHARGING,
    CHARGING,

    /** Plugged in but not charging: full, at the 80% limit, or bypass (charging separation). */
    PLUGGED_IDLE
}

sealed class BatteryEstimate {
    /** Not enough trustworthy data (implausible readings, unknown direction, or feature off). */
    data object Hidden : BatteryEstimate()

    /** Collecting samples after start-up or a plug / unplug event. */
    data object WarmingUp : BatteryEstimate()

    data class TimeToEmpty(val minutes: Long) : BatteryEstimate()

    data class TimeToFull(val minutes: Long, val targetPercent: Int) : BatteryEstimate()

    data class AtTarget(val chargeLimitReached: Boolean) : BatteryEstimate()
}

/**
 * Pure battery time-remaining estimator (no Android dependencies).
 *
 * - The battery current is smoothed with an exponential moving average whose time constant is
 *   [SMOOTHING_WINDOW_MS]; `alpha = 1 - e^(-dt / window)` keeps the smoothing independent of the
 *   sampling cadence.
 * - No estimate is shown until [WARM_UP_MS] of samples have been collected in the current
 *   [BatteryFlow]; the average resets whenever the flow changes or after a sampling gap longer
 *   than twice the window (e.g. screen off).
 * - Discharging: `hours = chargeCounter / avgCurrent`.
 * - Charging: `hours = (target - chargeCounter) / avgCurrent`, target = 80% of full capacity
 *   while the 80% Charge Limit is on, else 100%. A positive system estimate
 *   (`BatteryManager.computeChargeTimeRemaining()`) is preferred when the limit is off, since
 *   the system estimate always targets 100%.
 * - Results are clamped to [MAX_MINUTES] (99 h).
 */
class BatteryEstimator(
    private val smoothingWindowMs: Long = SMOOTHING_WINDOW_MS,
    private val warmUpMs: Long = WARM_UP_MS
) {

    companion object {
        const val SMOOTHING_WINDOW_MS = 90_000L
        const val WARM_UP_MS = 30_000L
        const val MIN_PLAUSIBLE_CURRENT_UA = 5_000L // 5 mA
        const val MAX_PLAUSIBLE_CURRENT_UA = 10_000_000L // 10 A
        const val MAX_MINUTES = 99L * 60L
        const val CHARGE_LIMIT_PERCENT = 80

        /** Short label for the telemetry panel / status bar, or `null` when nothing should be shown. */
        fun formatShort(estimate: BatteryEstimate): String? = when (estimate) {
            is BatteryEstimate.TimeToEmpty -> "~${formatDuration(estimate.minutes)}"
            is BatteryEstimate.TimeToFull -> "full in ${formatDuration(estimate.minutes)}"
            is BatteryEstimate.AtTarget -> if (estimate.chargeLimitReached) "Limit reached" else "Full"
            BatteryEstimate.Hidden,
            BatteryEstimate.WarmingUp -> null
        }

        fun formatDuration(minutes: Long): String {
            val m = minutes.coerceIn(0L, MAX_MINUTES)
            val h = m / 60L
            val rem = m % 60L
            return when {
                h == 0L -> "${rem}m"
                rem == 0L -> "${h}h"
                else -> "${h}h ${rem}m"
            }
        }
    }

    data class Sample(
        val elapsedMs: Long,
        val flow: BatteryFlow?,
        val levelPercent: Int,
        /** Remaining charge in µAh (`BATTERY_PROPERTY_CHARGE_COUNTER` / `charge_counter`). */
        val chargeCounterUah: Long,
        /** Full capacity in µAh (`charge_full`), or `<= 0` when unknown. */
        val chargeFullUah: Long,
        /** Raw `current_now` reading in [currentUnit]; its sign is ignored. */
        val currentRaw: Long,
        val currentUnit: BatteryCurrentUnit = BatteryCurrentUnit.MICROAMPS,
        val chargeLimitEnabled: Boolean,
        /** `BatteryManager.computeChargeTimeRemaining()` in ms, or `<= 0` when unavailable. */
        val systemChargeTimeRemainingMs: Long = -1L
    )

    private var flow: BatteryFlow? = null
    private var flowStartedElapsedMs: Long = 0L
    private var lastSampleElapsedMs: Long = Long.MIN_VALUE
    private var avgCurrentUa: Double = Double.NaN
    private var sampleCount: Int = 0

    /** Current smoothed |current| in µA, or `NaN` before the first usable sample (for tests/diagnostics). */
    val averageCurrentMicroamps: Double get() = avgCurrentUa

    fun reset() {
        flow = null
        flowStartedElapsedMs = 0L
        lastSampleElapsedMs = Long.MIN_VALUE
        avgCurrentUa = Double.NaN
        sampleCount = 0
    }

    private fun restartAverage(sample: Sample) {
        flow = sample.flow
        flowStartedElapsedMs = sample.elapsedMs
        avgCurrentUa = Double.NaN
        sampleCount = 0
    }

    fun update(sample: Sample): BatteryEstimate {
        val gapTooLong = lastSampleElapsedMs != Long.MIN_VALUE &&
            sample.elapsedMs - lastSampleElapsedMs > smoothingWindowMs * 2
        if (sample.flow != flow || gapTooLong || lastSampleElapsedMs == Long.MIN_VALUE) {
            restartAverage(sample)
        }
        val dtMs = if (lastSampleElapsedMs == Long.MIN_VALUE) 0L else sample.elapsedMs - lastSampleElapsedMs
        lastSampleElapsedMs = sample.elapsedMs

        val currentFlow = sample.flow ?: return BatteryEstimate.Hidden

        val fullUah = resolveFullCapacityUah(sample)
        if (currentFlow == BatteryFlow.PLUGGED_IDLE) {
            return plugIdleEstimate(sample, fullUah)
        }

        if (sample.chargeCounterUah <= 0L) return BatteryEstimate.Hidden
        val currentUa = abs(sample.currentUnit.toMicroamps(sample.currentRaw))
        if (currentUa < MIN_PLAUSIBLE_CURRENT_UA || currentUa > MAX_PLAUSIBLE_CURRENT_UA) {
            return BatteryEstimate.Hidden
        }

        avgCurrentUa = if (avgCurrentUa.isNaN() || dtMs <= 0L) {
            currentUa.toDouble()
        } else {
            val alpha = 1.0 - exp(-dtMs.toDouble() / smoothingWindowMs.toDouble())
            avgCurrentUa + alpha * (currentUa - avgCurrentUa)
        }
        sampleCount++

        if (sampleCount < 2 || sample.elapsedMs - flowStartedElapsedMs < warmUpMs) {
            return BatteryEstimate.WarmingUp
        }

        return when (currentFlow) {
            BatteryFlow.DISCHARGING -> BatteryEstimate.TimeToEmpty(
                hoursToMinutes(sample.chargeCounterUah / avgCurrentUa)
            )
            BatteryFlow.CHARGING -> chargingEstimate(sample, fullUah)
            BatteryFlow.PLUGGED_IDLE -> BatteryEstimate.Hidden
        }
    }

    private fun chargingEstimate(sample: Sample, fullUah: Long): BatteryEstimate {
        val targetPercent = if (sample.chargeLimitEnabled) CHARGE_LIMIT_PERCENT else 100
        if (sample.levelPercent >= targetPercent) {
            return BatteryEstimate.AtTarget(chargeLimitReached = sample.chargeLimitEnabled)
        }
        if (!sample.chargeLimitEnabled && sample.systemChargeTimeRemainingMs > 0L) {
            val minutes = (sample.systemChargeTimeRemainingMs / 60_000.0).roundToLong()
            return BatteryEstimate.TimeToFull(minutes.coerceIn(0L, MAX_MINUTES), targetPercent)
        }
        if (fullUah <= 0L) return BatteryEstimate.Hidden
        val targetUah = fullUah * targetPercent / 100L
        val remainingUah = targetUah - sample.chargeCounterUah
        if (remainingUah <= 0L) {
            return BatteryEstimate.AtTarget(chargeLimitReached = sample.chargeLimitEnabled)
        }
        return BatteryEstimate.TimeToFull(hoursToMinutes(remainingUah / avgCurrentUa), targetPercent)
    }

    private fun plugIdleEstimate(sample: Sample, fullUah: Long): BatteryEstimate {
        if (sample.chargeLimitEnabled && sample.levelPercent >= CHARGE_LIMIT_PERCENT - 1) {
            return BatteryEstimate.AtTarget(chargeLimitReached = true)
        }
        val atFull = sample.levelPercent >= 100 ||
            (fullUah > 0L && sample.chargeCounterUah >= fullUah)
        // Otherwise plugged but not charging (e.g. charging separation): no meaningful estimate
        return if (atFull) BatteryEstimate.AtTarget(chargeLimitReached = false) else BatteryEstimate.Hidden
    }

    private fun resolveFullCapacityUah(sample: Sample): Long {
        val counter = sample.chargeCounterUah
        val reported = sample.chargeFullUah
        // Trust charge_full unless it is clearly inconsistent with the remaining charge
        if (reported > 0L && (counter <= 0L || counter <= reported * 105L / 100L)) return reported
        if (counter > 0L && sample.levelPercent in 1..100) {
            return counter * 100L / sample.levelPercent
        }
        return -1L
    }

    private fun hoursToMinutes(hours: Double): Long {
        if (!hours.isFinite() || hours < 0.0) return 0L
        return (hours * 60.0).roundToLong().coerceIn(0L, MAX_MINUTES)
    }
}
