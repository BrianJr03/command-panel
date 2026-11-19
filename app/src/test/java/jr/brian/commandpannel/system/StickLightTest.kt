package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.SystemSettings.StickLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickLightTest {

    @Test
    fun onWhenEitherStickIsOn() {
        assertTrue(StickLight.isOn("1,1"))
        assertTrue(StickLight.isOn("1,0"))
        assertTrue(StickLight.isOn("0, 1"))
        assertTrue(StickLight.isOn("true,false"))
    }

    @Test
    fun offWhenBothAreOffOrUnset() {
        assertFalse(StickLight.isOn("0,0"))
        assertFalse(StickLight.isOn(""))
        assertFalse(StickLight.isOn(null))
        assertFalse(StickLight.isOn("null"))
    }

    @Test
    fun writesBothSticksTogether() {
        assertEquals("1,1", StickLight.valueFor(true))
        assertEquals("0,0", StickLight.valueFor(false))
        assertTrue(StickLight.isOn(StickLight.valueFor(true)))
        assertFalse(StickLight.isOn(StickLight.valueFor(false)))
    }

    @Test
    fun stickLedTileIsAnOptInToggle() {
        val tile = QuickSettingTileId.fromKey("stick_led")!!
        assertEquals(QuickSettingTileId.STICK_LED, tile)
        assertEquals(TileInteraction.TOGGLE, tile.interaction)
        assertFalse(tile.defaultEnabled)
        assertFalse("stick_led" in QuickSettingTileId.enabledKeys(null))
    }
}
