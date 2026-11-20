package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.settings.RailGroup
import jr.brian.commandpannel.ui.settings.SettingsCategory
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
        // A opens / closes, Y moves a group, Y moves a section.
        assertEquals(listOf(GamepadButton.A, GamepadButton.Y, GamepadButton.Y), step.controls.map { it.button })
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

    @Test
    fun `every section has an intro on its rail item`() {
        for (category in SettingsCategory.entries) {
            val intros = TOUR_STEPS.count { it.section == category && it.spot == TourSpot.railItem(category) }
            assertEquals("intro steps for $category", 1, intros)
        }
    }

    @Test
    fun `sections come in the rail's default order, each in one run`() {
        val railOrder = listOf(RailGroup.PINNED) + RailGroup.entries.flatMap { it.sections }
        // The rail and group steps sit on a section only to show the rail; skip them and the closing step.
        val runs = TOUR_STEPS
            .filter { it.spot != TourSpot.RAIL && it.spot != TourSpot.RAIL_GROUP && it.spot != TourSpot.TOUR_REPLAY }
            .mapNotNull { it.section }
            .fold(mutableListOf<SettingsCategory>()) { acc, c -> if (acc.lastOrNull() != c) acc += c; acc }
        assertEquals(railOrder, runs)
    }

    @Test
    fun `every pane spot is shown by some step`() {
        // BUILTIN_SELECTED is lit by the overlay onboarding's look follow, not by a tour step.
        val shown = TOUR_STEPS.mapNotNull { it.spot }.toSet()
        val missing = TourSpot.entries.filter { it.inPane && it != TourSpot.BUILTIN_SELECTED && it !in shown }
        assertEquals(emptyList<TourSpot>(), missing)
    }

    @Test
    fun `Replay Studio leads the Replay part`() {
        val replay = TOUR_STEPS.filter { it.section == SettingsCategory.REPLAY && it.spot?.inPane == true }
        assertEquals(TourSpot.REPLAY_STUDIO, replay.first().spot)
    }

    @Test
    fun `new cards each have a step in their own section`() {
        val expected = mapOf(
            TourSpot.SHOW_NEW_DOTS to SettingsCategory.GENERAL,
            TourSpot.REPLAY_STUDIO to SettingsCategory.REPLAY,
            TourSpot.THORKBM to SettingsCategory.KEYBOARD,
        )
        for ((spot, section) in expected) {
            assertEquals(section, TOUR_STEPS.single { it.spot == spot }.section)
        }
    }
}
