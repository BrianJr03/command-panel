package jr.brian.commandpannel.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import jr.brian.commandpannel.system.DisplayTopology

/**
 * A toast drawn in our own overlay window on the top screen. System toasts from a background app are dropped while
 * its notifications are off ("Suppressing toast ... by user request"), so a double press with no window showing gave
 * no feedback. Falls back to a system toast if the window can't be added (no overlay permission).
 */
class OverlayToast(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)
    private var current: TextView? = null
    private val dismiss = Runnable { hide() }

    fun show(message: String) = main.post {
        hide(animate = false)
        val display = topology.topDisplay
        val displayContext = display?.let(context::createDisplayContext)
        if (displayContext == null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return@post
        }
        val density = displayContext.resources.displayMetrics.density
        val view = TextView(displayContext).apply {
            text = message
            setTextColor(android.graphics.Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxWidth = (420 * density).toInt()
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * density
                setColor(0xE6202124.toInt())
            }
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (64 * density).toInt()
        }
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val added = runCatching { wm.addView(view, lp) }
            .onFailure { Log.w(TAG, "toast window failed; using a system toast", it) }
            .isSuccess
        if (!added) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return@post
        }
        current = view
        view.animate().alpha(1f).setDuration(FADE_MS).start()
        main.postDelayed(dismiss, SHOW_MS)
    }

    private fun hide(animate: Boolean = true) {
        main.removeCallbacks(dismiss)
        val view = current ?: return
        current = null
        val remove = {
            runCatching {
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
            }
            Unit
        }
        if (animate) view.animate().alpha(0f).setDuration(FADE_MS).withEndAction(remove).start() else remove()
    }

    private companion object {
        const val TAG = "OverlayToast"
        const val SHOW_MS = 2_500L
        const val FADE_MS = 150L
    }
}
