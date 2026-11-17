package jr.brian.commandpannel.system

import android.content.Context
import android.media.MediaScannerConnection
import jr.brian.commandpannel.privileged.PrivilegedAccess
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup & restore (fork feature) for Command Panel's companion settings. Exports the allow-listed app
 * preferences as JSON to a user-visible folder (via the root shell, so it survives reinstalls), and restores them.
 * Only app-private `command_panel` prefs are covered — vendor `Settings.System` values are not backed up.
 */
object SettingsBackup {
    private const val PREFS = "command_panel"
    private const val DIR = "/sdcard/Documents/CommandPanel/backups"
    const val FORMAT = "command-panel-backup"
    const val SCHEMA = 1

    /** Prefs that are safe and useful to carry across devices/reinstalls. */
    private val EXPORTED = setOf(
        "replace_stock_assistant", "battery_estimate", "hide_net_speed", "clear_tasks_keep_on_screen",
        "suppress_bottom_tip", "rgb_accent_sync", "qs_enabled_tiles",
        "quick_launch_component", "quick_launch_display",
        "overlay_look", CustomLookStore.KEY, "companion_follow_look", "nav_bar_order", "quick_launch_show_app",
    )

    /** Serializes the exported prefs to JSON. Pure, so it is unit-tested. */
    fun encode(all: Map<String, *>, appVersion: String): String {
        val prefs = JSONObject()
        for ((k, v) in all) {
            if (k !in EXPORTED || v == null) continue
            val entry = JSONObject()
            when (v) {
                is Boolean -> entry.put("type", "boolean").put("value", v)
                is Int -> entry.put("type", "int").put("value", v)
                is Long -> entry.put("type", "long").put("value", v)
                is String -> entry.put("type", "string").put("value", v)
                else -> continue
            }
            prefs.put(k, entry)
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA)
            .put("appVersion", appVersion)
            .put("prefs", prefs)
            .toString(2)
    }

    /** Parses a backup into (key -> typed value), or null if it is not a valid Command Panel backup. */
    fun decode(json: String): Map<String, Any>? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("format") != FORMAT || root.optInt("schemaVersion") > SCHEMA) return null
        val prefs = root.optJSONObject("prefs") ?: return null
        val out = HashMap<String, Any>()
        for (key in prefs.keys()) {
            if (key !in EXPORTED) continue
            val e = prefs.optJSONObject(key) ?: continue
            when (e.optString("type")) {
                "boolean" -> out[key] = e.optBoolean("value")
                "int" -> out[key] = e.optInt("value")
                "long" -> out[key] = e.optLong("value")
                "string" -> out[key] = e.optString("value")
            }
        }
        return out
    }

    fun export(context: Context, privileged: PrivilegedAccess): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val json = encode(prefs.all, version)
        val name = "command-panel-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json"
        // Stage in app storage, then copy to the visible folder as root.
        val staged = File(context.cacheDir, name).apply { writeText(json) }
        val dest = "$DIR/$name"
        privileged.shell("mkdir -p $DIR && cp ${staged.absolutePath} $dest && chmod 644 $dest")
        staged.delete()
        MediaScannerConnection.scanFile(context, arrayOf(dest), arrayOf("application/json"), null)
        return dest
    }

    fun list(privileged: PrivilegedAccess): List<String> =
        privileged.shell("ls -1t $DIR 2>/dev/null").orEmpty()
            .lineSequence().filter { it.endsWith(".json") }.map { "$DIR/$it" }.toList()

    fun import(context: Context, privileged: PrivilegedAccess, path: String): Int {
        val json = privileged.shell("cat $path") ?: return 0
        val values = decode(json) ?: return 0
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for ((k, v) in values) when (v) {
            is Boolean -> editor.putBoolean(k, v)
            is Int -> editor.putInt(k, v)
            is Long -> editor.putLong(k, v)
            is String -> editor.putString(k, v)
        }
        editor.apply()
        return values.size
    }
}
