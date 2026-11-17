package jr.brian.commandpannel.system

import jr.brian.commandpannel.ui.theme.BundledFont
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.FontRef
import jr.brian.commandpannel.ui.theme.FontSource
import jr.brian.commandpannel.ui.theme.LookPack
import jr.brian.commandpannel.ui.theme.LookPackJson
import jr.brian.commandpannel.ui.theme.TccUiLook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackTest {

    private val neon = CustomLook(
        id = "neon-term", name = "Neon Term", baseLook = TccUiLook.TERMINAL, card = CardStyle.NEON_TUBE,
        labelFont = FontRef.User("local-id", "Orbitron", "Orbitron.ttf"), displayFont = FontRef.Bundled(BundledFont.MONO),
    )
    private val plain = CustomLook(id = "plain", name = "Plain", baseLook = TccUiLook.BLOOM)

    @Test
    fun `pack round trip keeps looks, fonts and metadata`() {
        val pack = LookPack(
            name = "Neon Pack", looks = listOf(neon, plain), author = "Brian", description = "Two looks",
            fonts = mapOf("Orbitron.ttf" to FontSource("https://example.com/Orbitron.ttf", "OFL")),
        )
        val decoded = LookPackJson.decode(LookPackJson.encode(pack))!!
        assertEquals(pack.name, decoded.name)
        assertEquals(pack.author, decoded.author)
        assertEquals(pack.fonts, decoded.fonts)
        assertEquals(listOf("neon-term", "plain"), decoded.looks.map { it.id })
        assertEquals("Orbitron.ttf", (decoded.looks[0].labelFont as FontRef.User).fileName)
        assertEquals(setOf("Orbitron.ttf"), LookPackJson.fontFiles(decoded.looks))
    }

    @Test
    fun `a single legacy look imports as a one-look pack`() {
        val legacy = CustomLookJson.encode(plain).toString()
        val pack = LookPackJson.decode(legacy)!!
        assertEquals("Plain", pack.name)
        assertEquals(listOf(plain), pack.looks)
    }

    @Test
    fun `old user font refs without a file name still read`() {
        val o = CustomLookJson.encode(plain).put("labelFont", JSONObject().put("user", "Orbitron").put("id", "x"))
        val ref = CustomLookJson.decode(o)!!.labelFont as FontRef.User
        assertEquals("Orbitron.ttf", ref.fileName)
    }

    @Test
    fun `hand-written entries get ids, and http font urls are dropped`() {
        val json = """{"format":"tcp-looks","version":1,"name":"P",
            "fonts":{"A.ttf":{"url":"http://insecure/A.ttf"}},
            "looks":[{"name":"My Cool Look","baseLook":"JELLY"}]}"""
        val pack = LookPackJson.decode(json)!!
        assertEquals("my-cool-look", pack.looks.single().id)
        assertTrue(pack.fonts.isEmpty())
    }

    @Test
    fun `source links parse github repos, branches and direct files`() {
        val repo = ThemeSources.parse("https://github.com/Brian/tcp-themes")!!
        assertEquals("gh:brian/tcp-themes", repo.id)
        assertEquals("Brian", repo.owner)
        assertNull(repo.branch)
        assertEquals("dev", ThemeSources.parse("https://github.com/Brian/tcp-themes/tree/dev")!!.branch)
        assertEquals("tcp-themes", ThemeSources.parse("https://github.com/Brian/tcp-themes.git")!!.repo)
        val file = ThemeSources.parse("https://example.com/packs/neon.tcplooks.json")!!
        assertEquals("neon", file.name)
        assertNull(file.owner)
        assertNull(ThemeSources.parse("http://github.com/Brian/tcp-themes"))
        assertNull(ThemeSources.parse("https://example.com/readme.md"))
    }

    @Test
    fun `sync downloads only new or changed packs and reports removed ones`() {
        val local = mapOf(
            "themes/a.tcplooks.json" to SyncedFile("1", listOf("x")),
            "themes/b.tcplooks.json" to SyncedFile("2", listOf("y")),
            "themes/gone.tcplooks.json" to SyncedFile("3", listOf("z")),
        )
        val remote = mapOf("themes/a.tcplooks.json" to "1", "themes/b.tcplooks.json" to "9", "themes/new.tcplooks.json" to "4")
        val (changed, removed) = ThemeSources.diff(local, remote)
        assertEquals(listOf("themes/b.tcplooks.json", "themes/new.tcplooks.json"), changed)
        assertEquals(listOf("themes/gone.tcplooks.json"), removed)
    }

    @Test
    fun `pack file names are filesystem safe`() {
        assertEquals("My_Looks_ v2.tcplooks.json", LookPackJson.fileName("My/Looks: v2"))
        assertEquals("evil_x.ttf", ThemeFolder.safeName("../../evil_x.ttf").substringAfterLast('/'))
    }

    @Test
    fun `the theme template's example pack parses completely`() {
        val text = java.io.File("../theme-template/themes/example.tcplooks.json").readText()
        val pack = LookPackJson.decode(text)!!
        assertEquals(listOf("glass-terminal", "orbit-hud", "soft-block"), pack.looks.map { it.id })
        assertEquals(setOf("Orbitron.ttf"), pack.fonts.keys)
        val block = pack.looks.last()
        assertEquals(CardStyle.CUSTOM, block.card)
        assertEquals(jr.brian.commandpannel.ui.theme.GaugeKind.ARC, block.custom.gauge.kind)
        assertEquals(jr.brian.commandpannel.ui.theme.NavIndicator.BLOCK, block.custom.nav.indicator)
    }
}
