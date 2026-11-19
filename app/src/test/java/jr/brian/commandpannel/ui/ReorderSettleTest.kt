package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.settings.settleTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers when a dragged row passes its neighbors (rail groups, sections, Button Order, tile order). */
class ReorderSettleTest {

    private val slot = 100f

    @Test
    fun `under half a slot it stays put`() {
        assertEquals(2 to 49f, settleTarget(2, 4, 49f, slot))
        assertEquals(2 to -50f, settleTarget(2, 4, -50f, slot))
    }

    @Test
    fun `a finger move of about one slot moves it exactly one row`() {
        assertEquals(3 to 25f, settleTarget(2, 4, 125f, slot))
        assertEquals(1 to -25f, settleTarget(2, 4, -125f, slot))
    }

    @Test
    fun `it moves one row per slot and stops at the ends`() {
        assertEquals(4 to 0f, settleTarget(2, 4, 200f, slot))
        assertEquals(4 to 300f, settleTarget(2, 4, 500f, slot))
        assertEquals(0 to -100f, settleTarget(1, 4, -200f, slot))
    }

    @Test
    fun `no measured slot yet means no move`() {
        assertEquals(2 to 300f, settleTarget(2, 4, 300f, 0f))
    }
}
