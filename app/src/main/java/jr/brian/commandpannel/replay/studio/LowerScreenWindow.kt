package jr.brian.commandpannel.replay.studio

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import jr.brian.commandpannel.system.DisplayTopology

/**
 * A black, full-screen window on the lower display holding one [SurfaceView] for the player's video (#47).
 *
 * It's an app overlay (`TYPE_APPLICATION_OVERLAY`), the window type [jr.brian.commandpannel.overlay.OverlayHost]
 * already uses on the lower display, rather than a `Presentation`: the Thor's lower display isn't a presentation
 * display, and a presentation window is refused there. It's added after the panel, so it sits over it, and it's
 * neither focusable nor touchable: gamepad focus stays with the player on the top screen.
 *
 * Main thread only.
 */
class LowerScreenWindow(private val context: Context) {

    private var root: FrameLayout? = null
    private var wm: WindowManager? = null

    /** The video surface, once [show] succeeded. */
    var surfaceView: SurfaceView? = null
        private set

    /** Adds the window; false when there's no lower display or the window is refused. */
    fun show(callback: SurfaceHolder.Callback? = null): Boolean {
        if (root != null) return true
        val topology = DisplayTopology(context)
        val display = topology.bottomDisplay ?: return false
        val size = topology.realSize(display)
        val displayContext = context.createDisplayContext(display)
        val manager = displayContext.getSystemService(WindowManager::class.java)
        val surface = SurfaceView(displayContext).apply { callback?.let(holder::addCallback) }
        val layout = FrameLayout(displayContext).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val lp = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.CENTER
            fitInsetsTypes = 0
            title = "ReplayPlayerLower"
        }
        return runCatching { manager.addView(layout, lp) }
            .onSuccess {
                root = layout
                wm = manager
                surfaceView = surface
                Log.i(TAG, "lower screen window on display ${display.displayId} (${size.x}x${size.y})")
            }
            .onFailure { Log.w(TAG, "lower screen window refused", it) }
            .isSuccess
    }

    fun hide() {
        val view = root ?: return
        runCatching { wm?.removeViewImmediate(view) }.onFailure { Log.w(TAG, "lower screen window remove failed", it) }
        root = null
        wm = null
        surfaceView = null
    }

    private companion object {
        const val TAG = "ReplayPlayer"
    }
}
