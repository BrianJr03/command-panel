package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.settings.RailGroup
import jr.brian.commandpannel.ui.settings.RailGroup.ADVANCED
import jr.brian.commandpannel.ui.settings.RailGroup.EXTRAS
import jr.brian.commandpannel.ui.settings.RailGroup.LOOK
import jr.brian.commandpannel.ui.settings.RailGroup.PANEL
import jr.brian.commandpannel.ui.settings.RailExpanded
import jr.brian.commandpannel.ui.settings.RailLayout
import jr.brian.commandpannel.ui.settings.RailRow
import jr.brian.commandpannel.ui.settings.railFocusTarget
import jr.brian.commandpannel.ui.settings.visibleRows
import jr.brian.commandpannel.ui.settings.SettingsCategory
import jr.brian.commandpannel.ui.settings.SettingsCategory.APPEARANCE
import jr.brian.commandpannel.ui.settings.SettingsCategory.DIAGNOSTICS
import jr.brian.commandpannel.ui.settings.SettingsCategory.EXPERIMENTAL
import jr.brian.commandpannel.ui.settings.SettingsCategory.GENERAL
import jr.brian.commandpannel.ui.settings.SettingsCategory.KEYBOARD
import jr.brian.commandpannel.ui.settings.SettingsCategory.NAV_BAR
import jr.brian.commandpannel.ui.settings.SettingsCategory.QUICK_TOGGLES
import jr.brian.commandpannel.ui.settings.SettingsCategory.REPLAY
import jr.brian.commandpannel.ui.settings.SettingsCategory.THEMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers reading the companion rail's saved section order: the legacy flat one and the two-level group order. */
class RailOrderTest {

    @Test
    fun `nothing saved is the default order, with Replay after Themes and Experimental last`() {
        assertEquals(SettingsCategory.entries, SettingsCategory.ordered(null))
        assertEquals(
            listOf(GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, THEMES, REPLAY, KEYBOARD, DIAGNOSTICS, EXPERIMENTAL),
            SettingsCategory.ordered(""),
        )
    }

    @Test
    fun `a full saved order round-trips`() {
        val order = listOf(REPLAY, EXPERIMENTAL, KEYBOARD, THEMES, GENERAL, DIAGNOSTICS, NAV_BAR, APPEARANCE, QUICK_TOGGLES)
        assertEquals(order, SettingsCategory.ordered(order.joinToString(",") { it.key }))
    }

    @Test
    fun `a section missing from an older save goes after the one before it by default`() {
        // Saved before Replay existed, with Themes moved to the top.
        assertEquals(
            listOf(THEMES, REPLAY, KEYBOARD, GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, DIAGNOSTICS, EXPERIMENTAL),
            SettingsCategory.ordered("themes,general,nav_bar,tiles,appearance,diagnostics"),
        )
    }

    @Test
    fun `Experimental joins an order saved before it existed, right after Diagnostics`() {
        // A v0.8.0-style save with every other section, Diagnostics moved up.
        assertEquals(
            listOf(GENERAL, DIAGNOSTICS, EXPERIMENTAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, THEMES, REPLAY, KEYBOARD),
            SettingsCategory.ordered("general,diagnostics,nav_bar,tiles,appearance,themes,replay"),
        )
    }

    @Test
    fun `Keyboard joins an order saved before it existed, right after Replay`() {
        // A v0.9.1-style save with Replay moved to the top.
        assertEquals(
            listOf(REPLAY, KEYBOARD, GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, THEMES, DIAGNOSTICS, EXPERIMENTAL),
            SettingsCategory.ordered("replay,general,nav_bar,tiles,appearance,themes,diagnostics,experimental"),
        )
    }

    @Test
    fun `unknown keys and repeats are dropped`() {
        assertEquals(
            listOf(DIAGNOSTICS, EXPERIMENTAL, GENERAL, NAV_BAR, QUICK_TOGGLES, APPEARANCE, THEMES, REPLAY, KEYBOARD),
            SettingsCategory.ordered("diagnostics, bogus,diagnostics,general"),
        )
    }

