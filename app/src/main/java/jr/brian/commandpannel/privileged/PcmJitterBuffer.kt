package jr.brian.commandpannel.privileged

/**
 * Keeps the mic in step with the game-audio loopback when the Replay Buffer records both (see `helper/AudioCapture`).
 * The loopback paces the capture loop; the mic runs on its own hardware clock, so the two drift apart slowly. Reading
 * the mic with blocking reads would let that drift pile up as mic latency (mic faster) or stall the loopback (mic
 * slower). Instead the mic is drained without blocking into this FIFO, and one frame is taken per loopback frame:
 *
 * - It starts (and restarts after running dry) only once [targetSamples] are buffered, so bursty mic delivery
 *   doesn't starve it. Until then [take] gives silence.
 * - Running dry pads the rest of the frame with silence.
 * - Going past [maxSamples] drops the oldest samples down to [targetSamples], so mic latency stays bounded.
 *
 * Counts are in samples (both channels interleaved); keep them multiples of the channel count. Not thread-safe; the
 * capture loop owns it. Pure, so it is unit tested on the JVM.
 */
class PcmJitterBuffer(private val targetSamples: Int, private val maxSamples: Int) {
    init {
        require(targetSamples in 0..maxSamples) { "target must be within 0..max" }
    }

    private val ring = ShortArray(maxSamples + 1)
    private var head = 0
    private var size = 0
    private var primed = false

    /** Samples currently buffered. */
    val available: Int get() = size

    /** Times the oldest samples were dropped to catch up (mic running fast). */
    var overflows = 0
        private set

    /** Times the buffer ran dry mid-frame (mic running slow). */
    var underflows = 0
        private set

    /** Appends the first [count] samples of [src]. */
    fun offer(src: ShortArray, count: Int) {
        var from = 0
        var n = count
        // More than fits even after a drop: only the newest samples matter.
        if (n > maxSamples) {
            from = n - targetSamples
            n = targetSamples
            clearSamples()
            overflows++
        } else if (size + n > maxSamples) {
            drop(size + n - targetSamples)
            overflows++
        }
        for (i in 0 until n) {
            ring[(head + size) % ring.size] = src[from + i]
            size++
        }
    }

    /**
     * Fills the first [count] samples of [dst]: buffered mic audio once primed, silence otherwise. Returns how many
     * samples were real mic audio.
     */
    fun take(dst: ShortArray, count: Int): Int {
        if (!primed && size >= targetSamples.coerceAtLeast(1)) primed = true
        if (!primed) {
            dst.fill(0, 0, count)
            return 0
        }
        val n = minOf(count, size)
        for (i in 0 until n) dst[i] = ring[(head + i) % ring.size]
        head = (head + n) % ring.size
        size -= n
        if (n < count) {
            dst.fill(0, n, count)
            underflows++
            primed = false
        }
        return n
    }

    private fun drop(samples: Int) {
        val n = samples.coerceIn(0, size)
        head = (head + n) % ring.size
        size -= n
    }

    private fun clearSamples() {
        head = 0
        size = 0
    }
}
