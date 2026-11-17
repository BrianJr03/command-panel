package jr.brian.commandpannel.system.update

import android.content.Context
import androidx.core.content.edit

/** Update-check bookkeeping in the app's `command_panel` prefs (not backed up). */
class UpdatePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("command_panel", Context.MODE_PRIVATE)

    /** A version the user chose "Skip this version" for; automatic checks don't offer it again. */
    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED, null)
        set(v) = prefs.edit { putString(KEY_SKIPPED, v) }

    /** When the last check ran (ms), so automatic checks run at most once an hour. */
    var lastCheck: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(v) = prefs.edit { putLong(KEY_LAST_CHECK, v) }

    /** The version whose patch notes were last shown (or that was first installed); What's New shows on change. */
    var whatsNewSeen: String?
        get() = prefs.getString(KEY_WHATS_NEW, null)
        set(v) = prefs.edit { putString(KEY_WHATS_NEW, v) }

    fun isAutoCheckDue(now: Long = System.currentTimeMillis()): Boolean = now - lastCheck > AUTO_CHECK_MS

    private companion object {
        const val KEY_SKIPPED = "update_skipped_version"
        const val KEY_LAST_CHECK = "update_last_check"
        const val KEY_WHATS_NEW = "whats_new_seen_version"
        const val AUTO_CHECK_MS = 60 * 60 * 1000L
    }
}
