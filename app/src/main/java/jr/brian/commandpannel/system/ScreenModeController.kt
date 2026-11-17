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
 * sets `sys.temp.disabled.touch` through the root shell, and covers the blanked panel with a black window. Stock
 * does not change brightness, so neither do we. The bottom cover is a `TYPE_APPLICATION_OVERLAY` window the app can
 * add; the top cover needs the stock app's `TYPE_BOOT_PROGRESS` (helper window, deferred), so mode 2 currently
 * applies focus + touch only.
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
        settings.putInt(Keys.FOCUS_LOCK, prefs.getInt("saved_focus_lock", 0))
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, prefs.getInt("saved_sys_key_lock", 0))
    }

    private fun blankBottom(fromDual: Boolean) {
        if (fromDual) saveFocusState()
        onHideOverlay()
        settings.putInt(Keys.FOCUS_LOCK, 1)
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
        setTouch("0,1")
        showBottomCover()
    }

    private fun blankTop(fromDual: Boolean) {
        if (fromDual) saveFocusState()
        onHideOverlay()
        settings.putInt(Keys.FOCUS_LOCK, 2)
        settings.putInt(Keys.SYSTEM_KEY_FOCUS_LOCK, 1)
        setTouch("1,0")
        // Top cover (TYPE_BOOT_PROGRESS on display 0) needs the root helper window — deferred.
    }

    private fun setTouch(value: String) {
        privileged.shell("setprop sys.temp.disabled.touch $value")
    }

    private fun showBottomCover() = main.post {
        if (bottomCover != null) return@post
        val display = topology.bottomDisplay ?: return@post
        val displayContext = context.createDisplayContext(display)
        val wm = displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = topology.realSize(display)
        val view = View(displayContext).apply { setBackgroundColor(Color.BLACK) }
        val lp = WindowManager.LayoutParams(
            size.x, size.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE,
        ).apply { gravity = Gravity.CENTER; fitInsetsTypes = 0 }
        runCatching { wm.addView(view, lp) }.onSuccess { bottomCover = view }
    }

    private fun removeBottomCover() = main.post {
        val view = bottomCover ?: return@post
        runCatching {
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        }
        bottomCover = null
    }
}
