package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.overlay.OverlayDialogKind
import jr.brian.commandpannel.ui.overlay.dialogForTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayDialogsTest {

    @Test
    fun modalTilesMapToTheirDialogs() {
        assertEquals(OverlayDialogKind.FOCUS, dialogForTile("focus_lock"))
        assertEquals(OverlayDialogKind.PERFORMANCE, dialogForTile("performance"))
        assertEquals(OverlayDialogKind.FAN, dialogForTile("fan"))
        assertEquals(OverlayDialogKind.CONTROLLER, dialogForTile("controller"))
        assertEquals(OverlayDialogKind.THEME, dialogForTile("theme"))
    }

    @Test
    fun instantTilesHaveNoDialog() {
        assertNull(dialogForTile("vibration"))
        assertNull(dialogForTile("clear_memory"))
        assertNull(dialogForTile("ambient_led"))
    }
}
