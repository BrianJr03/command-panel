package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompanionRoutesTest {

    @Test
    fun `the Replay Studio screen extra opens Replay Studio`() {
        assertEquals("replay_studio", MainActivity.SCREEN_REPLAY_STUDIO)
        assertEquals(CompanionRoutes.REPLAY_STUDIO, CompanionRoutes.forScreen(MainActivity.SCREEN_REPLAY_STUDIO))
    }

    @Test
    fun `screens that need arguments or unknown values open nothing`() {
        assertNull(CompanionRoutes.forScreen(null))
        assertNull(CompanionRoutes.forScreen(""))
        assertNull(CompanionRoutes.forScreen("studio"))
        assertNull(CompanionRoutes.forScreen("themes"))
        assertNull(CompanionRoutes.forScreen("settings"))
        assertNull(CompanionRoutes.forScreen("Replay_Studio"))
    }
}
