package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.overlay.OverlayRoot
import jr.brian.commandpannel.ui.overlay.OverlayTab
import jr.brian.commandpannel.ui.overlay.OverlayUiState
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.TccUiLook

/** The bottom screen in dp (1240 x 1080 px landscape at 369 dpi), which the overlay is laid out against. */
internal val OverlayScreenSize = DpSize(538.dp, 468.dp)

/**
 * Renders [content] as if on a [design]-sized screen, shrunk to [width]. It lowers the density instead of scaling a
 * bitmap, so text and strokes stay sharp. The preview is display-only: it swallows touches and can't take focus, so
 * gamepad navigation skips over it.
 */
@Composable
internal fun ScaledPreview(
    width: Dp,
    design: DpSize = OverlayScreenSize,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = width / design.width
    val outer = LocalDensity.current
    Box(
        modifier
            .size(width, design.height * scale)
            .clip(cardShape())
            .border(1.dp, p.border, cardShape())
            .focusProperties { onEnter = { cancelFocusChange() } }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, outer.fontScale)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** A miniature of the real overlay under [look] (or the built-in [base] when [look] is null). */
@Composable
internal fun OverlayLookPreview(
    state: SettingsState,
    base: TccUiLook,
    look: CustomLook?,
    width: Dp,
    tab: OverlayTab = OverlayTab.SETTINGS,
    themeMode: Int = state.themeMode,
    modifier: Modifier = Modifier,
) {
    ScaledPreview(width, modifier = modifier) {
        OverlayRoot(
            OverlayUiState(
                themeMode = themeMode,
                accentOverride = state.accentOverride,
                look = look?.baseLook ?: base,
                customLook = look,
                navTab = tab,
                navOrder = jr.brian.commandpannel.system.NavBarButton.ordered(state.navOrder.joinToString(",")),
                layout = state.overlayLayout,
            ),
        )
    }
}
