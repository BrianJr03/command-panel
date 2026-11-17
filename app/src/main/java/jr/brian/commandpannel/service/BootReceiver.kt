package jr.brian.commandpannel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts [AssistantService] on boot and after an app update, like the stock assistant's boot start. Direct-boot
 * aware so it comes up before the user unlocks.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> AssistantService.start(context)
        }
    }
}
