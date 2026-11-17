package jr.brian.commandpannel.ui.overlay

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
        modifier = modifier.fillMaxWidth().height(44.dp).padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(state.clock, color = colors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.bluetoothOn) {
                Image(
                    painter = rememberStockPainter("ic_bluetooth"),
                    contentDescription = "Bluetooth",
                    colorFilter = stockIconColorFilter(colors.primary),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (!state.hideNetSpeed) {
                Text(state.netSpeed, color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (state.wifiLevel in 0..3) {
                Image(
                    painter = rememberStockPainter("ic_status_wifi_${state.wifiLevel}"),
                    contentDescription = "Wi-Fi",
                    colorFilter = stockIconColorFilter(colors.primary),
                    modifier = Modifier.size(width = 20.dp, height = 16.dp),
                )
            }
            BatteryIcon(
                percent = state.batteryPercent,
                charging = state.charging,
                lightning = colors.lightning,
                modifier = Modifier.size(width = 30.dp, height = 15.dp),
            )
            Text("${state.batteryPercent}%", color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            state.batteryEstimate?.let {
                Text("· $it", color = colors.secondary, fontSize = 13.sp)
            }
        }
    }
}
