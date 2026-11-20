package jr.brian.commandpannel.replay.studio

/**
 * Decides when the Replay Buffer pauses for the in-app player and when it comes back (#46). The player's own video and
 * audio would otherwise end up in the buffer, so the buffer stops while the player is in front.
 *
 * The buffer must never stay off because of the player. It comes back when the player closes, when the player stops
 * checking in for [timeoutMs], or when the service sees the player is gone (it crashed, or its activity was destroyed
 * without saying so). A crash that takes the whole process down is covered by the service's restart, which arms the
 * buffer from the `replay_enabled` pref as usual.
 *
 * Pure: the service feeds it events on one thread and runs the returned [Action] on the Replay Buffer. `enabled` is the
 * user's `replay_enabled` pref at the time of the call, so a buffer the user turned off is never armed again here.
 */
class ReplayPauseGate(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {

    enum class Action {
        NONE,
        /** Stop the running buffer. */
        DISARM,
        /** Arm the buffer again (if it isn't already). */
        REARM,
    }

    /** True from the player opening until it closes, times out or is found gone. */
    var paused: Boolean = false
        private set

    private var lastSeenMs = 0L

    /**
     * The player came to the front, or checked in while it stays there (a heartbeat). Only the first call pauses; a
     * heartbeat just keeps the pause alive, so a buffer the user arms by hand during playback isn't stopped again.
     */
    fun onPlayerOpen(nowMs: Long, armed: Boolean): Action {
        lastSeenMs = nowMs
        if (paused) return Action.NONE
        paused = true
        return if (armed) Action.DISARM else Action.NONE
    }

    /** The player left the front (closed, or the user switched away). */
    fun onPlayerClose(enabled: Boolean): Action = resume(enabled)

    /** Periodic safety check while paused: re-arms when the player is gone or hasn't checked in for [timeoutMs]. */
    fun onCheck(nowMs: Long, playerAlive: Boolean, enabled: Boolean): Action {
        if (!paused) return Action.NONE
        if (playerAlive && nowMs - lastSeenMs < timeoutMs) return Action.NONE
        return resume(enabled)
    }

    private fun resume(enabled: Boolean): Action {
        if (!paused) return Action.NONE
        paused = false
        return if (enabled) Action.REARM else Action.NONE
    }

    companion object {
        /** How often the player checks in while it's in front. */
        const val HEARTBEAT_MS = 60_000L
        /** How often the service runs [onCheck] while paused. */
        const val CHECK_MS = 30_000L
        /** No check-in for this long means the player is stuck or gone: three missed heartbeats. */
        const val DEFAULT_TIMEOUT_MS = 3 * HEARTBEAT_MS
    }
}
