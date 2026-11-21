package jr.brian.commandpannel.ui.settings

import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.settings.SettingsCategory.APPEARANCE
import jr.brian.commandpannel.ui.settings.SettingsCategory.DIAGNOSTICS
import jr.brian.commandpannel.ui.settings.SettingsCategory.EXPERIMENTAL
import jr.brian.commandpannel.ui.settings.SettingsCategory.KEYBOARD
import jr.brian.commandpannel.ui.settings.SettingsCategory.NAV_BAR
import jr.brian.commandpannel.ui.settings.SettingsCategory.QUICK_TOGGLES
import jr.brian.commandpannel.ui.settings.SettingsCategory.REPLAY
import jr.brian.commandpannel.ui.settings.SettingsCategory.THEMES

/**
 * The companion rail's collapsible groups, in their default order, each with its [sections] in their default order.
 * [RailGroup.PINNED] (General) sits above every group and is never moved. [key] is what `rail_group_order` stores.
 */
enum class RailGroup(val key: String, @StringRes val titleRes: Int, val sections: List<SettingsCategory>) {
    PANEL("panel", R.string.settings_group_panel, listOf(NAV_BAR, QUICK_TOGGLES)),
    LOOK("look", R.string.settings_group_look, listOf(APPEARANCE, THEMES)),
    EXTRAS("extras", R.string.settings_group_extras, listOf(REPLAY, KEYBOARD)),
    ADVANCED("advanced", R.string.settings_group_advanced, listOf(DIAGNOSTICS, EXPERIMENTAL));

    /** Pref holding this group's own section order (a CSV of section keys). */
    val sectionOrderKey: String get() = "rail_sections_$key"

    companion object {
        /** The section pinned at the top of the rail, outside any group. */
        val PINNED = SettingsCategory.GENERAL

        fun fromKey(key: String): RailGroup? = entries.firstOrNull { it.key == key }

        /** The group holding [category], or null for the pinned section. */
        fun of(category: SettingsCategory): RailGroup? = entries.firstOrNull { category in it.sections }
    }
}

/**
 * Reads a saved CSV of keys against [defaults]. Unknown keys, keys not in [defaults] and repeats are dropped; items
 * missing from it (new ones, or an older save) go in after the nearest item that comes before them by default.
 */
internal fun <T : Any> tolerantOrder(csv: String?, defaults: List<T>, fromKey: (String) -> T?): List<T> {
    val saved = csv?.split(",")?.mapNotNull { fromKey(it.trim()) }?.filter { it in defaults }?.distinct().orEmpty()
    val out = saved.toMutableList()
    defaults.forEachIndexed { i, item ->
        if (item in out) return@forEachIndexed
        val prev = defaults.subList(0, i).lastOrNull { it in out }
        out.add(if (prev == null) 0 else out.indexOf(prev) + 1, item)
    }
    return out
}

/**
 * The rail's two-level order: the [groups] among themselves, and each group's [sections] within it. A section never
 * leaves its own group. Pure, so it is unit-tested; prefs go in and out through [read] and [prefs].
 */
