package jr.brian.commandpannel.system

import jr.brian.commandpannel.plugin.PanelPluginTrust

/**
 * What the panel's Keyboard tile does (#41). Pure, for tests; the service carries it out.
 *
 * 1. A ThorKBM that speaks the Panel plugin contract is listed: swap it in, the same path as its nav bar button.
 * 2. ThorKBM is installed without plugin mode (2.2.1 and older): open its app on the top screen. Its main screen is a
 *    settings page made for the top screen, and the app puts its own keyboard on the bottom screen, so the panel
 *    closes to make room for it.
 * 3. Not installed: open the companion on its Keyboard section, which installs it.
 *
 * The release ThorKBM wins over the debug one when both qualify.
 */
object KeyboardTile {

    const val THORKBM_DEBUG_PACKAGE = "jr.brian.thorkbm.debug"

    /** ThorKBM's packages, release first. */
    val THORKBM_PACKAGES = listOf(PanelPluginTrust.THORKBM_PACKAGE, THORKBM_DEBUG_PACKAGE)

    sealed interface Action {
        data class Swap(val pluginKey: String) : Action
        data class OpenApp(val packageName: String) : Action
        data object OpenCompanion : Action
    }

    /**
     * [listedPluginKeys] are the listed Panel plugins' keys (`package/class`); [launchablePackages] the installed
     * packages that have a launcher activity.
     */
    fun decide(listedPluginKeys: List<String>, launchablePackages: Set<String>): Action {
        for (pkg in THORKBM_PACKAGES) {
            listedPluginKeys.firstOrNull { it.substringBefore('/') == pkg }?.let { return Action.Swap(it) }
        }
        THORKBM_PACKAGES.firstOrNull { it in launchablePackages }?.let { return Action.OpenApp(it) }
        return Action.OpenCompanion
    }
}
