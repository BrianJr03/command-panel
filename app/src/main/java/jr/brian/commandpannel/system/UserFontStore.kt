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
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A font file the user imported for Theme Studio looks. [fileName] is the original file name (`Orbitron.ttf`), which
 * theme packs use to find the font in `TCP Themes/fonts` on another device.
 */
data class UserFont(
    val id: String,
    val displayName: String,
    val fileName: String = "$displayName.ttf",
    /** Where the font was downloaded from (theme packs), so exported packs can tell others where to get it. */
    val url: String? = null,
    val license: String? = null,
)

/** Why a font import failed; [messageRes] is the user-facing explanation. */
class FontImportException(@StringRes val messageRes: Int) : Exception()

/**
 * User-imported TTF/OTF fonts. Files are copied into `filesDir/fonts/<id>.ttf` (app-private, never backed up) and
 * indexed in the `user_fonts` pref. The overlay service runs in the same app, so both surfaces load them from the same
 * files. A look whose font file is missing falls back to the default font. [ThemeProvider] serves these files
 * read-only to the apps on [SharedFontReaders] (docs/SHARED_THEME.md), and to no one else.
 */
object UserFontStore {
    internal const val KEY = "user_fonts"
    private const val MAX_BYTES = 5L * 1024 * 1024
    private val cache = ConcurrentHashMap<String, FontFamily>()
    private val digests = ConcurrentHashMap<String, FileDigest>()

    /** A file's SHA-256, valid while its size and modified time stay the same. */
    private class FileDigest(val length: Long, val modified: Long, val sha256: String)

    private fun prefs(context: Context) = Prefs.of(context)
    private fun dir(context: Context) = File(context.filesDir, "fonts").apply { mkdirs() }
    private fun file(context: Context, id: String) = File(dir(context), "$id.ttf")

    fun all(context: Context): List<UserFont> {
        val arr = runCatching { JSONArray(prefs(context).getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o ->
                val name = o.optString("name")
                UserFont(
                    o.optString("id"), name, o.optString("file").ifBlank { "$name.ttf" },
                    o.optString("url").ifBlank { null }, o.optString("license").ifBlank { null },
                )
            }
                ?.takeIf { it.id.isNotBlank() && file(context, it.id).exists() }
        }
    }

    private fun write(context: Context, fonts: List<UserFont>) {
        val arr = JSONArray().apply { fonts.forEach { put(JSONObject().put("id", it.id).put("name", it.displayName).put("file", it.fileName).put("url", it.url).put("license", it.license)) } }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    /** Copies the font at [uri] (from the system file picker) into app storage. */
    fun import(context: Context, uri: Uri): Result<UserFont> {
        val resolver = context.contentResolver
        return runCatching {
            val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: (context.getString(R.string.font_default_name) + ".ttf")
            val stream = resolver.openInputStream(uri) ?: throw FontImportException(R.string.msg_font_unreadable)
            stream.use { register(context, it, fileName).getOrThrow() }
        }
    }

    /**
     * Stores the font read from [input] as [fileName] after checking its size and that Android can parse it. Used by
     * the picker import, the `TCP Themes/fonts` folder and font downloads.
     */
    fun register(context: Context, input: InputStream, fileName: String, url: String? = null, license: String? = null): Result<UserFont> {
        val id = UUID.randomUUID().toString()
        val dest = file(context, id)
        return runCatching {
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
            // An unparseable file yields the default face rather than throwing on newer APIs.
            val parsed = runCatching { Typeface.createFromFile(dest) }.getOrNull()
            if (parsed == null || parsed == Typeface.DEFAULT) throw FontImportException(R.string.msg_font_invalid)
            UserFont(id, fileName.substringBeforeLast('.').take(40), fileName.take(80), url, license)
                .also { write(context, all(context) + it) }
        }.onFailure { dest.delete() }
    }

    /** The app-private file holding [id] (for copying fonts out into `TCP Themes/fonts`). */
    fun fileOf(context: Context, id: String): File = file(context, id)

    /**
     * The file for imported font [id] when it's indexed and its file sits directly in the fonts folder, otherwise
     * `null`. What the shared theme serves, so an id can never reach another file.
     */
    fun sharedFile(context: Context, id: String): File? {
        if (all(context).none { it.id == id }) return null
        val folder = dir(context).canonicalFile
        return file(context, id).canonicalFile.takeIf { it.parentFile == folder && it.isFile }
    }

    /** SHA-256 of [id]'s file (lowercase hex), or `null` when it isn't an imported font any more. */
    fun sha256(context: Context, id: String): String? {
        val f = sharedFile(context, id) ?: return null
        val length = f.length()
        val modified = f.lastModified()
        digests[id]?.takeIf { it.length == length && it.modified == modified }?.let { return it.sha256 }
        val digest = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        digests[id] = FileDigest(length, modified, hex)
        return hex
    }

    fun delete(context: Context, id: String) {
        cache.remove(id)
        digests.remove(id)
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

    /** Finds an installed font by its original file name (how theme packs reference fonts). */
    fun findByFileName(context: Context, fileName: String): UserFont? =
        all(context).firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
}
