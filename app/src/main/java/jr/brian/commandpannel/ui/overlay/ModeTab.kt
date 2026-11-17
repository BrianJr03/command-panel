package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.components.TccCard
import jr.brian.commandpannel.ui.components.TccIconButton
import jr.brian.commandpannel.ui.components.TccSliderRow

/**
 * Stock Mode tab: a screen-mode panel on top, then a brightness card and a volume card side by side.
 */
@Composable
fun ModeTab(state: OverlayUiState, actions: OverlayActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenModePanel(state, actions, Modifier.fillMaxWidth().weight(0.46f))
        Row(Modifier.fillMaxWidth().weight(0.54f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrightnessCard(state, actions, Modifier.weight(1f).fillMaxHeight())
            VolumeCard(state, actions, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ScreenModePanel(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    TccCard(modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButton("ic_dual_screen_display", "Dual-screen\ndisplay", state.displayMode == 0) { actions.selectMode(0) }
            ModeButton("ic_just_screen_above", "Only top\nscreen", state.displayMode == 1) { actions.selectMode(1) }
            ModeButton("ic_just_screen_below", "Only bottom\nscreen", state.displayMode == 2) { actions.selectMode(2) }
        }
    }
}

@Composable
private fun ModeButton(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    TccIconButton(iconBase = icon, label = label, circleSize = 66.dp, active = active, onClick = onClick)
}

@Composable
private fun BrightnessCard(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    TccCard(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            TccSliderRow("Dual\nScreen", state.dualBrightness, brightnessIcon(state.dualBrightness), dimmed = true) { f, c -> actions.setDualBrightness(f, c) }
            TccSliderRow("Top\nScreen", state.topBrightness, brightnessIcon(state.topBrightness)) { f, c -> actions.setTopBrightness(f, c) }
            TccSliderRow("Bottom\nScreen", state.bottomBrightness, brightnessIcon(state.bottomBrightness)) { f, c -> actions.setBottomBrightness(f, c) }
        }
    }
}

@Composable
private fun VolumeCard(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    TccCard(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            TccSliderRow("Top\nScreen", state.topVolume, volumeIcon(state.topVolume)) { f, c -> actions.setTopVolume(f, c) }
            TccSliderRow("Bottom\nScreen", state.bottomVolume, volumeIcon(state.bottomVolume)) { f, c -> actions.setBottomVolume(f, c) }
        }
    }
}

private fun brightnessIcon(f: Float) = when {
    f > 0.5f -> "ic_brightness_2"
    f > 0f -> "ic_brightness_1"
    else -> "ic_brightness_0"
}

private fun volumeIcon(f: Float) = when {
    f > 0.6f -> "ic_volume_3"
    f > 0.3f -> "ic_volume_2"
    f > 0f -> "ic_volume_1"
    else -> "ic_volume_0"
}
