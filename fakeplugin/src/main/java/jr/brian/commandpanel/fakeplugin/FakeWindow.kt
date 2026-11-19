package jr.brian.commandpanel.fakeplugin

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * The fake plugin's UI: one full-screen, solid-color TYPE_APPLICATION_OVERLAY window with a label, on the display
 * Command Panel asked for. Not focusable, so the top screen keeps focus. At most one at a time. Main thread only.
 */
object FakeWindow {

    private var view: View? = null
    private var windowManager: WindowManager? = null

    /**
     * Adds the window on [displayId] and calls [onDrawn] once its first frame is on screen. False when it can't be
     * added (no such display, or SYSTEM_ALERT_WINDOW isn't granted).
     */
    fun show(context: Context, displayId: Int, label: String, onDrawn: () -> Unit): Boolean {
        remove()
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        if (display == null) {
            Log.w(FakePluginService.TAG, "no display $displayId")
            return false
        }
        val windowContext = context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val wm = windowContext.getSystemService(WindowManager::class.java)
        val tv = TextView(windowContext).apply {
            text = label
            textSize = 40f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(COLOR)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply { title = WINDOW_TITLE }
        val added = runCatching { wm.addView(tv, params) }
            .onFailure { Log.w(FakePluginService.TAG, "addView on display $displayId failed", it) }.isSuccess
        if (!added) return false
        view = tv
        windowManager = wm
        tv.viewTreeObserver.registerFrameCommitCallback { if (view === tv) onDrawn() }
        Log.i(FakePluginService.TAG, "window added on display $displayId")
        return true
    }

    /** Removes the window right away, with no exit animation. True if there was one. */
    fun remove(): Boolean {
        val v = view ?: return false
        runCatching { windowManager?.removeViewImmediate(v) }
        view = null
        windowManager = null
        return true
    }

    /** Magenta: easy to tell apart from any Command Panel look in a screencap. */
    private const val COLOR = 0xFFC2185B.toInt()
    private const val WINDOW_TITLE = "FakePlugin"
}
