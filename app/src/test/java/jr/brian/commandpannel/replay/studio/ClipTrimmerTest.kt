package jr.brian.commandpannel.replay.studio

import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trimming a clip (#51): keyframe snapping, span clamping and the trimmed copy's name. */
class ClipTrimmerTest {

    private val s = 1_000_000L
    private val min = ClipTrimmer.MIN_TRIM_US

    // Snapping.

    @Test
    fun snapsBackToThePreviousKeyframe() {
        val keys = listOf(0L, 2 * s, 4 * s, 6 * s)
        assertEquals(2 * s, snapToKeyframeUs(keys, 3 * s))
        assertEquals(2 * s, snapToKeyframeUs(keys, 4 * s - 1))
        assertEquals(6 * s, snapToKeyframeUs(keys, 30 * s))
    }

    @Test
    fun aStartOnAKeyframeStaysPut() {
        assertEquals(4 * s, snapToKeyframeUs(listOf(0L, 2 * s, 4 * s), 4 * s))
        assertEquals(0L, snapToKeyframeUs(listOf(0L, 2 * s), 0L))
    }

    @Test
    fun aStartBeforeEveryKeyframeUsesTheFirst() {
        assertEquals(500_000L, snapToKeyframeUs(listOf(500_000L, 2 * s), 100_000L))
    }

    @Test
    fun unsortedKeyframesStillSnap() {
        assertEquals(2 * s, snapToKeyframeUs(listOf(4 * s, 0L, 2 * s), 3 * s))
    }

    @Test
    fun noKeyframesIsNull() {
        assertNull(snapToKeyframeUs(emptyList(), 3 * s))
    }

    // Clamping.

    @Test
    fun aSpanInsideTheClipIsKept() {
        assertEquals(TrimSpan(2 * s, 8 * s), clampTrimSpan(2 * s, 8 * s, 30 * s))
    }

    @Test
    fun pointsInEitherOrderGiveTheSameSpan() {
        assertEquals(clampTrimSpan(2 * s, 8 * s, 30 * s), clampTrimSpan(8 * s, 2 * s, 30 * s))
    }

    @Test
    fun pointsOutsideTheClipAreClamped() {
        assertEquals(TrimSpan(0L, 30 * s), clampTrimSpan(-5 * s, 45 * s, 30 * s))
    }

    @Test
    fun aShortSpanGrowsPastTheOutPoint() {
        val span = clampTrimSpan(5 * s, 5 * s + 200_000L, 30 * s)!!
        assertEquals(TrimSpan(5 * s, 5 * s + min), span)
        assertEquals(min, span.durationUs)
    }

    @Test
    fun aShortSpanAtTheEndGrowsBackward() {
        assertEquals(TrimSpan(29 * s, 30 * s), clampTrimSpan(29_800_000L, 30 * s, 30 * s))
        assertEquals(TrimSpan(29 * s, 30 * s), clampTrimSpan(31 * s, 32 * s, 30 * s))
    }

    @Test
    fun aClipShorterThanTheMinimumCantBeTrimmed() {
        assertNull(clampTrimSpan(0L, 500_000L, 900_000L))
        assertNull(clampTrimSpan(0L, 0L, 0L))
        assertEquals(TrimSpan(0L, min), clampTrimSpan(0L, min, min))
    }

    // Naming.

    private val dir = "/storage/emulated/0/TCP Clips/Top"

    @Test
    fun trimNameAddsTheSuffix() {
        assertEquals("$dir/Replay_20261119_210300_Top_trim.mp4", ScreenRecorderEngine.replayTrimClipPath("$dir/Replay_20261119_210300_Top.mp4"))
        assertEquals(
            "/storage/emulated/0/TCP Clips/Both/Replay_20261119_210300_Both_trim.mp4",
            ScreenRecorderEngine.replayTrimClipPath("/storage/emulated/0/TCP Clips/Both/Replay_20261119_210300_Both.mp4"),
        )
    }

    @Test
    fun trimNameDropsTheSourceNumberAndDoesntStack() {
        val base = "$dir/Replay_20261119_210300_Top_trim.mp4"
        assertEquals(base, ScreenRecorderEngine.replayTrimClipPath("$dir/Replay_20261119_210300_3_Top.mp4"))
        assertEquals(base, ScreenRecorderEngine.replayTrimClipPath("$dir/Replay_20261119_210300_Top_trim.mp4"))
        assertEquals(base, ScreenRecorderEngine.replayTrimClipPath("$dir/Replay_20261119_210300_2_Top_trim.mp4"))
    }

    @Test
    fun trimNameRejectsNonClips() {
        assertNull(ScreenRecorderEngine.replayTrimClipPath("$dir/clip.mp4"))
        assertNull(ScreenRecorderEngine.replayTrimClipPath("$dir/Replay_20261119_210300_Top.mp4.png"))
    }

    @Test
    fun aTakenTrimNameGetsANumberBeforeTheScreen() {
        val base = "$dir/Replay_20261119_210300_Top_trim.mp4"
        assertEquals(base, ScreenRecorderEngine.pickFreeReplayClipPath(base, setOf("Replay_20261119_210300_Top.mp4")))
        assertEquals(
            "$dir/Replay_20261119_210300_2_Top_trim.mp4",
            ScreenRecorderEngine.pickFreeReplayClipPath(base, setOf("Replay_20261119_210300_Top_trim.mp4")),
        )
        assertEquals(
            "$dir/Replay_20261119_210300_3_Top_trim.mp4",
            ScreenRecorderEngine.pickFreeReplayClipPath(
                base,
                setOf("Replay_20261119_210300_Top_trim.mp4", "Replay_20261119_210300_2_Top_trim.mp4"),
            ),
        )
    }

    @Test
    fun trimPathsAreValidAndParseAsTrims() {
        for (path in listOf(
            "$dir/Replay_20261119_210300_Top_trim.mp4",
            "$dir/Replay_20261119_210300_7_Top_trim.mp4",
            "/storage/emulated/0/Movies/My Clips/Bottom/Replay_20261119_210300_Bottom_trim.mp4",
        )) {
            assertTrue(path, ScreenRecorderEngine.isValidReplayClipOutputPath(path))
            val clip = parseReplayClip(path.substringAfterLast('/'), path.substringBeforeLast('/'))!!
            assertTrue(path, clip.isTrim)
            assertEquals(path, clip.path)
        }
    }

    @Test
    fun malformedTrimNamesAreStillRejected() {
        for (path in listOf(
            "$dir/Replay_20261119_210300_trim_Top.mp4",
            "$dir/Replay_20261119_210300_Top_trim_trim.mp4",
            "$dir/Replay_20261119_210300_Top_Trim.mp4",
            "/storage/emulated/0/Replay_20261119_210300_Top_trim.mp4",
        )) {
            assertFalse(path, ScreenRecorderEngine.isValidReplayClipOutputPath(path))
        }
    }
}
