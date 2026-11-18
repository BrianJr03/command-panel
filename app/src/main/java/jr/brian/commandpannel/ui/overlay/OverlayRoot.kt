package jr.brian.commandpannel.ui.overlay

import jr.brian.commandpannel.R
import androidx.compose.ui.res.stringResource
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import jr.brian.commandpannel.system.OverlayCardId
import jr.brian.commandpannel.system.UserFontStore
import jr.brian.commandpannel.ui.theme.toSpec
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeToolbar
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeableCard
import jr.brian.commandpannel.ui.overlay.arrange.arrangeContentArea
import jr.brian.commandpannel.ui.overlay.arrange.longPressToArrange
import jr.brian.commandpannel.ui.overlay.arrange.rememberArrangeController
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import jr.brian.commandpannel.ui.theme.TccTheme

/**
 * Root of the bottom-screen overlay. Picks the palette from `tcc_theme_mode` (1 = follow system night, 3 = light,
 * else pure black), then stacks the status bar, the active tab, and the nav bar over the theme background.
 * [showStatusBar] false leaves out the clock / network / battery row (look previews in the companion).
 */
@Composable
fun OverlayRoot(
    state: OverlayUiState = OverlayUiState(),
    actions: OverlayActions = OverlayActions.None,
    showStatusBar: Boolean = true,
) {
    val nightActive = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    // When the LED→accent sync is on, the Thor's ambient-LED color becomes the overlay accent, even over a look's own.
    val colors = resolveLookPalette(state.themeMode, nightActive, state.accentOverride, state.customLook?.colors)
    val context = androidx.compose.ui.platform.LocalContext.current
    val lookSpec = remember(state.look, state.customLook) {
        state.customLook?.toSpec(UserFontStore.resolver(context)) ?: jr.brian.commandpannel.ui.theme.specForLook(state.look)
    }
    TccTheme(colors) {
      androidx.compose.runtime.CompositionLocalProvider(
          jr.brian.commandpannel.ui.theme.LocalTccLookSpec provides lookSpec,
      ) {
        val spec = jr.brian.commandpannel.ui.theme.LocalTccLookSpec.current
        val lookColors = jr.brian.commandpannel.ui.theme.tccLookColors()
        val arrange = rememberArrangeController(state.layout) { actions.setOverlayLayout(it) }
        // Open on the last-used tab and persist changes, so a reopen lands where the user left off. Keyed on navTab so a
        // caller that picks the tab (Theme Studio's preview) switches it.
        var tab by remember(state.navTab) { mutableStateOf(state.navTab) }
        val selectTab: (OverlayTab) -> Unit = { tab = it; actions.setNavTab(it) }
        var dialog by remember { mutableStateOf(OverlayDialogKind.NONE) }
        var showBottomTip by remember { mutableStateOf(false) }
        // The dummy bool: onboarding shows the Launch button (and points at it) even before an app is chosen.
        val onboarding = state.onboardingStep != null
        var launchBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

        // Modal tiles open a dialog; instant tiles run their action.
        val onTile: (String) -> Unit = { id ->
            val d = dialogForTile(id)
            if (d != null) dialog = d else actions.onTileClick(id)
        }
        // "Only top screen" shows the wake tip first unless suppressed.
        val onRequestMode: (Int) -> Unit = { mode ->
            if (mode == 1 && !state.suppressBottomTip) showBottomTip = true else actions.selectMode(mode)
        }

        val navBar: @Composable () -> Unit = {
            ArrangeableCard(OverlayCardId.NAV_BAR, arrange, Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 15.dp)) {
                NavBar(
                    activeTab = tab,
                    modifier = Modifier.fillMaxSize(),
                    quickLaunchLabel = state.quickLaunchLabel,
                    quickLaunchApp = state.quickLaunchApp,
                    forceLaunch = onboarding,
                    onLaunchPositioned = { launchBounds = it },
                    order = state.navOrder,
                    onMode = { selectTab(OverlayTab.MODE) },
                    onTask = { actions.openRecents() },
                    onLaunch = { if (!onboarding) actions.quickLaunch() },
                    onSettings = { selectTab(OverlayTab.SETTINGS) },
                    onApp = { actions.launchCompanion() },
                )
            }
        }
        // Nav bar sits at the top or bottom per the user's arrangement (drag it in Arrange mode); default top.
        val navAtTop = state.layout.navBarPosition == jr.brian.commandpannel.system.NavBarPosition.TOP
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .tccLookBackdrop(spec, LocalTccColors.current, lookColors)
                    .longPressToArrange(arrange),
            ) {
                Spacer(Modifier.height(10.dp))
                if (showStatusBar) StatusBar(state)
                if (navAtTop) { if (showStatusBar) Spacer(Modifier.height(8.dp)); navBar() }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 15.dp, vertical = 5.dp)
                        .arrangeContentArea(arrange),
                ) {
                    when (tab) {
                        OverlayTab.MODE -> ModeTab(state, actions, onRequestMode, arrange, Modifier.fillMaxSize())
                        OverlayTab.SETTINGS -> SettingsTab(state, actions, onTile, arrange, Modifier.fillMaxSize())
                    }
                }
                if (!navAtTop) navBar()
                Spacer(Modifier.height(12.dp))
            }
            if (arrange.isArranging) {
                Box(Modifier.fillMaxSize().padding(top = 44.dp), contentAlignment = Alignment.TopCenter) {
                    ArrangeToolbar(
                        hint = stringResource(R.string.arrange_hint),
                        tabLabels = listOf(stringResource(R.string.navigation_screen_control_text), stringResource(R.string.navigation_system_set_text)),
                        activeTab = if (tab == OverlayTab.MODE) 0 else 1,
                        onSelectTab = { selectTab(if (it == 0) OverlayTab.MODE else OverlayTab.SETTINGS) },
                        resetLabel = stringResource(R.string.arrange_reset),
                        isResetEnabled = !state.layout.isDefault,
                        onReset = { arrange.reset() },
                        doneLabel = stringResource(R.string.arrange_done),
                        onDone = { arrange.exit() },
                        modifier = Modifier.wrapContentHeight(),
                    )
                }
            }
            OverlayDialogLayer(dialog, state, actions, onDismiss = { dialog = OverlayDialogKind.NONE })
            if (showBottomTip) {
                BottomTipDialog(
                    onConfirm = { dontShow ->
                        if (dontShow) actions.setSuppressBottomTip(true)
                        showBottomTip = false
                        actions.selectMode(1)
                    },
                    onDismiss = { showBottomTip = false },
                )
            }
            state.onboardingStep?.let { step ->
                OverlayOnboardingLayer(
                    step = step,
                    arrange = arrange,
                    layout = state.layout,
                    navAtTop = navAtTop,
                    launchBounds = launchBounds,
                    currentLook = state.look.takeIf { state.customLook == null },
                    onStep = actions::setOnboardingStep,
                    onLook = actions::setLook,
                    onEnd = actions::endOnboarding,
                )
            }
        }
      }
    }
}
