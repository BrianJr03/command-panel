package jr.brian.commandpannel.system

import kotlin.math.roundToInt

/** The bottom screen's volume is the vendor key `secondary_screen_volume_level`, an int from 0 to [BOTTOM_VOLUME_MAX]. */
const val BOTTOM_VOLUME_MAX = 15

/** The bottom volume level for a slider [fraction], rounded to the nearest of the 0..[BOTTOM_VOLUME_MAX] steps. */
fun bottomVolumeLevel(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * BOTTOM_VOLUME_MAX).roundToInt()

/**
 * Decides when a dragged stepped slider writes its level. Each write is a privileged `settings put`, so a drag only
 * writes when the level changes, and at most once per [minIntervalMs]. A level held back by the interval is kept as
 * [pending] for a trailing [flush], and the release ([onCommit]) always writes the final level if it isn't written yet.
 * Pure (the caller passes the clock), so it is unit-tested.
 */
class SteppedWriteThrottle(private val minIntervalMs: Long) {
    /** The last level handed out for writing, or null before the first. */
    var lastWritten: Int? = null
        private set
    /** A level the interval held back, waiting for [flush]. */
    var pending: Int? = null
        private set
    private var lastWriteAt = Long.MIN_VALUE

    /** A drag moved the slider to [level] at [now]: returns the level to write now, or null. */
    fun onDrag(level: Int, now: Long): Int? {
        if (level == lastWritten) {
            pending = null
            return null
        }
        if (lastWriteAt != Long.MIN_VALUE && now - lastWriteAt < minIntervalMs) {
            pending = level
            return null
        }
        return take(level, now)
    }

    /** How long after [now] the [pending] level is due, or null when nothing is pending. */
    fun flushDelay(now: Long): Long? =
        if (pending == null) null else (lastWriteAt + minIntervalMs - now).coerceAtLeast(0L)

    /** Returns the [pending] level once its interval is up, or null. */
    fun flush(now: Long): Int? {
        val level = pending ?: return null
        if (now - lastWriteAt < minIntervalMs) return null
        return take(level, now)
    }

    /** The slider was released (or tapped) at [level]: returns it unless it is already written. */
    fun onCommit(level: Int, now: Long): Int? {
        pending = null
        return if (level == lastWritten) null else take(level, now)
    }

    /** The level changed somewhere else (a volume key, another app), so the next drag compares against [level]. */
    fun sync(level: Int) {
        lastWritten = level
        pending = null
    }

    private fun take(level: Int, now: Long): Int {
        lastWritten = level
        lastWriteAt = now
        pending = null
        return level
    }
}

/**
 * Keeps a volume slider from being yanked by its own echoes. While the user drags, and for [holdMs] after the release
 * or after the last write lands, change notifications for that volume are ignored, so a late echo of an older level
 * can't snap the slider back. Pure (the caller passes the clock), so it is unit-tested.
 */
class VolumeEchoGuard(private val holdMs: Long) {
    var dragging = false
        private set
    private var holdUntil = Long.MIN_VALUE

    fun onDrag() {
        dragging = true
    }

    /** The drag ended (or a single tap / key write happened) at [now]. */
    fun onRelease(now: Long) {
        dragging = false
        holdUntil = maxOf(holdUntil, now + holdMs)
    }

    /** A write for this volume finished at [now]; its echo is still on the way. */
    fun onWriteDone(now: Long) {
        holdUntil = maxOf(holdUntil, now + holdMs)
    }

    /** Whether a change notification at [now] should be ignored. */
    fun ignoring(now: Long): Boolean = dragging || now < holdUntil

    /** When the hold ends (for a catch-up read), or null while dragging. */
    fun holdEndsAt(): Long? = if (dragging) null else holdUntil
}
