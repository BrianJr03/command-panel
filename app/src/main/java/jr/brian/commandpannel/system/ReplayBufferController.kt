package jr.brian.commandpannel.system

import android.content.Context
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.ReplayAudio
import jr.brian.commandpannel.privileged.ReplayBitratePreset
import jr.brian.commandpannel.privileged.ReplayCaptureEngine
import jr.brian.commandpannel.privileged.ReplayTarget
import jr.brian.commandpannel.privileged.ReplayClipWriter
import jr.brian.commandpannel.privileged.ReplayRingBuffer
import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import jr.brian.commandpannel.replay.studio.ClipRepository
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

    private fun sound(): ReplayAudio = ReplayAudio(
        game = prefs.getBoolean(PREF_GAME_AUDIO, true),
        mic = prefs.getBoolean(PREF_MIC, false),
    )

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

    fun arm(): Boolean = engine.start(windowSeconds(), preset(), target(), sound())

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

    /** Saves the buffered clip. Blocking; call off the main thread. Returns the saved path, or `error:<code>`. */
    fun save(): String {
        if (!engine.isRunning) return "error:${ScreenRecorderEngine.REPLAY_ERR_NOT_ARMED}"
        val format = engine.videoFormat ?: return "error:${ScreenRecorderEngine.REPLAY_ERR_START_FAILED}"
        // A save right after arming would otherwise beat the first audio packet and come out video only.
        engine.awaitAudio(AUDIO_READY_TIMEOUT_MS)
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
                if (!ReplayClipWriter.write(slice, format, engine.audioFormat, tmp)) return "error:${ScreenRecorderEngine.REPLAY_ERR_WRITE_FAILED}"
                val dir = clipsRelativeDir()
                val basePath = ScreenRecorderEngine.buildReplayClipPath(
                    targetMode = engine.target.mode,
                    baseDir = "${ScreenRecorderEngine.REPLAY_STORAGE_ROOT}/$dir/${ScreenRecorderEngine.replayTargetFolder(engine.target.mode)}",
                )
                if (!ScreenRecorderEngine.isValidReplayClipOutputPath(basePath)) {
                    runCatching { tmp.delete() }
                    return "error:${ScreenRecorderEngine.REPLAY_ERR_INVALID_PATH}"
                }
                val parent = basePath.substringBeforeLast('/')
                // Root lists the folder (the app can't always see it), so a second save in the same second gets `_2`.
                val taken = privileged.shell("mkdir -p ${shellQuote(parent)} && ls -1 ${shellQuote(parent)} 2>/dev/null; echo $LIST_END")
                    ?.takeIf { it.contains(LIST_END) }
                    ?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                val path = taken?.let { ScreenRecorderEngine.pickFreeReplayClipPath(basePath, it) }
                    ?.takeIf { ScreenRecorderEngine.isValidReplayClipOutputPath(it) }
                val copied = path != null && privileged.shell(
                    "cp ${shellQuote(tmp.absolutePath)} ${shellQuote(path)} && chmod 664 ${shellQuote(path)} && echo $COPY_OK",
                )?.contains(COPY_OK) == true
                if (!copied || path == null) {
                    Log.w(tag, "couldn't copy the clip into $parent (name ${path ?: "unavailable"})")
                    runCatching { tmp.delete() }
                    return "error:${ScreenRecorderEngine.REPLAY_ERR_WRITE_FAILED}"
                }
                // Percent-encoded, so a folder name with spaces ("TCP Clips") still makes a valid URI.
                val uri = android.net.Uri.fromFile(File(path))
                privileged.shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d ${shellQuote(uri.toString())} >/dev/null 2>&1")
                runCatching { tmp.delete() }
                // Replay Studio keeps listing this folder after the user picks another one.
                ClipRepository.rememberDir(prefs, dir)
                Log.i(tag, "saved replay clip -> $path")
                return path
            }
        }
    }

    companion object {
        const val PREF_GAME_AUDIO = "replay_game_audio"
        const val PREF_MIC = "replay_mic"

        /** Longest a save waits for audio to start flowing after arming. [save] runs off the main thread. */
        const val AUDIO_READY_TIMEOUT_MS = 500L

        /** Shell output markers: the folder listing finished, and the copy succeeded. */
        private const val LIST_END = "__CP_LS_END__"
        private const val COPY_OK = "__CP_COPY_OK__"
    }
}
