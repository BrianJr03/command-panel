package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.settings.NewBadgeState
import jr.brian.commandpannel.ui.settings.NewItem
import jr.brian.commandpannel.ui.settings.NewItems
import jr.brian.commandpannel.ui.settings.NewPlace
import jr.brian.commandpannel.ui.settings.RailGroup
import jr.brian.commandpannel.ui.settings.SettingsCategory
import jr.brian.commandpannel.ui.settings.SettingsCategory.KEYBOARD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers which "new" bubbles show: baseline, seen, and the roll-up from card to section to group. */
class NewBadgesTest {

    private val card = NewItem("card:thorkbm", 15, NewPlace.Card(KEYBOARD, NewItems.CARD_THORKBM))
    private val section = NewItem("section:keyboard", 15, NewPlace.Section(KEYBOARD))
    private val group = NewItem("group:extras", 15, NewPlace.Group(RailGroup.EXTRAS))
    private val items = listOf(card, section, group)

    private fun state(baseline: Int, seen: Set<String> = emptySet()) = NewBadgeState(baseline, seen, items)

    @Test
    fun `an upgrade from 14 shows this release's items, a fresh install shows none`() {
        assertEquals(14, NewBadgeState.firstBaseline(versionCode = 15, upgraded = true, items = items))
        assertEquals(15, NewBadgeState.firstBaseline(versionCode = 15, upgraded = false, items = items))
        // A dev build still on 14 doesn't show this release's items to a fresh install either.
        assertEquals(15, NewBadgeState.firstBaseline(versionCode = 14, upgraded = false, items = items))
        // A later fresh install (17) hides everything up to 17.
        assertEquals(17, NewBadgeState.firstBaseline(versionCode = 17, upgraded = false, items = items))
        assertTrue(state(14).isNew(card))
        assertFalse(state(15).isNew(card))
        assertFalse(NewBadgeState().groupNew(RailGroup.EXTRAS)) // not loaded yet: nothing shows
    }

    @Test
    fun `parents roll up and clear bottom-up`() {
        var s = state(14)
        assertTrue(s.cardNew(KEYBOARD, NewItems.CARD_THORKBM))
        assertTrue(s.sectionNew(KEYBOARD))
        assertTrue(s.groupNew(RailGroup.EXTRAS))
        assertFalse(s.groupNew(RailGroup.PANEL))

        // Focusing the section clears only its own item: the card in it is still new, so the section still shows.
        s = s.copy(seen = s.seen + s.keysAt(NewPlace.Section(KEYBOARD)))
        assertTrue(s.sectionNew(KEYBOARD))
        // Seeing the card clears the section.
        s = s.copy(seen = s.seen + s.keysAt(NewPlace.Card(KEYBOARD, NewItems.CARD_THORKBM)))
        assertFalse(s.sectionNew(KEYBOARD))
        // The group still has its own item until its header is focused.
        assertTrue(s.groupNew(RailGroup.EXTRAS))
        s = s.copy(seen = s.seen + s.keysAt(NewPlace.Group(RailGroup.EXTRAS)))
        assertFalse(s.groupNew(RailGroup.EXTRAS))
    }

    @Test
    fun `a parent whose children are all seen clears even if its header wasn't focused`() {
        val s = NewBadgeState(14, setOf("card:thorkbm", "section:keyboard"), listOf(card, section))
        assertFalse(s.groupNew(RailGroup.EXTRAS))
    }

    @Test
    fun `keysAt only returns unseen items at that exact place`() {
        val s = state(14, seen = setOf("section:keyboard"))
        assertEquals(emptySet<String>(), s.keysAt(NewPlace.Section(KEYBOARD)))
        assertEquals(setOf("card:thorkbm"), s.keysAt(NewPlace.Card(KEYBOARD, NewItems.CARD_THORKBM)))
        assertEquals(emptySet<String>(), state(15).keysAt(NewPlace.Group(RailGroup.EXTRAS)))
    }

    @Test
    fun `seen set round-trips`() {
        val seen = setOf("group:look", "card:thorkbm")
        assertEquals(seen, NewBadgeState.parseSeen(NewBadgeState.encodeSeen(seen)))
        assertEquals(emptySet<String>(), NewBadgeState.parseSeen(null))
        assertEquals(setOf("a"), NewBadgeState.parseSeen(" a, ,"))
    }

    @Test
    fun `every registry item points at a real group, section or card`() {
        val keys = NewItems.ALL.map { it.key }
        assertEquals("keys are unique", keys.distinct(), keys)
        for (item in NewItems.ALL) {
            when (val place = item.place) {
                is NewPlace.Group -> assertTrue(place.group in RailGroup.entries)
                // General is pinned outside groups; every other section must sit in a group to roll up.
                is NewPlace.Section -> assertTrue(place.section == SettingsCategory.GENERAL || RailGroup.of(place.section) != null)
                is NewPlace.Card -> assertTrue("${item.key} has no tagged card", place.card in NewItems.CARDS[place.section].orEmpty())
            }
            assertTrue("${item.key} must ship after 0.9.1 (14)", item.since > NewBadgeState.LEGACY_BASELINE)
        }
    }

    @Test
    fun `this release marks the grouped rail, Keyboard and the ThorKBM card`() {
        val s = NewBadgeState(baseline = 14)
        RailGroup.entries.forEach { assertTrue(it.name, s.groupNew(it)) }
        assertTrue(s.sectionNew(KEYBOARD))
        assertTrue(s.cardNew(KEYBOARD, NewItems.CARD_THORKBM))
        assertFalse(s.sectionNew(SettingsCategory.GENERAL))
    }

