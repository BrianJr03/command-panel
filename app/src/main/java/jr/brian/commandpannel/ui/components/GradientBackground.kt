package jr.brian.commandpannel.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RuntimeShader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import jr.brian.commandpannel.overlay.LocalOverlayPanelSettled
import jr.brian.commandpannel.system.BackgroundImageRules
import jr.brian.commandpannel.system.GradientFrame
import jr.brian.commandpannel.system.GradientMotionMath
import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientSpec
import jr.brian.commandpannel.system.GradientStop
import jr.brian.commandpannel.system.GradientStyle
import jr.brian.commandpannel.system.PxSize
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "LookBackground"

/** The clock every animated gradient runs on, so the panel and the editor preview show the same moment. */
object GradientClock {
    fun nowMs(): Long = SystemClock.uptimeMillis()
}

/**
 * A gradient background (#66): [spec] drawn by one AGSL shader over [modifier]'s bounds. Stops are opaque, so nothing
 * behind it shows through.
 *
 * Still (speed 0, or [animate] false: the Experimental Animated backgrounds switch off) it's drawn once in the window
 * at the loop's first frame, with no clock and no thread, so several can sit on one screen cheaply.
 *
 * Moving, a player thread advances it at no more than 30 fps ([BackgroundImageRules.MIN_FRAME_MS]), half that when the
 * device is warm and not at all when it's hot ([GradientMotionMath.frameIntervalMs]), only while this is composed, its
 * lifecycle is started and its display is on. In the overlay, once the panel has finished showing
 * ([LocalOverlayPanelSettled]), frames go to a [SurfaceView] under the panel window at a quarter of the layout's size
 * ([GradientMotionMath.surfaceSize]), drawn with a hardware canvas and scaled up by the display, so the panel window
 * doesn't repaint per frame. During the show and hide animations and outside the overlay (previews), frames are drawn
 * in the window instead.
 */
@Composable
fun GradientBackgroundLayer(spec: GradientSpec, modifier: Modifier = Modifier, animate: Boolean = true) {
    val context = LocalContext.current.applicationContext
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    val moves = GradientRules.moves(spec, animate)
    // Only read while drawing, so a new time repaints the window without recomposing anything.
    val time = remember(spec, moves) { mutableLongStateOf(if (moves) GradientClock.nowMs() else 0L) }
    val windowFrames = remember { AtomicInteger() }
    var player by remember(spec) { mutableStateOf<GradientPlayer?>(null) }
    val target = PxSize(layoutSize.width, layoutSize.height)
    if (moves) {
        val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
        val displayOn = rememberDisplayOn()
        val play = layoutSize != IntSize.Zero && displayOn && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
        DisposableEffect(spec, target, play) {
            if (!play) return@DisposableEffect onDispose { }
            val p = GradientPlayer(context, spec, target, windowFrames) { time.longValue = it }
            p.start()
            player = p
            onDispose {
                player = null
                p.release()
            }
        }
    }
    val settled = LocalOverlayPanelSettled.current?.value
    Box(modifier.onSizeChanged { layoutSize = it }) {
        Box(
            Modifier.matchParentSize().graphicsLayer().drawBehind {
                val frame = if (moves) GradientMotionMath.frame(spec, time.longValue) else GradientMotionMath.still(spec)
                if (moves) windowFrames.incrementAndGet()
                drawIntoCanvas { WindowGradientShader.shader?.draw(it.nativeCanvas, spec, frame, size.width, size.height) }
            },
        )
        val p = player
        if (p != null && BackgroundImageRules.drawsOnSurface(playing = true, settled = settled, hasFirstFrame = true)) {
            key(p) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            // Not OPAQUE: SurfaceView turns that into RGB_565, which bands a gradient. RGBX is opaque 8888.
                            holder.setFormat(PixelFormat.RGBX_8888)
                            holder.setFixedSize(p.surfaceSize.width, p.surfaceSize.height)
                            holder.addCallback(p.surfaceCallback)
                        }
                    },
                    modifier = Modifier.matchParentSize(),
                    onRelease = { view ->
                        view.holder.removeCallback(p.surfaceCallback)
                        p.detachSurface()
                    },
                )
            }
        }
    }
}

