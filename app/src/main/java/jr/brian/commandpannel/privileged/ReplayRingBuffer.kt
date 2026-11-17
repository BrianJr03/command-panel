package jr.brian.commandpannel.privileged

import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import java.nio.ByteBuffer

/** Bitrate preset for the Replay Buffer, as a fraction of the normal Screen Record bitrate. */
enum class ReplayBitratePreset(val key: String, @StringRes val labelRes: Int, val bitRateScale: Float) {
    HIGH("high", R.string.replay_quality_high, 1.0f),
    BALANCED("balanced", R.string.replay_quality_balanced, 0.5f),
    LOW("low", R.string.replay_quality_low, 0.25f);

    companion object {
        val DEFAULT = BALANCED

        fun fromKey(key: String?): ReplayBitratePreset = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Fixed-capacity in-memory ring of encoded audio/video packets for the Replay Buffer.
 *
 * - Packet bytes live in one preallocated direct [ByteBuffer] arena (no per-frame allocations,
 *   no Java heap / GC churn at 60 fps); a small index holds offset, size, timestamp, and flags.
 * - Packets are evicted from the head when they are older than `window + slack` relative to the
 *   newest timestamp, or when a new packet does not fit in the arena (byte cap).
 * - [slice] cuts a playable clip of the last `window`: it starts at the latest video keyframe at
 *   or before `newest - window` (so the clip is never shorter than the window when enough is
 *   buffered), falling back to the first keyframe after it, drops audio before that keyframe,
 *   and rebases every timestamp so the clip starts at 0.
 *
 * Thread-safe; [slice] copies under the lock so capture never stops while a clip is written.
 * Codec config (CSD) is not stored here: encoders report it via their output `MediaFormat`.
 */
class ReplayRingBuffer(
    val capacityBytes: Int,
    @Volatile var windowUs: Long,
    private val slackUs: Long = DEFAULT_SLACK_US
) {

    companion object {
        /** Extra history kept beyond the window so a keyframe at or before the window start exists (I-frame interval is 1 s). */
        const val DEFAULT_SLACK_US = 2_000_000L
    }

    private class Entry(
        val offset: Int,
        val size: Int,
        val ptsUs: Long,
        val flags: Int,
        val isVideo: Boolean,
        val isKeyFrame: Boolean
    )

    data class Packet(
        val isVideo: Boolean,
        val offset: Int,
        val size: Int,
        val ptsUs: Long,
        val flags: Int,
        val isKeyFrame: Boolean
    )

    enum class SliceError { EMPTY, NO_KEYFRAME }

    sealed class Slice {
        /** Packets in original interleaved order; [data] holds their bytes at each packet's offset. */
        class Clip(val packets: List<Packet>, val data: ByteBuffer, val durationUs: Long) : Slice() {
            val hasAudio: Boolean get() = packets.any { !it.isVideo }
        }

        data class Failure(val error: SliceError) : Slice()
    }

    private val lock = Any()
    private val arena: ByteBuffer = ByteBuffer.allocateDirect(capacityBytes)
    private val entries = ArrayDeque<Entry>()
    private var usedBytes: Long = 0L
    private var newestPtsUs: Long = Long.MIN_VALUE

    /**
     * Appends one encoded packet. Returns `false` (and stores nothing) when the packet alone is
     * larger than the arena.
     */
    fun append(
        isVideo: Boolean,
        src: ByteBuffer,
        offset: Int,
        size: Int,
        ptsUs: Long,
        flags: Int,
        isKeyFrame: Boolean
    ): Boolean {
        if (size <= 0 || size > capacityBytes) return false
        synchronized(lock) {
            var position = allocateLocked(size)
            while (position < 0) {
                evictHeadLocked()
                position = allocateLocked(size)
            }
            val view = arena.duplicate()
            view.position(position)
            val source = src.duplicate()
            source.limit(offset + size)
            source.position(offset)
            view.put(source)
            entries.addLast(Entry(position, size, ptsUs, flags, isVideo, isKeyFrame))
            usedBytes += size
            if (ptsUs > newestPtsUs) newestPtsUs = ptsUs
            val oldestAllowed = newestPtsUs - (windowUs + slackUs)
            while (entries.size > 1 && entries.first().ptsUs < oldestAllowed) {
                evictHeadLocked()
            }
            return true
        }
    }

    /** Returns an arena offset where [size] bytes fit contiguously, or -1 if the head must be evicted. */
    private fun allocateLocked(size: Int): Int {
        if (entries.isEmpty()) return 0
        val head = entries.first().offset
        val last = entries.last()
        val tailEnd = last.offset + last.size
        return if (last.offset >= head) {
            // Not wrapped: free space after the tail, then before the head
            when {
                capacityBytes - tailEnd >= size -> tailEnd
                head >= size -> 0
                else -> -1
            }
        } else {
            // Wrapped: the only free gap is between the tail and the head
            if (head - tailEnd >= size) tailEnd else -1
        }
    }

    private fun evictHeadLocked() {
        val removed = entries.removeFirst()
        usedBytes -= removed.size
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            usedBytes = 0L
            newestPtsUs = Long.MIN_VALUE
        }
    }

    fun usedBytes(): Long = synchronized(lock) { usedBytes }

    fun packetCount(): Int = synchronized(lock) { entries.size }

    /** Span of buffered video, newest minus oldest video timestamp. */
    fun bufferedDurationUs(): Long = synchronized(lock) {
        val oldest = entries.firstOrNull { it.isVideo } ?: return 0L
        val newest = entries.lastOrNull { it.isVideo } ?: return 0L
        (newest.ptsUs - oldest.ptsUs).coerceAtLeast(0L)
    }

    fun slice(windowUs: Long = this.windowUs): Slice {
        synchronized(lock) {
            val list = entries.toList()
            val newestVideo = list.lastOrNull { it.isVideo } ?: return Slice.Failure(SliceError.EMPTY)
            val windowStart = newestVideo.ptsUs - windowUs
            var keyIndex = -1
            for (i in list.indices) {
                val e = list[i]
                if (e.isVideo && e.isKeyFrame && e.ptsUs <= windowStart) keyIndex = i
            }
            if (keyIndex < 0) {
                keyIndex = list.indexOfFirst { it.isVideo && it.isKeyFrame && it.ptsUs > windowStart }
            }
            if (keyIndex < 0) return Slice.Failure(SliceError.NO_KEYFRAME)
            val basePts = list[keyIndex].ptsUs

            val selected = list.filterIndexed { i, e ->
                if (e.isVideo) i >= keyIndex else e.ptsUs >= basePts
            }
            val total = selected.sumOf { it.size.toLong() }.toInt()
            val data = ByteBuffer.allocateDirect(total.coerceAtLeast(1))
            val packets = ArrayList<Packet>(selected.size)
            var cursor = 0
            for (e in selected) {
                val src = arena.duplicate()
                src.limit(e.offset + e.size)
                src.position(e.offset)
                data.position(cursor)
                data.put(src)
                packets.add(Packet(e.isVideo, cursor, e.size, e.ptsUs - basePts, e.flags, e.isKeyFrame))
                cursor += e.size
            }
            data.clear()
            val duration = (selected.maxOf { it.ptsUs } - basePts).coerceAtLeast(0L)
            return Slice.Clip(packets, data, duration)
        }
    }
}
