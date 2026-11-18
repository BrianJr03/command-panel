package jr.brian.commandpannel.ui

import jr.brian.commandpannel.ui.settings.copyName
import jr.brian.commandpannel.ui.settings.nonBlankName
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioNamingTest {
    private val copy = { n: String -> String.format("%1\$s copy", n) }
    private val copyN = { n: String, i: Int -> String.format("%1\$s copy %2\$d", n, i) }

    @Test
    fun `copies get the next free name`() {
        assertEquals("Glacier copy", copyName("Glacier", listOf("Glacier"), copy, copyN))
        assertEquals("Glacier copy 2", copyName("Glacier", listOf("Glacier", "Glacier copy"), copy, copyN))
        assertEquals("Glacier copy 3", copyName("Glacier", listOf("glacier COPY", "Glacier copy 2"), copy, copyN))
        assertEquals("Look0 copy", copyName("Look0", listOf("Look0"), copy, copyN))
    }

    @Test
    fun `blank names fall back`() {
        assertEquals("Mine", nonBlankName("  ") { "Mine" })
        assertEquals("Neon", nonBlankName(" Neon ") { "Mine" })
    }
}
