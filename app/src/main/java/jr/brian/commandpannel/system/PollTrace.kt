package jr.brian.commandpannel.system

import android.util.Log
import jr.brian.commandpannel.BuildConfig

/**
 * Debug builds only: logs the panel pollers starting, stopping and sampling, so a device script can check they stop
 * while the screen is off (#92, verify-sleep-drain.sh). Each sample's line is off unless the tag is turned on
 * (`adb shell setprop log.tag.PanelPoll VERBOSE`), so it stays out of everyday logcat. Release builds log nothing.
 */
internal object PollTrace {
    private const val TAG = "PanelPoll"

    /** The pollers as a whole: `running` or `paused`. */
    fun state(state: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "pollers $state")
    }

    /** One sample of the [poller] named (`status`, `telemetry`, `battery`). */
    fun tick(poller: String) {
        if (BuildConfig.DEBUG && Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "tick $poller")
    }
}
