package jr.brian.commandpannel.system

/**
 * The overlay nav bar's buttons, in their default order. The user's order is saved as a csv of [key]s under
 * `nav_bar_order` and edited by drag-and-drop in the companion's Nav Bar section. [LAUNCH] only shows when a Quick
 * Launch app is chosen.
 */
enum class NavBarButton(val key: String, val label: String) {
    MODE("mode", "Mode"),
    TASK("task", "Task"),
    LAUNCH("launch", "Quick Launch"),
    SETTINGS("settings", "Settings"),
    APP("app", "App");

    companion object {
        fun fromKey(key: String): NavBarButton? = entries.firstOrNull { it.key == key }

        /** Saved order (unknown keys dropped, duplicates removed, missing buttons appended in default order). */
        fun ordered(csv: String?): List<NavBarButton> {
            val saved = csv?.split(",")?.mapNotNull { fromKey(it.trim()) }?.distinct() ?: emptyList()
            return saved + entries.filter { it !in saved }
        }

        fun encode(order: List<NavBarButton>): String = ordered(order.joinToString(",") { it.key }).joinToString(",") { it.key }
    }
}
