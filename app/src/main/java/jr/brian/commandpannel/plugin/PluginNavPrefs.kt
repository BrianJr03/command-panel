package jr.brian.commandpannel.plugin

/**
 * Which Panel plugins have a button in the panel's nav bar (#43). Every listed plugin shows one unless its key
 * (`package/class`) is in the hidden set, saved as a csv under [KEY], so a new plugin shows by default. Hiding a
 * plugin's button doesn't stop the plugin: the Keyboard tile still opens ThorKBM. Pure, for tests.
 */
object PluginNavPrefs {
    const val KEY = "nav_hidden_plugins"

    fun parse(csv: String?): Set<String> =
        csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun encode(hidden: Set<String>): String = hidden.sorted().joinToString(",")

    /** True while none of [keys] is hidden (so an empty list counts as shown). */
    fun shown(keys: Collection<String>, hidden: Set<String>): Boolean = keys.none { it in hidden }

    /** [hidden] with every one of [keys] shown, or hidden. */
    fun setShown(hidden: Set<String>, keys: Collection<String>, shown: Boolean): Set<String> =
        if (shown) hidden - keys.toSet() else hidden + keys

    /** The [plugins] that get a nav bar button, in order. */
    fun <T> visible(plugins: List<T>, hidden: Set<String>, key: (T) -> String): List<T> =
        plugins.filter { key(it) !in hidden }
}
