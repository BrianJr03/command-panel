package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the small markdown subset patch notes use: `#`/`##`/`###` headings, `-`/`*` bullets (indented by two spaces
 * per level), `---` rules, and inline `**bold**`, `*italic*` and `` `code` ``. Modeled on the Home / ThorHUD updaters.
 */
@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))
                trimmed == "---" || trimmed == "***" ->
                    Spacer(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(p.border))
                trimmed.startsWith("### ") -> Heading(trimmed.removePrefix("### "), 15)
                trimmed.startsWith("## ") -> Heading(trimmed.removePrefix("## "), 18)
                trimmed.startsWith("# ") -> Heading(trimmed.removePrefix("# "), 21)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val level = ((line.length - trimmed.length) / 2).coerceAtMost(3)
                    Row(Modifier.padding(start = (level * 16).dp)) {
                        Text(if (level == 0) "•" else "◦", color = p.accent, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(inline(trimmed.drop(2), p.accent), color = p.textPrimary, fontSize = 14.sp)
                    }
                }
                else -> Text(inline(trimmed, p.accent), color = p.textSecondary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Heading(text: String, size: Int) {
    Text(inline(text, p.accent), color = p.textPrimary, fontSize = size.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp))
}

/**
 * Inline `**bold**`, `*italic*` and `` `code` ``; anything unmatched is kept literally. (`_italic_` is left out so
 * snake_case names in notes aren't italicized.)
 */
internal fun inline(text: String, codeColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        fun span(open: String, close: String = open, style: SpanStyle): Boolean {
            if (!text.startsWith(open, i)) return false
            val end = text.indexOf(close, i + open.length)
            if (end <= i + open.length) return false
            withStyle(style) { append(text.substring(i + open.length, end)) }
            i = end + close.length
            return true
        }
        if (span("**", style = SpanStyle(fontWeight = FontWeight.Bold))) continue
        if (span("`", style = SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor))) continue
        if (span("*", style = SpanStyle(fontStyle = FontStyle.Italic))) continue
        append(text[i])
        i++
    }
}
