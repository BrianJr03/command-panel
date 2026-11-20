package jr.brian.commandpannel.system

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradientRulesTest {
    private val sunset = GradientSpec(
        id = "g1",
        name = "Sunset",
        stops = listOf(GradientStop(0xFFFF6A00.toInt(), 0f), GradientStop(0xFF8E2DE2.toInt(), 0.45f), GradientStop(0xFF00C9FF.toInt(), 1f)),
        style = GradientStyle.AURORA,
        angle = 135f,
        motion = GradientMotion.HUE_CYCLE,
        speed = 1.5f,
        links = mapOf("hue" to "battery"),
    )

    @Test
    fun codecRoundTrips() {
        assertEquals(sunset, GradientJson.decode(GradientJson.encode(sunset).toString()))
        val plain = sunset.copy(links = emptyMap(), style = GradientStyle.RADIAL, motion = GradientMotion.WAVE, speed = 0f)
        assertEquals(plain, GradientJson.decode(GradientJson.encode(plain)))
        val list = listOf(sunset, plain.copy(id = "g2"))
        assertEquals(list, GradientJson.decodeList(GradientJson.encodeList(list)))
    }

    @Test
    fun enumKeysAreStable() {
        assertEquals(listOf("linear", "radial", "aurora"), GradientStyle.entries.map { it.key })
        assertEquals(listOf("drift", "rotate", "breathe", "hue_cycle", "wave"), GradientMotion.entries.map { it.key })
        val o = GradientJson.encode(sunset)
        assertEquals("aurora", o.getString("style"))
        assertEquals("hue_cycle", o.getString("motion"))
    }

    @Test
    fun stopsAreClampedSortedOpaqueAndCounted() {
        val stops = GradientRules.normalizeStops(
            listOf(GradientStop(0x10FF0000, 1.5f), GradientStop(0x0000FF00, -0.2f), GradientStop(0x7F0000FF, 0.5f)),
        )!!
        assertEquals(listOf(0f, 0.5f, 1f), stops.map { it.position })
        assertEquals(listOf(0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFF0000.toInt()), stops.map { it.color })
        val eight = (0 until 8).map { GradientStop(0xFF000000.toInt() or it, 1f - it / 10f) }
        val six = GradientRules.normalizeStops(eight)!!
        assertEquals(GradientRules.MAX_STOPS, six.size)
        assertEquals((0 until 6).map { 0xFF000000.toInt() or it }.reversed(), six.map { it.color })
        assertNull(GradientRules.normalizeStops(listOf(GradientStop(0, 0f))))
        assertNull(GradientRules.normalizeStops(listOf(GradientStop(0, 0f), GradientStop(0, Float.NaN))))
    }

    @Test
    fun angleWrapsAndSpeedClamps() {
        assertEquals(0f, GradientRules.wrapAngle(360f))
        assertEquals(270f, GradientRules.wrapAngle(-90f))
        assertEquals(30f, GradientRules.wrapAngle(750f))
        assertEquals(0f, GradientRules.wrapAngle(Float.NaN))
        assertEquals(0f, GradientRules.wrapAngle(Float.POSITIVE_INFINITY))
        assertEquals(0f, GradientRules.clampSpeed(-1f))
        assertEquals(GradientRules.MAX_SPEED, GradientRules.clampSpeed(99f))
        assertEquals(GradientRules.DEFAULT_SPEED, GradientRules.clampSpeed(Float.NaN))
        val n = GradientRules.normalize(sunset.copy(angle = -45f, speed = 10f, name = "  ${"x".repeat(60)} "))!!
        assertEquals(315f, n.angle)
        assertEquals(GradientRules.MAX_SPEED, n.speed)
        assertEquals(GradientRules.MAX_NAME, n.name.length)
        assertNull(GradientRules.normalize(sunset.copy(id = " ")))
    }

    @Test
    fun motionNeedsTheSwitchAndASpeed() {
        assertTrue(GradientRules.moves(sunset, animationsOn = true))
        assertFalse(GradientRules.moves(sunset, animationsOn = false))
        assertFalse(GradientRules.moves(sunset.copy(speed = 0f), animationsOn = true))
    }

    @Test
    fun decodeRejectsBadOrNewerInputWithoutThrowing() {
        assertNull(GradientJson.decode(null as String?))
        assertNull(GradientJson.decode("not json"))
        assertNull(GradientJson.decode("[1,2]"))
        assertNull(GradientJson.decode("""{"format":"something-else","id":"a","stops":[]}"""))
        val newer = GradientJson.encode(sunset).put("version", GradientJson.VERSION + 1)
        assertNull(GradientJson.decode(newer.toString()))
        assertNull(GradientJson.decode(GradientJson.encode(sunset).put("id", "").toString()))
        assertNull(GradientJson.decode(GradientJson.encode(sunset).put("stops", "nope").toString()))
        val oneStop = JSONObject(GradientJson.encode(sunset).toString()).apply { getJSONArray("stops").apply { remove(2); remove(1) } }
        assertNull(GradientJson.decode(oneStop.toString()))
        // Unknown enum keys (from a hand-edited file) fall back to the defaults instead of dropping the gradient.
        val odd = GradientJson.encode(sunset).put("style", "plasma").put("motion", "spin").put("speed", "fast")
        val decoded = GradientJson.decode(odd.toString())!!
        assertEquals(GradientStyle.DEFAULT, decoded.style)
        assertEquals(GradientMotion.DEFAULT, decoded.motion)
        assertEquals(GradientRules.DEFAULT_SPEED, decoded.speed)
        // A broken entry in a list is skipped, the rest survive.
        val list = "[${GradientJson.encode(sunset)}, {\"id\":\"x\"}, 7]"
        assertEquals(listOf(sunset), GradientJson.decodeList(list))
        assertTrue(GradientJson.decodeList("{").isEmpty())
        // No format or version (an older hand-written file) still reads as version 1.
        assertNotNull(GradientJson.decode(GradientJson.encode(sunset).apply { remove("format"); remove("version") }))
    }
}
