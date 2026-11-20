package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.R

/** The "new" bubbles' state and how to clear one, for the rail and the panes. */
internal class NewBadgesHost(val state: NewBadgeState, val onSeen: (NewPlace) -> Unit)

internal val LocalNewBadges = compositionLocalOf { NewBadgesHost(NewBadgeState()) {} }

/**
 * The "new" bubble: a dot in the look's accent, with a ring in [ring] so it reads against any edge, overlapping this
 * element's top-left corner like a badge (a card's or tile row's corner, or a rail row's icon). It is drawn, not laid out, so the item's size and its label's width don't
 * change. Put it after any graphicsLayer (so it scales and moves with the item) and before any clip (so the clip
 * doesn't cut it). It is never a focus target; focusing (or tapping) the item clears it.
 */
internal fun Modifier.newCornerDot(show: Boolean, accent: Color, ring: Color): Modifier = if (!show) this else drawWithContent {
    drawContent()
    val r = 4.5.dp.toPx()
    // Centered just inside the corner, so about half of it hangs over the edge.
    val c = Offset(r * 0.5f, r * 0.5f)
    drawCircle(ring, r + 2.dp.toPx(), c)
    drawCircle(accent, r, c)
}

/**
 * The bubble's description for TalkBack (and device checks): "[label] is new since the last update". It reports
 * no size to its row, so it adds nothing to the layout. Place it inside the item that carries [newCornerDot].
 */
@Composable
internal fun NewBadgeLabel(label: String) {
    val desc = stringResource(R.string.settings_new_badge_desc, label)
    // Takes no room in its row (it reports 0 x 0), but its own node is 1 px, since accessibility (and uiautomator)
    // skip a node with no size.
    Box(
        Modifier
            .layout { m, _ ->
                val node = m.measure(Constraints.fixed(1, 1))
                layout(0, 0) { node.place(0, 0) }
            }
            .semantics { contentDescription = desc },
    )
}

/**
 * Tags a card in a pane as [section]'s [card]: while it's new, focus landing anywhere inside it, or a touch on it,
 * marks it seen.
 */
@Composable
internal fun Modifier.newCard(section: SettingsCategory, card: String): Modifier {
    val host = LocalNewBadges.current
    val onSeen by rememberUpdatedState(host.onSeen)
    if (!host.state.cardNew(section, card)) return this
    val place = NewPlace.Card(section, card)
    return onFocusChanged { if (it.hasFocus) onSeen(place) }
        .pointerInput(place) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onSeen(place)
            }
        }
}
