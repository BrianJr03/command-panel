package jr.brian.commandpannel.privileged

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the pure colour/format logic of the ported Thor RGB controller. */
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
}
