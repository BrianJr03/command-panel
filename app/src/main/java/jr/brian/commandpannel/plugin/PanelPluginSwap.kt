package jr.brian.commandpannel.plugin

/**
 * Who has the bottom screen, and the steps of one Panel plugin session (#19). Pure, with an injected clock, so it is
 * unit-tested without Android; [PanelPluginSession] binds the service, sends the messages and runs the [Effect]s.
 *
 * A session goes [Phase.BINDING] (the tile was picked, the service is being bound) -> [Phase.SHOWING] (`MSG_SHOW`
 * sent, the panel still up) -> [Phase.ACTIVE] (`MSG_SHOWN` came back and the panel was hidden: the plugin [owner]s the
 * bottom screen). Any [end] goes back to [Phase.IDLE] and, for most [EndReason]s, shows the panel **before** telling
 * the plugin to hide, so the bottom screen is never empty. While the panel is up nothing is lost by ending, so a
 * pending session ends the same way.
 *
 * Every session has an id; callbacks from an older session (a late `MSG_SHOWN`, a dead binder) carry its id and are
 * ignored.
 */
class PanelPluginSwap(
    private val now: () -> Long,
    private val bindTimeoutMs: Long = BIND_TIMEOUT_MS,
    private val shownTimeoutMs: Long = PanelPluginContract.SHOWN_TIMEOUT_MS,
) {
    enum class Phase { IDLE, BINDING, SHOWING, ACTIVE }

    /** The bottom screen's owner: the panel, or the plugin with key [Plugin.key] (only once it's [Phase.ACTIVE]). */
    sealed interface Owner {
        data object Panel : Owner
        data class Plugin(val key: String) : Owner
    }

    /**
     * Why a session ended. [restorePanel] says whether the panel comes back first; it doesn't when Command Panel is
     * about to blank the bottom screen itself, go on standby or stop, since the panel would only flash.
     */
    enum class EndReason(val restorePanel: Boolean) {
        /** The plugin sent `MSG_CLOSED` (its Hide key, an error, an unknown reason). */
        PLUGIN_CLOSED(true),
        /**
         * The plugin sent `MSG_CLOSED` with `CLOSED_OPENED_ELSEWHERE`: its own UI is up on its own now, so the panel
         * stays hidden (the two would stack). The next AYN press shows it as usual.
         */
        PLUGIN_OPENED_ELSEWHERE(false),
        /** AYN button single press. */
        AYN_PRESS(true),
        /** AYN button long press: Command Panel blanks the bottom screen right after. */
        AYN_LONG_PRESS(false),
        /** A toggle or explicit show of the panel (broadcast, companion's preview button). */
        PANEL_REQUESTED(true),
        /** The screen turned off (the lid closed): the panel is what's there when it opens again. */
        SCREEN_OFF(true),
        /** A screen mode blanked a panel. */
        BOTTOM_BLANKED(false),
        /** Command Panel went on standby (another app answers the AYN button). */
        STANDBY(false),
        /** No `MSG_SHOWN` within [PanelPluginContract.SHOWN_TIMEOUT_MS] of `MSG_SHOW`. */
        SHOWN_TIMEOUT(true),
        /** The service never connected. */
        BIND_TIMEOUT(true),
        /** `bindService` refused, `onNullBinding`, or the binder died. The plugin gets no `MSG_HIDE`. */
        PLUGIN_DIED(true),
        /** The service is stopping. */
        SERVICE_STOPPED(false),
        /** The debug-only end broadcast. */
        DEBUG(true),
    }

    sealed interface Effect {
        /** Bind the plugin service [key]. */
        data class Bind(val key: String) : Effect
        /** Send `MSG_SHOW` with this session's [version] (the display id is the session's to fill in). */
        data class SendShow(val version: Int) : Effect
        data object HidePanel : Effect
        data object ShowPanel : Effect
        data object SendHide : Effect
        data object Unbind : Effect
    }

    var phase: Phase = Phase.IDLE
        private set

    /** Key of the session's plugin, or null when [Phase.IDLE]. */
    var key: String? = null
        private set

    /** Id of the current (or last) session; callbacks pass it back. */
    var sessionId: Int = 0
        private set

    private var version = 0
    private var deadline: Long? = null

    val owner: Owner get() = key?.takeIf { phase == Phase.ACTIVE }?.let { Owner.Plugin(it) } ?: Owner.Panel

    /** A session is binding, waiting for `MSG_SHOWN`, or active. */
    val busy: Boolean get() = phase != Phase.IDLE

    /** When [onTick] next has something to do (a timeout), on the [now] clock; null if nothing is pending. */
    val nextDeadline: Long? get() = deadline

    /**
     * Starts a session with plugin [key], speaking contract [version]. Ignored (no effects) while another session is
     * busy: the tile can only be picked while the panel is up, so a second tap during the handoff is a double tap.
     */
    fun start(key: String, version: Int): List<Effect> {
        if (busy) return emptyList()
        sessionId++
        this.key = key
        this.version = version
        phase = Phase.BINDING
        deadline = now() + bindTimeoutMs
        return listOf(Effect.Bind(key))
    }

    /** The service connected: send `MSG_SHOW` and wait for `MSG_SHOWN`. */
    fun onConnected(session: Int): List<Effect> {
        if (session != sessionId || phase != Phase.BINDING) return emptyList()
        phase = Phase.SHOWING
        deadline = now() + shownTimeoutMs
        return listOf(Effect.SendShow(version))
    }

    /**
     * `MSG_SHOWN`: the plugin's UI is drawn on the bottom screen, so the panel can go without leaving it empty. An
     * activity plugin sits under the panel and the panel's fade-out reveals it; an overlay plugin may already be on top.
     */
    fun onShown(session: Int): List<Effect> {
        if (session != sessionId || phase != Phase.SHOWING) return emptyList()
        phase = Phase.ACTIVE
        deadline = null
        return listOf(Effect.HidePanel)
    }

    /** `MSG_CLOSED` with its `arg1` [reason]; unknown reasons count as [PanelPluginContract.CLOSED_BY_USER]. */
    fun onClosed(session: Int, reason: Int = PanelPluginContract.CLOSED_BY_USER): List<Effect> = when {
        session != sessionId -> emptyList()
        reason == PanelPluginContract.CLOSED_OPENED_ELSEWHERE -> end(EndReason.PLUGIN_OPENED_ELSEWHERE)
        else -> end(EndReason.PLUGIN_CLOSED)
    }

    /** The binder died, the bind was refused or came back null. */
    fun onDied(session: Int): List<Effect> =
        if (session != sessionId) emptyList() else end(EndReason.PLUGIN_DIED)

    /** Checks the bind and shown timeouts against [now]. */
    fun onTick(): List<Effect> {
        val due = deadline ?: return emptyList()
        if (now() < due) return emptyList()
        return when (phase) {
            Phase.BINDING -> end(EndReason.BIND_TIMEOUT)
            Phase.SHOWING -> end(EndReason.SHOWN_TIMEOUT)
            else -> emptyList()
        }
    }

    /**
     * Ends the session, if any. The panel comes back first ([EndReason.restorePanel]), then the plugin gets `MSG_HIDE`
     * if it was ever sent `MSG_SHOW` and is still alive, then the service is unbound. Idle: no effects.
     */
    fun end(reason: EndReason): List<Effect> {
        if (!busy) return emptyList()
        val wasShown = phase == Phase.SHOWING || phase == Phase.ACTIVE
        phase = Phase.IDLE
        key = null
        deadline = null
        return buildList {
            if (reason.restorePanel) add(Effect.ShowPanel)
            if (wasShown && reason != EndReason.PLUGIN_DIED) add(Effect.SendHide)
            add(Effect.Unbind)
        }
    }

    /**
     * Whether a request to show the panel leaves an active plugin alone. Only an automatic show (the companion's
     * preview on launch) does: opening the companion on the top screen shouldn't take the bottom screen back. Anything
     * else that shows the panel ends the session first.
     */
    fun keepsPluginOnShow(auto: Boolean): Boolean = auto && phase == Phase.ACTIVE

    companion object {
        /** How long a plugin service gets to connect (a cold process start) before the session is dropped. */
        const val BIND_TIMEOUT_MS = 5_000L

        /**
         * Splits [effects] for the handback: what runs now (up to and including [Effect.ShowPanel]) and what waits
         * until the panel is fully drawn (the rest: `MSG_HIDE`, unbind). Telling the plugin to hide while the panel is
         * still fading in from transparent shows whatever is underneath for a few frames. Without a [Effect.ShowPanel],
         * everything runs now.
         */
        fun splitAtShowPanel(effects: List<Effect>): Pair<List<Effect>, List<Effect>> {
            val i = effects.indexOf(Effect.ShowPanel)
            return if (i < 0) effects to emptyList() else effects.subList(0, i + 1) to effects.subList(i + 1, effects.size)
        }
    }
}
