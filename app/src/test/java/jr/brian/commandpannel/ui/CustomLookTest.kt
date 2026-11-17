package jr.brian.commandpannel.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import jr.brian.commandpannel.ui.theme.BundledFont
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.FontRef
import jr.brian.commandpannel.ui.theme.GaugeStyle
import jr.brian.commandpannel.ui.theme.LabelCase
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.PinnedColors
import jr.brian.commandpannel.ui.theme.ShapeDef
import jr.brian.commandpannel.ui.theme.ShapeKind
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccMonoFontFamily
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.TileStyle
import jr.brian.commandpannel.ui.theme.contrastRatio
import jr.brian.commandpannel.ui.theme.resolveLookColors
import jr.brian.commandpannel.ui.theme.specForLook
import jr.brian.commandpannel.ui.theme.toSpec
import jr.brian.commandpannel.ui.theme.withPinned
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomLookTest {

    @Test
    fun `no overrides resolves to the base look's spec`() {
        TccUiLook.entries.forEach { base ->
            assertEquals(base.name, specForLook(base), CustomLook(id = "x", name = "x", baseLook = base).toSpec())
        }
    }

    @Test
    fun `overrides mix parts from other looks`() {
        val spec = CustomLook(
            id = "x", name = "Mix", baseLook = TccUiLook.TERMINAL,
            card = CardStyle.GLASS, tile = TileStyle.GLASS_GLOW, labelFont = FontRef.Bundled(BundledFont.DEFAULT),
        ).toSpec()
        assertEquals(CardStyle.GLASS, spec.traits.card)
        assertEquals(TileStyle.GLASS_GLOW, spec.traits.tile)
        assertEquals(GaugeStyle.ASCII_BAR, spec.traits.gauge) // inherited from Terminal
        assertNull(spec.traits.labelFontFamily) // DEFAULT explicitly clears Terminal's mono
        assertEquals(TccMonoFontFamily, spec.traits.displayFontFamily)
    }

    @Test
    fun `soft extruded cards lift the backdrop`() {
        assertTrue(CustomLook(id = "x", name = "x", card = CardStyle.SOFT_EXTRUDED).toSpec().traits.liftedBackdrop)
    }

    @Test
    fun `json round trip`() {
        val look = CustomLook(
            id = "abc", name = "Neon Term", baseLook = TccUiLook.NEON_ARCADE,
            gauge = GaugeStyle.ASCII_BAR, panelCardShape = ShapeDef(ShapeKind.CHAMFER, 9f),
            labelFont = FontRef.User("f1", "Orbitron"), displayFont = FontRef.Bundled(BundledFont.SEGMENT),
            labelCase = LabelCase.SNAKE, letterSpacingEm = 0.04f, glow = false, outlineWidth = 2f,
            ringValueScale = 0.25f, colors = PinnedColors(accent = Color(0xFF00FFAA), background = Color(0xFF101010)),
        )
        val decoded = CustomLookJson.decodeList(CustomLookJson.encodeList(listOf(look))).single()
        assertEquals(look, decoded)
    }

    @Test
    fun `unknown enums inherit and numbers clamp`() {
        val o = JSONObject().put("id", "x").put("name", "x").put("card", "FROM_THE_FUTURE").put("ringValueScale", 9.0)
        val look = CustomLookJson.decode(o)!!
        assertNull(look.card)
        assertEquals(0.45f, look.ringValueScale)
    }

    @Test
    fun `look refs parse both kinds`() {
        assertEquals(LookRef.Custom("abc"), LookRef.parse("custom:abc"))
        assertEquals(LookRef.BuiltIn(TccUiLook.TERMINAL), LookRef.parse("TERMINAL"))
        assertEquals(LookRef.BuiltIn(TccUiLook.AUTO), LookRef.parse(null))
    }

    @Test
    fun `pinned light background flips to light text and keeps look contrast`() {
        val palette = TccColors.PureBlack.withPinned(PinnedColors(background = Color(0xFFF7F1E3), accent = Color(0xFFFFD54F)))
        assertTrue(!palette.isDark)
        assertTrue(palette.primary.luminance() < 0.2f)
        val c = resolveLookColors(palette, TccUiLook.TERMINAL)
        assertTrue(c.terminalInk.contrastRatio(palette.primaryBackground) >= 4.5f)
    }

    @Test
    fun `pinned accent2 replaces the derived complement`() {
        val palette = TccColors.PureBlack.withPinned(PinnedColors(accent2 = Color(0xFFFF0066)))
        assertEquals(Color(0xFFFF0066), resolveLookColors(palette, TccUiLook.AUTO).accent2)
    }

    @Test
    fun `pinned black background keeps cards distinguishable`() {
        val palette = TccColors.Light.withPinned(PinnedColors(background = Color.Black))
        assertTrue(palette.isDark)
        assertTrue(palette.panelBackground.contrastRatio(palette.primaryBackground) >= 1.15f)
    }
}
