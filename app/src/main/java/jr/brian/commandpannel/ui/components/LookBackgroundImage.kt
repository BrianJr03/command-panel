package jr.brian.commandpannel.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import jr.brian.commandpannel.system.BackgroundImageRules
import jr.brian.commandpannel.system.BackgroundImageStore
import jr.brian.commandpannel.system.PxSize
import jr.brian.commandpannel.ui.theme.BackgroundImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LookBackground"
/** Frames the panel drew with the image since the last animation started (for the stop log line). */
private val renderedFrames = java.util.concurrent.atomic.AtomicInteger()

/**
 * The look's background image (#57) as a modifier: it draws [image] center-cropped over whatever is behind it (the
 * backdrop) and under the content. Nothing is drawn while it loads or when the file is missing or can't be decoded,
 * so the backdrop shows instead. `null` returns a no-op modifier.
 *
 * The file is decoded at the layout's size. A still image is decoded once and cached. An animation plays on its own
 * thread at no more than 30 fps, only while this is composed (the overlay drops its view when hidden), its lifecycle
 * is started, and its display is on (screen off, lid closed).
 */
@Composable
fun rememberLookBackgroundImage(image: BackgroundImage?): Modifier {
    if (image == null) return Modifier
    val context = LocalContext.current.applicationContext
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val frame = remember(image) { mutableStateOf<ImageBitmap?>(null) }
    val target = PxSize(layoutSize.width, layoutSize.height)
    if (image.animated) {
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val displayOn = rememberDisplayOn()
        val play = layoutSize != IntSize.Zero && displayOn && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        DisposableEffect(image, target, play) {
            if (!play) return@DisposableEffect onDispose { }
            val player = AnimatedBackgroundPlayer(BackgroundImageStore.file(context, image), target) { frame.value = it }
            player.start()
            onDispose { player.release() }
        }
    } else {
        LaunchedEffect(image, target) {
            if (layoutSize == IntSize.Zero) return@LaunchedEffect
            frame.value = withContext(Dispatchers.IO) { StillBackgroundCache.load(context, image, target) }
        }
    }
    return Modifier
        .onSizeChanged { layoutSize = it }
        .drawBehind {
            // Scaled to the current size, in case the layout changed before the next decode lands.
            frame.value?.let {
                renderedFrames.incrementAndGet()
                drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
        }
}

/** Whether the display this view is on is lit (not off or dozing). True when there's no display to ask. */
@Composable
private fun rememberDisplayOn(): Boolean {
    val view = LocalView.current
    val display = view.display ?: return true
    var on by remember(display.displayId) { mutableStateOf(display.isLit()) }
    DisposableEffect(display.displayId) {
        val dm = view.context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(id: Int) { if (id == display.displayId) on = display.isLit() }
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
        }
        dm?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm?.unregisterDisplayListener(listener) }
    }
    return on
}

private fun Display.isLit(): Boolean = state == Display.STATE_ON || state == Display.STATE_VR

/**
 * Decodes [file] center-cropped to [target]: a bitmap for a still image, an [AnimatedImageDrawable] for an animation.
 * With [noUpscale] an image smaller than the target is cropped at its own resolution instead (the GPU scales it up when
 * drawn); [onSize] gets the size the result is decoded at.
 */
private fun decodeCover(
    file: File,
    target: PxSize,
    asDrawable: Boolean,
    noUpscale: Boolean = false,
    onSize: (PxSize) -> Unit = {},
): Any {
    val source = ImageDecoder.createSource(file)
    val listener = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
        // EXIF rotation is already applied to info.size (and to the pixels).
        val image = PxSize(info.size.width, info.size.height)
        val out = if (noUpscale) BackgroundImageRules.renderSize(image, target) else target
        val plan = BackgroundImageRules.cover(image, out)
        decoder.setTargetSize(plan.scaled.width, plan.scaled.height)
        decoder.crop = Rect(plan.cropLeft, plan.cropTop, plan.cropLeft + plan.crop.width, plan.cropTop + plan.crop.height)
        onSize(plan.crop)
    }
    return if (asDrawable) ImageDecoder.decodeDrawable(source, listener) else ImageDecoder.decodeBitmap(source, listener)
}

/** Still images by file and size: the overlay's and Theme Studio preview's, so reopening the panel doesn't decode again. */
private object StillBackgroundCache {
    private val cache = LruCache<String, ImageBitmap>(2)

    fun load(context: Context, image: BackgroundImage, target: PxSize): ImageBitmap? {
        val key = "${image.fileName}@${target.width}x${target.height}"
        cache.get(key)?.let { return it }
        val file = BackgroundImageStore.file(context, image)
        if (!file.isFile) return null
        return runCatching { (decodeCover(file, target, asDrawable = false) as Bitmap).asImageBitmap() }
            .onFailure { Log.w(TAG, "decode failed: ${image.fileName}", it) }
            .getOrNull()?.also { cache.put(key, it) }
    }
}

