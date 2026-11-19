package jr.brian.commandpannel.system

/**
 * Stock's hardware volume key chaining (`Q0/C0197a`, driven by `VolumeKeyReceiver` on `action.volume.key.event`):
 * the volume keys change the music (top) volume as usual, and once music is at its max, UP also raises the bottom
 * level; once music is at its min, DOWN also lowers it. Presses within [debounceMs] of the last handled one are
 * dropped, as stock does (30 ms, one timer shared by both keys). Pure (the caller passes the clock and the volumes).
 */
class VolumeKeyChain(private val debounceMs: Long = 30L) {
    private var lastHandledAt = Long.MIN_VALUE

    /**
     * Returns the new bottom level for a key press, or null to leave it. [keyType] is the broadcast's `key_type`
     * extra ([KEY_UP] / [KEY_DOWN]); anything else is ignored without touching the debounce, like stock.
     */
    fun onKey(keyType: String?, now: Long, music: Int, musicMin: Int, musicMax: Int, bottom: Int): Int? {
        val up = when (keyType) {
            KEY_UP -> true
            KEY_DOWN -> false
            else -> return null
        }
        if (lastHandledAt != Long.MIN_VALUE && now - lastHandledAt < debounceMs) return null
        lastHandledAt = now
        return when {
            up && music >= musicMax && bottom < BOTTOM_VOLUME_MAX -> bottom + 1
            !up && music <= musicMin && bottom > 0 -> bottom - 1
            else -> null
        }
    }

    companion object {
        const val ACTION = "action.volume.key.event"
        const val EXTRA_KEY_TYPE = "key_type"
        const val KEY_UP = "KEYCODE_VOLUME_UP"
        const val KEY_DOWN = "KEYCODE_VOLUME_DOWN"
    }
}
