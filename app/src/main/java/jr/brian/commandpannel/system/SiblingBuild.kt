package jr.brian.commandpannel.system

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess

/**
 * Debug and release builds install side by side (`jr.brian.commandpannel` and `jr.brian.commandpannel.debug`), and
 * both would answer the AYN button, freeze the stock process and drive screen modes. Only one may replace the stock
 * assistant at a time: the build that does records its package in the global setting [KEY], and the other one turns
 * its own "Replace stock assistant" off and stands by, the same way it does for Better Dual Screen Assistant.
 *
 * A global setting rather than IPC because the builds are signed with different keys: anyone can read and observe it,
 * so the hand-off is instant in both directions. The most recent claim wins.
 */
class SiblingBuild(private val context: Context, private val privileged: PrivilegedAccess) {

    private val self = context.packageName
    val siblingPackage: String = siblingPackage(self)

    private var observer: ContentObserver? = null

    /** The package that currently replaces the stock assistant, or null when none does. */
    fun owner(): String? = runCatching { Settings.Global.getString(context.contentResolver, KEY) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Blocking (package manager, maybe the root shell); call off the main thread. */
    fun siblingOwns(): Boolean = ownerState(owner(), self, siblingPackage, isSiblingInstalled()) == Owner.SIBLING

    fun isSiblingInstalled(): Boolean =
        runCatching { context.packageManager.getApplicationInfo(siblingPackage, PackageManager.MATCH_DISABLED_COMPONENTS) }.isSuccess

    /** The sibling's launcher label ("TCP" / "TCP Debug"), for the companion. */
    fun siblingLabel(): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(siblingPackage, 0)).toString()
    }.getOrNull()

    /** Records this build as the one replacing the stock assistant; the sibling sees the change and stands down. */
    suspend fun claim() {
        if (owner() == self) return
        write(self)
    }

    /** Clears the record if it still names this build (a newer claim by the sibling is left alone). */
    suspend fun release() {
        if (owner() == self) write(null)
    }

    /** Clears a record naming [pkg] (its owner was uninstalled). */
    suspend fun clearIfOwner(pkg: String) {
        if (owner() == pkg) write(null)
    }

    private suspend fun write(value: String?) {
        val ok = runCatching { Settings.Global.putString(context.contentResolver, KEY, value) }.getOrDefault(false)
        if (!ok) {
            if (value == null) privileged.run("settings", "delete", "global", KEY)
            else privileged.run("settings", "put", "global", KEY, value)
        }
        Log.i(TAG, "owner -> ${value ?: "none"} (api=$ok)")
    }

    /** Calls [onChange] on the main thread whenever the owner changes, from either build. */
    fun observe(onChange: () -> Unit) {
        stop()
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }.also { context.contentResolver.registerContentObserver(Settings.Global.getUriFor(KEY), false, it) }
    }

    fun stop() {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    enum class Owner { NONE, SELF, SIBLING }

    companion object {
        private const val TAG = "SiblingBuild"
        const val KEY = "command_panel_stock_owner"
        private const val DEBUG_SUFFIX = ".debug"

        /** The other build's package: release for a debug build, debug for a release one. */
        fun siblingPackage(self: String): String =
            if (self.endsWith(DEBUG_SUFFIX)) self.removeSuffix(DEBUG_SUFFIX) else self + DEBUG_SUFFIX

        /** Who owns the stock replacement. A record naming an uninstalled sibling (or anything else) counts as nobody. */
        fun ownerState(owner: String?, self: String, sibling: String, siblingInstalled: Boolean): Owner = when (owner) {
            self -> Owner.SELF
            sibling -> if (siblingInstalled) Owner.SIBLING else Owner.NONE
            else -> Owner.NONE
        }
    }
}
