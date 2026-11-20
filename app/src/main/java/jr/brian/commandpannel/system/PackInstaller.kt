package jr.brian.commandpannel.system

import android.content.Context
import android.content.SharedPreferences
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.FontRef
import jr.brian.commandpannel.ui.theme.FontSource
import jr.brian.commandpannel.ui.theme.LookPack
import jr.brian.commandpannel.ui.theme.LookPackJson
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Fonts a pack needs: [missing] has no local copy; [downloadable] is the subset the pack gives an https URL for. */
data class FontNeeds(val missing: List<String>, val downloadable: Map<String, FontSource>)

/**
 * Installs theme packs as saved looks. Fonts are resolved by file name: an installed font, then
 * `TCP Themes/fonts/<file>`, then (after the user agrees) the pack's download URL, else the default font.
 */
object PackInstaller {

    /**
     * Imports any fonts [looks] need from `TCP Themes/fonts` and reports the rest. Call before [install] and offer
     * [FontNeeds.downloadable] to the user.
     */
    fun prepareFonts(context: Context, privileged: PrivilegedAccess, pack: LookPack, looks: List<CustomLook> = pack.looks): FontNeeds {
        val missing = LookPackJson.fontFiles(looks).filter { file ->
            UserFontStore.findByFileName(context, file) == null &&
                !(ThemeFolder.hasFont(privileged, file) && ThemeFolder.importFont(context, privileged, file).isSuccess)
        }
        return FontNeeds(missing, pack.fonts.filterKeys { it in missing })
    }

    /**
     * Copies the background images [looks] need from `TCP Themes/images` into the app, and returns the file names still
     * missing (those looks show their backup backdrop). Theme repos don't carry images yet.
     */
    fun prepareImages(context: Context, privileged: PrivilegedAccess, looks: List<CustomLook>): List<String> =
        LookPackJson.imageFiles(looks).filter { file ->
            !BackgroundImageStore.exists(context, jr.brian.commandpannel.ui.theme.BackgroundImage(file)) &&
                !(ThemeFolder.hasImage(privileged, file) && ThemeFolder.importImage(context, privileged, file).isSuccess)
        }

    /** Downloads [file] from [source] (https only), installs it, and drops a copy in `TCP Themes/fonts`. */
    fun downloadFont(context: Context, privileged: PrivilegedAccess, file: String, source: FontSource): Result<UserFont> = runCatching {
        val bytes = Http.getBytes(source.url, maxBytes = 5 * 1024 * 1024)
        UserFontStore.register(context, ByteArrayInputStream(bytes), file, source.url, source.license).getOrThrow()
    }.onSuccess { ThemeFolder.exportFont(context, privileged, it) }

    /**
     * Saves [looks] (default: the whole pack). With a [sourceId] (a synced repo) ids are `<sourceId>:<entry id>` so a
     * re-sync replaces looks in place; local packs get fresh ids, since adding one is a one-time copy. Returns the
     * saved looks; fonts that still aren't installed fall back to the default font.
     */
    fun install(
        context: Context,
        prefs: SharedPreferences,
        looks: List<CustomLook>,
        sourceId: String? = null,
    ): List<CustomLook> = looks.map { entry ->
        val look = entry.copy(
            id = if (sourceId != null) "$sourceId:${entry.id}" else UUID.randomUUID().toString(),
            labelFont = relink(context, entry.labelFont),
            displayFont = relink(context, entry.displayFont),
            source = sourceId,
        )
        CustomLookStore.save(prefs, look)
        look
    }

    /** [look] with its fonts pointed at installed fonts of the same file name (for previews and Theme Studio). */
    fun relinked(context: Context, look: CustomLook): CustomLook =
        look.copy(labelFont = relink(context, look.labelFont), displayFont = relink(context, look.displayFont))

    /** Re-links every saved look's fonts by file name (after fonts were downloaded or added to the folder). */
    fun relinkSaved(context: Context, prefs: SharedPreferences) {
        CustomLookStore.all(prefs).forEach { look ->
            val next = relinked(context, look)
            if (next != look) CustomLookStore.save(prefs, next)
        }
    }

