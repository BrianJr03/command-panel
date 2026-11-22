package jr.brian.commandpannel.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import jr.brian.commandpannel.privileged.ClearTasksPolicy
import jr.brian.commandpannel.system.QuickLaunchReturnTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Reopen when the app closes" for Quick Launch (#9). After the panel hides for a launch, [watch] polls the root
 * `dumpsys activity activities` once a second and lets [QuickLaunchReturnTracker] decide; on a reopen it calls
 * [reopen] on the main thread, the same overlay show the AYN button uses. One watch at a time: a new launch replaces
 * it, and [cancel] (service stop, standby, screen off) ends it.
 */
class QuickLaunchReturnWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    /** The root shell ([jr.brian.commandpannel.privileged.PrivilegedAccess.shell]). */
    private val shell: suspend (String) -> String?,
    /** Screen on and keyguard unlocked. */
    private val isDeviceAwake: () -> Boolean,
    private val isPanelShowing: () -> Boolean,
    /** Last check on the main thread right before reopening (not on standby, lower screen not blanked). */
    private val canReopen: () -> Boolean,
    private val reopen: () -> Unit,
) {
    private var job: Job? = null

    fun watch(packageName: String, targetDisplayId: Int) {
        cancel()
        job = scope.launch(Dispatchers.IO) {
            val tracker = QuickLaunchReturnTracker(packageName, targetDisplayId, ignoredPackages())
            val start = SystemClock.elapsedRealtime()
            Log.i(TAG, "watching $packageName (display $targetDisplayId)")
            while (isActive) {
                delay(POLL_MS)
                val tasks = ClearTasksPolicy.parseActivitiesDump(shell("dumpsys activity activities").orEmpty())
                val verdict = tracker.onTick(
                    tasks = tasks,
                    elapsedMs = SystemClock.elapsedRealtime() - start,
                    deviceAwake = isDeviceAwake(),
                    panelShowing = isPanelShowing(),
                )
                when (verdict) {
                    QuickLaunchReturnTracker.Verdict.WAIT -> Unit
                    QuickLaunchReturnTracker.Verdict.STOP -> {
                        Log.i(TAG, "stopped watching $packageName")
                        return@launch
                    }
                    QuickLaunchReturnTracker.Verdict.REOPEN -> {
                        withContext(Dispatchers.Main) {
                            val ok = canReopen() && !isPanelShowing()
                            Log.i(TAG, "$packageName closed on display ${tracker.watchedDisplayId}; reopen=$ok")
                            if (ok) reopen()
                        }
                        return@launch
                    }
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Launchers (home and secondary home, which can show up as plain standard tasks), SystemUI and this app. */
    private fun ignoredPackages(): Set<String> {
        val pm = context.packageManager
        val launchers = listOf(Intent.CATEGORY_HOME, Intent.CATEGORY_SECONDARY_HOME).flatMap { category ->
            runCatching {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), PackageManager.MATCH_ALL)
                    .map { it.activityInfo.packageName }
            }.getOrDefault(emptyList())
        }
        return ClearTasksPolicy.filterHomePackages(launchers) + setOf(
            context.packageName, "com.android.systemui", "com.android.launcher3", "com.odin.dualscreen.assistant",
        )
    }

    private companion object {
        const val TAG = "QuickLaunchReturn"
        const val POLL_MS = 1_000L
    }
}
