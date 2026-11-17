package jr.brian.commandpannel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jr.brian.commandpannel.service.AssistantService
import jr.brian.commandpannel.ui.settings.SettingsScreen
import jr.brian.commandpannel.ui.settings.SettingsViewModel

/**
 * Companion settings screen on the top display. Ensures the assistant service is running, then shows the two-pane
 * settings UI (general / appearance / diagnostics). The overlay itself lives on the bottom display via the service.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AssistantService.start(this)
        setContent {
            val vm: SettingsViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(state, vm)
        }
    }

}
