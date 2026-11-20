package jr.brian.commandpannel.replay.studio

import android.view.KeyEvent

/** What a key does in the Replay Studio player. */
enum class PlayerCommand { TOGGLE_PLAY, SEEK_BACK, SEEK_FORWARD, PREVIOUS_CLIP, NEXT_CLIP, TRIM, CLOSE }

/** How far D-pad left/right seeks. */
const val PLAYER_SEEK_STEP_MS = 5_000L

/**
 * The player's gamepad map: A play/pause, D-pad left/right seek, L1/R1 previous/next clip, Y trim (#51), B close.
 * Keyboard and media keys map the same way. Other keys return null; they only bring the controls back.
 */
fun playerCommandFor(keyCode: Int): PlayerCommand? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerCommand.TOGGLE_PLAY
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerCommand.SEEK_BACK
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerCommand.SEEK_FORWARD
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP -> PlayerCommand.PREVIOUS_CLIP
    KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_DOWN -> PlayerCommand.NEXT_CLIP
    KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_T -> PlayerCommand.TRIM
    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> PlayerCommand.CLOSE
    else -> null
}

/**
 * What a key does in the player's trim mode (#51). The shoulders set the cut instead of changing clips, so the
 * D-pad can keep seeking.
 */
enum class TrimCommand { TOGGLE_PLAY, SEEK_BACK, SEEK_FORWARD, SET_IN, SET_OUT, PREVIEW, SAVE, EXIT }

/** How far D-pad left/right seeks in trim mode: finer than [PLAYER_SEEK_STEP_MS], to place a cut. */
const val PLAYER_TRIM_SEEK_STEP_MS = 1_000L

/**
 * The trim-mode map: A play/pause, D-pad left/right seek 1 s, L1 set the in point, R1 set the out point, X preview the
 * cut, Y save a copy, B leave trim mode (the player stays open). Keyboard: I / O set in / out, P previews, S saves.
 * After a save the same keys answer the Share offer: A shares, B is done. Other keys return null.
 */
fun trimCommandFor(keyCode: Int): TrimCommand? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> TrimCommand.TOGGLE_PLAY
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> TrimCommand.SEEK_BACK
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> TrimCommand.SEEK_FORWARD
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_I -> TrimCommand.SET_IN
    KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_O -> TrimCommand.SET_OUT
    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_P -> TrimCommand.PREVIEW
    KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_S -> TrimCommand.SAVE
    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> TrimCommand.EXIT
    else -> null
}

/** Whether holding the key down repeats the trim command (seeking does). */
fun TrimCommand.repeats(): Boolean = this == TrimCommand.SEEK_BACK || this == TrimCommand.SEEK_FORWARD

/**
 * Trim in / out points in ms from the clip's start. An [outMs] of [END] means the clip's end (its length may not be
 * known yet). Setting one point past the other moves the other back to its default, so the cut is never empty. Pure.
 */
data class TrimPoints(val inMs: Long = 0L, val outMs: Long = END) {

    /** The out point, with [END] resolved against [durationMs] (unknown when not positive). */
    fun out(durationMs: Long): Long = if (durationMs > 0) outMs.coerceAtMost(durationMs) else outMs

    /** In point at [positionMs]; at or past the out point, the out point goes back to the end. */
    fun withIn(positionMs: Long, durationMs: Long): TrimPoints {
        val p = clampPosition(positionMs, durationMs)
        return if (p >= out(durationMs)) TrimPoints(p, END) else copy(inMs = p)
    }

    /** Out point at [positionMs]; at or before the in point, the in point goes back to the start. */
    fun withOut(positionMs: Long, durationMs: Long): TrimPoints {
        val p = clampPosition(positionMs, durationMs)
        return if (p <= inMs) TrimPoints(0L, p) else copy(outMs = p)
    }

    /** True when the points still cover the whole clip (nothing to cut). */
    fun isWholeClip(durationMs: Long): Boolean = inMs <= 0L && (outMs == END || (durationMs > 0 && outMs >= durationMs))

    /** The cut as fractions of the clip, for the progress bar. Null while the length is unknown. */
    fun fractions(durationMs: Long): ClosedFloatingPointRange<Float>? {
        if (durationMs <= 0) return null
        val d = durationMs.toFloat()
        return (inMs / d).coerceIn(0f, 1f)..(out(durationMs) / d).coerceIn(0f, 1f)
    }

    private fun clampPosition(positionMs: Long, durationMs: Long): Long =
        positionMs.coerceAtLeast(0L).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }

    companion object {
        const val END = Long.MAX_VALUE
    }
}

/** Whether holding the key down repeats the command (seeking does; the rest act once per press). */
fun PlayerCommand.repeats(): Boolean = this == PlayerCommand.SEEK_BACK || this == PlayerCommand.SEEK_FORWARD

/** Where a seek by [deltaMs] from [positionMs] lands, kept inside the clip. A negative [durationMs] means unknown. */
fun seekTarget(positionMs: Long, durationMs: Long, deltaMs: Long): Long {
    val target = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}

/**
 * The index [step] places away from the clip at [currentPath] in [paths] (the library, newest first; previous is -1,
 * next is +1), or null at either end or when the clip isn't in the list. Paths are matched exactly.
 */
fun neighborIndex(paths: List<String>, currentPath: String, step: Int): Int? {
    val i = paths.indexOf(currentPath)
    if (i < 0) return null
    return (i + step).takeIf { it in paths.indices }
}

/** Splits [ms] into whole minutes and seconds for the `player_time` format. */
fun clockParts(ms: Long): Pair<Long, Long> {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000L
    return totalSeconds / 60L to totalSeconds % 60L
}
