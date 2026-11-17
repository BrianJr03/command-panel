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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import jr.brian.commandpannel.overlay.ExternalDisplayTapWaker
import jr.brian.commandpannel.overlay.OverlayHost
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.ScreenModeController
import jr.brian.commandpannel.system.SettingsRepository
import jr.brian.commandpannel.system.StockCoexistence
import jr.brian.commandpannel.ui.overlay.OverlayRoot
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
    private lateinit var repository: SettingsRepository
    private lateinit var screenMode: ScreenModeController
    private lateinit var coexistence: StockCoexistence
    private lateinit var tapWaker: ExternalDisplayTapWaker

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> coexistence.onScreenOff()
                Intent.ACTION_SCREEN_ON -> coexistence.onScreenOn()
            }
        }
    }

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
                ACTION_SET_REPLACE_STOCK -> coexistence.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        repository = SettingsRepository(applicationContext, privileged)
        screenMode = ScreenModeController(applicationContext, privileged, onHideOverlay = { overlay.hide() })
        repository.onHideOverlay = { overlay.hide() }
        coexistence = StockCoexistence(applicationContext, privileged, scope)
        tapWaker = ExternalDisplayTapWaker(applicationContext, privileged, scope)
        overlay = OverlayHost(this) { _ ->
            val state by repository.uiState.collectAsState()
            OverlayRoot(state = state, actions = repository)
        }
        machine = AynButtonMachine(
            isKeyguardLocked = { keyguard.isKeyguardLocked },
            isBottomBlanked = { screenMode.isBottomBlanked() },
            onToggleOverlay = { overlay.toggle() },
            onHideOverlay = { overlay.hide() },
            onBlankBottom = { repository.selectMode(1) },
            onWakeBottom = { repository.selectMode(0) },
        )

        startAsForeground()
        registerAynReceiver()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        scope.launch {
            privileged.initialize(applicationContext)
            repository.start(scope)
            screenMode.start()
            coexistence.applyInitial()
            tapWaker.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> overlay.show()
            ACTION_HIDE_OVERLAY -> overlay.hide()
            ACTION_TOGGLE_OVERLAY -> overlay.toggle()
            ACTION_SET_REPLACE_STOCK -> coexistence.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(aynReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        coexistence.onStop()
        tapWaker.stop()
        repository.stop()
        screenMode.stop()
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
            addAction(ACTION_SET_REPLACE_STOCK)
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
        const val ACTION_SET_REPLACE_STOCK = "jr.brian.commandpannel.action.SET_REPLACE_STOCK"
        const val EXTRA_ENABLE = "enable"

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java)
            context.startForegroundService(intent)
        }
    }
}
