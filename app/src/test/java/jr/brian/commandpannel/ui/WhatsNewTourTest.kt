package jr.brian.commandpannel.ui

import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.settings.SettingsCategory
import jr.brian.commandpannel.ui.settings.TOUR_STEPS
import jr.brian.commandpannel.ui.settings.TourSpot
import jr.brian.commandpannel.ui.settings.WHATS_NEW_TOURS
import jr.brian.commandpannel.ui.settings.WHATS_NEW_TOUR_VERSION
import jr.brian.commandpannel.ui.settings.tourBackStep
import jr.brian.commandpannel.ui.settings.tourNextStep
import jr.brian.commandpannel.ui.settings.tourStepsFor
import jr.brian.commandpannel.ui.settings.whatsNewTourFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the what's-new tours (#62) and running the tour over any list of steps. */
class WhatsNewTourTest {

    @Test
    fun `the pinned version has a what's new tour`() {
        assertNotNull(whatsNewTourFor(WHATS_NEW_TOUR_VERSION))
    }

    @Test
    fun `a version without steps gets no tour, so the dialog shows no Tour button`() {
        assertNull(whatsNewTourFor("0.0.1"))
        assertNull(whatsNewTourFor(""))
    }

    @Test
    fun `every what's new tour opens with its version and ends with the done step`() {
        for ((version, steps) in WHATS_NEW_TOURS) {
            assertTrue(steps.size >= 3)
            val first = steps.first()
            assertEquals(R.string.whats_new_title, first.titleRes)
            assertEquals(version, first.titleArg)
            assertNull(first.spot)
            assertEquals(R.string.tour_whats_new_done_title, steps.last().titleRes)
            assertNull(steps.last().spot)
        }
    }

    @Test
    fun `the 0_14_0 tour covers its notes`() {
        val steps = whatsNewTourFor(WHATS_NEW_TOUR_VERSION)!!
        assertEquals("0.14.0", WHATS_NEW_TOUR_VERSION)
        // Media Controls in General, then text steps for the Jarn colors and the panel fixes.
        assertEquals(SettingsCategory.GENERAL, steps.single { it.spot == TourSpot.OVERLAY_EXTRAS }.section)
        assertTrue(steps.any { it.titleRes == R.string.tour_new_jarn_colors_title && it.spot == null })
        assertTrue(steps.any { it.titleRes == R.string.tour_new_fixes_v014_title && it.spot == null })
    }

    @Test
    fun `the 0_13_0 tour still covers its notes`() {
        val steps = whatsNewTourFor("0.13.0")!!
        // Gradients and Gradient Studio, Performance, SD card clips, then two text steps for the panel and fixes.
        assertEquals(2, steps.count { it.spot == TourSpot.GRADIENTS && it.section == SettingsCategory.APPEARANCE })
        assertEquals(SettingsCategory.EXPERIMENTAL, steps.single { it.spot == TourSpot.ANIMATED_BACKGROUNDS }.section)
        assertEquals(SettingsCategory.REPLAY, steps.single { it.spot == TourSpot.REPLAY }.section)
        assertTrue(steps.any { it.titleRes == R.string.tour_new_background_tile_title && it.spot == null })
        assertTrue(steps.any { it.titleRes == R.string.tour_new_fixes_title && it.spot == null })
    }

    @Test
    fun `the 0_12_0 tour still covers its notes`() {
        val steps = whatsNewTourFor("0.12.0")!!
        // Fan slider (panel only, text step), background image, animated backgrounds, the tile.
        assertTrue(steps.any { it.titleRes == R.string.tour_new_fan_speed_title && it.spot == null })
        assertEquals(SettingsCategory.APPEARANCE, steps.single { it.spot == TourSpot.BACKGROUND_IMAGE }.section)
        assertEquals(SettingsCategory.EXPERIMENTAL, steps.single { it.spot == TourSpot.ANIMATED_BACKGROUNDS }.section)
        assertEquals(SettingsCategory.QUICK_TOGGLES, steps.single { it.spot == TourSpot.TILE_PICKER }.section)
    }

    @Test
    fun `pane spots in a what's new tour name their section`() {
        for (steps in WHATS_NEW_TOURS.values) for (s in steps) {
            if (s.spot?.inPane == true) assertNotNull(s.section)
        }
    }

    @Test
    fun `the full tour runs when no what's new tour is active`() {
        assertSame(TOUR_STEPS, tourStepsFor(null))
        assertSame(TOUR_STEPS, tourStepsFor("0.0.1"))
        assertSame(WHATS_NEW_TOURS.getValue(WHATS_NEW_TOUR_VERSION), tourStepsFor(WHATS_NEW_TOUR_VERSION))
    }

    @Test
    fun `next walks a given list to its end`() {
        for (steps in listOf(TOUR_STEPS, tourStepsFor(WHATS_NEW_TOUR_VERSION))) {
            var step: Int? = 0
            var visited = 0
            while (step != null) {
                visited++
                step = tourNextStep(step, steps.size)
            }
            assertEquals(steps.size, visited)
        }
    }

    @Test
    fun `back leaves the tour from its first step`() {
        assertNull(tourBackStep(0))
        assertEquals(0, tourBackStep(1))
        assertEquals(4, tourBackStep(5))
    }

    @Test
    fun `the full tour is unchanged by the what's new tours`() {
        assertTrue(TOUR_STEPS.none { it.spot == TourSpot.BACKGROUND_IMAGE })
        assertEquals(R.string.tour_welcome_title, TOUR_STEPS.first().titleRes)
        assertEquals(TourSpot.TOUR_REPLAY, TOUR_STEPS.last().spot)
    }
}
