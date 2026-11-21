package jr.brian.commandpannel.ui.settings

/**
 * A run of inline text with its styles. [url] is set for link text; every span of one link shares its [linkIndex]
 * (0, 1, … in reading order across the whole document), which the dialog uses to move focus between links.
 */
internal data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val url: String? = null,
    val linkIndex: Int = -1,
)

/** One line-level block of the patch notes subset [parseMarkdown] understands. */
internal sealed interface MdBlock {
    data object Blank : MdBlock
    data object Rule : MdBlock
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Bullet(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock

    /** Consecutive `>` lines, the `>` dropped; each entry is one line. */
    data class Quote(val lines: List<List<MdSpan>>) : MdBlock
}

/** This block's links in reading order, each as the spans of its label (they share one url and linkIndex). */
internal fun MdBlock.links(): List<List<MdSpan>> {
    val all = when (this) {
        is MdBlock.Heading -> spans
        is MdBlock.Bullet -> spans
        is MdBlock.Paragraph -> spans
        is MdBlock.Quote -> lines.flatten()
        else -> emptyList()
    }
    return all.filter { it.url != null }.groupBy { it.linkIndex }.values.toList()
}

/** Plain text of [spans], as the dialog shows it (no markup). */
internal fun List<MdSpan>.plain(): String = joinToString("") { it.text }

/**
 * Parses the small markdown subset patch notes use: `#`/`##`/`###` headings, `-`/`*` bullets (indented by two spaces
 * per level), `---` rules, `>` quotes, and inline `**bold**`, `*italic*`, `` `code` `` and `[text](url)` links. Pure,
 * so it's unit-tested on the real patch notes. Anything it can't match is kept as literal text.
 */
internal fun parseMarkdown(markdown: String): List<MdBlock> {
    val counter = intArrayOf(0)
    val blocks = mutableListOf<MdBlock>()
    for (raw in markdown.lines()) {
        val line = raw.trimEnd()
        val trimmed = line.trimStart()
        val block = when {
            trimmed.isEmpty() -> MdBlock.Blank
            trimmed == "---" || trimmed == "***" -> MdBlock.Rule
            trimmed.startsWith(">") -> {
                val spans = parseInline(trimmed.removePrefix(">").removePrefix(" "), counter)
                val last = blocks.lastOrNull()
                if (last is MdBlock.Quote) {
                    blocks[blocks.size - 1] = MdBlock.Quote(last.lines + listOf(spans))
                    continue
                }
                MdBlock.Quote(listOf(spans))
            }
            trimmed.startsWith("### ") -> MdBlock.Heading(3, parseInline(trimmed.removePrefix("### "), counter))
            trimmed.startsWith("## ") -> MdBlock.Heading(2, parseInline(trimmed.removePrefix("## "), counter))
            trimmed.startsWith("# ") -> MdBlock.Heading(1, parseInline(trimmed.removePrefix("# "), counter))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                MdBlock.Bullet(((line.length - trimmed.length) / 2).coerceAtMost(3), parseInline(trimmed.drop(2), counter))
            else -> MdBlock.Paragraph(parseInline(trimmed, counter))
        }
        blocks += block
    }
    return blocks
}

/** Inline markup of [text] as spans, for tests and callers outside a document (links numbered from 0). */
internal fun parseInline(text: String): List<MdSpan> = parseInline(text, intArrayOf(0))

private fun parseInline(text: String, counter: IntArray): List<MdSpan> {
    val out = mutableListOf<MdSpan>()
    parseInto(text, MdSpan(""), counter, out)
    // Merge neighbors with the same style, so plain text is one span.
    return out.fold(mutableListOf<MdSpan>()) { acc, s ->
        val last = acc.lastOrNull()
        if (last != null && last.copy(text = "") == s.copy(text = "")) acc[acc.size - 1] = last.copy(text = last.text + s.text)
        else acc += s
        acc
    }.filter { it.text.isNotEmpty() }
}

/**
 * Appends [text] to [out] with [style] as the base. `**…**` and `*…*` nest (their inside is parsed again), `` `…` ``
 * is literal, and `[label](url)` makes a link of its parsed label; the URL may contain balanced parentheses. (`_italic_`
 * is left out so snake_case names in notes aren't italicized.)
 */
private fun parseInto(text: String, style: MdSpan, counter: IntArray, out: MutableList<MdSpan>) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) out += style.copy(text = plain.toString())
        plain.clear()
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                parseInto(text.substring(i + 2, end), style.copy(bold = true), counter, out)
                i = end + 2
                continue
            }
        } else if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                out += style.copy(text = text.substring(i + 1, end), code = true)
                i = end + 1
                continue
            }
        } else if (c == '*') {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1) {
                flush()
                parseInto(text.substring(i + 1, end), style.copy(italic = true), counter, out)
                i = end + 1
                continue
            }
        } else if (c == '[' && style.url == null) {
            val link = matchLink(text, i)
            if (link != null) {
                flush()
                // A link that can't open in a browser (relative, mailto, …) shows just its label.
                val linked = if (isOpenableUrl(link.url)) style.copy(url = link.url, linkIndex = counter[0]++) else style
                parseInto(link.label, linked, counter, out)
                i = link.end
                continue
            }
        }
        plain.append(c)
        i++
    }
    flush()
}

private class LinkMatch(val label: String, val url: String, val end: Int)

/** `[label](url)` starting at [start], with balanced brackets in the label and parentheses in the URL; or null. */
private fun matchLink(text: String, start: Int): LinkMatch? {
    var depth = 0
    var i = start
    var labelEnd = -1
    while (i < text.length) {
        when (text[i]) {
            '[' -> depth++
            ']' -> if (--depth == 0) { labelEnd = i; break }
        }
        i++
    }
    if (labelEnd < 0 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
    depth = 0
    i = labelEnd + 1
    while (i < text.length) {
        when (text[i]) {
            '(' -> depth++
            ')' -> if (--depth == 0) break
            ' ' -> return null
        }
        i++
    }
    if (i >= text.length) return null
    val url = text.substring(labelEnd + 2, i)
    val label = text.substring(start + 1, labelEnd)
    if (url.isEmpty() || label.isEmpty()) return null
    return LinkMatch(label, url, i + 1)
}

/** Only web links open; anything else stays plain text in the dialog. */
internal fun isOpenableUrl(url: String?): Boolean = url != null && (url.startsWith("https://") || url.startsWith("http://"))
