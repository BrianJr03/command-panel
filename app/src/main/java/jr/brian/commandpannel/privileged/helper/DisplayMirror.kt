package jr.brian.commandpannel.privileged.helper

import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import android.view.Surface

/**
 * Root-side screen mirror: creates a virtual SurfaceControl display that renders the content of a physical display's
 * layer stack onto a caller-provided [Surface] (the Replay Buffer's encoder input). This is the classic `screenrecord`
 * mirror, done from the uid-0 helper so it needs no MediaProjection consent (the fork's Replay Buffer auto-re-arms on
 * boot). All SurfaceControl display APIs are @hide and reached reflectively; exemptions are set in the helper's main().
 */
internal object DisplayMirror {
    private const val TAG = "DisplayMirror"
    private var token: IBinder? = null

    @Synchronized
    fun start(surface: Surface, layerStack: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Boolean {
        stop()
        return try {
            val sc = Class.forName("android.view.SurfaceControl")
            val displayToken = sc.getMethod("createDisplay", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, "command-panel-replay", false) as IBinder

            val txClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val tx = txClass.getConstructor().newInstance()
            txClass.getMethod("setDisplaySurface", IBinder::class.java, Surface::class.java).invoke(tx, displayToken, surface)
            txClass.getMethod("setDisplayLayerStack", IBinder::class.java, Int::class.javaPrimitiveType).invoke(tx, displayToken, layerStack)
            txClass.getMethod(
                "setDisplayProjection", IBinder::class.java, Int::class.javaPrimitiveType, Rect::class.java, Rect::class.java,
            ).invoke(tx, displayToken, 0 /* ORIENTATION_0 */, Rect(0, 0, srcW, srcH), Rect(0, 0, dstW, dstH))
            runCatching {
                txClass.getMethod("setDisplaySize", IBinder::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(tx, displayToken, dstW, dstH)
            }
            txClass.getMethod("apply").invoke(tx)
            token = displayToken
            Log.i(TAG, "mirror started: layerStack=$layerStack ${srcW}x$srcH -> ${dstW}x$dstH")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "startDisplayMirror failed", t)
            stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        val tok = token ?: return
        token = null
        runCatching {
            Class.forName("android.view.SurfaceControl")
                .getMethod("destroyDisplay", IBinder::class.java).invoke(null, tok)
        }.onFailure { Log.w(TAG, "destroyDisplay failed", it) }
    }
}
