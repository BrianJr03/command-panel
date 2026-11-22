package jr.brian.commandpannel.system

import android.content.Context
import android.content.pm.PackageManager
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootShell
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether Better Dual Screen Assistant is around, and whether it currently claims the AYN button. */
enum class BdsaPresence { NONE, INSTALLED_INACTIVE, ACTIVE }

/**
 * Detection result; [packageName] is the BDSA install it found (null when [presence] is NONE). The UI always names it by
 * upstream's app name rather than the installed label, since forks rename it.
 */
data class BdsaStatus(val presence: BdsaPresence, val packageName: String? = null) {
    val active: Boolean get() = presence == BdsaPresence.ACTIVE

    companion object {
        val None = BdsaStatus(BdsaPresence.NONE)
    }
}

/**
 * Detects Better Dual Screen Assistant (BDSA), another replacement for the stock assistant. BDSA listens for the same
 * AYN button broadcast, freezes the same stock process and drives the same screen-mode settings, and nothing outside it
 * can tell it to stand down. So while it claims the hardware, Command Panel stands by instead of fighting over it.
 *
 * BDSA claims the hardware when it's installed, enabled, and its "Replace stock assistant" pref is on. The pref lives
 * in its device-protected storage (`better_dsa_prefs`, key `replace_stock_assistant`, default true) and is read with
 * the root shell. Without root, an enabled BDSA is assumed to claim it (the safe side).
 */
class BdsaDetector(private val context: Context, private val privileged: PrivilegedAccess) {

    @Volatile private var cached: BdsaStatus? = null
    @Volatile private var cachedAt = 0L

    /** One detection at a time, so callers share a result instead of each reading BDSA's prefs. */
    private val lock = Mutex()

    /** Reads BDSA's prefs with the root shell. Results are reused for [CACHE_MS] unless [force]. */
    suspend fun detect(force: Boolean = false): BdsaStatus = lock.withLock {
        val now = System.currentTimeMillis()
        cached?.let { if (!force && now - cachedAt < CACHE_MS) return@withLock it }
        compute().also { cached = it; cachedAt = now }
    }

    private suspend fun compute(): BdsaStatus {
        val pm = context.packageManager
        var inactive: BdsaStatus? = null
        for (pkg in PACKAGES) {
            // MATCH_DISABLED_COMPONENTS so a disabled BDSA is still found (and shown as inactive).
            val info = runCatching { pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS) }.getOrNull() ?: continue
            val enabled = isEnabled(pm, pkg, info.enabled)
            val replacing = enabled && readReplaceStock(pkg) != false
            if (replacing) return BdsaStatus(BdsaPresence.ACTIVE, pkg)
            if (inactive == null) inactive = BdsaStatus(BdsaPresence.INSTALLED_INACTIVE, pkg)
        }
        return inactive ?: BdsaStatus.None
    }

    private fun isEnabled(pm: PackageManager, pkg: String, manifestEnabled: Boolean): Boolean =
        when (runCatching { pm.getApplicationEnabledSetting(pkg) }.getOrNull()) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestEnabled
            null -> manifestEnabled
            else -> false // DISABLED, DISABLED_USER, DISABLED_UNTIL_USED
        }

    /** BDSA's pref, or null when it can't be read (no root, file missing): callers treat null as BDSA's default (on). */
    private suspend fun readReplaceStock(pkg: String): Boolean? {
        val xml = privileged.shell("cat ${RootShell.q("/data/user_de/0/$pkg/shared_prefs/$PREFS_FILE")} 2>/dev/null") ?: return null
        return parseReplaceStock(xml)
    }

    /** Disables [pkg] for user 0 via root. BDSA's helper restores the stock assistant and screens when this happens. */
    suspend fun disable(pkg: String): Boolean {
        require(pkg in PACKAGES)
        privileged.run("pm", "disable-user", "--user", "0", pkg)
        return isDisabledNow(pkg).also { invalidate() }
    }

    /** Re-enables a BDSA install that Command Panel disabled. */
    suspend fun enable(pkg: String): Boolean {
        require(pkg in PACKAGES)
        privileged.run("pm", "enable", "--user", "0", pkg)
        return !isDisabledNow(pkg).also { invalidate() }
    }

    private fun isDisabledNow(pkg: String): Boolean =
        runCatching { context.packageManager.getApplicationEnabledSetting(pkg) }.getOrNull()
            ?.let { it == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER || it == PackageManager.COMPONENT_ENABLED_STATE_DISABLED } ?: false

    /** Drops the cached result; the next [detect] reads again. */
    fun invalidate() {
        cached = null
    }

    companion object {
        /** BDSA release and debug package names (both can be installed side by side). */
        val PACKAGES = listOf("com.stormpanda.betterdualscreenassistant", "com.stormpanda.betterdualscreenassistant.debug")
        private const val PREFS_FILE = "better_dsa_prefs.xml"
        private const val CACHE_MS = 5_000L

        /**
         * BDSA's `replace_stock_assistant` from its prefs XML: true / false, or null when the key (or a parseable file)
         * is missing, which BDSA treats as true.
         */
        fun parseReplaceStock(xml: String): Boolean? {
            val m = Regex("""<boolean\s+name="replace_stock_assistant"\s+value="(true|false)"\s*/>""").find(xml) ?: return null
            return m.groupValues[1] == "true"
        }
    }
}
