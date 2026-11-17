package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutTest {

    private val default = OverlayLayout.DEFAULT

    @Test
    fun `same-size mode cards swap halves`() {
        val result = default.applyDrop(OverlayCardId.VOLUME, OverlayCardId.BRIGHTNESS)
        assertEquals(listOf(OverlayCardId.VOLUME, OverlayCardId.BRIGHTNESS), result.modeHalfOrder)
        assertFalse(result.modeRowsSwapped)
    }

    @Test
    fun `mode buttons dropped on a slider card swaps the rows and keeps halves`() {
        val result = default.applyDrop(OverlayCardId.MODE_BUTTONS, OverlayCardId.VOLUME)
        assertTrue(result.modeRowsSwapped)
        assertEquals(OverlayLayout.DEFAULT_MODE_HALVES, result.modeHalfOrder)
        // Dragging a slider card back onto the mode card restores the stock order.
        assertEquals(default, result.applyDrop(OverlayCardId.BRIGHTNESS, OverlayCardId.MODE_BUTTONS))
    }

    @Test
    fun `temp and fan swap quarters only`() {
        val result = default.applyDrop(OverlayCardId.TEMP, OverlayCardId.FAN)
        assertEquals(listOf(OverlayCardId.FAN, OverlayCardId.TEMP), result.settingsQuarterOrder)
        assertFalse(result.settingsRowsSwapped)
        assertFalse(result.settingsColsSwapped)
    }

    @Test
    fun `same row different column swaps settings columns`() {
        val result = default.applyDrop(OverlayCardId.FRAME_RATE, OverlayCardId.QUICK_TILES)
        assertTrue(result.settingsColsSwapped)
        assertFalse(result.settingsRowsSwapped)
        assertEquals(result, default.applyDrop(OverlayCardId.FAN, OverlayCardId.RINGS))
    }

    @Test
    fun `same column different row swaps settings rows`() {
        val result = default.applyDrop(OverlayCardId.FRAME_RATE, OverlayCardId.TEMP)
        assertTrue(result.settingsRowsSwapped)
        assertFalse(result.settingsColsSwapped)
        assertEquals(result, default.applyDrop(OverlayCardId.RINGS, OverlayCardId.QUICK_TILES))
    }

    @Test
    fun `diagonal drop swaps both rows and columns`() {
        val result = default.applyDrop(OverlayCardId.FRAME_RATE, OverlayCardId.RINGS)
        assertTrue(result.settingsRowsSwapped)
        assertTrue(result.settingsColsSwapped)
    }

    @Test
    fun `drop rules use stock cells so they work from an already swapped layout`() {
        val swapped = default.applyDrop(OverlayCardId.FRAME_RATE, OverlayCardId.QUICK_TILES)
        assertEquals(default, swapped.applyDrop(OverlayCardId.QUICK_TILES, OverlayCardId.FRAME_RATE))
    }

    @Test
    fun `invalid drops leave the layout untouched`() {
        assertSame(default, default.applyDrop(OverlayCardId.VOLUME, OverlayCardId.VOLUME))
        assertSame(default, default.applyDrop(OverlayCardId.VOLUME, OverlayCardId.RINGS))
        assertSame(default, default.applyDrop(OverlayCardId.NAV_BAR, OverlayCardId.MODE_BUTTONS))
    }

    @Test
    fun `nav bar position updates`() {
        val top = default.withNavBarPosition(NavBarPosition.TOP)
        assertEquals(NavBarPosition.TOP, top.navBarPosition)
        assertFalse(top.isDefault)
        assertTrue(top.withNavBarPosition(NavBarPosition.BOTTOM).isDefault)
    }

    @Test
    fun `encode and decode round-trip every field`() {
        val custom = OverlayLayout(
            modeRowsSwapped = true,
            modeHalfOrder = listOf(OverlayCardId.VOLUME, OverlayCardId.BRIGHTNESS),
            settingsRowsSwapped = true,
            settingsColsSwapped = true,
            settingsQuarterOrder = listOf(OverlayCardId.FAN, OverlayCardId.TEMP),
            navBarPosition = NavBarPosition.TOP
        )
        assertEquals(custom, OverlayLayout.decode(custom.encode()))
        assertEquals(default, OverlayLayout.decode(default.encode()))
    }

    @Test
    fun `decode repairs missing, unknown, or malformed values`() {
        assertEquals(default, OverlayLayout.decode(null))
        assertEquals(default, OverlayLayout.decode(""))
        assertEquals(default, OverlayLayout.decode("garbage;;=;nav=sideways;future_key=1"))
        // Orders that aren't an exact permutation of their cards fall back to the stock order.
        val partial = OverlayLayout.decode("mode_halves=volume;settings_quarters=fan,fan;mode_rows=1")
        assertEquals(OverlayLayout.DEFAULT_MODE_HALVES, partial.modeHalfOrder)
        assertEquals(OverlayLayout.DEFAULT_SETTINGS_QUARTERS, partial.settingsQuarterOrder)
        assertTrue(partial.modeRowsSwapped)
    }
}
