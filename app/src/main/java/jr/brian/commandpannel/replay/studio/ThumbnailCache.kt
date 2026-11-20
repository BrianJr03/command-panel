package jr.brian.commandpannel.replay.studio

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Disk cache of each clip's duration and thumbnail in `<cacheDir>/replay_thumbs/`: `<stem>.jpg` (a frame about 1 s in)
 * and `<stem>.meta` (`<durationMs>|<width>|<height>`), where the stem is [replayClipCacheName], so a rewritten clip gets
 * fresh entries. Used by [ClipRepository]; not thread-confined, but callers limit concurrent [decode]s.
 */
internal class ThumbnailCache(cacheDir: File) {

    /** What was read from a clip. A `null` [thumbnail] means no frame could be decoded. */
    data class Entry(val durationMs: Long, val width: Int, val height: Int, val thumbnail: File?)

    private val dir = File(cacheDir, DIR)

    /** The cached entry for [clip], or `null` if it hasn't been decoded yet. */
    fun cached(clip: ReplayClip): Entry? {
        val stem = replayClipCacheName(clip)
        val parts = runCatching { File(dir, "$stem.meta").readText().trim().split('|') }.getOrNull() ?: return null
        if (parts.size != 3) return null
        val duration = parts[0].toLongOrNull() ?: return null
        val w = parts[1].toIntOrNull() ?: return null
        val h = parts[2].toIntOrNull() ?: return null
        return Entry(duration, w, h, File(dir, "$stem.jpg").takeIf { it.exists() })
    }

    /** Decodes [clip] from [source] (a readable file with its content) and caches it. `null` if it can't be opened. */
    fun decode(clip: ReplayClip, source: File): Entry? {
        val stem = replayClipCacheName(clip)
        val thumb = File(dir, "$stem.jpg")
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val atUs = if (duration > THUMB_AT_MS) THUMB_AT_MS * 1000L else 0L
            val frame = retriever.getScaledFrameAtTime(
                atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_MAX_PX, THUMB_MAX_PX,
            )
            dir.mkdirs()
            val wrote = frame?.let { bmp ->
                runCatching {
                    FileOutputStream(thumb).use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                }.isSuccess.also { bmp.recycle() }
            } == true
            if (!wrote) runCatching { thumb.delete() }
            runCatching { File(dir, "$stem.meta").writeText("$duration|$w|$h") }
            Entry(duration, w, h, thumb.takeIf { wrote })
        } catch (t: Throwable) {
            Log.w(TAG, "can't open ${clip.path}", t)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Deletes entries whose stem isn't in [liveStems] (their clip is gone or changed). */
    fun prune(liveStems: Set<String>) {
        dir.listFiles()?.forEach { f -> if (f.nameWithoutExtension !in liveStems) runCatching { f.delete() } }
    }

    /** Deletes [clip]'s entry. */
    fun drop(clip: ReplayClip) {
        val stem = replayClipCacheName(clip)
        runCatching { File(dir, "$stem.jpg").delete() }
        runCatching { File(dir, "$stem.meta").delete() }
    }

    companion object {
        private const val TAG = "ReplayThumbs"
        const val DIR = "replay_thumbs"
        private const val THUMB_AT_MS = 1_000L
        private const val THUMB_MAX_PX = 640
        private const val JPEG_QUALITY = 85
    }
}
