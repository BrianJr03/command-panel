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

    /** A section ([jr.brian.commandpannel.ui.settings.SettingsCategory.key]) to open, from [MainActivity.EXTRA_SECTION]. */
    val requestedSection = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** A screen to open on top of settings, from [MainActivity.EXTRA_SCREEN] (for example [MainActivity.SCREEN_REPLAY_STUDIO]). */
    val requestedScreen = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Debug builds only: open the What's new dialog, from [MainActivity.EXTRA_DEBUG_WHATS_NEW] (scripts, #62). */
    val requestedWhatsNew = kotlinx.coroutines.flow.MutableStateFlow(false)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeSection(intent)
    }

    /** Opens the section (the panel's Keyboard tile, #41) or screen (its Replay Studio tile, #49) the intent asks for, once. */
    private fun takeSection(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SECTION)?.let { CompanionPresence.requestedSection.value = it }
        intent?.removeExtra(EXTRA_SECTION)
        intent?.getStringExtra(EXTRA_SCREEN)?.let { CompanionPresence.requestedScreen.value = it }
        intent?.removeExtra(EXTRA_SCREEN)
        if (jr.brian.commandpannel.BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEBUG_WHATS_NEW, false) == true) {
            CompanionPresence.requestedWhatsNew.value = true
        }
        intent?.removeExtra(EXTRA_DEBUG_WHATS_NEW)
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
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .apply {
                            intent?.getStringExtra(EXTRA_SECTION)?.let { putExtra(EXTRA_SECTION, it) }
                            intent?.getStringExtra(EXTRA_SCREEN)?.let { putExtra(EXTRA_SCREEN, it) }
                        },
                    opts.toBundle(),
                )
            }.isSuccess
            android.util.Log.i("MainActivity", "relaunch on display 0 requested=$moved")
            finish()
            return
        }
        if (savedInstanceState == null) takeSection(intent)
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

    companion object {
        /** Opens the companion on this section: a [jr.brian.commandpannel.ui.settings.SettingsCategory.key]. */
        const val EXTRA_SECTION = "section"

        /** Opens the companion on this screen, over settings; see [jr.brian.commandpannel.ui.settings.CompanionRoutes.forScreen]. */
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_REPLAY_STUDIO = "replay_studio"

        /** Debug builds only: `--ez debug_whats_new true` opens the What's new dialog (scripts, #62). */
        const val EXTRA_DEBUG_WHATS_NEW = "debug_whats_new"
    }
}
