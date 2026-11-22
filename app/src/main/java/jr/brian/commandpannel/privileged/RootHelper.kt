package jr.brian.commandpannel.privileged

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import jr.brian.commandpannel.privileged.helper.HelperProtocol
import jr.brian.commandpannel.privileged.helper.IRootHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-side manager for the root helper process. Launches [RootHelperMain] through [VendorRootShell] via
 * `app_process`; the helper publishes its binder in `ServiceManager`, which this picks up with `getService`.
 *
 * Supervision runs both ways. When the helper dies, this reconnects with [HelperProtocol.backoffMs] backoff. When
 * this app dies, the helper waits [HelperProtocol.GRACE_MS] for it to come back: [ensureConnected] adopts an
 * already-published helper before spawning one, so a restarted app picks the same helper up again. A helper running
 * from an older APK (after an update) is shut down and replaced.
 *
 * Steady-state privileged work (brightness, root shell, window ops added in later phases) goes through [binder]
 * rather than spawning a shell per call.
 */
object RootHelper {
    private const val TAG = "RootHelper"
    private const val SPAWN_TIMEOUT_MS = 8_000L
    private const val POLL_INTERVAL_MS = 150L
    private const val MAX_ATTEMPTS = 3

    enum class State { DISCONNECTED, CONNECTING, CONNECTED, UNAVAILABLE }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile
    private var helper: IRootHelper? = null
    private val clientToken = Binder()

    val binder: IRootHelper? get() = helper
    val isConnected: Boolean get() = helper?.asBinder()?.isBinderAlive == true

    /** Set once [ensureConnected] has run; supervision reconnects with it. Cleared by [shutdown]. */
    @Volatile
    private var appContext: Context? = null
    private val supervisor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "RootHelperSupervisor").apply { isDaemon = true }
    }
    private var reconnect: ScheduledFuture<*>? = null

    /**
     * Whether the helper should restore the stock assistant if this app goes away for good. Kept here so a
     * reconnected (or replacement) helper is told again in [adopt].
     */
    @Volatile
    var stockRecoveryArmed: Boolean = false
        set(value) {
            field = value
            helper?.let { runCatching { it.setStockRecoveryArmed(value) } }
        }

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "helper binder died")
        helper = null
        _state.value = State.DISCONNECTED
        scheduleReconnect(0)
    }

    private fun scheduleReconnect(attempt: Int) {
        val ctx = appContext ?: return
        synchronized(supervisor) {
            reconnect?.cancel(false)
            val delay = HelperProtocol.backoffMs(attempt)
            Log.i(TAG, "reconnecting in $delay ms (attempt ${attempt + 1})")
            reconnect = supervisor.schedule({
                if (appContext != null && !isConnected && !ensureConnected(ctx)) scheduleReconnect(attempt + 1)
            }, delay, TimeUnit.MILLISECONDS)
        }
    }

    /** Blocking connect: returns true once the helper is up and speaks our protocol version. */
    @Synchronized
    fun ensureConnected(context: Context): Boolean {
        appContext = context.applicationContext
        if (isConnected) return true
        if (!VendorRootShell.isAvailable()) {
            _state.value = State.UNAVAILABLE
            return false
        }
        _state.value = State.CONNECTING
        val appInfo = context.applicationInfo
        val serviceName = HelperProtocol.serviceName(context.packageName)
        // A helper still in its grace period (this app crashed and restarted) is adopted rather than replaced.
        getServiceBinder(serviceName)?.takeIf { it.isBinderAlive }?.let { existing ->
            if (adopt(existing, appInfo.sourceDir)) return true
        }
        repeat(MAX_ATTEMPTS) { attempt ->
            val cmd = buildString {
                append("CLASSPATH=").append(appInfo.sourceDir).append(' ')
                append("app_process /system/bin ")
                append(HelperProtocol.MAIN_CLASS).append(' ')
                append(context.packageName).append(' ')
                // The helper answers only this uid (and root); see RootHelperService.onTransact.
                append(android.os.Process.myUid())
                append(" >/dev/null 2>&1 &")
            }
            VendorRootShell.exec(cmd, wait = false)
            if (awaitService(serviceName, appInfo.sourceDir) && isConnected) return true
            Log.w(TAG, "helper spawn attempt ${attempt + 1} failed")
        }
        _state.value = State.UNAVAILABLE
        return false
    }

    /** Polls ServiceManager for the helper's published binder and adopts it once it appears. */
    private fun awaitService(serviceName: String, apk: String): Boolean {
        val deadline = System.currentTimeMillis() + SPAWN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val ibinder = getServiceBinder(serviceName)
            if (ibinder != null && ibinder.isBinderAlive && adopt(ibinder, apk)) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private fun getServiceBinder(name: String): IBinder? = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        sm.getMethod("getService", String::class.java).invoke(null, name) as? IBinder
    }.getOrNull()

    private fun adopt(ibinder: IBinder, apk: String): Boolean {
        val h = IRootHelper.Stub.asInterface(ibinder)
        val start = System.nanoTime()
        val version = runCatching { h.protocolVersion() }.getOrElse { return false }
        val roundTripUs = (System.nanoTime() - start) / 1_000L
        if (version != HelperProtocol.VERSION) {
            Log.w(TAG, "helper protocol $version != ${HelperProtocol.VERSION}; shutting it down")
            runCatching { h.shutdown() }
            return false
        }
        val identity = runCatching { h.identity() }.getOrNull()
        val helperApk = identity?.getString("apk")
        if (helperApk != apk) {
            Log.w(TAG, "helper runs from $helperApk, not $apk (app updated); shutting it down")
            runCatching { h.shutdown() }
            return false
        }
        runCatching { ibinder.linkToDeath(deathRecipient, 0) }
        runCatching { h.attachClient(clientToken) }
        runCatching { h.setStockRecoveryArmed(stockRecoveryArmed) }
        helper = h
        _state.value = State.CONNECTED
        Log.i(TAG, "helper connected (round trip $roundTripUs µs): $identity")
        return true
    }

    fun shutdown() {
        appContext = null
        synchronized(supervisor) { reconnect?.cancel(false) }
        runCatching { helper?.shutdown() }
        helper = null
        _state.value = State.DISCONNECTED
    }
}
