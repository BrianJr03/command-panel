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
    val colors = when (state.themeMode) {
        3 -> TccColors.Light
        1 -> if (nightActive) TccColors.PureBlack else TccColors.Light
        else -> TccColors.PureBlack
    }
    TccTheme(colors) {
        val bg = LocalTccColors.current.primaryBackground
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

        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(bg)) {
                Spacer(Modifier.height(15.dp))
                StatusBar(state)
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 15.dp, vertical = 5.dp),
                ) {
                    when (tab) {
                        OverlayTab.MODE -> ModeTab(state, actions, onRequestMode, Modifier.fillMaxSize())
                        OverlayTab.SETTINGS -> SettingsTab(state, actions, onTile, Modifier.fillMaxSize())
                    }
                }
                NavBar(
                    activeTab = tab,
                    modifier = Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 15.dp),
                    onMode = { tab = OverlayTab.MODE },
                    onSettings = { tab = OverlayTab.SETTINGS },
                )
                Spacer(Modifier.height(12.dp))
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
