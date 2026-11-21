package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCostTest {

    @Test
    fun `the largest value fills the bar area and the rest scale from zero`() {
        val f = BackgroundCost.fractions(listOf(3, 12, 13))
        assertEquals(1f, f[2], 1e-6f)
        assertEquals(12f / 13f, f[1], 1e-6f)
        assertEquals(3f / 13f, f[0], 1e-6f)
    }

    @Test
    fun `fractions handle zeros, negatives and an empty list`() {
        assertEquals(listOf(0f, 0f), BackgroundCost.fractions(listOf(0, 0)))
        assertEquals(listOf(0f, 1f), BackgroundCost.fractions(listOf(-4, 8)))
        assertEquals(emptyList<Float>(), BackgroundCost.fractions(emptyList()))
    }

    @Test
    fun `the entries keep the chart's fixed order and the baseline numbers`() {
        assertEquals(
            listOf(R.string.settings_performance_still, R.string.settings_performance_gradient, R.string.settings_performance_gif),
            BackgroundCost.ENTRIES.map { it.labelRes },
        )
        assertEquals(listOf(3, 12, 13), BackgroundCost.ENTRIES.map { it.cpuPercent })
        assertTrue(BackgroundCost.ENTRIES.all { it.cpuPercent in 0..100 })
    }

    @Test
    fun `the description reads the title and every value in order`() {
        // The same patterns as settings_performance_chart_item and settings_performance_chart_desc.
        val desc = BackgroundCost.describe(
            "Processor use while the panel is open",
            listOf("Still image" to 3, "Moving gradient" to 12, "Animated GIF" to 13),
            item = { label, v -> String.format("%1\$s, about %2\$d%%.", label, v) },
            whole = { t, items -> String.format("%1\$s. %2\$s", t, items) },
        )
        assertEquals(
            "Processor use while the panel is open. Still image, about 3%. Moving gradient, about 12%. Animated GIF, about 13%.",
            desc,
        )
    }

    @Test
    fun `the value label pattern prints a whole percent`() {
        assertEquals("12%", String.format("%1\$d%%", 12))
    }
}
