package jr.brian.commandpannel.system.update

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Display
import jr.brian.commandpannel.plugin.PanelPluginTrust
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.Http
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Which ThorKBM builds (the Thor keyboard and mouse app) are installed, by version, or null when absent. Install
 * follows the release only; a debug build next to it doesn't count as installed.
 */
data class ThorKbmStatus(val releaseVersion: String? = null, val debugVersion: String? = null) {
    val releaseInstalled: Boolean get() = releaseVersion != null

    /** What Open launches: the release, or the debug build when it's the only one. Null when neither is installed. */
    val openPackage: String?
        get() = when {
            releaseVersion != null -> ThorKbmInstaller.PACKAGE
            debugVersion != null -> ThorKbmInstaller.DEBUG_PACKAGE
            else -> null
        }
}

/** ThorKBM's latest release: its version (the tag without the `v`) and its `ThorKBM-v<version>.apk` asset. */
data class ThorKbmRelease(val version: String, val url: String, val size: Long)

/**
 * Installs ThorKBM from its own repo's latest GitHub release, once. After that ThorKBM updates itself, so this never
 * replaces an installed copy. The APK must be ThorKBM signed with its release key (the same pin as the Panel plugin
 * allowlist), checked before either install path. Blocking calls; run them off the main thread.
 */
object ThorKbmInstaller {
    const val PACKAGE = PanelPluginTrust.THORKBM_PACKAGE
    const val DEBUG_PACKAGE = "$PACKAGE.debug"

    /** ThorKBM's GitHub page: the card's Open on GitHub button, and where [latest] looks for releases. */
    const val REPO_URL = "https://github.com/BrianJr03/Thor-Ergo-Android"
    private val LATEST = latestReleaseApi(REPO_URL)
    private const val MAX_APK_BYTES = 40L * 1024 * 1024
    private const val STAGED = "/data/local/tmp/thorkbm.apk"

    /** The only key a ThorKBM APK from GitHub may be signed with. */
    private val SIGNERS = setOf(PanelPluginTrust.THORKBM_CERT_SHA256)

    /** Digits and dots with an optional suffix (`2.2.1`, `2.3.0-beta1`); keeps the tag safe to put in a file name. */
    private val VERSION = Regex("""\d+(\.\d+)*(-[A-Za-z0-9.]+)?""")

    fun assetName(version: String) = "ThorKBM-v$version.apk"

    /** The GitHub API's latest-release URL for the repo at [repoUrl] (`https://github.com/<owner>/<repo>`). */
    internal fun latestReleaseApi(repoUrl: String) =
        "https://api.github.com/repos/${repoUrl.removePrefix("https://github.com/").trimEnd('/')}/releases/latest"

    fun status(context: Context): ThorKbmStatus {
        val pm = context.packageManager
        fun version(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()?.let { it.versionName.orEmpty() }
        return ThorKbmStatus(version(PACKAGE), version(DEBUG_PACKAGE))
    }

    /**
     * The latest release (or, on a debug build, the [DebugOverrides.releaseTag] one), or null when it has no
     * `ThorKBM-v<version>.apk`. Counts against [GitHubRateGuard].
     */
    fun latest(context: Context): ThorKbmRelease? {
        val url = DebugOverrides.read(context).releaseTag?.let { "$TAGS/v$it" } ?: LATEST
        return parseRelease(JSONObject(Http.getText(url, 512 * 1024, "application/vnd.github+json")))
    }

    private const val TAGS = "https://api.github.com/repos/BrianJr03/Thor-Ergo-Android/releases/tags"

    /**
     * Debug builds only, for device checks: `files/thorkbm_debug.properties` in the app's own storage (written with
     * `run-as`). `release_tag=v2.2` installs that release instead of the latest; `no_root=true` skips the root shell,
     * so Install (and a Command Panel update, see [UpdateInstaller.installWithRoot]) goes through Android's
     * installer. Release builds never read it.
     */
    internal data class DebugOverrides(val releaseTag: String? = null, val noRoot: Boolean = false) {
        companion object {
            const val FILE = "thorkbm_debug.properties"

            fun read(context: Context): DebugOverrides {
                if (!jr.brian.commandpannel.BuildConfig.DEBUG) return DebugOverrides()
                val text = runCatching { File(context.filesDir, FILE).readText() }.getOrNull() ?: return DebugOverrides()
                return parse(text)
            }

            /** `key=value` lines; an unknown key or a tag that isn't a plain version is ignored. */
            fun parse(text: String): DebugOverrides {
                val map = text.lines().mapNotNull { line ->
                    line.substringBefore('#').split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
                }.toMap()
                return DebugOverrides(
                    releaseTag = map["release_tag"]?.removePrefix("v")?.takeIf { VERSION.matches(it) },
                    noRoot = map["no_root"] == "true",
                )
            }
        }
    }

    internal fun parseRelease(release: JSONObject): ThorKbmRelease? {
        val version = release.optString("tag_name").removePrefix("v").takeIf { VERSION.matches(it) } ?: return null
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName(version) }
            ?.let { a ->
                a.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    ?.let { ThorKbmRelease(version, it, a.optLong("size")) }
            }
    }

