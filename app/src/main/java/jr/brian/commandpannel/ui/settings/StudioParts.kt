package jr.brian.commandpannel.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import jr.brian.commandpannel.system.OverlayCardId

/**
 * The part of the overlay a Theme Studio control edits: which preview tab shows it best ([preview]) and which cards
 * to outline in it ([cards]). Focus on a tagged control (see [studioPart]) tells Studio which part is being edited.
 */
internal enum class StudioPart(val preview: PreviewKind, val cards: Set<OverlayCardId>) {
    CARDS(PreviewKind.SETTINGS, OverlayCardId.entries.filter { it != OverlayCardId.NAV_BAR }.toSet()),
    TILES(PreviewKind.SETTINGS, setOf(OverlayCardId.QUICK_TILES)),
    GAUGES(PreviewKind.SETTINGS, setOf(OverlayCardId.RINGS)),
    SLIDERS(PreviewKind.MODE, setOf(OverlayCardId.BRIGHTNESS, OverlayCardId.VOLUME)),
    NAV(PreviewKind.SETTINGS, setOf(OverlayCardId.NAV_BAR)),
    BACKDROP(PreviewKind.SETTINGS, emptySet()),
    HEADERS(PreviewKind.SETTINGS, setOf(OverlayCardId.TEMP, OverlayCardId.FRAME_RATE)),
    FPS(PreviewKind.SETTINGS, setOf(OverlayCardId.FRAME_RATE)),
    /** The Fit sizes: easiest to judge on the Cards preview, which shows every size. */
    FIT(PreviewKind.CARDS, emptySet()),
}

/** Set while Studio is open: called with the part a focused control edits, or null when focus leaves it. */
internal val LocalStudioPartFocus = staticCompositionLocalOf<((StudioPart?) -> Unit)?> { null }

/** Tags a control (or group of them) as editing [part]; null tags nothing. */
@Composable
internal fun Modifier.studioPart(part: StudioPart?): Modifier {
    val report = LocalStudioPartFocus.current ?: return this
    if (part == null) return this
    return composed {
        onFocusChanged { if (it.hasFocus) report(part) else report(null) }
    }
}
