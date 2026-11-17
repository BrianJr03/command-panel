package jr.brian.commandpannel.privileged

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replay-buffer sizing, bitrate and clip-path math (ported from the fork, feature 11). The actual screen capture is
 * Command Panel's own [ReplayCaptureEngine]; these pure helpers decide how big the ring buffer is, what bitrate to
 * encode at, and where a saved clip may be written.
 */
object ScreenRecorderEngine {

    // Capture targets.
    const val TARGET_TOP = 0
    const val TARGET_BOTTOM = 1
    const val TARGET_BOTH = 2

    const val AUDIO_BIT_RATE = 192_000

    const val REPLAY_STORAGE_ROOT = "/storage/emulated/0"
    const val DEFAULT_REPLAY_CLIPS_RELATIVE_DIR = "game_clips"
    const val REPLAY_CLIPS_DIR = "$REPLAY_STORAGE_ROOT/$DEFAULT_REPLAY_CLIPS_RELATIVE_DIR"
    private const val MAX_REPLAY_DIR_DEPTH = 8
    private const val MAX_REPLAY_DIR_SEGMENT_LENGTH = 64
    private val REPLAY_DIR_FORBIDDEN_CHARS = Regex("[\\\\:*?\"<>|\\u0000-\\u001F]")
    val REPLAY_WINDOW_OPTIONS_SECONDS = listOf(15, 30, 60, 90)
    const val DEFAULT_REPLAY_WINDOW_SECONDS = 30
    const val MAX_REPLAY_BUFFER_BYTES = 160L * 1024L * 1024L // 160 MB
    private const val MIN_REPLAY_BUFFER_BYTES = 8L * 1024L * 1024L
    private const val REPLAY_HEADROOM = 1.25
    private val REPLAY_CLIP_NAME_REGEX = Regex("^Replay_[0-9]{8}_[0-9]{6}(_[0-9]+)?_(Top|Bottom|Both)\\.mp4$")

    // Error / stop-reason codes shared with the app.
    const val REPLAY_ERR_ALREADY_ARMED = "already_armed"
    const val REPLAY_ERR_NOT_ARMED = "not_armed"
    const val REPLAY_ERR_SAVING = "saving"
    const val REPLAY_ERR_LOW_STORAGE = "low_storage"
    const val REPLAY_ERR_EMPTY = "empty"
    const val REPLAY_ERR_NO_KEYFRAME = "no_keyframe"
    const val REPLAY_ERR_INVALID_PATH = "invalid_path"
    const val REPLAY_ERR_START_FAILED = "start_failed"
    const val REPLAY_ERR_OUT_OF_MEMORY = "out_of_memory"
    const val REPLAY_ERR_WRITE_FAILED = "write_failed"

    fun normalizeReplayWindowSeconds(seconds: Int): Int =
        REPLAY_WINDOW_OPTIONS_SECONDS.minByOrNull { kotlin.math.abs(it - seconds) } ?: DEFAULT_REPLAY_WINDOW_SECONDS

    /** Screen Record video bitrate table (bps); the Replay Buffer scales it by its preset. */
    fun recordingVideoBitRate(targetMode: Int, fps: Int): Int = when (targetMode) {
        TARGET_TOP -> if (fps >= 60) 16_000_000 else 10_000_000
        TARGET_BOTTOM -> if (fps >= 60) 14_000_000 else 8_000_000
        else -> if (fps >= 60) 24_000_000 else 14_000_000
    }

    fun replayVideoBitRate(targetMode: Int, fps: Int, preset: ReplayBitratePreset): Int =
        (recordingVideoBitRate(targetMode, fps) * preset.bitRateScale).toInt()