data class RailLayout(
    val groups: List<RailGroup>,
    val sections: Map<RailGroup, List<SettingsCategory>>,
) {
    fun sectionsOf(group: RailGroup): List<SettingsCategory> = sections[group] ?: group.sections

    /** Every section top to bottom: the pinned one, then each group's sections in order. */
    val flat: List<SettingsCategory> get() = listOf(RailGroup.PINNED) + groups.flatMap(::sectionsOf)

    /** A new group order (from a reorder); keys it doesn't name keep their default spot. */
    fun withGroupOrder(keys: List<String>): RailLayout =
        copy(groups = tolerantOrder(keys.joinToString(","), RailGroup.entries, RailGroup::fromKey))

    /** A new section order inside [group]; sections from other groups are ignored. */
    fun withSectionOrder(group: RailGroup, keys: List<String>): RailLayout =
        copy(sections = sections + (group to tolerantOrder(keys.joinToString(","), group.sections, SettingsCategory::fromKey)))

    /**
     * The prefs to save. Also writes the flat legacy order, so an older build (or an older restore path) still sees
     * the user's order.
     */
    fun prefs(): Map<String, String> =
        mapOf(KEY_GROUP_ORDER to groups.joinToString(",") { it.key }) +
            RailGroup.entries.associate { g -> g.sectionOrderKey to sectionsOf(g).joinToString(",") { it.key } } +
            (KEY_LEGACY_ORDER to flat.joinToString(",") { it.key })

    companion object {
        const val KEY_GROUP_ORDER = "rail_group_order"

        /** The flat section order saved before rail groups existed. */
        const val KEY_LEGACY_ORDER = "rail_order"

        /** Every pref the two-level order is saved in. */
        val KEYS: Set<String> = setOf(KEY_GROUP_ORDER) + RailGroup.entries.map { it.sectionOrderKey }

        val DEFAULT = RailLayout(RailGroup.entries, RailGroup.entries.associateWith { it.sections })

        /**
         * Loads the layout from prefs ([get] returns a pref's string, or null). With none of the two-level prefs saved
         * yet, the legacy flat order is migrated.
         */
        fun read(get: (String) -> String?): RailLayout {
            if (KEYS.none { get(it) != null }) return migrate(get(KEY_LEGACY_ORDER))
            return RailLayout(
                tolerantOrder(get(KEY_GROUP_ORDER), RailGroup.entries, RailGroup::fromKey),
                RailGroup.entries.associateWith { g -> tolerantOrder(get(g.sectionOrderKey), g.sections, SettingsCategory::fromKey) },
            )
        }

        /**
         * Maps a flat saved order onto groups: groups go in the order their first section appears, and sections keep
         * their relative order inside their group. Where General sat is dropped, since it is pinned now.
         */
        fun migrate(legacyCsv: String?): RailLayout {
            val flat = SettingsCategory.ordered(legacyCsv)
            return RailLayout(
                RailGroup.entries.sortedBy { g -> g.sections.minOf(flat::indexOf) },
                RailGroup.entries.associateWith { g -> g.sections.sortedBy(flat::indexOf) },
            )
        }

        /**
         * Two-level prefs to clear after restoring a backup: one made before rail groups carries only the legacy
         * order, so the current two-level prefs are dropped and that order is migrated instead.
         */
        fun staleAfterRestore(restored: Set<String>): Set<String> =
            if (KEY_LEGACY_ORDER in restored && KEYS.none { it in restored }) KEYS else emptySet()
    }
}

/** One row on the rail: the pinned section, a group header, or a section inside an expanded group. */
sealed interface RailRow {
    /** Stable identity, for `key(...)` and edge scrolling. */
    val id: String

    data class Section(val category: SettingsCategory) : RailRow {
        override val id: String get() = category.key
    }

    data class Header(val group: RailGroup) : RailRow {
        override val id: String get() = "group:${group.key}"
    }
}

/** Which groups are expanded on the rail, saved in `rail_expanded` as a CSV of group keys. */
object RailExpanded {
    const val KEY = "rail_expanded"

    /** Unknown keys are dropped; nothing saved means every group starts collapsed. */
    fun parse(csv: String?): Set<RailGroup> =
        csv?.split(",")?.mapNotNull { RailGroup.fromKey(it.trim()) }?.toSet().orEmpty()

    fun encode(groups: Set<RailGroup>): String = RailGroup.entries.filter { it in groups }.joinToString(",") { it.key }

    /** The General switch for [toggle]'s one-open rule (#82); on by default. */
    const val KEY_ONE_OPEN = "rail_one_group_open"

    /**
     * The open groups after opening or closing [group]. With [oneOpen], opening a group closes every other one;
     * closing only ever removes [group].
     */
    fun toggle(current: Set<RailGroup>, group: RailGroup, expanded: Boolean, oneOpen: Boolean): Set<RailGroup> = when {
        !expanded -> current - group
        oneOpen -> setOf(group)
        else -> current + group
    }

    /**
     * [groups] cut down to one open group when [oneOpen] (a pref saved with several open, or the switch just turned
     * on): [keep] if it's open, else the first open one in rail [order].
     */
    fun limit(groups: Set<RailGroup>, oneOpen: Boolean, order: List<RailGroup> = RailGroup.entries, keep: RailGroup? = null): Set<RailGroup> =
        if (!oneOpen || groups.size <= 1) groups
        else setOf(keep?.takeIf { it in groups } ?: order.first { it in groups })
}

/** The rows on screen, top to bottom: sections of collapsed groups aren't there, so focus can't land on them. */
fun RailLayout.visibleRows(expanded: Set<RailGroup>): List<RailRow> = buildList {
    add(RailRow.Section(RailGroup.PINNED))
    for (g in groups) {
        add(RailRow.Header(g))
        if (g in expanded) sectionsOf(g).forEach { add(RailRow.Section(it)) }
    }
}

/** Where rail focus goes for the [selected] section: the section itself, or its group's header while collapsed. */
fun railFocusTarget(selected: SettingsCategory, expanded: Set<RailGroup>): RailRow {
    val group = RailGroup.of(selected)
    return if (group == null || group in expanded) RailRow.Section(selected) else RailRow.Header(group)
}
