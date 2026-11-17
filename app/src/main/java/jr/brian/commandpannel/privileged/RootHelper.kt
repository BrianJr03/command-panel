package jr.brian.commandpannel.privileged

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import jr.brian.commandpannel.privileged.helper.HelperProtocol
import jr.brian.commandpannel.privileged.helper.IRootHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-side manager for the root helper process. Launches [RootHelperMain] through [VendorRootShell] via
 * `app_process`; the helper publishes its binder in `ServiceManager`, which this picks up with `getService`.
 * The helper exits when this app dies (client token), and the app relaunches a dead helper with backoff.
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

    private val deathRecipient = IBinder.DeathRecipient {
        Log.w(TAG, "helper binder died")
        helper = null
        _state.value = State.DISCONNECTED
    }

    /** Blocking connect: returns true once the helper is up and speaks our protocol version. */
    @Synchronized
    fun ensureConnected(context: Context): Boolean {
        if (isConnected) return true
        if (!VendorRootShell.isAvailable()) {
            _state.value = State.UNAVAILABLE
            return false
        }
        _state.value = State.CONNECTING
        val appInfo = context.applicationInfo
        val serviceName = HelperProtocol.serviceName(context.packageName)
        repeat(MAX_ATTEMPTS) { attempt ->
            val cmd = buildString {
                append("CLASSPATH=").append(appInfo.sourceDir).append(' ')
                append("app_process /system/bin ")
                append(HelperProtocol.MAIN_CLASS).append(' ')
                append(context.packageName)
                append(" >/dev/null 2>&1 &")
            }
            VendorRootShell.exec(cmd, wait = false)
            if (awaitService(serviceName) && isConnected) return true
            Log.w(TAG, "helper spawn attempt ${attempt + 1} failed")
        }
        _state.value = State.UNAVAILABLE
        return false
    }

    /** Polls ServiceManager for the helper's published binder and adopts it once it appears. */
    private fun awaitService(serviceName: String): Boolean {
        val deadline = System.currentTimeMillis() + SPAWN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val ibinder = getServiceBinder(serviceName)
            if (ibinder != null && ibinder.isBinderAlive && adopt(ibinder)) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private fun getServiceBinder(name: String): IBinder? = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        sm.getMethod("getService", String::class.java).invoke(null, name) as? IBinder
    }.getOrNull()

    private fun adopt(ibinder: IBinder): Boolean {
        val h = IRootHelper.Stub.asInterface(ibinder)
        val version = runCatching { h.protocolVersion() }.getOrElse { return false }
        if (version != HelperProtocol.VERSION) {
            Log.w(TAG, "helper protocol $version != ${HelperProtocol.VERSION}; shutting it down")
            runCatching { h.shutdown() }
            return false
        }
        runCatching { ibinder.linkToDeath(deathRecipient, 0) }
        runCatching { h.attachClient(clientToken) }
        helper = h
        _state.value = State.CONNECTED
        Log.i(TAG, "helper connected: ${runCatching { h.identity() }.getOrNull()}")
        return true
    }

    fun shutdown() {
        runCatching { helper?.shutdown() }
        helper = null
        _state.value = State.DISCONNECTED
    }
}
