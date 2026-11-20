package jr.brian.commandpannel.replay.studio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.service.AssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * Debug builds only: lets adb scripts drive the clip library before Studio has a screen. Registered at runtime by
 * [register] (never in the manifest), and [register] is a no-op in release builds.
 *
 * - `am broadcast -a <pkg>.action.STUDIO_SAVE` saves a Replay Buffer clip through the service.
 * - `am broadcast -a <pkg>.action.STUDIO_LIST [--ez details true]` logs the listing under the `ReplayStudioDebug` tag:
 *   `LIST n=<count>` (or `LIST unavailable`), then one
 *   `CLIP|<origin>|<size>|direct=<yes|no>|readable=<yes|no>|duration=<ms|none>|thumb=<yes|no>|<path>` per clip
 *   (duration and thumb only with `details`), then `END`.
 */
class ReplayStudioDebugReceiver private constructor(private val repo: ClipRepository) : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            context.packageName + ACTION_SAVE -> runCatching {
                context.startService(
                    Intent(context, AssistantService::class.java).setAction(AssistantService.ACTION_REPLAY_SAVE_OR_ARM),
                )
            }.onFailure { Log.w(TAG, "save: couldn't reach the service", it) }
            context.packageName + ACTION_LIST -> {
                val details = intent.getBooleanExtra(EXTRA_DETAILS, false)
                val pending = goAsync()
                scope.launch {
                    try {
                        logListing(details)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun logListing(details: Boolean) {
        val clips = repo.list()
        if (clips == null) {
            Log.i(TAG, "LIST unavailable")
            Log.i(TAG, "END")
            return
        }
        Log.i(TAG, "LIST n=${clips.size}")
        for (clip in clips) {
            val direct = runCatching { FileInputStream(File(clip.path)).use { it.read() >= 0 } }.getOrDefault(false)
            val readable = repo.readableFile(clip) != null
            val duration = if (details) repo.durationMs(clip)?.toString() ?: "none" else "skipped"
            val thumb = if (details) (if (repo.thumbnail(clip) != null) "yes" else "no") else "skipped"
            Log.i(
                TAG,
                "CLIP|${clip.origin}|${clip.sizeBytes}|direct=${yn(direct)}|readable=${yn(readable)}|" +
                    "duration=$duration|thumb=$thumb|${clip.path}",
            )
        }
        Log.i(TAG, "END")
    }

    private fun yn(b: Boolean) = if (b) "yes" else "no"

    companion object {
        private const val TAG = "ReplayStudioDebug"
        private const val ACTION_SAVE = ".action.STUDIO_SAVE"
        private const val ACTION_LIST = ".action.STUDIO_LIST"
        private const val EXTRA_DETAILS = "details"

        /** Registers the receiver for this process. Does nothing in release builds. */
        fun register(context: Context) {
            if (!BuildConfig.DEBUG) return
            val app = context.applicationContext ?: context
            val filter = IntentFilter().apply {
                addAction(app.packageName + ACTION_SAVE)
                addAction(app.packageName + ACTION_LIST)
            }
            app.registerReceiver(
                ReplayStudioDebugReceiver(ClipRepository(app, PrivilegedAccess())),
                filter,
                Context.RECEIVER_EXPORTED,
            )
        }
    }
}
