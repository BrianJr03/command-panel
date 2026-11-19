package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers when the Experimental In-game FPS switch stops the root helper's task FPS callback. */
class TaskFpsSwitchTest {

    private var on = false
    private var stops = 0
    private val switch = TaskFpsSwitch(enabled = { on }, stop = { stops++ })

    @Test
    fun `while off it never stops the callback`() {
        repeat(3) { assertFalse(switch.sample()) }
        switch.release()
        assertEquals(0, stops)
    }

    @Test
    fun `turning it off stops the callback once`() {
        on = true
        assertTrue(switch.sample())
        assertTrue(switch.sample())
        on = false
        assertFalse(switch.sample())
        assertFalse(switch.sample())
        assertEquals(1, stops)
    }

    @Test
    fun `stopping the monitor stops the callback only if it was on`() {
        on = true
        switch.sample()
        switch.release()
        assertEquals(1, stops)
        // Already released: a second stop, or one after an off sample, does nothing more.
        switch.release()
        on = false
        switch.sample()
        switch.release()
        assertEquals(1, stops)
    }

    @Test
    fun `turning it back on after a stop starts fresh`() {
        on = true
        switch.sample()
        on = false
        switch.sample()
        on = true
        assertTrue(switch.sample())
        switch.release()
        assertEquals(2, stops)
    }
}
