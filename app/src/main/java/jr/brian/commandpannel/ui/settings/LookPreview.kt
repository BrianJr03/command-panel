package jr.brian.commandpannel.ui.settings

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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import jr.brian.commandpannel.system.UserFontStore
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.overlay.FrameRatePanel
import jr.brian.commandpannel.ui.overlay.OverlayActions
import jr.brian.commandpannel.ui.overlay.OverlayRoot
import jr.brian.commandpannel.ui.overlay.RingsCard
import jr.brian.commandpannel.ui.overlay.TempFanCard
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.TccTheme
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.toSpec
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
    /** Space around the miniature inside the preview's clipped frame. */
    padding: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val scale = (width - padding * 2) / design.width
    val outer = LocalDensity.current
    Box(
        modifier
            .size(width, design.height * scale + padding * 2)
            .clip(cardShape())
            // The miniature overlay has focusable controls of its own. Fencing it in a group that can't be entered
            // keeps D-pad focus from landing on (invisible) controls inside a preview.
            .focusProperties { onEnter = { cancelFocusChange() } }
            .focusGroup()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, outer.fontScale)) {
            Box(Modifier.fillMaxSize().padding(padding / scale)) { content() }
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
    padding: Dp = 0.dp,
) {
    ScaledPreview(width, modifier = modifier, padding = padding) {
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

/**
 * The hardware cards in the alternate forms they take when moved to other grid slots, under [look]: the rings as a 2×2
 * block and a full-width strip, TEMP/FAN as two columns, and a small FPS counter. (The overlay tab preview already
 * shows the default forms.) Slots use the real grid sizes (half width ≈ 254 dp), so clipping here is real clipping.
 */
@Composable
internal fun CardsLookPreview(state: SettingsState, look: CustomLook, width: Dp, themeMode: Int, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = resolveLookPalette(themeMode, nightActive = themeMode != 3, state.accentOverride, look.colors)
    val spec = androidx.compose.runtime.remember(look) { look.toSpec(UserFontStore.resolver(context)) }
    ScaledPreview(width, modifier = modifier) {
        TccTheme(colors) {
            CompositionLocalProvider(LocalTccLookSpec provides spec) {
                val s = OverlayUiState(themeMode = themeMode)
                Column(
                    Modifier.fillMaxSize().tccLookBackdrop(spec, colors, tccLookColors()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RingsCard(s, Modifier.weight(1f).fillMaxHeight())
                        TempFanCard(s, Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FrameRatePanel(s, OverlayActions.None, Modifier.weight(1f).fillMaxHeight())
                        RingsCard(s, Modifier.weight(1f).fillMaxHeight())
                    }
                    RingsCard(s, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}