    @Test
    fun `the Keyboard tile row bubbles up through Tiles to Panel and clears bottom-up`() {
        var s = NewBadgeState(baseline = 14)
        val tile = NewPlace.Card(SettingsCategory.QUICK_TOGGLES, NewItems.CARD_KEYBOARD_TILE)
        assertEquals(NewItems.CARD_KEYBOARD_TILE, NewItems.TILE_CARDS["keyboard"])
        assertTrue(s.cardNew(SettingsCategory.QUICK_TOGGLES, NewItems.CARD_KEYBOARD_TILE))
        assertTrue(s.sectionNew(SettingsCategory.QUICK_TOGGLES))
        // Panel's own header item seen: still new while Tiles holds a new tile.
        s = s.copy(seen = s.seen + s.keysAt(NewPlace.Group(RailGroup.PANEL)))
        assertTrue(s.groupNew(RailGroup.PANEL))
        s = s.copy(seen = s.seen + s.keysAt(tile))
        assertFalse(s.sectionNew(SettingsCategory.QUICK_TOGGLES))
        assertFalse(s.groupNew(RailGroup.PANEL))
        // Nav Bar never had anything new.
        assertFalse(s.sectionNew(SettingsCategory.NAV_BAR))
    }

    @Test
    fun `0_10_0 users see only the Keyboard section's new toggles, which roll up to Keyboard and Extras`() {
        var s = NewBadgeState(baseline = 15)
        assertTrue(s.cardNew(KEYBOARD, NewItems.CARD_THORKBM_NAV))
        assertTrue(s.cardNew(KEYBOARD, NewItems.CARD_THORKBM_TILE))
        assertFalse(s.cardNew(KEYBOARD, NewItems.CARD_THORKBM)) // shipped in 15
        assertTrue(s.sectionNew(KEYBOARD))
        RailGroup.of(KEYBOARD)?.let { assertTrue(s.groupNew(it)) }
        s = s.copy(seen = s.seen + s.keysAt(NewPlace.Card(KEYBOARD, NewItems.CARD_THORKBM_NAV)) +
            s.keysAt(NewPlace.Card(KEYBOARD, NewItems.CARD_THORKBM_TILE)))
        assertFalse(s.sectionNew(KEYBOARD))
        // A fresh install of 16 shows neither.
        assertFalse(NewBadgeState(baseline = 16).sectionNew(KEYBOARD))
    }

    @Test
    fun `0_10_1 users see the Replay Studio card, which rolls up to Replay Buffer and Extras and clears bottom-up`() {
        val replay = SettingsCategory.REPLAY
        val studio = NewPlace.Card(replay, NewItems.CARD_REPLAY_STUDIO)
        assertEquals(17, NewItems.ALL.single { it.place == studio }.since)
        var s = NewBadgeState(baseline = 16)
        assertTrue(s.cardNew(replay, NewItems.CARD_REPLAY_STUDIO))
        assertTrue(s.sectionNew(replay))
        assertTrue(s.groupNew(RailGroup.EXTRAS))
        // Nothing older shows to someone on 0.10.1.
        assertFalse(s.sectionNew(KEYBOARD))
        s = s.copy(seen = s.seen + s.keysAt(studio))
        // "Play on the recorded screen" (#47) is new in the same release; the section clears once both are seen.
        val sourceScreen = NewPlace.Card(replay, NewItems.CARD_REPLAY_SOURCE_SCREEN)
        assertTrue(s.cardNew(replay, NewItems.CARD_REPLAY_SOURCE_SCREEN))
        assertTrue(s.sectionNew(replay))
        s = s.copy(seen = s.seen + s.keysAt(sourceScreen))
        assertFalse(s.sectionNew(replay))
        assertFalse(s.groupNew(RailGroup.EXTRAS))
        // A fresh install of the Replay Studio release shows nothing.
        assertEquals(17, NewBadgeState.firstBaseline(versionCode = 16, upgraded = false))
        assertFalse(NewBadgeState(baseline = 17).sectionNew(replay))
    }

    @Test
    fun `with Show New Dots off nothing is new anywhere, and nothing gets marked seen`() {
        val on = NewBadgeState(baseline = 14, seen = setOf("card:thorkbm"))
        val off = on.shown(showDots = false)
        for (item in NewItems.ALL) {
            assertFalse(item.key, off.isNew(item))
            assertEquals(item.key, emptySet<String>(), off.keysAt(item.place))
            when (val place = item.place) {
                is NewPlace.Group -> assertFalse(off.groupNew(place.group))
                is NewPlace.Section -> assertFalse(off.sectionNew(place.section))
                is NewPlace.Card -> {
                    assertFalse(off.cardNew(place.section, place.card))
                    assertFalse(off.sectionNew(place.section))
                }
            }
        }
        RailGroup.entries.forEach { assertFalse(it.name, off.groupNew(it)) }
        // The seen set is kept, so turning the dots back on shows only what's still unseen.
        assertEquals(on.seen, off.seen)
    }

    @Test
    fun `with Show New Dots on the state is unchanged`() {
        val on = NewBadgeState(baseline = 14, seen = setOf("card:thorkbm"))
        assertEquals(on, on.shown(showDots = true))
        assertFalse(on.shown(true).cardNew(KEYBOARD, NewItems.CARD_THORKBM))
        assertTrue(on.shown(true).sectionNew(KEYBOARD))
    }
}
