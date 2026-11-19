package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * The nav bar's button positions: spaced evenly (stock's `SpaceEvenly`) while the buttons fit, otherwise packed
 * [minGapPx] apart from the start so the row can scroll (#19). Pure, for tests.
 *
 * [totalPx] is the row's width (at least the viewport, wider when it overflows), [sizesPx] the buttons' widths.
 * Returns each button's start, left to right.
 */
fun navBarPositions(totalPx: Int, sizesPx: IntArray, minGapPx: Int): IntArray {
    val n = sizesPx.size
    if (n == 0) return IntArray(0)
    val free = totalPx - sizesPx.sum()
    val even = free.toFloat() / (n + 1)
    val out = IntArray(n)
    if (even >= minGapPx) {
        var x = even
        for (i in 0 until n) {
            out[i] = Math.round(x)
            x += sizesPx[i] + even
        }
    } else {
        // Doesn't fit evenly: minimum gaps between buttons, none at the ends (the row's content padding is the edge).
        val gap = if (n > 1) maxOf(minGapPx.toFloat(), free.toFloat() / (n - 1)) else 0f
        var x = 0f
        for (i in 0 until n) {
            out[i] = Math.round(x)
            x += sizesPx[i] + gap
        }
    }
    return out
}

/**
 * [Arrangement.Horizontal] for [navBarPositions]. Its [spacing] makes the row ask for at least [minGap] between
 * buttons, so an overflowing row measures wider than the viewport and scrolls instead of squeezing.
 */
class NavBarArrangement(private val minGap: Dp) : Arrangement.Horizontal {
    override val spacing: Dp get() = minGap

    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) {
        val positions = navBarPositions(totalSize, sizes, minGap.roundToPx())
        if (layoutDirection == LayoutDirection.Ltr) {
            positions.copyInto(outPositions)
        } else {
            // Mirror for right-to-left.
            for (i in sizes.indices) outPositions[i] = totalSize - positions[i] - sizes[i]
        }
    }
}
