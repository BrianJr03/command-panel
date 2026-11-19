package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.overlay.QuickTileSizing
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTileSizingTest {

    private val label = 27f // two 10 sp lines plus the gap, at font scale 1

    @Test
    fun smallCardKeepsTheOldFixedSizes() {
        assertEquals(48f, QuickTileSizing.circleDp(240f, 130f, rows = 2, labelDp = label, hideLabels = false), 0.01f)
        assertEquals(50f, QuickTileSizing.circleDp(240f, 100f, rows = 2, labelDp = 0f, hideLabels = true), 0.01f)
    }

    @Test
    fun growsWithTheCardUpToTheCap() {
        // 400 x 220 dp, two rows: 100 dp wide cells (70 dp) and 110 dp tall cells (110 - 27 - 10 = 73 dp).
        assertEquals(70f, QuickTileSizing.circleDp(400f, 220f, rows = 2, labelDp = label, hideLabels = false), 0.01f)
        assertEquals(QuickTileSizing.MAX_DP, QuickTileSizing.circleDp(1000f, 1000f, rows = 2, labelDp = label, hideLabels = false), 0.01f)
    }

    @Test
    fun theTighterSideWins() {
        // Wide but short: the height limits it (180 / 2 - 27 - 10 = 53 dp).
        assertEquals(53f, QuickTileSizing.circleDp(800f, 180f, rows = 2, labelDp = label, hideLabels = false), 0.01f)
        // Tall but narrow: the width limits it (320 / 4 * 0.7 = 56 dp).
        assertEquals(56f, QuickTileSizing.circleDp(320f, 800f, rows = 2, labelDp = label, hideLabels = false), 0.01f)
    }

    @Test
    fun hiddenLabelsFreeTheirHeight() {
        // 150 / 2 = 75 dp cells: 75 - 27 - 10 = 38 (floored to 48) with labels, 75 - 10 = 65 without.
        assertEquals(48f, QuickTileSizing.circleDp(800f, 150f, rows = 2, labelDp = label, hideLabels = false), 0.01f)
        assertEquals(65f, QuickTileSizing.circleDp(800f, 150f, rows = 2, labelDp = 0f, hideLabels = true), 0.01f)
    }

    @Test
    fun rowsPerPage() {
        assertEquals(1, QuickTileSizing.rows(0))
        assertEquals(1, QuickTileSizing.rows(4))
        assertEquals(2, QuickTileSizing.rows(5))
        assertEquals(2, QuickTileSizing.rows(8))
        assertEquals(2, QuickTileSizing.rows(20))
    }
}
