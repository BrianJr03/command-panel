package jr.brian.commandpannel.system

import android.content.Context
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.StockSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the opt-in "Replace stock assistant" state and applies it. When enabled, the stock assistant is ephemerally
 * suppressed so Command Panel owns the AYN button; when disabled (or the app stops), it is restored.
 *
 * Screen-off handling is the fork's fix (NOTES.md §8 / fork feature 14): a frozen stock process can't drain a queued
 * `SCREEN_OFF` broadcast and ANRs, so on screen-off the process is unfrozen for up to [UNFREEZE_MS] and then
 * re-frozen — long enough for a busy broadcast queue to reach it.
 */
class StockCoexistence(
    context: Context,
    privileged: PrivilegedAccess,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "StockCoexistence"
        const val PREF = "command_panel"
        const val KEY = "replace_stock_assistant"
        const val UNFREEZE_MS = 5000L
    }

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val suppressor = StockSuppressor(privileged)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var refreezeJob: Job? = null

    /** Apply the stored preference at startup. */
    fun applyInitial() {
        if (_enabled.value) scope.launch(Dispatchers.IO) { suppressor.suppress() }
    }

    fun setEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY, enable).apply()
        _enabled.value = enable
        scope.launch(Dispatchers.IO) {
            val ok = if (enable) suppressor.suppress() else suppressor.restore()
            Log.i(TAG, "setEnabled($enable) -> ${if (enable) "suppressed" else "restored"}=$ok")
        }
    }

    /** Restore stock when the app is stopping, so the AYN button is never left dead. */
    fun onStop() {
        if (_enabled.value) {
            // Best-effort synchronous restore on teardown.
            runCatching { suppressor.restore() }
        }
    }

    fun onScreenOff() {
        if (!_enabled.value) return
        refreezeJob?.cancel()
        refreezeJob = scope.launch(Dispatchers.IO) {
            suppressor.continueProcess()
            delay(UNFREEZE_MS)
            suppressor.stopProcess()
        }
    }

    fun onScreenOn() {
        // Nothing special; any pending re-freeze still completes.
    }

    suspend fun isSuppressed(): Boolean = withContext(Dispatchers.IO) { suppressor.isSuppressed() }
}
