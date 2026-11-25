package jr.brian.commandpannel.system

import jr.brian.commandpannel.ui.theme.BundledFont
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.FontRef
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.TccUiLook
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SharedFontsTest {
    @Test
    fun `the contract's values never change`() {
        assertEquals(2, SharedFontsContract.VERSION)
        assertEquals("fonts", SharedFontsContract.PATH)
        assertEquals("fonts", SharedFontsContract.FILE_PATH)
        assertEquals(
            listOf(
                "version", "look_id", "look_name", "display_font", "display_font_sha256", "label_font", "label_font_sha256",
            ),
            SharedFontsContract.COLUMNS,
        )
        assertEquals("bundled:", SharedFontsContract.PREFIX_BUNDLED)
        assertEquals("user:", SharedFontsContract.PREFIX_USER)
        assertEquals(
            listOf("condensed", "condensed_display", "mono", "segment", "rounded", "stencil", "comic", "chrome_wide", "serif"),
            SharedFontsContract.BUNDLED_KEYS,
        )
    }

    @Test
    fun `every bundled family has its own key`() {
        val keys = BundledFont.entries.filter { it != BundledFont.DEFAULT }.map { sharedBundledKey(it) }
        assertEquals(SharedFontsContract.BUNDLED_KEYS, keys)
        assertNull(sharedBundledKey(BundledFont.DEFAULT))
    }

    @Test
    fun `the row has exactly the contract's columns`() {
        assertEquals(SharedFontsContract.COLUMNS.toSet(), rowFor(SharedLook(TccUiLook.TERMINAL, null)).keys)
    }

    @Test
    fun `a built-in look publishes its bundled fonts`() {
        val row = rowFor(SharedLook(TccUiLook.SEGMENT_LCD, null))
        assertEquals(2, row[SharedFontsContract.COLUMN_VERSION])
        assertEquals("SEGMENT_LCD", row[SharedFontsContract.COLUMN_LOOK_ID])
        assertEquals("name of SEGMENT_LCD", row[SharedFontsContract.COLUMN_LOOK_NAME])
        assertEquals("bundled:segment", row[SharedFontsContract.COLUMN_DISPLAY_FONT])
        assertEquals("bundled:mono", row[SharedFontsContract.COLUMN_LABEL_FONT])
        assertNull(row[SharedFontsContract.COLUMN_DISPLAY_FONT_SHA256])
        assertNull(row[SharedFontsContract.COLUMN_LABEL_FONT_SHA256])
    }

    @Test
    fun `a look in the system font publishes null fonts`() {
        val row = rowFor(SharedLook(TccUiLook.AURORA_GLASS, null))
        assertNull(row[SharedFontsContract.COLUMN_DISPLAY_FONT])
        assertNull(row[SharedFontsContract.COLUMN_LABEL_FONT])
    }

    @Test
    fun `a missing display font falls back to the label font`() {
        val row = rowFor(SharedLook(TccUiLook.E_INK, null))
        assertEquals("bundled:serif", row[SharedFontsContract.COLUMN_DISPLAY_FONT])
        assertEquals("bundled:serif", row[SharedFontsContract.COLUMN_LABEL_FONT])
    }

    @Test
    fun `a saved look's imported font is published with its digest`() {
        val look = CustomLook(id = "mine", name = "Mine", baseLook = TccUiLook.TERMINAL, labelFont = userFont(FONT_ID))
        val row = rowFor(SharedLook(null, look.colors, look))
        assertEquals("custom:mine", row[SharedFontsContract.COLUMN_LOOK_ID])
        assertEquals("Mine", row[SharedFontsContract.COLUMN_LOOK_NAME])
        assertEquals("user:$FONT_ID", row[SharedFontsContract.COLUMN_LABEL_FONT])
        assertEquals(FONT_SHA, row[SharedFontsContract.COLUMN_LABEL_FONT_SHA256])
        assertEquals("bundled:mono", row[SharedFontsContract.COLUMN_DISPLAY_FONT])
    }

    @Test
    fun `an imported font whose file is gone publishes the fallback`() {
        val look = CustomLook(
            id = "mine", name = "mine", baseLook = TccUiLook.TERMINAL,
            labelFont = userFont(FONT_ID), displayFont = userFont("00000000-dead"),
        )
        val row = rowFor(SharedLook(null, look.colors, look))
        assertEquals("user:$FONT_ID", row[SharedFontsContract.COLUMN_DISPLAY_FONT])
        assertEquals(FONT_SHA, row[SharedFontsContract.COLUMN_DISPLAY_FONT_SHA256])
    }

    @Test
    fun `a saved look can pick the system font over its base look's`() {
        val look = CustomLook(
            id = "plain", name = "plain", baseLook = TccUiLook.E_INK, labelFont = FontRef.Bundled(BundledFont.DEFAULT),
        )
        val row = rowFor(SharedLook(null, look.colors, look))
        assertNull(row[SharedFontsContract.COLUMN_LABEL_FONT])
        assertEquals("bundled:serif", row[SharedFontsContract.COLUMN_DISPLAY_FONT])
    }

    @Test
    fun `the applied saved look is read from prefs`() {
        val look = CustomLook(id = "mine", name = "mine", labelFont = userFont(FONT_ID))
        val prefs = prefsOf(CustomLookStore.LOOK_KEY to look.ref.key, CustomLookStore.KEY to CustomLookJson.encodeList(listOf(look)))
        val row = rowFor(ActiveSharedLook().of(prefs))
        assertEquals("user:$FONT_ID", row[SharedFontsContract.COLUMN_LABEL_FONT])
    }

    @Test
    fun `an unknown look id publishes the default look`() {
        val prefs = prefsOf(CustomLookStore.LOOK_KEY to LookRef.Custom("gone").key)
        val row = rowFor(ActiveSharedLook().of(prefs))
        assertEquals(TccUiLook.DEFAULT.name, row[SharedFontsContract.COLUMN_LOOK_ID])
        assertEquals("name of ${TccUiLook.DEFAULT.name}", row[SharedFontsContract.COLUMN_LOOK_NAME])
        assertNull(row[SharedFontsContract.COLUMN_LABEL_FONT])
    }

    @Test
    fun `a projection returns its columns in order, nulls included`() {
        val row = rowFor(SharedLook(TccUiLook.AURORA_GLASS, null))
        val values = selectSharedColumns(row, arrayOf("label_font", "version"))
        assertArrayEquals(arrayOf<Any?>(null, 2), values)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown column is refused`() {
        selectSharedColumns(rowFor(SharedLook(TccUiLook.TERMINAL, null)), arrayOf("secret"))
    }

    @Test
    fun `a font file path names one imported font id`() {
        assertEquals(FONT_ID, sharedFontFileId(listOf("fonts", FONT_ID)))
        assertNull(sharedFontFileId(listOf("fonts")))
        assertNull(sharedFontFileId(listOf("palette", FONT_ID)))
    }

    @Test
    fun `path traversal is refused`() {
        listOf(
            listOf("fonts", ".."),
            listOf("fonts", "../../shared_prefs/prefs.xml"),
            listOf("fonts", "..%2F..%2Fshared_prefs"),
            listOf("fonts", "$FONT_ID.ttf"),
            listOf("fonts", "a/b"),
            listOf("fonts", ""),
            listOf("fonts", "..", FONT_ID),
            listOf("fonts", FONT_ID, ".."),
        ).forEach { assertNull(it.toString(), sharedFontFileId(it)) }
    }

    @Test
    fun `only Jarngreipr signed with its pinned key may read font files`() {
        val release = SharedFontReaders.allowed(debugBuild = false)
        val jarn = SharedFontReaders.JARN_PACKAGE
        val pin = SharedFontReaders.JARN_RELEASE_CERT_SHA256
        assertTrue(SharedFontReaders.isTrusted(listOf(jarn), release) { listOf(pin) })
        assertTrue(SharedFontReaders.isTrusted(listOf("other", jarn), release) { if (it == jarn) listOf(pin) else emptyList() })
    }

    @Test
    fun `an untrusted caller can't read font files`() {
        val release = SharedFontReaders.allowed(debugBuild = false)
        val jarn = SharedFontReaders.JARN_PACKAGE
        val pin = SharedFontReaders.JARN_RELEASE_CERT_SHA256
        val other = "0".repeat(64)
        assertFalse(SharedFontReaders.isTrusted(emptyList(), release) { listOf(pin) })
        assertFalse(SharedFontReaders.isTrusted(listOf("com.evil"), release) { listOf(pin) })
        assertFalse(SharedFontReaders.isTrusted(listOf(jarn), release) { listOf(other) })
        assertFalse(SharedFontReaders.isTrusted(listOf(jarn), release) { listOf(pin, other) })
        assertFalse(SharedFontReaders.isTrusted(listOf(jarn), release) { emptyList() })
        assertFalse(
            SharedFontReaders.isTrusted(listOf(SharedFontReaders.JARN_DEBUG_PACKAGE), release) {
                listOf(SharedFontReaders.JARN_DEBUG_CERT_SHA256)
            },
        )
    }

    @Test
    fun `a debug build also serves Jarngreipr's debug build`() {
        val debug = SharedFontReaders.allowed(debugBuild = true)
        assertTrue(
            SharedFontReaders.isTrusted(listOf(SharedFontReaders.JARN_DEBUG_PACKAGE), debug) {
                listOf(SharedFontReaders.JARN_DEBUG_CERT_SHA256)
            },
        )
        assertFalse(
            SharedFontReaders.isTrusted(listOf(SharedFontReaders.JARN_DEBUG_PACKAGE), debug) {
                listOf(SharedFontReaders.JARN_RELEASE_CERT_SHA256)
            },
        )
    }

    private fun rowFor(look: SharedLook) =
        sharedFontsRow(look, { "name of ${it.name}" }) { id -> FONT_SHA.takeIf { id == FONT_ID } }

    private fun userFont(id: String) = FontRef.User(id, "Orbitron")

    private fun prefsOf(vararg values: Pair<String, String>): android.content.SharedPreferences =
        Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java),
        ) { _, method, args ->
            check(method.name == "getString") { "unexpected call: ${method.name}" }
            values.toMap()[args[0] as String] ?: args[1]
        } as android.content.SharedPreferences

    private companion object {
        const val FONT_ID = "3f2b6c1e-8a4d-4e7a-9b1c-0d2e3f4a5b6c"
        val FONT_SHA = "ab".repeat(32)
    }
}
