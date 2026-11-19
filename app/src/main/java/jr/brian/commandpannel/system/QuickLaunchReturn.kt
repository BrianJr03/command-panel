package jr.brian.commandpannel.system

import jr.brian.commandpannel.privileged.ClearTasksPolicy
import jr.brian.commandpannel.privileged.ClearTasksPolicy.TaskSnapshot

/**
 * Pure decision logic behind "Reopen when the app closes" (#9). The service polls `dumpsys activity activities`
 * (parsed by [ClearTasksPolicy.parseActivitiesDump]) and feeds each snapshot to [onTick], which says whether to keep
 * waiting, reopen the panel, or give up. No Android dependencies, so it is unit-tested directly.
 *
 * The rules:
 *  1. Until the launched [packageName] shows up visible on some display, wait; give up after [launchGraceMs]
 *     (the launch failed, or the app went straight to the background). The display it first shows up on
 *     ([targetDisplayId] preferred) is the one watched from then on.
 *  2. While the package is visible on the watched display, wait.
 *  3. If another app is visible there instead (a frontend launched a game, or the user switched apps), wait: the
 *     user is still busy on that screen, and the panel comes back once that screen returns to home. Recents counts
 *     as busy too.
 *  4. Otherwise (home, or nothing but [ignoredPackages]: launchers, which a secondary display can list as plain
 *     standard tasks, SystemUI, this app) the app has closed:
 *     reopen after [goneTicksToReopen] ticks in a row, so a single odd snapshot mid-transition doesn't pop it up.
 *
 * Gives up ([Verdict.STOP]) when the device is asleep or locked, when the panel is already showing (the user opened
 * it), after [timeoutMs], or after [maxDumpFailures] unreadable dumps in a row.
 */
class QuickLaunchReturnTracker(
    private val packageName: String,
    private val targetDisplayId: Int,
    private val ignoredPackages: Set<String>,
    private val launchGraceMs: Long = LAUNCH_GRACE_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val goneTicksToReopen: Int = 2,
    private val maxDumpFailures: Int = 3,
) {
    enum class Verdict { WAIT, REOPEN, STOP }

    /** The display the app was first seen on, or null before that. */
    var watchedDisplayId: Int? = null
        private set

    private var goneTicks = 0
    private var dumpFailures = 0

    /**
     * One poll. [tasks] is the parsed dump (null if it couldn't be read), [elapsedMs] the time since the launch,
     * [deviceAwake] whether the screen is on and unlocked, [panelShowing] whether the overlay is up.
     */
    fun onTick(tasks: List<TaskSnapshot>?, elapsedMs: Long, deviceAwake: Boolean, panelShowing: Boolean): Verdict {
        if (!deviceAwake || panelShowing || elapsedMs >= timeoutMs) return Verdict.STOP
        if (tasks == null) return if (++dumpFailures >= maxDumpFailures) Verdict.STOP else Verdict.WAIT
        dumpFailures = 0

        val shown = tasks.filter { it.visible && packageName in it.packages }
        val display = watchedDisplayId ?: run {
            if (shown.isEmpty()) return if (elapsedMs >= launchGraceMs) Verdict.STOP else Verdict.WAIT
            (shown.firstOrNull { it.displayId == targetDisplayId } ?: shown.first()).displayId.also { watchedDisplayId = it }
        }
        if (shown.any { it.displayId == display } || tasks.any { it.displayId == display && isBusy(it) }) {
            goneTicks = 0
            return Verdict.WAIT
        }
        return if (++goneTicks >= goneTicksToReopen) Verdict.REOPEN else Verdict.WAIT
    }

    /** A visible task that means the user is doing something else on the screen: another app, or recents. */
    private fun isBusy(task: TaskSnapshot): Boolean {
        if (!task.visible) return false
        if (task.activityType == ClearTasksPolicy.ACTIVITY_TYPE_RECENTS) return true
        if (!ClearTasksPolicy.isClearableActivityType(task.activityType)) return false
        val pkgs = task.packages
        return pkgs.isNotEmpty() && pkgs.none { it in ignoredPackages }
    }

    companion object {
        const val LAUNCH_GRACE_MS = 15_000L
        const val TIMEOUT_MS = 30 * 60_000L
    }
}
