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
    private val privileged: PrivilegedAccess,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "StockCoexistence"
        const val PREF = "command_panel"
        const val KEY = "replace_stock_assistant"
        const val UNFREEZE_MS = 5000L
    }

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val suppressor = StockSuppressor(privileged::shell)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var refreezeJob: Job? = null

    /**
     * True while another stock replacement (Better Dual Screen Assistant) controls the stock process. On standby this
     * never suppresses, restores, freezes or unfreezes it: the other app owns that state, and touching it would undo
     * the other app's work (and the other app would just redo it).
     */
    @Volatile
    var standby: Boolean = false
        set(value) {
            field = value
            if (value) {
                refreezeJob?.cancel()
                // The other app owns the stock process now; the helper must not "recover" it behind its back.
                privileged.setStockRecoveryArmed(false)
            }
        }

    /** Apply the stored preference at startup (and when leaving standby). */
    fun applyInitial() {
        if (_enabled.value && !standby) scope.launch(Dispatchers.IO) { suppressAndArm() }
    }

    fun setEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY, enable).apply()
        _enabled.value = enable
        // On standby the choice is kept and applied once the other replacement stands down.
        if (standby) return
        scope.launch(Dispatchers.IO) {
            val ok = if (enable) suppressAndArm() else restoreAndDisarm()
            Log.i(TAG, "setEnabled($enable) -> ${if (enable) "suppressed" else "restored"}=$ok")
        }
    }

    /** Restore stock when the app is stopping, so the AYN button is never left dead. */
    fun onStop() {
        if (_enabled.value && !standby) {
            // Best-effort synchronous restore on teardown.
            runCatching { restoreAndDisarm() }
        }
    }

    /** Arms the helper's stock recovery first, so there is no window where stock is frozen but not covered. */
    private fun suppressAndArm(): Boolean {
        privileged.setStockRecoveryArmed(true)
        return suppressor.suppress()
    }

    private fun restoreAndDisarm(): Boolean {
        privileged.setStockRecoveryArmed(false)
        return suppressor.restore()
    }

    fun onScreenOff() {
        if (!_enabled.value || standby) return
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
