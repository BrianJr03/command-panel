package jr.brian.commandpannel.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import jr.brian.commandpannel.privileged.PrivilegedAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tap-to-wake for the Thor "turn off handheld console's screen" setting
 * (`Settings.System["close_screen_when_output_video"]`), ported from the fork and adapted to Command Panel's
 * [PrivilegedAccess]/`SystemSettings` instead of its repository flow.
 *
 * While the setting is `1` and an external DisplayPort display is connected, the firmware blacks out both handheld
 * panels below the window layer (panels stay `ON`, touch keeps reaching apps). A `1×1` `FLAG_WATCH_OUTSIDE_TOUCH`
 * window per display reports any touch as `ACTION_OUTSIDE` without consuming it; on a tap the setting is written back
 * to `0`, which the firmware applies live to wake both screens. DP presence comes from
 * `/sys/class/drm/card0-DP-1/status`, re-checked on the vendor `*_STATUS_CHANGED` broadcasts and polled while armed.
 */
class ExternalDisplayTapWaker(
    private val context: Context,
    private val privileged: PrivilegedAccess,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val TAG = "ExtDisplayTapWaker"
        const val KEY = "close_screen_when_output_video"
        const val DP_STATUS_PATH = "/sys/class/drm/card0-DP-1/status"
        const val POLL_INTERVAL_MS = 3000L
        val WATCHED_DISPLAY_IDS = intArrayOf(0, 4)
        val DP_STATUS_ACTIONS = arrayOf(
            "com.ro.action.DISPLAY_PORT_STATUS_CHANGED",
            "com.ro.action.HDMI_STATUS_CHANGED",
            "com.ro.action.HDMI_OR_DP_STATUS_CHANGED",
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val watchers = mutableMapOf<Int, Pair<WindowManager, View>>()
    private var pollJob: Job? = null
    private var receiverRegistered = false

    private fun isEnabled(): Boolean =
        runCatching { Settings.System.getInt(context.contentResolver, KEY, 0) }.getOrDefault(0) == 1

    private val settingObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = evaluate()
    }
    private val dpStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            scope.launch { recheck() }
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(KEY), false, settingObserver)
        runCatching {
            val filter = IntentFilter().apply { DP_STATUS_ACTIONS.forEach { addAction(it) } }
            context.registerReceiver(dpStatusReceiver, filter, Context.RECEIVER_EXPORTED)
            receiverRegistered = true
        }.onFailure { Log.w(TAG, "DP status receiver registration failed", it) }
        evaluate()
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(settingObserver) }
        if (receiverRegistered) runCatching { context.unregisterReceiver(dpStatusReceiver) }
        receiverRegistered = false
        pollJob?.cancel(); pollJob = null
        detachWatchers()
    }

    private fun evaluate() {
        if (isEnabled()) {
            if (pollJob?.isActive == true) return
            pollJob = scope.launch {
                while (isActive && isEnabled()) {
                    recheck()
                    delay(POLL_INTERVAL_MS)
                }
                detachWatchers()
            }
        } else {
            pollJob?.cancel(); pollJob = null
            detachWatchers()
        }
    }

    private suspend fun recheck() {
        val armed = isEnabled() && isExternalDisplayConnected()
        main.post { if (armed) attachWatchers() else detachWatchers() }
    }

    private suspend fun isExternalDisplayConnected(): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(DP_STATUS_PATH).readText().trim() == "connected" }.getOrDefault(false)
    }

    private fun attachWatchers() {
        for (displayId in WATCHED_DISPLAY_IDS) {
            if (watchers.containsKey(displayId)) continue
            val display = displayManager?.getDisplay(displayId) ?: continue
            runCatching {
                val displayContext = context.createDisplayContext(display)
                val wm = displayContext.getSystemService(WindowManager::class.java) ?: return@runCatching
                val view = createWatcherView(displayContext, displayId)
                val lp = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    title = "CommandPanelTapWaker$displayId"
                }
                wm.addView(view, lp)
                watchers[displayId] = wm to view
            }.onFailure { Log.w(TAG, "attach watcher on display $displayId failed", it) }
        }
    }

    private fun detachWatchers() {
        if (watchers.isEmpty()) return
        for ((_, entry) in watchers) runCatching { entry.first.removeViewImmediate(entry.second) }
        watchers.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWatcherView(displayContext: Context, displayId: Int): View =
        View(displayContext).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE ||
                    event.actionMasked == MotionEvent.ACTION_DOWN
                ) onTouchDown()
                false
            }
        }

    private fun onTouchDown() {
        if (watchers.isEmpty()) return
        Log.d(TAG, "tap detected; waking handheld screens")
        // A root write: off the main thread (this runs from the touch listener).
        scope.launch { privileged.run("settings", "put", "system", KEY, "0") }
        detachWatchers()
    }
}
