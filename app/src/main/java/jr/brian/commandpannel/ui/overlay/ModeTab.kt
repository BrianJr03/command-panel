package jr.brian.commandpannel.ui.overlay

import jr.brian.commandpannel.R
import androidx.compose.ui.res.stringResource
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
import jr.brian.commandpannel.system.OverlayCardGroup
import jr.brian.commandpannel.system.OverlayCardId
import jr.brian.commandpannel.ui.components.TccCard
import jr.brian.commandpannel.ui.components.TccIconButton
import jr.brian.commandpannel.ui.components.TccSliderRow
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeController

/**
 * Stock Mode tab, laid out by the data-driven [OverlayGrid]: a screen-mode panel and brightness/volume cards the user
 * can rearrange in Arrange mode (long-press the overlay).
 */
@Composable
fun ModeTab(
    state: OverlayUiState,
    actions: OverlayActions,
    onRequestMode: (Int) -> Unit,
    arrange: ArrangeController,
    modifier: Modifier = Modifier,
) {
    OverlayGrid(OverlayCardGroup.MODE_TAB, state.layout.modeCells, arrange, modifier) { card, m ->
        when (card) {
            OverlayCardId.MODE_BUTTONS -> ScreenModePanel(state, onRequestMode, m)
            OverlayCardId.BRIGHTNESS -> BrightnessCard(state, actions, m)
            OverlayCardId.VOLUME -> VolumeCard(state, actions, m)
            else -> Unit
        }
    }
}

@Composable
internal fun ScreenModePanel(state: OverlayUiState, onRequestMode: (Int) -> Unit, modifier: Modifier) {
    TccCard(modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButton("ic_dual_screen_display", stringResource(R.string.mode_dual_screen_display), state.displayMode == 0) { onRequestMode(0) }
            ModeButton("ic_just_screen_above", stringResource(R.string.mode_only_top_screen), state.displayMode == 1) { onRequestMode(1) }
            ModeButton("ic_just_screen_below", stringResource(R.string.mode_only_bottom_screen), state.displayMode == 2) { onRequestMode(2) }
        }
    }
}

@Composable
private fun ModeButton(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    TccIconButton(iconBase = icon, label = label, circleSize = 48.dp, active = active, onClick = onClick)
}

@Composable
internal fun BrightnessCard(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    TccCard(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            TccSliderRow(stringResource(R.string.slider_dual_screen), state.dualBrightness, brightnessIcon(state.dualBrightness), dimmed = true) { f, c -> actions.setDualBrightness(f, c) }
            TccSliderRow(stringResource(R.string.slider_top_screen), state.topBrightness, brightnessIcon(state.topBrightness)) { f, c -> actions.setTopBrightness(f, c) }
            TccSliderRow(stringResource(R.string.slider_bottom_screen), state.bottomBrightness, brightnessIcon(state.bottomBrightness)) { f, c -> actions.setBottomBrightness(f, c) }
        }
    }
}

@Composable
internal fun VolumeCard(state: OverlayUiState, actions: OverlayActions, modifier: Modifier) {
    TccCard(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            TccSliderRow(stringResource(R.string.slider_top_screen), state.topVolume, volumeIcon(state.topVolume)) { f, c -> actions.setTopVolume(f, c) }
            TccSliderRow(stringResource(R.string.slider_bottom_screen), state.bottomVolume, volumeIcon(state.bottomVolume)) { f, c -> actions.setBottomVolume(f, c) }
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
