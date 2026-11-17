package jr.brian.commandpannel.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import jr.brian.commandpannel.R
import jr.brian.commandpannel.overlay.OverlayHost
import jr.brian.commandpannel.overlay.OverlayPlaceholder
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

/**
 * Persistent foreground service that owns the bottom-screen overlay and answers the physical AYN button, the way the
 * stock `TccServices` does. It initializes the privileged layer, keeps the root helper connected, hosts the
 * [OverlayHost], and runs the [AynButtonMachine].
 *
 * The AYN broadcast is registered dynamically (while the service lives), matching stock; it is never a manifest
 * receiver. `ACTION_SHOW_OVERLAY` / `ACTION_HIDE_OVERLAY` let the app (and tests, via `am broadcast`) drive it.
 */
@AndroidEntryPoint
class AssistantService : Service() {

    @Inject lateinit var privileged: PrivilegedAccess

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlay: OverlayHost
    private lateinit var keyguard: KeyguardManager
    private lateinit var machine: AynButtonMachine

    private val aynReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TCC_BUTTON -> {
                    val down = intent.getBooleanExtra(EXTRA_KEY_DOWN, true)
                    val long = intent.getBooleanExtra(EXTRA_KEY_LONG, true)
                    Log.d(TAG, "AYN button down=$down long=$long")
                    machine.onEvent(down, long)
                }
                ACTION_SHOW_OVERLAY -> overlay.show()
                ACTION_HIDE_OVERLAY -> overlay.hide()
                ACTION_TOGGLE_OVERLAY -> overlay.toggle()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        overlay = OverlayHost(this) { dismiss -> OverlayPlaceholder(dismiss) }
        machine = AynButtonMachine(
            isKeyguardLocked = { keyguard.isKeyguardLocked },
            isBottomBlanked = { false }, // real blanking arrives in phase 4
            onToggleOverlay = { overlay.toggle() },
            onHideOverlay = { overlay.hide() },
            onBlankBottom = { overlay.hide() }, // placeholder until phase 4
            onWakeBottom = { }, // placeholder until phase 4
        )

        startAsForeground()
        registerAynReceiver()
        scope.launch { privileged.initialize(applicationContext) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> overlay.show()
            ACTION_HIDE_OVERLAY -> overlay.hide()
            ACTION_TOGGLE_OVERLAY -> overlay.toggle()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(aynReceiver) }
        overlay.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerAynReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TCC_BUTTON)
            addAction(ACTION_SHOW_OVERLAY)
            addAction(ACTION_HIDE_OVERLAY)
            addAction(ACTION_TOGGLE_OVERLAY)
        }
        // Exported so the firmware's system broadcast and `am broadcast` can reach it.
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(aynReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(aynReceiver, filter)
        }
    }

    private fun startAsForeground() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Command Panel", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Command Panel")
            .setContentText("Dual-screen assistant is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val TAG = "AssistantService"
        private const val CHANNEL_ID = "command_panel"
        private const val NOTIF_ID = 1

        const val ACTION_TCC_BUTTON = "action.tcc.button.key.event"
        const val EXTRA_KEY_DOWN = "key_action_down"
        const val EXTRA_KEY_LONG = "key_isLongPress"

        const val ACTION_SHOW_OVERLAY = "jr.brian.commandpannel.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "jr.brian.commandpannel.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "jr.brian.commandpannel.action.TOGGLE_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java)
            context.startForegroundService(intent)
        }
    }
}
