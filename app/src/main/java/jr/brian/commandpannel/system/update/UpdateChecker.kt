package jr.brian.commandpannel.system.update

import jr.brian.commandpannel.system.Http
import jr.brian.commandpannel.system.HttpStatusException
import org.json.JSONObject

/** The latest published release: its version (tag without a leading `v`), notes, page, and APK asset. */
data class ReleaseInfo(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long,
)

/** Outcome of a check: a newer release, nothing newer (or nothing published yet), or a failure to reach GitHub. */
sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data object UpToDate : UpdateCheck
    data class Failed(val reason: String) : UpdateCheck
}

/**
 * Checks GitHub for a newer Command Panel release (the same approach as the Home and ThorHUD apps). Only stable
 * releases are considered: GitHub's `releases/latest` excludes drafts and pre-releases. Blocking; call off the main
 * thread.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/BrianJr03/command-panel/releases/latest"
    private const val TAGS = "https://api.github.com/repos/BrianJr03/command-panel/releases/tags"

    fun check(currentVersion: String): UpdateCheck = runCatching {
        val json = Http.getText(LATEST, 512 * 1024, "application/vnd.github+json")
        val release = parse(JSONObject(json))
        if (isNewer(release.version, currentVersion)) UpdateCheck.Available(release) else UpdateCheck.UpToDate
    }.getOrElse { e ->
        // 404: nothing has been published yet.
        if (e is HttpStatusException && e.code == 404) UpdateCheck.UpToDate
        else UpdateCheck.Failed(e.message ?: e.javaClass.simpleName)
    }

    /** The release notes published for [version] (tag `v<version>` or `<version>`), or null. Blocking. */
    fun releaseNotes(version: String): String? = listOf("v$version", version).firstNotNullOfOrNull { tag ->
        runCatching {
            JSONObject(Http.getText("$TAGS/$tag", 512 * 1024, "application/vnd.github+json")).optString("body").ifBlank { null }
        }.getOrNull()
    }

    /** Reads a GitHub release; the APK is the `command-panel-*.apk` asset (never a `-debug` build). */
    internal fun parse(o: JSONObject): ReleaseInfo {
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).mapNotNull { assets!!.optJSONObject(it) }.firstOrNull {
            val name = it.optString("name")
            name.startsWith("command-panel-") && name.endsWith(".apk") && !name.endsWith("-debug.apk")
        }
        return ReleaseInfo(
            version = o.optString("tag_name").removePrefix("v").removePrefix("V"),
            notes = o.optString("body"),
            pageUrl = o.optString("html_url"),
            apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") },
            apkName = apk?.optString("name"),
            apkSize = apk?.optLong("size") ?: 0L,
        )
    }

    /**
     * True when [latest] is a higher version than [current]. Only the numeric part before any `-` counts
     * (`0.1.0-debug` is `0.1.0`), compared segment by segment with missing segments as 0.
     */
    internal fun isNewer(latest: String, current: String): Boolean {
        val a = numeric(latest)
        val b = numeric(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numeric(v: String): List<Int> =
        v.trim().removePrefix("v").substringBefore('-').split('.').map { it.takeWhile(Char::isDigit) }
            .takeWhile { it.isNotEmpty() }.map { it.toInt() }

    /** Whether bundled patch notes are for [version]: their first heading names it (`## Command Panel v0.1.0`). */
    internal fun notesMatch(notes: String, version: String): Boolean {
        val heading = notes.lineSequence().firstOrNull { it.trimStart().startsWith("#") } ?: return false
        val v = version.substringBefore('-')
        return Regex("""(?<![\d.])v?${Regex.escape(v)}(?![\d.])""").containsMatchIn(heading)
    }
}
