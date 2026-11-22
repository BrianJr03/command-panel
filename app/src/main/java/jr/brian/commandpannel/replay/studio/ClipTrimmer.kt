package jr.brian.commandpannel.replay.studio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootShell
import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** A cut span in a clip, in microseconds from the clip's first frame. */
data class TrimSpan(val startUs: Long, val endUs: Long) {
    val durationUs: Long get() = endUs - startUs
}

/**
 * The span to cut for in/out points [inUs] and [outUs] (either order) in a clip [durationUs] long: both clamped into
 * the clip, then widened to at least [minUs] (past the out point first, then before the in point when the out point
 * hits the end). `null` when the clip itself is shorter than [minUs]. Pure.
 */
fun clampTrimSpan(inUs: Long, outUs: Long, durationUs: Long, minUs: Long = ClipTrimmer.MIN_TRIM_US): TrimSpan? {
    if (durationUs < minUs || durationUs <= 0L) return null
    var start = minOf(inUs, outUs).coerceIn(0L, durationUs)
    var end = maxOf(inUs, outUs).coerceIn(0L, durationUs)
    if (end - start < minUs) {
        end = minOf(start + minUs, durationUs)
        start = maxOf(0L, end - minUs)
    }
    return TrimSpan(start, end)
}

/**
 * Where a cut that should start at [startUs] really starts: the last keyframe at or before it (a copy without
 * re-encoding can only start on a keyframe), or the first keyframe when [startUs] is before all of them. `null` when
 * there are no keyframes. [keyframesUs] needn't be sorted. Pure.
 */
fun snapToKeyframeUs(keyframesUs: List<Long>, startUs: Long): Long? {
    if (keyframesUs.isEmpty()) return null
    return keyframesUs.filter { it <= startUs }.maxOrNull() ?: keyframesUs.min()
}

/** Why a trim failed, with the message the player shows. */
enum class TrimError(@StringRes val messageRes: Int) {
    /** The clip couldn't be read (gone, or the privileged shell is off). */
    UNREADABLE(R.string.player_trim_error_unreadable),

    /** No video track or no keyframes. */
    NO_VIDEO(R.string.player_trim_error_no_video),

    /** The clip is shorter than [ClipTrimmer.MIN_TRIM_US]. */
    TOO_SHORT(R.string.player_trim_error_too_short),

    /** Copying the samples into a new file failed. */
    MUX_FAILED(R.string.player_trim_error_mux),

    /** No valid free name for the copy in the clip's folder. */
    INVALID_PATH(R.string.player_trim_error_name),

    /** The copy couldn't be put next to the clip. */
    COPY_FAILED(R.string.player_trim_error_copy),
}

sealed interface TrimResult {
    /**
     * The trimmed copy, saved next to the source.
     *
     * @property startMs where the copy really starts in the source (at or before the in point).
     * @property earlyMs how much earlier than the in point it starts, from snapping to a keyframe.
     * @property durationMs the copy's length.
     */
    data class Saved(val clip: ReplayClip, val startMs: Long, val earlyMs: Long, val durationMs: Long) : TrimResult

    data class Failed(val error: TrimError) : TrimResult
}

/**
 * Cuts a shorter copy of a Replay Studio clip without re-encoding: [MediaExtractor] samples straight into a
 * [MediaMuxer], like [jr.brian.commandpannel.privileged.ReplayClipWriter]. The cut starts on the last video keyframe
 * at or before the in point and copies audio for the same span; timestamps start at zero.
 *
 * Clips live on shared storage and are written by root, so the copy is muxed into the app cache from
 * [ClipRepository.readableFile] and then root-copied next to the source as `Replay_<ts>[_n]_<screen>_trim.mp4`
 * (never over an existing file), the same way `ReplayBufferController.save()` places a new clip. The source is
 * never changed. Main-safe; one trim runs at a time.
 */
