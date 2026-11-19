package jr.brian.commandpannel.ui.overlay

import kotlin.math.min

/**
 * Sizes the quick-setting tile circles from the space the grid gets, so the icons grow with a big card instead of
 * leaving it hollow. Pure (plain dp floats) so it's unit tested; the icon inset is a fraction of the circle, so the
 * glyph grows with it.
 */
internal object QuickTileSizing {
    const val COLUMNS = 4
    const val PAGE_SIZE = 8

    /** The old fixed sizes are the floor, so a small card looks as it always did. */
    const val MIN_DP = 48f
    const val MIN_ICON_ONLY_DP = 50f
    const val MAX_DP = 72f

    /** Share of a cell's width the circle may take, leaving a gap between neighbors. */
    private const val WIDTH_SHARE = 0.7f

    /** Vertical room per cell beyond the circle and label: spacing between rows and the press animation's dip. */
    private const val VERTICAL_ROOM_DP = 10f

    /** Rows on a page: one row for up to 4 tiles, two for more (every page sizes like the first). */
    fun rows(tileCount: Int): Int = (tileCount.coerceIn(1, PAGE_SIZE) + COLUMNS - 1) / COLUMNS

    /**
     * Circle diameter in dp for a page of [pageWidthDp] x [pageHeightDp] with [rows] rows, where each label takes
     * [labelDp] (0 when hidden).
     */
    fun circleDp(pageWidthDp: Float, pageHeightDp: Float, rows: Int, labelDp: Float, hideLabels: Boolean): Float {
        val byWidth = pageWidthDp / COLUMNS * WIDTH_SHARE
        val byHeight = pageHeightDp / rows.coerceAtLeast(1) - labelDp - VERTICAL_ROOM_DP
        val floor = if (hideLabels) MIN_ICON_ONLY_DP else MIN_DP
        return min(byWidth, byHeight).coerceIn(floor, MAX_DP)
    }
}
