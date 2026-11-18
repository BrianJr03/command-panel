package jr.brian.commandpannel.privileged

import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Which screen(s) the Replay Buffer captures (`replay_target` pref); [mode] is the [ScreenRecorderEngine] target. */
enum class ReplayTarget(val key: String, val mode: Int, @StringRes val labelRes: Int) {
    TOP("top", ScreenRecorderEngine.TARGET_TOP, R.string.replay_target_top),
    BOTTOM("bottom", ScreenRecorderEngine.TARGET_BOTTOM, R.string.replay_target_bottom),
    BOTH("both", ScreenRecorderEngine.TARGET_BOTH, R.string.replay_target_both);

    companion object {
        val DEFAULT = TOP

        fun fromKey(key: String?): ReplayTarget = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

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
    const val DEFAULT_REPLAY_CLIPS_RELATIVE_DIR = "TCP Clips"
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

    /** Longest side of a single captured screen in the encoded clip; both screens share this width. */
    const val REPLAY_MAX_EDGE = 1280

    /** A screen's place in the encoded frame, in pixels from the top-left corner. */
    data class Region(val left: Int, val top: Int, val width: Int, val height: Int)

    /** The encoded frame size and where each captured screen goes in it (null for a screen that isn't captured). */
    data class ReplayLayout(val width: Int, val height: Int, val top: Region?, val bottom: Region?)

    /**
     * Encoder frame for [target], given each screen's real size. One screen is scaled to fit [REPLAY_MAX_EDGE] (never
     * up). Both screens are stacked like the device: the top screen above, the bottom one centered under it, at the
     * same scale (the Thor's panels have the same pixel density), with black around the smaller one. Frame sides are
     * multiples of 16, which every H.264 encoder accepts.
     */
    fun replayLayout(target: ReplayTarget, topW: Int, topH: Int, bottomW: Int, bottomH: Int): ReplayLayout {
        fun align16(v: Float) = ((v / 16f).roundToInt() * 16).coerceAtLeast(16)
        fun scaleFor(longest: Int) = minOf(1f, REPLAY_MAX_EDGE.toFloat() / longest)
        return when (target) {
            ReplayTarget.TOP, ReplayTarget.BOTTOM -> {
                val (w, h) = if (target == ReplayTarget.TOP) topW to topH else bottomW to bottomH
                val s = scaleFor(maxOf(w, h))
                val region = Region(0, 0, align16(w * s), align16(h * s))
                ReplayLayout(
                    region.width, region.height,
                    region.takeIf { target == ReplayTarget.TOP }, region.takeIf { target == ReplayTarget.BOTTOM },
                )
            }
            ReplayTarget.BOTH -> {
                val s = scaleFor(maxOf(topW, bottomW))
                val tw = (topW * s).roundToInt(); val th = (topH * s).roundToInt()
                val bw = (bottomW * s).roundToInt(); val bh = (bottomH * s).roundToInt()
                val width = (maxOf(tw, bw) + 15) / 16 * 16
                val height = (th + bh + 15) / 16 * 16
                ReplayLayout(
                    width, height,
                    Region((width - tw) / 2, 0, tw, th),
                    Region((width - bw) / 2, th, bw, bh),
                )
            }
        }
    }

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
        return "${baseDir.trimEnd('/')}/Replay_${timestampStr}_${replayTargetFolder(targetMode)}.mp4"
    }

    /** What a clip captured ("Top" / "Bottom" / "Both"): its subfolder under the clips folder and its file-name suffix. */
    fun replayTargetFolder(targetMode: Int): String = when (targetMode) {
        TARGET_TOP -> "Top"
        TARGET_BOTTOM -> "Bottom"
        else -> "Both"
    }

    /**
     * The clips folder for a folder picked in the system picker, from its tree document id (`"primary:Movies/Clips"`),
     * or `null` if clips can't go there: another volume (SD card), the storage root, anything
     * [normalizeReplayClipsRelativeDir] rejects, or too deep to fit the per-screen subfolder.
     */
    fun replayClipsDirFromTreeDocId(docId: String): String? {
        val volume = docId.substringBefore(':', missingDelimiterValue = "")
        if (volume != "primary") return null
        val dir = normalizeReplayClipsRelativeDir(docId.substringAfter(':')) ?: return null
        return dir.takeIf { it.split('/').size < MAX_REPLAY_DIR_DEPTH }
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
