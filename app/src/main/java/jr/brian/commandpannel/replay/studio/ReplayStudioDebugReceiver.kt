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
 * - `am broadcast -a <pkg>.action.STUDIO_AUTO_DELETE_SET --ez enable true --ei age_days 7 --ei size_gb 0` saves the
 *   auto-delete rules (0 turns a rule off) and logs `AUTODELETE_RULES <rules>`.
 * - `am broadcast -a <pkg>.action.STUDIO_AUTO_DELETE_RUN [--ei age_days N] [--el max_bytes N]` runs auto-delete now,
 *   with the given limits or, with neither, the saved rules. Logs `AUTODELETE removed=<n>`, one `GONE|<path>` per
 *   removed clip, then `END`.
 */
class ReplayStudioDebugReceiver private constructor(
    private val repo: ClipRepository,
    private val autoDelete: ClipAutoDelete,
) : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            context.packageName + ACTION_SAVE -> runCatching {
                context.startService(
                    Intent(context, AssistantService::class.java).setAction(AssistantService.ACTION_REPLAY_SAVE_OR_ARM),
                )
            }.onFailure { Log.w(TAG, "save: couldn't reach the service", it) }
            context.packageName + ACTION_AUTO_DELETE_SET -> {
                val ageDays = intent.getIntExtra(EXTRA_AGE_DAYS, 0)
                val sizeGb = intent.getIntExtra(EXTRA_SIZE_GB, 0)
                val rules = AutoDeleteRules(
                    enabled = intent.getBooleanExtra(EXTRA_ENABLE, true),
                    ageOn = ageDays > 0,
                    ageDays = if (ageDays > 0) ageDays else AutoDeleteRules.DEFAULT_AGE_DAYS,
                    sizeOn = sizeGb > 0,
                    sizeGb = if (sizeGb > 0) sizeGb else AutoDeleteRules.DEFAULT_SIZE_GB,
                )
                rules.save(jr.brian.commandpannel.system.Prefs.of(context))
                Log.i(TAG, "AUTODELETE_RULES $rules")
            }
            context.packageName + ACTION_AUTO_DELETE_RUN -> {
                val ageDays = intent.getIntExtra(EXTRA_AGE_DAYS, 0).takeIf { it > 0 }
                val maxBytes = intent.getLongExtra(EXTRA_MAX_BYTES, -1L).takeIf { it >= 0 }
                val pending = goAsync()
                scope.launch {
                    try {
                        val removed = if (ageDays == null && maxBytes == null) autoDelete.run()
                        else autoDelete.run(ageDays, maxBytes)
                        Log.i(TAG, "AUTODELETE removed=${removed.size}")
                        removed.forEach { Log.i(TAG, "GONE|${it.path}") }
                        Log.i(TAG, "END")
                    } finally {
                        pending.finish()
                    }
                }
            }
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
        private const val ACTION_AUTO_DELETE_SET = ".action.STUDIO_AUTO_DELETE_SET"
        private const val ACTION_AUTO_DELETE_RUN = ".action.STUDIO_AUTO_DELETE_RUN"
        private const val EXTRA_ENABLE = "enable"
        private const val EXTRA_AGE_DAYS = "age_days"
        private const val EXTRA_SIZE_GB = "size_gb"
        private const val EXTRA_MAX_BYTES = "max_bytes"

        /** Registers the receiver for this process. Does nothing in release builds. */
        fun register(context: Context) {
            if (!BuildConfig.DEBUG) return
            val app = context.applicationContext ?: context
            val filter = IntentFilter().apply {
                addAction(app.packageName + ACTION_SAVE)
                addAction(app.packageName + ACTION_LIST)
                addAction(app.packageName + ACTION_AUTO_DELETE_SET)
                addAction(app.packageName + ACTION_AUTO_DELETE_RUN)
            }
            app.registerReceiver(
                PrivilegedAccess().let { ReplayStudioDebugReceiver(ClipRepository(app, it), ClipAutoDelete(app, it)) },
                filter,
                Context.RECEIVER_EXPORTED,
            )
        }
    }
}