/** The window's shader: main thread only, shared by every gradient drawn in a window (each draw snapshots its uniforms). */
private object WindowGradientShader {
    val shader by lazy { GradientShader.create() }
}

/** The gradient's [RuntimeShader] and the paint that draws it. Not thread safe: one per thread. */
private class GradientShader private constructor() {
    companion object {
        /** A ready shader, or `null` (logged) if this device can't compile or feed it, so the backdrop shows instead. */
        fun create(): GradientShader? = runCatching {
            GradientShader().also { it.draw(Canvas(), PROBE_SPEC, GradientMotionMath.still(PROBE_SPEC), 0f, 0f, probe = true) }
        }.onFailure { Log.w(TAG, "gradient shader unavailable", it) }.getOrNull()

        private val PROBE_SPEC = GradientSpec(
            id = "probe", name = "",
            stops = listOf(GradientStop(0, 0f), GradientStop(0, 1f)),
        )
    }

    private val shader = RuntimeShader(GRADIENT_AGSL)
    private val paint = Paint().apply { shader = this@GradientShader.shader }
    private val colors = FloatArray(GradientRules.MAX_STOPS * 4)
    private val positions = FloatArray(GradientRules.MAX_STOPS)
    private var stopsOf: GradientSpec? = null

    /** Draws [spec] at [frame] over 0, 0, [width], [height]; [probe] sets every uniform without drawing. */
    fun draw(canvas: Canvas, spec: GradientSpec, frame: GradientFrame, width: Float, height: Float, probe: Boolean = false) {
        if (!probe && (width <= 0f || height <= 0f)) return
        if (stopsOf !== spec) setStops(spec)
        shader.setFloatUniform("iResolution", width, height)
        shader.setFloatUniform("iAngle", frame.angle)
        shader.setFloatUniform("iShift", frame.shiftX, frame.shiftY)
        shader.setFloatUniform("iScale", frame.scale)
        shader.setFloatUniform("iHue", frame.hue)
        shader.setFloatUniform("iWave", frame.wavePhase)
        shader.setFloatUniform("iWaveAmp", frame.waveAmp)
        shader.setFloatUniform("iBlob", frame.blobPhase)
        if (probe) return
        // Uniforms changed: the paint picks up the new shader instance.
        paint.shader = shader
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun setStops(spec: GradientSpec) {
        val stops = spec.stops
        for (i in 0 until GradientRules.MAX_STOPS) {
            // Unused slots repeat the last stop, so they can't change the ramp.
            val s = stops[minOf(i, stops.lastIndex)]
            colors[i * 4] = ((s.color shr 16) and 0xFF) / 255f
            colors[i * 4 + 1] = ((s.color shr 8) and 0xFF) / 255f
            colors[i * 4 + 2] = (s.color and 0xFF) / 255f
            colors[i * 4 + 3] = 1f
            positions[i] = s.position
        }
        shader.setFloatUniform("iColors", colors)
        shader.setFloatUniform("iPos", positions)
        shader.setFloatUniform("iCount", stops.size.coerceIn(1, GradientRules.MAX_STOPS).toFloat())
        shader.setFloatUniform(
            "iStyle",
            when (spec.style) {
                GradientStyle.LINEAR -> 0f
                GradientStyle.RADIAL -> 1f
                GradientStyle.AURORA -> 2f
            },
        )
        stopsOf = spec
    }
}

/**
 * Advances a moving gradient on its own thread. Each frame takes the time from [GradientClock] (a late frame skips
 * ahead rather than queueing up) and goes to the attached surface ([surfaceCallback]) through a hardware canvas, since
 * a software canvas can't run a [RuntimeShader]; with no surface the time goes to [onTime] on the main thread, which
 * draws it in the window. Logs like the image player, which device scripts read.
 */
private class GradientPlayer(
    context: Context,
    private val spec: GradientSpec,
    private val target: PxSize,
    private val windowFrames: AtomicInteger,
    private val onTime: (Long) -> Unit,
) {
    private val thread = HandlerThread("bg-gradient", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val power = context.getSystemService(PowerManager::class.java)
    private val label = "gradient:${spec.id}"
    val surfaceSize = GradientMotionMath.surfaceSize(target)

    // Player thread only, except under surfaceLock (surfaceRedrawNeeded draws from the main thread).
    private val gpu by lazy { GradientShader.create() }
    private var surface: SurfaceHolder? = null
    private val surfaceLock = ReentrantLock()
    @Volatile private var lastTime = GradientClock.nowMs()
    @Volatile private var released = false
    @Volatile private var thermal = PowerManager.THERMAL_STATUS_NONE
    private var held = false

    private var frames = 0
    private var surfaceFrames = 0
    private var drawNanos = 0L
    private var startedAt = 0L

    private val drawFrame = Runnable { draw() }
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        thermal = status
        if (released) return@OnThermalStatusChangedListener
        val interval = GradientMotionMath.frameIntervalMs(status)
        if (interval == null && !held) {
            held = true
            handler.removeCallbacks(drawFrame)
            Log.i(TAG, "animation held $label thermal=$status")
        } else if (interval != null && held) {
            held = false
            Log.i(TAG, "animation resumed $label thermal=$status")
            handler.removeCallbacks(drawFrame)
            handler.post(drawFrame)
        }
    }

    fun start() {
        handler.post {
            if (released) return@post
            thermal = power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
            held = GradientMotionMath.frameIntervalMs(thermal) == null
            // Called on this thread, so held and the schedule stay single-threaded.
            runCatching { power?.addThermalStatusListener({ handler.post(it) }, thermalListener) }
            startedAt = SystemClock.uptimeMillis()
            windowFrames.set(0)
            Log.i(
                TAG,
                "animation started $label ${surfaceSize.width}x${surfaceSize.height} for ${target.width}x${target.height} " +
                    "style=${spec.style.key} motion=${spec.motion.key} speed=${spec.speed} thermal=$thermal",
            )
            if (held) Log.i(TAG, "animation held $label thermal=$thermal")
            draw()
        }
    }

    private fun draw() {
        if (released) return
        val startedFrameAt = SystemClock.uptimeMillis()
        val t0 = System.nanoTime()
        val now = GradientClock.nowMs()
        lastTime = now
        val onSurface = surfaceLock.withLock { surface?.let { post(it, now) } ?: false }
        drawNanos += System.nanoTime() - t0
        frames++
        if (onSurface) surfaceFrames++ else main.post { if (!released) onTime(now) }
        // Held (hot): this frame stays up until it cools. Otherwise the next one, no sooner than 30 fps (or the
        // thermal step) after this one started, and never in the past.
        val interval = GradientMotionMath.frameIntervalMs(thermal) ?: return
        val at = BackgroundImageRules.nextFrameAt(startedFrameAt + interval, startedFrameAt, SystemClock.uptimeMillis())
        handler.removeCallbacks(drawFrame)
        handler.postAtTime(drawFrame, at)
    }

    /** Draws the gradient at [time] into [holder]'s surface. Call under [surfaceLock]. */
    private fun post(holder: SurfaceHolder, time: Long): Boolean {
        val shader = gpu ?: return false
        val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return false
        try {
            shader.draw(canvas, spec, GradientMotionMath.frame(spec, time), canvas.width.toFloat(), canvas.height.toFloat())
        } finally {
            runCatching { holder.unlockCanvasAndPost(canvas) }
        }
        return true
    }

    /**
     * The [SurfaceView]'s callbacks (main thread). The current frame is drawn into the surface before the window shows
     * it ([SurfaceHolder.Callback2.surfaceRedrawNeeded]), so there's never an empty hole.
     */
    val surfaceCallback = object : SurfaceHolder.Callback2 {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
            surfaceLock.withLock {
                if (!released) post(holder, lastTime)
                if (surface == null) Log.i(TAG, "surface attached $label ${surfaceSize.width}x${surfaceSize.height}")
                surface = holder
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) = detachSurface()
    }

    /** Frames go back to the window, starting with the current one, so the window isn't left on an old frame. */
    fun detachSurface() {
        surfaceLock.withLock { surface.also { surface = null } } ?: return
        if (!released) onTime(lastTime)
        Log.i(TAG, "surface detached $label")
    }

    fun release() {
        released = true
        handler.post {
            handler.removeCallbacksAndMessages(null)
            runCatching { power?.removeThermalStatusListener(thermalListener) }
            val ms = SystemClock.uptimeMillis() - startedAt
            if (startedAt > 0) Log.i(
                TAG,
                "animation stopped $label frames=$frames ms=$ms rendered=${windowFrames.get()} surfaceFrames=$surfaceFrames " +
                    "drawMs=${"%.1f".format(if (frames > 0) drawNanos / frames / 1_000_000.0 else 0.0)} " +
                    "size=${surfaceSize.width}x${surfaceSize.height}",
            )
            thread.quitSafely()
        }
    }
}

/**
 * Styles: 0 linear (along iAngle), 1 radial (from the center), 2 aurora (soft blobs over a dim linear wash). The ramp
 * mixes up to six stops in order (unused slots repeat the last). Uniforms come from [GradientMotionMath.frame]; every
 * moving one is used through sin or cos, so a loop has no seam. A little noise against banding on slow, dark gradients.
 * Names must also avoid GLSL ES reserved words (`active`, `filter`, `common`, `input`, `output`, `half`, ...): Skia passes
 * them through, the GPU driver then refuses the program, and the gradient silently draws nothing.
 */
private const val GRADIENT_AGSL = """
uniform float2 iResolution;
uniform float4 iColors[6];
uniform float iPos[6];
uniform float iCount;
uniform float iStyle;
uniform float iAngle;
uniform float2 iShift;
uniform float iScale;
uniform float iHue;
uniform float iWave;
uniform float iWaveAmp;
uniform float iBlob;

float3 ramp(float t) {
    float3 c = iColors[0].rgb;
    for (int i = 1; i < 6; i++) {
        float used = step(float(i) + 0.5, iCount);
        float p0 = iPos[i - 1];
        float p1 = iPos[i];
        float k = clamp((t - p0) / max(p1 - p0, 0.0001), 0.0, 1.0);
        c = mix(c, iColors[i].rgb, k * used);
    }
    return c;
}

float3 hueRotate(float3 c, float a) {
    float3 k = float3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 fragCoord) {
    float2 res = max(iResolution, float2(1.0));
    float aspect = res.x / res.y;
    float2 ext = float2(aspect, 1.0) * 0.5;
    float2 p = (fragCoord / res - 0.5) * float2(aspect, 1.0) - iShift;
    float2 d = float2(cos(iAngle), sin(iAngle));
    float2 n = float2(-d.y, d.x);
    float3 c;
    if (iStyle > 0.5 && iStyle < 1.5) {
        float r = length(p) / length(ext);
        r += iWaveAmp * 1.5 * sin(atan(p.y, p.x) * 3.0 + iWave);
        c = ramp(clamp(r / iScale, 0.0, 1.0));
    } else {
        float reach = ext.x * abs(d.x) + ext.y * abs(d.y);
        float t = dot(p, d) / (2.0 * reach) + 0.5;
        t += iWaveAmp * sin(dot(p, n) * 14.0 + iWave);
        t = 0.5 + (t - 0.5) * iScale;
        c = ramp(clamp(t, 0.0, 1.0));
        if (iStyle > 1.5) {
            c *= 0.6;
            float2 q = float2(dot(p, d), dot(p, n));
            for (int j = 0; j < 4; j++) {
                float fj = float(j);
                float h = 1.0 + mod(fj, 2.0);
                float2 center = float2(
                    (fj / 3.0 - 0.5) * ext.x * 1.4 + 0.42 * sin(iBlob * h + fj * 1.7),
                    0.36 * sin(iBlob * 2.0 + fj * 2.3) + 0.12 * cos(iBlob + fj));
                float2 dq = q - center;
                float w = exp(-dot(dq, dq) / 0.12);
                c = mix(c, ramp((fj + 0.5) / 4.0), w * 0.85);
            }
        }
    }
    c = clamp(hueRotate(c, iHue), 0.0, 1.0);
    c += (hash(fragCoord) + hash(fragCoord + 17.0) - 1.0) / 255.0;
    return half4(half3(c), 1.0);
}
"""
