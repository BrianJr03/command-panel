package jr.brian.commandpannel.system

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Client for the AYN vendor fan service, reached through `ServiceManager` the same way the stock app does
 * (`SettingsController` → `FanProvider`). The capability probe confirmed a user app may call it directly.
 *
 *  - `SettingsController` (`com.ro.settings.IExternalControlManager`) txn 1, writeString("FanProvider") → binder
 *  - `IFanControlProvider`: txn 3 → float live duty (×100 = %), txn 1 → int max and txn 2 → int min of the custom
 *    `fan_speed` range (stock's seekbar does `setMax(txn 1)`, `setMin(txn 2)`; a Thor reports 50000 and 25000),
 *    txn 10 → int (read by stock's fan ring callback, meaning unknown)
 */
object FanProvider {
    private const val TAG = "FanProvider"
    private const val CONTROLLER = "com.ro.settings.IExternalControlManager"
    private const val PROVIDER = "com.ro.settings.IFanControlProvider"

    private fun controller(): IBinder? = runCatching {
        val sm = Class.forName("android.os.ServiceManager")
        sm.getMethod("getService", String::class.java).invoke(null, "SettingsController") as? IBinder
    }.getOrNull()

    private fun providerBinder(): IBinder? {
        val c = controller() ?: return null
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(CONTROLLER)
            data.writeString("FanProvider")
            c.transact(1, data, reply, 0)
            reply.readException()
            reply.readStrongBinder()
        } catch (t: Throwable) {
            Log.w(TAG, "getProvider(FanProvider) failed", t); null
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    // The custom range doesn't change while running, so the first good read is kept.
    @Volatile private var cachedRange: IntRange? = null

    /** The custom `fan_speed` range (low..high, whichever bound comes back larger), or null if unavailable. */
    fun speedRange(): IntRange? {
        cachedRange?.let { return it }
        val p = providerBinder() ?: return null
        return FanControl.speedRange(readInt(p, 1), readInt(p, 2))?.also { cachedRange = it }
    }

    private fun readInt(p: IBinder, txn: Int): Int? {
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PROVIDER)
            p.transact(txn, data, reply, 0)
            reply.readException()
            reply.readInt()
        } catch (t: Throwable) {
            Log.w(TAG, "txn $txn failed", t); null
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    /** Live fan duty as a percentage (0..100), or null if unavailable. */
    fun dutyPercent(): Int? {
        val p = providerBinder() ?: return null
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PROVIDER)
            p.transact(3, data, reply, 0)
            reply.readException()
            (reply.readFloat() * 100f).toInt().coerceIn(0, 100)
        } catch (t: Throwable) {
            Log.w(TAG, "dutyPercent failed", t); null
        } finally {
            data.recycle(); reply.recycle()
        }
    }
}
