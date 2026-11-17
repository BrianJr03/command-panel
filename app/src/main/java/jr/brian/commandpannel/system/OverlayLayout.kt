package jr.brian.commandpannel.system

/** How many columns of the 2-column overlay grid a card occupies. */
enum class CardSpan { FULL, HALF }

/**
 * Cards of the bottom-screen overlay that can be rearranged in Arrange mode. Each tab is a 2-column grid; a card has
 * an intrinsic [span] (FULL spans both columns and owns its row, HALF takes one column) and an intrinsic [rowWeight]
 * (its row's share of the tab height). Size is never user-changed, so span/weight are constants, not persisted.
 *
 * `NAV_BAR` is not a grid card — it is overlay chrome placed above or below the tab content; its position lives in
 * [OverlayLayout.navBarPosition].
 */
enum class OverlayCardId(
    val key: String,
    val group: OverlayCardGroup,
    val span: CardSpan = CardSpan.HALF,
    val rowWeight: Float = 1f,
) {
    MODE_BUTTONS("mode_buttons", OverlayCardGroup.MODE_TAB, CardSpan.FULL, 0.46f),
    BRIGHTNESS("brightness", OverlayCardGroup.MODE_TAB, CardSpan.HALF, 0.54f),
    VOLUME("volume", OverlayCardGroup.MODE_TAB, CardSpan.HALF, 0.54f),
    FRAME_RATE("frame_rate", OverlayCardGroup.SETTINGS_TAB, CardSpan.HALF, 0.38f),
    QUICK_TILES("quick_tiles", OverlayCardGroup.SETTINGS_TAB, CardSpan.HALF, 0.70f),
    TEMP("temp", OverlayCardGroup.SETTINGS_TAB, CardSpan.HALF, 0.30f),
    RINGS("rings", OverlayCardGroup.SETTINGS_TAB, CardSpan.HALF, 0.30f),
    NAV_BAR("nav_bar", OverlayCardGroup.NAV_BAR);

    companion object {
        fun fromKey(key: String): OverlayCardId? = entries.firstOrNull { it.key == key }
        fun of(group: OverlayCardGroup): List<OverlayCardId> = entries.filter { it.group == group }
    }
}

enum class OverlayCardGroup { MODE_TAB, SETTINGS_TAB, NAV_BAR }

enum class NavBarPosition(val key: String) {
    TOP("top"),
    BOTTOM("bottom");

    companion object {
        fun fromKey(key: String): NavBarPosition? = entries.firstOrNull { it.key == key }
    }
}

/** A card's position in the 2-column grid. [col] is 0 or 1; a FULL card sits at col 0 and owns the whole row. */
data class GridCell(val row: Int, val col: Int)

/** Where a drag was released: onto another card, or onto an empty grid cell. */
sealed interface DropTarget {
    data class Card(val id: OverlayCardId) : DropTarget
    data class Empty(val group: OverlayCardGroup, val cell: GridCell) : DropTarget
}

/**
 * Persisted overlay arrangement: an explicit [GridCell] per card, per tab, plus the nav-bar position. A drop is
 * always local — it swaps two cards or fills an empty cell — so moving one card never reflows the others.
 */
