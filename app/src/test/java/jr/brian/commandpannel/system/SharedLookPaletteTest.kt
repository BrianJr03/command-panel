package jr.brian.commandpannel.system

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import jr.brian.commandpannel.ui.settings.companionPaletteFrom
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.LOOK_LINE_CONTRAST
import jr.brian.commandpannel.ui.theme.LOOK_TEXT_CONTRAST
import jr.brian.commandpannel.ui.theme.PinnedColors
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.clampContrast
import jr.brian.commandpannel.ui.theme.contrastRatio
import jr.brian.commandpannel.ui.theme.resolveLookColors
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SharedLookPaletteTest {
    private val mapped = listOf(
        TccUiLook.SOFT_CONSOLE, TccUiLook.SOFT_CONSOLE_OLED, TccUiLook.SEGMENT_LCD, TccUiLook.BLOOM,
        TccUiLook.NEON_ARCADE, TccUiLook.TERMINAL, TccUiLook.COCKPIT, TccUiLook.INDUSTRIAL,
        TccUiLook.COMIC_PANEL, TccUiLook.E_INK,
    )
    private val rendererOnly = TccUiLook.entries - mapped.toSet()
    private val palettes = listOf(TccColors.PureBlack, TccColors.Light)

    private fun row(colors: TccColors, look: TccUiLook, led: Boolean = false) =
        sharedThemeRow(publishedLookPalette(colors, look, led))

    @Test
    fun `every look is either mapped or renderer-only`() {
        assertEquals(
            setOf(
                TccUiLook.AUTO, TccUiLook.AURORA_GLASS, TccUiLook.AURORA_BLUR, TccUiLook.CARTRIDGE,
                TccUiLook.TACTICAL_HUD, TccUiLook.STICKER_BOOK, TccUiLook.JELLY, TccUiLook.Y2K_CHROME,
            ),
            rendererOnly.toSet(),
        )
    }

    @Test
    fun `each mapped look changes the published row`() {
        palettes.forEach { colors ->
            mapped.forEach { look -> assertNotEquals("$look", sharedThemeRow(colors), row(colors, look)) }
        }
    }

    @Test
    fun `renderer-only looks keep the base colors`() {
        palettes.forEach { colors ->
            rendererOnly.forEach { look -> assertEquals("$look", sharedThemeRow(colors), row(colors, look)) }
        }
    }

    @Test
    fun `mapped looks publish their own look colors`() {
        palettes.forEach { colors ->
            fun lc(look: TccUiLook) = resolveLookColors(colors, look)
            fun surface(look: TccUiLook, fill: Color) =
                fill.copy(alpha = 1f).clampContrast(row(colors, look).getValue("bg").let(::Color), SHARED_SURFACE_STEP).toArgb()
            assertEquals(surface(TccUiLook.SOFT_CONSOLE, lc(TccUiLook.SOFT_CONSOLE).softSurface), row(colors, TccUiLook.SOFT_CONSOLE)["surface"])
            assertEquals(surface(TccUiLook.BLOOM, lc(TccUiLook.BLOOM).bloomContainer), row(colors, TccUiLook.BLOOM)["surface"])
            assertEquals(surface(TccUiLook.NEON_ARCADE, lc(TccUiLook.NEON_ARCADE).neonPanel), row(colors, TccUiLook.NEON_ARCADE)["surface"])
            assertEquals(surface(TccUiLook.TERMINAL, colors.primaryBackground), row(colors, TccUiLook.TERMINAL)["surface"])
            assertEquals(surface(TccUiLook.COCKPIT, lc(TccUiLook.COCKPIT).cockpitPanel), row(colors, TccUiLook.COCKPIT)["surface"])
            assertEquals(surface(TccUiLook.INDUSTRIAL, lc(TccUiLook.INDUSTRIAL).metalFill), row(colors, TccUiLook.INDUSTRIAL)["surface"])
            assertEquals(surface(TccUiLook.COMIC_PANEL, lc(TccUiLook.COMIC_PANEL).comicPaper), row(colors, TccUiLook.COMIC_PANEL)["surface"])
            assertEquals(surface(TccUiLook.E_INK, lc(TccUiLook.E_INK).einkPaper), row(colors, TccUiLook.E_INK)["surface"])
            assertEquals(lc(TccUiLook.TERMINAL).terminalInk.argb(), publishedLookPalette(colors, TccUiLook.TERMINAL, false).primary.toArgb())
            assertEquals(lc(TccUiLook.SEGMENT_LCD).lcdInk.argb(), publishedLookPalette(colors, TccUiLook.SEGMENT_LCD, false).accent.toArgb())
        }
    }

    @Test
    fun `mapped looks keep surfaces and borders visible`() {
        palettes.forEach { colors ->
            mapped.forEach { look ->
                val p = companionPaletteFrom(publishedLookPalette(colors, look, false))
                val where = "$look on dark=${colors.isDark}"
                assertTrue("$where surface ${p.surface.contrastRatio(p.bg)}", p.surface.contrastRatio(p.bg) >= SHARED_SURFACE_STEP - 0.001f)
                assertTrue("$where high ${p.surfaceHigh.contrastRatio(p.surface)}", p.surfaceHigh.contrastRatio(p.surface) >= SHARED_SURFACE_HIGH_STEP - 0.001f)
                assertTrue("$where border ${p.border.contrastRatio(p.surface)}", p.border.contrastRatio(p.surface) >= 1.1f)
            }
        }
    }

    @Test
    fun `Soft Console OLED keeps a black backdrop on dark palettes`() {
        assertEquals(Color.Black.toArgb(), row(TccColors.PureBlack, TccUiLook.SOFT_CONSOLE_OLED)["bg"])
    }

    @Test
    fun `switching from Neon Arcade to Terminal changes the row`() {
        palettes.forEach { colors ->
            assertNotEquals(row(colors, TccUiLook.NEON_ARCADE), row(colors, TccUiLook.TERMINAL))
        }
    }

    @Test
    fun `every mapped look publishes a different row`() {
        fun sameRows(colors: TccColors, looks: List<TccUiLook>) =
            looks.groupBy { row(colors, it) }.values.filter { it.size > 1 }
        assertEquals(emptyList<List<TccUiLook>>(), sameRows(TccColors.PureBlack, mapped))
        assertEquals(emptyList<List<TccUiLook>>(), sameRows(TccColors.Light, mapped - TccUiLook.SOFT_CONSOLE_OLED))
    }

    @Test
    fun `Soft Console OLED only differs from Soft Console on dark palettes`() {
        assertEquals(row(TccColors.Light, TccUiLook.SOFT_CONSOLE), row(TccColors.Light, TccUiLook.SOFT_CONSOLE_OLED))
        assertNotEquals(
            row(TccColors.PureBlack, TccUiLook.SOFT_CONSOLE),
            row(TccColors.PureBlack, TccUiLook.SOFT_CONSOLE_OLED),
        )
    }

    @Test
    fun `every published color is opaque except the soft accent`() {
        val accents = listOf(null, 0xFF00C8FF.toInt(), 0x80FF2040.toInt())
        palettes.forEach { base ->
            accents.forEach { accent ->
                val colors = accent?.let { base.copy(accent = Color(it)) } ?: base
                mapped.forEach { look ->
                    val values = row(colors, look, led = accent != null) - SharedThemeContract.COLUMN_VERSION -
                        SharedThemeContract.COLUMN_IS_DARK - SharedThemeContract.COLUMN_ACCENT_SOFT
                    values.forEach { (column, argb) -> assertEquals("$look $column", 0xFF, argb ushr 24) }
                }
            }
        }
    }

    @Test
    fun `mapped looks keep the documented contrast`() {
        val accents = listOf(null, 0xFF00C8FF.toInt(), 0xFFFFD400.toInt(), 0xFF2030A0.toInt())
        palettes.forEach { base ->
            accents.forEach { accent ->
                val colors = accent?.let { base.copy(accent = Color(it)) } ?: base
                mapped.forEach { look ->
                    val p = companionPaletteFrom(publishedLookPalette(colors, look, accent != null))
                    val where = "$look on ${base.isDark} with $accent"
                    assertTrue(where, p.textPrimary.contrastRatio(p.surface) >= LOOK_TEXT_CONTRAST)
                    assertTrue(where, p.textSecondary.contrastRatio(p.surface) >= LOOK_TEXT_CONTRAST)
                    assertTrue(where, p.textTertiary.contrastRatio(p.surface) >= LOOK_LINE_CONTRAST)
                    assertTrue(where, p.accent.contrastRatio(p.surface) >= LOOK_LINE_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `the LED accent wins over a mapped look's accent`() {
        val led = 0xFF00C8FF.toInt()
        mapped.forEach { look ->
            val colors = publishedPalette(4, true, led, SharedLook(look, null))
            assertEquals("$look", Color(led), colors.accent)
        }
    }

    @Test
    fun `a saved look's pins skip the built-in mapping`() {
        val pinned = PinnedColors(background = Color(0xFF102030), accent = Color(0xFF40E0D0))
        val saved = CustomLook(id = "saved", name = "saved", baseLook = TccUiLook.TERMINAL, colors = pinned)
        val prefs = prefsOf(
            CustomLookStore.LOOK_KEY to saved.ref.key,
            CustomLookStore.KEY to CustomLookJson.encodeList(listOf(saved)),
        )
        val look = ActiveSharedLook().of(prefs)
        assertNull(look.builtIn)
        assertEquals(
            sharedThemeRow(resolveLookPalette(4, true, null, pinned)),
            sharedThemeRow(publishedPalette(4, true, null, look)),
        )
    }

    @Test
    fun `the LED accent still wins over a saved look's pinned accent`() {
        val led = 0xFF00C8FF.toInt()
        val look = SharedLook(null, PinnedColors(accent = Color(0xFF40E0D0)))
        assertEquals(Color(led), publishedPalette(4, true, led, look).accent)
    }

    @Test
    fun `a deleted saved look publishes the default look`() {
        val prefs = prefsOf(CustomLookStore.LOOK_KEY to "custom:gone")
        assertEquals(SharedLook(TccUiLook.DEFAULT, null), ActiveSharedLook().of(prefs))
    }

    @Test
    fun `a built-in look ignores Theme Studio's draft`() {
        val draft = CustomLook(id = "draft", name = "draft", colors = PinnedColors(background = Color(0xFFFF0000)))
        val prefs = prefsOf(
            CustomLookStore.LOOK_KEY to TccUiLook.TERMINAL.name,
            "studio_draft" to CustomLookJson.encode(draft).toString(),
        )
        assertEquals(SharedLook(TccUiLook.TERMINAL, null), ActiveSharedLook().of(prefs))
    }

    @Test
    fun `changing the built-in look is picked up right away`() {
        val values = mutableMapOf(CustomLookStore.LOOK_KEY to TccUiLook.NEON_ARCADE.name)
        val prefs = prefsBackedBy(values)
        val active = ActiveSharedLook()
        val neon = sharedThemeRow(publishedPalette(4, true, null, active.of(prefs)))
        values[CustomLookStore.LOOK_KEY] = TccUiLook.TERMINAL.name
        val terminal = sharedThemeRow(publishedPalette(4, true, null, active.of(prefs)))
        assertNotEquals(neon, terminal)
    }

    private fun Color.argb() = copy(alpha = 1f).toArgb()

    private fun prefsOf(vararg values: Pair<String, String>) = prefsBackedBy(values.toMap())

    private fun prefsBackedBy(values: Map<String, String>): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        check(method.name == "getString") { "unexpected call: ${method.name}" }
        values[args[0] as String] ?: args[1]
    } as SharedPreferences
}
