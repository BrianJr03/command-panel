package jr.brian.commandpannel.privileged

import android.util.Log
import java.lang.reflect.Method

/**
 * Lifts hidden-API restrictions for the current process so framework internals (e.g. `ServiceManager`) are
 * reachable by reflection. Reflects on `Class` itself to reach `VMRuntime.setHiddenApiExemptions`. Used by both
 * the app process and the root helper.
 */
object HiddenApi {
    private const val TAG = "HiddenApi"

    @Volatile
    var exempted = false
        private set

    fun exempt(): Boolean {
        if (exempted) return true
        return try {
            val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )
            val vmRuntime = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(vmRuntime, "getRuntime", arrayOfNulls<Class<*>>(0)) as Method
            val setExemptions = getDeclaredMethod.invoke(
                vmRuntime, "setHiddenApiExemptions", arrayOf<Class<*>>(Array<String>::class.java)
            ) as Method
            setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
            exempted = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "hidden-API exemption failed", t)
            false
        }
    }
}