    // ---- rail groups (#28) ----

    private fun layout(vararg prefs: Pair<String, String>): RailLayout = prefs.toMap().let { m -> RailLayout.read { m[it] } }

    @Test
    fun `every section but General is in exactly one group`() {
        val grouped = RailGroup.entries.flatMap { it.sections }
        assertEquals(grouped.distinct(), grouped)
        assertEquals(SettingsCategory.entries - GENERAL, grouped.sortedBy { it.ordinal })
        assertEquals(null, RailGroup.of(GENERAL))
        assertEquals(EXTRAS, RailGroup.of(KEYBOARD))
    }

    @Test
    fun `nothing saved is the default layout, in the old default order`() {
        assertEquals(RailLayout.DEFAULT, layout())
        assertEquals(listOf(PANEL, LOOK, EXTRAS, ADVANCED), layout().groups)
        assertEquals(SettingsCategory.entries, layout().flat)
    }

    @Test
    fun `a legacy order migrates groups by their first section and keeps order inside each group`() {
        // Diagnostics up top, Themes above Appearance, Keyboard above Replay, General moved down.
        val l = layout("rail_order" to "diagnostics,themes,nav_bar,general,keyboard,appearance,tiles,replay,experimental")
        assertEquals(listOf(ADVANCED, LOOK, PANEL, EXTRAS), l.groups)
        assertEquals(listOf(THEMES, APPEARANCE), l.sectionsOf(LOOK))
        assertEquals(listOf(NAV_BAR, QUICK_TOGGLES), l.sectionsOf(PANEL))
        assertEquals(listOf(KEYBOARD, REPLAY), l.sectionsOf(EXTRAS))
        assertEquals(listOf(DIAGNOSTICS, EXPERIMENTAL), l.sectionsOf(ADVANCED))
        // General's custom spot is dropped: it is pinned on top.
        assertEquals(GENERAL, l.flat.first())
    }

    @Test
    fun `a split group goes where its first section was`() {
        // Experimental first, Diagnostics last: Advanced leads, with Experimental above Diagnostics.
        val l = RailLayout.migrate("experimental,general,nav_bar,tiles,appearance,themes,replay,keyboard,diagnostics")
        assertEquals(listOf(ADVANCED, PANEL, LOOK, EXTRAS), l.groups)
        assertEquals(listOf(EXPERIMENTAL, DIAGNOSTICS), l.sectionsOf(ADVANCED))
    }

    @Test
    fun `a legacy order saved before Keyboard existed puts it after Replay`() {
        val l = RailLayout.migrate("replay,general,nav_bar,tiles,appearance,themes,diagnostics,experimental")
        assertEquals(listOf(EXTRAS, PANEL, LOOK, ADVANCED), l.groups)
        assertEquals(listOf(REPLAY, KEYBOARD), l.sectionsOf(EXTRAS))
    }

    @Test
    fun `the default legacy order migrates to the default layout`() {
        assertEquals(RailLayout.DEFAULT, RailLayout.migrate(SettingsCategory.entries.joinToString(",") { it.key }))
        assertEquals(RailLayout.DEFAULT, RailLayout.migrate(null))
    }

    @Test
    fun `a two-level save round-trips and wins over the legacy order`() {
        val saved = RailLayout.DEFAULT
            .withGroupOrder(listOf("extras", "advanced", "panel", "look"))
            .withSectionOrder(LOOK, listOf("themes", "appearance"))
        val back = RailLayout.read { saved.prefs()[it] }
        assertEquals(saved, back)
        assertEquals(listOf(EXTRAS, ADVANCED, PANEL, LOOK), back.groups)
        // The flat legacy order is kept in step, so an older build sees the same rail.
        assertEquals(
            "general,replay,keyboard,diagnostics,experimental,nav_bar,tiles,themes,appearance",
            saved.prefs()["rail_order"],
        )
        // A stale legacy order doesn't override a two-level save.
        val prefs = saved.prefs() + ("rail_order" to "experimental,diagnostics")
        assertEquals(saved, RailLayout.read { prefs[it] })
    }

