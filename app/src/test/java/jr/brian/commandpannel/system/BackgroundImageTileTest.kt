package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImageTileTest {
    private val gif = PanelBackground.Image(BackgroundImage("cat-1.gif", animated = true))
    private val gradient = PanelBackground.Gradient(
        GradientSpec("g1", "Aurora", listOf(GradientStop(0xFF00FF88.toInt(), 0f), GradientStop(0xFF2200FF.toInt(), 1f))),
    )

    @Test
    fun `available whenever an image is set, whatever the look`() {
        // The image is app-wide (#61), so the tile no longer depends on the look being a custom one.
        assertTrue(BackgroundImageTile.available(gif))
        assertTrue(BackgroundImageTile.available(PanelBackground.Image(BackgroundImage("still.png"))))
        assertFalse(BackgroundImageTile.available(null))
    }

    @Test
    fun `lit only when available and on`() {
        assertTrue(BackgroundImageTile.active(gif, on = true))
        assertFalse(BackgroundImageTile.active(gif, on = false))
        assertFalse(BackgroundImageTile.active(null, on = true))
    }

    @Test
    fun `the image shows unless the tile is off or the look blurs behind`() {
        assertEquals(gif, BackgroundImageTile.shown(gif, on = true, blurBehind = false))
        assertNull(BackgroundImageTile.shown(gif, on = false, blurBehind = false))
        assertNull(BackgroundImageTile.shown(gif, on = true, blurBehind = true))
        assertNull(BackgroundImageTile.shown(null, on = true, blurBehind = false))
    }

    @Test
    fun `the tile is a Tools toggle, off by default, at the end of a saved order`() {
        val id = QuickSettingTileId.fromKey("background_image")!!
        assertEquals(QuickSettingTileId.BACKGROUND_IMAGE, id)
        assertEquals(TileCategory.TOOLS, id.category)
        assertEquals(TileInteraction.TOGGLE, id.interaction)
        assertTrue("background_image" !in QuickSettingTileId.enabledKeys(null))
        val savedOrder = QuickSettingTileId.entries.filter { it != id }.joinToString(",") { it.key }
        assertEquals("background_image", QuickSettingTileId.orderedKeys(savedOrder).last())
    }

    @Test
    fun `a gradient source works the tile like an image, and Aurora Blur hides it too`() {
        assertTrue(BackgroundImageTile.available(gradient))
        assertTrue(BackgroundImageTile.active(gradient, on = true))
        assertFalse(BackgroundImageTile.active(gradient, on = false))
        assertEquals(gradient, BackgroundImageTile.shown(gradient, on = true, blurBehind = false))
        assertNull(BackgroundImageTile.shown(gradient, on = false, blurBehind = false))
        assertNull(BackgroundImageTile.shown(gradient, on = true, blurBehind = true))
    }
}
