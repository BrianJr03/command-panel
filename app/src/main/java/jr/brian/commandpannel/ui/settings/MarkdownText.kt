package jr.brian.commandpannel.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.noRippleClickable

/**
 * The focusable links of one [MarkdownText] (#53), so its host (the update / What's new dialog) can move D-pad focus
 * between the links it can see while still scrolling with up / down. Links are numbered in reading order
 * ([MdSpan.linkIndex]); each reports its bounds, the notes' viewport reports its own.
 */
internal class MarkdownLinks {
    private class Entry(val requester: FocusRequester, var bounds: Rect? = null)

    private val entries = sortedMapOf<Int, Entry>()

    /** The notes' visible area, in root coordinates. */
    var viewport: Rect? = null

    /** The focused link's index, or null when focus is elsewhere (the dialog's buttons). */
    var focused: Int? by mutableStateOf(null)

    /** Called when the focused link scrolls out of view, so focus can go back to the dialog's main button. */
    var onFocusedLeftView: () -> Unit = {}

    internal fun requester(index: Int): FocusRequester = entries.getOrPut(index) { Entry(FocusRequester()) }.requester

    internal fun moved(index: Int, bounds: Rect) {
        entries[index]?.bounds = bounds
        if (focused == index && !visible(bounds)) onFocusedLeftView()
    }

    internal fun forget(index: Int) {
        entries.remove(index)
        if (focused == index) focused = null
    }

    /** Mostly on screen: its vertical center sits inside the viewport. */
    private fun visible(b: Rect?): Boolean {
        val vp = viewport ?: return false
        if (b == null) return false
        val c = b.center.y
        return c > vp.top && c < vp.bottom
    }

    /**
     * Where D-pad [down] / up goes from the current focus: the next visible link below or above the focused one; from
     * outside the links (the buttons), up enters the lowest visible link and down returns null (the notes scroll).
     */
    fun next(down: Boolean): FocusRequester? {
        val visible = entries.filter { visible(it.value.bounds) }
        val from = focused
        val pick = when {
            from == null -> if (down) null else visible.keys.maxOrNull()
            down -> visible.keys.filter { it > from }.minOrNull()
            else -> visible.keys.filter { it < from }.maxOrNull()
        }
        return pick?.let { entries[it]?.requester }
    }
}

/**
 * Renders the small markdown subset patch notes use ([parseMarkdown]): headings, bullets, `---` rules, `>` quotes
 * (an indented note with an accent bar), and inline bold, italic, `code` (monospace, no backticks) and `[text](url)`
 * links. A line with one link is a focusable row: A (or a tap) opens the link in the browser. A line with several
 * keeps its text and lists each link as its own focusable row below it. [links] lets the host move focus between
 * them (see [MarkdownLinks]); without it the links still work by touch and default focus search.
 * Modeled on the Home / ThorHUD updaters.
 */
@Composable
internal fun MarkdownText(markdown: String, modifier: Modifier = Modifier, links: MarkdownLinks? = null) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val registry = links ?: remember { MarkdownLinks() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                MdBlock.Blank -> Spacer(Modifier.height(4.dp))
                MdBlock.Rule -> Spacer(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(p.border))
                is MdBlock.Heading -> Box(Modifier.padding(top = 6.dp)) {
                    SpansLine(block.spans, registry, p.textPrimary, listOf(21, 18, 15)[block.level - 1], FontWeight.Bold)
                }
                is MdBlock.Bullet -> Row(Modifier.padding(start = (block.level * 16).dp)) {
                    Text(if (block.level == 0) "•" else "◦", color = p.accent, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    SpansLine(block.spans, registry, p.textPrimary, 14)
                }
                is MdBlock.Paragraph -> SpansLine(block.spans, registry, p.textSecondary, 14)
                is MdBlock.Quote -> Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp)
                        .clip(controlShape(8.dp)).background(p.accentSoft),
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(p.accent))
                    Column(
                        Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        block.lines.forEach { line ->
                            if (line.isEmpty()) Spacer(Modifier.height(2.dp))
                            else SpansLine(line, registry, p.textPrimary, 14)
                        }
                    }
                }
            }
        }
    }
}

/** One line of [spans]: plain text, a focusable link row (one link), or the text plus a row per link (several). */
@Composable
private fun SpansLine(spans: List<MdSpan>, links: MarkdownLinks, color: Color, size: Int, weight: FontWeight? = null) {
    val text = annotated(spans, p.accent)
    val lineLinks = spans.filter { it.url != null }.groupBy { it.linkIndex }
    when (lineLinks.size) {
        0 -> Text(text, color = color, fontSize = size.sp, fontWeight = weight)
        1 -> {
            val (index, label) = lineLinks.entries.first()
            LinkRow(index, label.first().url!!, links) { Text(text, color = color, fontSize = size.sp, fontWeight = weight) }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, color = color, fontSize = size.sp, fontWeight = weight)
            lineLinks.forEach { (index, label) ->
                LinkRow(index, label.first().url!!, links) {
                    Text(annotated(label, p.accent), color = color, fontSize = (size - 1).sp)
                }
            }
        }
    }
}

/** A focusable link: an accent ring while focused; A or a tap opens [url] in the browser. */
@Composable
private fun LinkRow(index: Int, url: String, links: MarkdownLinks, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val open = {
        val ok = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!ok) Toast.makeText(context, context.getString(R.string.msg_link_no_browser), Toast.LENGTH_SHORT).show()
    }
    val f = focusState()
    val shape = controlShape(8.dp)
    androidx.compose.runtime.DisposableEffect(index) { onDispose { links.forget(index) } }
    Box(
        Modifier
            .onGloballyPositioned { links.moved(index, it.boundsInRoot()) }
            .focusRequester(links.requester(index))
            // Above focusControl, so it sees the link's own focus target (placed after it, it watched the clickable's
            // target, which never takes focus, and the dialog never learned a link was focused).
            .onFocusChanged {
                if (it.hasFocus) links.focused = index else if (links.focused == index) links.focused = null
            }
            .clip(shape)
            .background(if (f.value) p.surfaceHigh else Color.Transparent)
            .then(if (f.value) Modifier.border(1.5.dp, p.accent, shape) else Modifier)
            .focusControl(f, open)
            .noRippleClickable(onClick = open)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) { content() }
}

/**
 * [spans] as styled text: bold, italic, `code` (monospace in [accent]) and links ([accent], underlined).
 */
internal fun annotated(spans: List<MdSpan>, accent: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            color = if (s.code || s.url != null) accent else Color.Unspecified,
            textDecoration = if (s.url != null) TextDecoration.Underline else null,
        )
        withStyle(style) { append(s.text) }
    }
}