    /**
     * Ring arena size: (video + audio bitrate) x (window + keyframe slack) + 25% headroom, clamped to
     * [MAX_REPLAY_BUFFER_BYTES]. This is the memory actually reserved while armed.
     */
    fun estimateReplayBufferBytes(
        targetMode: Int,
        fps: Int,
        preset: ReplayBitratePreset,
        windowSeconds: Int,
        withAudio: Boolean,
    ): Long {
        val bitsPerSecond = replayVideoBitRate(targetMode, fps, preset).toLong() + if (withAudio) AUDIO_BIT_RATE else 0
        val seconds = windowSeconds + ReplayRingBuffer.DEFAULT_SLACK_US / 1_000_000.0
        val bytes = (bitsPerSecond / 8.0 * seconds * REPLAY_HEADROOM).toLong()
        return bytes.coerceIn(MIN_REPLAY_BUFFER_BYTES, MAX_REPLAY_BUFFER_BYTES)
    }

    /** Window actually covered when [estimateReplayBufferBytes] hits the 160 MB ceiling. */
    fun effectiveReplayWindowSeconds(
        targetMode: Int,
        fps: Int,
        preset: ReplayBitratePreset,
        windowSeconds: Int,
        withAudio: Boolean,
    ): Int {
        val bitsPerSecond = replayVideoBitRate(targetMode, fps, preset).toLong() + if (withAudio) AUDIO_BIT_RATE else 0
        val bytesPerSecond = bitsPerSecond / 8.0 * REPLAY_HEADROOM
        val coverable = MAX_REPLAY_BUFFER_BYTES / bytesPerSecond - ReplayRingBuffer.DEFAULT_SLACK_US / 1_000_000.0
        return minOf(windowSeconds, coverable.toInt().coerceAtLeast(1))
    }

    /**
     * Normalizes a folder relative to [REPLAY_STORAGE_ROOT], or returns `null` if it is not allowed: empty,
     * `.` / `..` / hidden segments, illegal characters, anything under `Android/`, or too deep.
     */
    fun normalizeReplayClipsRelativeDir(raw: String?): String? {
        val segments = raw.orEmpty().trim().trim('/').split('/').map { it.trim() }
        if (segments.isEmpty() || segments.size > MAX_REPLAY_DIR_DEPTH) return null
        for (segment in segments) {
            if (segment.isEmpty() ||
                segment.startsWith(".") ||
                segment.length > MAX_REPLAY_DIR_SEGMENT_LENGTH ||
                REPLAY_DIR_FORBIDDEN_CHARS.containsMatchIn(segment)
            ) {
                return null
            }
        }
        if (segments.first().equals("Android", ignoreCase = true)) return null
        return segments.joinToString("/")
    }

    fun buildReplayClipPath(
        timestampStr: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()),
        targetMode: Int,
        baseDir: String = REPLAY_CLIPS_DIR,
    ): String {
        val suffix = when (targetMode) {
            TARGET_TOP -> "Top"
            TARGET_BOTTOM -> "Bottom"
            else -> "Both"
        }
        return "${baseDir.trimEnd('/')}/Replay_${timestampStr}_${suffix}.mp4"
    }

    /** Confined to a [normalizeReplayClipsRelativeDir]-valid folder on internal shared storage with a strict leaf name. */
    internal fun isValidReplayClipOutputPath(path: String): Boolean {
        val root = PrivilegedHelperMain.DEFAULT_STORAGE_ROOTS.firstOrNull { path.startsWith("$it/") } ?: return false
        val relative = path.removePrefix("$root/")
        val leaf = relative.substringAfterLast('/')
        if (!REPLAY_CLIP_NAME_REGEX.matches(leaf)) return false
        val relativeDir = relative.substringBeforeLast('/', missingDelimiterValue = "")
        val normalizedDir = normalizeReplayClipsRelativeDir(relativeDir) ?: return false
        if (normalizedDir != relativeDir) return false
        return PrivilegedHelperMain.isValidScopedMediaOutputPath(
            path = path,
            subDirs = normalizedDir.split('/'),
            requiredExtension = ".mp4",
        )
    }
}
