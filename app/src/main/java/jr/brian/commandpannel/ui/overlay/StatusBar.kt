package jr.brian.commandpannel.ui.overlay

import jr.brian.commandpannel.R
import androidx.compose.ui.res.stringResource
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import jr.brian.commandpannel.ui.components.BatteryIcon
import jr.brian.commandpannel.ui.components.rememberStockPainter
import jr.brian.commandpannel.ui.components.stockIconColorFilter
import jr.brian.commandpannel.ui.theme.LocalTccColors

/**
 * Stock top status bar: clock at the left; Bluetooth, network speed, Wi-Fi and battery clustered at the right. With
 * [OverlayUiState.mediaControls] on, previous / play-pause / next sit centered in the gap between the clock and the
 * first icon on the right (#88).
 */
@Composable
fun StatusBar(state: OverlayUiState, actions: OverlayActions, modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    Row(
        modifier = modifier.fillMaxWidth().height(34.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(state.clock, color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (state.mediaControls) MediaControls(state.musicActive, actions)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.bluetoothOn) {
                Image(
                    painter = rememberStockPainter("ic_bluetooth"),
                    contentDescription = stringResource(R.string.overlay_bluetooth),
                    colorFilter = stockIconColorFilter(colors.primary),
                    modifier = Modifier.size(15.dp),
                )
            }
            if (!state.hideNetSpeed) {
                Text(state.netSpeed, color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (state.wifiLevel in 0..3) {
                Image(
                    painter = rememberStockPainter("ic_status_wifi_${state.wifiLevel}"),
                    contentDescription = stringResource(R.string.overlay_wifi),
                    colorFilter = stockIconColorFilter(colors.primary),
                    modifier = Modifier.size(width = 17.dp, height = 13.dp),
                )
            }
            BatteryIcon(
                percent = state.batteryPercent,
                charging = state.charging,
                lightning = colors.lightning,
                modifier = Modifier.size(width = 26.dp, height = 13.dp),
            )
            Text(stringResource(R.string.unit_percent, state.batteryPercent), color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            state.batteryEstimate?.let {
                Text(stringResource(R.string.overlay_battery_estimate, it), color = colors.secondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MediaControls(playing: Boolean, actions: OverlayActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MediaButton(Icons.Rounded.SkipPrevious, stringResource(R.string.overlay_media_previous)) {
            actions.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        MediaButton(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (playing) R.string.overlay_media_pause else R.string.overlay_media_play),
        ) { actions.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        MediaButton(Icons.Rounded.SkipNext, stringResource(R.string.overlay_media_next)) {
            actions.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }
}

/** A 30dp touch target around a 22dp glyph, so the buttons fit the 34dp bar and are still easy to hit. */
@Composable
private fun MediaButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = LocalTccColors.current.primary, modifier = Modifier.size(22.dp))
    }
}