    /** Points a font reference at the installed font with the same file name (or leaves it unresolved). */
    private fun relink(context: Context, ref: FontRef?): FontRef? = when (ref) {
        is FontRef.User -> UserFontStore.findByFileName(context, ref.fileName)
            ?.let { ref.copy(id = it.id, displayName = it.displayName) } ?: ref.copy(id = "")
        else -> ref
    }

    /** Builds a pack from saved looks, copying their imported fonts and background images into `TCP Themes`. */
    fun exportPack(context: Context, privileged: PrivilegedAccess, name: String, looks: List<CustomLook>): String? {
        val installed = UserFontStore.all(context)
        val used = LookPackJson.fontFiles(looks).mapNotNull { file -> installed.firstOrNull { it.fileName.equals(file, ignoreCase = true) } }
        used.forEach { ThemeFolder.exportFont(context, privileged, it) }
        looks.mapNotNull { it.backgroundImage }.distinctBy { it.fileName }.forEach { ThemeFolder.exportImage(context, privileged, it) }
        // Fonts that came from a download URL keep it, so people importing the pack can fetch them too.
        val sources = used.mapNotNull { f -> f.url?.let { f.fileName to FontSource(it, f.license) } }.toMap()
        return ThemeFolder.writePack(context, privileged, LookPack(name = name, looks = looks, fonts = sources))
    }
}

/** Minimal https GET for theme repos and font downloads (no extra dependency). */
/** A non-2xx response; [code] lets callers treat e.g. 404 as "nothing there" rather than a failure. */
internal class HttpStatusException(val code: Int) : IllegalStateException("HTTP $code")

internal object Http {
    /**
     * GitHub API calls go through [GitHubRateGuard]: [githubReserve] requests are kept back (theme syncs leave a few for
     * the update check), and a call past the limit throws [GitHubRateLimitedException] instead of being sent.
     */
    fun getBytes(url: String, maxBytes: Int, accept: String? = null, githubReserve: Int = 0): ByteArray {
        require(url.startsWith("https://")) { "https only" }
        val github = URL(url).host == GitHubRateGuard.HOST
        if (github) GitHubRateGuard.acquire(githubReserve)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "TCP-Theme-Studio")
            conn.setRequestProperty("Cache-Control", "no-cache")
            accept?.let { conn.setRequestProperty("Accept", it) }
            val code = conn.responseCode
            if (github) {
                GitHubRateGuard.observe(conn)
                if ((code == 403 || code == 429) && GitHubRateGuard.available() == 0) {
                    throw GitHubRateLimitedException(GitHubRateGuard.retryAt())
                }
            }
            if (code !in 200..299) throw HttpStatusException(code)
            // Redirects (raw.githubusercontent, CDNs) must stay on https.
            require(conn.url.protocol == "https") { "https only" }
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (out.size() + n > maxBytes) error("too large")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    fun getText(url: String, maxBytes: Int, accept: String? = null, githubReserve: Int = 0): String =
        String(getBytes(url, maxBytes, accept, githubReserve), Charsets.UTF_8)

    /**
     * Streams [url] into [dest] (https only, redirects included), reporting `(bytesRead, totalOrMinus1)` as it goes.
     * Fails past [maxBytes]; a partial file is deleted on failure. Blocking; call off the main thread.
     */
    fun download(url: String, dest: java.io.File, maxBytes: Long, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        require(url.startsWith("https://")) { "https only" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "TCP-Updater")
            conn.setRequestProperty("Accept", "application/octet-stream")
            val code = conn.responseCode
            if (code !in 200..299) throw HttpStatusException(code)
            require(conn.url.protocol == "https") { "https only" }
            val total = conn.contentLengthLong
            if (total > maxBytes) error("too large")
            dest.parentFile?.mkdirs()
            runCatching {
                conn.inputStream.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            read += n
                            if (read > maxBytes) error("too large")
                            out.write(buf, 0, n)
                            onProgress(read, total)
                        }
                    }
                }
            }.onFailure { dest.delete(); throw it }
        } finally {
            conn.disconnect()
        }
    }
}
