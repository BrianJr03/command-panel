package jr.brian.commandpannel.system

import android.content.Context
import android.content.SharedPreferences
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.LookPack
import jr.brian.commandpannel.ui.theme.LookPackJson
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * A theme repo the user follows: a GitHub repo made from the theme template (packs in its `themes/` folder) or a
 * direct link to one `.tcplooks.json` file. [files] maps each synced pack path to its content hash and the look
 * entry ids it held, so a sync only downloads what changed and can remove looks the repo dropped.
 */
data class ThemeSource(
    val id: String,
    val url: String,
    val name: String,
    val owner: String? = null,
    val repo: String? = null,
    val branch: String? = null,
    val lastSync: Long = 0L,
    val files: Map<String, SyncedFile> = emptyMap(),
) {
    val isGitHub: Boolean get() = owner != null && repo != null
}

data class SyncedFile(val sha: String, val lookIds: List<String>)

/** Outcome of one sync, for the result message and the font-download prompt. */
data class SyncResult(
    val updatedFiles: Int,
    val lookCount: Int,
    val removedLooks: Int,
    val fontNeeds: FontNeeds,
    val packs: List<LookPack>,
)

object ThemeSources {
    const val KEY = "theme_sources"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    // ---- links ----

    private val GITHUB = Regex("^https://(?:www\\.)?github\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?(?:/tree/([^/?#]+))?/?(?:[?#].*)?$")

    /**
     * Parses a pasted link: `https://github.com/<owner>/<repo>` (optionally `/tree/<branch>`) or a direct https link to
     * a `.tcplooks.json` file. Returns `null` for anything else, including plain http.
     */
    fun parse(link: String): ThemeSource? {
        val url = link.trim()
        if (!url.startsWith("https://")) return null
        GITHUB.matchEntire(url)?.let { m ->
            val (owner, repo, branch) = m.destructured
            return ThemeSource(
                id = "gh:$owner/$repo".lowercase(), url = "https://github.com/$owner/$repo", name = repo,
                owner = owner, repo = repo, branch = branch.ifBlank { null },
            )
        }
        val path = url.substringBefore('?').substringBefore('#')
        if (path.endsWith(LookPackJson.EXTENSION)) {
            return ThemeSource(id = "url:" + sha1(url).take(12), url = url, name = path.substringAfterLast('/').removeSuffix(LookPackJson.EXTENSION))
        }
        return null
    }

    // ---- storage ----

