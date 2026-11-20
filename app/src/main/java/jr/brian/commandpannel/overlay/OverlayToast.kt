package jr.brian.commandpannel.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import jr.brian.commandpannel.system.DisplayTopology

/**
 * A toast drawn in our own overlay window on the top screen. System toasts from a background app are dropped while
 * its notifications are off ("Suppressing toast ... by user request"), so a double press with no window showing gave
 * no feedback. Falls back to a system toast if the window can't be added (no overlay permission).
 *
 * With an action ([show]'s `actionLabel`), the pill takes taps (only on itself; touches elsewhere pass through), stays
 * up longer, and runs the action when tapped. A system-toast fallback can't hold a button, so it drops the action.
 */
class OverlayToast(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)
    private var current: View? = null
    private val dismiss = Runnable { hide() }

    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) = main.post {
        val action = onAction?.takeIf { actionLabel != null }
        hide(animate = false)
        val display = topology.topDisplay
        val displayContext = display?.let(context::createDisplayContext)
        if (displayContext == null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            return@post
        }
        val density = displayContext.resources.displayMetrics.density
        val label = TextView(displayContext).apply {
            text = message
            setTextColor(android.graphics.Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxWidth = (420 * density).toInt()
        }
        val view = LinearLayout(displayContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 24 * density
                setColor(0xE6202124.toInt())
            }
            alpha = 0f
            addView(label)
            if (action != null) {
                addView(
                    TextView(displayContext).apply {
                        text = actionLabel
                        setTextColor(ACTION_COLOR)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding((16 * density).toInt(), 0, 0, 0)
                    },
                )
                isClickable = true
                setOnClickListener {
                    hide()
                    action()
                }
            }
        }
        // Touchable only with an action; NOT_TOUCH_MODAL lets touches outside the pill reach the game below.
        val touch = if (action != null) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or touch,
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
        main.postDelayed(dismiss, if (action != null) SHOW_ACTION_MS else SHOW_MS)
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
        /** Long enough to reach for the screen and tap the action. */
        const val SHOW_ACTION_MS = 5_000L
        const val ACTION_COLOR = 0xFF8AB4F8.toInt()
        const val FADE_MS = 150L
    }
}
