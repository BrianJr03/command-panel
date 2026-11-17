package jr.brian.commandpannel.privileged.helper

import android.util.Log

/**
 * Per-display brightness via the hidden `DisplayManagerGlobal` API, runnable only from the root helper (the app's
 * own uid is denied `CONTROL_DISPLAY_BRIGHTNESS`, as the capability probe showed). Methods are resolved reflectively
 * because they are not in the public SDK.
 */
object DisplayBrightness {
    private const val TAG = "DisplayBrightness"

    private val global: Any? by lazy {
        runCatching {
            val cls = Class.forName("android.hardware.display.DisplayManagerGlobal")
            cls.getMethod("getInstance").invoke(null)
        }.getOrNull()
    }

    fun get(displayId: Int): Float {
        val g = global ?: return Float.NaN
        return runCatching {
            val info = g.javaClass.getMethod("getBrightnessInfo", Int::class.javaPrimitiveType)
                .invoke(g, displayId) ?: return Float.NaN
            info.javaClass.getField("brightness").getFloat(info)
        }.getOrElse {
            Log.w(TAG, "getBrightnessInfo($displayId) failed", it)
            Float.NaN
        }
    }

    fun set(displayId: Int, brightness: Float, temporary: Boolean) {
        val g = global ?: return
        runCatching {
            val name = if (temporary) "setTemporaryBrightness" else "setBrightness"
            g.javaClass.getMethod(name, Int::class.javaPrimitiveType, Float::class.javaPrimitiveType)
                .invoke(g, displayId, brightness)
        }.onFailure { Log.w(TAG, "set($displayId,$brightness,temp=$temporary) failed", it) }
    }
}
