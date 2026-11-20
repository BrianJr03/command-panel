package jr.brian.commandpannel

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import jr.brian.commandpannel.replay.studio.ReplayStudioDebugReceiver

@HiltAndroidApp
class CommandPanelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Debug builds only (no-op in release): adb hooks for the Replay Studio clip library.
        ReplayStudioDebugReceiver.register(this)
    }
}
