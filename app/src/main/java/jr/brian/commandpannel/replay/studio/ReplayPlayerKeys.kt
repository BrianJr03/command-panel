package jr.brian.commandpannel.replay.studio

import android.view.KeyEvent

/** What a key does in the Replay Studio player. */
enum class PlayerCommand { TOGGLE_PLAY, SEEK_BACK, SEEK_FORWARD, PREVIOUS_CLIP, NEXT_CLIP, CLOSE }

/** How far D-pad left/right seeks. */
const val PLAYER_SEEK_STEP_MS = 5_000L

/**
 * The player's gamepad map: A play/pause, D-pad left/right seek, L1/R1 previous/next clip, B close. Keyboard and media
 * keys map the same way. Other keys return null; they only bring the controls back.
 */
fun playerCommandFor(keyCode: Int): PlayerCommand? = when (keyCode) {
    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerCommand.TOGGLE_PLAY
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerCommand.SEEK_BACK
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerCommand.SEEK_FORWARD
    KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP -> PlayerCommand.PREVIOUS_CLIP
    KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_DOWN -> PlayerCommand.NEXT_CLIP
    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> PlayerCommand.CLOSE
    else -> null
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
