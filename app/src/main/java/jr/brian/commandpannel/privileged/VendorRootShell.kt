package jr.brian.commandpannel.privileged

import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.nio.charset.StandardCharsets

/**
 * Client for the AYN Thor firmware's `PServerBinder` service, which runs a shell command as root for the caller.
 *
 * Protocol is the vendor's own (the stock `com.odin.dualscreen.assistant` uses the same binder): service name
 * `PServerBinder`, transaction `0`, request is a two-element string array `[command, waitFlag]` where waitFlag is
 * "1" to wait for and return stdout, the reply is a UTF-8 byte array. No interface token.
 *
 * This is the bootstrap used once at startup to self-grant the app's own permissions and to launch the long-lived
 * [RootHelper]; steady-state privileged calls go through that helper, not through a shell per call.
 */
object VendorRootShell {
    private const val TAG = "VendorRootShell"
    private const val SERVICE = "PServerBinder"

    @Volatile
    private var cached: IBinder? = null

    private fun binder(): IBinder? {
        cached?.let { if (it.isBinderAlive) return it }
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val get = sm.getMethod("getService", String::class.java)
            (get.invoke(null, SERVICE) as? IBinder).also { cached = it }
        } catch (t: Throwable) {
            Log.w(TAG, "ServiceManager.getService($SERVICE) failed", t)
            null
        }
    }

    fun isAvailable(): Boolean = binder() != null

    /** Runs [command] as root. Returns stdout (trimmed) when [wait], "" on an empty reply, or null if unreachable. */
    fun exec(command: String, wait: Boolean = true): String? {
        val b = binder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(command, if (wait) "1" else "0"))
            b.transact(0, data, reply, 0)
            val bytes = reply.createByteArray()
            if (bytes != null) String(bytes, StandardCharsets.UTF_8).trimEnd() else ""
        } catch (t: Throwable) {
            Log.w(TAG, "exec failed: $command", t)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}
