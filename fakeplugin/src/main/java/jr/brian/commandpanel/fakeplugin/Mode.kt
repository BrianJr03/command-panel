package jr.brian.commandpanel.fakeplugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * How the fake plugin answers `MSG_SHOW`. Kept in prefs so it survives the process being killed or force-stopped.
 * The version cases aren't modes: they're separate services ([FutureVersionService], [NoVersionService]).
 */
enum class Mode(val key: String, val defaultDelayMs: Long = 0) {
    /** Draws its window, then replies `MSG_SHOWN`. */
    NORMAL("normal"),
    /** Draws its window and never replies `MSG_SHOWN`. */
    NEVER_SHOWN("never_shown"),
    /** Kills its own process on `MSG_SHOW`. */
    CRASH("crash"),
    /** Draws its window, then replies `MSG_SHOWN` after the delay, past Command Panel's 3 s timeout. */
    LATE("late", 4_000),
    /** Replies `MSG_SHOWN`, then ends the session itself with `MSG_CLOSED` after the delay. */
    CLOSE_AFTER("close_after", 3_000),
    /**
     * Replies `MSG_SHOWN`, then after the delay acts as if its UI was opened elsewhere: `MSG_CLOSED` with
     * `CLOSED_OPENED_ELSEWHERE`, and its window stays up (ignoring `MSG_HIDE`) until the next mode change.
     */
    OPENED_ELSEWHERE("opened_elsewhere", 3_000);

    companion object {
        private const val PREFS = "fake_plugin"
        private const val KEY_MODE = "mode"
        private const val KEY_DELAY = "delay_ms"

        fun fromKey(key: String?): Mode? = entries.firstOrNull { it.key == key }

        fun read(context: Context): Mode =
            fromKey(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, null)) ?: NORMAL

        /** The delay set with the mode, or the mode's default. */
        fun delayMs(context: Context, mode: Mode): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DELAY, -1).takeIf { it >= 0 }
                ?: mode.defaultDelayMs

        fun write(context: Context, mode: Mode, delayMs: Long?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MODE, mode.key)
                .apply { if (delayMs != null && delayMs >= 0) putLong(KEY_DELAY, delayMs) else remove(KEY_DELAY) }
                .commit()
        }
    }
}

/**
 * Sets the fake plugin's mode from adb:
 * `am broadcast -n jr.brian.commandpanel.fakeplugin/.ModeReceiver -f 0x20 --es mode late --el delay_ms 4000`.
 * `-f 0x20` (include stopped packages) reaches it after an install or `am force-stop`.
 */
class ModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val wanted = intent.getStringExtra(EXTRA_MODE)
        val mode = Mode.fromKey(wanted)
        if (mode == null) {
            Log.w(FakePluginService.TAG, "unknown mode $wanted; known: ${Mode.entries.joinToString { it.key }}")
            resultCode = 0
            return
        }
        val delay = if (intent.hasExtra(EXTRA_DELAY_MS)) intent.getLongExtra(EXTRA_DELAY_MS, -1) else null
        Mode.write(context, mode, delay)
        // A mode change clears whatever the last session left up (the opened_elsewhere window).
        FakePluginService.openedElsewhere = false
        if (FakeWindow.remove()) Log.i(FakePluginService.TAG, "mode change: leftover window removed")
        Log.i(FakePluginService.TAG, "mode=${mode.key} delay=${Mode.delayMs(context, mode)}")
        resultCode = 1
        resultData = mode.key
    }

    private companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_DELAY_MS = "delay_ms"
    }
}
