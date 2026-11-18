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
 * Screen on/off handling extends the fork's fix (NOTES.md §8 / fork feature 14): a frozen stock process can't drain a
 * queued `SCREEN_OFF` or `SCREEN_ON` broadcast and ANRs. The system then kills it, and since it's persistent it
 * restarts unfrozen and answers the AYN button again. So on every screen off and on the process is unfrozen for up to
 * [UNFREEZE_MS] and then re-frozen, long enough for a busy broadcast queue to reach it. A check [RECHECK_MS] after each
 * freeze ([scheduleRecheck]) and on each AYN press ([ensureSuppressed]) catches a restart for any other reason.
 */
class StockCoexistence(
    context: Context,
    private val privileged: PrivilegedAccess,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "StockCoexistence"
        const val KEY = "replace_stock_assistant"
        const val UNFREEZE_MS = 5000L
        /** How long after a freeze to check that the system didn't kill and restart the stock process. */
        const val RECHECK_MS = 30_000L
    }

    private val prefs = Prefs.of(context)
    private val suppressor = StockSuppressor(privileged::shell)
    /** The other build (debug / release); only one of the two replaces stock at a time. */
    val sibling = SiblingBuild(context, privileged)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var refreezeJob: Job? = null
    private var recheckJob: Job? = null

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
                recheckJob?.cancel()
                // The other app owns the stock process now; the helper must not "recover" it behind its back.
                privileged.setStockRecoveryArmed(false)
            }
        }

    /**
     * Apply the stored preference at startup (and when leaving standby). If the other build claimed the stock
     * replacement more recently, this one stands down instead (so having both on resolves on the first start);
     * otherwise an enabled preference claims it.
     */
    fun applyInitial() {
        if (!_enabled.value) return
        scope.launch(Dispatchers.IO) {
            if (sibling.siblingOwns()) {
                standDownForSibling()
                return@launch
            }
            sibling.claim()
            if (!standby) suppressAndArm()
        }
    }

    fun setEnabled(enable: Boolean) {
        prefs.edit().putBoolean(KEY, enable).apply()
        _enabled.value = enable
        scope.launch(Dispatchers.IO) {
            // Claiming makes the other build stand down; it leaves the stock process for this one to take.
            if (enable) sibling.claim() else sibling.release()
            // On standby the choice is kept and applied once the other replacement stands down.
            if (standby) return@launch
            val ok = if (enable) suppressAndArm() else restoreAndDisarm()
            Log.i(TAG, "setEnabled($enable) -> ${if (enable) "suppressed" else "restored"}=$ok")
        }
    }

    /**
     * The other build took over the stock replacement: turn this one's preference off without restoring stock (the
     * other build owns the stock process now, and restoring it would undo its freeze).
     */
    fun standDownForSibling() {
        if (!_enabled.value) return
        Log.i(TAG, "${sibling.siblingPackage} took over replacing the stock assistant; standing down")
        prefs.edit().putBoolean(KEY, false).apply()
        _enabled.value = false
        refreezeJob?.cancel()
        recheckJob?.cancel()
        privileged.setStockRecoveryArmed(false)
    }

    /** Restore stock when the app is stopping, so the AYN button is never left dead. */
    fun onStop() {
        if (_enabled.value && !standby) {
            // Best-effort synchronous restore on teardown.
            runCatching { restoreAndDisarm() }
        }
        // Nothing is replacing stock from here any more; the pref stays on and claims again on the next start.
        if (_enabled.value) runCatching { sibling.release() }
    }

    /** Arms the helper's stock recovery first, so there is no window where stock is frozen but not covered. */
    private fun suppressAndArm(): Boolean {
        privileged.setStockRecoveryArmed(true)
        val ok = suppressor.suppress()
        scheduleRecheck()
        return ok
    }

    private fun restoreAndDisarm(): Boolean {
        recheckJob?.cancel()
        privileged.setStockRecoveryArmed(false)
        return suppressor.restore()
    }

    fun onScreenOff() = unfreezeBriefly()

    fun onScreenOn() = unfreezeBriefly()

    /** Lets the stock process handle a screen broadcast, then freezes it again (see the class comment). */
    private fun unfreezeBriefly() {
        if (!_enabled.value || standby) return
        refreezeJob?.cancel()
        recheckJob?.cancel()
        refreezeJob = scope.launch(Dispatchers.IO) {
            suppressor.continueProcess()
            delay(UNFREEZE_MS)
            suppressor.stopProcess()
            scheduleRecheck()
        }
    }

    /**
     * A broadcast already on its way to the stock process when it's frozen (say, a `SCREEN_ON` sent just before the
     * service started) still ANRs it, and the system's restart comes back unfrozen. So [RECHECK_MS] after each freeze,
     * suppress it again if it isn't frozen. Skipped while an unfreeze window is open, since that's on purpose.
     */
    private fun scheduleRecheck() {
        recheckJob?.cancel()
        recheckJob = scope.launch(Dispatchers.IO) {
            delay(RECHECK_MS)
            if (!_enabled.value || standby || refreezeJob?.isActive == true || suppressor.isSuppressed()) return@launch
            Log.w(TAG, "stock assistant restarted after being frozen; suppressing it again")
            suppressAndArm()
        }
    }

    /**
     * Safety net, run on each AYN press: if the stock process isn't frozen (it restarted after a crash or a kill
     * Command Panel isn't told about), suppress it again. Suppressing kills the running process, so a stock panel it
     * just opened closes too. An unfreeze window in progress is cut short, since the press means the user is back.
     */
    fun ensureSuppressed() {
        if (!_enabled.value || standby) return
        scope.launch(Dispatchers.IO) {
            if (suppressor.isSuppressed()) return@launch
            refreezeJob?.cancel()
            Log.w(TAG, "stock assistant was running unfrozen; suppressing it again")
            suppressAndArm()
        }
    }

    suspend fun isSuppressed(): Boolean = withContext(Dispatchers.IO) { suppressor.isSuppressed() }
}
