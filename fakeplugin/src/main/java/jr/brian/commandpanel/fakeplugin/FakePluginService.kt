package jr.brian.commandpanel.fakeplugin

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log

/**
 * The fake Panel plugin's bound service. It speaks contract version 1 and behaves as its [Mode] says (set over adb with
 * [ModeReceiver]). Every step is logged under [TAG] for `scripts/verify-plugin.sh`.
 *
 * [FutureVersionService] and [NoVersionService] are this same service declared with other `META_VERSION` meta-data
 * (meta-data is static, so each version needs its own service). Command Panel must never start those two.
 */
open class FakePluginService : Service() {

    private val messenger by lazy { Messenger(Handler(Looper.getMainLooper()) { msg -> handle(msg); true }) }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "bound ${javaClass.simpleName}")
        return messenger.binder
    }

    override fun onDestroy() {
        // Command Panel should always send MSG_HIDE before it unbinds a live plugin; this line means it didn't. Opened
        // elsewhere, the window is the plugin's own UI now and stays until the next mode change.
        if (!openedElsewhere && FakeWindow.remove()) Log.w(TAG, "window removed on destroy (no MSG_HIDE)")
        main.removeCallbacks(closeNow)
        Log.i(TAG, "destroyed ${javaClass.simpleName}")
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        if (!fromCommandPanel(msg.sendingUid)) {
            Log.w(TAG, "ignored message ${msg.what} from uid ${msg.sendingUid}")
            return
        }
        when (msg.what) {
            PanelPluginContract.MSG_SHOW -> show(display = msg.arg1, version = msg.arg2, replyTo = msg.replyTo)
            PanelPluginContract.MSG_HIDE -> {
                main.removeCallbacks(closeNow)
                // Contract: after CLOSED_OPENED_ELSEWHERE the plugin ignores MSG_HIDE for the UI it opened elsewhere.
                if (openedElsewhere) Log.i(TAG, "MSG_HIDE ignored: opened elsewhere, window kept")
                else Log.i(TAG, "MSG_HIDE: window ${if (FakeWindow.remove()) "removed" else "already gone"}")
            }
            else -> Log.w(TAG, "unknown message ${msg.what}")
        }
    }

    private fun show(display: Int, version: Int, replyTo: Messenger?) {
        val mode = Mode.read(this)
        openedElsewhere = false
        val delayMs = Mode.delayMs(this, mode)
        Log.i(TAG, "MSG_SHOW display=$display version=$version mode=${mode.key} delay=$delayMs")
        if (replyTo == null) {
            Log.w(TAG, "MSG_SHOW without replyTo")
            return
        }
        if (version != PanelPluginContract.VERSION) {
            reply(replyTo, PanelPluginContract.MSG_CLOSED, PanelPluginContract.CLOSED_ERROR)
            return
        }
        if (mode == Mode.CRASH) {
            // Abrupt death without a crash dialog, so focus on the top screen isn't disturbed.
            Log.i(TAG, "crash mode: killing the process")
            Process.killProcess(Process.myPid())
            return
        }
        val label = getString(R.string.overlay_label, mode.key, display)
        val shown = FakeWindow.show(this, display, label) {
            Log.i(TAG, "first frame drawn on display $display")
            when (mode) {
                Mode.NORMAL -> reply(replyTo, PanelPluginContract.MSG_SHOWN)
                Mode.NEVER_SHOWN -> Log.i(TAG, "never_shown mode: not replying MSG_SHOWN")
                // Not canceled by MSG_HIDE: the point is to see Command Panel ignore a MSG_SHOWN after its timeout.
                Mode.LATE -> main.postDelayed({
                    reply(replyTo, PanelPluginContract.MSG_SHOWN)
                    Log.i(TAG, "late MSG_SHOWN sent after $delayMs ms")
                }, delayMs)
                Mode.CLOSE_AFTER, Mode.OPENED_ELSEWHERE -> {
                    reply(replyTo, PanelPluginContract.MSG_SHOWN)
                    pendingClose = replyTo
                    closeReason = if (mode == Mode.OPENED_ELSEWHERE) PanelPluginContract.CLOSED_OPENED_ELSEWHERE
                    else PanelPluginContract.CLOSED_BY_USER
                    main.postDelayed(closeNow, delayMs)
                }
                Mode.CRASH -> Unit
            }
        }
        if (!shown) reply(replyTo, PanelPluginContract.MSG_CLOSED, PanelPluginContract.CLOSED_ERROR)
    }

    private var pendingClose: Messenger? = null
    private var closeReason = PanelPluginContract.CLOSED_BY_USER
    private val closeNow = Runnable {
        val to = pendingClose ?: return@Runnable
        pendingClose = null
        if (closeReason == PanelPluginContract.CLOSED_OPENED_ELSEWHERE) openedElsewhere = true
        reply(to, PanelPluginContract.MSG_CLOSED, closeReason)
    }

    private fun reply(to: Messenger, what: Int, arg1: Int = 0) {
        val ok = runCatching { to.send(Message.obtain(null, what, arg1, 0)) }
            .onFailure { Log.w(TAG, "send $what failed", it) }.isSuccess
        Log.i(TAG, "sent ${name(what)} arg1=$arg1 ok=$ok")
    }

    /** Debug tool: checks the package only, not Command Panel's release certificate (the contract allows that). */
    private fun fromCommandPanel(uid: Int): Boolean =
        uid >= 0 && packageManager.getPackagesForUid(uid).orEmpty().any { it in PanelPluginContract.COMMAND_PANEL_PACKAGES }

    private fun name(what: Int) = when (what) {
        PanelPluginContract.MSG_SHOWN -> "MSG_SHOWN"
        PanelPluginContract.MSG_CLOSED -> "MSG_CLOSED"
        else -> what.toString()
    }

    companion object {
        const val TAG = "FakePlugin"
        /** Outlives a service instance, so a late reply still goes out after Command Panel unbinds. */
        private val main = Handler(Looper.getMainLooper())

        /** The session ended with CLOSED_OPENED_ELSEWHERE: the window is the plugin's own UI until the next mode change. */
        var openedElsewhere = false
    }
}

/** Declares `META_VERSION` 2, newer than Command Panel speaks. */
class FutureVersionService : FakePluginService()

/** Declares no `META_VERSION`. */
class NoVersionService : FakePluginService()
