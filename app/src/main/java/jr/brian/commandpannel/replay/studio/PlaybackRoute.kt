package jr.brian.commandpannel.replay.studio

import jr.brian.commandpannel.privileged.ReplayTarget
import jr.brian.commandpannel.privileged.ScreenRecorderEngine
import jr.brian.commandpannel.privileged.ScreenRecorderEngine.Region
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a clip's video plays (#47). The player activity and its controls always stay on the top screen. */
enum class PlaybackRoute {
    /** The whole frame on the top screen (the default, and every fallback). */
    TOP,
    /** The whole frame on the lower screen; the top screen keeps the controls. */
    LOWER,
    /** A Both clip split back into its screens: the top half on top, the bottom half on the lower screen. */
    SPLIT,
}

/** Pref for "Play clips on the screen they were recorded on", off by default. */
const val PREF_PLAY_ON_SOURCE_SCREEN = "replay_play_on_source_screen"

/**
 * Where a clip from [origin] plays. With [playOnOrigin] off, or the lower screen not [lowerAvailable], everything plays
 * on top. Pure.
 */
fun routePlayback(origin: ClipOrigin, playOnOrigin: Boolean, lowerAvailable: Boolean): PlaybackRoute = when {
    !playOnOrigin || !lowerAvailable -> PlaybackRoute.TOP
    origin == ClipOrigin.BOTTOM -> PlaybackRoute.LOWER
    origin == ClipOrigin.BOTH -> PlaybackRoute.SPLIT
    else -> PlaybackRoute.TOP
}

/** What can keep the lower screen from showing a clip. Each one, when true, sends playback to the top screen. */
data class LowerScreenBlockers(
    /** No second display (or it's gone). */
    val missing: Boolean = false,
    /** The lower display is off (the lid is closed, or the screen is asleep). */
    val off: Boolean = false,
    /** Command Panel blanks the lower screen ("only top screen", fully black). */
    val blanked: Boolean = false,
    /** A Panel plugin (ThorKBM's keyboard, for one) has the lower screen. */
    val pluginSession: Boolean = false,
    /** ThorKBM is open on the lower screen as an app. */
    val keyboardApp: Boolean = false,
) {
    val available: Boolean get() = !missing && !off && !blanked && !pluginSession && !keyboardApp
}

/**
 * Where each screen sits in a Both clip's [videoWidth] x [videoHeight] frame, as the Replay Buffer laid it out
 * ([ScreenRecorderEngine.replayLayout]) from the screens' current sizes. Null when the frame doesn't match that layout
 * (recorded with other screen sizes, or not a Both clip), so the caller plays it whole instead of cropping wrongly.
 * A frame that's the same shape but another size (a re-encoded copy) is scaled to fit. Pure.
 */
fun bothCropRegions(
    videoWidth: Int,
    videoHeight: Int,
    topWidth: Int,
    topHeight: Int,
    bottomWidth: Int,
    bottomHeight: Int,
): Pair<Region, Region>? {
    if (videoWidth <= 0 || videoHeight <= 0) return null
    val layout = ScreenRecorderEngine.replayLayout(ReplayTarget.BOTH, topWidth, topHeight, bottomWidth, bottomHeight)
    val topRegion = layout.top ?: return null
    val bottomRegion = layout.bottom ?: return null
    val scale = videoWidth.toFloat() / layout.width
    // The height must follow from the width, give or take the encoder's 16-pixel alignment.
    if (abs(layout.height * scale - videoHeight) > CROP_TOLERANCE_PX) return null
    fun Region.scaled() = Region(
        (left * scale).roundToInt(),
        (top * scale).roundToInt(),
        (width * scale).roundToInt().coerceAtMost(videoWidth),
        (height * scale).roundToInt(),
    )
    val t = topRegion.scaled()
    val b = bottomRegion.scaled().let { it.copy(height = it.height.coerceAtMost(videoHeight - it.top)) }
    if (t.width <= 0 || t.height <= 0 || b.width <= 0 || b.height <= 0) return null
    return t to b
}

private const val CROP_TOLERANCE_PX = 16f

/**
 * [region] of a [videoWidth] x [videoHeight] frame as texture coordinates `[s0, t0, s1, t1]` for a SurfaceTexture: s
 * runs left to right, t bottom to top (the texture's transform matrix then maps them onto the decoded buffer). Pure.
 */
fun regionTexCoords(region: Region, videoWidth: Int, videoHeight: Int): FloatArray {
    val w = videoWidth.toFloat()
    val h = videoHeight.toFloat()
    return floatArrayOf(
        region.left / w,
        1f - (region.top + region.height) / h,
        (region.left + region.width) / w,
        1f - region.top / h,
    )
}

/**
 * The largest [srcWidth] x [srcHeight] shaped rect that fits centered in a [dstWidth] x [dstHeight] surface, as a
 * GL viewport `[x, y, width, height]` (the rest stays black). Pure.
 */
fun fitViewport(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): IntArray {
    if (srcWidth <= 0 || srcHeight <= 0 || dstWidth <= 0 || dstHeight <= 0) return intArrayOf(0, 0, dstWidth, dstHeight)
    val scale = minOf(dstWidth.toFloat() / srcWidth, dstHeight.toFloat() / srcHeight)
    val w = (srcWidth * scale).roundToInt().coerceIn(1, dstWidth)
    val h = (srcHeight * scale).roundToInt().coerceIn(1, dstHeight)
    return intArrayOf((dstWidth - w) / 2, (dstHeight - h) / 2, w, h)
}