    fun all(prefs: SharedPreferences): List<ThemeSource> {
        val arr = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::decode) }
    }

    fun save(prefs: SharedPreferences, source: ThemeSource) {
        val next = all(prefs).filterNot { it.id == source.id } + source
        prefs.edit().putString(KEY, JSONArray().apply { next.forEach { put(encode(it)) } }.toString()).apply()
    }

    /** Forgets [id] and deletes the looks it installed. */
    fun remove(prefs: SharedPreferences, id: String) {
        CustomLookStore.all(prefs).filter { it.source == id }.forEach { CustomLookStore.delete(prefs, it.id) }
        val next = all(prefs).filterNot { it.id == id }
        prefs.edit().putString(KEY, JSONArray().apply { next.forEach { put(encode(it)) } }.toString()).apply()
    }

    fun isDue(source: ThemeSource, now: Long = System.currentTimeMillis()): Boolean = now - source.lastSync > DAY_MS

    // ---- sync ----

    /** Which pack paths to download (new or changed hash) and which were removed upstream. */
    internal fun diff(local: Map<String, SyncedFile>, remote: Map<String, String>): Pair<List<String>, List<String>> =
        remote.filter { (path, sha) -> local[path]?.sha != sha }.keys.sorted() to (local.keys - remote.keys).sorted()

    /**
     * Fetches [source], installs new or changed packs as looks (ids `<source>:<entry>`, so they update in place),
     * removes looks the source dropped, and copies each pack into `TCP Themes/themes/<repo>/`. Blocking; call off
     * the main thread.
     */
    fun sync(context: Context, privileged: PrivilegedAccess, prefs: SharedPreferences, source: ThemeSource): Result<SyncResult> = runCatching {
        val remote: Map<String, String>
        val downloadUrls: Map<String, String>
        val prefetched = mutableMapOf<String, String>()
        if (source.isGitHub) {
            // The listing is cached for up to 60 s (s-maxage), so a fresh query string keeps "Update" right after a push
            // from reporting stale versions.
            val query = listOfNotNull(source.branch?.let { "ref=$it" }, "t=${System.currentTimeMillis()}").joinToString("&")
            val listing = JSONArray(
                Http.getText("https://api.github.com/repos/${source.owner}/${source.repo}/contents/themes?$query", 256 * 1024, "application/vnd.github+json"),
            )
            val files = (0 until listing.length()).mapNotNull { listing.optJSONObject(it) }
                .filter { it.optString("type") == "file" && it.optString("name").endsWith(LookPackJson.EXTENSION) }
            remote = files.associate { it.optString("path") to it.optString("sha") }
            // Fetch each pack by its blob sha rather than download_url: raw.githubusercontent caches files for minutes,
            // which could install stale content under the new sha (and then never re-download it). Blobs are immutable.
            downloadUrls = files.associate {
                it.optString("path") to "https://api.github.com/repos/${source.owner}/${source.repo}/git/blobs/${it.optString("sha")}"
            }
        } else {
            // A single file has no listing; its content hash decides whether it changed.
            val text = Http.getText(source.url, LookPackJson.MAX_BYTES)
            remote = mapOf(source.url to sha1(text))
            prefetched[source.url] = text
            downloadUrls = mapOf(source.url to source.url)
        }
        val (changed, removed) = diff(source.files, remote)
        val subdir = (source.repo ?: source.name).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val newFiles = source.files.toMutableMap()
        val packs = mutableListOf<LookPack>()
        var removedLooks = 0
        val fontLooks = mutableListOf<CustomLook>()
        var fonts = emptyMap<String, jr.brian.commandpannel.ui.theme.FontSource>()
        for (path in changed) {
            val text = prefetched[path] ?: Http.getText(downloadUrls.getValue(path), LookPackJson.MAX_BYTES, "application/vnd.github.raw").also {
                // Only record a version whose content we actually have, so a bad download is retried next sync.
                check(gitBlobSha(it.toByteArray()) == remote.getValue(path)) { "content didn't match $path" }
            }
            val fileName = path.substringAfterLast('/')
            val pack = LookPackJson.decode(text, fileName.substringBefore('.')) ?: continue
            packs += pack
            ThemeFolder.writePack(context, privileged, pack, fileName, subdir)
            fontLooks += pack.looks
            fonts = fonts + pack.fonts
            val ids = pack.looks.map { it.id }
            // Entries dropped from a changed pack go away too.
            source.files[path]?.lookIds.orEmpty().filterNot { it in ids }.forEach {
                CustomLookStore.delete(prefs, "${source.id}:$it"); removedLooks++
            }
            newFiles[path] = SyncedFile(remote.getValue(path), ids)
        }
        for (path in removed) {
            source.files[path]?.lookIds.orEmpty().forEach { CustomLookStore.delete(prefs, "${source.id}:$it"); removedLooks++ }
            ThemeFolder.deletePath(privileged, "${ThemeFolder.THEMES}/$subdir/${path.substringAfterLast('/')}")
            newFiles.remove(path)
        }
        // Fonts first, so the installed looks link to them.
        val needs = PackInstaller.prepareFonts(context, privileged, LookPack(source.name, fontLooks, fonts = fonts))
        packs.forEach { PackInstaller.install(context, prefs, it.looks, source.id) }
        save(prefs, source.copy(lastSync = System.currentTimeMillis(), files = newFiles))
        SyncResult(changed.size, newFiles.values.sumOf { it.lookIds.size }, removedLooks, needs, packs)
    }

    // ---- json ----

    private fun encode(s: ThemeSource) = JSONObject()
        .put("id", s.id).put("url", s.url).put("name", s.name)
        .put("owner", s.owner).put("repo", s.repo).put("branch", s.branch).put("lastSync", s.lastSync)
        .put("files", JSONObject().apply {
            s.files.forEach { (path, f) -> put(path, JSONObject().put("sha", f.sha).put("looks", JSONArray(f.lookIds))) }
        })

    private fun decode(o: JSONObject): ThemeSource? {
        val id = o.optString("id").ifBlank { return null }
        val files = o.optJSONObject("files")?.let { f ->
            f.keys().asSequence().associateWith { path ->
                val e = f.optJSONObject(path)
                val looks = e?.optJSONArray("looks")
                SyncedFile(e?.optString("sha").orEmpty(), (0 until (looks?.length() ?: 0)).map { looks!!.optString(it) })
            }
        }.orEmpty()
        return ThemeSource(
            id = id, url = o.optString("url"), name = o.optString("name"),
            owner = o.optString("owner").ifBlank { null }, repo = o.optString("repo").ifBlank { null },
            branch = o.optString("branch").ifBlank { null }, lastSync = o.optLong("lastSync"), files = files,
        )
    }

    /** Git's blob id for [bytes] (`sha1("blob <size>\u0000" + bytes)`), which the GitHub contents API reports as `sha`. */
    internal fun gitBlobSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray())
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}
