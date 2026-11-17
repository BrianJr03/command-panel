package jr.brian.commandpannel.privileged

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Display
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Replay-buffer screen capture (Command Panel's own, since the fork's capture engine was upstream code): a
 * `MediaCodec` H.264 encoder with a Surface input, fed by the root helper's [PrivilegedAccess.startDisplayMirror]
 * display mirror. Encoded packets drain into a [ReplayRingBuffer]; [slice] cuts a clip of the last window for saving.
 */
class ReplayCaptureEngine(
    private val context: Context,
    private val privileged: PrivilegedAccess,
) {
    private companion object {
        const val TAG = "ReplayCapture"
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        const val ENC_WIDTH = 1280
        const val ENC_HEIGHT = 720
    }

    private var codec: MediaCodec? = null
    private var ring: ReplayRingBuffer? = null
    private var drainThread: Thread? = null
    @Volatile private var running = false

    /** Encoder output format (with CSD) once known; required to mux a clip. */
    @Volatile
    var videoFormat: MediaFormat? = null
        private set

    val isRunning: Boolean get() = running

    @Synchronized
    fun start(windowSeconds: Int, preset: ReplayBitratePreset, fps: Int = 30): Boolean {
        if (running) return true
        val target = ScreenRecorderEngine.TARGET_TOP
        val bitRate = ScreenRecorderEngine.replayVideoBitRate(target, fps, preset)
        val capacity = ScreenRecorderEngine
            .estimateReplayBufferBytes(target, fps, preset, windowSeconds, withAudio = false)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val (srcW, srcH) = topDisplaySize()
        return try {
            val format = MediaFormat.createVideoFormat(MIME, ENC_WIDTH, ENC_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val encoder = MediaCodec.createEncoderByType(MIME)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()
            codec = encoder
            ring = ReplayRingBuffer(capacity, windowSeconds * 1_000_000L)

            val mirrored = privileged.startDisplayMirror(inputSurface, 0, srcW, srcH, ENC_WIDTH, ENC_HEIGHT)
            if (!mirrored) {
                Log.w(TAG, "display mirror failed; aborting capture")
                stop()
                return false
            }
            running = true
            drainThread = thread(name = "replay-drain") { drainLoop() }
            Log.i(TAG, "replay capture armed: ${ENC_WIDTH}x$ENC_HEIGHT @ ${bitRate / 1_000_000}Mbps, cap=${capacity / 1024 / 1024}MB")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "start failed", t)
            stop()
            false
        }
    }

    private fun drainLoop() {
        val encoder = codec ?: return
        val buffer = ring ?: return
        val info = MediaCodec.BufferInfo()
        while (running) {
            val index = try {
                encoder.dequeueOutputBuffer(info, 10_000)
            } catch (t: Throwable) {
                Log.w(TAG, "dequeue failed", t); break
            }
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> videoFormat = encoder.outputFormat
                index >= 0 -> {
                    val out = encoder.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !isConfig) {
                        val isKey = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        buffer.append(true, out, info.offset, info.size, info.presentationTimeUs, info.flags, isKey)
                    }
                    runCatching { encoder.releaseOutputBuffer(index, false) }
                }
            }
        }
    }

    /** Cuts a clip of the last [windowUs] (defaults to the armed window), or null if nothing is buffered yet. */
    fun slice(): ReplayRingBuffer.Slice? = ring?.slice()

    fun bufferedDurationUs(): Long = ring?.bufferedDurationUs() ?: 0L
    fun usedBytes(): Long = ring?.usedBytes() ?: 0L

    @Synchronized
    fun stop() {
        running = false
        runCatching { drainThread?.join(500) }
        drainThread = null
        privileged.stopDisplayMirror()
        codec?.let { enc ->
            runCatching { enc.stop() }
            runCatching { enc.release() }
        }
        codec = null
        ring = null
        videoFormat = null
    }

    private fun topDisplaySize(): Pair<Int, Int> {
        return runCatching {
            val dm = context.getSystemService(DisplayManager::class.java)
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val point = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            (if (point.x > 0) point.x else 1920) to (if (point.y > 0) point.y else 1080)
        }.getOrDefault(1920 to 1080)
    }
}
