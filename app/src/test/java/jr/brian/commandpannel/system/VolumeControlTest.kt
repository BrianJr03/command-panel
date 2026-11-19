package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the bottom-volume drag throttle, its step mapping and the echo guard. */
class VolumeControlTest {

    @Test fun levelRoundsToNearestStep() {
        assertEquals(0, bottomVolumeLevel(0f))
        assertEquals(15, bottomVolumeLevel(1f))
        assertEquals(15, bottomVolumeLevel(0.99f))
        assertEquals(8, bottomVolumeLevel(0.5f))
        assertEquals(0, bottomVolumeLevel(-0.2f))
        assertEquals(15, bottomVolumeLevel(1.4f))
    }

    @Test fun firstDragWritesRightAway() {
        val t = SteppedWriteThrottle(100)
        assertEquals(5, t.onDrag(5, now = 0))
    }

    @Test fun sameStepNeverWritesAgain() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(5, 0)
        assertNull(t.onDrag(5, 500))
        assertNull(t.pending)
    }

    @Test fun stepsInsideTheIntervalWaitForFlush() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(5, 0)
        assertNull(t.onDrag(6, 30))
        assertNull(t.onDrag(7, 60))
        assertEquals(7, t.pending)
        assertEquals(40L, t.flushDelay(60))
        assertNull(t.flush(80))
        assertEquals(7, t.flush(100))
        assertNull(t.pending)
        assertNull(t.flushDelay(100))
    }

    @Test fun movingBackToTheWrittenStepDropsThePending() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(5, 0)
        t.onDrag(6, 30)
        assertNull(t.onDrag(5, 60))
        assertNull(t.pending)
        assertNull(t.flush(200))
    }

    @Test fun dragAfterTheIntervalWritesRightAway() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(5, 0)
        assertEquals(6, t.onDrag(6, 100))
    }

    @Test fun commitWritesTheFinalStepIgnoringTheInterval() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(5, 0)
        t.onDrag(9, 20)
        assertEquals(9, t.onCommit(9, 30))
        assertNull(t.pending)
    }

    @Test fun commitOnTheWrittenStepDoesNothing() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(5, 0)
        assertNull(t.onCommit(5, 10))
    }

    @Test fun syncRebasesSoTheOldStepWritesAgain() {
        val t = SteppedWriteThrottle(100)
        t.onDrag(8, 0)
        t.sync(3) // changed elsewhere
        assertEquals(8, t.onCommit(8, 500))
    }

    @Test fun guardIgnoresWhileDraggingAndDuringTheHold() {
        val g = VolumeEchoGuard(400)
        assertFalse(g.ignoring(0))
        g.onDrag()
        assertTrue(g.ignoring(10_000))
        assertNull(g.holdEndsAt())
        g.onRelease(1_000)
        assertTrue(g.ignoring(1_399))
        assertFalse(g.ignoring(1_400))
        assertEquals(1_400L, g.holdEndsAt())
    }

    @Test fun aLateWriteExtendsTheHold() {
        val g = VolumeEchoGuard(400)
        g.onDrag()
        g.onRelease(1_000)
        g.onWriteDone(1_300)
        assertTrue(g.ignoring(1_600))
        assertFalse(g.ignoring(1_700))
    }

    @Test fun anEarlyWriteDoesNotShortenTheHold() {
        val g = VolumeEchoGuard(400)
        g.onRelease(1_000)
        g.onWriteDone(500)
        assertTrue(g.ignoring(1_200))
    }
}
