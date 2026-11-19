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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import jr.brian.commandpannel.system.DisplayTopology
import java.util.function.Consumer

/**
 * The overlay window's blur-behind, for looks that want it ([jr.brian.commandpannel.ui.theme.LookTraits.blurBehind]).
 * The overlay [request]s it for its look; [active] says whether the window really blurs, which is false when the
 * device has window blur off (developer option, battery saver). Not provided outside the overlay window.
 */
class OverlayWindowBlur internal constructor(private val onRequest: (Float?) -> Unit) {
    var active by mutableStateOf(false)
        internal set

    /** Blurs behind the window at [radiusDp], or turns blur off when it's `null`. */
    fun request(radiusDp: Float?) = onRequest(radiusDp)
}

val LocalOverlayWindowBlur = staticCompositionLocalOf<OverlayWindowBlur?> { null }

/**
 * Owns the bottom-screen overlay window: a `ComposeView` attached to the bottom display's `WindowManager`.
 *
 * Window type is `TYPE_APPLICATION_OVERLAY` (2038) — the probe confirmed a user app may add it on display 4, while
 * the stock app's `TYPE_BOOT_PROGRESS` (2021) is denied. `FLAG_LAYOUT_NO_LIMITS` is never set (it would lay the
 * surface out at the top display's width and clip the right edge). `WindowManagerService` ignores window animations
 * for a non-system overlay, so show/hide is animated on the view (200 ms slide + fade), matching stock's feel.
 * `FLAG_ALT_FOCUSABLE_IM` (set by stock too) keeps the soft keyboard above the panel: a window that is only
 * `FLAG_NOT_FOCUSABLE` is Z-ordered over the input method and would cover a game's keyboard.
 *
 * All window mutations run on the main thread.
 */
class OverlayHost(
    private val context: Context,
    private val content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var blurRadiusDp: Float? = null
    private val blur = OverlayWindowBlur { radiusDp -> onMain { blurRadiusDp = radiusDp; applyBlur() } }
    private val blurEnabledListener = Consumer<Boolean> { applyBlur() }

    @Volatile
    var isShowing = false
        private set

    fun toggle() = if (isShowing) hide() else show()

    @SuppressLint("ClickableViewAccessibility")
    fun show() = onMain {
        if (isShowing) return@onMain
        val display = topology.bottomDisplay ?: run {
            Log.w(TAG, "no bottom display; cannot show overlay")
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
            setContent { CompositionLocalProvider(LocalOverlayWindowBlur provides blur) { content(::hide) } }
            alpha = 0f
            translationY = size.y * SLIDE_FRACTION
        }

        val lp = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
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
            return@onMain
        }
        composeView = view
        lifecycleOwner = owner
        layoutParams = lp
        isShowing = true
        wm.addCrossWindowBlurEnabledListener(main::post, blurEnabledListener)
        applyBlur()
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
        releaseBlur(view)
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
            releaseBlur(view)
            runCatching {
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
            }
        }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        isShowing = false
    }

    /** Turns the window's blur-behind on or off to match the look's request and whether the device allows it. */
    private fun applyBlur() {
        val view = composeView ?: return
        val lp = layoutParams ?: return
        val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val radiusDp = blurRadiusDp
        val on = radiusDp != null && wm.isCrossWindowBlurEnabled
        val radius = if (radiusDp != null && on) (radiusDp * view.resources.displayMetrics.density).toInt() else 0
        val flags = if (on) lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        else lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
        if (flags != lp.flags || radius != lp.blurBehindRadius) {
            lp.flags = flags
            lp.blurBehindRadius = radius
            runCatching { wm.updateViewLayout(view, lp) }.onFailure { Log.w(TAG, "blur update failed", it) }
        }
        blur.active = on
    }

    private fun releaseBlur(view: ComposeView) {
        runCatching {
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .removeCrossWindowBlurEnabledListener(blurEnabledListener)
        }
        layoutParams = null
        blur.active = false
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
