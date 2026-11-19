package jr.brian.commandpannel.privileged

import kotlin.math.abs

/**
 * Presentation timestamps for the Replay Buffer's audio, on the same monotonic clock as the video encoder's Surface
 * timestamps (`System.nanoTime()`). Counts frames from an anchor so timestamps step evenly, and re-anchors when the
 * count drifts more than [maxDriftUs] from the wall clock (a stall, dropped audio). Never goes backward.
 */
class AudioPtsClock(private val sampleRate: Int, private val maxDriftUs: Long = 40_000L) {
    private var anchorUs = -1L
    private var framesSinceAnchor = 0L
    private var lastPtsUs = -1L

    /** The timestamp for a chunk of [frames] frames that finished arriving at [nowUs]. */
    fun next(frames: Int, nowUs: Long): Long {
        val chunkUs = frames * 1_000_000L / sampleRate
        val wallStartUs = nowUs - chunkUs
        if (anchorUs < 0) {
            anchorUs = wallStartUs
            framesSinceAnchor = 0
        }
        var pts = anchorUs + framesSinceAnchor * 1_000_000L / sampleRate
        if (abs(pts - wallStartUs) > maxDriftUs) {
            anchorUs = wallStartUs
            framesSinceAnchor = 0
            pts = wallStartUs
        }
        if (pts <= lastPtsUs) pts = lastPtsUs + 1
        framesSinceAnchor += frames
        lastPtsUs = pts
        return pts
    }
}
