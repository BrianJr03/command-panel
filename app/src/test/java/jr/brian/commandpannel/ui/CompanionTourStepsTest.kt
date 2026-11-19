package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.settings.RailGroup
import jr.brian.commandpannel.ui.settings.TOUR_STEPS
import jr.brian.commandpannel.ui.settings.TourSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the companion tour's step list against the grouped rail. */
class CompanionTourStepsTest {

    @Test
    fun `one step teaches the rail controls on a group header`() {
        val steps = TOUR_STEPS.filter { it.spot == TourSpot.RAIL_GROUP }
        assertEquals(1, steps.size)
        val step = steps.single()
        // Its section opens a group, so the header it spotlights is on screen.
        assertNotNull(step.section?.let(RailGroup::of))
        // A opens / closes, Y moves a group, A moves a section.
        assertEquals(listOf(GamepadButton.A, GamepadButton.Y, GamepadButton.A), step.controls.map { it.button })
    }

    @Test
    fun `the group step comes before the first step inside a group`() {
        val groupStep = TOUR_STEPS.indexOfFirst { it.spot == TourSpot.RAIL_GROUP }
        val firstGrouped = TOUR_STEPS.indexOfFirst { s -> s.section?.let(RailGroup::of) != null && s.spot != TourSpot.RAIL_GROUP }
        assertTrue(groupStep in 0 until firstGrouped)
    }

    @Test
    fun `every section step spotlights its own rail item`() {
        for (s in TOUR_STEPS) {
            val section = s.section ?: continue
            if (s.spot?.inPane == false && s.spot != TourSpot.RAIL && s.spot != TourSpot.RAIL_GROUP) {
                assertEquals(TourSpot.railItem(section), s.spot)
            }
        }
    }
}
