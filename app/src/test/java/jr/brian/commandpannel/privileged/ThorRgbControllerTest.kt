package jr.brian.commandpannel.privileged

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers the pure color/format logic of the ported Thor RGB controller. */
class ThorRgbControllerTest {

    @Test
    fun pickerColorForcesOpaqueArgb() {
        assertEquals("#FF8E84FF,#FF8E84FF", ThorRgbController.formatPickerColor(0x008E84FF, 0x008E84FF))
    }

    @Test
    fun accentPerThemeMode() {
        // light palette accent vs the pure-black/dark accent
        assertEquals(0xFF8E84FF.toInt(), ThorRgbController.accentForThemeMode(3))
        assertEquals(0xFF6A0D83.toInt(), ThorRgbController.accentForThemeMode(4))
        assertEquals(0xFF6A0D83.toInt(), ThorRgbController.accentForThemeMode(1))
    }

    @Test
    fun sticksOffWritesBlackAtTheSavedBrightness() {
        val saved = ThorRgbController.Snapshot(0xFF8E84FF.toInt(), 0xFF112233.toInt(), 60)
        assertEquals(ThorRgbController.Snapshot(0, 0, 60), ThorRgbController.sticksSnapshot(saved, lit = false))
    }

    @Test
    fun sticksOnRestoresTheSavedColor() {
        val saved = ThorRgbController.Snapshot(0xFF8E84FF.toInt(), 0xFF112233.toInt(), 60)
        assertEquals(saved, ThorRgbController.sticksSnapshot(saved, lit = true))
    }

    @Test
    fun sticksOnWithNoSavedColorWritesNothing() {
        assertNull(ThorRgbController.sticksSnapshot(ThorRgbController.Snapshot(0, 0, 50), lit = true))
        assertNull(ThorRgbController.sticksSnapshot(ThorRgbController.Snapshot(0xFF000000.toInt(), 0, 50), lit = true))
    }
}
