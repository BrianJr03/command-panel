package jr.brian.commandpannel.privileged.helper

import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Surface
import android.view.SurfaceControl

/**
 * Screen off's black cover as a SurfaceFlinger layer, drawn by the root helper (#39). The app's cover is a window, so
 * the status bar draws above it and apps that hide overlay windows (Android Settings, installers) hide it. This layer
 * isn't a window at all: it has no parent, sits on the display's layer stack above everything WindowManager draws,
 * and nothing in WindowManager can hide it. It takes no input, so the app's cover underneath still eats touches.
 *
 * Lifetime: the layer belongs to this process's SurfaceFlinger session, and SurfaceFlinger drops a session's layers
 * when its process dies, so a crashed or killed helper never leaves a screen covered. The helper also removes its
 * layers before it exits on its own ([hideAll]), and the app removes them on every way back.
 *
 * The layer is an effect (color) layer filled black; where that builder method is missing it falls back to a
 * buffer drawn black once. Hidden APIs (`Builder.setEffectLayer`, `Transaction.setLayerStack` / `setColor`, the
 * DisplayInfo fields) are reached reflectively, as [DisplayMirror] does.
 */
internal object BlankLayer {
    private const val TAG = "BlankLayer"
    /** Just under the top, so nothing WindowManager puts on the display (status bar, screen decorations) is above. */
    private const val Z = Int.MAX_VALUE - 1

    private val shown = mutableMapOf<Int, SurfaceControl>()

    /** Covers [displayId] in black. True once it's up, including when it already was. */
    @Synchronized
    fun show(displayId: Int): Boolean {
        shown[displayId]?.let { if (it.isValid) return true else shown.remove(displayId) }
        return runCatching {
            val info = displayInfo(displayId) ?: error("no display $displayId")
            val layerStack = info.javaClass.getField("layerStack").getInt(info)
            val width = info.javaClass.getField("logicalWidth").getInt(info)
            val height = info.javaClass.getField("logicalHeight").getInt(info)
            val tx = SurfaceControl.Transaction()
            val layer = build(displayId, width, height, tx)
            SurfaceControl.Transaction::class.java
                .getMethod("setLayerStack", SurfaceControl::class.java, Int::class.javaPrimitiveType)
                .invoke(tx, layer, layerStack)
            tx.setLayer(layer, Z)
                .setAlpha(layer, 1f)
                .setVisibility(layer, true)
                .apply()
            shown[displayId] = layer
            Log.i(TAG, "display $displayId covered (layer stack $layerStack, ${width}x$height)")
            true
        }.getOrElse {
            Log.w(TAG, "show($displayId) failed", it)
            false
        }
    }

    /** Removes [displayId]'s layer. True once it's gone, including when there was none. */
    @Synchronized
    fun hide(displayId: Int): Boolean {
        val layer = shown.remove(displayId) ?: return true
        return runCatching {
            SurfaceControl.Transaction().reparent(layer, null).apply()
            layer.release()
            Log.i(TAG, "display $displayId uncovered")
            true
        }.getOrElse {
            Log.w(TAG, "hide($displayId) failed", it)
            false
        }
    }

    /** Removes every layer (the helper is about to exit). */
    @Synchronized
    fun hideAll() {
        shown.keys.toList().forEach(::hide)
    }

    /**
     * An opaque black layer the size of the display: an effect layer colored black in [tx] if this Android has both
     * hidden methods, else a buffer drawn black once (an effect layer with no color would draw nothing).
     */
    private fun build(displayId: Int, width: Int, height: Int, tx: SurfaceControl.Transaction): SurfaceControl {
        val name = "command-panel-screen-off-$displayId"
        val effectBuilder = SurfaceControl.Builder().setName(name)
        val isEffect = runCatching { SurfaceControl.Builder::class.java.getMethod("setEffectLayer").invoke(effectBuilder) }.isSuccess ||
            runCatching { SurfaceControl.Builder::class.java.getMethod("setColorLayer").invoke(effectBuilder) }.isSuccess
        if (isEffect) {
            val layer = effectBuilder.build()
            val colored = runCatching {
                SurfaceControl.Transaction::class.java.getMethod("setColor", SurfaceControl::class.java, FloatArray::class.java)
                    .invoke(tx, layer, floatArrayOf(0f, 0f, 0f))
            }.isSuccess
            if (colored) return layer
            layer.release()
        }
        val layer = SurfaceControl.Builder().setName(name)
            .setBufferSize(width, height).setFormat(PixelFormat.OPAQUE).setOpaque(true).build()
        val surface = Surface(layer)
        try {
            val canvas = surface.lockCanvas(null)
            canvas.drawColor(Color.BLACK)
            surface.unlockCanvasAndPost(canvas)
        } finally {
            surface.release()
        }
        return layer
    }

    private fun displayInfo(displayId: Int): Any? {
        val global = Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null)
        return global.javaClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(global, displayId)
    }
}