    @Test
    fun `two-level prefs are read tolerantly`() {
        val l = layout(
            "rail_group_order" to "advanced, bogus,advanced,look",
            // Themes belongs to Look: it can't be saved into Panel.
            "rail_sections_panel" to "tiles,themes,nav_bar,tiles",
        )
        // Groups missing from the save go after the one before them by default.
        assertEquals(listOf(PANEL, ADVANCED, LOOK, EXTRAS), l.groups)
        assertEquals(listOf(QUICK_TOGGLES, NAV_BAR), l.sectionsOf(PANEL))
        // A group with nothing saved keeps its default section order.
        assertEquals(listOf(APPEARANCE, THEMES), l.sectionsOf(LOOK))
    }

    @Test
    fun `a section added to a group later joins after the one before it by default`() {
        // Saved when Extras held only Replay (before Keyboard).
        val l = layout("rail_group_order" to "advanced,extras,panel,look", "rail_sections_extras" to "replay")
        assertEquals(listOf(REPLAY, KEYBOARD), l.sectionsOf(EXTRAS))
        assertEquals(listOf(ADVANCED, EXTRAS, PANEL, LOOK), l.groups)
    }

    @Test
    fun `reorders stay inside their level`() {
        val l = RailLayout.DEFAULT.withSectionOrder(PANEL, listOf("themes", "tiles", "nav_bar"))
        assertEquals(listOf(QUICK_TOGGLES, NAV_BAR), l.sectionsOf(PANEL))
        assertEquals(listOf(APPEARANCE, THEMES), l.sectionsOf(LOOK))
    }

    @Test
    fun `restoring a backup from before groups clears the two-level prefs so its order migrates`() {
        assertEquals(RailLayout.KEYS, RailLayout.staleAfterRestore(setOf("rail_order", "overlay_look")))
        assertTrue(RailLayout.staleAfterRestore(setOf("rail_order", "rail_group_order")).isEmpty())
        assertTrue(RailLayout.staleAfterRestore(setOf("overlay_look")).isEmpty())
        // After clearing, the restored legacy order is what the rail reads. Diagnostics, missing from it, follows
        // Keyboard as before, so Advanced comes second.
        val restored = mapOf("rail_order" to "keyboard,general")
        assertEquals(listOf(EXTRAS, ADVANCED, PANEL, LOOK), RailLayout.read { restored[it] }.groups)
    }

    // ---- grouped rail rows and focus (#29) ----

    @Test
    fun `collapsed groups show only their header, and General is always first`() {
        val rows = RailLayout.DEFAULT.withGroupOrder(listOf("look", "extras", "advanced", "panel")).visibleRows(setOf(PANEL))
        assertEquals(
            listOf(
                RailRow.Section(GENERAL), RailRow.Header(LOOK), RailRow.Header(EXTRAS), RailRow.Header(ADVANCED),
                RailRow.Header(PANEL), RailRow.Section(NAV_BAR), RailRow.Section(QUICK_TOGGLES),
            ),
            rows,
        )
        // All collapsed: General plus four headers.
        assertEquals(5, RailLayout.DEFAULT.visibleRows(emptySet()).size)
        // Row ids never collide (they key the composables).
        val all = RailLayout.DEFAULT.visibleRows(RailGroup.entries.toSet())
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(13, all.size)
    }

    @Test
    fun `rail focus goes to the selected section, or its header while the group is collapsed`() {
        assertEquals(RailRow.Section(GENERAL), railFocusTarget(GENERAL, emptySet()))
        assertEquals(RailRow.Section(THEMES), railFocusTarget(THEMES, setOf(LOOK)))
        assertEquals(RailRow.Header(LOOK), railFocusTarget(THEMES, setOf(PANEL)))
        // The target is always a row that is on screen.
        for (cat in SettingsCategory.entries) for (open in listOf(emptySet(), setOf(EXTRAS), RailGroup.entries.toSet())) {
            assertTrue(railFocusTarget(cat, open) in RailLayout.DEFAULT.visibleRows(open))
        }
    }

