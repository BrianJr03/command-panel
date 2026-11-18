package jr.brian.commandpannel.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import jr.brian.commandpannel.system.DisplayTopology

/**
 * Owns the bottom-screen overlay window: a `ComposeView` attached to the bottom display's `WindowManager`.
 *
 * Window type is `TYPE_APPLICATION_OVERLAY` (2038) — the probe confirmed a user app may add it on display 4, while
 * the stock app's `TYPE_BOOT_PROGRESS` (2021) is denied. `FLAG_LAYOUT_NO_LIMITS` is never set (it would lay the
 * surface out at the top display's width and clip the right edge). `WindowManagerService` ignores window animations
 * for a non-system overlay, so show/hide is animated on the view (200 ms slide + fade), matching stock's feel.
 *
 * All window mutations run on the main thread.
 */
class OverlayHost(
    private val context: Context,
    private val content: @Composable (dismiss: () -> Unit) -> Unit,
    /** Called when [show] can't put the window up (no bottom display, or the window manager refused it). */
    private val onShowFailed: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    @Volatile
    var isShowing = false
        private set

    fun toggle() = if (isShowing) hide() else show()

    @SuppressLint("ClickableViewAccessibility")
    fun show() = onMain {
        if (isShowing) return@onMain
        val display = topology.bottomDisplay ?: run {
            Log.w(TAG, "no bottom display; cannot show overlay")
            onShowFailed()
            return@onMain
        }
        val displayContext = context.createDisplayContext(display)
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = topology.realSize(display)

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(displayContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { content(::hide) }
            alpha = 0f
            translationY = size.y * SLIDE_FRACTION
        }

        val lp = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            fitInsetsTypes = 0
        }

        try {
            wm.addView(view, lp)
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed", t)
            owner.onDestroy()
            onShowFailed()
            return@onMain
        }
        composeView = view
        lifecycleOwner = owner
        isShowing = true
        owner.onResume()
        view.animate()
            .alpha(1f).translationY(0f)
            .setDuration(ANIM_MS).setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hide() = onMain {
        val view = composeView ?: return@onMain
        val owner = lifecycleOwner
        isShowing = false
        view.animate()
            .alpha(0f).translationY(view.height * SLIDE_FRACTION)
            .setDuration(ANIM_MS).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                runCatching {
                    (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .removeViewImmediate(view)
                }
                owner?.onDestroy()
            }
            .start()
        composeView = null
        lifecycleOwner = null
    }

    fun destroy() = onMain {
        composeView?.let { view ->
            runCatching {
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
            }
        }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        isShowing = false
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "OverlayHost"
        const val ANIM_MS = 200L
        const val SLIDE_FRACTION = 0.90f
    }
}
