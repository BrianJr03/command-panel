package jr.brian.commandpannel.system

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import jr.brian.commandpannel.R
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

/** Why a background image import failed; [messageRes] is the user-facing explanation. */
class BackgroundImportException(@StringRes val messageRes: Int) : Exception()

/**
 * The panel's background image (#57, #61). Files live in `filesDir/backgrounds/<fileName>` (app-private, like
 * [UserFontStore]); [BackgroundImageSetting] points at the one in use by file name. Imports are checked against [BackgroundImageRules]: big still
 * photos are downscaled (and turned upright from their EXIF rotation, which [ImageDecoder] applies), animations over
 * the size limit are refused. The overlay decodes them again at its own size when it draws them.
 */
object BackgroundImageStore {

    fun dir(context: Context): File = File(context.filesDir, "backgrounds").apply { mkdirs() }

    fun file(context: Context, image: BackgroundImage): File = File(dir(context), image.fileName)

    fun exists(context: Context, image: BackgroundImage): Boolean = file(context, image).isFile

    /** The bottom screen's size, which the size limits are measured against (the current display if it's missing). */
    fun screenSize(context: Context): PxSize {
        val topology = DisplayTopology(context)
        val display = topology.bottomDisplay
        if (display != null) topology.realSize(display).let { return PxSize(it.x, it.y) }
        val m = context.resources.displayMetrics
        return PxSize(m.widthPixels, m.heightPixels)
    }

    /**
     * Imports the image at [uri] (from the system file picker). Animations are refused unless [allowAnimated] (the
     * Animated backgrounds switch). Blocking; call off the main thread.
     */
    fun import(context: Context, uri: Uri, allowAnimated: Boolean): Result<BackgroundImage> = runCatching {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        val bytes = resolver.openInputStream(uri)?.use { readLimited(it) }
            ?: throw BackgroundImportException(R.string.msg_background_unreadable)
        register(context, bytes, name, allowAnimated).getOrThrow()
    }

    /** Imports [source] (a file the app can read, e.g. one staged by the debug hook), as [import] does. */
    fun importFile(context: Context, source: File, allowAnimated: Boolean): Result<BackgroundImage> = runCatching {
        if (source.length() > BackgroundImageRules.MAX_BYTES) throw BackgroundImportException(R.string.msg_background_too_large)
        register(context, source.readBytes(), source.name, allowAnimated).getOrThrow()
    }

    /** Checks [bytes] and stores them under a unique name made from [displayName]. */
    fun register(context: Context, bytes: ByteArray, displayName: String?, allowAnimated: Boolean): Result<BackgroundImage> = runCatching {
        var mime: String? = null
        var size = PxSize(0, 0)
        var animated = false
        // Decoding at a small sample size proves the file is readable (all frames' header for animations) without the memory.
        val probe = runCatching {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                mime = info.mimeType
                size = PxSize(info.size.width, info.size.height)
                animated = info.isAnimated
                decoder.setTargetSampleSize(8)
            }
        }.getOrNull() ?: throw BackgroundImportException(R.string.msg_background_invalid)
        (probe as? AnimatedImageDrawable)?.let { animated = true }
        val plan = BackgroundImageRules.plan(bytes.size.toLong(), size, animated, screenSize(context), mime, allowAnimated)
        val tag = UUID.randomUUID().toString().take(8)
        val image = when (plan) {
            is ImportPlan.Reject -> throw BackgroundImportException(messageFor(plan.reason))
            ImportPlan.Keep -> {
                val stored = BackgroundImageRules.storedName(displayName, tag, mime)
                File(dir(context), stored).writeBytes(bytes)
                BackgroundImage(stored, animated)
            }
            is ImportPlan.Downscale -> {
                // Photos stay JPEG; anything else (it may have transparency) becomes WebP.
                val jpeg = mime == "image/jpeg"
                val outMime = if (jpeg) "image/jpeg" else "image/webp"
                val stored = BackgroundImageRules.storedName(displayName, tag, outMime)
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(plan.size.width, plan.size.height)
                }
                val dest = File(dir(context), stored)
                dest.outputStream().use { out ->
                    bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.WEBP_LOSSY, 90, out)
                }
                bitmap.recycle()
                BackgroundImage(stored, animated = false)
            }
        }
        image
    }

    private fun messageFor(reason: RejectReason): Int = when (reason) {
        RejectReason.TOO_LARGE_FILE -> R.string.msg_background_too_large
        RejectReason.TOO_LARGE_ANIMATION -> R.string.msg_background_animation_too_big
        RejectReason.UNSUPPORTED_TYPE -> R.string.msg_background_unsupported
        RejectReason.NOT_AN_IMAGE -> R.string.msg_background_invalid
        RejectReason.ANIMATION_OFF -> R.string.msg_background_animated_off
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > BackgroundImageRules.MAX_BYTES) throw BackgroundImportException(R.string.msg_background_too_large)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Deletes every stored image but [keep] and the one set in Appearance, after it's changed or removed. The stored
     * image is kept even while a gradient is the active background (#64), so the user can switch back to it.
     */
    fun prune(context: Context, keep: BackgroundImage?) {
        val names = keptNames(Prefs.of(context), keep)
        dir(context).listFiles()?.filter { it.name !in names }?.forEach { it.delete() }
    }

    /** The file names [prune] keeps: [keep] and the stored image, whatever the active source. Pure, for tests. */
    fun keptNames(prefs: SharedPreferences, keep: BackgroundImage?): Set<String> =
        setOfNotNull(keep?.fileName, BackgroundImageSetting.read(prefs)?.fileName)
}
