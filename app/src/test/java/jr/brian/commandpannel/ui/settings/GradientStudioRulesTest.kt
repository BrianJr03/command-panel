package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.system.GradientPresets
import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GradientStudioRulesTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val two = listOf(GradientStop(red, 0f), GradientStop(blue, 1f))

    private fun stops(n: Int) = (0 until n).map { GradientStop(red, it / (n - 1f)) }

    // ---- stops ----

    @Test
    fun `add puts a mixed stop halfway after the selected one`() {
        val (next, i) = GradientStudioRules.addStop(two, 0)!!
        assertEquals(3, next.size)
        assertEquals(1, i)
        assertEquals(0.5f, next[1].position, 1e-4f)
        assertEquals(GradientPresets.mix(red, blue, 0.5f), next[1].color)
    }

    @Test
    fun `add after the last stop goes halfway back toward the one before`() {
        val (next, i) = GradientStudioRules.addStop(two, 1)!!
        assertEquals(1, i)
        assertEquals(0.5f, next[i].position, 1e-4f)
        assertEquals(listOf(0f, 0.5f, 1f), next.map { it.position })
    }

    @Test
    fun `add stops at six and remove stops at two`() {
        assertNull(GradientStudioRules.addStop(stops(GradientRules.MAX_STOPS), 0))
        assertNotNull(GradientStudioRules.addStop(stops(GradientRules.MAX_STOPS - 1), 0))
        assertNull(GradientStudioRules.removeStop(two, 0))
        val (next, sel) = GradientStudioRules.removeStop(stops(3), 2)!!
        assertEquals(2, next.size)
        assertEquals(1, sel)
        assertNull(GradientStudioRules.removeStop(stops(3), 5))
    }

    @Test
    fun `move clamps and snaps the position and keeps the stops sorted`() {
        val three = listOf(GradientStop(red, 0f), GradientStop(0xFF00FF00.toInt(), 0.5f), GradientStop(blue, 1f))
        val (a, ia) = GradientStudioRules.moveStop(three, 1, 1.7f)
        assertEquals(1f, a[ia].position, 1e-4f)
        assertEquals(0xFF00FF00.toInt(), a[ia].color)
        assertEquals(a.sortedBy { it.position }, a)
        val (b, ib) = GradientStudioRules.moveStop(three, 1, -0.2f)
        assertEquals(0f, b[ib].position, 1e-4f)
        assertEquals(0xFF00FF00.toInt(), b[ib].color)
        val (c, ic) = GradientStudioRules.moveStop(three, 0, 0.73f)
        assertEquals(0.75f, c[ic].position, 1e-4f)
        assertEquals(red, c[ic].color)
        assertEquals(1, ic)
        assertEquals(c.sortedBy { it.position }, c)
    }

    @Test
    fun `moving a stop onto another keeps the moved one picked`() {
        val (next, i) = GradientStudioRules.moveStop(two, 0, 1f)
        assertEquals(red, next[i].color)
        val (back, j) = GradientStudioRules.moveStop(two, 1, 0f)
        assertEquals(blue, back[j].color)
    }

    @Test
    fun `stop color is made opaque`() {
        val next = GradientStudioRules.setStopColor(two, 1, 0x00123456)
        assertEquals(0xFF123456.toInt(), next[1].color)
        assertEquals(red, next[0].color)
    }

    // ---- speed and angle ----

    @Test
    fun `speed steps run from still to the limit in order`() {
        val speeds = GradientStudioRules.SPEED_STEPS.map { it.speed }
        assertEquals(0f, speeds.first())
        assertEquals(GradientRules.MAX_SPEED, speeds.last())
        assertEquals(speeds.sorted(), speeds)
        assertEquals(speeds.size, GradientStudioRules.SPEED_STEPS.map { it.labelRes }.toSet().size)
    }

    @Test
    fun `speed steps up and down, from between steps to the nearest that way, and stays at the ends`() {
        assertEquals(0.25f, GradientStudioRules.stepSpeed(0f, 1))
        assertEquals(0f, GradientStudioRules.stepSpeed(0f, -1))
        assertEquals(4f, GradientStudioRules.stepSpeed(4f, 1))
        assertEquals(1.5f, GradientStudioRules.stepSpeed(1.1f, 1))
        assertEquals(1f, GradientStudioRules.stepSpeed(1.1f, -1))
        assertEquals(4, GradientStudioRules.speedIndex(1.1f))
    }

    @Test
    fun `angle steps wrap around`() {
        assertEquals(345f, GradientStudioRules.stepAngle(0f, -15f))
        assertEquals(0f, GradientStudioRules.stepAngle(345f, 15f))
        assertEquals(46f, GradientStudioRules.stepAngle(45f, 1f))
    }

    // ---- palettes and readability ----

    @Test
    fun `shuffle makes valid, evenly spaced, readable palettes`() {
        val random = Random(7)
        repeat(200) {
            val stops = GradientStudioRules.shuffle(random)
            assertTrue(stops.size in 3..4)
            assertEquals(0f, stops.first().position)
            assertEquals(1f, stops.last().position)
            assertNotNull(GradientRules.normalizeStops(stops))
            assertTrue(stops.all { (it.color ushr 24) == 0xFF })
            assertTrue("unreadable: $stops", GradientStudioRules.isReadable(stops))
        }
    }

    @Test
    fun `shuffle is repeatable with the same seed and varies otherwise`() {
        assertEquals(GradientStudioRules.shuffle(Random(3)), GradientStudioRules.shuffle(Random(3)))
        val many = (0 until 20).map { GradientStudioRules.shuffle(Random(it)) }.toSet()
        assertTrue(many.size > 15)
    }

    @Test
    fun `contrast matches the WCAG extremes`() {
        assertEquals(21f, GradientStudioRules.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01f)
        assertEquals(1f, GradientStudioRules.contrast(red, red), 0.001f)
    }

    @Test
    fun `readability looks at the brightest stop`() {
        assertTrue(GradientStudioRules.isReadable(listOf(GradientStop(0xFF101020.toInt(), 0f), GradientStop(0xFF203040.toInt(), 1f))))
        // Yellow and white are too bright for light labels, even next to a dark stop.
        assertFalse(GradientStudioRules.isReadable(listOf(GradientStop(0xFF000000.toInt(), 0f), GradientStop(0xFFFFFF00.toInt(), 1f))))
        assertEquals(0xFFFFFF00.toInt(), GradientStudioRules.brightest(listOf(GradientStop(blue, 0f), GradientStop(0xFFFFFF00.toInt(), 1f))))
    }

    @Test
    fun `darken to readable keeps the hue and reaches the bar`() {
        val c = GradientStudioRules.darkenToReadable(0xFFFFD54F.toInt())
        assertTrue(GradientStudioRules.contrast(0xFFFFFFFF.toInt(), c) >= GradientStudioRules.SHUFFLE_TEXT_CONTRAST)
        assertEquals(argbToHsv(0xFFFFD54F.toInt()).h, argbToHsv(c).h, 3f)
    }

    @Test
    fun `the fixed presets pass the readability check`() {
        GradientPresets.all.filterNot { it.fromLook }.forEach { p ->
            assertTrue(p.key, GradientStudioRules.isReadable(p.spec("", 0, 0).stops))
        }
    }

    // ---- new, presets, from my look ----

    @Test
    fun `a new gradient is valid as is`() {
        val g = GradientStudioRules.newGradient("id", "My gradient 1")
        assertEquals(g, GradientRules.normalize(g))
        assertTrue(GradientStudioRules.isReadable(g.stops))
    }

    @Test
    fun `apply preset keeps the id and name`() {
        val g = GradientStudioRules.newGradient("mine", "Mine")
        val preset = GradientPresets.all.last().spec("Midnight", 0, 0)
        val next = GradientStudioRules.applyPreset(g, preset)
        assertEquals("mine", next.id)
        assertEquals("Mine", next.name)
        assertEquals(preset.stops, next.stops)
        assertEquals(preset.style, next.style)
        assertEquals(preset.motion, next.motion)
        assertEquals(preset.speed, next.speed)
        assertEquals(preset.angle, next.angle)
    }

    @Test
    fun `from my look takes the look's colors and keeps the style and motion`() {
        val g = GradientStudioRules.newGradient("mine", "Mine")
        val accent = 0xFF8E84FF.toInt()
        val bg = 0xFF0B0D12.toInt()
        val next = GradientStudioRules.fromLook(g, accent, bg)
        assertEquals(g.style, next.style)
        assertEquals(g.motion, next.motion)
        assertEquals(bg, next.stops.first().color)
        assertTrue(next.stops.size >= GradientRules.MIN_STOPS)
    }

    // ---- history (StudioHistory over gradients) ----

    @Test
    fun `history undoes and redoes gradient edits`() {
        val h = StudioHistory<List<GradientStop>>(coalesceMs = 0)
        val a = two
        val b = GradientStudioRules.addStop(a, 0)!!.first
        val c = GradientStudioRules.setStopColor(b, 1, 0xFF00FF00.toInt())
        h.record(a, 0); h.record(b, 10)
        assertEquals(b, h.undo(c))
        assertEquals(a, h.undo(b))
        assertNull(h.undo(a))
        assertEquals(b, h.redo(a))
        assertEquals(c, h.redo(b))
        assertFalse(h.canRedo)
    }
}
