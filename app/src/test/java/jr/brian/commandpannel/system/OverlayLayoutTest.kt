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
        val fq = default.applyDrop(OverlayCardId.FRAME_RATE, card(OverlayCardId.QUICK_TILES))
        assertEquals(GridCell(0, 1), fq.settingsCells[OverlayCardId.FRAME_RATE])
        assertEquals(GridCell(0, 0), fq.settingsCells[OverlayCardId.QUICK_TILES])

        val tr = default.applyDrop(OverlayCardId.TEMP, card(OverlayCardId.RINGS))
        assertEquals(GridCell(1, 1), tr.settingsCells[OverlayCardId.TEMP])
        assertEquals(GridCell(1, 0), tr.settingsCells[OverlayCardId.RINGS])
        assertEquals(GridCell(0, 0), tr.settingsCells[OverlayCardId.FRAME_RATE]) // untouched
    }

    @Test
    fun `move HALF to an empty cell leaves a gap`() {
        val r = default.applyDrop(OverlayCardId.TEMP, empty(OverlayCardGroup.SETTINGS_TAB, 3, 0))
        assertEquals(GridCell(3, 0), r.settingsCells[OverlayCardId.TEMP])
        assertFalse(r.settingsCells.values.contains(GridCell(1, 0))) // old slot is now a gap
        assertEquals(GridCell(1, 1), r.settingsCells[OverlayCardId.RINGS]) // partner stays put
        assertTrue(OverlayLayout.isValid(OverlayCardGroup.SETTINGS_TAB, r.settingsCells))
    }

    @Test
    fun `FULL onto HALF swaps the two whole rows`() {
        val r = default.applyDrop(OverlayCardId.MODE_BUTTONS, card(OverlayCardId.VOLUME))
        assertEquals(GridCell(1, 0), r.modeCells[OverlayCardId.MODE_BUTTONS])
        assertEquals(GridCell(0, 0), r.modeCells[OverlayCardId.BRIGHTNESS])
        assertEquals(GridCell(0, 1), r.modeCells[OverlayCardId.VOLUME])
    }

    @Test
    fun `move FULL to a free row works, onto an occupied row is a no-op`() {
        val moved = default.applyDrop(OverlayCardId.MODE_BUTTONS, empty(OverlayCardGroup.MODE_TAB, 5, 0))
        assertEquals(GridCell(5, 0), moved.modeCells[OverlayCardId.MODE_BUTTONS])

        assertSame(default, default.applyDrop(OverlayCardId.MODE_BUTTONS, empty(OverlayCardGroup.MODE_TAB, 1, 0)))
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
            .applyDrop(OverlayCardId.TEMP, card(OverlayCardId.RINGS))
            .applyDrop(OverlayCardId.FRAME_RATE, card(OverlayCardId.QUICK_TILES))
            .applyDrop(OverlayCardId.BRIGHTNESS, card(OverlayCardId.VOLUME))
            .applyDrop(OverlayCardId.MODE_BUTTONS, card(OverlayCardId.BRIGHTNESS))
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
        val duplicate = "v=2;settings=frame_rate:0:0,quick_tiles:0:0,temp:1:0,rings:1:1"
        assertEquals(OverlayLayout.DEFAULT_SETTINGS_CELLS, OverlayLayout.decode(duplicate).settingsCells)

        // MODE_BUTTONS is FULL, so it must sit at col 0 — col 1 is invalid and reverts the mode tab.
        val fullAtCol1 = "v=2;mode=mode_buttons:0:1,brightness:1:0,volume:1:1"
        assertEquals(OverlayLayout.DEFAULT_MODE_CELLS, OverlayLayout.decode(fullAtCol1).modeCells)
    }

    @Test
    fun `isValid rejects a FULL card sharing its row`() {
        val bad = mapOf(
            OverlayCardId.MODE_BUTTONS to GridCell(0, 0), // FULL
            OverlayCardId.BRIGHTNESS to GridCell(0, 1),   // shares row 0 with the FULL card
            OverlayCardId.VOLUME to GridCell(1, 0),
        )
        assertFalse(OverlayLayout.isValid(OverlayCardGroup.MODE_TAB, bad))
        assertNull(default.cells(OverlayCardGroup.NAV_BAR)[OverlayCardId.NAV_BAR])
    }
}
