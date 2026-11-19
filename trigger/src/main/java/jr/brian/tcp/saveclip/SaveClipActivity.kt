package jr.brian.tcp.saveclip

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Asks Command Panel (release and debug builds) to save a replay clip, or arm the buffer if it's off, then finishes
 * without drawing or animating. Each build's receiver accepts only its own `<applicationId>.action.SAVE_OR_ARM_REPLAY`
 * from apps holding its signature permission.
 */
class SaveClipActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        for (pkg in TARGETS) sendBroadcast(Intent("$pkg.action.SAVE_OR_ARM_REPLAY").setPackage(pkg))
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private companion object {
        val TARGETS = listOf("jr.brian.commandpannel", "jr.brian.commandpannel.debug")
    }
}
