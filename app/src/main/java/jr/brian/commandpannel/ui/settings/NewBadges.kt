package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.ui.settings.SettingsCategory.KEYBOARD

/** Where a "new" bubble sits: a rail group's header, a section on the rail, or a card inside a section's pane. */
sealed interface NewPlace {
    data class Group(val group: RailGroup) : NewPlace
    data class Section(val section: SettingsCategory) : NewPlace
    data class Card(val section: SettingsCategory, val card: String) : NewPlace
}

/** One thing an update added or changed: [since] is the versionCode it ships in. [key] is what the seen set stores. */
data class NewItem(val key: String, val since: Int, val place: NewPlace)

/** The registry of new things. Marking something for a future release is one line in [ALL]. */
object NewItems {
    /** Card keys; each is tagged in its pane with `Modifier.newCard(...)` and its title shows the bubble. */
    const val CARD_THORKBM = "thorkbm"
    const val CARD_KEYBOARD_TILE = "tile_keyboard"
    const val CARD_THORKBM_NAV = "thorkbm_nav"
    const val CARD_THORKBM_TILE = "thorkbm_tile"
    const val CARD_REPLAY_STUDIO = "replay_studio"
    const val CARD_REPLAY_SOURCE_SCREEN = "replay_source_screen"
    const val CARD_REPLAY_AUTO_DELETE = "replay_auto_delete"

    /** Tile rows in Tiles settings that can carry a bubble: tile key to card key. */
    val TILE_CARDS: Map<String, String> = mapOf("keyboard" to CARD_KEYBOARD_TILE)

    /** The cards that can carry a bubble, by section (a registry card must be one of these). */
    val CARDS: Map<SettingsCategory, Set<String>> = mapOf(
        KEYBOARD to setOf(CARD_THORKBM, CARD_THORKBM_NAV, CARD_THORKBM_TILE),
        SettingsCategory.QUICK_TOGGLES to setOf(CARD_KEYBOARD_TILE),
        SettingsCategory.REPLAY to setOf(CARD_REPLAY_STUDIO, CARD_REPLAY_SOURCE_SCREEN, CARD_REPLAY_AUTO_DELETE),
    )

    /** versionCode of the release that brings the grouped rail, Keyboard, ThorKBM and the Keyboard tile (0.9.1 is 14). */
    private const val V_GROUPED_RAIL = 15

    /** versionCode of the release after 0.10.0: the Keyboard section's nav bar and tile toggles (#43). */
    private const val V_KEYBOARD_TOGGLES = 16

    /** versionCode of the release after 0.10.1: Replay Studio (#44 to #51). */
    internal const val V_REPLAY_STUDIO = 17

    val ALL: List<NewItem> = buildList {
        // The grouped rail itself: each group header is new.
        RailGroup.entries.forEach { add(NewItem("group:${it.key}", V_GROUPED_RAIL, NewPlace.Group(it))) }
        add(NewItem("section:keyboard", V_GROUPED_RAIL, NewPlace.Section(KEYBOARD)))
        add(NewItem("card:thorkbm", V_GROUPED_RAIL, NewPlace.Card(KEYBOARD, CARD_THORKBM)))
        add(NewItem("card:tile_keyboard", V_GROUPED_RAIL, NewPlace.Card(SettingsCategory.QUICK_TOGGLES, CARD_KEYBOARD_TILE)))
        add(NewItem("card:thorkbm_nav", V_KEYBOARD_TOGGLES, NewPlace.Card(KEYBOARD, CARD_THORKBM_NAV)))
        add(NewItem("card:thorkbm_tile", V_KEYBOARD_TOGGLES, NewPlace.Card(KEYBOARD, CARD_THORKBM_TILE)))
        add(NewItem("card:replay_studio", V_REPLAY_STUDIO, NewPlace.Card(SettingsCategory.REPLAY, CARD_REPLAY_STUDIO)))
        add(NewItem("card:replay_source_screen", V_REPLAY_STUDIO, NewPlace.Card(SettingsCategory.REPLAY, CARD_REPLAY_SOURCE_SCREEN)))
        add(NewItem("card:replay_auto_delete", V_REPLAY_STUDIO, NewPlace.Card(SettingsCategory.REPLAY, CARD_REPLAY_AUTO_DELETE)))
    }
}

/**
 * Which bubbles show. An item is new while it shipped after the install's [baseline] and hasn't been [seen]. A section
 * is new while it, or a card in it, is new; a group while it, or a section in it, is new, so a parent clears once its
 * new children are seen. Pure, so it is unit-tested.
 */
data class NewBadgeState(
    /** Nothing at or below this versionCode shows. [Int.MAX_VALUE] (not loaded yet) shows nothing. */
    val baseline: Int = Int.MAX_VALUE,
    val seen: Set<String> = emptySet(),
    val items: List<NewItem> = NewItems.ALL,
) {
    fun isNew(item: NewItem): Boolean = item.since > baseline && item.key !in seen

    fun cardNew(section: SettingsCategory, card: String): Boolean =
        items.any { it.place == NewPlace.Card(section, card) && isNew(it) }

    fun sectionNew(section: SettingsCategory): Boolean = items.any {
        isNew(it) && (it.place == NewPlace.Section(section) || (it.place is NewPlace.Card && it.place.section == section))
    }

    fun groupNew(group: RailGroup): Boolean =
        items.any { it.place == NewPlace.Group(group) && isNew(it) } || group.sections.any(::sectionNew)

    /** Keys to mark seen when [place] is focused or clicked (only items at that exact place; parents roll up). */
    fun keysAt(place: NewPlace): Set<String> = items.filter { it.place == place && isNew(it) }.mapTo(mutableSetOf()) { it.key }

    /** What the screens see: this state, or nothing new while "Show New Dots" is off (#54). [seen] is kept as is. */
    fun shown(showDots: Boolean): NewBadgeState = if (showDots) this else copy(baseline = Int.MAX_VALUE)

    companion object {
        const val KEY_BASELINE = "new_badge_baseline"
        const val KEY_SEEN = "new_badge_seen"

        /** The baseline for someone upgrading from a version before bubbles existed (0.9.1 and older). */
        const val LEGACY_BASELINE = 14

        /**
         * The baseline stored the first time this runs. An upgrade from a version before bubbles sees everything
         * since [LEGACY_BASELINE]; a fresh install sees nothing that's already in the registry, only later updates.
         */
        fun firstBaseline(versionCode: Int, upgraded: Boolean, items: List<NewItem> = NewItems.ALL): Int =
            if (upgraded) LEGACY_BASELINE else maxOf(versionCode, items.maxOfOrNull { it.since } ?: versionCode)

        fun parseSeen(csv: String?): Set<String> = csv?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

        fun encodeSeen(seen: Set<String>): String = seen.sorted().joinToString(",")
    }
}
