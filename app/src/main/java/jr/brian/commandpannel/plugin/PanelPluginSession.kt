package jr.brian.commandpannel.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import jr.brian.commandpannel.system.Prefs

/**
 * Runs Panel plugin sessions (#19) for [jr.brian.commandpannel.service.AssistantService]: binds the plugin service,
 * talks the contract's messages, and carries out what [PanelPluginSwap] decides, including its timeouts. Main thread
 * only.
 *
 * The plugin is recorded in prefs while a session is busy, so a service that died mid-session can tell that plugin to
 * hide when it starts again ([clearStale]) instead of leaving its UI on the bottom screen.
 */
class PanelPluginSession(
    private val context: Context,
    /** The bottom screen's display id, or null when there isn't one. */
    private val bottomDisplayId: () -> Int?,
    /** Shows the panel; the callback runs once it's fully drawn (see [jr.brian.commandpannel.overlay.OverlayHost.show]). */
    private val showPanel: (onVisible: () -> Unit) -> Unit,
    private val hidePanel: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val swap = PanelPluginSwap(now = SystemClock::uptimeMillis)
    private val prefs = Prefs.of(context)

    private var connection: Connection? = null
    private var stale: ServiceConnection? = null
    private val tick = Runnable {
        val phase = swap.phase
        val effects = swap.onTick()
        if (effects.isNotEmpty()) Log.i(TAG, "timed out while $phase")
        run(effects)
    }

    val owner: PanelPluginSwap.Owner get() = swap.owner
    val busy: Boolean get() = swap.busy

    /** See [PanelPluginSwap.keepsPluginOnShow]. */
    fun keepsPluginOnShow(auto: Boolean): Boolean = swap.keepsPluginOnShow(auto)

    /** Opens [plugin] in the panel's place. False when it can't start (a session is busy, no bottom screen). */
    fun start(plugin: PanelPlugin): Boolean {
        if (swap.busy) {
            Log.i(TAG, "start ${plugin.key} ignored: session ${swap.key} is ${swap.phase}")
            return false
        }
        if (bottomDisplayId() == null) {
            Log.w(TAG, "start ${plugin.key} ignored: no bottom display")
            return false
        }
        Log.i(TAG, "start ${plugin.key} v${plugin.version}")
        run(swap.start(plugin.key, plugin.version))
        return true
    }

    /** Ends the session for [reason], if one is busy. True if one was. */
    fun end(reason: PanelPluginSwap.EndReason): Boolean {
        if (!swap.busy) return false
        Log.i(TAG, "end ${swap.key} (${swap.phase}): $reason")
        run(swap.end(reason))
        return true
    }

    /** The service is stopping: end any session without bringing the panel back, and drop a stale-cleanup bind. */
    fun destroy() {
        end(PanelPluginSwap.EndReason.SERVICE_STOPPED)
        main.removeCallbacks(tick)
        stale?.let { runCatching { context.unbindService(it) } }
        stale = null
    }

    /**
     * After a service restart: if a session was busy when the last service died, binds that plugin once, sends it
     * `MSG_HIDE` and unbinds. The panel isn't touched; the service shows it as usual.
     */
    fun clearStale() {
        val key = prefs.getString(PREF_SESSION, null) ?: return
        prefs.edit().remove(PREF_SESSION).apply()
        val component = ComponentName.unflattenFromString(key) ?: return
        Log.i(TAG, "stale session $key from before the restart: sending MSG_HIDE")
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                send(Messenger(service), PanelPluginContract.MSG_HIDE)
                release(this)
            }
            override fun onServiceDisconnected(name: ComponentName) = release(this)
            override fun onBindingDied(name: ComponentName) = release(this)
            override fun onNullBinding(name: ComponentName) = release(this)
        }
        val bound = runCatching {
            context.bindService(Intent().setComponent(component), conn, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) {
            runCatching { context.unbindService(conn) }
            return
        }
        stale = conn
        main.postDelayed({ release(conn) }, PanelPluginContract.SHOWN_TIMEOUT_MS)
    }

    private fun release(conn: ServiceConnection) {
        if (stale !== conn) return
        stale = null
        runCatching { context.unbindService(conn) }
    }

    /**
     * Carries out [effects]. On a handback the panel shows first and the rest (`MSG_HIDE`, unbind) waits until it's
     * fully drawn ([PanelPluginSwap.splitAtShowPanel]), so the plugin never leaves before the panel is there. The rest
     * acts on the connection of the session that ended, even if a new one starts meanwhile, and runs once: from the
     * panel's callback, or from a fallback timer if that never comes.
     */
    private fun run(effects: List<PanelPluginSwap.Effect>) {
        val (now, afterVisible) = PanelPluginSwap.splitAtShowPanel(effects)
        val conn = connection
        for (effect in now) apply(effect, conn)
        if (afterVisible.isNotEmpty()) {
            var done = false
            val rest = Runnable {
                if (done) return@Runnable
                done = true
                for (effect in afterVisible) apply(effect, conn)
            }
            showPanel {
                Log.i(TAG, "panel visible")
                rest.run()
            }
            main.postDelayed(rest, HANDBACK_FALLBACK_MS)
        }
        scheduleTick()
    }

    private fun apply(effect: PanelPluginSwap.Effect, conn: Connection?) {
        when (effect) {
            is PanelPluginSwap.Effect.Bind -> bind(effect.key)
            is PanelPluginSwap.Effect.SendShow -> sendShow(effect.version)
            PanelPluginSwap.Effect.HidePanel -> {
                hidePanel()
                Log.i(TAG, "shown: panel hidden, owner=${swap.owner}")
            }
            // Shown by run() with its visible callback; logged here so scripts see the order.
            PanelPluginSwap.Effect.ShowPanel -> Log.i(TAG, "panel shown")
            PanelPluginSwap.Effect.SendHide -> {
                conn?.messenger?.let { send(it, PanelPluginContract.MSG_HIDE) }
                Log.i(TAG, "MSG_HIDE sent")
            }
            PanelPluginSwap.Effect.Unbind -> unbind(conn)
        }
    }

    private fun bind(key: String) {
        val component = ComponentName.unflattenFromString(key)
        val conn = Connection(swap.sessionId, component?.packageName.orEmpty())
        connection = conn
        prefs.edit().putString(PREF_SESSION, key).apply()
        val bound = component != null && runCatching {
            context.bindService(Intent().setComponent(component), conn, Context.BIND_AUTO_CREATE)
        }.onFailure { Log.w(TAG, "bind $key failed", it) }.getOrDefault(false)
        Log.i(TAG, "bind $key: $bound")
        // A refused bind still needs unbindService to release the connection; the end's Unbind does that.
        if (!bound) main.post { run(swap.onDied(conn.session)) }
    }

    private fun sendShow(version: Int) {
        val conn = connection ?: return
        val display = bottomDisplayId()
        val messenger = conn.messenger
        if (display == null || messenger == null) {
            Log.w(TAG, "MSG_SHOW not sent: display=$display")
            run(swap.onDied(conn.session))
            return
        }
        val msg = Message.obtain(null, PanelPluginContract.MSG_SHOW, display, version).apply { replyTo = conn.replies }
        val ok = runCatching { messenger.send(msg) }.onFailure { Log.w(TAG, "MSG_SHOW failed", it) }.isSuccess
        Log.i(TAG, "MSG_SHOW sent display=$display version=$version ok=$ok")
        if (!ok) run(swap.onDied(conn.session))
    }

    private fun unbind(conn: Connection?) {
        conn ?: return
        // A newer session may own the field (and the pref) by now; only clear what's still this session's.
        if (connection === conn) {
            connection = null
            prefs.edit().remove(PREF_SESSION).apply()
        }
        runCatching { context.unbindService(conn) }
        Log.i(TAG, "unbound ${conn.packageName}, owner=${swap.owner}")
    }

    private fun scheduleTick() {
        main.removeCallbacks(tick)
        val due = swap.nextDeadline ?: return
        main.postAtTime(tick, due)
    }

    private fun send(messenger: Messenger, what: Int) {
        runCatching { messenger.send(Message.obtain(null, what)) }.onFailure { Log.w(TAG, "send $what failed", it) }
    }

    /** One session's binding, and the Messenger the plugin replies to. */
    private inner class Connection(val session: Int, val packageName: String) : ServiceConnection {
        var messenger: Messenger? = null

        val replies = Messenger(Handler(Looper.getMainLooper()) { msg -> onReply(msg); true })

        private fun onReply(msg: Message) {
            if (!fromPlugin(msg.sendingUid)) {
                Log.w(TAG, "ignored message ${msg.what} from uid ${msg.sendingUid}")
                return
            }
            when (msg.what) {
                PanelPluginContract.MSG_SHOWN -> {
                    Log.i(TAG, "MSG_SHOWN from $packageName (session $session)")
                    run(swap.onShown(session))
                }
                PanelPluginContract.MSG_CLOSED -> {
                    Log.i(TAG, "MSG_CLOSED from $packageName (session $session) reason=${msg.arg1}")
                    run(swap.onClosed(session, msg.arg1))
                }
                else -> Log.w(TAG, "unknown message ${msg.what} from $packageName")
            }
        }

        /** The sender is the plugin's own package (contract: the plugin checks us, we check it). */
        private fun fromPlugin(uid: Int): Boolean =
            uid >= 0 && context.packageManager.getPackagesForUid(uid)?.contains(packageName) == true

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (connection !== this) return
            messenger = Messenger(service)
            Log.i(TAG, "connected ${name.flattenToShortString()} (session $session)")
            run(swap.onConnected(session))
        }

        override fun onServiceDisconnected(name: ComponentName) = died("disconnected")
        override fun onBindingDied(name: ComponentName) = died("binding died")
        override fun onNullBinding(name: ComponentName) = died("null binding")

        private fun died(how: String) {
            if (connection !== this) return
            Log.i(TAG, "$packageName $how (session $session)")
            messenger = null
            run(swap.onDied(session))
        }
    }

    private companion object {
        const val TAG = "PanelPlugin"
        /** Set while a session is busy: the plugin's component, for [clearStale]. */
        const val PREF_SESSION = "panel_plugin_session"
        /** The handback's `MSG_HIDE` goes out by now even if the panel never reports itself drawn (fade is 200 ms). */
        const val HANDBACK_FALLBACK_MS = 1_000L
    }
}
