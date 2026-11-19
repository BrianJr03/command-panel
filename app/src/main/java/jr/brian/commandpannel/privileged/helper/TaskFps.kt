package jr.brian.commandpannel.privileged.helper

import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.window.ITaskFpsCallback
import jr.brian.commandpannel.privileged.ClearTasksPolicy

/**
 * Per-task FPS via the hidden `IWindowManager.registerTaskFpsCallback` (API 33), runnable only from the root helper:
 * it needs `ACCESS_FPS_COUNTER` (signature|privileged), which uid 0 always passes. SurfaceFlinger's `FpsReporter`
 * counts the frames presented by the layers inside the task's subtree and reports roughly every 500 ms, so the
 * overlay and other apps never inflate the number the way the global composite counter (`SurfaceFlinger 1013`) does.
 *
 * The app polls [sample]; each call re-resolves the game task and moves the registration when it changes, so no
 * task-stack listener is needed. The game may be on either screen (the Thor's top is display 0, the bottom is 4), so
 * the task is found on whichever display it is on: the focused root task first (the one taking gamepad input), then
 * the top standard task on the preferred display, then on every other display.
 */
object TaskFps {
    private const val TAG = "TaskFps"

    const val KEY_FPS = "fps"
    const val KEY_TASK_ID = "taskId"
    const val KEY_PACKAGE = "pkg"
    const val KEY_AGE_MS = "ageMs"
    const val KEY_DISPLAY_ID = "displayId"

    private val windowManager: Any? by lazy {
        runCatching {
            Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null)
        }.getOrNull()
    }

    private val activityTaskManager: Any? by lazy {
        runCatching {
            Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        }.getOrNull()
    }

    @Volatile private var lastFps = -1f
    @Volatile private var lastAtMs = 0L
    private var registeredTaskId = -1
    // Not retried every poll: a task WMS rejects once (gone, or the call is blocked) stays rejected.
    private var failedTaskId = -1

    private val callback = object : ITaskFpsCallback.Stub() {
        override fun onFpsReported(fps: Float) {
            lastFps = fps
            lastAtMs = SystemClock.elapsedRealtime()
        }
    }

    /** A resolved game task: its id, top package and the display it is on. */
    private data class GameTask(val taskId: Int, val pkg: String?, val displayId: Int)

    /**
     * Latest FPS of the game task (see the class doc for how it is found, starting from [preferredDisplayId]);
     * `fps` is -1 when there is none or registration failed.
     */
    @Synchronized
    fun sample(preferredDisplayId: Int): Bundle {
        val task = resolveGameTask(preferredDisplayId)
        if (task == null) {
            stop()
            return result(-1f, null, -1L)
        }
        if (task.taskId != registeredTaskId && (task.taskId == failedTaskId || !register(task))) {
            return result(-1f, task, -1L)
        }
        val fps = lastFps
        val age = if (lastAtMs == 0L) -1L else SystemClock.elapsedRealtime() - lastAtMs
        return result(fps, task, age)
    }

    @Synchronized
    fun stop() {
        if (registeredTaskId < 0) return
        val wm = windowManager
        if (wm != null) {
            runCatching {
                wm.javaClass.getMethod("unregisterTaskFpsCallback", ITaskFpsCallback::class.java).invoke(wm, callback)
            }.onFailure { Log.w(TAG, "unregisterTaskFpsCallback failed", it) }
        }
        registeredTaskId = -1
        lastFps = -1f
        lastAtMs = 0L
    }

    private fun register(task: GameTask): Boolean {
        stop()
        val wm = windowManager ?: return false
        return runCatching {
            wm.javaClass.getMethod("registerTaskFpsCallback", Int::class.javaPrimitiveType, ITaskFpsCallback::class.java)
                .invoke(wm, task.taskId, callback)
            registeredTaskId = task.taskId
            Log.i(TAG, "registered for task ${task.taskId} (${task.pkg}) on display ${task.displayId}")
            true
        }.getOrElse {
            Log.w(TAG, "registerTaskFpsCallback(${task.taskId}) failed", it)
            failedTaskId = task.taskId
            false
        }
    }

    private fun resolveGameTask(preferredDisplayId: Int): GameTask? {
        focusedGameTask()?.let { return it }
        topGameTaskOn(preferredDisplayId)?.let { return it }
        for (id in displayIds()) {
            if (id == preferredDisplayId) continue
            topGameTaskOn(id)?.let { return it }
        }
        return null
    }

    /** The focused root task when it is a visible standard (app or game) task; null for home, recents, etc. */
    private fun focusedGameTask(): GameTask? {
        val atm = activityTaskManager ?: return null
        val info = runCatching { atm.javaClass.getMethod("getFocusedRootTaskInfo").invoke(atm) }
            .onFailure { Log.w(TAG, "getFocusedRootTaskInfo failed", it) }
            .getOrNull() ?: return null
        return gameTaskOf(info, displayId = null)
    }

    /** First visible standard root task on [displayId] (the list is ordered top-first). */
    private fun topGameTaskOn(displayId: Int): GameTask? {
        val atm = activityTaskManager ?: return null
        val infos = runCatching {
            atm.javaClass.getMethod("getAllRootTaskInfosOnDisplay", Int::class.javaPrimitiveType)
                .invoke(atm, displayId) as? List<*>
        }.getOrElse {
            Log.w(TAG, "getAllRootTaskInfosOnDisplay($displayId) failed", it)
            null
        } ?: return null
        for (info in infos) {
            gameTaskOf(info ?: continue, displayId)?.let { return it }
        }
        return null
    }

    private fun gameTaskOf(info: Any, displayId: Int?): GameTask? {
        if (!isVisible(info) || activityType(info) != ClearTasksPolicy.ACTIVITY_TYPE_STANDARD) return null
        val taskId = runCatching { info.javaClass.getField("taskId").getInt(info) }.getOrNull() ?: return null
        val pkg = runCatching {
            (info.javaClass.getField("topActivity").get(info) as? ComponentName)?.packageName
        }.getOrNull()
        val display = displayId
            ?: runCatching { info.javaClass.getField("displayId").getInt(info) }.getOrDefault(-1)
        return GameTask(taskId, pkg, display)
    }

    /** Every logical display id, via the hidden `DisplayManagerGlobal`; just the default display if that fails. */
    private fun displayIds(): IntArray = runCatching {
        val cls = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val g = cls.getMethod("getInstance").invoke(null)
        cls.getMethod("getDisplayIds").invoke(g) as IntArray
    }.getOrElse {
        Log.w(TAG, "getDisplayIds failed", it)
        intArrayOf(0)
    }

    private fun isVisible(info: Any): Boolean =
        runCatching { info.javaClass.getField("visible").getBoolean(info) }
            .recoverCatching { info.javaClass.getField("isVisible").getBoolean(info) }
            .getOrDefault(false)

    private fun activityType(info: Any): Int =
        runCatching { info.javaClass.getMethod("getActivityType").invoke(info) as Int }
            .recoverCatching { info.javaClass.getField("topActivityType").getInt(info) }
            .getOrDefault(ClearTasksPolicy.ACTIVITY_TYPE_UNDEFINED)

    private fun result(fps: Float, task: GameTask?, ageMs: Long) = Bundle().apply {
        putFloat(KEY_FPS, fps)
        putInt(KEY_TASK_ID, task?.taskId ?: -1)
        putString(KEY_PACKAGE, task?.pkg)
        putInt(KEY_DISPLAY_ID, task?.displayId ?: -1)
        putLong(KEY_AGE_MS, ageMs)
    }
}
