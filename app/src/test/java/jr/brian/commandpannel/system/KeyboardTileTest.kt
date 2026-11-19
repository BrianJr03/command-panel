package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.KeyboardTile.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTileTest {

    private val release = "jr.brian.thorkbm"
    private val debug = "jr.brian.thorkbm.debug"
    private val service = "jr.brian.thorkbm.feature.panel.plugin.PanelPluginService"

    @Test
    fun `a listed ThorKBM plugin swaps in`() {
        assertEquals(Action.Swap("$debug/$service"), KeyboardTile.decide(listOf("$debug/$service"), setOf(release, debug)))
    }

    @Test
    fun `the release ThorKBM plugin wins over the debug one`() {
        assertEquals(
            Action.Swap("$release/$service"),
            KeyboardTile.decide(listOf("$debug/$service", "$release/$service"), setOf(release, debug)),
        )
    }

    @Test
    fun `other plugins don't count`() {
        assertEquals(Action.OpenApp(release), KeyboardTile.decide(listOf("com.example.fake/.Plugin"), setOf(release)))
    }

    @Test
    fun `ThorKBM without plugin mode opens the app, release first`() {
        assertEquals(Action.OpenApp(release), KeyboardTile.decide(emptyList(), setOf(release, debug)))
        assertEquals(Action.OpenApp(debug), KeyboardTile.decide(emptyList(), setOf(debug)))
    }

    @Test
    fun `no ThorKBM opens the companion's Keyboard section`() {
        assertEquals(Action.OpenCompanion, KeyboardTile.decide(emptyList(), emptySet()))
        assertEquals(Action.OpenCompanion, KeyboardTile.decide(listOf("com.example.fake/.Plugin"), setOf("com.example.other")))
    }
}
