package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.overlay.navBarPositions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NavBarArrangementTest {

    @Test
    fun `buttons that fit are spaced evenly, edges included`() {
        // 3 buttons of 100 in 700: 400 free, 100 per gap (4 gaps).
        assertArrayEquals(intArrayOf(100, 300, 500), navBarPositions(700, intArrayOf(100, 100, 100), minGapPx = 20))
    }

    @Test
    fun `buttons that don't fit evenly pack from the start with the minimum gap`() {
        // The row asks for 100*4 + 20*3 = 460 when it overflows a smaller viewport.
        assertArrayEquals(intArrayOf(0, 120, 240, 360), navBarPositions(460, intArrayOf(100, 100, 100, 100), minGapPx = 20))
    }

    @Test
    fun `in between, the spare room goes between the buttons, never below the minimum`() {
        // 500 for 4x100: even would be 20 per gap (5 gaps) < 30, so 3 inner gaps of 33.
        val p = navBarPositions(500, intArrayOf(100, 100, 100, 100), minGapPx = 30)
        assertEquals(0, p[0])
        for (i in 1 until p.size) assert(p[i] - p[i - 1] - 100 >= 30)
        assertEquals(500.0, (p.last() + 100).toDouble(), 1.0) // the last button ends at the row's end
    }

    @Test
    fun `nothing overlaps or leaves the row`() {
        val sizes = intArrayOf(88, 120, 64, 150, 99, 101, 77)
        for (total in listOf(sizes.sum() + 12 * 6, 900, 1200, 1600)) {
            val p = navBarPositions(total, sizes, minGapPx = 12)
            for (i in 1 until p.size) assert(p[i] >= p[i - 1] + sizes[i - 1] + 11) { "overlap at $i in $total" }
            assert(p.last() + sizes.last() <= total + 1) { "past the end in $total" }
        }
    }

    @Test
    fun `one button is centered and none is empty`() {
        assertArrayEquals(intArrayOf(450), navBarPositions(1000, intArrayOf(100), minGapPx = 12))
        assertArrayEquals(IntArray(0), navBarPositions(1000, IntArray(0), minGapPx = 12))
    }
}