/**
 * Plays an animated image on a background thread. [AnimatedImageDrawable] drawn into a hardware canvas runs on the
 * render thread at the file's own speed, with no way to slow it, so frames are drawn here into two software buffers
 * instead, at the file's own resolution (never upscaled; the GPU scales them to the panel). The player keeps its own
 * clock: a frame starts when the drawable says the next one is due, but never sooner than
 * [BackgroundImageRules.MIN_FRAME_MS] after the last one started (30 fps). Each finished frame goes to [onFrame] on the
 * main thread.
 */
private class AnimatedBackgroundPlayer(
    private val file: File,
    private val target: PxSize,
    private val onFrame: (ImageBitmap) -> Unit,
) {
    private val thread = HandlerThread("bg-image", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var drawable: Drawable? = null
    private val buffers = arrayOfNulls<Bitmap>(2)
    private val images = arrayOfNulls<ImageBitmap>(2)
    private var next = 0
    private var lastFrameAt = 0L
    private var frames = 0
    private var startedAt = 0L
    // For the stop log line (scripts read the playback rate and draw cost from it).
    private var drawNanos = 0L
    @Volatile private var released = false

    private val drawFrame = Runnable { draw() }

    /** The size frames are drawn at (at most [target]). */
    private var frameSize = target

    /** When the drawable last asked to be drawn again (its next frame is due), or 0 when it didn't ask. */
    private var wantedAt = 0L

    // The drawable keeps only a weak reference to its callback; this player holds the strong one. Its own scheduling
    // in software mode doesn't reliably ask again after every frame (playback stalled at a few fps), so the player
    // runs its own clock: the drawable's request is only a hint for when the next frame is due.
    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            if (released) return
            handler.removeCallbacks(drawFrame)
            handler.post(drawFrame)
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            wantedAt = `when`
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    fun start() {
        handler.post {
            if (released || !file.isFile) return@post
            // Frames are drawn at no more than the file's own resolution: a small GIF stays small in memory and in the
            // per-frame software draw and upload, and the GPU scales it to the panel.
            val d = runCatching { decodeCover(file, target, asDrawable = true, noUpscale = true) { frameSize = it } as Drawable }
                .onFailure { Log.w(TAG, "decode failed: ${file.name}", it) }
                .getOrNull() ?: return@post
            d.setBounds(0, 0, frameSize.width, frameSize.height)
            d.callback = callback
            drawable = d
            startedAt = SystemClock.uptimeMillis()
            renderedFrames.set(0)
            Log.i(TAG, "animation started ${file.name} ${frameSize.width}x${frameSize.height} for ${target.width}x${target.height}")
            if (d is AnimatedImageDrawable) {
                d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                d.start() // invalidates itself, which draws the first frame
            } else {
                draw()
            }
        }
    }

    private fun draw() {
        val d = drawable ?: return
        if (released) return
        val i = next
        val bitmap = buffers[i] ?: Bitmap.createBitmap(frameSize.width, frameSize.height, Bitmap.Config.ARGB_8888).also {
            buffers[i] = it
            images[i] = it.asImageBitmap()
        }
        // Clearing also marks the pixels changed, so the render thread uploads the new frame.
        val t0 = System.nanoTime()
        val startedFrameAt = SystemClock.uptimeMillis()
        wantedAt = 0L
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        d.draw(Canvas(bitmap))
        drawNanos += System.nanoTime() - t0
        lastFrameAt = startedFrameAt
        frames++
        next = 1 - i
        val image = images[i] ?: return
        main.post { if (!released) onFrame(image) }
        // The next frame: when the drawable says it's due, but no sooner than 30 fps allows. Without a request, check
        // again at the 30 fps step, so playback never stalls.
        if (d is AnimatedImageDrawable) {
            val at = BackgroundImageRules.nextFrameAt(wantedAt, lastFrameAt, SystemClock.uptimeMillis())
            handler.removeCallbacks(drawFrame)
            handler.postAtTime(drawFrame, at)
        }
    }

    fun release() {
        released = true
        handler.post {
            (drawable as? AnimatedImageDrawable)?.stop()
            drawable?.callback = null
            drawable = null
            handler.removeCallbacksAndMessages(null)
            val ms = SystemClock.uptimeMillis() - startedAt
            if (startedAt > 0) Log.i(
                TAG,
                "animation stopped ${file.name} frames=$frames ms=$ms rendered=${renderedFrames.get()} " +
                    "drawMs=${"%.1f".format(if (frames > 0) drawNanos / frames / 1_000_000.0 else 0.0)} size=${frameSize.width}x${frameSize.height}",
            )
            thread.quitSafely()
        }
    }
}