data class OverlayLayout(
    val modeCells: Map<OverlayCardId, GridCell> = DEFAULT_MODE_CELLS,
    val settingsCells: Map<OverlayCardId, GridCell> = DEFAULT_SETTINGS_CELLS,
    val navBarPosition: NavBarPosition = NavBarPosition.TOP,
) {
    val isDefault: Boolean get() = this == DEFAULT

    /** The cell map for [group] (empty for the nav bar, which isn't a grid card). */
    fun cells(group: OverlayCardGroup): Map<OverlayCardId, GridCell> = when (group) {
        OverlayCardGroup.MODE_TAB -> modeCells
        OverlayCardGroup.SETTINGS_TAB -> settingsCells
        OverlayCardGroup.NAV_BAR -> emptyMap()
    }

    fun withNavBarPosition(position: NavBarPosition): OverlayLayout = copy(navBarPosition = position)

    private fun withCells(group: OverlayCardGroup, cells: Map<OverlayCardId, GridCell>): OverlayLayout = when (group) {
        OverlayCardGroup.MODE_TAB -> copy(modeCells = cells)
        OverlayCardGroup.SETTINGS_TAB -> copy(settingsCells = cells)
        OverlayCardGroup.NAV_BAR -> this
    }

    /** Returns the layout after dropping [dragged] onto [target], or this layout if the drop is not valid. */
    fun applyDrop(dragged: OverlayCardId, target: DropTarget): OverlayLayout {
        val group = dragged.group
        if (group == OverlayCardGroup.NAV_BAR) return this
        val cells = cells(group).toMutableMap()
        val draggedCell = cells[dragged] ?: return this

        when (target) {
            is DropTarget.Card -> {
                val t = target.id
                if (t == dragged || t.group != group) return this
                val targetCell = cells[t] ?: return this
                if (dragged.span == t.span) {
                    // Same size: swap the two cards' cells.
                    cells[dragged] = targetCell
                    cells[t] = draggedCell
                } else {
                    // FULL <-> HALF: the two whole rows trade places.
                    return swapRows(group, cells, draggedCell.row, targetCell.row)
                }
            }
            is DropTarget.Empty -> {
                if (target.group != group) return this
                val cell = target.cell
                if (dragged.span == CardSpan.FULL) {
                    // A FULL card needs its whole target row free.
                    if (cells.any { (k, v) -> k != dragged && v.row == cell.row }) return this
                    cells[dragged] = GridCell(cell.row, 0)
                } else {
                    // A HALF card fills a single empty cell in a row that has no FULL card.
                    if (cells.any { (k, v) -> k != dragged && (v == cell || (v.row == cell.row && k.span == CardSpan.FULL)) }) return this
                    cells[dragged] = cell
                }
            }
        }
        return if (isValid(group, cells)) withCells(group, cells) else this
    }

    private fun swapRows(group: OverlayCardGroup, cells: Map<OverlayCardId, GridCell>, a: Int, b: Int): OverlayLayout {
        val next = cells.mapValues { (_, c) ->
            when (c.row) {
                a -> c.copy(row = b)
                b -> c.copy(row = a)
                else -> c
            }
        }
        return if (isValid(group, next)) withCells(group, next) else this
    }

    /** Serialized as `key=value;...`, each tab a comma-separated list of `cardKey:row:col`. */
    fun encode(): String = listOf(
        "v=$FORMAT_VERSION",
        "mode=${encodeCells(modeCells)}",
        "settings=${encodeCells(settingsCells)}",
        "nav=${navBarPosition.key}",
    ).joinToString(";")

    companion object {
        const val FORMAT_VERSION = 2

        val DEFAULT_MODE_CELLS = mapOf(
            OverlayCardId.MODE_BUTTONS to GridCell(0, 0),
            OverlayCardId.BRIGHTNESS to GridCell(1, 0),
            OverlayCardId.VOLUME to GridCell(1, 1),
        )
        val DEFAULT_SETTINGS_CELLS = mapOf(
            OverlayCardId.FRAME_RATE to GridCell(0, 0),
            OverlayCardId.QUICK_TILES to GridCell(0, 1),
            OverlayCardId.TEMP to GridCell(1, 0),
            OverlayCardId.RINGS to GridCell(1, 1),
        )
        val DEFAULT = OverlayLayout()

        /**
         * A cell map is valid when it holds exactly the group's cards, every col is 0/1, every FULL card is at col 0
         * and alone in its row, and no two cards share a cell. Keeps [applyDrop] and [decode] from ever overlapping.
         */
        fun isValid(group: OverlayCardGroup, cells: Map<OverlayCardId, GridCell>): Boolean {
            if (cells.keys != OverlayCardId.of(group).toSet()) return false
            if (cells.values.any { it.row < 0 || it.col !in 0..1 }) return false
            if (cells.any { (k, v) -> k.span == CardSpan.FULL && v.col != 0 }) return false
            if (cells.values.size != cells.values.toSet().size) return false
            for ((k, v) in cells) {
                if (k.span == CardSpan.FULL && cells.any { (k2, v2) -> k2 != k && v2.row == v.row }) return false
            }
            return true
        }

        private fun encodeCells(cells: Map<OverlayCardId, GridCell>): String =
            cells.entries.sortedBy { it.key.ordinal }.joinToString(",") { (id, c) -> "${id.key}:${c.row}:${c.col}" }

        /** Parses [encode] output. Each tab validates as a whole; any missing/extra/overlapping cell reverts that
         *  tab to its default. Old v1 strings (no `mode=`/`settings=`) fall through to defaults. */
        fun decode(raw: String?): OverlayLayout {
            if (raw.isNullOrBlank()) return DEFAULT
            val values = raw.split(";").mapNotNull { entry ->
                val sep = entry.indexOf('=')
                if (sep <= 0) null else entry.substring(0, sep).trim() to entry.substring(sep + 1).trim()
            }.toMap()
            return OverlayLayout(
                modeCells = parseCells(values["mode"], OverlayCardGroup.MODE_TAB, DEFAULT_MODE_CELLS),
                settingsCells = parseCells(values["settings"], OverlayCardGroup.SETTINGS_TAB, DEFAULT_SETTINGS_CELLS),
                navBarPosition = values["nav"]?.let { NavBarPosition.fromKey(it) } ?: NavBarPosition.TOP,
            )
        }

        private fun parseCells(
            raw: String?,
            group: OverlayCardGroup,
            default: Map<OverlayCardId, GridCell>,
        ): Map<OverlayCardId, GridCell> {
            if (raw.isNullOrBlank()) return default
            val parsed = raw.split(",").mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size != 3) return@mapNotNull null
                val id = OverlayCardId.fromKey(parts[0].trim())?.takeIf { it.group == group } ?: return@mapNotNull null
                val row = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
                val col = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
                id to GridCell(row, col)
            }.toMap()
            return if (isValid(group, parsed)) parsed else default
        }
    }
}
