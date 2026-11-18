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
import jr.brian.commandpannel.system.BdsaDetector
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
import kotlinx.coroutines.withContext
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
    private lateinit var replay: jr.brian.commandpannel.system.ReplayBufferController
    private lateinit var bdsa: BdsaDetector

    /**
     * True while Better Dual Screen Assistant claims the hardware. Command Panel then stands by: it ignores the AYN
     * button, leaves the stock process alone, and stops driving screen modes, so the two never fight. Its own UI (the
     * companion and an explicitly requested overlay preview) keeps working.
     */
    @Volatile private var standby = false
    /** Whether the hardware-facing parts (screen modes, tap waker) are running; flips with [standby]. */
    private var hardwareRunning = false
    /** Set once the startup standby decision is made; re-checks before that are ignored. */
    private var standbyDecided = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> coexistence.onScreenOff()
                Intent.ACTION_SCREEN_ON -> {
                    coexistence.onScreenOn()
                    recheckStandby(force = false)
                }
            }
        }
    }

    private val aynReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TCC_BUTTON -> {
                    val down = intent.getBooleanExtra(EXTRA_KEY_DOWN, true)
                    val long = intent.getBooleanExtra(EXTRA_KEY_LONG, true)
                    Log.d(TAG, "AYN button down=$down long=$long standby=$standby")
                    // On standby the other replacement answers the button; a cheap (cached) re-check picks up its
                    // being turned off.
                    if (standby) {
                        recheckStandby(force = false)
                    } else {
                        // The stock app also got this press if it's no longer frozen; take the button back.
                        if (down) coexistence.ensureSuppressed()
                        machine.onEvent(down, long)
                    }
                }
                ACTION_SHOW_OVERLAY -> if (!(standby && intent.getBooleanExtra(EXTRA_AUTO, false))) overlay.show()
                ACTION_HIDE_OVERLAY -> overlay.hide()
                ACTION_TOGGLE_OVERLAY -> overlay.toggle()
                ACTION_SET_REPLACE_STOCK -> coexistence.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
                ACTION_RECHECK_COEXISTENCE -> recheckStandby(force = true)
                ACTION_REPLAY_SET_ENABLED -> scope.launch(Dispatchers.IO) {
                    if (intent.hasExtra(EXTRA_ENABLE)) replay.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
                    else replay.applyEnabledState()
                }
                ACTION_REPLAY_SAVE -> scope.launch(Dispatchers.IO) {
                    val result = replay.save()
                    Log.i(TAG, "replay save: $result")
                    sendBroadcast(Intent(ACTION_REPLAY_SAVED).setPackage(packageName).putExtra(EXTRA_REPLAY_RESULT, result))
                }
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
        replay = jr.brian.commandpannel.system.ReplayBufferController(applicationContext, privileged)
        bdsa = BdsaDetector(applicationContext, privileged)
        overlay = OverlayHost(
            this,
            content = { _ ->
                val state by repository.uiState.collectAsState()
                // Theme Studio's unsaved draft, shown live while it's open.
                val draft by jr.brian.commandpannel.ui.theme.StudioDraft.look.collectAsState()
                OverlayRoot(
                    state = draft?.let { state.copy(look = it.baseLook, customLook = it) } ?: state,
                    actions = repository,
                )
            },
            // Onboarding that can't be shown counts as done, so the companion tour isn't left waiting for it.
            onShowFailed = {
                val seen = jr.brian.commandpannel.system.Prefs.of(this)
                    .getBoolean(jr.brian.commandpannel.ui.overlay.OverlayOnboarding.KEY_SEEN, false)
                if (!seen) repository.endOnboarding()
            },
        )
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
        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        scope.launch {
            privileged.initialize(applicationContext)
            repository.start(scope)
            // Decide standby before touching the stock process or screen modes, so Command Panel never acts, even
            // briefly, while Better Dual Screen Assistant is in control (e.g. at boot).
            applyStandby(withContext(Dispatchers.IO) { bdsa.detect(force = true) }.active)
            withContext(Dispatchers.IO) { replay.applyEnabledState() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // The companion's automatic preview on launch is skipped on standby, so it doesn't cover the other app's
            // overlay; an explicit show (preview button) still works.
            ACTION_SHOW_OVERLAY -> if (!(standby && intent.getBooleanExtra(EXTRA_AUTO, false))) overlay.show()
            ACTION_HIDE_OVERLAY -> overlay.hide()
            ACTION_TOGGLE_OVERLAY -> overlay.toggle()
            ACTION_SET_REPLACE_STOCK -> coexistence.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
            ACTION_RECHECK_COEXISTENCE -> recheckStandby(force = true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(aynReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        coexistence.onStop()
        if (hardwareRunning) tapWaker.stop()
        runCatching { replay.disarm() }
        repository.stop()
        if (hardwareRunning) screenMode.stop()
        overlay.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Re-checks for Better Dual Screen Assistant ([force] skips the detector's short cache) and applies the result. */
    private fun recheckStandby(force: Boolean) {
        scope.launch {
            val active = withContext(Dispatchers.IO) { bdsa.detect(force) }.active
            if (standbyDecided && active != standby) applyStandby(active)
        }
    }

    /**
     * Enters or leaves standby. Entering stops screen-mode handling and the tap waker and hides the overlay, without
     * restoring or unfreezing stock (the other app owns it). Leaving starts them again and re-applies "Replace stock
     * assistant"; the other app already restored stock when it stood down, so there is nothing to hand over.
     */
    private fun applyStandby(active: Boolean) {
        standbyDecided = true
        standby = active
        coexistence.standby = active
        Log.i(TAG, "standby=$active")
        if (active) {
            overlay.hide()
            if (hardwareRunning) {
                screenMode.stop()
                tapWaker.stop()
                hardwareRunning = false
            }
        } else if (!hardwareRunning) {
            screenMode.start()
            tapWaker.start()
            hardwareRunning = true
            coexistence.applyInitial()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.data?.schemeSpecificPart in BdsaDetector.PACKAGES) recheckStandby(force = true)
        }
    }

    private fun registerAynReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TCC_BUTTON)
            addAction(ACTION_SHOW_OVERLAY)
            addAction(ACTION_HIDE_OVERLAY)
            addAction(ACTION_TOGGLE_OVERLAY)
            addAction(ACTION_SET_REPLACE_STOCK)
            addAction(ACTION_RECHECK_COEXISTENCE)
            addAction(ACTION_REPLAY_SET_ENABLED)
            addAction(ACTION_REPLAY_SAVE)
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
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_running))
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
        const val ACTION_REPLAY_SET_ENABLED = "jr.brian.commandpannel.action.REPLAY_SET_ENABLED"
        const val ACTION_REPLAY_SAVE = "jr.brian.commandpannel.action.REPLAY_SAVE"
        const val ACTION_REPLAY_SAVED = "jr.brian.commandpannel.action.REPLAY_SAVED"
        const val EXTRA_REPLAY_RESULT = "replay_result"
        const val EXTRA_ENABLE = "enable"
        /** Re-run Better Dual Screen Assistant detection now (sent by the companion after it changes BDSA). */
        const val ACTION_RECHECK_COEXISTENCE = "jr.brian.commandpannel.action.RECHECK_COEXISTENCE"
        /** On [ACTION_SHOW_OVERLAY]: an automatic show (companion launch) that standby may skip. */
        const val EXTRA_AUTO = "auto"

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java)
            context.startForegroundService(intent)
        }
    }
}
