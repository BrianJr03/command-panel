package jr.brian.commandpannel.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import jr.brian.commandpannel.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** An installed, listed Panel plugin: its service, the label for its tile, and the contract version its session uses. */
data class PanelPlugin(val component: ComponentName, val label: String, val version: Int) {
    /** [component] as a string; the key the session and the tile use. */
    val key: String get() = component.flattenToString()
}

/**
 * The Panel plugins installed right now (#19): services answering [PanelPluginContract.ACTION_PANEL_PLUGIN] with a
 * version this build speaks ([PanelPluginTrust.sessionVersion]), signed only with allowlisted keys
 * ([PanelPluginTrust.isTrustedPackage]); debug builds also list debuggable plugin apps ([PanelPluginTrust.isListed]).
 * One tile per service. [refresh] after any package change.
 */
class PanelPluginRegistry(private val context: Context, private val scope: CoroutineScope) {

    private val _plugins = MutableStateFlow<List<PanelPlugin>>(emptyList())
    val plugins: StateFlow<List<PanelPlugin>> = _plugins.asStateFlow()

    /** Re-reads the installed plugins in the background. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val found = runCatching { query(allowUntrusted = false) }
                .onFailure { Log.w(TAG, "plugin query failed", it) }
                .getOrDefault(emptyList())
            if (found != _plugins.value) {
                Log.i(TAG, "plugins: ${found.joinToString { "${it.key} v${it.version}" }.ifEmpty { "none" }}")
            }
            _plugins.value = found
        }
    }

    /** The listed plugin with [key], or null. */
    fun find(key: String): PanelPlugin? = _plugins.value.firstOrNull { it.key == key }

    /**
     * Blocking. Every plugin service on the device that passes the version check, and the certificate check unless
     * [allowUntrusted] (debug builds' start broadcast only, so a debug-signed test plugin can be driven).
     */
    fun query(allowUntrusted: Boolean): List<PanelPlugin> {
        val pm = context.packageManager
        val services = pm.queryIntentServices(
            Intent(PanelPluginContract.ACTION_PANEL_PLUGIN),
            PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )
        val candidates = services.mapNotNull { info ->
            val service = info.serviceInfo ?: return@mapNotNull null
            if (!service.exported || service.packageName == context.packageName) return@mapNotNull null
            @Suppress("DEPRECATION") // Bundle.get: the version may be an Int or a String.
            Candidate(
                packageName = service.packageName,
                className = service.name,
                rawVersion = service.metaData?.get(PanelPluginContract.META_VERSION),
                label = service.loadLabel(pm).toString(),
            )
        }
        val debuggable = services.mapNotNull { it.serviceInfo?.applicationInfo }
            .associate { it.packageName to (it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) }
        val listed = HashMap<String, Boolean>()
        return select(candidates) { pkg ->
            allowUntrusted || listed.getOrPut(pkg) {
                PanelPluginTrust.isListed(
                    trusted = PanelPluginTrust.isTrustedPackage(pm, pkg),
                    debuggable = debuggable[pkg] == true,
                    debugBuild = BuildConfig.DEBUG,
                )
            }
        }.map { PanelPlugin(ComponentName(it.candidate.packageName, it.candidate.className), it.candidate.label, it.version) }
    }

    /** A plugin service as found, before the checks. */
    data class Candidate(val packageName: String, val className: String, val rawVersion: Any?, val label: String)

    data class Selected(val candidate: Candidate, val version: Int)

    companion object {
        private const val TAG = "PanelPlugin"

        /**
         * The candidates Command Panel lists, sorted by label then class: a usable version and a trusted package
         * ([isTrusted], asked only for candidates whose version passed). Pure, for tests.
         */
        fun select(candidates: List<Candidate>, isTrusted: (packageName: String) -> Boolean): List<Selected> =
            candidates
                .mapNotNull { c ->
                    PanelPluginTrust.sessionVersion(PanelPluginTrust.parseVersion(c.rawVersion))?.let { Selected(c, it) }
                }
                .filter { isTrusted(it.candidate.packageName) }
                .distinctBy { it.candidate.packageName to it.candidate.className }
                .sortedWith(compareBy({ it.candidate.label.lowercase() }, { it.candidate.className }))
    }
}