    /** Downloads [release] into the updates cache (shared with Android's installer) and [verify]s it. */
    fun download(context: Context, release: ThorKbmRelease): File {
        val dest = File(File(context.cacheDir, "updates"), "thorkbm.apk")
        Http.download(release.url, dest, MAX_APK_BYTES)
        if (release.size > 0 && dest.length() != release.size) {
            dest.delete()
            error("download incomplete")
        }
        runCatching { verify(context, dest, release.version) }.onFailure { dest.delete(); throw it }
        return dest
    }

    /** Checks [apk] is ThorKBM [version], signed only with ThorKBM's release key. */
    fun verify(context: Context, apk: File, version: String) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("not a valid APK")
        check(info.packageName, PanelPluginTrust.signerDigests(info), info.versionName.orEmpty(), version)
    }

    /** [verify]'s rules, without Android: the package, signer and version an APK reported. */
    internal fun check(packageName: String?, signers: List<String>, apkVersion: String, expected: String) {
        require(packageName == PACKAGE) { "the APK is for $packageName" }
        require(PanelPluginTrust.isTrusted(signers, SIGNERS)) { "the APK isn't signed with ThorKBM's key" }
        require(apkVersion.substringBefore('-') == expected.substringBefore('-')) { "the APK is version $apkVersion" }
    }

    /**
     * Installs [apk] (already [verify]d) through the root shell. The staged copy must hash the same as [apk], so what
     * root installs is the APK that was checked. Never replaces an installed ThorKBM. Returns the installer's output,
     * or null when the root shell isn't available.
     */
    fun installWithRoot(context: Context, privileged: PrivilegedAccess, apk: File): String? {
        if (DebugOverrides.read(context).noRoot) return null
        val sha = sha256(apk)
        val staged = privileged.shell(
            "cp '${apk.absolutePath}' $STAGED && chmod 644 $STAGED && sha256sum $STAGED", timeoutMs = 30_000,
        )?.trim() ?: return null
        if (staged.substringBefore(' ') != sha) {
            privileged.shell("rm -f $STAGED")
            return if (staged.isEmpty()) null else "staged copy doesn't match"
        }
        // A copy installed in the meantime is left alone: ThorKBM's own updater owns updates.
        return privileged.shell(
            "if pm path $PACKAGE >/dev/null 2>&1; then echo 'Failure [already installed]'; else pm install $STAGED 2>&1; fi; " +
                "rm -f $STAGED",
            timeoutMs = 60_000,
        )?.trim()
    }

    /**
     * The card's message after its status is re-read. When an install [handedOff] to Android's installer shows up,
     * it's [installedMessage], like the root path. Otherwise the old message stays while the status is unchanged or
     * an install is running, and is dropped once the status moves on.
     */
    internal fun messageAfterRefresh(
        old: ThorKbmStatus,
        new: ThorKbmStatus,
        oldMessage: String?,
        busy: Boolean,
        handedOff: Boolean,
        installedMessage: String,
    ): String? = when {
        handedOff && !old.releaseInstalled && new.releaseInstalled -> installedMessage
        old == new || busy -> oldMessage
        else -> null
    }

    /** Opens ThorKBM's main screen on the top display. False when it isn't installed or can't be started. */
    fun open(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        return runCatching { context.startActivity(intent, opts) }.isSuccess
    }

    /** Opens [REPO_URL] in the browser on the top display. False when no app can show it. */
    fun openRepoPage(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
        return runCatching { context.startActivity(intent, opts) }.isSuccess
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
