package jr.brian.commandpannel.overlay

/**
 * Callbacks waiting for the panel to be fully visible ([OverlayHost.show]'s `onVisible`). Each runs exactly once: right
 * away when the panel is already fully visible, otherwise at [flush] (the fade-in ended, or the panel was hidden,
 * destroyed or never shown, so nobody waits forever). Pure, for tests.
 */
class PendingVisible {
    private val waiting = mutableListOf<() -> Unit>()

    /** Number of callbacks still waiting. */
    val size: Int get() = waiting.size

    /** Runs [callback] now if [visibleNow], otherwise keeps it for [flush]. */
    fun add(visibleNow: Boolean, callback: () -> Unit) {
        if (visibleNow) callback() else waiting += callback
    }

    /** Runs and forgets every waiting callback, in the order added. */
    fun flush() {
        if (waiting.isEmpty()) return
        val due = waiting.toList()
        waiting.clear()
        due.forEach { it() }
    }
}
