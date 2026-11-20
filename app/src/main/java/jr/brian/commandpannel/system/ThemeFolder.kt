package jr.brian.commandpannel.system

import android.content.Context
import android.media.MediaScannerConnection
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.ui.theme.BackgroundImage
import jr.brian.commandpannel.ui.theme.LookPack
import jr.brian.commandpannel.ui.theme.LookPackJson
import java.io.File

/** A pack file found in `TCP Themes/themes` ([path] is absolute; [pack] is null when it couldn't be parsed). */
data class FolderPack(val path: String, val pack: LookPack?)

/**
 * The shared `TCP Themes` folder on internal storage (`/sdcard/TCP Themes`):
 * - `themes/`: theme packs (`*.tcplooks.json`; synced repos keep theme packs in `themes/<repo>/`)
 * - `fonts/`: font files the packs reference by file name
 * - `images/`: background images the packs reference by file name
 *
 * It has the same layout as a theme repo made from the template, so the folder can be shared or pushed as-is. Like
 * [SettingsBackup], all access goes through the root shell, so the app needs no storage permission.
 */
object ThemeFolder {
    const val ROOT = "/sdcard/TCP Themes"
    const val THEMES = "$ROOT/themes"

    /** [path] as the user's storage shows it: `/sdcard` is a shortcut for `/storage/emulated/0`. */
    fun displayPath(path: String): String = path.replaceFirst("/sdcard", "/storage/emulated/0")
    const val FONTS = "$ROOT/fonts"
    const val IMAGES = "$ROOT/images"
    private const val README = "README.txt"

    /** Single-quotes [path] for the shell (the folder name has a space). */
    internal fun q(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    /** Creates the folder layout and its README if missing. Returns false when the root shell isn't available. */
    fun ensure(context: Context, privileged: PrivilegedAccess): Boolean {
        val ok = privileged.shell("mkdir -p ${q(THEMES)} ${q(FONTS)} ${q(IMAGES)} && echo ok")?.trim() == "ok"
        if (ok && privileged.shell("[ -f ${q("$ROOT/$README")} ] && echo yes")?.trim() != "yes") {
            writeText(context, privileged, "$ROOT/$README", context.getString(jr.brian.commandpannel.R.string.theme_folder_readme))
        }
        return ok
    }

    /** Every pack under `themes/` (including synced repo folders), newest first. */
    fun listPacks(privileged: PrivilegedAccess): List<FolderPack> =
        privileged.shell("find ${q(THEMES)} -type f \\( -name '*${LookPackJson.EXTENSION}' -o -name '*.cplook.json' \\) 2>/dev/null")
            .orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.sorted()
            .map { path -> FolderPack(path, readPack(privileged, path)) }.toList()

    fun readPack(privileged: PrivilegedAccess, path: String): LookPack? {
        val text = privileged.shell("head -c ${LookPackJson.MAX_BYTES + 1} ${q(path)}") ?: return null
        return LookPackJson.decode(text, File(path).name.substringBefore('.'))
    }

    /** Writes [pack] to `themes/[subdir]/<file>` and returns the absolute path. */
    fun writePack(context: Context, privileged: PrivilegedAccess, pack: LookPack, fileName: String = LookPackJson.fileName(pack.name), subdir: String? = null): String? {
        val dir = if (subdir == null) THEMES else "$THEMES/$subdir"
        val dest = "$dir/$fileName"
        return if (writeText(context, privileged, dest, LookPackJson.encode(pack))) dest else null
    }

    /** Stages [text] in app storage, then copies it into place as root (as [SettingsBackup.export] does). */
    fun writeText(context: Context, privileged: PrivilegedAccess, dest: String, text: String): Boolean {
        val staged = File(context.cacheDir, "theme-stage-${System.nanoTime()}").apply { writeText(text) }
        val dir = dest.substringBeforeLast('/')
        val ok = privileged.shell("mkdir -p ${q(dir)} && cp ${q(staged.absolutePath)} ${q(dest)} && chmod 644 ${q(dest)} && echo ok")?.trim() == "ok"
        staged.delete()
        if (ok) MediaScannerConnection.scanFile(context, arrayOf(dest), null, null)
        return ok
    }

    fun deletePath(privileged: PrivilegedAccess, path: String) {
        if (path.startsWith("$THEMES/")) privileged.shell("rm -f ${q(path)}")
    }

    fun hasFont(privileged: PrivilegedAccess, fileName: String): Boolean =
        privileged.shell("[ -f ${q("$FONTS/${safeName(fileName)}")} ] && echo yes")?.trim() == "yes"

    /** Imports `fonts/[fileName]` into the app's font store (root copies it somewhere the app can read first). */
    fun importFont(context: Context, privileged: PrivilegedAccess, fileName: String): Result<UserFont> {
        val staged = File(context.cacheDir, "font-stage-${System.nanoTime()}")
        return runCatching {
            val src = "$FONTS/${safeName(fileName)}"
            val ok = privileged.shell("cp ${q(src)} ${q(staged.absolutePath)} && chmod 644 ${q(staged.absolutePath)} && echo ok")?.trim() == "ok"
            if (!ok) throw FontImportException(jr.brian.commandpannel.R.string.msg_font_unreadable)
            staged.inputStream().use { UserFontStore.register(context, it, fileName).getOrThrow() }
        }.also { staged.delete() }
    }

    /** Copies an imported font out to `fonts/<its file name>`, so exported packs travel with their fonts. */
    fun exportFont(context: Context, privileged: PrivilegedAccess, font: UserFont): Boolean {
        val dest = "$FONTS/${safeName(font.fileName)}"
        val src = UserFontStore.fileOf(context, font.id).absolutePath
        return privileged.shell("mkdir -p ${q(FONTS)} && cp ${q(src)} ${q(dest)} && chmod 644 ${q(dest)} && echo ok")?.trim() == "ok"
    }

    fun hasImage(privileged: PrivilegedAccess, fileName: String): Boolean =
        privileged.shell("[ -f ${q("$IMAGES/${safeName(fileName)}")} ] && echo yes")?.trim() == "yes"

    /** Imports `images/[fileName]` into the app's background store under the same name (root stages a readable copy). */
    fun importImage(context: Context, privileged: PrivilegedAccess, fileName: String): Result<BackgroundImage> {
        val staged = File(context.cacheDir, "image-stage-${System.nanoTime()}")
        return runCatching {
            val src = "$IMAGES/${safeName(fileName)}"
            val ok = privileged.shell("cp ${q(src)} ${q(staged.absolutePath)} && chmod 644 ${q(staged.absolutePath)} && echo ok")?.trim() == "ok"
            if (!ok) throw BackgroundImportException(jr.brian.commandpannel.R.string.msg_background_unreadable)
            BackgroundImageStore.importFile(context, staged, fileName = safeName(fileName)).getOrThrow()
        }.also { staged.delete() }
    }

    /** Copies a look's background image out to `images/<its file name>`, so exported packs travel with it. */
    fun exportImage(context: Context, privileged: PrivilegedAccess, image: BackgroundImage): Boolean {
        val src = BackgroundImageStore.file(context, image)
        if (!src.isFile) return false
        val dest = "$IMAGES/${safeName(image.fileName)}"
        return privileged.shell("mkdir -p ${q(IMAGES)} && cp ${q(src.absolutePath)} ${q(dest)} && chmod 644 ${q(dest)} && echo ok")?.trim() == "ok"
    }

    /** Keeps a font or image file name inside its folder (no path separators or parent references). */
    internal fun safeName(fileName: String): String = fileName.substringAfterLast('/').replace("..", "_")
}
