package jr.brian.commandpannel.system

import kotlin.math.max
import kotlin.math.roundToInt

/** A pixel size (kept free of Android types so the rules are unit-testable). */
data class PxSize(val width: Int, val height: Int)

/** Where a center crop sits inside an image scaled to [scaled]: the [crop] window is the target size. */
data class CoverPlan(val scaled: PxSize, val cropLeft: Int, val cropTop: Int, val crop: PxSize)

/** What an import does with a picked file. */
sealed interface ImportPlan {
    /** Store the file as it is. */
    data object Keep : ImportPlan
    /** A big still photo: re-encode it at [size] (it still covers twice the screen). */
    data class Downscale(val size: PxSize) : ImportPlan
    /** Refuse it; [reason] picks the message. */
    data class Reject(val reason: RejectReason) : ImportPlan
}

enum class RejectReason { TOO_LARGE_FILE, TOO_LARGE_ANIMATION, NOT_AN_IMAGE, UNSUPPORTED_TYPE }

/** Limits and geometry for background images (#57). Pure, so it is unit-tested. */
object BackgroundImageRules {
    /** Files over this are refused before decoding. */
    const val MAX_BYTES = 20L * 1024 * 1024
    /** Neither side may exceed this many screens; still images are downscaled to it, animations refused. */
    const val MAX_SCREENS = 2
    /** The fastest an animation is drawn (30 fps). */
    const val MIN_FRAME_MS = 33L

    /** The types Theme Studio imports: PNG, JPG, WebP (still or animated) and GIF. */
    val MIME_TYPES = arrayOf("image/png", "image/jpeg", "image/webp", "image/gif")

    fun isSupportedMime(mime: String?): Boolean = mime != null && mime.lowercase() in MIME_TYPES.toSet()

    /** Decides how to import a file of [bytes] whose (EXIF-rotated) size is [image], for a bottom screen of [screen]. */
    fun plan(bytes: Long, image: PxSize, animated: Boolean, screen: PxSize, mime: String?): ImportPlan {
        if (bytes > MAX_BYTES) return ImportPlan.Reject(RejectReason.TOO_LARGE_FILE)
        if (image.width <= 0 || image.height <= 0) return ImportPlan.Reject(RejectReason.NOT_AN_IMAGE)
        if (!isSupportedMime(mime)) return ImportPlan.Reject(RejectReason.UNSUPPORTED_TYPE)
        val maxW = screen.width * MAX_SCREENS
        val maxH = screen.height * MAX_SCREENS
        if (image.width <= maxW && image.height <= maxH) return ImportPlan.Keep
        if (animated) return ImportPlan.Reject(RejectReason.TOO_LARGE_ANIMATION)
        // Shrink just enough that the image still covers twice the screen, so the crop at draw time stays sharp.
        val scale = max(maxW.toFloat() / image.width, maxH.toFloat() / image.height)
        if (scale >= 1f) return ImportPlan.Keep
        return ImportPlan.Downscale(
            PxSize((image.width * scale).roundToInt().coerceAtLeast(1), (image.height * scale).roundToInt().coerceAtLeast(1)),
        )
    }

    /**
     * Center-crop to fill [target] without stretching: scale [image] so it covers [target] on both axes, then cut the
     * middle [target]-sized window out of it.
     */
    fun cover(image: PxSize, target: PxSize): CoverPlan {
        val tw = target.width.coerceAtLeast(1)
        val th = target.height.coerceAtLeast(1)
        if (image.width <= 0 || image.height <= 0) return CoverPlan(PxSize(tw, th), 0, 0, PxSize(tw, th))
        val scale = max(tw.toFloat() / image.width, th.toFloat() / image.height)
        val sw = (image.width * scale).roundToInt().coerceAtLeast(tw)
        val sh = (image.height * scale).roundToInt().coerceAtLeast(th)
        return CoverPlan(PxSize(sw, sh), (sw - tw) / 2, (sh - th) / 2, PxSize(tw, th))
    }

    /**
     * The size to decode [image] at to fill [target] without upscaling it: [target] itself when the image is big
     * enough, else the target's shape at the image's own scale (the GPU scales it up when drawing). Animations use it,
     * since each of their frames is drawn and uploaded again.
     */
    fun renderSize(image: PxSize, target: PxSize): PxSize {
        if (image.width <= 0 || image.height <= 0 || target.width <= 0 || target.height <= 0) return target
        val scale = max(target.width.toFloat() / image.width, target.height.toFloat() / image.height)
        if (scale <= 1f) return target
        return PxSize(
            (target.width / scale).roundToInt().coerceIn(1, image.width),
            (target.height / scale).roundToInt().coerceIn(1, image.height),
        )
    }

    /**
     * Whether an animation draws on its own surface under the panel (#60) instead of in the panel's window: only while
     * it is playing in the overlay, the panel has finished its show animation (a surface doesn't follow the window's
     * fade and slide), and a first frame exists to put in it (so the backdrop shows until then, never an empty hole).
     * [settled] is `null` outside the overlay (previews).
     */
    fun drawsOnSurface(playing: Boolean, settled: Boolean?, hasFirstFrame: Boolean): Boolean =
        playing && settled == true && hasFirstFrame

    /** When the next frame may be drawn: no sooner than [MIN_FRAME_MS] after the last one, nor in the past. */
    fun nextFrameAt(requestedAt: Long, lastFrameAt: Long, now: Long): Long =
        maxOf(requestedAt, lastFrameAt + MIN_FRAME_MS, now)

    /** `<name>-<tag>.<ext>`: unique per import, safe in a folder, and still recognizable in `TCP Themes/images`. */
    fun storedName(displayName: String?, tag: String, mime: String?): String {
        val stem = displayName?.substringAfterLast('/')?.substringBeforeLast('.')
            ?.replace(Regex("[^A-Za-z0-9 _-]"), "_")?.trim()?.take(40)?.ifBlank { null } ?: "background"
        return "$stem-$tag.${extensionFor(mime)}"
    }

    fun extensionFor(mime: String?): String = when (mime?.lowercase()) {
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        else -> "png"
    }
}
