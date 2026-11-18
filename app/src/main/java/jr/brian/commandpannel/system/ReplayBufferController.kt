package jr.brian.commandpannel.system

import android.content.Context
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.ReplayBitratePreset
import jr.brian.commandpannel.privileged.ReplayCaptureEngine
import jr.brian.commandpannel.privileged.ReplayTarget
import jr.brian.commandpannel.privileged.ReplayClipWriter
import jr.brian.commandpannel.privileged.ReplayRingBuffer
import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import java.io.File

/**
 * Orchestrates the Replay Buffer: arms/disarms the [ReplayCaptureEngine] from the `replay_*` prefs and saves the
 * last-N-seconds clip (mux to the app cache, then root-copy into the user's clips folder on shared storage).
 */
class ReplayBufferController(
    private val context: Context,
    private val privileged: PrivilegedAccess,
) {
    private val tag = "ReplayBuffer"
    private val prefs = Prefs.of(context)
    private val engine = ReplayCaptureEngine(context, privileged)

    val isArmed: Boolean get() = engine.isRunning

    /** What the running capture records. */
    val armedTarget: ReplayTarget get() = engine.target

    private fun windowSeconds(): Int =
        ScreenRecorderEngine.normalizeReplayWindowSeconds(
            prefs.getInt("replay_window_seconds", ScreenRecorderEngine.DEFAULT_REPLAY_WINDOW_SECONDS),
        )

    private fun preset(): ReplayBitratePreset = ReplayBitratePreset.fromKey(prefs.getString("replay_bitrate", null))

    private fun target(): ReplayTarget = ReplayTarget.fromKey(prefs.getString("replay_target", null))

    /**
     * The clips folder: the user's custom folder while it still exists, else TCP Clips. A custom folder deleted outside
     * the app is dropped (pref cleared) rather than silently recreated. If the check itself fails, the folder is kept.
     */
    private fun clipsRelativeDir(): String {
        val custom = ScreenRecorderEngine.normalizeReplayClipsRelativeDir(prefs.getString("replay_clips_dir", null))
            ?: return ScreenRecorderEngine.DEFAULT_REPLAY_CLIPS_RELATIVE_DIR
        val dir = shellQuote("${ScreenRecorderEngine.REPLAY_STORAGE_ROOT}/$custom")
        if (privileged.shell("[ -d $dir ] && echo yes || echo no")?.trim() != "no") return custom
        Log.i(tag, "custom clips folder '$custom' is gone; saving to the default folder")
        prefs.edit().remove("replay_clips_dir").apply()
        return ScreenRecorderEngine.DEFAULT_REPLAY_CLIPS_RELATIVE_DIR
    }

    /** Single-quotes [s] for the shell; folder names may contain an apostrophe. */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Arms or disarms capture to match the `replay_enabled` pref. */
    fun applyEnabledState() {
        if (prefs.getBoolean("replay_enabled", false)) arm() else disarm()
    }

    /** Persists the enabled pref and arms/disarms to match. */
    fun setEnabled(enable: Boolean) {
        prefs.edit().putBoolean("replay_enabled", enable).apply()
        applyEnabledState()
    }

    fun arm(): Boolean = engine.start(windowSeconds(), preset(), target())

    fun disarm() = engine.stop()

    /**
     * Restarts a running capture with the current prefs, so a new target, window or bitrate applies now. The buffered
     * footage is dropped. Returns whether capture is armed afterward.
     */
    fun rearm(): Boolean {
        engine.stop()
        return arm().also { if (!it) Log.w(tag, "re-arm after a settings change failed") }
    }

    fun bufferedSeconds(): Int = (engine.bufferedDurationUs() / 1_000_000L).toInt()

    /** How long a clip saved now would be: what's buffered, up to the save window, and at least 1 s. */
    fun clipSeconds(): Int = bufferedSeconds().coerceIn(1, windowSeconds())

    /** Saves the buffered clip. Returns the saved path, or `error:<code>`. */
    fun save(): String {
        if (!engine.isRunning) return "error:${ScreenRecorderEngine.REPLAY_ERR_NOT_ARMED}"
        val format = engine.videoFormat ?: return "error:${ScreenRecorderEngine.REPLAY_ERR_START_FAILED}"
        when (val slice = engine.slice()) {
            null, is ReplayRingBuffer.Slice.Failure -> {
                val code = (slice as? ReplayRingBuffer.Slice.Failure)?.error?.let {
                    if (it == ReplayRingBuffer.SliceError.NO_KEYFRAME) ScreenRecorderEngine.REPLAY_ERR_NO_KEYFRAME
                    else ScreenRecorderEngine.REPLAY_ERR_EMPTY
                } ?: ScreenRecorderEngine.REPLAY_ERR_EMPTY
                return "error:$code"
            }
            is ReplayRingBuffer.Slice.Clip -> {
                val tmp = File(context.cacheDir, "replay_tmp.mp4")
                if (!ReplayClipWriter.write(slice, format, tmp)) return "error:${ScreenRecorderEngine.REPLAY_ERR_WRITE_FAILED}"
                val dir = clipsRelativeDir()
                val path = ScreenRecorderEngine.buildReplayClipPath(
                    targetMode = engine.target.mode,
                    baseDir = "${ScreenRecorderEngine.REPLAY_STORAGE_ROOT}/$dir/${ScreenRecorderEngine.replayTargetFolder(engine.target.mode)}",
                )
                if (!ScreenRecorderEngine.isValidReplayClipOutputPath(path)) {
                    return "error:${ScreenRecorderEngine.REPLAY_ERR_INVALID_PATH}"
                }
                val parent = path.substringBeforeLast('/')
                privileged.shell("mkdir -p ${shellQuote(parent)} && cp ${shellQuote(tmp.absolutePath)} ${shellQuote(path)} && chmod 664 ${shellQuote(path)}")
                // Percent-encoded, so a folder name with spaces ("TCP Clips") still makes a valid URI.
                val uri = android.net.Uri.fromFile(File(path))
                privileged.shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d ${shellQuote(uri.toString())} >/dev/null 2>&1")
                runCatching { tmp.delete() }
                Log.i(tag, "saved replay clip -> $path")
                return path
            }
        }
    }
}
