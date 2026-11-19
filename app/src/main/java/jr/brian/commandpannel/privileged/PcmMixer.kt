package jr.brian.commandpannel.privileged

/** Pure 16-bit PCM helpers for the Replay Buffer's audio (unit tested on the JVM). */
object PcmMixer {
    /** Adds [b] into [a] sample by sample, saturating at the 16-bit range, over the first [count] samples. */
    fun mixInto(a: ShortArray, b: ShortArray, count: Int) {
        for (i in 0 until count) {
            a[i] = (a[i] + b[i]).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** Little-endian bytes of the first [count] samples of [src], into [dst] (2 * count bytes). */
    fun toBytes(src: ShortArray, count: Int, dst: ByteArray) {
        for (i in 0 until count) {
            val v = src[i].toInt()
            dst[2 * i] = v.toByte()
            dst[2 * i + 1] = (v shr 8).toByte()
        }
    }
}
