package jr.brian.commandpannel.privileged

import java.io.File

/**
 * Reads the string literals passed to `shell(…)` / `privileged.shell(…)` / `VendorRootShell.exec(…)` in Kotlin source
 * (including literals joined with `+` across lines) and reports every `$…` that isn't quoted, by the rules in
 * [RootShell]: allowed are `RootShell.q(…)` / `RootShell.command(…)`, an UPPER_CASE constant (`$STAGED`,
 * `${LookPackJson.EXTENSION}`) and a `…Command(…)` builder. Anything else should go through `privileged.run(…)`.
 *
 * A command built in a variable or by a function isn't a literal here, so it isn't checked; build those with
 * [RootShell.q] / [RootShell.command] too.
 */
internal object ShellLiteralGuard {
    data class Violation(val file: String, val line: Int, val expression: String) {
        override fun toString() = "$file:$line: \${$expression}"
    }

    private val call = Regex("""(?<![\w])(shell|VendorRootShell\.exec)\(""")
    private val allowed = listOf(
        Regex("""^([a-z]\w*\.)*RootShell\.(q|command)\("""),
        Regex("""^([A-Za-z_]\w*\.)*[A-Z][A-Z0-9_]*$"""),
        Regex("""^([A-Za-z_]\w*\.)*\w+Command\("""),
    )
    private val identifier = Regex("""[A-Za-z_]\w*""")

    fun scan(root: File): List<Violation> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .flatMap { scan(it.path, it.readText()) }
        .toList()

    fun scan(name: String, text: String): List<Violation> {
        val out = mutableListOf<Violation>()
        for (m in call.findAll(text)) {
            if (text.regionMatches(maxOf(0, m.range.first - 4), "fun ", 0, 4)) continue // the definition itself
            var i = m.range.last + 1
            while (true) {
                i = skipSpace(text, i)
                if (i >= text.length || text[i] != '"' || text.startsWith("\"\"\"", i)) break
                val (templates, end) = templates(text, i)
                templates.filterNot { (e, _) -> allowed.any { it.containsMatchIn(e) } }
                    .forEach { (e, pos) -> out += Violation(name, lineOf(text, pos), e) }
                i = end
                val next = skipSpace(text, i)
                if (next < text.length && text[next] == '+') i = next + 1 else break
            }
        }
        return out
    }

    private fun skipSpace(t: String, from: Int): Int {
        var i = from
        while (i < t.length && t[i].isWhitespace()) i++
        return i
    }

    private fun lineOf(t: String, pos: Int) = t.substring(0, pos).count { it == '\n' } + 1

    /** The templates in the literal starting at [start] (`"`), with their positions, and the index after it. */
    private fun templates(t: String, start: Int): Pair<List<Pair<String, Int>>, Int> {
        val found = mutableListOf<Pair<String, Int>>()
        var i = start + 1
        while (i < t.length) {
            when {
                t[i] == '\\' -> i += 2
                t[i] == '"' -> return found to i + 1
                t[i] == '$' && i + 1 < t.length && t[i + 1] == '{' -> {
                    val end = skipBraces(t, i + 1)
                    found += t.substring(i + 2, end - 1).trim() to i
                    i = end
                }
                t[i] == '$' -> {
                    val id = identifier.matchAt(t, i + 1)
                    if (id != null) {
                        found += id.value to i
                        i = id.range.last + 1
                    } else {
                        i++
                    }
                }
                else -> i++
            }
        }
        return found to i
    }

    /** From a `{` at [start], the index after its matching `}` (skipping nested strings). */
    private fun skipBraces(t: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < t.length) {
            when (t[i]) {
                '"' -> { i = skipString(t, i); continue }
                '{' -> depth++
                '}' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return i
    }

    /** From a `"` at [start], the index after the closing one (skipping escapes and templates). */
    private fun skipString(t: String, start: Int): Int {
        var i = start + 1
        while (i < t.length) {
            when {
                t[i] == '\\' -> i += 2
                t[i] == '$' && i + 1 < t.length && t[i + 1] == '{' -> i = skipBraces(t, i + 1)
                t[i] == '"' -> return i + 1
                else -> i++
            }
        }
        return i
    }
}
