package jr.brian.commandpannel.replay.studio

import jr.brian.commandpannel.replay.studio.ReplayPauseGate.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Replay Buffer's pause while Replay Studio's player is in front, and every way it comes back (#46). */
class ReplayPauseGateTest {

    private val timeout = 180_000L
    private fun gate() = ReplayPauseGate(timeoutMs = timeout)

    @Test fun openPausesARunningBuffer() {
        val g = gate()
        assertEquals(Action.DISARM, g.onPlayerOpen(nowMs = 0, armed = true))
        assertTrue(g.paused)
    }

    @Test fun closeRearms() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        assertEquals(Action.REARM, g.onPlayerClose(enabled = true))
        assertFalse(g.paused)
    }

    @Test fun heartbeatKeepsThePauseWithoutDisarmingAgain() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        // The user armed it by hand meanwhile; a check-in leaves it alone.
        assertEquals(Action.NONE, g.onPlayerOpen(60_000, armed = true))
        assertTrue(g.paused)
    }

    @Test fun closeTwiceRearmsOnce() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        assertEquals(Action.REARM, g.onPlayerClose(enabled = true))
        assertEquals(Action.NONE, g.onPlayerClose(enabled = true))
    }

    @Test fun closeWithoutOpenDoesNothing() = assertEquals(Action.NONE, gate().onPlayerClose(enabled = true))

    @Test fun reopenAfterCloseDisarmsAgain() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        g.onPlayerClose(enabled = true)
        assertEquals(Action.DISARM, g.onPlayerOpen(10_000, armed = true))
    }

    @Test fun bufferDisabledByUserIsNeverArmed() {
        val g = gate()
        assertEquals(Action.NONE, g.onPlayerOpen(0, armed = false))
        assertTrue(g.paused)
        assertEquals(Action.NONE, g.onPlayerClose(enabled = false))
        assertFalse(g.paused)
    }

    @Test fun userTurnsBufferOffWhilePlayingIsRespected() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        assertEquals(Action.NONE, g.onPlayerClose(enabled = false))
    }

    @Test fun enabledButNotArmedAtOpenIsArmedOnClose() {
        // e.g. the buffer failed to arm earlier: leaving the player tries again, so it never stays off.
        val g = gate()
        assertEquals(Action.NONE, g.onPlayerOpen(0, armed = false))
        assertEquals(Action.REARM, g.onPlayerClose(enabled = true))
    }

    @Test fun crashedPlayerIsCaughtByTheCheck() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        // The activity is gone without a close; the next check notices even before the timeout.
        assertEquals(Action.REARM, g.onCheck(nowMs = 30_000, playerAlive = false, enabled = true))
        assertFalse(g.paused)
    }

    @Test fun crashedPlayerWithBufferDisabledStaysOff() {
        val g = gate()
        g.onPlayerOpen(0, armed = false)
        assertEquals(Action.NONE, g.onCheck(30_000, playerAlive = false, enabled = false))
        assertFalse(g.paused)
    }

    @Test fun aliveAndCheckingInStaysPaused() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        g.onPlayerOpen(60_000, armed = false)
        assertEquals(Action.NONE, g.onCheck(200_000, playerAlive = true, enabled = true))
        assertTrue(g.paused)
    }

    @Test fun timeoutRearmsAStuckPlayer() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        assertEquals(Action.NONE, g.onCheck(timeout - 1, playerAlive = true, enabled = true))
        assertEquals(Action.REARM, g.onCheck(timeout, playerAlive = true, enabled = true))
        assertFalse(g.paused)
        // A late check-in from the stuck player pauses again.
        assertEquals(Action.DISARM, g.onPlayerOpen(timeout + 1, armed = true))
    }

    @Test fun checkWhileNotPausedDoesNothing() =
        assertEquals(Action.NONE, gate().onCheck(1_000_000, playerAlive = false, enabled = true))

    // ---- screen off (#91) ----

    @Test fun screenOffStopsARunningBuffer() {
        val g = gate()
        assertEquals(Action.DISARM, g.onScreenOff(armed = true))
        assertTrue(g.asleep)
    }

    @Test fun screenOnRearms() {
        val g = gate()
        g.onScreenOff(armed = true)
        assertEquals(Action.REARM, g.onScreenOn(enabled = true))
        assertFalse(g.asleep)
    }

    @Test fun screenOnRespectsTheUserTurningItOff() {
        val g = gate()
        g.onScreenOff(armed = true)
        assertEquals(Action.NONE, g.onScreenOn(enabled = false))
    }

    @Test fun startingWithTheScreenOffArmsOnScreenOn() {
        // The service started with the lid closed: nothing to stop, and the first screen on arms it.
        val g = gate()
        assertEquals(Action.NONE, g.onScreenOff(armed = false))
        assertEquals(Action.REARM, g.onScreenOn(enabled = true))
    }

    @Test fun screenOnWithoutScreenOffDoesNothing() = assertEquals(Action.NONE, gate().onScreenOn(enabled = true))

    @Test fun playerClosingWhileAsleepWaitsForScreenOn() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        g.onScreenOff(armed = false)
        assertEquals(Action.NONE, g.onPlayerClose(enabled = true))
        assertEquals(Action.REARM, g.onScreenOn(enabled = true))
    }

    @Test fun playerWatchWhileAsleepWaitsForScreenOn() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        g.onScreenOff(armed = false)
        assertEquals(Action.NONE, g.onCheck(nowMs = 30_000, playerAlive = false, enabled = true))
        assertFalse(g.paused)
        assertEquals(Action.REARM, g.onScreenOn(enabled = true))
    }

    @Test fun screenOnWhilePlayerStillOpenWaitsForThePlayer() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        g.onScreenOff(armed = false)
        assertEquals(Action.NONE, g.onScreenOn(enabled = true))
        assertEquals(Action.REARM, g.onPlayerClose(enabled = true))
    }

    @Test fun screenOffDuringPlaybackStopsABufferArmedByHand() {
        val g = gate()
        g.onPlayerOpen(0, armed = true)
        // The user armed it by hand during playback; sleep still stops it.
        assertEquals(Action.DISARM, g.onScreenOff(armed = true))
    }
}
