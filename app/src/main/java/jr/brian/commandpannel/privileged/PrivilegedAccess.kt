package jr.brian.commandpannel.privileged

import android.content.Context
import android.util.Log
import jr.brian.commandpannel.privileged.helper.ShellRunner
import jr.brian.commandpannel.privileged.helper.TaskFps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point the rest of Command Panel uses for privileged work. Hides the two-tier design: the long-lived
 * root [RootHelper] is the fast path; [VendorRootShell] is the bootstrap and fallback.
 *
 * Phase 1 exposes init, a root shell, and per-display brightness. Later phases add window/cover operations, task
 * management, screen capture and settings-file I/O as methods on the helper.
 */
@Singleton
class PrivilegedAccess @Inject constructor() {

    /** One per-task FPS reading from the helper; [fps] is -1 when no standard task is on the display. */
    data class TaskFpsSample(val fps: Float, val taskId: Int, val packageName: String?, val displayId: Int, val ageMs: Long)

    data class InitResult(
        val hiddenApi: Boolean,
        val rootShell: Boolean,
        val helper: Boolean,
        val grant: PermissionGranter.Result?,
        val helperIdentity: String?,
    )

    /** One-time bootstrap: exemptions, self-grant, helper launch. Safe to call again; it no-ops once connected. */
    suspend fun initialize(context: Context): InitResult = withContext(Dispatchers.IO) {
        val hidden = HiddenApi.exempt()
        val shell = VendorRootShell.isAvailable()
        val grant = if (shell) PermissionGranter.ensureGranted(context) else null
        val helperUp = if (shell) RootHelper.ensureConnected(context) else false
        val identity = if (helperUp) runCatching { RootHelper.binder?.identity() }.getOrNull()?.let {
            "uid=${it.getInt("uid")} pid=${it.getInt("pid")} ctx=${it.getString("context")}"
        } else null
        InitResult(hidden, shell, helperUp, grant, identity).also {
            Log.i("PrivilegedAccess", "init: $it")
        }
    }

    val isHelperConnected: Boolean get() = RootHelper.isConnected

    /** The helper's connection state, to act when it (re)connects. */
    val helperState: kotlinx.coroutines.flow.StateFlow<RootHelper.State> get() = RootHelper.state

    /** Root shell: helper fast path when connected, PServerBinder otherwise. Returns stdout, or null on a timeout. */
    fun shell(command: String, timeoutMs: Int = 5_000): String? {
        RootHelper.binder?.let { h ->
            return runCatching {
                h.exec(command, timeoutMs).takeIf { it.getInt("exit") != ShellRunner.FAILED }?.getString("out")
            }.getOrNull()
        }
        return VendorRootShell.exec(command)
    }

    fun getDisplayBrightness(displayId: Int): Float? =
        RootHelper.binder?.let { runCatching { it.getDisplayBrightness(displayId) }.getOrNull() }

    /**
     * Covers a display with the helper's black SurfaceFlinger layer, above the status bar and every app (helper
     * only). False when the helper is down or the layer couldn't be added.
     */
    fun showBlankLayer(displayId: Int): Boolean =
        RootHelper.binder?.let { runCatching { it.showBlankLayer(displayId) }.getOrDefault(false) } ?: false

    /** Removes that layer. False when the helper is down (its layers went with it) or removing it failed. */
    fun hideBlankLayer(displayId: Int): Boolean =
        RootHelper.binder?.let { runCatching { it.hideBlankLayer(displayId) }.getOrDefault(false) } ?: false

    /** Removes a recent task by id (helper only). Returns true if removed. */
    fun removeTask(taskId: Int): Boolean =
        RootHelper.binder?.let { runCatching { it.removeTask(taskId) }.getOrDefault(false) } ?: false

    /** False when the helper is down or the call failed, so a restore can be kept and retried. */
    fun setDisplayBrightness(displayId: Int, brightness: Float, temporary: Boolean): Boolean =
        RootHelper.binder?.let {
            runCatching { it.setDisplayBrightness(displayId, brightness, temporary) }.getOrDefault(false)
        } ?: false

    /** Mirrors a physical display's layer stack onto [surface] via the root helper (Replay Buffer capture). */
    fun startDisplayMirror(surface: android.view.Surface, layerStack: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Boolean =
        RootHelper.binder?.let {
            runCatching { it.startDisplayMirror(surface, layerStack, srcW, srcH, dstW, dstH) }.getOrDefault(false)
        } ?: false

    /** Adds a mirror of another display without stopping the current one (Replay Buffer's both-screens capture). */
    fun addDisplayMirror(surface: android.view.Surface, layerStack: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Boolean =
        RootHelper.binder?.let {
            runCatching { it.addDisplayMirror(surface, layerStack, srcW, srcH, dstW, dstH) }.getOrDefault(false)
        } ?: false

    fun stopDisplayMirror() {
        RootHelper.binder?.let { runCatching { it.stopDisplayMirror() } }
    }

    /** Has the root helper stream game audio and/or the mic into [out] as PCM (Replay Buffer); false if unavailable. */
    fun startAudioCapture(out: android.os.ParcelFileDescriptor, game: Boolean, mic: Boolean): Boolean =
        RootHelper.binder?.let { runCatching { it.startAudioCapture(out, game, mic) }.getOrDefault(false) } ?: false

    fun stopAudioCapture() {
        RootHelper.binder?.let { runCatching { it.stopAudioCapture() } }
    }

    /**
     * FPS of the game task (helper only), looked for on [preferredDisplayId] after the focused task and before the
     * other displays; null when the helper is down or the call fails.
     */
    fun taskFps(preferredDisplayId: Int): TaskFpsSample? =
        RootHelper.binder?.let { h ->
            runCatching {
                val b = h.taskFps(preferredDisplayId)
                TaskFpsSample(
                    fps = b.getFloat(TaskFps.KEY_FPS, -1f),
                    taskId = b.getInt(TaskFps.KEY_TASK_ID, -1),
                    packageName = b.getString(TaskFps.KEY_PACKAGE),
                    displayId = b.getInt(TaskFps.KEY_DISPLAY_ID, -1),
                    ageMs = b.getLong(TaskFps.KEY_AGE_MS, -1L),
                )
            }.onFailure { Log.w("PrivilegedAccess", "taskFps failed", it) }.getOrNull()
        }

    fun stopTaskFps() {
        RootHelper.binder?.let { runCatching { it.stopTaskFps() } }
    }

    /** SurfaceFlinger's global composite counter straight over the helper binder; null without the helper. */
    fun surfaceFlingerFrameCount(): Long? =
        RootHelper.binder?.let { h -> runCatching { h.surfaceFlingerFrameCount() }.getOrNull()?.takeIf { it >= 0 } }

    /**
     * Arms or disarms the helper's stock recovery: when armed and this app goes away for good (uninstall,
     * force-stop, a crash it doesn't come back from), the helper restores the stock assistant before exiting.
     */
    fun setStockRecoveryArmed(armed: Boolean) {
        RootHelper.stockRecoveryArmed = armed
    }
}
