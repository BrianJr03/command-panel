package jr.brian.commandpannel.privileged

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockSuppressorTest {

    @Test
    fun parsePidsKeepsOnlyNumbers() {
        assertEquals(listOf("1234", "5678"), StockSuppressor.parsePids("1234 5678"))
        assertEquals(emptyList<String>(), StockSuppressor.parsePids(""))
        assertEquals(emptyList<String>(), StockSuppressor.parsePids(null))
        assertEquals(listOf("42"), StockSuppressor.parsePids("  42  "))
    }

    @Test
    fun stoppedStatDetectsTheStoppedState() {
        assertTrue(StockSuppressor.isStoppedStat("22906 (dualscreen.assist) T 1736 1736 0 ..."))
        assertFalse(StockSuppressor.isStoppedStat("22906 (dualscreen.assist) S 1736 1736 0 ..."))
        assertFalse(StockSuppressor.isStoppedStat(null))
    }
}
