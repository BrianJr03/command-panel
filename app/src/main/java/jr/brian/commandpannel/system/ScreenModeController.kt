package jr.brian.commandpannel.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootHelper
import jr.brian.commandpannel.system.ScreenModePlan.Panel
import jr.brian.commandpannel.system.SystemSettings.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Screen off, done by Command Panel itself (#35). Holding the AYN button, or picking "only top screen" / "only bottom
 * screen" in the Mode tab, sets our own blank state ([KEY_BLANK], see [ScreenModePlan]); the vendor's
 * `dual_screen_display_mode` stays 0. On a panel that mode marks "off" the firmware force-dims every app window to
 * alpha 0.8, so no cover could be black there; with the mode left at 0 nothing dims it.
 *
 * The blanked panel gets a solid black window that slides in like the panel does. It's touchable and eats every touch,
 * so nothing reaches the apps under it, and it takes no key focus. Controller focus is locked to the visible screen
 * with `screen_focus_lock` / `enable_system_key_focus_lock`, saved first and restored when the panel comes back. The
 * backlight isn't touched. `sys.temp.disabled.touch` isn't used any more: the cover already eats touches, and a cover
 * goes away with our process while a stuck prop would leave a screen without touch.
 *
 * The blank state lives in prefs, so a service restart re-covers the panel; sleep and wake leave the window alone. A
 * vendor mode of 1 or 2 (an older Command Panel, something else) is adopted as our state and put back to 0.
 * Brightness levels that older versions dropped and saved are restored once (#36: kept until the helper takes them).
 */
class ScreenModeController(
    private val context: Context,
    private val privileged: PrivilegedAccess,
    private val onHideOverlay: () -> Unit,
    /** Called on the main thread shortly after a saved brightness was restored, so the sliders can re-read it. */
    private val onBrightnessRestored: () -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val topology = DisplayTopology(context)
    private val prefs = Prefs.of(context)
    private val settings = SystemSettings(context.contentResolver, privileged)

    private var bottomCover: View? = null
    private var topCover: View? = null
    private var blank = ScreenModePlan.NONE
    private var running = false
    private var helperWatch: Job? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_BLANK) main.post { apply(prefs.getInt(KEY_BLANK, ScreenModePlan.NONE)) }
    }

    /** Something other than us set the vendor mode to 1 or 2: take it as our own blank state, then put it back. */
    private val vendorObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = adoptVendorMode()
    }

    private val notifyRestored = Runnable { onBrightnessRestored() }

    fun start() {
        running = true
        prefs.edit().remove(LEGACY_SAVED_BOTTOM).remove(LEGACY_SAVED_TOP).apply()
        adoptVendorMode()
        context.contentResolver.registerContentObserver(settings.uriFor(Keys.DISPLAY_MODE), false, vendorObserver)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        apply(prefs.getInt(KEY_BLANK, ScreenModePlan.NONE), starting = true)
        restoreLegacyBrightness()
        helperWatch?.cancel()
        helperWatch = CoroutineScope(Dispatchers.Main).launch {
            privileged.helperState.collect { if (it == RootHelper.State.CONNECTED) restoreLegacyBrightness() }
        }
    }

    /**
     * Stops following the blank state. [handOver] (standby: something else takes the screens) uncovers the panel and
     * restores focus lock, so whoever handles the screens next starts from dual. Otherwise (the service going away)
     * the state is kept and [start] covers the panel again.
     */
    fun stop(handOver: Boolean) {
        running = false
        context.contentResolver.unregisterContentObserver(vendorObserver)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        main.removeCallbacks(notifyRestored)
        helperWatch?.cancel()
        helperWatch = null
        if (handOver && blank != ScreenModePlan.NONE) {
            prefs.edit().putInt(KEY_BLANK, ScreenModePlan.NONE).apply()
            apply(ScreenModePlan.NONE, force = true)
        } else {
            removeCover(Panel.BOTTOM, animate = false)
            removeCover(Panel.TOP, animate = false)
        }
    }

    /** Sets the blank state (the Mode tab's numbers, see [ScreenModePlan]); the change is applied through prefs. */
    fun select(mode: Int) = select(prefs, mode)

    /** Either screen is covered: a press brings it back. */
    fun isBlanked(): Boolean = blank != ScreenModePlan.NONE

    /** The bottom screen is covered (plugins and Quick Launch stay away from it). */
    fun isBottomBlanked(): Boolean = blank == ScreenModePlan.BLANK_BOTTOM

    private fun adoptVendorMode() {
        val adopted = ScreenModePlan.adoptVendorMode(settings.getInt(Keys.DISPLAY_MODE, 0)) ?: return
        Log.i(TAG, "vendor mode $adopted adopted as our blank state; vendor mode back to 0")
        // An older version also set touch off for the blanked panel; the cover eats touches now.
        privileged.shell("setprop sys.temp.disabled.touch 0,0")
        settings.putInt(Keys.DISPLAY_MODE, 0)
        prefs.edit().putInt(KEY_BLANK, adopted).apply()
    }

    private fun apply(target: Int, starting: Boolean = false, force: Boolean = false) {
        if (!running && !force) return
        if (target == blank && !starting) return
        val from = blank
        val plan = ScreenModePlan.plan(target, prefs.getBoolean(KEY_FOCUS_SAVED, false), fromNone = from == ScreenModePlan.NONE)
            ?: return
        blank = target
        Log.i(TAG, "blank state $from -> $target")
        saveFocusState(plan.saveFocus)
        when (plan.cover) {
            null -> {
                removeCover(Panel.BOTTOM, animate = true)
                removeCover(Panel.TOP, animate = true)
            }
            else -> {
                onHideOverlay()
                plan.focusLock?.let {
                    settings.putInt(Keys.FOCUS_LOCK, it)
                    settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
                }
                removeCover(if (plan.cover == Panel.BOTTOM) Panel.TOP else Panel.BOTTOM, animate = false)
                showCover(plan.cover, animate = !starting)
            }
        }
        if (plan.restoreFocus) restoreFocusState()
    }

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

    // ---- brightness levels saved by older versions ----

    /**
     * Brings back levels an older version dropped and saved, once; each is forgotten only once the helper took it
     * (#36), so with the helper down it's tried again when the helper connects.
     */
    private fun restoreLegacyBrightness() {
        val saved = ScreenModePlan.Saved(top = prefs.contains(KEY_SAVED_TOP), bottom = prefs.contains(KEY_SAVED_BOTTOM))
        val restored = ScreenModePlan.legacyRestores(saved).map { restoreBrightness(it) }
        if (restored.any { it }) {
            main.removeCallbacks(notifyRestored)
            main.postDelayed(notifyRestored, RESTORE_NOTIFY_DELAY_MS)
        }
    }

    /**
     * Sets [panel] back to its saved level, forgetting it only once the helper took it. For the top panel it also
     * writes `screen_brightness`, which the top slider reads, the same way the slider commits.
     */
    private fun restoreBrightness(panel: Panel): Boolean {
        val key = if (panel == Panel.TOP) KEY_SAVED_TOP else KEY_SAVED_BOTTOM
        val saved = prefs.getFloat(key, Float.NaN)
        val id = displayOf(panel)?.displayId
        val applied = id != null && ScreenModePlan.isRestorable(saved) && privileged.setDisplayBrightness(id, saved, false)
        if (ScreenModePlan.keepSavedLevel(saved, applied)) {
            Log.w(TAG, "$panel brightness from an older version not restored yet (level $saved)")
            return false
        }
        prefs.edit().remove(key).apply()
        if (applied) {
            if (panel == Panel.TOP) settings.putInt(Keys.SCREEN_BRIGHTNESS, ScreenModePlan.toScreenBrightnessSetting(saved))
            Log.i(TAG, "$panel brightness restored to $saved (saved by an older version)")
        }
        return applied
    }

    // ---- the cover ----

    private fun displayOf(panel: Panel) = when (panel) {
        Panel.TOP -> topology.topDisplay
        Panel.BOTTOM -> topology.bottomDisplay
    }

    private fun coverOf(panel: Panel) = if (panel == Panel.TOP) topCover else bottomCover

    private fun setCover(panel: Panel, view: View?) {
        if (panel == Panel.TOP) topCover = view else bottomCover = view
    }

    /**
     * A solid black window over the whole of [panel]'s display, sliding in from below like the panel. Touchable, and
     * every touch on it is consumed; not focusable, so keys stay with the visible screen.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showCover(panel: Panel, animate: Boolean) = main.post {
        if (coverOf(panel) != null) return@post
        val display = displayOf(panel) ?: return@post
        val displayContext = context.createDisplayContext(display)
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = topology.realSize(display)
        val view = View(displayContext).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            setOnTouchListener { _, _ -> true }
            if (animate) translationY = size.y * SLIDE_FRACTION
        }
        val lp = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            // Translucent so the part the slide hasn't reached yet stays clear; the view itself is opaque black.
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            fitInsetsTypes = 0
            alpha = 1f
            dimAmount = 0f
            title = COVER_TITLE
        }
        val added = runCatching { wm.addView(view, lp) }
            .onFailure { Log.w(TAG, "cover addView failed on display ${display.displayId}", it) }
            .isSuccess
        if (!added) return@post
        setCover(panel, view)
        Log.i(TAG, "$panel covered")
        if (animate) {
            view.animate().translationY(0f).setDuration(SLIDE_MS).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun removeCover(panel: Panel, animate: Boolean) = main.post {
        val view = coverOf(panel) ?: return@post
        setCover(panel, null)
        val remove = {
            runCatching {
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
            }
            Log.i(TAG, "$panel uncovered")
            Unit
        }
        if (animate && view.isAttachedToWindow) {
            view.animate().translationY(view.height * SLIDE_FRACTION).setDuration(SLIDE_MS)
                .setInterpolator(AccelerateInterpolator()).withEndAction(remove).start()
        } else {
            remove()
        }
    }

    companion object {
        private const val TAG = "ScreenModeController"

        /** Our blank state (the Mode tab's numbers, see [ScreenModePlan]). */
        const val KEY_BLANK = "screen_blank"

        /** Sets the blank state in [prefs]; a running controller applies it. */
        fun select(prefs: SharedPreferences, mode: Int) {
            prefs.edit().putInt(KEY_BLANK, mode).apply()
        }

        /** The blank state in [prefs], for the Mode tab. */
        fun blankState(prefs: SharedPreferences): Int = prefs.getInt(KEY_BLANK, ScreenModePlan.NONE)

        private const val RESTORE_NOTIFY_DELAY_MS = 300L
        private const val SLIDE_MS = 200L
        private const val SLIDE_FRACTION = 0.90f
        private const val COVER_TITLE = "CommandPanelScreenOff"
        /** Levels an older version dropped and saved, to restore once. */
        private const val KEY_SAVED_BOTTOM = "blanked_brightness_bottom"
        private const val KEY_SAVED_TOP = "blanked_brightness_top"
        /** Pre-v2 saved levels that were never cleared; dropped without restoring (#12). */
        private const val LEGACY_SAVED_BOTTOM = "saved_brightness_bottom"
        private const val LEGACY_SAVED_TOP = "saved_brightness_top"
        /** True only while the focus-lock settings hold our single-screen values. */
        private const val KEY_FOCUS_SAVED = "focus_lock_saved"
        private const val KEY_FOCUS_LOCK = "saved_focus_lock"
        private const val KEY_SYS_KEY_LOCK = "saved_sys_key_lock"
    }
}
