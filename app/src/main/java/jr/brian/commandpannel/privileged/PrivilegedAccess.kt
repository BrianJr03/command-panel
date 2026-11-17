package jr.brian.commandpannel.privileged

import android.content.Context
import android.util.Log
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

    /** Root shell: helper fast path when connected, PServerBinder otherwise. Returns stdout or null. */
    fun shell(command: String, timeoutMs: Int = 5_000): String? {
        RootHelper.binder?.let { h ->
            return runCatching { h.exec(command, timeoutMs).getString("out") }.getOrNull()
        }
        return VendorRootShell.exec(command)
    }

    fun getDisplayBrightness(displayId: Int): Float? =
        RootHelper.binder?.let { runCatching { it.getDisplayBrightness(displayId) }.getOrNull() }

    /** Removes a recent task by id (helper only). Returns true if removed. */
    fun removeTask(taskId: Int): Boolean =
        RootHelper.binder?.let { runCatching { it.removeTask(taskId) }.getOrDefault(false) } ?: false

    fun setDisplayBrightness(displayId: Int, brightness: Float, temporary: Boolean) {
        RootHelper.binder?.let { runCatching { it.setDisplayBrightness(displayId, brightness, temporary) } }
    }

    /** Mirrors a physical display's layer stack onto [surface] via the root helper (Replay Buffer capture). */
    fun startDisplayMirror(surface: android.view.Surface, layerStack: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Boolean =
        RootHelper.binder?.let {
            runCatching { it.startDisplayMirror(surface, layerStack, srcW, srcH, dstW, dstH) }.getOrDefault(false)
        } ?: false

    fun stopDisplayMirror() {
        RootHelper.binder?.let { runCatching { it.stopDisplayMirror() } }
    }
}
