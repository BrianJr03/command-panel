package jr.brian.commandpannel.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginNavPrefsTest {

    private val kbm = "jr.brian.thorkbm/jr.brian.thorkbm.feature.panel.plugin.PanelPluginService"
    private val kbmDebug = "jr.brian.thorkbm.debug/jr.brian.thorkbm.feature.panel.plugin.PanelPluginService"
    private val fake = "jr.brian.commandpanel.fakeplugin/jr.brian.commandpanel.fakeplugin.FakePluginService"

    @Test
    fun `every plugin shows by default`() {
        val hidden = PluginNavPrefs.parse(null)
        assertTrue(hidden.isEmpty())
        assertTrue(PluginNavPrefs.shown(listOf(kbm), hidden))
        assertEquals(listOf(kbm, fake), PluginNavPrefs.visible(listOf(kbm, fake), hidden) { it })
    }

    @Test
    fun `hiding ThorKBM keeps the other plugins and round-trips`() {
        val hidden = PluginNavPrefs.setShown(emptySet(), listOf(kbm, kbmDebug), shown = false)
        assertFalse(PluginNavPrefs.shown(listOf(kbm, kbmDebug), hidden))
        assertEquals(listOf(fake), PluginNavPrefs.visible(listOf(kbm, fake, kbmDebug), hidden) { it })
        assertEquals(hidden, PluginNavPrefs.parse(PluginNavPrefs.encode(hidden)))
    }

    @Test
    fun `showing again removes only those keys`() {
        val hidden = setOf(kbm, kbmDebug, fake)
        assertEquals(setOf(fake), PluginNavPrefs.setShown(hidden, listOf(kbm, kbmDebug), shown = true))
    }

    @Test
    fun `one hidden ThorKBM build means the switch reads off`() {
        assertFalse(PluginNavPrefs.shown(listOf(kbm, kbmDebug), setOf(kbmDebug)))
    }

    @Test
    fun `blank entries in the saved csv are ignored`() {
        assertEquals(setOf(kbm), PluginNavPrefs.parse(" ,$kbm,, "))
    }
}
