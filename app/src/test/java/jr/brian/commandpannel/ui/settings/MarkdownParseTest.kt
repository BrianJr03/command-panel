package jr.brian.commandpannel.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkdownParseTest {
    private val notes = File("src/main/res/raw/patch_notes.md").readText()
    private val blocks = parseMarkdown(notes)

    /** Every line of text the dialog shows, without markup. */
    private fun shownText(blocks: List<MdBlock>): List<String> = blocks.flatMap { b ->
        when (b) {
            is MdBlock.Heading -> listOf(b.spans.plain())
            is MdBlock.Bullet -> listOf(b.spans.plain())
            is MdBlock.Paragraph -> listOf(b.spans.plain())
            is MdBlock.Quote -> b.lines.map { it.plain() }
            else -> emptyList()
        }
    }

    @Test
    fun `the real patch notes show no raw markup`() {
        for (line in shownText(blocks)) {
            assertFalse(line, line.contains("]("))
            assertFalse(line, line.contains("**"))
            assertFalse(line, line.contains('`'))
            assertFalse(line, line.startsWith(">"))
            assertFalse(line, line.startsWith("#"))
        }
    }

    @Test
    fun `the previous release link is link text with its url`() {
        val para = blocks.filterIsInstance<MdBlock.Paragraph>().first { it.spans.plain().startsWith("See the ") }
        val link = para.spans.single { it.url != null }
        assertTrue(link.text.matches(Regex("v\\d+\\.\\d+\\.\\d+ notes")))
        assertTrue(link.url!!.startsWith("https://github.com/BrianJr03/command-panel/releases/tag/v"))
        assertEquals(0, link.linkIndex)
        assertEquals(1, para.links().size)
        assertTrue(para.spans.plain().endsWith(" for the previous release."))
    }

    @Test
    fun `the TCP SC note is a quote with bold and code inside`() {
        val quote = blocks.filterIsInstance<MdBlock.Quote>().single()
        val spans = quote.lines.single()
        assertTrue(spans.plain().startsWith("About tcp-sc-"))
        val code = spans.single { it.code }
        assertTrue(code.text.matches(Regex("tcp-sc-\\d+\\.\\d+\\.\\d+\\.apk")))
        assertTrue("code inside bold stays bold", code.bold)
        assertEquals("About ", spans.first().text)
        assertTrue(spans.first().bold)
        assertTrue(spans.any { it.bold && it.text == ":" })
        assertTrue(spans.last().text.endsWith("install it after Command Panel."))
        assertFalse(spans.last().bold)
    }

    @Test
    fun `headings, bullets and the rule keep working`() {
        assertTrue(blocks.first() is MdBlock.Heading)
        assertEquals(2, (blocks.first() as MdBlock.Heading).level)
        assertTrue(blocks.any { it is MdBlock.Heading && it.level == 3 && it.spans.plain() == "New" })
        val bullet = blocks.filterIsInstance<MdBlock.Bullet>().first()
        assertTrue(bullet.spans.first().bold)
        assertTrue(blocks.contains(MdBlock.Rule))
        val footer = blocks.filterIsInstance<MdBlock.Paragraph>().last()
        assertTrue(footer.spans.all { it.italic })
    }

    @Test
    fun `brackets without a url stay literal`() {
        assertEquals("[not a link] and [x] (y)", parseInline("[not a link] and [x] (y)").plain())
        assertTrue(parseInline("[not a link] and [x] (y)").none { it.url != null })
        assertEquals("[open", parseInline("[open").plain())
        assertEquals("[label](", parseInline("[label](").plain())
    }

    @Test
    fun `a url with parentheses keeps them`() {
        val spans = parseInline("see [Thing](https://en.wikipedia.org/wiki/Thing_(disambiguation)) now")
        val link = spans.single { it.url != null }
        assertEquals("Thing", link.text)
        assertEquals("https://en.wikipedia.org/wiki/Thing_(disambiguation)", link.url)
        assertEquals("see Thing now", spans.plain())
    }

    @Test
    fun `non web links show only their label`() {
        val spans = parseInline("read [the docs](docs/README.md)")
        assertEquals("read the docs", spans.plain())
        assertTrue(spans.none { it.url != null })
    }

    @Test
    fun `nested bold and links with styled labels`() {
        val nested = parseInline("**bold *and italic* here**")
        assertEquals("bold and italic here", nested.plain())
        assertTrue(nested.all { it.bold })
        assertTrue(nested.single { it.italic }.text == "and italic")

        val doubled = parseInline("**outer **inner** text**")
        assertFalse(doubled.plain().contains("*"))

        val styled = parseInline("[**v1** notes](https://x.y/z) and [two](https://a.b)")
        assertEquals("v1 notes and two", styled.plain())
        assertEquals(listOf(0, 0, 1), styled.filter { it.url != null }.map { it.linkIndex })
        assertTrue(styled.first().bold && styled.first().url == "https://x.y/z")
    }

    @Test
    fun `links are numbered across the document and consecutive quote lines group`() {
        val doc = parseMarkdown("- [a](https://a.io)\n\n> one [b](https://b.io)\n> two\n\ntext [c](https://c.io)")
        val indices = doc.flatMap { b -> b.links().map { it.first().linkIndex } }
        assertEquals(listOf(0, 1, 2), indices)
        val quote = doc.filterIsInstance<MdBlock.Quote>().single()
        assertEquals(listOf("one b", "two"), quote.lines.map { it.plain() })
    }

    @Test
    fun `inline code is literal and loses its backticks`() {
        val spans = parseInline("run `**not bold**` now")
        assertEquals("run **not bold** now", spans.plain())
        assertTrue(spans.single { it.code }.let { !it.bold && it.text == "**not bold**" })
    }
}
