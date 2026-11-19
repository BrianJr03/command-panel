package jr.brian.commandpannel.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingVisibleTest {

    @Test
    fun `runs right away when the panel is already fully visible`() {
        val pending = PendingVisible()
        var ran = 0
        pending.add(visibleNow = true) { ran++ }
        assertEquals(1, ran)
        assertEquals(0, pending.size)
    }

    @Test
    fun `waits for the fade-in, then runs once`() {
        val pending = PendingVisible()
        var ran = 0
        pending.add(visibleNow = false) { ran++ }
        assertEquals(0, ran)
        pending.flush()
        assertEquals(1, ran)
        pending.flush()
        assertEquals(1, ran)
    }

    @Test
    fun `runs in the order added`() {
        val pending = PendingVisible()
        val order = mutableListOf<Int>()
        pending.add(visibleNow = false) { order += 1 }
        pending.add(visibleNow = false) { order += 2 }
        pending.flush()
        assertEquals(listOf(1, 2), order)
    }

    @Test
    fun `a callback added while flushing waits for the next flush`() {
        val pending = PendingVisible()
        var late = 0
        pending.add(visibleNow = false) { pending.add(visibleNow = false) { late++ } }
        pending.flush()
        assertEquals(0, late)
        assertEquals(1, pending.size)
        pending.flush()
        assertEquals(1, late)
    }
}
