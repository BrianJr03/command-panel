package jr.brian.commandpannel.system

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import jr.brian.commandpannel.ui.settings.companionPaletteFrom
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.PinnedColors
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class SharedThemeTest {
    @Test
    fun `the contract's values never change`() {
        assertEquals(1, SharedThemeContract.VERSION)
        assertEquals(".theme", SharedThemeContract.AUTHORITY_SUFFIX)
        assertEquals("palette", SharedThemeContract.PATH)
        assertEquals("jr.brian.commandpannel", SharedThemeContract.RELEASE_PACKAGE)
        assertEquals("jr.brian.commandpannel.debug", SharedThemeContract.DEBUG_PACKAGE)
        assertEquals(
            jr.brian.commandpannel.plugin.PanelPluginContract.COMMAND_PANEL_CERT_SHA256,
            SharedThemeContract.RELEASE_CERT_SHA256,
        )
        assertEquals(
            listOf(
                "version", "is_dark", "bg", "rail", "surface", "surface_high", "border", "accent", "accent_soft",
                "accent_focused", "on_accent", "text_primary", "text_secondary", "text_tertiary", "danger", "success",
            ),
            SharedThemeContract.COLUMNS,
        )
    }

    @Test
    fun `the row has exactly the contract's columns`() {
        assertEquals(SharedThemeContract.COLUMNS.toSet(), sharedThemeRow(TccColors.PureBlack).keys)
    }

    @Test
    fun `the row matches what the companion shows`() {
        listOf(TccColors.PureBlack, TccColors.Light).forEach { colors ->
            val palette = companionPaletteFrom(colors)
            val row = sharedThemeRow(colors)
            assertEquals(palette.bg.toArgb(), row[SharedThemeContract.COLUMN_BG])
            assertEquals(palette.surfaceHigh.toArgb(), row[SharedThemeContract.COLUMN_SURFACE_HIGH])
            assertEquals(palette.accentSoft.toArgb(), row[SharedThemeContract.COLUMN_ACCENT_SOFT])
            assertEquals(palette.textTertiary.toArgb(), row[SharedThemeContract.COLUMN_TEXT_TERTIARY])
            assertEquals(palette.success.toArgb(), row[SharedThemeContract.COLUMN_SUCCESS])
            assertEquals(if (colors.isDark) 1 else 0, row[SharedThemeContract.COLUMN_IS_DARK])
        }
    }

    @Test
    fun `pinned colors and the LED accent reach the row`() {
        val pinned = PinnedColors(background = Color(0xFF102030))
        val led = 0xFF00C8FF.toInt()
        val colors = resolveLookPalette(themeMode = 4, nightActive = true, accentOverride = led, pinned = pinned)
        val row = sharedThemeRow(colors)
        assertEquals(Color(0xFF102030).toArgb(), row[SharedThemeContract.COLUMN_BG])
        assertEquals(companionPaletteFrom(colors).accent.toArgb(), row[SharedThemeContract.COLUMN_ACCENT])
    }

    @Test
    fun `a projection returns its columns in order`() {
        val row = sharedThemeRow(TccColors.PureBlack)
        val values = selectSharedThemeColumns(row, arrayOf("accent", "version"))
        assertArrayEquals(arrayOf<Any>(row.getValue("accent"), 1), values)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown column is refused`() {
        selectSharedThemeColumns(sharedThemeRow(TccColors.PureBlack), arrayOf("secret"))
    }

    @Test
    fun `the applied custom look's colors are published`() {
        val prefs = prefsOf(CustomLookStore.LOOK_KEY to saved.ref.key, CustomLookStore.KEY to savedJson)
        assertEquals(saved.colors, ActiveSharedLook().of(prefs).pinned)
    }

    @Test
    fun `Theme Studio's unsaved draft is never published`() {
        val draft = CustomLook(id = "draft", name = "draft", colors = PinnedColors(background = Color(0xFFFF0000)))
        val draftJson = CustomLookJson.encode(draft).toString()
        val onBuiltIn = prefsOf(STUDIO_DRAFT_KEY to draftJson)
        val onSaved = prefsOf(
            CustomLookStore.LOOK_KEY to saved.ref.key,
            CustomLookStore.KEY to savedJson,
            STUDIO_DRAFT_KEY to draftJson,
        )
        assertNull(ActiveSharedLook().of(onBuiltIn).pinned)
        assertEquals(saved.colors, ActiveSharedLook().of(onSaved).pinned)
    }

    @Test
    fun `a deleted look publishes no pinned colors`() {
        val prefs = prefsOf(CustomLookStore.LOOK_KEY to LookRef.Custom("gone").key, CustomLookStore.KEY to savedJson)
        assertNull(ActiveSharedLook().of(prefs).pinned)
    }

    @Test
    fun `switching looks is picked up right away`() {
        val values = mutableMapOf(CustomLookStore.LOOK_KEY to saved.ref.key, CustomLookStore.KEY to savedJson)
        val prefs = prefsBackedBy(values)
        val active = ActiveSharedLook()
        assertEquals(saved.colors, active.of(prefs).pinned)
        values.remove(CustomLookStore.LOOK_KEY)
        assertNull(active.of(prefs).pinned)
    }

    private val saved = CustomLook(id = "saved", name = "saved", colors = PinnedColors(background = Color(0xFF102030)))
    private val savedJson = CustomLookJson.encodeList(listOf(saved))

    private fun prefsOf(vararg values: Pair<String, String>) = prefsBackedBy(values.toMap())

    private fun prefsBackedBy(values: Map<String, String>): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        check(method.name == "getString") { "unexpected call: ${method.name}" }
        values[args[0] as String] ?: args[1]
    } as SharedPreferences

    private companion object {
        const val STUDIO_DRAFT_KEY = "studio_draft"
    }
}
