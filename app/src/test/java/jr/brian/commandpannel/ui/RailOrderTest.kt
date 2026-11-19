package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.settings.SettingsCategory
import jr.brian.commandpannel.ui.settings.SettingsCategory.APPEARANCE
import jr.brian.commandpannel.ui.settings.SettingsCategory.DIAGNOSTICS
import jr.brian.commandpannel.ui.settings.SettingsCategory.GENERAL
import jr.brian.commandpannel.ui.settings.SettingsCategory.NAV_BAR
import jr.brian.commandpannel.ui.settings.SettingsCategory.QUICK_TOGGLES
import jr.brian.commandpannel.ui.settings.SettingsCategory.REPLAY
import jr.brian.commandpannel.ui.settings.SettingsCategory.THEMES
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers reading the companion rail's saved section order. */
class RailOrderTest {

    @Test
    fun `nothing saved is the default order, with Replay after Themes`() {
        assertEquals(SettingsCategory.entries, SettingsCategory.ordered(null))
        assertEquals(listOf(GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, THEMES, REPLAY, DIAGNOSTICS), SettingsCategory.ordered(""))
    }

    @Test
    fun `a full saved order round-trips`() {
        val order = listOf(REPLAY, THEMES, GENERAL, DIAGNOSTICS, NAV_BAR, APPEARANCE, QUICK_TOGGLES)
        assertEquals(order, SettingsCategory.ordered(order.joinToString(",") { it.key }))
    }

    @Test
    fun `a section missing from an older save goes after the one before it by default`() {
        // Saved before Replay existed, with Themes moved to the top.
        assertEquals(
            listOf(THEMES, REPLAY, GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, DIAGNOSTICS),
            SettingsCategory.ordered("themes,general,nav_bar,tiles,appearance,diagnostics"),
        )
    }

    @Test
    fun `unknown keys and repeats are dropped`() {
        assertEquals(
            listOf(DIAGNOSTICS, GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, THEMES, REPLAY),
            SettingsCategory.ordered("diagnostics, bogus,diagnostics,general"),
        )
    }
}
