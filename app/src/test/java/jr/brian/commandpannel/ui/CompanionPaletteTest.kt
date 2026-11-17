package jr.brian.commandpannel.ui

import androidx.compose.ui.graphics.Color
import jr.brian.commandpannel.ui.settings.companionPaletteFrom
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.contrastRatio
import jr.brian.commandpannel.ui.theme.resolvePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the companion's "follow overlay look" palette: same palette pick as the overlay, readable everywhere. */
class CompanionPaletteTest {

    private val ledAccents = listOf(0xFF2244FF.toInt(), 0xFFFFE04A.toInt(), 0xFF101010.toInt(), 0xFFFFFFFF.toInt())

    private val palettes: List<TccColors> = listOf(TccColors.Light, TccColors.PureBlack) +
        ledAccents.flatMap { argb -> listOf(TccColors.Light, TccColors.PureBlack).map { it.copy(accent = Color(argb)) } }

    @Test
    fun resolvePaletteMatchesOverlayThemeModes() {
        assertEquals(TccColors.Light, resolvePalette(3, nightActive = true, accentOverride = null))
        assertEquals(TccColors.PureBlack, resolvePalette(4, nightActive = false, accentOverride = null))
        assertEquals(TccColors.PureBlack, resolvePalette(2, nightActive = false, accentOverride = null))
        assertEquals(TccColors.PureBlack, resolvePalette(1, nightActive = true, accentOverride = null))
        assertEquals(TccColors.Light, resolvePalette(1, nightActive = false, accentOverride = null))
        assertEquals(Color(0xFF2244FF), resolvePalette(4, nightActive = false, accentOverride = 0xFF2244FF.toInt()).accent)
    }

    @Test
    fun textStaysReadableOnSurfaces() {
        palettes.forEach { colors ->
            val p = companionPaletteFrom(colors)
            assertTrue("primary on $colors", p.textPrimary.contrastRatio(p.surface) >= 4.5f)
            assertTrue("secondary on $colors", p.textSecondary.contrastRatio(p.surface) >= 4.5f)
        }
    }

    @Test
    fun accentIsOpaqueAndVisible() {
        palettes.forEach { colors ->
            val p = companionPaletteFrom(colors)
            assertEquals(1f, p.accent.alpha)
            assertTrue("accent on $colors", p.accent.contrastRatio(p.surface) >= 3f)
            assertTrue("onAccent on $colors", p.onAccent.contrastRatio(p.accent) >= 3f)
        }
    }
}
