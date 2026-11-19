package jr.brian.commandpannel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import jr.brian.commandpannel.R
import jr.brian.commandpannel.overlay.OverlayToast

/**
 * Save-or-arm requests from other apps (TCP Save Clip, launched by Back Button Hero). Guarded in the manifest by the
 * signature-level `<applicationId>.permission.TRIGGER`, so only apps signed with Command Panel's key get through.
 * Takes no extras. Forwards to [AssistantService] only while it's running; it never starts it from the background.
 */
class ExternalTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "${context.packageName}$ACTION_SUFFIX") return
        if (!AssistantService.isRunning) {
            OverlayToast(context.applicationContext).show(context.getString(R.string.msg_trigger_not_running))
            return
        }
        runCatching {
            context.startService(
                Intent(context, AssistantService::class.java).setAction(AssistantService.ACTION_REPLAY_SAVE_OR_ARM)
            )
        }.onFailure { Log.w(TAG, "couldn't reach the service", it) }
    }

    private companion object {
        const val TAG = "ExternalTrigger"
        /** Appended to the application id, so each build (release / `.debug`) has its own action. */
        const val ACTION_SUFFIX = ".action.SAVE_OR_ARM_REPLAY"
    }
}
