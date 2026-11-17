package jr.brian.commandpannel.privileged

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads AYN's stock drawables at runtime from the installed stock app ([StockSuppressor.STOCK_PKG]) instead of
 * bundling copies of them. The stock app is a non-removable system app whose APK is world-readable, so this needs no
 * root and lets Command Panel ship zero AYN icon art.
 *
 * The stock [Resources] handle is resolved **once per process** and cached (the overlay lives in a persistent
 * foreground service, so that is effectively once until reboot); name→id lookups are cached too. We hand back a fresh
 * [Drawable] per call so each render site can tint independently.
 */
object StockResources {
    private const val TAG = "StockResources"
    private const val STOCK_APK = "/system/app/DualScreenAssistant/DualScreenAssistant.apk"

    @Volatile private var cached: Resources? = null
    private val idCache = ConcurrentHashMap<String, Int>()
    private val lock = Any()

    /** The stock app's resources, resolved once and reused. Null only if the stock app can't be reached at all. */
    private fun resources(context: Context): Resources? {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val resolved = fromPackageManager(context) ?: fromApkPath(context)
            if (resolved == null) Log.w(TAG, "stock resources unavailable — icons will use the placeholder")
            else Log.i(TAG, "stock resources resolved (cached for the process)")
            cached = resolved
            return resolved
        }
    }

    /** Primary path: the installed package's resources (QUERY_ALL_PACKAGES / a <queries> entry makes it visible). */
    private fun fromPackageManager(context: Context): Resources? = runCatching {
        context.packageManager.getResourcesForApplication(StockSuppressor.STOCK_PKG)
    }.getOrNull()

    /** Fallback: build Resources straight from the world-readable system APK, independent of package visibility. */
    private fun fromApkPath(context: Context): Resources? = runCatching {
        val assets = AssetManager::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val cookie = AssetManager::class.java
            .getMethod("addAssetPath", String::class.java)
            .invoke(assets, STOCK_APK) as Int
        if (cookie == 0) return null
        @Suppress("DEPRECATION")
        Resources(assets, context.resources.displayMetrics, context.resources.configuration)
    }.getOrNull()

    /** A fresh [Drawable] for a stock drawable [name], or null if the stock app lacks it / is unreachable. */
    fun drawable(context: Context, name: String): Drawable? {
        val res = resources(context) ?: return null
        val id = idCache.getOrPut(name) { res.getIdentifier(name, "drawable", StockSuppressor.STOCK_PKG) }
        if (id == 0) return null
        return runCatching { ResourcesCompat.getDrawable(res, id, null) }.getOrNull()
    }
}