    @Test
    fun `expanded groups round-trip and are read tolerantly`() {
        assertEquals(emptySet<RailGroup>(), RailExpanded.parse(null))
        assertEquals(setOf(LOOK, ADVANCED), RailExpanded.parse("advanced, bogus,look,look"))
        assertEquals("look,advanced", RailExpanded.encode(setOf(ADVANCED, LOOK)))
        assertEquals(setOf(PANEL, EXTRAS), RailExpanded.parse(RailExpanded.encode(setOf(PANEL, EXTRAS))))
    }

    // ---- one group open at a time (#82) ----

    @Test
    fun `opening a group closes the others`() {
        assertEquals(setOf(LOOK), RailExpanded.toggle(emptySet(), LOOK, expanded = true))
        assertEquals(setOf(LOOK), RailExpanded.toggle(setOf(PANEL), LOOK, expanded = true))
        assertEquals(setOf(LOOK), RailExpanded.toggle(setOf(PANEL, EXTRAS), LOOK, expanded = true))
        assertEquals(setOf(LOOK), RailExpanded.toggle(setOf(LOOK), LOOK, expanded = true))
        // Closing only ever removes that group.
        assertEquals(emptySet<RailGroup>(), RailExpanded.toggle(setOf(LOOK), LOOK, expanded = false))
        assertEquals(setOf(PANEL), RailExpanded.toggle(setOf(PANEL), LOOK, expanded = false))
    }

    @Test
    fun `a save with several open groups is cut to one`() {
        // Saves from before #82 can hold several; the first open one in rail order stays.
        assertEquals(setOf(PANEL), RailExpanded.limit(setOf(EXTRAS, PANEL)))
        assertEquals(setOf(EXTRAS), RailExpanded.limit(setOf(EXTRAS, PANEL), order = listOf(ADVANCED, EXTRAS, PANEL, LOOK)))
        assertEquals(setOf(LOOK), RailExpanded.limit(setOf(LOOK)))
        assertEquals(emptySet<RailGroup>(), RailExpanded.limit(emptySet()))
    }

    // ---- moving groups (#30) ----

    @Test
    fun `moving a group keeps every group's own section order`() {
        val before = RailLayout.DEFAULT
            .withSectionOrder(LOOK, listOf("themes", "appearance"))
            .withSectionOrder(ADVANCED, listOf("experimental", "diagnostics"))
        val after = before.withGroupOrder(listOf("advanced", "look", "panel", "extras"))
        assertEquals(listOf(ADVANCED, LOOK, PANEL, EXTRAS), after.groups)
        assertEquals(before.sections, after.sections)
        assertEquals(
            listOf(GENERAL, EXPERIMENTAL, DIAGNOSTICS, THEMES, APPEARANCE, NAV_BAR, QUICK_TOGGLES, REPLAY, KEYBOARD),
            after.flat,
        )
    }

    @Test
    fun `a group move round-trips through prefs and General stays first`() {
        val moved = RailLayout.DEFAULT.withGroupOrder(listOf("extras", "panel", "advanced", "look"))
        val back = RailLayout.read { moved.prefs()[it] }
        assertEquals(moved, back)
        assertEquals(GENERAL, back.flat.first())
        assertEquals("extras,panel,advanced,look", moved.prefs()["rail_group_order"])
    }

    @Test
    fun `while a group is lifted only headers show, in the group order`() {
        val l = RailLayout.DEFAULT.withGroupOrder(listOf("look", "advanced", "panel", "extras"))
        assertEquals(
            listOf(RailRow.Section(GENERAL), RailRow.Header(LOOK), RailRow.Header(ADVANCED), RailRow.Header(PANEL), RailRow.Header(EXTRAS)),
            l.visibleRows(emptySet()),
        )
    }

    @Test
    fun `a group order naming General or a section ignores those keys`() {
        val l = RailLayout.DEFAULT.withGroupOrder(listOf("general", "advanced", "themes", "panel"))
        assertEquals(listOf(ADVANCED, PANEL, LOOK, EXTRAS), l.groups)
    }
}
