package jr.brian.commandpannel.privileged

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/** Muxes a [ReplayRingBuffer.Slice.Clip] of H.264 packets into a playable MP4 using the encoder's output format. */
object ReplayClipWriter {
    private const val TAG = "ReplayClipWriter"

    fun write(clip: ReplayRingBuffer.Slice.Clip, videoFormat: MediaFormat, outFile: File): Boolean {
        var muxer: MediaMuxer? = null
        return try {
            outFile.parentFile?.mkdirs()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTrack = muxer.addTrack(videoFormat)
            muxer.start()
            val info = MediaCodec.BufferInfo()
            for (packet in clip.packets) {
                if (!packet.isVideo) continue
                val flags = if (packet.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(packet.offset, packet.size, packet.ptsUs, flags)
                muxer.writeSampleData(videoTrack, clip.data, info)
            }
            muxer.stop()
            true
        } catch (t: Throwable) {
            Log.w(TAG, "mux failed", t)
            runCatching { outFile.delete() }
            false
        } finally {
            runCatching { muxer?.release() }
        }
    }
}
