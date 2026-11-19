package jr.brian.commandpannel.privileged

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The Replay Buffer's audio track: the root helper streams 48 kHz 16-bit stereo PCM (game audio and/or mic, see
 * `helper/AudioCapture`) into a pipe; this AAC-encodes it and appends the packets to the shared [ReplayRingBuffer]
 * alongside the video. Timestamps come from [AudioPtsClock], on the video's clock.
 */
class ReplayAudioEncoder(private val privileged: PrivilegedAccess, private val ring: ReplayRingBuffer) {
    private companion object {
        const val TAG = "ReplayAudio"
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        /** 20 ms of PCM, the helper's frame size. */
        const val CHUNK_BYTES = SAMPLE_RATE / 50 * CHANNELS * 2
    }

    @Volatile private var running = false
    private var codec: MediaCodec? = null
    private var input: InputStream? = null
    private var worker: Thread? = null

    /** Encoder output format (with the AAC config) once known; needed to mux the audio track. */
    @Volatile
    var format: MediaFormat? = null
        private set

    /** Released once the first AAC packet is in the ring (or the encoder stops), for [awaitFirstPacket]. */
    private val firstPacket = CountDownLatch(1)

    /**
     * Waits up to [timeoutMs] for the first encoded packet. Capture, the AAC encoder's priming and its output format
     * all take a moment after arming, so a clip saved right away would otherwise have no audio track. True if audio
     * is in the ring.
     */
    fun awaitFirstPacket(timeoutMs: Long): Boolean =
        firstPacket.await(timeoutMs, TimeUnit.MILLISECONDS) && running && format != null

    /** Starts capture of the requested sources; false if the helper couldn't open any of them. */
    fun start(game: Boolean, mic: Boolean): Boolean {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        val started = privileged.startAudioCapture(writeEnd, game, mic)
        // The helper holds its own copy of the write end now; ours would keep the pipe open after it stops.
        runCatching { writeEnd.close() }
        if (!started) {
            runCatching { readEnd.close() }
            return false
        }
        return try {
            val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, ScreenRecorderEngine.AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_BYTES * 2)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            codec = enc
            input = ParcelFileDescriptor.AutoCloseInputStream(readEnd)
            running = true
            worker = thread(name = "replay-audio-enc") { loop(enc, input!!) }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "AAC encoder failed to start", t)
            runCatching { readEnd.close() }
            stop()
            false
        }
    }

    fun stop() {
        running = false
        firstPacket.countDown()
        privileged.stopAudioCapture()
        // Closing our end unblocks a pending read and ends the helper's writes.
        runCatching { input?.close() }
        runCatching { worker?.join(500) }
        worker = null
        input = null
        codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        codec = null
        format = null
    }

    private fun loop(enc: MediaCodec, stream: InputStream) {
        val clock = AudioPtsClock(SAMPLE_RATE)
        val chunk = ByteArray(CHUNK_BYTES)
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                if (!readFull(stream, chunk)) break
                val pts = clock.next(CHUNK_BYTES / (CHANNELS * 2), System.nanoTime() / 1_000)
                val index = enc.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    enc.getInputBuffer(index)?.apply { clear(); put(chunk) }
                    enc.queueInputBuffer(index, 0, CHUNK_BYTES, pts, 0)
                }
                drain(enc, info)
            }
        } catch (t: Throwable) {
            if (running) Log.w(TAG, "audio encode loop ended", t)
        }
    }

    private fun drain(enc: MediaCodec, info: MediaCodec.BufferInfo) {
        while (true) {
            val index = enc.dequeueOutputBuffer(info, 0)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = enc.outputFormat
                index >= 0 -> {
                    val out = enc.getOutputBuffer(index)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !isConfig) {
                        ring.append(false, out, info.offset, info.size, info.presentationTimeUs, info.flags, false)
                        firstPacket.countDown()
                    }
                    enc.releaseOutputBuffer(index, false)
                }
                else -> return
            }
        }
    }

    private fun readFull(stream: InputStream, buf: ByteArray): Boolean {
        var n = 0
        while (n < buf.size) {
            val r = stream.read(buf, n, buf.size - n)
            if (r < 0) return false
            n += r
        }
        return true
    }
}
