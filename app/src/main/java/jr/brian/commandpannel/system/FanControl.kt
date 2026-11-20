package jr.brian.commandpannel.system

import kotlin.math.roundToInt

/**
 * Fan tile rules, kept pure so they can be unit tested. `fan_mode`: 0 off, 1 quiet, 4 smart, 5 sport, 6 custom.
 *
 * Stock (`FanControlQsItem`) hides options by `performance_mode`: 0 hides Custom; 1 hides Off + Custom; 2 hides Off +
 * Quiet. So Custom only shows at High performance. Its seekbar runs from FanProvider txn 2 (min) to txn 1 (max) and
 * writes `fan_speed` directly.
 */
object FanControl {
    const val OFF = 0
    const val QUIET = 1
    const val SMART = 4
    const val SPORT = 5
    const val CUSTOM = 6

    /** Used when FanProvider can't be read. Matches what a Thor reports (txn 2 = 25000, txn 1 = 50000). */
    val FALLBACK_SPEED_RANGE: IntRange = 25000..50000

    /**
     * Slider left to right goes from the range's low end to its high end, like stock's seekbar. Checked on a Thor: a
     * higher `fan_speed` is a faster fan (25000 gives 50 % duty, 50000 gives 100 %), so this stays false.
     */
    private const val INVERTED = false

    /** The fan modes the dialog offers at [performanceMode], in stock's order. */
    fun options(performanceMode: Int): List<Int> = when (performanceMode) {
        1 -> listOf(QUIET, SMART, SPORT)
        2 -> listOf(SMART, SPORT, CUSTOM)
        else -> listOf(OFF, QUIET, SMART, SPORT)
    }

    /**
     * The fan mode to keep after performance changes to [performanceMode]. A mode that level hides (Custom below High,
     * Off or Quiet at High) falls back to Smart, which every level offers, so the tile never shows a mode the dialog
     * can't select. Stock's app leaves `fan_mode` alone here because the firmware resets it when `performance_mode`
     * is written; this is the safety net for when the firmware leaves a hidden mode behind (or its reset is off).
     * The firmware applies Custom at any level; the dialog only hides it below High, like stock.
     */
    fun modeAfterPerformanceChange(performanceMode: Int, fanMode: Int): Int =
        if (fanMode in options(performanceMode)) fanMode else SMART

    /** Builds the slider range from FanProvider's two bounds, whichever order they come in; null if unusable. */
    fun speedRange(a: Int?, b: Int?): IntRange? {
        if (a == null || b == null || a == b || a < 0 || b < 0) return null
        return minOf(a, b)..maxOf(a, b)
    }

    /** Slider position (0..1) for a `fan_speed` value. */
    fun speedToFraction(speed: Int, range: IntRange): Float {
        val span = (range.last - range.first).toFloat()
        if (span <= 0f) return 0f
        val f = ((speed.coerceIn(range) - range.first) / span).coerceIn(0f, 1f)
        return if (INVERTED) 1f - f else f
    }

    /** The fan duty a `fan_speed` asks for, in percent of the range's top (duty tracks `fan_speed / max`). */
    fun speedPercent(speed: Int, range: IntRange): Int =
        if (range.last <= 0) 0 else (speed.coerceIn(range).toLong() * 100 / range.last).toInt()

    /** `fan_speed` value for a slider position (0..1). */
    fun fractionToSpeed(fraction: Float, range: IntRange): Int {
        val f = fraction.coerceIn(0f, 1f).let { if (INVERTED) 1f - it else it }
        return (range.first + f * (range.last - range.first)).roundToInt().coerceIn(range)
    }
}