class ClipTrimmer(
    context: Context,
    private val privileged: PrivilegedAccess,
    private val repo: ClipRepository = ClipRepository(context, privileged),
) {
    private val app = context.applicationContext ?: context
    private val workDir = File(app.cacheDir, WORK_DIR)

    /** Trims [clip] to the in/out points [inMs]..[outMs] (from its first frame) and saves the copy beside it. */
    suspend fun trim(clip: ReplayClip, inMs: Long, outMs: Long): TrimResult = withContext(Dispatchers.IO) {
        running.withLock {
            val source = repo.readableFile(clip) ?: return@withLock TrimResult.Failed(TrimError.UNREADABLE)
            workDir.mkdirs()
            val tmp = File(workDir, "trim_${System.nanoTime()}.mp4")
            try {
                val cut = when (val m = mux(source, inMs * 1000L, outMs * 1000L, tmp)) {
                    is MuxOutcome.Done -> m
                    is MuxOutcome.Error -> return@withLock TrimResult.Failed(m.error)
                }
                val path = place(clip, tmp) ?: return@withLock TrimResult.Failed(TrimError.COPY_FAILED)
                val saved = parseReplayClip(
                    name = path.substringAfterLast('/'),
                    dir = path.substringBeforeLast('/'),
                    sizeBytes = tmp.length(),
                    modifiedMs = System.currentTimeMillis(),
                ) ?: return@withLock TrimResult.Failed(TrimError.INVALID_PATH)
                Log.i(TAG, "trimmed ${clip.path} -> $path (${cut.startUs / 1000}..${cut.endUs / 1000} ms)")
                TrimResult.Saved(
                    clip = saved,
                    startMs = cut.startUs / 1000L,
                    earlyMs = ((inMs * 1000L - cut.startUs).coerceAtLeast(0L)) / 1000L,
                    durationMs = (cut.endUs - cut.startUs) / 1000L,
                )
            } catch (e: PlaceError) {
                TrimResult.Failed(e.error)
            } finally {
                runCatching { tmp.delete() }
            }
        }
    }

    private sealed interface MuxOutcome {
        /** The span written, from the source's first frame: [startUs] is the keyframe the copy starts on. */
        data class Done(val startUs: Long, val endUs: Long) : MuxOutcome
        data class Error(val error: TrimError) : MuxOutcome
    }

    private class PlaceError(val error: TrimError) : Exception()

    private fun mux(source: File, inUs: Long, outUs: Long, dest: File): MuxOutcome {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(source.absolutePath)
            var videoIndex = -1
            var audioIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                if (videoIndex < 0 && mime.startsWith("video/")) videoIndex = i
                if (audioIndex < 0 && mime.startsWith("audio/")) audioIndex = i
            }
            if (videoIndex < 0) return MuxOutcome.Error(TrimError.NO_VIDEO)
            val videoFormat = extractor.getTrackFormat(videoIndex)

            // One pass over the video track's sample table (no sample data) for keyframes and the first frame.
            extractor.selectTrack(videoIndex)
            val keyframes = ArrayList<Long>()
            var firstUs = Long.MAX_VALUE
            var lastUs = Long.MIN_VALUE
            while (true) {
                val t = extractor.sampleTime
                if (t < 0) break
                firstUs = minOf(firstUs, t)
                lastUs = maxOf(lastUs, t)
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) keyframes += t
                if (!extractor.advance()) break
            }
            if (keyframes.isEmpty()) return MuxOutcome.Error(TrimError.NO_VIDEO)
            val durationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                videoFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                lastUs - firstUs
            }
            val span = clampTrimSpan(inUs, outUs, durationUs) ?: return MuxOutcome.Error(TrimError.TOO_SHORT)
            val startAbs = snapToKeyframeUs(keyframes, firstUs + span.startUs)
                ?: return MuxOutcome.Error(TrimError.NO_VIDEO)
            val endAbs = firstUs + span.endUs

            muxer = MediaMuxer(dest.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                muxer.setOrientationHint(videoFormat.getInteger(MediaFormat.KEY_ROTATION))
            }
            val outVideo = muxer.addTrack(videoFormat)
            val outAudio = if (audioIndex >= 0) muxer.addTrack(extractor.getTrackFormat(audioIndex)) else -1
            muxer.start()
            started = true

            if (audioIndex >= 0) extractor.selectTrack(audioIndex)
            extractor.seekTo(startAbs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var buffer = ByteBuffer.allocateDirect(bufferSize(videoFormat, audioIndex, extractor))
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = audioIndex < 0
            var videoWritten = 0
            while (!(videoDone && audioDone)) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val t = extractor.sampleTime
                val isVideo = track == videoIndex
                // Decode order runs ahead of the out point once, so stop each track at its first sample past it.
                if (t >= endAbs) {
                    if (isVideo) videoDone = true else audioDone = true
                } else if (t >= startAbs && (if (isVideo) !videoDone else !audioDone)) {
                    val needed = extractor.sampleSize
                    if (needed > buffer.capacity()) buffer = ByteBuffer.allocateDirect(needed.toInt())
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size >= 0) {
                        val keyframe = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                        info.set(0, size, t - startAbs, if (keyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        muxer.writeSampleData(if (isVideo) outVideo else outAudio, buffer, info)
                        if (isVideo) videoWritten++
                    }
                }
                if (!extractor.advance()) break
            }
            if (videoWritten == 0) return MuxOutcome.Error(TrimError.MUX_FAILED)
            muxer.stop()
            started = false
            // The span is clamped to the clip, so the copy runs to the out point.
            return MuxOutcome.Done(startUs = startAbs - firstUs, endUs = span.endUs)
        } catch (t: Throwable) {
            Log.w(TAG, "trim mux failed for ${source.name}", t)
            runCatching { dest.delete() }
            return MuxOutcome.Error(TrimError.MUX_FAILED)
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun bufferSize(videoFormat: MediaFormat, audioIndex: Int, extractor: MediaExtractor): Int {
        fun max(format: MediaFormat) =
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
        val audioMax = if (audioIndex >= 0) max(extractor.getTrackFormat(audioIndex)) else 0
        return maxOf(MIN_BUFFER_BYTES, max(videoFormat), audioMax)
    }

    /**
     * Root-copies [tmp] next to [clip] under the first free trim name, then tells the media scanner. Returns the new
     * path, or `null` when the copy failed. Throws [PlaceError] with [TrimError.INVALID_PATH] when no valid name is
     * free.
     */
    private fun place(clip: ReplayClip, tmp: File): String? {
        // A trim goes next to its clip, which may be on a mounted SD card (#75).
        val mounted = jr.brian.commandpannel.system.ReplayStorage.mountedIds(app)
        val base = ScreenRecorderEngine.replayTrimClipPath(clip.path)
            ?.takeIf { ScreenRecorderEngine.isValidReplayClipOutputPath(it, mounted) }
            ?: throw PlaceError(TrimError.INVALID_PATH)
        val parent = RootShell.q(clip.dir)
        // Root lists the folder (the app can't always see it), the same way a save picks `_2`.
        val taken = privileged.shell("ls -1 $parent 2>/dev/null; echo $LIST_END")
            ?.takeIf { it.contains(LIST_END) }
            ?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: return null
        val path = ScreenRecorderEngine.pickFreeReplayClipPath(base, taken)
            ?.takeIf { ScreenRecorderEngine.isValidReplayClipOutputPath(it, mounted) }
            ?: throw PlaceError(TrimError.INVALID_PATH)
        // Keeps a file that appeared since the listing; this never overwrites anything.
        val copied = privileged.shell(
            jr.brian.commandpannel.system.ClipCopy.command(tmp.absolutePath, path, COPY_OK),
            jr.brian.commandpannel.system.ClipCopy.TIMEOUT_MS,
        )?.contains(COPY_OK) == true
        if (!copied) {
            Log.w(TAG, "couldn't copy the trim into ${clip.dir}")
            privileged.shell(jr.brian.commandpannel.system.ClipCopy.cleanupCommand(path))
            return null
        }
        val uri = RootShell.q(Uri.fromFile(File(path)).toString())
        privileged.shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d $uri >/dev/null 2>&1")
        return path
    }

    companion object {
        private const val TAG = "ClipTrimmer"

        /** The shortest copy: a trim shorter than this is widened to it. */
        const val MIN_TRIM_US = 1_000_000L

        private const val WORK_DIR = "replay_trim"
        private const val MIN_BUFFER_BYTES = 1024 * 1024
        private const val LIST_END = "__CP_LS_END__"
        private const val COPY_OK = "__CP_COPY_OK__"

        /** One trim at a time across instances: each holds a muxer and a cache file. */
        private val running = Mutex()
    }
}
