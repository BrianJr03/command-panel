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
 * The app polls [sample]; each call re-resolves the top standard root task on the display and moves the
 * registration when it changes, so no task-stack listener is needed.
 */
object TaskFps {
    private const val TAG = "TaskFps"

    const val KEY_FPS = "fps"
    const val KEY_TASK_ID = "taskId"
    const val KEY_PACKAGE = "pkg"
    const val KEY_AGE_MS = "ageMs"

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

    /** Latest FPS of the top standard task on [displayId]; `fps` is -1 when there is none or registration failed. */
    @Synchronized
    fun sample(displayId: Int): Bundle {
        val task = resolveGameTask(displayId)
        if (task == null) {
            stop()
            return result(-1f, -1, null, -1L)
        }
        if (task.first != registeredTaskId && (task.first == failedTaskId || !register(task.first))) {
            return result(-1f, task.first, task.second, -1L)
        }
        val fps = lastFps
        val age = if (lastAtMs == 0L) -1L else SystemClock.elapsedRealtime() - lastAtMs
        return result(fps, task.first, task.second, age)
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

    private fun register(taskId: Int): Boolean {
        stop()
        val wm = windowManager ?: return false
        return runCatching {
            wm.javaClass.getMethod("registerTaskFpsCallback", Int::class.javaPrimitiveType, ITaskFpsCallback::class.java)
                .invoke(wm, taskId, callback)
            registeredTaskId = taskId
            Log.i(TAG, "registered for task $taskId")
            true
        }.getOrElse {
            Log.w(TAG, "registerTaskFpsCallback($taskId) failed", it)
            failedTaskId = taskId
            false
        }
    }

    /** First visible standard root task on [displayId] (the list is ordered top-first), as (taskId, package). */
    private fun resolveGameTask(displayId: Int): Pair<Int, String?>? {
        val atm = activityTaskManager ?: return null
        val infos = runCatching {
            atm.javaClass.getMethod("getAllRootTaskInfosOnDisplay", Int::class.javaPrimitiveType)
                .invoke(atm, displayId) as? List<*>
        }.getOrElse {
            Log.w(TAG, "getAllRootTaskInfosOnDisplay($displayId) failed", it)
            null
        } ?: return null
        for (info in infos) {
            info ?: continue
            if (!isVisible(info) || activityType(info) != ClearTasksPolicy.ACTIVITY_TYPE_STANDARD) continue
            val taskId = runCatching { info.javaClass.getField("taskId").getInt(info) }.getOrNull() ?: continue
            val pkg = runCatching {
                (info.javaClass.getField("topActivity").get(info) as? ComponentName)?.packageName
            }.getOrNull()
            return taskId to pkg
        }
        return null
    }

    private fun isVisible(info: Any): Boolean =
        runCatching { info.javaClass.getField("visible").getBoolean(info) }
            .recoverCatching { info.javaClass.getField("isVisible").getBoolean(info) }
            .getOrDefault(false)

    private fun activityType(info: Any): Int =
        runCatching { info.javaClass.getMethod("getActivityType").invoke(info) as Int }
            .recoverCatching { info.javaClass.getField("topActivityType").getInt(info) }
            .getOrDefault(ClearTasksPolicy.ACTIVITY_TYPE_UNDEFINED)

    private fun result(fps: Float, taskId: Int, pkg: String?, ageMs: Long) = Bundle().apply {
        putFloat(KEY_FPS, fps)
        putInt(KEY_TASK_ID, taskId)
        putString(KEY_PACKAGE, pkg)
        putLong(KEY_AGE_MS, ageMs)
    }
}
