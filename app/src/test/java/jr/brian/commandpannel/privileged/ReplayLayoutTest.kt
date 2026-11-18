package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.privileged.ScreenRecorderEngine.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Encoder frame sizes and screen placement for the Replay Buffer's capture targets, at the sizes the Thor reports:
 * top 1920x1080, bottom 1240x1080 (both landscape).
 */
class ReplayLayoutTest {

    private fun layout(target: ReplayTarget) = ScreenRecorderEngine.replayLayout(target, 1920, 1080, 1240, 1080)

    @Test
    fun topScreenScalesTo720p() {
        val l = layout(ReplayTarget.TOP)
        assertEquals(1280, l.width)
        assertEquals(720, l.height)
        assertEquals(Region(0, 0, 1280, 720), l.top)
        assertNull(l.bottom)
    }

    @Test
    fun bottomScreenKeepsItsSizeAlignedTo16() {
        val l = layout(ReplayTarget.BOTTOM)
        assertEquals(1248, l.width)
        assertEquals(1088, l.height)
        assertNull(l.top)
        assertEquals(Region(0, 0, 1248, 1088), l.bottom)
    }

    @Test
    fun bothScreensStackWithTheBottomCentered() {
        val l = layout(ReplayTarget.BOTH)
        assertEquals(1280, l.width)
        assertEquals(1440, l.height)
        assertEquals(Region(0, 0, 1280, 720), l.top)
        // Same scale as the top screen (2/3), centered horizontally, directly under it.
        assertEquals(Region(226, 720, 827, 720), l.bottom)
        assertTrue(l.bottom!!.top + l.bottom!!.height <= l.height)
    }

    @Test
    fun framesAreMultiplesOf16() {
        ReplayTarget.entries.forEach { t ->
            val l = layout(t)
            assertEquals(0, l.width % 16)
            assertEquals(0, l.height % 16)
        }
    }

    @Test
    fun unknownTargetKeyFallsBackToTop() {
        assertEquals(ReplayTarget.TOP, ReplayTarget.fromKey(null))
        assertEquals(ReplayTarget.TOP, ReplayTarget.fromKey("sideways"))
        assertEquals(ReplayTarget.BOTH, ReplayTarget.fromKey("both"))
    }
}
