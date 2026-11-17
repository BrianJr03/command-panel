package jr.brian.commandpannel.ui.overlay

import jr.brian.commandpannel.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import jr.brian.commandpannel.ui.components.BatteryIcon
import jr.brian.commandpannel.ui.components.rememberStockPainter
import jr.brian.commandpannel.ui.components.stockIconColorFilter
import jr.brian.commandpannel.ui.theme.LocalTccColors

/** Stock top status bar: clock at the left; Bluetooth, network speed, Wi-Fi and battery clustered at the right. */
@Composable
fun StatusBar(state: OverlayUiState, modifier: Modifier = Modifier) {
    val colors = LocalTccColors.current
    Row(
        modifier = modifier.fillMaxWidth().height(34.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(state.clock, color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
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
