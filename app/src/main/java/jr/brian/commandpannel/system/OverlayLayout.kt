package jr.brian.commandpannel.system

/**
 * Cards of the bottom-screen overlay that can be rearranged in Arrange mode.
 *
 * Cards never change size: dropping a card onto a same-sized card swaps the two, while dropping
 * it onto a differently sized card swaps the whole row and/or column that holds them.
 */
enum class OverlayCardId(val key: String, val group: OverlayCardGroup) {
    MODE_BUTTONS("mode_buttons", OverlayCardGroup.MODE_TAB),
    BRIGHTNESS("brightness", OverlayCardGroup.MODE_TAB),
    VOLUME("volume", OverlayCardGroup.MODE_TAB),
    FRAME_RATE("frame_rate", OverlayCardGroup.SETTINGS_TAB),
    QUICK_TILES("quick_tiles", OverlayCardGroup.SETTINGS_TAB),
    TEMP("temp", OverlayCardGroup.SETTINGS_TAB),
    FAN("fan", OverlayCardGroup.SETTINGS_TAB),
    RINGS("rings", OverlayCardGroup.SETTINGS_TAB),
    NAV_BAR("nav_bar", OverlayCardGroup.NAV_BAR);

    companion object {
        fun fromKey(key: String): OverlayCardId? = entries.firstOrNull { it.key == key }
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

/**
 * Persisted overlay arrangement. Swap flags are relative to the stock layout:
 *
 * - Mode tab: row 0 = [MODE_BUTTONS], row 1 = [modeHalfOrder[0] | modeHalfOrder[1]].
 * - Settings tab: row 0 = [FRAME_RATE | QUICK_TILES], row 1 = [quarters(TEMP, FAN) | RINGS];
 *   column 0 is the narrow left column. Swapped rows/columns keep their own weights.
 */
data class OverlayLayout(
    val modeRowsSwapped: Boolean = false,
    val modeHalfOrder: List<OverlayCardId> = DEFAULT_MODE_HALVES,
    val settingsRowsSwapped: Boolean = false,
    val settingsColsSwapped: Boolean = false,
    val settingsQuarterOrder: List<OverlayCardId> = DEFAULT_SETTINGS_QUARTERS,
    val navBarPosition: NavBarPosition = NavBarPosition.BOTTOM
) {
    val isDefault: Boolean get() = this == DEFAULT

    /** Returns the layout after dropping [dragged] onto [target], or this layout if the drop is not valid. */
    fun applyDrop(dragged: OverlayCardId, target: OverlayCardId): OverlayLayout {
        if (dragged == target || dragged.group != target.group) return this
        return when (dragged.group) {
            OverlayCardGroup.MODE_TAB -> applyModeDrop(dragged, target)
            OverlayCardGroup.SETTINGS_TAB -> applySettingsDrop(dragged, target)
            OverlayCardGroup.NAV_BAR -> this
        }
    }

    fun withNavBarPosition(position: NavBarPosition): OverlayLayout = copy(navBarPosition = position)

    private fun applyModeDrop(dragged: OverlayCardId, target: OverlayCardId): OverlayLayout {
        return if (dragged in DEFAULT_MODE_HALVES && target in DEFAULT_MODE_HALVES) {
            copy(modeHalfOrder = modeHalfOrder.reversed())
        } else {
            copy(modeRowsSwapped = !modeRowsSwapped)
        }
    }

    private fun applySettingsDrop(dragged: OverlayCardId, target: OverlayCardId): OverlayLayout {
        if (dragged in DEFAULT_SETTINGS_QUARTERS && target in DEFAULT_SETTINGS_QUARTERS) {
            return copy(settingsQuarterOrder = settingsQuarterOrder.reversed())
        }
        val (draggedRow, draggedCol) = settingsCell(dragged)
        val (targetRow, targetCol) = settingsCell(target)
        return copy(
            settingsRowsSwapped = settingsRowsSwapped xor (draggedRow != targetRow),
            settingsColsSwapped = settingsColsSwapped xor (draggedCol != targetCol)
        )
    }

    /** Serialized as `key=value` pairs separated by `;`, with orders as comma-separated card keys. */
    fun encode(): String = listOf(
        "v=$FORMAT_VERSION",
        "mode_rows=${if (modeRowsSwapped) 1 else 0}",
        "mode_halves=${modeHalfOrder.joinToString(",") { it.key }}",
        "settings_rows=${if (settingsRowsSwapped) 1 else 0}",
        "settings_cols=${if (settingsColsSwapped) 1 else 0}",
        "settings_quarters=${settingsQuarterOrder.joinToString(",") { it.key }}",
        "nav=${navBarPosition.key}"
    ).joinToString(";")

    companion object {
        const val FORMAT_VERSION = 1
        val DEFAULT_MODE_HALVES = listOf(OverlayCardId.BRIGHTNESS, OverlayCardId.VOLUME)
        val DEFAULT_SETTINGS_QUARTERS = listOf(OverlayCardId.TEMP, OverlayCardId.FAN)
        val DEFAULT = OverlayLayout()

        /** Parses [encode] output; unknown keys are ignored and invalid values fall back to defaults. */
        fun decode(raw: String?): OverlayLayout {
            if (raw.isNullOrBlank()) return DEFAULT
            val values = raw.split(";").mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) null else entry.substring(0, separator).trim() to entry.substring(separator + 1).trim()
            }.toMap()
            return OverlayLayout(
                modeRowsSwapped = values["mode_rows"] == "1",
                modeHalfOrder = parseOrder(values["mode_halves"], DEFAULT_MODE_HALVES),
                settingsRowsSwapped = values["settings_rows"] == "1",
                settingsColsSwapped = values["settings_cols"] == "1",
                settingsQuarterOrder = parseOrder(values["settings_quarters"], DEFAULT_SETTINGS_QUARTERS),
                navBarPosition = values["nav"]?.let { NavBarPosition.fromKey(it) } ?: NavBarPosition.BOTTOM
            )
        }

        /** Accepts the saved order only if it is exactly a permutation of [defaultOrder]. */
        private fun parseOrder(raw: String?, defaultOrder: List<OverlayCardId>): List<OverlayCardId> {
            val parsed = raw?.split(",")?.mapNotNull { OverlayCardId.fromKey(it.trim()) }.orEmpty()
            return if (parsed.size == defaultOrder.size && parsed.toSet() == defaultOrder.toSet()) {
                parsed
            } else {
                defaultOrder
            }
        }

        /** Logical (row, column) of a Settings-tab card in the stock layout. */
        private fun settingsCell(card: OverlayCardId): Pair<Int, Int> = when (card) {
            OverlayCardId.FRAME_RATE -> 0 to 0
            OverlayCardId.QUICK_TILES -> 0 to 1
            OverlayCardId.TEMP, OverlayCardId.FAN -> 1 to 0
            else -> 1 to 1
        }
    }
}
