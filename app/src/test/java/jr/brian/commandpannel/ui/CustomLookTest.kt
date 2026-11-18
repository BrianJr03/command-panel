package jr.brian.commandpannel.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import jr.brian.commandpannel.ui.theme.BackdropStyle
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
import jr.brian.commandpannel.ui.theme.resolveLookPalette
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
    fun `new backdrops round trip and apply`() {
        listOf(BackdropStyle.GRADIENT_WASH, BackdropStyle.TOPOGRAPHIC, BackdropStyle.HONEYCOMB, BackdropStyle.STARFIELD).forEach { b ->
            val look = CustomLook(id = b.name, name = b.name, baseLook = TccUiLook.AURORA_GLASS, backdrop = b)
            val decoded = CustomLookJson.decodeList(CustomLookJson.encodeList(listOf(look))).single()
            assertEquals(b, decoded.backdrop)
        }
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
        assertEquals(LookRef.BuiltIn(TccUiLook.DEFAULT), LookRef.parse(null))
        assertEquals(LookRef.BuiltIn(TccUiLook.AURORA_GLASS), LookRef.parse(null))
        assertEquals(LookRef.BuiltIn(TccUiLook.AUTO), LookRef.parse("NOT_A_LOOK"))
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

    @Test
    fun `custom part params round trip and only reach the spec when a part is Custom`() {
        val params = jr.brian.commandpannel.ui.theme.CustomRenderParams(
            card = jr.brian.commandpannel.ui.theme.CustomCardParams(
                fill = jr.brian.commandpannel.ui.theme.FillKind.GRADIENT, borderWidth = 2f, shadow = 4f, glow = true,
            ),
            gauge = jr.brian.commandpannel.ui.theme.CustomGaugeParams(kind = jr.brian.commandpannel.ui.theme.GaugeKind.BAR, thickness = 0.1f),
            nav = jr.brian.commandpannel.ui.theme.CustomNavParams(jr.brian.commandpannel.ui.theme.NavIndicator.BLOCK),
        )
        val look = CustomLook(id = "c", name = "c", custom = params)
        assertNull(look.toSpec().custom)
        val custom = look.copy(card = CardStyle.CUSTOM)
        assertEquals(params, custom.toSpec().custom)
        assertEquals(custom, CustomLookJson.decodeList(CustomLookJson.encodeList(listOf(custom))).single())
    }

    @Test
    fun ambientLedAccentWinsOverAPinnedAccent() {
        val pinned = PinnedColors(accent = Color(0xFF00FF00), background = Color(0xFF102030))
        val led = 0xFFFF0000.toInt()

        val synced = resolveLookPalette(themeMode = 0, nightActive = true, accentOverride = led, pinned = pinned)
        assertEquals(Color(led), synced.accent)
        assertEquals(Color(0xFF102030), synced.primaryBackground) // the pack's other pinned colors are kept

        val unsynced = resolveLookPalette(themeMode = 0, nightActive = true, accentOverride = null, pinned = pinned)
        assertEquals(Color(0xFF00FF00), unsynced.accent)
    }
}
