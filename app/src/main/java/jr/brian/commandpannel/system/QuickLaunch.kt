package jr.brian.commandpannel.system

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import jr.brian.commandpannel.R

/** A launchable app the overlay's Quick Launch button can open. */
data class QuickLaunchApp(val component: ComponentName, val label: String)

/** Screen the Quick Launch app opens on (matches the Thor display ids). */
enum class QuickLaunchDisplay(val displayId: Int, @StringRes val labelRes: Int) {
    TOP(0, R.string.key_focus_mode_lock_on_upper_screen),
    BOTTOM(4, R.string.key_focus_mode_lock_on_lower_screen);

    companion object {
        fun fromId(id: Int): QuickLaunchDisplay = entries.firstOrNull { it.displayId == id } ?: TOP
    }
}

/**
 * What happens to the panel once Quick Launch has opened the app (#9). [key] is the stored pref value.
 * [KEEP_OPEN] only works when the app opens on the other screen than the panel; see [effectiveFor].
 */
enum class QuickLaunchAfter(val key: String, @StringRes val labelRes: Int) {
    CLOSE("close", R.string.settings_after_launch_close),
    KEEP_OPEN("keep_open", R.string.settings_after_launch_keep_open),
    REOPEN("reopen", R.string.settings_after_launch_reopen);

    /**
     * The behavior that really applies for an app opening on [display]. The panel sits on the lower screen, so an app
     * opening there would sit under it: keeping the panel open falls back to closing it.
     */
    fun effectiveFor(display: QuickLaunchDisplay): QuickLaunchAfter =
        if (this == KEEP_OPEN && display.displayId == PANEL_DISPLAY_ID) CLOSE else this

    companion object {
        /** The Thor display the panel is shown on (the lower screen). */
        const val PANEL_DISPLAY_ID = 4

        fun fromKey(key: String?): QuickLaunchAfter = entries.firstOrNull { it.key == key } ?: CLOSE
    }
}

/**
 * Quick Launch (fork feature): persists a chosen launcher app + target display and launches it on that display via
 * `ActivityOptions.setLaunchDisplayId`.
 */
object QuickLaunch {
    private const val KEY_COMPONENT = "quick_launch_component"
    private const val KEY_DISPLAY = "quick_launch_display"
    const val KEY_AFTER = "quick_launch_after"

    /** All launcher activities except this app, sorted by label. Does package-manager I/O — call off the main thread. */
    fun listApps(context: Context): List<QuickLaunchApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map { QuickLaunchApp(ComponentName(it.packageName, it.name), it.loadLabel(pm).toString()) }
            .distinctBy { it.component.flattenToString() }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun savedComponent(context: Context): ComponentName? =
        prefs(context).getString(KEY_COMPONENT, null)?.let { ComponentName.unflattenFromString(it) }

    fun savedDisplay(context: Context): QuickLaunchDisplay =
        QuickLaunchDisplay.fromId(prefs(context).getInt(KEY_DISPLAY, 0))

    /** Current label if the saved app still resolves, else null (uninstalled / disabled). */
    fun savedLabel(context: Context): String? {
        val cn = savedComponent(context) ?: return null
        return runCatching {
            context.packageManager.getActivityInfo(cn, 0).loadLabel(context.packageManager).toString()
        }.getOrNull()
    }

    fun save(context: Context, component: ComponentName, display: QuickLaunchDisplay) {
        prefs(context).edit()
            .putString(KEY_COMPONENT, component.flattenToString())
            .putInt(KEY_DISPLAY, display.displayId)
            .apply()
    }

    fun savedAfter(context: Context): QuickLaunchAfter = QuickLaunchAfter.fromKey(prefs(context).getString(KEY_AFTER, null))

    fun saveAfter(context: Context, after: QuickLaunchAfter) {
        prefs(context).edit().putString(KEY_AFTER, after.key).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_COMPONENT).apply()
    }

    fun launch(context: Context): Boolean {
        val cn = savedComponent(context) ?: return false
        val display = savedDisplay(context)
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle()
            context.startActivity(intent, opts)
            true
        }.getOrDefault(false)
    }

    private fun prefs(context: Context) = Prefs.of(context)
}
