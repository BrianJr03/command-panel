package jr.brian.commandpannel.replay.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which clips the auto-delete rules pick: age, size cap, both, the just-saved clip, and the rules themselves. */
class ClipAutoDeleteTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_795_122_180_000L // 2026-11-19 21:03:00 UTC
    private val dir = "/storage/emulated/0/TCP Clips/Top"

    /** A clip made [daysAgo] days before [now], [mb] MB, name and file time agreeing. */
    private fun clip(name: String, daysAgo: Int, mb: Long = 100, trim: Boolean = false): ReplayClip {
        val at = now - daysAgo * day
        return ReplayClip(
            path = "$dir/$name.mp4",
            name = "$name.mp4",
            origin = ClipOrigin.TOP,
            recordedAtMs = at,
            sizeBytes = mb * 1_000_000L,
            modifiedMs = at,
            isTrim = trim,
        )
    }

    private fun names(clips: List<ReplayClip>) = clips.map { it.name.removeSuffix(".mp4") }

    private val library = listOf(
        clip("new", 0),
        clip("week", 8),
        clip("month", 40),
        clip("ancient", 200),
        clip("month_trim", 35, trim = true),
    )

    @Test
    fun emptyListPicksNothing() {
        assertEquals(emptyList<ReplayClip>(), pickClipsToDelete(emptyList(), now, 7, 1_000L))
    }

    @Test
    fun noRulesPicksNothing() {
        assertEquals(emptyList<ReplayClip>(), pickClipsToDelete(library, now, null, null))
    }

    @Test
    fun ageOnlyPicksOlderClipsOldestFirst() {
        assertEquals(listOf("ancient", "month", "month_trim"), names(pickClipsToDelete(library, now, 30, null)))
        assertEquals(listOf("ancient", "month", "month_trim", "week"), names(pickClipsToDelete(library, now, 7, null)))
        assertEquals(emptyList<String>(), names(pickClipsToDelete(library, now, 365, null)))
    }

    @Test
    fun ageUsesTheLaterOfNameAndFileTime() {
        // An old clip copied back in by hand (new file time) isn't old yet.
        val copied = clip("copied", 100).copy(modifiedMs = now - day)
        assertEquals(emptyList<String>(), names(pickClipsToDelete(listOf(copied), now, 30, null)))
    }

    @Test
    fun capOnlyRemovesOldestUntilUnderTheCap() {
        // 500 MB total; under 300 MB means dropping the two oldest.
        assertEquals(listOf("ancient", "month"), names(pickClipsToDelete(library, now, null, 300_000_000L)))
        // Exactly at the cap is fine.
        assertEquals(emptyList<String>(), names(pickClipsToDelete(library, now, null, 500_000_000L)))
        // A cap of zero removes everything.
        assertEquals(5, pickClipsToDelete(library, now, null, 0L).size)
    }

    @Test
    fun bothRulesAgeFirstThenCap() {
        // Age 30 removes ancient, month, month_trim (300 MB); 200 MB left, a 150 MB cap also takes week.
        assertEquals(
            listOf("ancient", "month", "month_trim", "week"),
            names(pickClipsToDelete(library, now, 30, 150_000_000L)),
        )
        // A cap already met after the age rule adds nothing.
        assertEquals(listOf("ancient", "month", "month_trim"), names(pickClipsToDelete(library, now, 30, 250_000_000L)))
    }

    @Test
    fun justSavedClipIsKeptButCounts() {
        val saved = clip("saved", 0, mb = 400)
        val clips = library + saved
        // 900 MB with the new clip; under 450 MB means everything but the new clip goes.
        val picked = pickClipsToDelete(clips, now, null, 450_000_000L, keepPath = saved.path)
        assertFalse(saved in picked)
        assertEquals(listOf("ancient", "month", "month_trim", "week", "new"), names(picked))
        // Even when it alone is over the cap, or "old" by a clock change.
        val oldSaved = clip("saved", 99)
        assertFalse(oldSaved in pickClipsToDelete(listOf(oldSaved), now, 7, 0L, keepPath = oldSaved.path))
    }

    @Test
    fun rulesOnlyApplyWhenEnabled() {
        val off = AutoDeleteRules()
        assertFalse(off.active)
        assertNull(off.maxAgeDays)
        val on = AutoDeleteRules(enabled = true, ageOn = true, ageDays = 14, sizeOn = true, sizeGb = 5)
        assertTrue(on.active)
        assertEquals(14, on.maxAgeDays)
        assertEquals(5_000_000_000L, on.maxTotalBytes)
        assertFalse(on.copy(ageOn = false, sizeOn = false).active)
        assertNull(on.copy(enabled = false).maxTotalBytes)
    }
}
