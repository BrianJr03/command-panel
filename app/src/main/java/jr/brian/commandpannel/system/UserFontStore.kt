package jr.brian.commandpannel.system

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.theme.UserFontResolver
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A font file the user imported for Theme Studio looks. */
data class UserFont(val id: String, val displayName: String)

/** Why a font import failed; [messageRes] is the user-facing explanation. */
class FontImportException(@StringRes val messageRes: Int) : Exception()

/**
 * User-imported TTF/OTF fonts. Files are copied into `filesDir/fonts/<id>.ttf` (app-private, never exported or
 * backed up) and indexed in the `user_fonts` pref. The overlay service runs in the same app, so both surfaces load
 * them from the same files. A look whose font file is missing falls back to the default font.
 */
object UserFontStore {
    private const val KEY = "user_fonts"
    private const val MAX_BYTES = 5L * 1024 * 1024
    private val cache = ConcurrentHashMap<String, FontFamily>()

    private fun prefs(context: Context) = context.getSharedPreferences("command_panel", Context.MODE_PRIVATE)
    private fun dir(context: Context) = File(context.filesDir, "fonts").apply { mkdirs() }
    private fun file(context: Context, id: String) = File(dir(context), "$id.ttf")

    fun all(context: Context): List<UserFont> {
        val arr = runCatching { JSONArray(prefs(context).getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { UserFont(it.optString("id"), it.optString("name")) }
                ?.takeIf { it.id.isNotBlank() && file(context, it.id).exists() }
        }
    }

    private fun write(context: Context, fonts: List<UserFont>) {
        val arr = JSONArray().apply { fonts.forEach { put(JSONObject().put("id", it.id).put("name", it.displayName)) } }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    /** Copies the font at [uri] into app storage after checking its size and that Android can parse it. */
    fun import(context: Context, uri: Uri): Result<UserFont> {
        val resolver = context.contentResolver
        val id = UUID.randomUUID().toString()
        val dest = file(context, id)
        return runCatching {
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }?.substringBeforeLast('.')?.take(40) ?: context.getString(R.string.font_default_name)
            val stream = resolver.openInputStream(uri) ?: throw FontImportException(R.string.msg_font_unreadable)
            stream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw FontImportException(R.string.msg_font_too_large)
                        out.write(buf, 0, n)
                    }
                }
            }
            // An unparseable file yields the default face rather than throwing on newer APIs.
            val parsed = runCatching { Typeface.createFromFile(dest) }.getOrNull()
            if (parsed == null || parsed == Typeface.DEFAULT) throw FontImportException(R.string.msg_font_invalid)
            UserFont(id, name).also { write(context, all(context) + it) }
        }.onFailure { dest.delete() }
    }

    fun delete(context: Context, id: String) {
        cache.remove(id)
        file(context, id).delete()
        write(context, all(context).filterNot { it.id == id })
    }

    /** The family for [id], or `null` when its file is gone. */
    fun family(context: Context, id: String): FontFamily? {
        cache[id]?.let { return it }
        val f = file(context, id)
        if (!f.exists()) return null
        return runCatching { FontFamily(Font(f)) }.getOrNull()?.also { cache[id] = it }
    }

    fun resolver(context: Context): UserFontResolver {
        val app = context.applicationContext
        return UserFontResolver { family(app, it) }
    }

    /** Finds an installed font by display name (used to re-link fonts in an imported look). */
    fun findByName(context: Context, displayName: String): UserFont? =
        all(context).firstOrNull { it.displayName.equals(displayName, ignoreCase = true) }
}
