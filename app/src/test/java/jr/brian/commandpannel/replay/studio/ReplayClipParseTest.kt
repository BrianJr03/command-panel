package jr.brian.commandpannel.replay.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Replay Studio's clip-name parsing and listing: what counts as a clip, its origin, and the listed order. */
class ReplayClipParseTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val top = "/storage/emulated/0/TCP Clips/Top"

    private fun parse(name: String, dir: String = top) = parseReplayClip(name, dir, timeZone = utc)

    @Test
    fun parsesAPlainClip() {
        val c = parse("Replay_20261119_210300_Top.mp4")!!
        assertEquals("$top/Replay_20261119_210300_Top.mp4", c.path)
        assertEquals(top, c.dir)
        assertEquals(ClipOrigin.TOP, c.origin)
        assertEquals(1_795_122_180_000L, c.recordedAtMs) // 2026-11-19 21:03:00 UTC
        assertFalse(c.isTrim)
    }

    @Test
    fun acceptsANumberedDuplicate() {
        val c = parse("Replay_20261119_210300_2_Bottom.mp4")!!
        assertEquals(ClipOrigin.BOTTOM, c.origin)
        assertFalse(c.isTrim)
    }

    @Test
    fun acceptsATrimmedCopy() {
        val c = parse("Replay_20261119_210300_Both_trim.mp4")!!
        assertEquals(ClipOrigin.BOTH, c.origin)
        assertTrue(c.isTrim)
        assertTrue(parse("Replay_20261119_210300_3_Top_trim.mp4")!!.isTrim)
    }

    @Test
    fun ignoresFilesThatAreNotClips() {
        listOf(
            "notes.txt",
            "Replay_20261119_210300_Top.mkv",
            "Replay_20261119_210300.mp4",
            "Replay_20261119_210300_Left.mp4",
            "Replay_2026111_210300_Top.mp4",
            "replay_20261119_210300_Top.mp4",
            "Replay_20261119_210300_Top_trim2.mp4",
            ".Replay_20261119_210300_Top.mp4",
            "Replay_20261119_210300_Top.mp4.tmp",
            "Replay_20261340_210300_Top.mp4", // month 13
            "Replay_20261119_256100_Top.mp4", // hour 25
        ).forEach { assertNull(it, parse(it)) }
    }

    @Test
    fun customFolderIsKept() {
        val dir = "/storage/emulated/0/Movies/My Clips/Both/"
        val c = parse("Replay_20261119_210300_Both.mp4", dir)!!
        assertEquals("/storage/emulated/0/Movies/My Clips/Both", c.dir)
        assertEquals("/storage/emulated/0/Movies/My Clips/Both/Replay_20261119_210300_Both.mp4", c.path)
    }

    @Test
    fun nameSuffixWinsOverTheFolder() {
        // Moved by hand into another screen's folder: the suffix was written at save time.
        val c = parse("Replay_20261119_210300_Bottom.mp4", top)!!
        assertEquals(ClipOrigin.BOTTOM, c.origin)
        // A clip in the clips folder itself (no subfolder) still parses.
        assertEquals(ClipOrigin.TOP, parse("Replay_20261119_210300_Top.mp4", "/storage/emulated/0/TCP Clips")!!.origin)
    }

    @Test
    fun sortsNewestFirstWithATrimAfterItsSource() {
        val old = parseReplayClip("Replay_20261118_100000_Top.mp4", top, modifiedMs = 50, timeZone = utc)!!
        val src = parseReplayClip("Replay_20261119_210300_Top.mp4", top, modifiedMs = 100, timeZone = utc)!!
        val trim = parseReplayClip("Replay_20261119_210300_Top_trim.mp4", top, modifiedMs = 200, timeZone = utc)!!
        val newest = parseReplayClip("Replay_20261120_080000_Both.mp4", top, modifiedMs = 10, timeZone = utc)!!
        assertEquals(listOf(newest, trim, src, old), sortReplayClips(listOf(old, src, newest, trim)))
    }

    @Test
    fun listingParsesSortsAndDropsJunk() {
        val now = 2_000_000_000_000L
        val oldS = now / 1000 - 3600
        val out = """
            1234|$oldS|/storage/emulated/0/TCP Clips/Top/Replay_20261118_100000_Top.mp4
            999|$oldS|/storage/emulated/0/TCP Clips/Both/Replay_20261119_210300_Both.mp4
            50|$oldS|/storage/emulated/0/TCP Clips/Top/readme.mp4
            garbage line
            12|notanumber|/storage/emulated/0/TCP Clips/Top/Replay_20261117_100000_Top.mp4
            999|$oldS|/storage/emulated/0/TCP Clips/Both/Replay_20261119_210300_Both.mp4
            __CP_LIST_END__
        """.trimIndent()
        val clips = parseReplayClipListing(out, now, timeZone = utc)
        assertEquals(listOf("Replay_20261119_210300_Both.mp4", "Replay_20261118_100000_Top.mp4"), clips.map { it.name })
        assertEquals(999L, clips[0].sizeBytes)
        assertEquals(oldS * 1000, clips[0].modifiedMs)
    }

    @Test
    fun skipsFilesJustWritten() {
        val now = 2_000_000_000_000L
        val line = { ageMs: Long -> "10|${(now - ageMs) / 1000}|$top/Replay_20261119_210300_Top.mp4" }
        assertTrue(parseReplayClipListing(line(1_000), now, minAgeMs = 3_000, timeZone = utc).isEmpty())
        assertTrue(parseReplayClipListing(line(0), now, minAgeMs = 3_000, timeZone = utc).isEmpty())
        assertEquals(1, parseReplayClipListing(line(10_000), now, minAgeMs = 3_000, timeZone = utc).size)
        // A future mtime (clock changed) still lists.
        assertEquals(1, parseReplayClipListing(line(-60_000), now, minAgeMs = 3_000, timeZone = utc).size)
    }

    @Test
    fun cacheNameChangesWhenTheFileDoes() {
        val a = parseReplayClip("Replay_20261119_210300_Top.mp4", top, 10, 1000, utc)!!
        val b = a.copy(sizeBytes = 11)
        assertNotNull(replayClipCacheName(a))
        assertEquals(replayClipCacheName(a), replayClipCacheName(a.copy()))
        assertNotEquals(replayClipCacheName(a), replayClipCacheName(b))
    }
}
