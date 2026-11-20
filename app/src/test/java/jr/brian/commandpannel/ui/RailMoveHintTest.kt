package jr.brian.commandpannel.ui

import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.settings.RailGroup
import jr.brian.commandpannel.ui.settings.RailMoveHint
import jr.brian.commandpannel.ui.settings.SettingsCategory
import jr.brian.commandpannel.ui.settings.railMoveHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Covers which hint the companion rail shows at its bottom while a group or section is picked up. */
class RailMoveHintTest {

    @Test
    fun `nothing lifted shows no hint`() {
        assertNull(railMoveHint(grabbed = null, dragged = null))
    }

    @Test
    fun `a section picked up with the gamepad names it and shows the gamepad controls`() {
        assertEquals(
            RailMoveHint(SettingsCategory.REPLAY.titleRes, R.string.settings_rail_move_hint_gamepad),
            railMoveHint(grabbed = SettingsCategory.REPLAY.key, dragged = null),
        )
    }

    @Test
    fun `a group picked up with the gamepad names the group`() {
        assertEquals(
            RailMoveHint(RailGroup.EXTRAS.titleRes, R.string.settings_rail_move_hint_gamepad),
            railMoveHint(grabbed = RailGroup.EXTRAS.key, dragged = null),
        )
    }

    @Test
    fun `a touch drag says release to drop`() {
        assertEquals(
            RailMoveHint(SettingsCategory.THEMES.titleRes, R.string.settings_rail_move_hint_touch),
            railMoveHint(grabbed = null, dragged = SettingsCategory.THEMES.key),
        )
        assertEquals(
            RailMoveHint(RailGroup.LOOK.titleRes, R.string.settings_rail_move_hint_touch),
            railMoveHint(grabbed = null, dragged = RailGroup.LOOK.key),
        )
    }

    @Test
    fun `a touch drag wins over a gamepad pick-up`() {
        assertEquals(
            RailMoveHint(SettingsCategory.KEYBOARD.titleRes, R.string.settings_rail_move_hint_touch),
            railMoveHint(grabbed = SettingsCategory.REPLAY.key, dragged = SettingsCategory.KEYBOARD.key),
        )
    }

    @Test
    fun `an unknown key shows no hint`() {
        assertNull(railMoveHint(grabbed = "nope", dragged = null))
    }

    @Test
    fun `group and section keys never collide`() {
        val groups = RailGroup.entries.map { it.key }.toSet()
        SettingsCategory.entries.forEach { assert(it.key !in groups) { it.key } }
    }
}
