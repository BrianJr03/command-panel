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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import jr.brian.commandpannel.overlay.ExternalDisplayTapWaker
import jr.brian.commandpannel.overlay.OverlayHost
import jr.brian.commandpannel.plugin.PanelPluginRegistry
import jr.brian.commandpannel.plugin.PanelPluginSession
import jr.brian.commandpannel.plugin.PanelPluginSwap.EndReason
import jr.brian.commandpannel.system.DisplayTopology
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.BdsaDetector
import jr.brian.commandpannel.system.Prefs
import jr.brian.commandpannel.system.QuickLaunch
import jr.brian.commandpannel.system.QuickLaunchAfter
import jr.brian.commandpannel.system.ScreenModeController
import jr.brian.commandpannel.system.SettingsRepository
import jr.brian.commandpannel.system.StockCoexistence
import jr.brian.commandpannel.system.VolumeKeyChain
import jr.brian.commandpannel.ui.overlay.OverlayActions
import jr.brian.commandpannel.ui.overlay.OverlayRoot
import jr.brian.commandpannel.ui.overlay.PanelPluginButton
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
    private lateinit var quickLaunchReturn: QuickLaunchReturnWatcher
    /** Installed Panel plugins (their nav buttons) and the session that hands them the bottom screen (#19). */
    private lateinit var plugins: PanelPluginRegistry
    private lateinit var pluginSession: PanelPluginSession
    private val handler = Handler(Looper.getMainLooper())
    /** Double-press feedback; our own window, since system toasts are dropped while the app has no window showing. */
    private val toast by lazy { jr.brian.commandpannel.overlay.OverlayToast(applicationContext) }

    /**
     * True while Better Dual Screen Assistant, or the other Command Panel build (debug / release), claims the hardware.
     * Command Panel then stands by: it ignores the AYN button, leaves the stock process alone, and stops driving screen
     * modes, so the two never fight. Its own UI (the companion and an explicitly requested overlay preview) keeps
     * working.
     */
    @Volatile private var standby = false
    /** Whether the hardware-facing parts (screen modes, tap waker) are running; flips with [standby]. */
    private var hardwareRunning = false
    /** Set once the startup standby decision is made; re-checks before that are ignored. */
    private var standbyDecided = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    quickLaunchReturn.cancel()
                    // The lid closed (or the screen went off): the panel is what's there when it opens.
                    pluginSession.end(EndReason.SCREEN_OFF)
                    coexistence.onScreenOff()
                }
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
                    val extras = intent.extras
                    // Read like BDSA: a missing key_action_down is a key-up, never a long press (#14).
                    @Suppress("DEPRECATION")
                    val event = AynButtonEvent.parse { key -> extras?.get(key) }
                    val down = event.down
                    val long = event.longPress
                    val keys = extras?.keySet()?.sorted()?.joinToString(",").orEmpty()
                    Log.d(TAG, "AYN button down=$down long=$long standby=$standby extras=[$keys]")
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
                VolumeKeyChain.ACTION -> {
                    val keyType = intent.getStringExtra(VolumeKeyChain.EXTRA_KEY_TYPE)
                    val owns = stockReplaced()
                    Log.d(TAG, "volume key $keyType stockReplaced=$owns")
                    if (owns) repository.onVolumeKey(keyType)
                }
                ACTION_SHOW_OVERLAY -> showPanel(auto = intent.getBooleanExtra(EXTRA_AUTO, false))
                ACTION_HIDE_OVERLAY -> overlay.hide()
                ACTION_TOGGLE_OVERLAY -> togglePanel(EndReason.PANEL_REQUESTED)
                ACTION_SET_REPLACE_STOCK -> coexistence.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
                ACTION_RECHECK_COEXISTENCE -> recheckStandby(force = true)
                // Registered only in debug builds; checked again so a release build never acts on them.
                ACTION_DEBUG_PLUGIN_START -> if (BuildConfig.DEBUG) debugStartPlugin(intent)
                ACTION_DEBUG_PLUGIN_END -> if (BuildConfig.DEBUG) pluginSession.end(EndReason.DEBUG)
                ACTION_REPLAY_SET_ENABLED -> scope.launch(Dispatchers.IO) {
                    if (intent.hasExtra(EXTRA_ENABLE)) replay.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
                    else replay.applyEnabledState()
                }
                ACTION_REPLAY_REARM -> scope.launch(Dispatchers.IO) {
                    val armed = Prefs.of(applicationContext).getBoolean("replay_enabled", false) && replay.rearm()
                    sendBroadcast(Intent(ACTION_REPLAY_REARMED).setPackage(packageName).putExtra(EXTRA_REPLAY_ARMED, armed))
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
        isRunning = true
        keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        repository = SettingsRepository(applicationContext, privileged)
        screenMode = ScreenModeController(
            applicationContext, privileged,
            onHideOverlay = {
                // A screen mode blanked a panel; Command Panel owns the bottom screen's power, so the plugin goes too.
                pluginSession.end(EndReason.BOTTOM_BLANKED)
                overlay.hide()
            },
            onBrightnessRestored = { repository.refresh() },
        )
        repository.onHideOverlay = { overlay.hide() }
        coexistence = StockCoexistence(applicationContext, privileged, scope)
        tapWaker = ExternalDisplayTapWaker(applicationContext, privileged, scope)
        replay = jr.brian.commandpannel.system.ReplayBufferController(applicationContext, privileged)
        bdsa = BdsaDetector(applicationContext, privileged)
        plugins = PanelPluginRegistry(applicationContext, scope)
        pluginSession = PanelPluginSession(
            applicationContext,
            bottomDisplayId = { DisplayTopology(applicationContext).bottomDisplay?.displayId },
            showPanel = { onVisible -> overlay.show(onVisible) },
            hidePanel = { overlay.hide() },
        )
        val overlayActions = object : OverlayActions by repository {
            override fun openPanelPlugin(key: String) = startPanelPlugin(key)
        }
        overlay = OverlayHost(
            this,
            content = { _ ->
                val state by repository.uiState.collectAsState()
                val installed by plugins.plugins.collectAsState()
                // Theme Studio's unsaved draft, shown live while it's open.
                val draft by jr.brian.commandpannel.ui.theme.StudioDraft.look.collectAsState()
                val shown = draft?.let { state.copy(look = it.baseLook, customLook = it) } ?: state
                OverlayRoot(
                    state = shown.copy(panelPlugins = installed.map { PanelPluginButton(it.component, it.label) }),
                    actions = overlayActions,
                )
            },
            onVisibilityChanged = { repository.onOverlayVisibilityChanged(it) },
        )
        quickLaunchReturn = QuickLaunchReturnWatcher(
            context = applicationContext,
            scope = scope,
            shell = { privileged.shell(it) },
            isDeviceAwake = {
                getSystemService(android.os.PowerManager::class.java).isInteractive && !keyguard.isKeyguardLocked
            },
            // A plugin on the bottom screen counts as the panel being up: the user is busy there, so stop watching.
            isPanelShowing = { overlay.isShowing || pluginSession.busy },
            canReopen = { !standby && !screenMode.isBottomBlanked() && !pluginSession.busy },
            reopen = { overlay.show() },
        )
        repository.onQuickLaunched = ::onQuickLaunched
        machine = AynButtonMachine(
            isKeyguardLocked = { keyguard.isKeyguardLocked },
            isBottomBlanked = { screenMode.isBottomBlanked() },
            // During a plugin session the AYN press ends it and brings the panel back, instead of toggling.
            onToggleOverlay = { togglePanel(EndReason.AYN_PRESS) },
            // Long press: the plugin hides first, then the panel hides and the bottom screen blanks as usual.
            onHideOverlay = {
                pluginSession.end(EndReason.AYN_LONG_PRESS)
                overlay.hide()
            },
            onBlankBottom = { repository.selectMode(1) },
            onWakeBottom = { repository.selectMode(0) },
            doublePressEnabled = { Prefs.of(applicationContext).getBoolean(PREF_DOUBLE_PRESS_REPLAY, false) },
            onDoublePress = { saveOrArmReplay(SOURCE_DOUBLE_PRESS) },
            schedule = { ms, block ->
                val r = Runnable(block)
                handler.postDelayed(r, ms)
                ({ handler.removeCallbacks(r) })
            },
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
        // A plugin left showing by a service that died mid-session is told to hide; then list what's installed.
        pluginSession.clearStale()
        plugins.refresh()
        scope.launch {
            privileged.initialize(applicationContext)
            repository.start(scope)
            // Decide standby before touching the stock process or screen modes, so Command Panel never acts, even
            // briefly, while Better Dual Screen Assistant or the other build is in control (e.g. at boot).
            val inControl = withContext(Dispatchers.IO) {
                // The other build claimed stock more recently: this one's "Replace stock assistant" goes off.
                if (coexistence.sibling.siblingOwns()) coexistence.standDownForSibling()
                otherInControl(force = true)
            }
            applyStandby(inControl)
            coexistence.sibling.observe(::onStockOwnerChanged)
            withContext(Dispatchers.IO) { replay.applyEnabledState() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // The companion's automatic preview on launch is skipped on standby, so it doesn't cover the other app's
            // overlay; an explicit show (preview button) still works.
            ACTION_SHOW_OVERLAY -> showPanel(auto = intent.getBooleanExtra(EXTRA_AUTO, false))
            ACTION_HIDE_OVERLAY -> overlay.hide()
            ACTION_TOGGLE_OVERLAY -> togglePanel(EndReason.PANEL_REQUESTED)
            ACTION_SET_REPLACE_STOCK -> coexistence.setEnabled(intent.getBooleanExtra(EXTRA_ENABLE, false))
            ACTION_RECHECK_COEXISTENCE -> recheckStandby(force = true)
            // Only reachable through ExternalTriggerReceiver (the service isn't exported); a no-op on standby.
            ACTION_REPLAY_SAVE_OR_ARM -> saveOrArmReplay(SOURCE_EXTERNAL)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(aynReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        coexistence.sibling.stop()
        coexistence.onStop()
        if (hardwareRunning) tapWaker.stop()
        runCatching { replay.disarm() }
        quickLaunchReturn.cancel()
        pluginSession.destroy()
        repository.stop()
        if (hardwareRunning) screenMode.stop()
        overlay.destroy()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Every request to show the panel goes through here, so the bottom screen has one owner (#19): showing the panel
     * ends a plugin session first, which brings the panel back. The companion's automatic preview ([auto]) skips the
     * show on standby, and leaves an active plugin alone.
     */
    private fun showPanel(auto: Boolean) {
        if (auto && standby) return
        if (pluginSession.keepsPluginOnShow(auto)) {
            Log.i(TAG, "automatic show skipped: a plugin has the bottom screen")
            return
        }
        pluginSession.end(EndReason.PANEL_REQUESTED)
        overlay.show()
    }

    /** Toggles the panel, or, during a plugin session, ends it (which brings the panel back). */
    private fun togglePanel(reason: EndReason) {
        if (!pluginSession.end(reason)) overlay.toggle()
    }

    /** A plugin's nav button: hands it the bottom screen, unless Command Panel is on standby or the screen is blanked. */
    private fun startPanelPlugin(key: String) {
        if (standby || screenMode.isBottomBlanked()) {
            Log.i(TAG, "plugin $key not started: standby=$standby blanked=${screenMode.isBottomBlanked()}")
            return
        }
        val plugin = plugins.find(key) ?: run {
            Log.w(TAG, "plugin $key isn't listed")
            return
        }
        quickLaunchReturn.cancel()
        pluginSession.start(plugin)
    }

    /**
     * Debug builds only, for scripts: starts a plugin session as its nav button would. [EXTRA_PLUGIN_COMPONENT] picks
     * the service (default: the first listed one); [EXTRA_ALLOW_UNTRUSTED] also accepts a plugin signed with a key
     * that isn't allowlisted (a debug-signed test plugin). The version check still applies.
     */
    private fun debugStartPlugin(intent: Intent) {
        val wanted = intent.getStringExtra(EXTRA_PLUGIN_COMPONENT)
            ?.let { android.content.ComponentName.unflattenFromString(it)?.flattenToString() }
        val allowUntrusted = intent.getBooleanExtra(EXTRA_ALLOW_UNTRUSTED, false)
        scope.launch {
            val found = withContext(Dispatchers.IO) { plugins.query(allowUntrusted) }
            val plugin = if (wanted != null) found.firstOrNull { it.key == wanted } else found.firstOrNull()
            Log.i(TAG, "debug plugin start: wanted=$wanted untrusted=$allowUntrusted found=${plugin?.key}")
            if (plugin == null || standby || screenMode.isBottomBlanked()) return@launch
            // A second start while a session is busy is a no-op: showing the panel first would put it over the plugin.
            if (pluginSession.busy) {
                Log.i(TAG, "debug plugin start ignored: a session is busy")
                return@launch
            }
            quickLaunchReturn.cancel()
            // The panel is normally up when a tile is picked; a script may start from anywhere.
            overlay.show()
            pluginSession.start(plugin)
        }
    }

    /**
     * AYN double press or an external trigger ([source]): arms the Replay Buffer if it's off, otherwise saves a clip.
     * Either way, a toast says so. Only the external trigger checks standby here; the button handler already keeps
     * double presses from reaching the machine on standby.
     */
    private fun saveOrArmReplay(source: String) {
        scope.launch(Dispatchers.IO) {
            val action = replayTriggerAction(replay.isArmed, standby = source == SOURCE_EXTERNAL && standby)
            Log.i(TAG, "save-or-arm ($source): $action")
            when (action) {
                ReplayTriggerAction.NONE -> return@launch
                ReplayTriggerAction.ARM -> {
                    replay.setEnabled(true)
                    val armed = if (source == SOURCE_EXTERNAL) R.string.msg_replay_trigger_armed else R.string.msg_replay_double_armed
                    toast.show(getString(if (replay.isArmed) armed else R.string.msg_replay_double_arm_failed))
                    return@launch
                }
                ReplayTriggerAction.SAVE -> Unit
            }
            // Say it right away; a second toast follows only if the save fails.
            val saving = when (replay.armedTarget) {
                jr.brian.commandpannel.privileged.ReplayTarget.TOP -> R.string.msg_replay_double_saving_top
                jr.brian.commandpannel.privileged.ReplayTarget.BOTTOM -> R.string.msg_replay_double_saving_bottom
                jr.brian.commandpannel.privileged.ReplayTarget.BOTH -> R.string.msg_replay_double_saving_both
            }
            toast.show(getString(saving, replay.clipSeconds()))
            val result = replay.save()
            Log.i(TAG, "replay save ($source): $result")
            sendBroadcast(Intent(ACTION_REPLAY_SAVED).setPackage(packageName).putExtra(EXTRA_REPLAY_RESULT, result))
            if (result.startsWith("error:")) toast.show(getString(R.string.msg_save_failed, result.removePrefix("error:")))
        }
    }

    /**
     * Whether Command Panel stands in for the stock assistant, so it chains the volume keys to the bottom level (#7).
     * Stock does that in a receiver its `TccServices` registers, and "Replace stock assistant" keeps that service from
     * running: the process is frozen, and from the teardown its app binds the service only once at startup, which
     * fails while the suppress has it disabled, so it should stay down in the brief unfreeze windows too. With the option off, stock is running and chains them
     * itself; on standby the other replacement owns stock. Either way, acting too would move the level two steps.
     */
    private fun stockReplaced(): Boolean = !standby && coexistence.enabled.value

    /**
     * Quick Launch just opened the app: close the panel, keep it, or close it and watch for the app to close (#9).
     * A new launch always replaces an earlier watch.
     */
    private fun onQuickLaunched() {
        quickLaunchReturn.cancel()
        val display = QuickLaunch.savedDisplay(applicationContext)
        when (QuickLaunch.savedAfter(applicationContext).effectiveFor(display)) {
            QuickLaunchAfter.CLOSE -> overlay.hide()
            QuickLaunchAfter.KEEP_OPEN -> Unit
            QuickLaunchAfter.REOPEN -> {
                overlay.hide()
                QuickLaunch.savedComponent(applicationContext)?.let {
                    quickLaunchReturn.watch(it.packageName, display.displayId)
                }
            }
        }
    }

    /** Re-checks for Better Dual Screen Assistant ([force] skips the detector's short cache) and applies the result. */
    private fun recheckStandby(force: Boolean) {
        scope.launch {
            val active = withContext(Dispatchers.IO) { otherInControl(force) }
            if (standbyDecided && active != standby) applyStandby(active)
        }
    }

    /** Blocking. Whether BDSA or the other Command Panel build currently replaces the stock assistant. */
    private fun otherInControl(force: Boolean): Boolean = bdsa.detect(force).active || coexistence.sibling.siblingOwns()

    /**
     * The other build claimed (or released) the stock replacement. A claim turns this build's "Replace stock
     * assistant" off first, without restoring stock, then standby follows; a release lets this build leave standby.
     */
    private fun onStockOwnerChanged() {
        scope.launch {
            val siblingOwns = withContext(Dispatchers.IO) { coexistence.sibling.siblingOwns() }
            if (siblingOwns) coexistence.standDownForSibling()
            recheckStandby(force = false)
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
            quickLaunchReturn.cancel()
            pluginSession.end(EndReason.STANDBY)
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
            val pkg = intent.data?.schemeSpecificPart
            // Any install, update or removal can add or drop a Panel plugin.
            plugins.refresh()
            if (pkg == coexistence.sibling.siblingPackage) {
                // An owner that was uninstalled can't hand over; clear its record so neither build waits on it.
                val removed = intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                scope.launch {
                    if (removed) withContext(Dispatchers.IO) { coexistence.sibling.clearIfOwner(pkg) }
                    recheckStandby(force = true)
                }
            } else if (pkg in BdsaDetector.PACKAGES) {
                recheckStandby(force = true)
            }
        }
    }

    private fun registerAynReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_TCC_BUTTON)
            addAction(VolumeKeyChain.ACTION)
            addAction(ACTION_SHOW_OVERLAY)
            addAction(ACTION_HIDE_OVERLAY)
            addAction(ACTION_TOGGLE_OVERLAY)
            addAction(ACTION_SET_REPLACE_STOCK)
            addAction(ACTION_RECHECK_COEXISTENCE)
            addAction(ACTION_REPLAY_SET_ENABLED)
            addAction(ACTION_REPLAY_SAVE)
            addAction(ACTION_REPLAY_REARM)
            if (BuildConfig.DEBUG) {
                addAction(ACTION_DEBUG_PLUGIN_START)
                addAction(ACTION_DEBUG_PLUGIN_END)
            }
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

        const val ACTION_SHOW_OVERLAY = "jr.brian.commandpannel.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "jr.brian.commandpannel.action.HIDE_OVERLAY"
        const val ACTION_TOGGLE_OVERLAY = "jr.brian.commandpannel.action.TOGGLE_OVERLAY"
        const val ACTION_SET_REPLACE_STOCK = "jr.brian.commandpannel.action.SET_REPLACE_STOCK"
        const val ACTION_REPLAY_SET_ENABLED = "jr.brian.commandpannel.action.REPLAY_SET_ENABLED"
        const val ACTION_REPLAY_SAVE = "jr.brian.commandpannel.action.REPLAY_SAVE"
        const val ACTION_REPLAY_SAVED = "jr.brian.commandpannel.action.REPLAY_SAVED"
        const val EXTRA_REPLAY_RESULT = "replay_result"
        /** Restart a running Replay Buffer so a changed capture setting applies now; answered by [ACTION_REPLAY_REARMED]. */
        const val ACTION_REPLAY_REARM = "jr.brian.commandpannel.action.REPLAY_REARM"
        const val ACTION_REPLAY_REARMED = "jr.brian.commandpannel.action.REPLAY_REARMED"
        const val EXTRA_REPLAY_ARMED = "replay_armed"
        /** Arm the Replay Buffer if it's off, otherwise save a clip; sent by [ExternalTriggerReceiver]. */
        const val ACTION_REPLAY_SAVE_OR_ARM = "jr.brian.commandpannel.action.REPLAY_SAVE_OR_ARM"
        const val SOURCE_DOUBLE_PRESS = "double_press"
        const val SOURCE_EXTERNAL = "external"
        const val EXTRA_ENABLE = "enable"
        const val PREF_DOUBLE_PRESS_REPLAY = "ayn_double_press_replay"
        /** Re-run Better Dual Screen Assistant detection now (sent by the companion after it changes BDSA). */
        const val ACTION_RECHECK_COEXISTENCE = "jr.brian.commandpannel.action.RECHECK_COEXISTENCE"
        /** On [ACTION_SHOW_OVERLAY]: an automatic show (companion launch) that standby may skip. */
        const val EXTRA_AUTO = "auto"
        /** Debug builds only: start a Panel plugin session, as its nav button would (scripts). */
        const val ACTION_DEBUG_PLUGIN_START = "jr.brian.commandpannel.action.DEBUG_PLUGIN_START"
        /** Debug builds only: end the Panel plugin session. */
        const val ACTION_DEBUG_PLUGIN_END = "jr.brian.commandpannel.action.DEBUG_PLUGIN_END"
        /** On [ACTION_DEBUG_PLUGIN_START]: the plugin service, as `package/class`; the first listed one when missing. */
        const val EXTRA_PLUGIN_COMPONENT = "component"
        /** On [ACTION_DEBUG_PLUGIN_START]: also accept a plugin whose signing key isn't allowlisted. */
        const val EXTRA_ALLOW_UNTRUSTED = "allow_untrusted"

        /** True between [onCreate] and [onDestroy], so [ExternalTriggerReceiver] never starts the service itself. */
        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java)
            context.startForegroundService(intent)
        }
    }
}
