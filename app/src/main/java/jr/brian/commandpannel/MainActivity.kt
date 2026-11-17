package jr.brian.commandpannel

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
import jr.brian.commandpannel.ui.diagnostics.DiagnosticsViewModel

/**
 * Phase 1 host: a Diagnostics screen on the top display that bootstraps the privileged layer and reports each step.
 * This is the companion-settings activity's seed; the real settings UI arrives with the overlay (later phases).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DiagnosticsScreen() }
    }
}

@Composable
private fun DiagnosticsScreen(vm: DiagnosticsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
    ) {
        Text("Command Panel · ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 22.sp)
        Text("Privileged layer diagnostics (phase 1)", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.run() }, enabled = !state.running) {
            Text(if (state.running) "Running…" else "Run diagnostics")
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
