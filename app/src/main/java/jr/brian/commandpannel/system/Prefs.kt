package jr.brian.commandpannel.system

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import android.util.Log
import java.io.File

/**
 * The app's one `"command_panel"` preferences file, kept in device-protected storage so the direct-boot-aware
 * [jr.brian.commandpannel.service.AssistantService] can read it before the user unlocks (credential-protected
 * prefs throw until then). Every caller must go through [of] so the companion and the service share one instance
 * and its change listeners.
 *
 * Installs that predate this keep the file in credential-protected storage. It is moved over the first time [of]
 * runs while the user is unlocked; until then (a locked first boot after the update) the defaults apply, and the
 * moved file is reloaded into the same instance.
 */
object Prefs {
    const val NAME = "command_panel"
    private const val TAG = "Prefs"

    @Volatile
    private var cached: SharedPreferences? = null
    @Volatile
    private var migrationChecked = false

    fun of(context: Context): SharedPreferences {
        val app = context.applicationContext ?: context
        val de = app.createDeviceProtectedStorageContext()
        if (!migrationChecked) migrateIfUnlocked(app, de)
        return cached ?: synchronized(this) {
            cached ?: de.getSharedPreferences(NAME, Context.MODE_PRIVATE).also { cached = it }
        }
    }

    @Synchronized
    private fun migrateIfUnlocked(ce: Context, de: Context) {
        if (migrationChecked) return
        val unlocked = ce.getSystemService(UserManager::class.java)?.isUserUnlocked == true
        if (!unlocked) return
        migrationChecked = true
        if (!File(ce.dataDir, "shared_prefs/$NAME.xml").exists()) return
        val moved = de.moveSharedPreferencesFrom(ce, NAME)
        Log.i(TAG, "moved prefs to device-protected storage: $moved")
        // An instance loaded before unlock still holds the defaults; MODE_MULTI_PROCESS makes it reload the moved
        // file in place, so listeners already registered on it keep working.
        @Suppress("DEPRECATION")
        if (moved && cached != null) de.getSharedPreferences(NAME, Context.MODE_MULTI_PROCESS)
    }
}
