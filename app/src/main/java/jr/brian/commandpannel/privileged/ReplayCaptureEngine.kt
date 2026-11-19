package jr.brian.commandpannel.privileged

import android.content.Context
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
 * display mirror. One screen is mirrored straight into the encoder; both screens are mirrored into a
 * [StackedMirrorCompositor], which draws them stacked into the encoder. Encoded packets drain into a
 * [ReplayRingBuffer]; [slice] cuts a clip of the last window for saving.
 */
class ReplayCaptureEngine(
    private val context: Context,
    private val privileged: PrivilegedAccess,
) {
    private companion object {
        const val TAG = "ReplayCapture"
        const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    }

    /** A physical screen to mirror: its SurfaceFlinger layer stack and real size. */
    private class Screen(val layerStack: Int, val width: Int, val height: Int)

    private val topology = jr.brian.commandpannel.system.DisplayTopology(context)
    private var codec: MediaCodec? = null
    private var compositor: StackedMirrorCompositor? = null
    private var ring: ReplayRingBuffer? = null
    private var audio: ReplayAudioEncoder? = null
    private var drainThread: Thread? = null
    @Volatile private var running = false

    /** Encoder output format (with CSD) once known; required to mux a clip. */
    @Volatile
    var videoFormat: MediaFormat? = null
        private set

    val isRunning: Boolean get() = running

    /** Audio encoder output format while audio is being captured; `null` for a video-only capture. */
    val audioFormat: MediaFormat? get() = audio?.format

    /** What the running capture records (names the saved clip). */
    @Volatile
    var target: ReplayTarget = ReplayTarget.DEFAULT
        private set

    @Synchronized
    fun start(windowSeconds: Int, preset: ReplayBitratePreset, target: ReplayTarget, sound: ReplayAudio = ReplayAudio.NONE, fps: Int = 30): Boolean {
        if (running) return true
        val top = topology.topDisplay?.let(::screenOf)
        val bottom = topology.bottomDisplay?.let(::screenOf)
        if (target != ReplayTarget.BOTTOM && top == null || target != ReplayTarget.TOP && bottom == null) {
            Log.w(TAG, "a display to capture is missing (target=$target)")
            return false
        }
        val layout = ScreenRecorderEngine.replayLayout(
            target, top?.width ?: 0, top?.height ?: 0, bottom?.width ?: 0, bottom?.height ?: 0,
        )
        val bitRate = ScreenRecorderEngine.replayVideoBitRate(target.mode, fps, preset)
        val capacity = ScreenRecorderEngine
            .estimateReplayBufferBytes(target.mode, fps, preset, windowSeconds, withAudio = sound.any)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return try {
            val format = MediaFormat.createVideoFormat(MIME, layout.width, layout.height).apply {
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

            val mirrored = if (target == ReplayTarget.BOTH) {
                mirrorBoth(inputSurface, layout, top!!, bottom!!, fps)
            } else {
                val screen = if (target == ReplayTarget.TOP) top!! else bottom!!
                privileged.startDisplayMirror(inputSurface, screen.layerStack, screen.width, screen.height, layout.width, layout.height)
            }
            if (!mirrored) {
                Log.w(TAG, "display mirror failed; aborting capture")
                stop()
                return false
            }
            // Audio is best effort: if the helper can't capture it, keep recording video.
            if (sound.any) {
                val enc = ReplayAudioEncoder(privileged, ring!!)
                if (enc.start(sound.game, sound.mic)) audio = enc else Log.w(TAG, "audio capture unavailable; video only")
            }
            this.target = target
            running = true
            drainThread = thread(name = "replay-drain") { drainLoop() }
            Log.i(TAG, "replay capture armed: $target audio=${audio != null} ${layout.width}x${layout.height} @ ${bitRate / 1_000_000}Mbps, cap=${capacity / 1024 / 1024}MB")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "start failed", t)
            stop()
            false
        }
    }

    /** Mirrors each screen into its own compositor input, which draws them stacked into [encoderInput]. */
    private fun mirrorBoth(
        encoderInput: android.view.Surface,
        layout: ScreenRecorderEngine.ReplayLayout,
        top: Screen,
        bottom: Screen,
        fps: Int,
    ): Boolean {
        val topRegion = layout.top ?: return false
        val bottomRegion = layout.bottom ?: return false
        val rects = listOf(topRegion, bottomRegion).map {
            android.graphics.Rect(it.left, it.top, it.left + it.width, it.top + it.height)
        }
        val comp = StackedMirrorCompositor(encoderInput, layout.width, layout.height, rects, fps)
        if (!comp.start()) return false
        compositor = comp
        return privileged.startDisplayMirror(comp.inputs[0], top.layerStack, top.width, top.height, topRegion.width, topRegion.height) &&
            privileged.addDisplayMirror(comp.inputs[1], bottom.layerStack, bottom.width, bottom.height, bottomRegion.width, bottomRegion.height)
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
        audio?.stop()
        audio = null
        runCatching { drainThread?.join(500) }
        drainThread = null
        privileged.stopDisplayMirror()
        compositor?.release()
        compositor = null
        codec?.let { enc ->
            runCatching { enc.stop() }
            runCatching { enc.release() }
        }
        codec = null
        ring = null
        videoFormat = null
    }

    private fun screenOf(display: Display): Screen {
        val size = topology.realSize(display)
        return Screen(layerStackOf(display), size.x.takeIf { it > 0 } ?: 1920, size.y.takeIf { it > 0 } ?: 1080)
    }

    /**
     * The display's SurfaceFlinger layer stack, from the hidden `DisplayInfo.layerStack` (the app is exempt from the
     * hidden-API checks). Falls back to the display id, which is what AOSP assigns as the layer stack.
     */
    private fun layerStackOf(display: Display): Int = runCatching {
        val infoClass = Class.forName("android.view.DisplayInfo")
        val info = infoClass.getConstructor().newInstance()
        Display::class.java.getMethod("getDisplayInfo", infoClass).invoke(display, info)
        infoClass.getField("layerStack").getInt(info)
    }.getOrElse {
        Log.w(TAG, "layer stack lookup failed for display ${display.displayId}", it)
        display.displayId
    }
}

/** Which sounds the Replay Buffer records alongside the video. */
data class ReplayAudio(val game: Boolean, val mic: Boolean) {
    val any: Boolean get() = game || mic

    companion object {
        val NONE = ReplayAudio(game = false, mic = false)
    }
}
