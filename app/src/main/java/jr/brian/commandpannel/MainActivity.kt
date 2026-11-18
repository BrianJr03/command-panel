package jr.brian.commandpannel

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jr.brian.commandpannel.service.AssistantService
import jr.brian.commandpannel.system.Prefs
import jr.brian.commandpannel.ui.overlay.OverlayOnboarding
import jr.brian.commandpannel.ui.settings.SettingsScreen
import jr.brian.commandpannel.ui.settings.SettingsViewModel

/**
 * Companion settings screen on the top display. Ensures the assistant service is running, then shows the two-pane
 * settings UI (general / appearance / diagnostics). The overlay itself lives on the bottom display via the service.
 */
/** Whether the companion is in front (resumed). The overlay's onboarding brings it up when it isn't (same process). */
object CompanionPresence {
    val resumed = kotlinx.coroutines.flow.MutableStateFlow(false)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        CompanionPresence.resumed.value = true
    }

    override fun onPause() {
        CompanionPresence.resumed.value = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The companion UI is designed for the top screen; if it was launched on the bottom (or any other)
        // display, move it to the top display so the two-pane layout isn't cramped.
        val currentDisplay = display?.displayId ?: windowManager.defaultDisplay.displayId
        android.util.Log.i("MainActivity", "onCreate on display $currentDisplay")
        if (currentDisplay != Display.DEFAULT_DISPLAY) {
            val moved = runCatching {
                val opts = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    opts.toBundle(),
                )
            }.isSuccess
            android.util.Log.i("MainActivity", "relaunch on display 0 requested=$moved")
            finish()
            return
        }
        AssistantService.start(this)
        // Opening the companion also brings up the overlay on the bottom screen to preview changes. On first launch it
        // isn't marked automatic, so standby doesn't skip it: the overlay's onboarding runs before the companion tour.
        val onboarding = !Prefs.of(this).getBoolean(OverlayOnboarding.KEY_SEEN, false)
        runCatching {
            startService(
                Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_SHOW_OVERLAY)
                    .putExtra(AssistantService.EXTRA_AUTO, !onboarding),
            )
        }
        setContent {
            val vm: SettingsViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            SettingsScreen(state, vm)
        }
    }

}
