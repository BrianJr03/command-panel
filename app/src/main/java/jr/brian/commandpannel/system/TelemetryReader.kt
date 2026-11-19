package jr.brian.commandpannel.system

import kotlin.math.abs

/**
 * Pure parsing/formatting for the hardware telemetry the stock Settings tab shows, split out so it is unit-tested
 * without a device. Raw unit → display rules come straight from the teardown (NOTES.md §6).
 */
object TelemetryReader {

    /** Thermal zone millidegrees → °C; stock divides by 1000 when the raw value is out of the −30..250 range. */
    fun parseTempC(raw: String): Int? {
        val v = raw.trim().toDoubleOrNull() ?: return null
        val c = if (v in -30.0..250.0) v else v / 1000.0
        return c.toInt()
    }

    /** CPU `cpuinfo_cur_freq` is kHz; prime-core clock shown in GHz. */
    fun kHzToGhz(raw: String): Float? = raw.trim().toLongOrNull()?.let { it / 1_000_000f }

    /** GPU `cur_freq` is Hz; shown in MHz. */
    fun hzToMhz(raw: String): Int? = raw.trim().toLongOrNull()?.let { (it / 1_000_000L).toInt() }

    fun fraction(cur: String, max: String): Float? {
        val c = cur.trim().toDoubleOrNull() ?: return null
        val m = max.trim().toDoubleOrNull() ?: return null
        if (m <= 0.0) return null
        return (c / m).toFloat().coerceIn(0f, 1f)
    }

    /** Net battery/system power in watts: current(µA) × voltage(µV), each scaled to base units. */
    fun powerWatts(currentNowUa: String, voltageNowUv: String): Float? {
        val i = currentNowUa.trim().toDoubleOrNull() ?: return null
        val v = voltageNowUv.trim().toDoubleOrNull() ?: return null
        return ((i / 1_000_000.0) * (v / 1_000_000.0)).toFloat()
    }

    /** Top of the power ring's scale in watts (the stock charge ceiling; heavy draw on battery stays below it). */
    const val POWER_SCALE_MAX_W = 25f

    /** Net power below this magnitude counts as idle, so sensor noise around 0 W doesn't flip the charging label. */
    const val POWER_CHARGING_MIN_W = 0.1f

    /**
     * Power ring fraction: the magnitude of [watts] over 0..[POWER_SCALE_MAX_W], so the ring grows with the number
     * shown whether the battery is charging or draining. The direction is shown by [isCharging], not the ring.
     */
    fun powerFraction(watts: Float): Float = (abs(watts) / POWER_SCALE_MAX_W).coerceIn(0f, 1f)

    /** Positive net battery power means the battery is charging (it reads negative on battery). */
    fun isCharging(watts: Float): Boolean = watts > POWER_CHARGING_MIN_W

    /** Frame counter out of `service call SurfaceFlinger 1013` output, e.g. "Result: Parcel(00024f0e …)". */
    fun parseSurfaceFlingerFrames(output: String): Long? {
        val idx = output.indexOf("Parcel(")
        if (idx < 0) return null
        val hex = output.substring(idx + 7)
            .takeWhile { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            .take(8)
        // A valid frame-counter parcel is exactly 8 hex chars; "Parcel(Error: …)" is not one.
        return if (hex.length == 8) hex.toLongOrNull(16) else null
    }

    /** FPS from two frame-counter samples; capped at the panel's refresh rate. */
    fun fps(deltaFrames: Long, deltaMs: Long, cap: Int): Int {
        if (deltaMs <= 0 || deltaFrames < 0) return 0
        val raw = Math.ceil(deltaFrames * 1000.0 / deltaMs).toInt()
        return raw.coerceIn(0, cap)
    }

    /** A per-task reading older than this means the callback stopped (task gone, helper restarted). */
    const val TASK_FPS_MAX_AGE_MS = 2_000L

    /**
     * Per-task FPS when the sample is fresh, else the global composite FPS; capped at the panel's refresh rate.
     * The task value is already an average, so it is rounded rather than ceiled.
     */
    fun chooseFps(taskFps: Float?, taskAgeMs: Long?, globalFps: Int, cap: Int): Int {
        val fresh = taskFps != null && taskFps >= 0f && taskAgeMs != null && taskAgeMs in 0..TASK_FPS_MAX_AGE_MS
        val fps = if (fresh) Math.round(taskFps!!) else globalFps
        return fps.coerceIn(0, cap)
    }
}
