package jr.brian.commandpannel.replay.studio

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The player's gamepad map, seeking and clip paging (#46). */
class ReplayPlayerKeysTest {

    @Test fun gamepadMap() {
        assertEquals(PlayerCommand.TOGGLE_PLAY, playerCommandFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(PlayerCommand.CLOSE, playerCommandFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(PlayerCommand.CLOSE, playerCommandFor(KeyEvent.KEYCODE_BACK))
        assertEquals(PlayerCommand.SEEK_BACK, playerCommandFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(PlayerCommand.SEEK_FORWARD, playerCommandFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(PlayerCommand.PREVIOUS_CLIP, playerCommandFor(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(PlayerCommand.NEXT_CLIP, playerCommandFor(KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test fun otherKeysOnlyShowControls() {
        assertNull(playerCommandFor(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(playerCommandFor(KeyEvent.KEYCODE_BUTTON_X))
        // Volume stays with the system.
        assertNull(playerCommandFor(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test fun onlySeekingRepeats() {
        assertTrue(PlayerCommand.SEEK_FORWARD.repeats())
        assertFalse(PlayerCommand.CLOSE.repeats())
        assertFalse(PlayerCommand.NEXT_CLIP.repeats())
    }

    @Test fun seekStaysInsideTheClip() {
        assertEquals(15_000L, seekTarget(10_000, 60_000, PLAYER_SEEK_STEP_MS))
        assertEquals(0L, seekTarget(2_000, 60_000, -PLAYER_SEEK_STEP_MS))
        assertEquals(60_000L, seekTarget(58_000, 60_000, PLAYER_SEEK_STEP_MS))
        // Unknown length: only the start bounds it.
        assertEquals(105_000L, seekTarget(100_000, -1, PLAYER_SEEK_STEP_MS))
    }

    @Test fun neighbors() {
        val paths = listOf("/c/new.mp4", "/c/mid.mp4", "/c/old.mp4")
        assertEquals(2, neighborIndex(paths, "/c/mid.mp4", +1))
        assertEquals(0, neighborIndex(paths, "/c/mid.mp4", -1))
        assertNull(neighborIndex(paths, "/c/new.mp4", -1))
        assertNull(neighborIndex(paths, "/c/old.mp4", +1))
        assertNull(neighborIndex(paths, "/c/missing.mp4", +1))
    }

    @Test fun clock() {
        assertEquals(0L to 0L, clockParts(-5))
        assertEquals(1L to 5L, clockParts(65_999))
        assertEquals(10L to 0L, clockParts(600_000))
    }
}
