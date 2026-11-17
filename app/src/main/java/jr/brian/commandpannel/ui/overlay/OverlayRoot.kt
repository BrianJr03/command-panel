package jr.brian.commandpannel.ui.overlay

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
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeToolbar
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeableCard
import jr.brian.commandpannel.ui.overlay.arrange.arrangeContentArea
import jr.brian.commandpannel.ui.overlay.arrange.longPressToArrange
import jr.brian.commandpannel.ui.overlay.arrange.rememberArrangeController
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccTheme

/**
 * Root of the bottom-screen overlay. Picks the palette from `tcc_theme_mode` (1 = follow system night, 3 = light,
 * else pure black), then stacks the status bar, the active tab, and the nav bar over the theme background.
 */
@Composable
fun OverlayRoot(
    state: OverlayUiState = OverlayUiState(),
    actions: OverlayActions = OverlayActions.None,
    initialTab: OverlayTab = OverlayTab.SETTINGS,
) {
    val nightActive = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val basePalette = when (state.themeMode) {
        3 -> TccColors.Light
        1 -> if (nightActive) TccColors.PureBlack else TccColors.Light
        else -> TccColors.PureBlack
    }
    // When the LED→accent sync is on, the Thor's ambient-LED colour becomes the overlay accent.
    val colors = state.accentOverride?.let { basePalette.copy(accent = androidx.compose.ui.graphics.Color(it)) } ?: basePalette
    TccTheme(colors) {
      androidx.compose.runtime.CompositionLocalProvider(
          jr.brian.commandpannel.ui.theme.LocalTccLookSpec provides jr.brian.commandpannel.ui.theme.specForLook(state.look),
      ) {
        val spec = jr.brian.commandpannel.ui.theme.LocalTccLookSpec.current
        val lookColors = jr.brian.commandpannel.ui.theme.tccLookColors()
        val arrange = rememberArrangeController(state.layout) { actions.setOverlayLayout(it) }
        var tab by remember { mutableStateOf(initialTab) }
        var dialog by remember { mutableStateOf(OverlayDialogKind.NONE) }
        var showBottomTip by remember { mutableStateOf(false) }

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
                    onMode = { tab = OverlayTab.MODE },
                    onLaunch = { actions.quickLaunch() },
                    onSettings = { tab = OverlayTab.SETTINGS },
                    onApp = { actions.launchCompanion() },
                )
            }
        }
        // The nav bar always sits at the top of the overlay, never along the bottom edge.
        val navAtTop = true
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .tccLookBackdrop(spec, LocalTccColors.current, lookColors)
                    .longPressToArrange(arrange),
            ) {
                Spacer(Modifier.height(10.dp))
                StatusBar(state)
                if (navAtTop) { Spacer(Modifier.height(8.dp)); navBar() }
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
                        hint = "Drag to rearrange",
                        tabLabels = listOf("Mode", "Settings"),
                        activeTab = if (tab == OverlayTab.MODE) 0 else 1,
                        onSelectTab = { tab = if (it == 0) OverlayTab.MODE else OverlayTab.SETTINGS },
                        resetLabel = "Reset",
                        isResetEnabled = !state.layout.isDefault,
                        onReset = { arrange.reset() },
                        doneLabel = "Done",
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
        }
      }
    }
}
