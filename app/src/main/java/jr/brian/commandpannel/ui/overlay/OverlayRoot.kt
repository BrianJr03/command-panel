package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccTheme

/**
 * Root of the bottom-screen overlay (`TccView` / `main_layout`): a vertical stack of the status bar, the active tab,
 * and the nav bar over the theme's primary background. Phase 3 renders the stock layout from sample [OverlayUiState];
 * phase 4 feeds it live state and wires the controls.
 */
@Composable
fun OverlayRoot(
    state: OverlayUiState = OverlayUiState(),
    colors: TccColors = TccColors.PureBlack,
    initialTab: OverlayTab = OverlayTab.SETTINGS,
) {
    TccTheme(colors) {
        val bg = LocalTccColors.current.primaryBackground
        var tab by remember { mutableStateOf(initialTab) }
        Column(
            modifier = Modifier.fillMaxSize().background(bg),
        ) {
            Spacer(Modifier.height(15.dp))
            StatusBar(state)
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 15.dp, vertical = 5.dp),
            ) {
                when (tab) {
                    OverlayTab.MODE -> ModeTab(state, Modifier.fillMaxSize())
                    OverlayTab.SETTINGS -> SettingsTab(state, Modifier.fillMaxSize())
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
    }
}
