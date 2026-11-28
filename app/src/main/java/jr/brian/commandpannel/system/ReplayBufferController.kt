package jr.brian.commandpannel.system

import android.content.Context
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.ReplayAudio
import jr.brian.commandpannel.privileged.ReplayBitratePreset
import jr.brian.commandpannel.privileged.ReplayCaptureEngine
import jr.brian.commandpannel.privileged.ReplayTarget
import jr.brian.commandpannel.privileged.ReplayClipWriter
import jr.brian.commandpannel.privileged.ReplayClipsLocation
import jr.brian.commandpannel.privileged.ReplayVolumes
import jr.brian.commandpannel.privileged.ReplayRingBuffer
import jr.brian.commandpannel.privileged.RootShell
import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import jr.brian.commandpannel.replay.studio.ClipAutoDelete
import jr.brian.commandpannel.replay.studio.ClipRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Held while a save cuts its clip from the running capture, so [disarmAfterSave] (the screen going off, #91) waits
     * for that instead of pulling the ring out from under it. Muxing and copying the cut clip run outside it.
     */
    private val cutLock = Mutex()

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
     * Whether the last [save] went to TCP Clips because the chosen SD card or USB drive wasn't mounted (#75). The
     * chosen folder is kept, and clips go back to it once the card is back.
     */
    @Volatile var lastSaveFellBack: Boolean = false
        private set

    /**
     * The clips folder: the user's custom folder while it still exists, else TCP Clips. A custom folder deleted outside
     * the app is dropped (pref cleared) rather than silently recreated. If the check itself fails, the folder is kept.
     * A folder on a removable volume that isn't mounted now (#75) is kept, and this save goes to TCP Clips.
     */
    private suspend fun clipsLocation(mounted: Set<String>): ReplayClipsLocation {
        val custom = ReplayClipsLocation.parse(prefs.getString(ClipRepository.PREF_CLIPS_DIR, null))
            ?: return ReplayClipsLocation.DEFAULT
        if (custom.volume != null && custom.volume !in mounted) {
            Log.i(tag, "clips volume ${custom.volume} isn't mounted; saving to the default folder, keeping the choice")
            lastSaveFellBack = true
            return ReplayClipsLocation.DEFAULT
        }
        if (privileged.shell("[ -d ${RootShell.q(custom.path)} ] && echo yes || echo no")?.trim() != "no") return custom
        Log.i(tag, "custom clips folder '${custom.path}' is gone; saving to the default folder")
        prefs.edit().remove(ClipRepository.PREF_CLIPS_DIR).apply()
        return ReplayClipsLocation.DEFAULT
    }

    /**
     * Arms or disarms capture to match the `replay_enabled` pref. Without [allowArm] (the screen is off, #91) it only
     * disarms; the pref is left alone, so the buffer comes back on screen on.
     */
    fun applyEnabledState(allowArm: Boolean = true) {
        if (allowArm && prefs.getBoolean("replay_enabled", false)) arm() else disarm()
    }

    /** Persists the enabled pref and arms/disarms to match (see [applyEnabledState] for [allowArm]). */
    fun setEnabled(enable: Boolean, allowArm: Boolean = true) {
        prefs.edit().putBoolean("replay_enabled", enable).apply()
        applyEnabledState(allowArm)
    }

    fun arm(): Boolean = engine.start(windowSeconds(), preset(), target(), sound())

    fun disarm() = engine.stop()

    /** Disarms once a save under way has cut its clip, so the clip still saves (#91). */
    suspend fun disarmAfterSave() = cutLock.withLock { disarm() }

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

    /** Saves the buffered clip (it waits on audio and the root copy). Returns the saved path, or `error:<code>`. */
    suspend fun save(): String {
        // Everything read from the running capture is read here, under [cutLock]; the rest works on the cut clip.
        val (slice, format, audioFormat, mode) = cutLock.withLock {
            if (!engine.isRunning) return "error:${ScreenRecorderEngine.REPLAY_ERR_NOT_ARMED}"
            val format = engine.videoFormat ?: return "error:${ScreenRecorderEngine.REPLAY_ERR_START_FAILED}"
            // A save right after arming would otherwise beat the first audio packet and come out video only.
            engine.awaitAudio(AUDIO_READY_TIMEOUT_MS)
            Cut(engine.slice(), format, engine.audioFormat, engine.target.mode)
        }
        when (slice) {
            null, is ReplayRingBuffer.Slice.Failure -> {
                val code = (slice as? ReplayRingBuffer.Slice.Failure)?.error?.let {
                    if (it == ReplayRingBuffer.SliceError.NO_KEYFRAME) ScreenRecorderEngine.REPLAY_ERR_NO_KEYFRAME
                    else ScreenRecorderEngine.REPLAY_ERR_EMPTY
                } ?: ScreenRecorderEngine.REPLAY_ERR_EMPTY
                return "error:$code"
            }
            is ReplayRingBuffer.Slice.Clip -> {
                val tmp = File(context.cacheDir, "replay_tmp.mp4")
                if (!ReplayClipWriter.write(slice, format, audioFormat, tmp)) return "error:${ScreenRecorderEngine.REPLAY_ERR_WRITE_FAILED}"
                lastSaveFellBack = false
                val mounted = ReplayStorage.mountedIds(context)
                val location = clipsLocation(mounted)
                val basePath = ScreenRecorderEngine.buildReplayClipPath(
                    targetMode = mode,
                    baseDir = "${location.path}/${ScreenRecorderEngine.replayTargetFolder(mode)}",
                )
                if (!ScreenRecorderEngine.isValidReplayClipOutputPath(basePath, mounted)) {
                    runCatching { tmp.delete() }
                    return "error:${ScreenRecorderEngine.REPLAY_ERR_INVALID_PATH}"
                }
                val parent = basePath.substringBeforeLast('/')
                // Root lists the folder (the app can't always see it), so a second save in the same second gets `_2`.
                // The folder is made only while the volume's root is there (a card pulled since the check).
                val taken = privileged.shell("${ReplayVolumes.mkdirsCommand(location.root, parent)} && ls -1 ${RootShell.q(parent)} 2>/dev/null; echo $LIST_END")
                    ?.takeIf { it.contains(LIST_END) }
                    ?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                val path = taken?.let { ScreenRecorderEngine.pickFreeReplayClipPath(basePath, it) }
                    ?.takeIf { ScreenRecorderEngine.isValidReplayClipOutputPath(it, mounted) }
                val copied = path != null && privileged.shell(
                    ClipCopy.command(tmp.absolutePath, path, COPY_OK),
                    ClipCopy.TIMEOUT_MS,
                )?.contains(COPY_OK) == true
                if (!copied || path == null) {
                    Log.w(tag, "couldn't copy the clip into $parent (name ${path ?: "unavailable"})")
                    path?.let { privileged.shell(ClipCopy.cleanupCommand(it)) }
                    runCatching { tmp.delete() }
                    return "error:${ScreenRecorderEngine.REPLAY_ERR_WRITE_FAILED}"
                }
                // Percent-encoded, so a folder name with spaces ("TCP Clips") still makes a valid URI. The media scanner
                // indexes it on whichever volume it's on, so SD card clips show in the gallery too.
                val uri = android.net.Uri.fromFile(File(path))
                privileged.run("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", uri.toString())
                val bytes = tmp.length()
                runCatching { tmp.delete() }
                // Replay Studio keeps listing this folder after the user picks another one.
                ClipRepository.rememberDir(prefs, location)
                Log.i(tag, "saved replay clip -> $path")
                // Auto-delete old clips (#50), in the background so the save reports right away.
                ClipAutoDelete.afterSave(context, privileged, path, bytes)
                return path
            }
        }
    }

    /** What [save] reads from the running capture before it lets go of [cutLock]. */
    private data class Cut(
        val slice: ReplayRingBuffer.Slice?,
        val format: android.media.MediaFormat,
        val audioFormat: android.media.MediaFormat?,
        val mode: Int,
    )

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
