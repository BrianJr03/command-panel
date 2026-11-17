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
 * Quick Launch (fork feature): persists a chosen launcher app + target display and launches it on that display via
 * `ActivityOptions.setLaunchDisplayId`.
 */
object QuickLaunch {
    private const val PREFS = "command_panel"
    private const val KEY_COMPONENT = "quick_launch_component"
    private const val KEY_DISPLAY = "quick_launch_display"

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

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
