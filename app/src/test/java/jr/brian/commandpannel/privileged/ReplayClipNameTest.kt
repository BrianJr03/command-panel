package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.replay.studio.parseReplayClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Free clip names for saves in the same second (#52): `_2`, `_3`, ... before the screen suffix. */
class ReplayClipNameTest {

    private val dir = "/storage/emulated/0/TCP Clips/Top"
    private val base = "$dir/Replay_20261119_210300_Top.mp4"

    private fun pick(vararg taken: String) = ScreenRecorderEngine.pickFreeReplayClipPath(base, taken.toSet())

    @Test
    fun keepsTheBaseNameWhenFree() {
        assertEquals(base, pick())
        assertEquals(base, pick("Replay_20261119_210259_Top.mp4", "notes.txt"))
    }

    @Test
    fun addsTwoWhenTheBaseNameIsTaken() {
        assertEquals("$dir/Replay_20261119_210300_2_Top.mp4", pick("Replay_20261119_210300_Top.mp4"))
    }

    @Test
    fun skipsToTheFirstFreeNumber() {
        assertEquals(
            "$dir/Replay_20261119_210300_4_Top.mp4",
            pick("Replay_20261119_210300_Top.mp4", "Replay_20261119_210300_2_Top.mp4", "Replay_20261119_210300_3_Top.mp4"),
        )
        // A gap is reused.
        assertEquals(
            "$dir/Replay_20261119_210300_2_Top.mp4",
            pick("Replay_20261119_210300_Top.mp4", "Replay_20261119_210300_3_Top.mp4"),
        )
    }

    @Test
    fun otherScreensAndTrimsDontCount() {
        assertEquals(base, pick("Replay_20261119_210300_Both.mp4", "Replay_20261119_210300_Top_trim.mp4"))
    }

    @Test
    fun givesUpWhenEveryNumberIsTaken() {
        val all = (2..ScreenRecorderEngine.MAX_REPLAY_NAME_ATTEMPTS).map { "Replay_20261119_210300_${it}_Top.mp4" } +
            "Replay_20261119_210300_Top.mp4"
        assertNull(ScreenRecorderEngine.pickFreeReplayClipPath(base, all.toSet()))
    }

    @Test
    fun rejectsANonClipBase() {
        assertNull(ScreenRecorderEngine.pickFreeReplayClipPath("$dir/clip.mp4", emptySet()))
    }

    @Test
    fun pickedNamesAreValidAndParse() {
        val picked = pick("Replay_20261119_210300_Top.mp4")!!
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath(picked))
        val clip = parseReplayClip(picked.substringAfterLast('/'), picked.substringBeforeLast('/'))
        assertNotNull(clip)
        assertEquals(picked, clip!!.path)
    }

    @Test
    fun builtPathsStillPickTheBase() {
        val built = ScreenRecorderEngine.buildReplayClipPath("20261119_210300", ScreenRecorderEngine.TARGET_BOTH, "/storage/emulated/0/TCP Clips/Both")
        assertEquals(built, ScreenRecorderEngine.pickFreeReplayClipPath(built, emptySet()))
    }
}
