package jr.brian.commandpannel.replay.studio

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player's trim mode (#51): its key map and how in / out points move. */
class ReplayTrimKeysTest {

    @Test fun yEntersTrimFromThePlayer() {
        assertEquals(PlayerCommand.TRIM, playerCommandFor(KeyEvent.KEYCODE_BUTTON_Y))
        assertFalse(PlayerCommand.TRIM.repeats())
    }

    @Test fun trimMap() {
        assertEquals(TrimCommand.TOGGLE_PLAY, trimCommandFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(TrimCommand.SEEK_BACK, trimCommandFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TrimCommand.SEEK_FORWARD, trimCommandFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(TrimCommand.SET_IN, trimCommandFor(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(TrimCommand.SET_OUT, trimCommandFor(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(TrimCommand.PREVIEW, trimCommandFor(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(TrimCommand.SAVE, trimCommandFor(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(TrimCommand.EXIT, trimCommandFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(TrimCommand.EXIT, trimCommandFor(KeyEvent.KEYCODE_BACK))
        assertEquals(TrimCommand.SET_IN, trimCommandFor(KeyEvent.KEYCODE_I))
        assertEquals(TrimCommand.SET_OUT, trimCommandFor(KeyEvent.KEYCODE_O))
        assertNull(trimCommandFor(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(trimCommandFor(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test fun onlyTrimSeekingRepeats() {
        assertTrue(TrimCommand.SEEK_BACK.repeats())
        assertFalse(TrimCommand.SET_IN.repeats())
        assertFalse(TrimCommand.SAVE.repeats())
    }

    @Test fun fineSeekStaysInsideTheClip() {
        assertEquals(11_000L, seekTarget(10_000, 30_000, PLAYER_TRIM_SEEK_STEP_MS))
        assertEquals(0L, seekTarget(500, 30_000, -PLAYER_TRIM_SEEK_STEP_MS))
    }

    @Test fun startsAsTheWholeClip() {
        val p = TrimPoints()
        assertTrue(p.isWholeClip(30_000))
        assertTrue(p.isWholeClip(0))
        assertEquals(30_000L, p.out(30_000))
        assertEquals(0f..1f, p.fractions(30_000))
        assertNull(p.fractions(0))
    }

    @Test fun settingPoints() {
        val p = TrimPoints().withIn(5_000, 30_000).withOut(12_000, 30_000)
        assertEquals(TrimPoints(5_000, 12_000), p)
        assertFalse(p.isWholeClip(30_000))
        val f = p.fractions(30_000)!!
        assertEquals(5f / 30f, f.start, 1e-4f)
        assertEquals(12f / 30f, f.endInclusive, 1e-4f)
    }

    @Test fun anInPointPastTheOutPointResetsTheOut() {
        val p = TrimPoints(2_000, 8_000).withIn(10_000, 30_000)
        assertEquals(TrimPoints(10_000, TrimPoints.END), p)
        assertEquals(30_000L, p.out(30_000))
    }

    @Test fun anOutPointBeforeTheInPointResetsTheIn() {
        assertEquals(TrimPoints(0, 4_000), TrimPoints(6_000, 20_000).withOut(4_000, 30_000))
        assertEquals(TrimPoints(0, 6_000), TrimPoints(6_000, 20_000).withOut(6_000, 30_000))
    }

    @Test fun pointsAreClampedToTheClip() {
        assertEquals(0L, TrimPoints().withIn(-500, 30_000).inMs)
        assertEquals(30_000L, TrimPoints(1_000).withOut(45_000, 30_000).outMs)
        // An out point at the end counts as the whole clip again only with the in point at the start.
        assertTrue(TrimPoints(0, 30_000).isWholeClip(30_000))
        assertFalse(TrimPoints(1, 30_000).isWholeClip(30_000))
    }
}
