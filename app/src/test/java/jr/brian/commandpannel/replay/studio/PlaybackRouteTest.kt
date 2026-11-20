package jr.brian.commandpannel.replay.studio

import jr.brian.commandpannel.privileged.ReplayTarget
import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import jr.brian.commandpannel.privileged.ScreenRecorderEngine.Region
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a clip plays (#47), and how a Both clip is cut back into its two screens. */
class PlaybackRouteTest {

    // ---- routePlayback -------------------------------------------------------------------------------------------

    @Test fun settingOffPlaysEverythingOnTop() {
        ClipOrigin.entries.forEach { origin ->
            assertEquals(PlaybackRoute.TOP, routePlayback(origin, playOnOrigin = false, lowerAvailable = true))
        }
    }

    @Test fun lowerUnavailablePlaysOnTop() {
        ClipOrigin.entries.forEach { origin ->
            assertEquals(PlaybackRoute.TOP, routePlayback(origin, playOnOrigin = true, lowerAvailable = false))
        }
    }

    @Test fun settingOnPlaysOnTheSourceScreen() {
        assertEquals(PlaybackRoute.TOP, routePlayback(ClipOrigin.TOP, playOnOrigin = true, lowerAvailable = true))
        assertEquals(PlaybackRoute.LOWER, routePlayback(ClipOrigin.BOTTOM, playOnOrigin = true, lowerAvailable = true))
        assertEquals(PlaybackRoute.SPLIT, routePlayback(ClipOrigin.BOTH, playOnOrigin = true, lowerAvailable = true))
    }

    @Test fun anyBlockerMakesTheLowerScreenUnavailable() {
        assertTrue(LowerScreenBlockers().available)
        listOf(
            LowerScreenBlockers(missing = true),
            LowerScreenBlockers(off = true),
            LowerScreenBlockers(blanked = true),
            LowerScreenBlockers(pluginSession = true),
            LowerScreenBlockers(keyboardApp = true),
        ).forEach { assertFalse(it.toString(), it.available) }
    }

    // ---- bothCropRegions -----------------------------------------------------------------------------------------

    private val thor = intArrayOf(1920, 1080, 1240, 1080)
    private fun crop(w: Int, h: Int, sizes: IntArray = thor) = bothCropRegions(w, h, sizes[0], sizes[1], sizes[2], sizes[3])

    @Test fun cropMatchesTheReplayBufferLayout() {
        val layout = ScreenRecorderEngine.replayLayout(ReplayTarget.BOTH, 1920, 1080, 1240, 1080)
        val (top, bottom) = crop(layout.width, layout.height)!!
        assertEquals(layout.top, top)
        assertEquals(layout.bottom, bottom)
        // The Thor's two screens: 1280 x 1440 stacked, the lower one centered under the top.
        assertEquals(Region(0, 0, 1280, 720), top)
        assertEquals(Region(226, 720, 827, 720), bottom)
    }

    @Test fun sameShapeAtAnotherSizeScales() {
        val (top, bottom) = crop(640, 720)!!
        assertEquals(Region(0, 0, 640, 360), top)
        assertEquals(Region(113, 360, 414, 360), bottom)
    }

    @Test fun aFrameOfAnotherShapeIsNotSplit() {
        // A single-screen frame, or a Both clip recorded with other screen sizes.
        assertNull(crop(1280, 720))
        assertNull(crop(1280, 1440, intArrayOf(1920, 1200, 1240, 1080)))
        assertNull(crop(0, 0))
    }

    // ---- regionTexCoords / fitViewport ---------------------------------------------------------------------------

    @Test fun texCoordsFlipToBottomUp() {
        // The top half of a 1280 x 1440 frame is the upper half of the texture (t from 0.5 to 1).
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1f), regionTexCoords(Region(0, 0, 1280, 720), 1280, 1440), 1e-6f)
        val lower = regionTexCoords(Region(226, 720, 827, 720), 1280, 1440)
        assertArrayEquals(floatArrayOf(226f / 1280, 0f, 1053f / 1280, 0.5f), lower, 1e-6f)
    }

    @Test fun fitViewportLetterboxes() {
        // The top region fills the top screen exactly.
        assertArrayEquals(intArrayOf(0, 0, 1920, 1080), fitViewport(1280, 720, 1920, 1080))
        // The lower region fits the lower screen with bars on the sides.
        assertArrayEquals(intArrayOf(0, 0, 1240, 1080), fitViewport(827, 720, 1240, 1080))
        // A tall source in a wide surface: centered with side bars.
        assertArrayEquals(intArrayOf(660, 0, 600, 1080), fitViewport(600, 1080, 1920, 1080))
        // Nothing to fit: the whole surface.
        assertArrayEquals(intArrayOf(0, 0, 100, 50), fitViewport(0, 0, 100, 50))
    }
}
