package jr.brian.commandpannel.system

import android.content.Context
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.ScreenModePlan.Panel
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
 * backlight to 0 through the root helper, saving that panel's level just before (never once it's already dropped)
 * and restoring it when the panel comes back. On the top panel the system status bar (a higher-z window) still shows
 * through the overlay, but its backlight is off too, so the panel reads as black.
 *
 * Only what we changed is undone: a fresh start in dual leaves brightness and focus lock alone (see [ScreenModePlan]).
 * What we changed lives in prefs, so a service restart while a panel is blanked still knows what to restore.
 */
class ScreenModeController(
    private val context: Context,
    private val privileged: PrivilegedAccess,
    private val onHideOverlay: () -> Unit,
    /** Called on the main thread shortly after a panel's brightness was restored, so the sliders can re-read it. */
    private val onBrightnessRestored: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)
    private val prefs = Prefs.of(context)
    private val settings = SystemSettings(context.contentResolver, privileged)

    private var bottomCover: View? = null
    private var topCover: View? = null
    private var tracked = -1

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = apply(settings.getInt(Keys.DISPLAY_MODE, 0))
    }

    private val notifyRestored = Runnable { onBrightnessRestored() }

    fun start() {
        context.contentResolver.registerContentObserver(settings.uriFor(Keys.DISPLAY_MODE), false, observer)
        tracked = settings.getInt(Keys.DISPLAY_MODE, 0)
        migrateLegacyState(tracked)
        apply(tracked)
    }

    fun stop() {
        context.contentResolver.unregisterContentObserver(observer)
        main.removeCallbacks(notifyRestored)
    }

    fun isBottomBlanked(): Boolean = bottomCover != null

    private fun apply(mode: Int) {
        val old = tracked
        tracked = mode
        val plan = ScreenModePlan.plan(readState(), mode, fromDual = old == 0) ?: return
        // Save focus lock before it's overwritten below.
        saveFocusState(plan.saveFocus)
        when (plan.blank) {
            null -> {
                setTouch("0,0")
                removeBottomCover()
                removeTopCover()
            }
            Panel.BOTTOM -> {
                removeTopCover()
                onHideOverlay()
                settings.putInt(Keys.FOCUS_LOCK, 1)
                settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
                setTouch("0,1")
                showBottomCover()
            }
            Panel.TOP -> {
                removeBottomCover()
                onHideOverlay()
                settings.putInt(Keys.FOCUS_LOCK, 2)
                settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
                setTouch("1,0")
                showTopCover()
            }
        }
        // Cover + backlight-off = a genuinely black panel (a normal app window can't stay fully opaque on the
        // Thor's "off" screen: the vendor force-dims it to 0.8).
        plan.blank?.let { blank(it, plan.snapshot) }
        // Only panels we dropped come back, and only to the level we saved.
        plan.restore.forEach { restoreBrightness(it) }
        if (plan.restoreFocus) restoreFocusState()
        if (plan.restore.isNotEmpty()) {
            main.removeCallbacks(notifyRestored)
            // The display applies a new level asynchronously; give it a moment before the sliders re-read it.
            main.postDelayed(notifyRestored, RESTORE_NOTIFY_DELAY_MS)
        }
    }

    /** What we changed ourselves and still have to undo. */
    private fun readState() = ScreenModePlan.State(
        topBlanked = prefs.contains(KEY_BLANKED_TOP),
        bottomBlanked = prefs.contains(KEY_BLANKED_BOTTOM),
        focusSaved = prefs.getBoolean(KEY_FOCUS_SAVED, false),
    )

    private fun saveFocusState(source: ScreenModePlan.FocusSave) {
        val (focus, sysKey) = when (source) {
            ScreenModePlan.FocusSave.NONE -> return
            ScreenModePlan.FocusSave.CURRENT ->
                settings.getInt(Keys.FOCUS_LOCK, 0) to settings.getInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 0)
            ScreenModePlan.FocusSave.DEFAULT -> 0 to 0
        }
        prefs.edit()
            .putInt(KEY_FOCUS_LOCK, focus)
            .putInt(KEY_SYS_KEY_LOCK, sysKey)
            .putBoolean(KEY_FOCUS_SAVED, true)
            .apply()
    }

    private fun restoreFocusState() {
        settings.putInt(Keys.FOCUS_LOCK, prefs.getInt(KEY_FOCUS_LOCK, 0))
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, prefs.getInt(KEY_SYS_KEY_LOCK, 0))
        prefs.edit().remove(KEY_FOCUS_SAVED).apply()
    }

    /**
     * Drops [panel]'s backlight to 0. With [snapshot], first saves its current level (still the user's own, since we
     * haven't dropped it yet) as the restore target. If that level can't be read, the panel keeps its backlight
     * (cover only) rather than go black with nothing to bring it back to. Without [snapshot] the panel is already
     * ours (a service restart while blanked): just drop it again.
     */
    private fun blank(panel: Panel, snapshot: Boolean) {
        val id = displayOf(panel)?.displayId ?: return
        if (snapshot) {
            val current = privileged.getDisplayBrightness(id)
            if (current == null || !ScreenModePlan.isRestorable(current)) {
                Log.w(TAG, "not dropping $panel backlight: level $current can't be restored later")
                return
            }
            prefs.edit().putFloat(keyOf(panel), current).apply()
        }
        privileged.setDisplayBrightness(id, 0f, false)
    }

    /**
     * Brings [panel] back to the level saved when we dropped it, then forgets it. For the top panel it also writes
     * `screen_brightness`, which is what the top slider reads (and observes), the same way the slider commits.
     */
    private fun restoreBrightness(panel: Panel) {
        val key = keyOf(panel)
        val saved = prefs.getFloat(key, Float.NaN)
        prefs.edit().remove(key).apply()
        val id = displayOf(panel)?.displayId ?: return
        if (!ScreenModePlan.isRestorable(saved)) return
        privileged.setDisplayBrightness(id, saved, false)
        if (panel == Panel.TOP) settings.putInt(Keys.SCREEN_BRIGHTNESS, ScreenModePlan.toScreenBrightnessSetting(saved))
    }

    /**
     * One-time move off the old keys, whose saved levels were never cleared (so every start in dual restored them).
     * Only the panel that is blanked right now keeps its old level, and only if it isn't lit.
     */
    private fun migrateLegacyState(mode: Int) {
        if (prefs.getBoolean(KEY_STATE_V2, false)) return
        val edit = prefs.edit()
        val blanked = when (mode) {
            1 -> Panel.BOTTOM
            2 -> Panel.TOP
            else -> null
        }
        if (blanked != null) {
            val legacy = prefs.getFloat(legacyKeyOf(blanked), Float.NaN)
            val current = displayOf(blanked)?.displayId?.let { privileged.getDisplayBrightness(it) }
            if (ScreenModePlan.isRestorable(legacy) && !ScreenModePlan.isRestorable(current)) {
                edit.putFloat(keyOf(blanked), legacy)
            }
            if (prefs.contains(KEY_FOCUS_LOCK)) edit.putBoolean(KEY_FOCUS_SAVED, true)
        }
        edit.remove(legacyKeyOf(Panel.BOTTOM)).remove(legacyKeyOf(Panel.TOP)).putBoolean(KEY_STATE_V2, true).apply()
    }

    private fun displayOf(panel: Panel) = when (panel) {
        Panel.TOP -> topology.topDisplay
        Panel.BOTTOM -> topology.bottomDisplay
    }

    private fun keyOf(panel: Panel) = when (panel) {
        Panel.TOP -> KEY_BLANKED_TOP
        Panel.BOTTOM -> KEY_BLANKED_BOTTOM
    }

    private fun legacyKeyOf(panel: Panel) = when (panel) {
        Panel.TOP -> "saved_brightness_top"
        Panel.BOTTOM -> "saved_brightness_bottom"
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
            .onFailure { Log.w(TAG, "cover addView failed on display ${display.displayId}", it) }
            .getOrNull()
    }

    private fun removeCover(view: View?) {
        view ?: return
        runCatching {
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        }
    }

    private companion object {
        const val TAG = "ScreenModeController"
        const val RESTORE_NOTIFY_DELAY_MS = 300L
        /** Present only while we hold that panel's backlight at 0; the value is the level to restore. */
        const val KEY_BLANKED_BOTTOM = "blanked_brightness_bottom"
        const val KEY_BLANKED_TOP = "blanked_brightness_top"
        /** True only while the focus-lock settings hold our single-screen values. */
        const val KEY_FOCUS_SAVED = "focus_lock_saved"
        const val KEY_FOCUS_LOCK = "saved_focus_lock"
        const val KEY_SYS_KEY_LOCK = "saved_sys_key_lock"
        const val KEY_STATE_V2 = "screen_mode_state_v2"
    }
}
