package jr.brian.commandpannel

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jr.brian.commandpannel.service.AssistantService
import jr.brian.commandpannel.ui.diagnostics.DiagnosticsViewModel

/**
 * Diagnostics host on the top display: bootstraps the privileged layer, reports each step, and (phase 2) can poke
 * the overlay on the bottom screen. Seed of the companion-settings activity; the real settings UI arrives later.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AssistantService.start(this)
        setContent { DiagnosticsScreen(onToggleOverlay = { toggleOverlay() }) }
    }

    private fun toggleOverlay() {
        startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_TOGGLE_OVERLAY))
    }
}

@Composable
private fun DiagnosticsScreen(
    onToggleOverlay: () -> Unit,
    vm: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
    ) {
        Text("Command Panel · ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 22.sp)
        Text("Diagnostics (phases 1–2)", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.run() }, enabled = !state.running) {
                Text(if (state.running) "Running…" else "Run diagnostics")
            }
            Button(onClick = onToggleOverlay) { Text("Toggle overlay") }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((text, ok) in state.lines) {
                Row {
                    val mark = when (ok) {
                        true -> "[PASS] "
                        false -> "[FAIL] "
                        null -> "[info] "
                    }
                    val color = when (ok) {
                        true -> Color(0xFF76CA40)
                        false -> Color(0xFFF64646)
                        null -> Color.Gray
                    }
                    Text(mark, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(text, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }
}
