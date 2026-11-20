package jr.brian.commandpannel.replay.studio

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import android.view.Display
import jr.brian.commandpannel.R

/**
 * Tells the user a clip was saved, with a View action that opens it in Replay Studio's player (#48): a quiet
 * notification on its own channel, replaced by each new save. The overlay toast's View button is in AssistantService.
 */
object ClipSavedNotice {

    const val CHANNEL_ID = "replay_clips"
    const val NOTIF_ID = 48
    private const val TAG = "ClipSavedNotice"

    /** The saved clip's path from a `ReplayBufferController.save()` result, or null for an error or no path. */
    fun savedPath(result: String?): String? = result?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("error:") }

    /** The name the notification shows: the clip's file name. */
    fun displayName(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /** Posts (or replaces) the clip-saved notification. Does nothing if notifications are off. */
    fun notify(context: Context, path: String) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_clips), NotificationManager.IMPORTANCE_LOW),
            )
            val view = viewIntent(context, path)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_clip_saved_title))
                .setContentText(displayName(path))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(view)
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, R.mipmap.ic_launcher),
                        context.getString(R.string.notif_clip_saved_view),
                        view,
                    ).build(),
                )
                .setAutoCancel(true)
                .build()
            mgr.notify(NOTIF_ID, notification)
        }.onFailure { Log.w(TAG, "couldn't post the clip-saved notification", it) }
    }

    /** Opens the player on [path], on the top screen. A direct activity intent: Android blocks notification trampolines. */
    private fun viewIntent(context: Context, path: String): PendingIntent {
        val intent = Intent(context, ReplayPlayerActivity::class.java)
            .putExtra(ReplayPlayerActivity.EXTRA_CLIP_PATH, path)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        return PendingIntent.getActivity(
            context, NOTIF_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT, options,
        )
    }
}
