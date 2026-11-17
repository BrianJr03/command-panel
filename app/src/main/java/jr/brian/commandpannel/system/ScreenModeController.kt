package jr.brian.commandpannel.system

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.SystemSettings.Keys

/**
 * Applies the stock screen-mode blanking as `dual_screen_display_mode` changes (observed, exactly like the stock
 * `TccServices`): 0 = dual, 1 = top only (blank bottom), 2 = bottom only (blank top).
 *
 * For each single-screen mode it saves and later restores `screen_focus_lock` / `enable_system_key_focus_lock`,
 * sets `sys.temp.disabled.touch` through the root shell, and covers the blanked panel with a black
 * `TYPE_APPLICATION_OVERLAY` window.
 *
 * Stock covers the top with a privileged `TYPE_BOOT_PROGRESS` window that the vendor exempts from its single-screen
 * dim. Our app-type overlay can't use that window type, and the vendor force-dims any app window on the "off" panel
 * to alpha 0.8, so the cover alone only dims the screen. To actually black it out we also drop the blanked panel's
 * backlight to 0 through the root helper — snapshotting both panels' brightness on the dual→single transition (so a
 * value already pulled down by blanking never overwrites it) and restoring it when dual returns. On the top panel the
 * system status bar (a higher-z window) still shows through the overlay, but its backlight is off too, so the panel
 * reads as black.
 */
class ScreenModeController(
    private val context: Context,
    private val privileged: PrivilegedAccess,
    private val onHideOverlay: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)
    private val prefs = context.getSharedPreferences("command_panel", Context.MODE_PRIVATE)
    private val settings = SystemSettings(context.contentResolver, privileged)

    private var bottomCover: View? = null
    private var topCover: View? = null
    private var tracked = -1

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = apply(settings.getInt(Keys.DISPLAY_MODE, 0))
    }

    fun start() {
        context.contentResolver.registerContentObserver(settings.uriFor(Keys.DISPLAY_MODE), false, observer)
        tracked = settings.getInt(Keys.DISPLAY_MODE, 0)
        apply(tracked)
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(observer)
    }

    fun isBottomBlanked(): Boolean = bottomCover != null

    private fun apply(mode: Int) {
        val old = tracked
        tracked = mode
        when (mode) {
            0 -> restoreDual()
            1 -> blankBottom(fromDual = old == 0)
            2 -> blankTop(fromDual = old == 0)
        }
    }

    private fun saveFocusState() {
        prefs.edit()
            .putInt("saved_focus_lock", settings.getInt(Keys.FOCUS_LOCK, 0))
            .putInt("saved_sys_key_lock", settings.getInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 0))
            .apply()
    }

    private fun restoreDual() {
        setTouch("0,0")
        removeBottomCover()
        removeTopCover()
        // Both panels come back on: a normal app window can't stay fully opaque on the Thor's "off" screen (the
        // vendor force-dims it to 0.8), so the cover is paired with a backlight-off; undo it here.
        restoreBrightness(topology.bottomDisplay, KEY_BRIGHTNESS_BOTTOM)
        restoreBrightness(topology.topDisplay, KEY_BRIGHTNESS_TOP)
        settings.putInt(Keys.FOCUS_LOCK, prefs.getInt("saved_focus_lock", 0))
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, prefs.getInt("saved_sys_key_lock", 0))
    }

    private fun blankBottom(fromDual: Boolean) {
        if (fromDual) { saveFocusState(); snapshotBrightness() }
        removeTopCover()
        onHideOverlay()
        settings.putInt(Keys.FOCUS_LOCK, 1)
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
        setTouch("0,1")
        showBottomCover()
        // Cover + backlight-off = genuinely black bottom panel; top panel returns to its saved level.
        dropBacklight(topology.bottomDisplay)
        restoreBrightness(topology.topDisplay, KEY_BRIGHTNESS_TOP)
    }

    private fun blankTop(fromDual: Boolean) {
        if (fromDual) { saveFocusState(); snapshotBrightness() }
        removeBottomCover()
        onHideOverlay()
        settings.putInt(Keys.FOCUS_LOCK, 2)
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
        setTouch("1,0")
        showTopCover()
        dropBacklight(topology.topDisplay)
        restoreBrightness(topology.bottomDisplay, KEY_BRIGHTNESS_BOTTOM)
    }

    /**
     * Captures both panels' brightness at the moment we leave dual, so the blanked panel can be restored to the
     * user's real level later. Taken only on the dual→single transition (not on single↔single switches) so a
     * value already pulled down by blanking never overwrites the saved one.
     */
    private fun snapshotBrightness() {
        saveBrightness(topology.bottomDisplay, KEY_BRIGHTNESS_BOTTOM)
        saveBrightness(topology.topDisplay, KEY_BRIGHTNESS_TOP)
    }

    private fun saveBrightness(display: android.view.Display?, prefKey: String) {
        val id = display?.displayId ?: return
        val current = privileged.getDisplayBrightness(id)
        if (current != null && current.isFinite() && current > MIN_LIT_BRIGHTNESS) {
            prefs.edit().putFloat(prefKey, current).apply()
        }
    }

    /** Drops a panel's backlight to 0 — a genuinely black screen, since an app cover alone can't beat the vendor dim. */
    private fun dropBacklight(display: android.view.Display?) {
        privileged.setDisplayBrightness(display?.displayId ?: return, 0f, false)
    }

    /** Restores the display to the brightness captured before blanking (defaults to full if nothing was saved). */
    private fun restoreBrightness(display: android.view.Display?, prefKey: String) {
        val id = display?.displayId ?: return
        val saved = prefs.getFloat(prefKey, 1f)
        if (saved > MIN_LIT_BRIGHTNESS && saved <= 1f) {
            privileged.setDisplayBrightness(id, saved, false)
        }
    }

    private fun setTouch(value: String) {
        privileged.shell("setprop sys.temp.disabled.touch $value")
    }

    private fun showBottomCover() = main.post {
        if (bottomCover != null) return@post
        bottomCover = addCover(topology.bottomDisplay)
    }

    private fun removeBottomCover() = main.post {
        removeCover(bottomCover)
        bottomCover = null
    }

    private fun showTopCover() = main.post {
        if (topCover != null) return@post
        topCover = addCover(topology.topDisplay)
    }

    private fun removeTopCover() = main.post {
        removeCover(topCover)
        topCover = null
    }

    /** Adds a full-screen opaque black window over [display] (TYPE_APPLICATION_OVERLAY, non-focusable/non-touchable). */
    private fun addCover(display: android.view.Display?): View? {
        display ?: return null
        val displayContext = context.createDisplayContext(display)
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = topology.realSize(display)
        val view = View(displayContext).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val lp = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.CENTER
            fitInsetsTypes = 0
            // Keep the cover fully opaque: no window alpha, no dim-behind — a solid black panel like stock.
            alpha = 1f
            dimAmount = 0f
        }
        return runCatching { wm.addView(view, lp); view }
            .onFailure { android.util.Log.w("ScreenModeController", "cover addView failed on display ${display.displayId}", it) }
            .getOrNull()
    }

    private fun removeCover(view: View?) {
        view ?: return
        runCatching {
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        }
    }

    private companion object {
        /** Below this, a saved brightness is treated as "already off" and not used as a restore target. */
        const val MIN_LIT_BRIGHTNESS = 0.01f
        const val KEY_BRIGHTNESS_BOTTOM = "saved_brightness_bottom"
        const val KEY_BRIGHTNESS_TOP = "saved_brightness_top"
    }
}
