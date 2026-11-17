package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutTest {

    private val default = OverlayLayout.DEFAULT
    private fun card(id: OverlayCardId) = DropTarget.Card(id)
    private fun empty(group: OverlayCardGroup, row: Int, col: Int) = DropTarget.Empty(group, GridCell(row, col))

    @Test
    fun `swap same-span mode cards exchanges only those cells`() {
        val r = default.applyDrop(OverlayCardId.VOLUME, card(OverlayCardId.BRIGHTNESS))
        assertEquals(GridCell(1, 0), r.modeCells[OverlayCardId.VOLUME])
        assertEquals(GridCell(1, 1), r.modeCells[OverlayCardId.BRIGHTNESS])
        assertEquals(GridCell(0, 0), r.modeCells[OverlayCardId.MODE_BUTTONS]) // untouched
    }

    @Test
    fun `swap same-span settings cards`() {
        val tf = default.applyDrop(OverlayCardId.TEMP, card(OverlayCardId.FAN))
        assertEquals(GridCell(2, 1), tf.settingsCells[OverlayCardId.TEMP])
        assertEquals(GridCell(2, 0), tf.settingsCells[OverlayCardId.FAN])

        val fq = default.applyDrop(OverlayCardId.FRAME_RATE, card(OverlayCardId.QUICK_TILES))
        assertEquals(GridCell(0, 1), fq.settingsCells[OverlayCardId.FRAME_RATE])
        assertEquals(GridCell(0, 0), fq.settingsCells[OverlayCardId.QUICK_TILES])
        assertEquals(default.settingsCells[OverlayCardId.RINGS], fq.settingsCells[OverlayCardId.RINGS]) // untouched
    }

    @Test
    fun `move HALF to an empty cell leaves a gap`() {
        val r = default.applyDrop(OverlayCardId.TEMP, empty(OverlayCardGroup.SETTINGS_TAB, 3, 0))
        assertEquals(GridCell(3, 0), r.settingsCells[OverlayCardId.TEMP])
        assertFalse(r.settingsCells.values.contains(GridCell(2, 0))) // old slot is now an empty gap
        assertEquals(GridCell(2, 1), r.settingsCells[OverlayCardId.FAN]) // partner stays put
        assertTrue(OverlayLayout.isValid(OverlayCardGroup.SETTINGS_TAB, r.settingsCells))
    }

    @Test
    fun `FULL onto HALF swaps the two whole rows`() {
        val r = default.applyDrop(OverlayCardId.MODE_BUTTONS, card(OverlayCardId.VOLUME))
        assertEquals(GridCell(1, 0), r.modeCells[OverlayCardId.MODE_BUTTONS])
        assertEquals(GridCell(0, 0), r.modeCells[OverlayCardId.BRIGHTNESS])
        assertEquals(GridCell(0, 1), r.modeCells[OverlayCardId.VOLUME])

        val s = default.applyDrop(OverlayCardId.RINGS, card(OverlayCardId.TEMP))
        assertEquals(GridCell(2, 0), s.settingsCells[OverlayCardId.RINGS])
        assertEquals(GridCell(1, 0), s.settingsCells[OverlayCardId.TEMP])
        assertEquals(GridCell(1, 1), s.settingsCells[OverlayCardId.FAN])
        assertEquals(GridCell(0, 0), s.settingsCells[OverlayCardId.FRAME_RATE]) // other row untouched
    }

    @Test
    fun `move FULL to a free row works, onto an occupied row is a no-op`() {
        val moved = default.applyDrop(OverlayCardId.RINGS, empty(OverlayCardGroup.SETTINGS_TAB, 5, 0))
        assertEquals(GridCell(5, 0), moved.settingsCells[OverlayCardId.RINGS])

        assertSame(default, default.applyDrop(OverlayCardId.RINGS, empty(OverlayCardGroup.SETTINGS_TAB, 0, 0)))
    }

    @Test
    fun `invalid drops leave the layout untouched`() {
        assertSame(default, default.applyDrop(OverlayCardId.VOLUME, card(OverlayCardId.VOLUME)))
        assertSame(default, default.applyDrop(OverlayCardId.VOLUME, card(OverlayCardId.RINGS)))          // cross-group
        assertSame(default, default.applyDrop(OverlayCardId.NAV_BAR, card(OverlayCardId.MODE_BUTTONS)))
        assertSame(default, default.applyDrop(OverlayCardId.VOLUME, empty(OverlayCardGroup.SETTINGS_TAB, 0, 0))) // cross-group empty
    }

    @Test
    fun `layout stays valid after a sequence of drops`() {
        val r = default
            .applyDrop(OverlayCardId.TEMP, card(OverlayCardId.FAN))
            .applyDrop(OverlayCardId.RINGS, card(OverlayCardId.FRAME_RATE))
            .applyDrop(OverlayCardId.BRIGHTNESS, card(OverlayCardId.VOLUME))
        assertTrue(OverlayLayout.isValid(OverlayCardGroup.MODE_TAB, r.modeCells))
        assertTrue(OverlayLayout.isValid(OverlayCardGroup.SETTINGS_TAB, r.settingsCells))
    }

    @Test
    fun `isDefault and nav position`() {
        assertTrue(default.isDefault)
        val bottom = default.withNavBarPosition(NavBarPosition.BOTTOM)
        assertEquals(NavBarPosition.BOTTOM, bottom.navBarPosition)
        assertFalse(bottom.isDefault)
        assertTrue(bottom.withNavBarPosition(NavBarPosition.TOP).isDefault) // default nav is TOP
    }

    @Test
    fun `encode and decode round-trip a custom layout with a gap`() {
        val custom = default
            .applyDrop(OverlayCardId.TEMP, empty(OverlayCardGroup.SETTINGS_TAB, 3, 0))
            .applyDrop(OverlayCardId.MODE_BUTTONS, card(OverlayCardId.VOLUME))
            .withNavBarPosition(NavBarPosition.BOTTOM)
        assertEquals(custom, OverlayLayout.decode(custom.encode()))
        assertEquals(default, OverlayLayout.decode(default.encode()))
    }

    @Test
    fun `decode falls back to defaults for null, garbage, and old v1 strings`() {
        assertEquals(default, OverlayLayout.decode(null))
        assertEquals(default, OverlayLayout.decode(""))
        assertEquals(default, OverlayLayout.decode("garbage;;=;nav=sideways;future_key=1"))
        // Old v1 format has no mode=/settings= lists, so both grids reset to default.
        val oldV1 = OverlayLayout.decode("v=1;mode_rows=1;mode_halves=volume,brightness;settings_quarters=fan,temp")
        assertEquals(OverlayLayout.DEFAULT_MODE_CELLS, oldV1.modeCells)
        assertEquals(OverlayLayout.DEFAULT_SETTINGS_CELLS, oldV1.settingsCells)
    }

    @Test
    fun `decode repairs a bad tab while keeping a valid one`() {
        // mode is a valid non-default (brightness/volume swapped); settings is incomplete -> reverts.
        val d = OverlayLayout.decode("v=2;mode=mode_buttons:0:0,brightness:1:1,volume:1:0;settings=frame_rate:0:0;nav=top")
        assertEquals(GridCell(1, 1), d.modeCells[OverlayCardId.BRIGHTNESS])
        assertEquals(GridCell(1, 0), d.modeCells[OverlayCardId.VOLUME])
        assertEquals(OverlayLayout.DEFAULT_SETTINGS_CELLS, d.settingsCells)
    }

    @Test
    fun `decode rejects overlapping cells and misplaced FULL cards`() {
        val duplicate = "v=2;settings=frame_rate:0:0,quick_tiles:0:0,rings:1:0,temp:2:0,fan:2:1"
        assertEquals(OverlayLayout.DEFAULT_SETTINGS_CELLS, OverlayLayout.decode(duplicate).settingsCells)

        val fullAtCol1 = "v=2;settings=frame_rate:0:0,quick_tiles:0:1,rings:1:1,temp:2:0,fan:2:1"
        assertEquals(OverlayLayout.DEFAULT_SETTINGS_CELLS, OverlayLayout.decode(fullAtCol1).settingsCells)
    }

    @Test
    fun `isValid rejects a FULL card sharing its row`() {
        val bad = mapOf(
            OverlayCardId.FRAME_RATE to GridCell(0, 0),
            OverlayCardId.QUICK_TILES to GridCell(0, 1),
            OverlayCardId.RINGS to GridCell(0, 0), // shares row 0 with the halves (also duplicate cell)
            OverlayCardId.TEMP to GridCell(2, 0),
            OverlayCardId.FAN to GridCell(2, 1),
        )
        assertFalse(OverlayLayout.isValid(OverlayCardGroup.SETTINGS_TAB, bad))
        assertNull(default.cells(OverlayCardGroup.NAV_BAR)[OverlayCardId.NAV_BAR])
    }
}
