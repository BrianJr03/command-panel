package jr.brian.commandpannel.ui

import androidx.compose.ui.geometry.Rect
import jr.brian.commandpannel.ui.settings.PaneAlign
import jr.brian.commandpannel.ui.settings.paneScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class PaneJumpTest {
    // The pane on screen: 100 px from the top, 600 px tall.
    private val viewport = Rect(0f, 100f, 800f, 700f)

    @Test
    fun `top puts the target just under the pane's top, past the inset`() {
        // A target 900 px down (root coordinates) with the pane at the top: scroll so it sits 28 px below the top.
        assertEquals(772, paneScrollTarget(0, 5_000, Rect(0f, 900f, 800f, 1_000f), viewport, PaneAlign.TOP, topInset = 28f))
    }

    @Test
    fun `center puts the target in the middle of the pane`() {
        // Target center 950, pane center 400: 550 further down.
        assertEquals(750, paneScrollTarget(200, 5_000, Rect(0f, 900f, 800f, 1_000f), viewport, PaneAlign.CENTER))
    }

    @Test
    fun `scroll stays in range`() {
        // Near the top: can't scroll above 0.
        assertEquals(0, paneScrollTarget(0, 5_000, Rect(0f, 120f, 800f, 160f), viewport, PaneAlign.CENTER))
        // Near the end: can't pass the maximum (and a section that doesn't scroll stays put).
        assertEquals(300, paneScrollTarget(0, 300, Rect(0f, 2_000f, 800f, 2_100f), viewport, PaneAlign.TOP))
        assertEquals(0, paneScrollTarget(0, 0, Rect(0f, 2_000f, 800f, 2_100f), viewport, PaneAlign.TOP))
    }
}
