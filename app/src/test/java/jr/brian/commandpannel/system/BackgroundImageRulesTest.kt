package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImageRulesTest {
    private val screen = PxSize(1240, 1080)

    @Test
    fun `files over 20 MB are refused before anything else`() {
        val plan = BackgroundImageRules.plan(BackgroundImageRules.MAX_BYTES + 1, PxSize(100, 100), false, screen, "image/png")
        assertEquals(ImportPlan.Reject(RejectReason.TOO_LARGE_FILE), plan)
    }

    @Test
    fun `a file of exactly 20 MB is fine`() {
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(BackgroundImageRules.MAX_BYTES, PxSize(100, 100), false, screen, "image/jpeg"))
    }

    @Test
    fun `unsupported types and empty images are refused`() {
        assertEquals(ImportPlan.Reject(RejectReason.UNSUPPORTED_TYPE), BackgroundImageRules.plan(10, PxSize(10, 10), false, screen, "image/heif"))
        assertEquals(ImportPlan.Reject(RejectReason.UNSUPPORTED_TYPE), BackgroundImageRules.plan(10, PxSize(10, 10), false, screen, null))
        assertEquals(ImportPlan.Reject(RejectReason.NOT_AN_IMAGE), BackgroundImageRules.plan(10, PxSize(0, 10), false, screen, "image/png"))
    }

    @Test
    fun `images up to twice the screen are kept as they are`() {
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(10, PxSize(2480, 2160), false, screen, "image/png"))
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(10, PxSize(2480, 2160), true, screen, "image/gif"))
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(10, PxSize(320, 240), true, screen, "IMAGE/GIF"))
    }

    @Test
    fun `animations are refused while Animated backgrounds is off`() {
        assertEquals(
            ImportPlan.Reject(RejectReason.ANIMATION_OFF),
            BackgroundImageRules.plan(10, PxSize(320, 240), true, screen, "image/gif", allowAnimated = false),
        )
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(10, PxSize(320, 240), true, screen, "image/gif", allowAnimated = true))
        // Still images don't care, and a still WebP is fine either way.
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(10, PxSize(320, 240), false, screen, "image/webp", allowAnimated = false))
    }

    @Test
    fun `an animated image plays only with Animated backgrounds on`() {
        val gif = BackgroundImage("a.gif", animated = true)
        assertTrue(BackgroundImageRules.plays(gif, animationsOn = true))
        assertTrue(!BackgroundImageRules.plays(gif, animationsOn = false)) // first frame, no player or surface
        assertTrue(!BackgroundImageRules.plays(BackgroundImage("a.png"), animationsOn = true))
        assertTrue(!BackgroundImageRules.plays(null, animationsOn = true))
    }

    @Test
    fun `big animations are refused`() {
        assertEquals(ImportPlan.Reject(RejectReason.TOO_LARGE_ANIMATION), BackgroundImageRules.plan(10, PxSize(2481, 100), true, screen, "image/webp"))
    }

    @Test
    fun `big photos are downscaled so they still cover twice the screen`() {
        val plan = BackgroundImageRules.plan(10, PxSize(4960, 4320), false, screen, "image/jpeg") as ImportPlan.Downscale
        assertEquals(PxSize(2480, 2160), plan.size)
        // A 12 MP 4:3 photo: height sets the scale, the width stays over 2x.
        val photo = BackgroundImageRules.plan(10, PxSize(4000, 3000), false, screen, "image/jpeg") as ImportPlan.Downscale
        assertEquals(2160, photo.size.height)
        assertTrue(photo.size.width >= 2480)
    }

    @Test
    fun `a panorama taller than twice the screen is not downscaled below covering it`() {
        // 10000x1000: wider than 2x, but shrinking would leave it shorter than the screen, so it is kept.
        assertEquals(ImportPlan.Keep, BackgroundImageRules.plan(10, PxSize(10000, 1000), false, screen, "image/png"))
    }

    @Test
    fun `cover crops the middle without stretching`() {
        // Square image on a wide target: scaled to the target width, cropped top and bottom.
        val plan = BackgroundImageRules.cover(PxSize(500, 500), PxSize(1240, 1080))
        assertEquals(PxSize(1240, 1240), plan.scaled)
        assertEquals(0, plan.cropLeft)
        assertEquals(80, plan.cropTop)
        assertEquals(PxSize(1240, 1080), plan.crop)
        // A wide image: scaled to the target height, cropped at the sides.
        val wide = BackgroundImageRules.cover(PxSize(4000, 1000), PxSize(1240, 1080))
        assertEquals(PxSize(4320, 1080), wide.scaled)
        assertEquals((4320 - 1240) / 2, wide.cropLeft)
        assertEquals(0, wide.cropTop)
    }

    @Test
    fun `cover keeps the crop inside the scaled image`() {
        listOf(PxSize(1, 1), PxSize(333, 777), PxSize(1241, 1079), PxSize(5000, 3)).forEach { img ->
            val p = BackgroundImageRules.cover(img, screen)
            assertTrue(p.cropLeft >= 0 && p.cropTop >= 0)
            assertTrue(p.cropLeft + p.crop.width <= p.scaled.width)
            assertTrue(p.cropTop + p.crop.height <= p.scaled.height)
        }
    }

    @Test
    fun `animations render at their own resolution, never upscaled in software`() {
        // A 320x240 GIF on a 1240x1080 panel: the panel's shape at the GIF's scale (height-bound), cropped at the sides.
        assertEquals(PxSize(276, 240), BackgroundImageRules.renderSize(PxSize(320, 240), PxSize(1240, 1080)))
        // Big enough already: the panel's size.
        assertEquals(PxSize(1240, 1080), BackgroundImageRules.renderSize(PxSize(2000, 2000), PxSize(1240, 1080)))
        // The result always fits inside the image, so cover() never upscales it.
        listOf(PxSize(1, 1), PxSize(100, 900), PxSize(1239, 1081)).forEach { img ->
            val r = BackgroundImageRules.renderSize(img, PxSize(1240, 1080))
            assertTrue(r.width <= img.width && r.height <= img.height)
            assertEquals(PxSize(r.width, r.height), BackgroundImageRules.cover(img, r).crop)
        }
    }

    @Test
    fun `animations move to their own surface only once the panel is shown and a frame exists`() {
        assertTrue(BackgroundImageRules.drawsOnSurface(playing = true, settled = true, hasFirstFrame = true))
        // Showing or hiding (the surface wouldn't fade or slide with the window).
        assertTrue(!BackgroundImageRules.drawsOnSurface(playing = true, settled = false, hasFirstFrame = true))
        // Previews have no overlay window.
        assertTrue(!BackgroundImageRules.drawsOnSurface(playing = true, settled = null, hasFirstFrame = true))
        // Nothing to show yet: the backdrop stays.
        assertTrue(!BackgroundImageRules.drawsOnSurface(playing = true, settled = true, hasFirstFrame = false))
        assertTrue(!BackgroundImageRules.drawsOnSurface(playing = false, settled = true, hasFirstFrame = true))
    }

    @Test
    fun `stored image names stay inside their folder`() {
        assertEquals("x.png", BackgroundImageRules.safeName("../../data/x.png"))
        assertEquals("a_b.png", BackgroundImageRules.safeName("a..b.png"))
        assertEquals(null, BackgroundImageRules.safeName(" "))
        assertEquals(null, BackgroundImageRules.safeName(null))
    }

    @Test
    fun `frames are held to 30 fps`() {
        // The drawable asks for the next frame 10 ms after the last one: it waits until 33 ms.
        assertEquals(1033L, BackgroundImageRules.nextFrameAt(requestedAt = 1010, lastFrameAt = 1000, now = 1005))
        // A slow GIF (100 ms frames) keeps its own timing.
        assertEquals(1100L, BackgroundImageRules.nextFrameAt(requestedAt = 1100, lastFrameAt = 1000, now = 1005))
        // Never in the past.
        assertEquals(2000L, BackgroundImageRules.nextFrameAt(requestedAt = 1010, lastFrameAt = 1000, now = 2000))
    }

    @Test
    fun `stored names are unique, safe and keep the type`() {
        assertEquals("My cat-ab12cd34.jpg", BackgroundImageRules.storedName("My cat.jpeg", "ab12cd34", "image/jpeg"))
        assertEquals("x-t.gif", BackgroundImageRules.storedName("../../etc/x.gif", "t", "image/gif"))
        assertEquals("background-t.webp", BackgroundImageRules.storedName(null, "t", "image/webp"))
        assertEquals("background-t.png", BackgroundImageRules.storedName("  .png", "t", "image/png"))
        assertEquals("a_b-t.png", BackgroundImageRules.storedName("a:b.png", "t", "image/png"))
    }
}
