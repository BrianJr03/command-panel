package jr.brian.commandpannel.replay.studio

import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import jr.brian.commandpannel.plugin.PanelPluginSession
import jr.brian.commandpannel.privileged.ClearTasksPolicy
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.DisplayTopology
import jr.brian.commandpannel.system.KeyboardTile
import jr.brian.commandpannel.system.Prefs
import jr.brian.commandpannel.system.ScreenModeController
import jr.brian.commandpannel.system.ScreenModePlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Whether the lower screen can show a clip right now ([LowerScreenBlockers]), and a watch that reports when it stops
 * being able to, so the player can move the video back to the top screen mid-clip (#47).
 *
 * Display presence and power come from [DisplayManager] (a closed lid turns the display off); the blank state and a
 * Panel plugin session from Command Panel's own prefs; ThorKBM open as an app from `dumpsys activity activities`
 * through the root shell (polled, since nothing announces it).
 */
class LowerScreenWatch(private val context: Context, private val privileged: PrivilegedAccess) {

    private val topology = DisplayTopology(context)
    private val prefs = Prefs.of(context)
    private val main = Handler(Looper.getMainLooper())

    private var onLost: (() -> Unit)? = null
    private var pollJob: Job? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** The current blockers. Suspends: the ThorKBM check runs the root shell. */
    suspend fun blockers(): LowerScreenBlockers {
        val display = topology.bottomDisplay
        return fastBlockers(display).copy(keyboardApp = display != null && keyboardAppOn(display.displayId))
    }

    /** Everything but the ThorKBM check, which needs the root shell. Cheap. */
    private fun fastBlockers(display: Display? = topology.bottomDisplay): LowerScreenBlockers = LowerScreenBlockers(
        missing = display == null,
        off = display != null && display.state != Display.STATE_ON,
        blanked = ScreenModeController.blankState(prefs) == ScreenModePlan.BLANK_BOTTOM,
        pluginSession = prefs.getString(PanelPluginSession.PREF_SESSION, null) != null,
    )

    private suspend fun keyboardAppOn(displayId: Int): Boolean {
        val dump = runCatching { privileged.run("dumpsys", "activity", "activities") }.getOrNull() ?: return false
        val tasks = ClearTasksPolicy.parseActivitiesDump(dump) ?: return false
        return tasks.any { task ->
            task.displayId == displayId && task.visible && task.packages.any { it in KeyboardTile.THORKBM_PACKAGES }
        }
    }

    /**
     * Calls [lost] once, on the main thread, when the lower screen stops being available: removed, turned off, blanked,
     * taken by a plugin, or by ThorKBM. [stop] ends the watch.
     */
    fun watch(scope: CoroutineScope, lost: () -> Unit) {
        stop()
        onLost = lost
        val dm = context.getSystemService(DisplayManager::class.java)
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = recheckFast()
            override fun onDisplayChanged(displayId: Int) = recheckFast()
        }.also { dm.registerDisplayListener(it, main) }
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ScreenModeController.KEY_BLANK || key == PanelPluginSession.PREF_SESSION) recheckFast()
        }.also { prefs.registerOnSharedPreferenceChangeListener(it) }
        pollJob = scope.launch {
            while (isActive) {
                delay(KEYBOARD_POLL_MS)
                val b = withContext(Dispatchers.IO) { blockers() }
                if (!b.available) report(b)
            }
        }
    }

    private fun recheckFast() {
        val b = fastBlockers()
        if (!b.available) report(b)
    }

    private fun report(b: LowerScreenBlockers) {
        val lost = onLost ?: return
        Log.i(TAG, "lower screen unavailable: $b")
        stop()
        main.post(lost)
    }

    fun stop() {
        onLost = null
        pollJob?.cancel()
        pollJob = null
        displayListener?.let { runCatching { context.getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) } }
        displayListener = null
        prefsListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        prefsListener = null
    }

    private companion object {
        const val TAG = "ReplayPlayer"
        const val KEYBOARD_POLL_MS = 3_000L
    }
}
