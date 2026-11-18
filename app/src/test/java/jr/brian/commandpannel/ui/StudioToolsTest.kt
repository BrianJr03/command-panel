package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.settings.Hsv
import jr.brian.commandpannel.ui.settings.StudioHistory
import jr.brian.commandpannel.ui.settings.argbToHsv
import jr.brian.commandpannel.ui.settings.formatHexColor
import jr.brian.commandpannel.ui.settings.hsvToArgb
import jr.brian.commandpannel.ui.settings.parseHexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class StudioToolsTest {
    @Test
    fun `hsv round trips`() {
        listOf(0xFFFF0000.toInt(), 0xFF22B8F0.toInt(), 0xFF7C6FFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()).forEach {
            assertEquals(formatHexColor(it), formatHexColor(hsvToArgb(argbToHsv(it))))
        }
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(Hsv(120f, 1f, 1f)))
    }

    @Test
    fun `hex parses and rejects junk`() {
        assertEquals(0xFF22B8F0.toInt(), parseHexColor("#22b8f0"))
        assertEquals(0xFF22B8F0.toInt(), parseHexColor(" 22B8F0 "))
        assertNull(parseHexColor("#12345"))
        assertNull(parseHexColor("zzzzzz"))
    }

    @Test
    fun `history coalesces quick changes and redo clears on a new change`() {
        val h = StudioHistory<Int>(coalesceMs = 600)
        h.record(0, 1_000); h.record(1, 1_100); h.record(2, 1_200) // one held stepper: 0 → 3
        h.record(3, 5_000) // a separate change: 3 → 4
        assertEquals(3, h.undo(4))
        assertEquals(0, h.undo(3))
        assertFalse(h.canUndo)
        assertEquals(3, h.redo(0))
        h.record(3, 9_000)
        assertFalse(h.canRedo)
    }
}
