package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.system.GradientMotion
import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientSpec
import jr.brian.commandpannel.system.GradientStop
import jr.brian.commandpannel.system.GradientStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientLibraryTest {
    private fun spec(id: String, name: String = id) = GradientSpec(
        id = id,
        name = name,
        stops = listOf(GradientStop(0xFFFF0000.toInt(), 0f), GradientStop(0xFF0000FF.toInt(), 1f)),
        style = GradientStyle.RADIAL,
        angle = 45f,
        motion = GradientMotion.WAVE,
        speed = 2f,
        links = mapOf("hue" to "battery"),
    )

    private val copy: (String) -> String = { "$it copy" }
    private val copyN: (String, Int) -> String = { n, i -> "$n copy $i" }

    private val a = spec("a", "Sunset")
    private val b = spec("b", "Ocean")

    @Test
    fun `showing is the gradient only while the active id is saved`() {
        assertEquals(GradientLibrary.Showing.GRADIENT, GradientLibrary.showing("a", listOf(a, b), hasImage = true))
        assertEquals(GradientLibrary.Showing.GRADIENT, GradientLibrary.showing("b", listOf(a, b), hasImage = false))
        // A deleted gradient falls back like BackgroundSetting.resolve: the image, else the backdrop.
        assertEquals(GradientLibrary.Showing.IMAGE, GradientLibrary.showing("gone", listOf(a), hasImage = true))
        assertEquals(GradientLibrary.Showing.BACKDROP, GradientLibrary.showing("gone", listOf(a), hasImage = false))
        assertEquals(GradientLibrary.Showing.IMAGE, GradientLibrary.showing(null, listOf(a), hasImage = true))
        assertEquals(GradientLibrary.Showing.BACKDROP, GradientLibrary.showing(null, emptyList(), hasImage = false))
    }

    @Test
    fun `use my image is offered only while a gradient shows and an image is stored`() {
        assertTrue(GradientLibrary.canUseImage("a", listOf(a, b), hasImage = true))
        assertFalse(GradientLibrary.canUseImage("a", listOf(a, b), hasImage = false))
        assertFalse(GradientLibrary.canUseImage(null, listOf(a, b), hasImage = true))
        assertFalse(GradientLibrary.canUseImage("gone", listOf(a, b), hasImage = true))
    }

    @Test
    fun `duplicate keeps everything but the id and the name`() {
        val d = GradientLibrary.duplicate(a, listOf(a, b), "new", "Untitled", copy, copyN)
        assertEquals("new", d.id)
        assertEquals("Sunset copy", d.name)
        assertEquals(a.copy(id = "new", name = "Sunset copy"), d)
    }

    @Test
    fun `duplicate numbers copies past names already taken, ignoring case`() {
        val taken = listOf(a, spec("c", "sunset COPY"), spec("d", "Sunset copy 2"))
        assertEquals("Sunset copy 3", GradientLibrary.duplicate(a, taken, "new", "Untitled", copy, copyN).name)
    }

    @Test
    fun `duplicate of a blank name uses the fallback`() {
        val blank = spec("e", "  ")
        assertEquals("Untitled copy", GradientLibrary.duplicate(blank, listOf(blank), "new", "Untitled", copy, copyN).name)
    }

    @Test
    fun `duplicate shortens a long name so the copy fits the limit with its suffix`() {
        val long = spec("l", "x".repeat(GradientRules.MAX_NAME))
        val d = GradientLibrary.duplicate(long, listOf(long), "new", "Untitled", copy, copyN)
        assertTrue(d.name.length <= GradientRules.MAX_NAME)
        assertTrue(d.name.endsWith(" copy"))
        // And the next copy is still a different name.
        val d2 = GradientLibrary.duplicate(long, listOf(long, d), "new2", "Untitled", copy, copyN)
        assertTrue(d2.name.length <= GradientRules.MAX_NAME)
        assertTrue(d2.name != d.name)
        assertTrue(d2.name.contains(" copy"))
    }

    @Test
    fun `duplicate survives normalize unchanged`() {
        val d = GradientLibrary.duplicate(a, listOf(a), "new", "Untitled", copy, copyN)
        assertEquals(d, GradientRules.normalize(d))
    }

    @Test
    fun `focus after delete goes to the next tile, else the previous, else none`() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", GradientLibrary.focusAfterDelete(ids, "a"))
        assertEquals("c", GradientLibrary.focusAfterDelete(ids, "b"))
        assertEquals("b", GradientLibrary.focusAfterDelete(ids, "c"))
        assertNull(GradientLibrary.focusAfterDelete(listOf("a"), "a"))
        assertNull(GradientLibrary.focusAfterDelete(ids, "missing"))
    }

    @Test
    fun `every style and motion has a display label`() {
        assertEquals(GradientStyle.entries.size, GradientStyle.entries.map { it.labelRes }.toSet().size)
        assertEquals(GradientMotion.entries.size, GradientMotion.entries.map { it.labelRes }.toSet().size)
    }

    @Test
    fun `the gradient library has its own route`() {
        assertEquals("gradients", CompanionRoutes.GRADIENTS)
        assertNull(CompanionRoutes.forScreen(CompanionRoutes.GRADIENTS))
    }
}
