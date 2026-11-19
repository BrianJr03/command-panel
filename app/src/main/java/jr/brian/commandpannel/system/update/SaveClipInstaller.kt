package jr.brian.commandpannel.system.update

import android.content.Context
import android.content.pm.PackageManager
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.Http
import org.json.JSONObject
import java.io.File

/** Whether TCP Save Clip is installed, and whether Android let it trigger this build (same signing key, installed after). */
sealed interface SaveClipStatus {
    data object Missing : SaveClipStatus
    data class Ready(val version: String) : SaveClipStatus
    /** Installed, but without this build's trigger permission: a different key, or installed before Command Panel. */
    data class NoPermission(val version: String) : SaveClipStatus
}

/** The `tcp-save-clip.apk` asset of a release. */
data class SaveClipAsset(val url: String, val size: Long)

/**
 * Installs TCP Save Clip (the Back Button Hero trigger) from the GitHub release matching this Command Panel version,
 * the way [UpdateInstaller] installs updates: through the root shell, or else Android's installer. Blocking calls; run
 * them off the main thread.
 */
object SaveClipInstaller {
    const val PACKAGE = "jr.brian.tcp.saveclip"
    const val ASSET_NAME = "tcp-save-clip.apk"
    private const val TAGS = "https://api.github.com/repos/BrianJr03/command-panel/releases/tags"
    private const val MAX_APK_BYTES = 10L * 1024 * 1024
    private const val STAGED = "/data/local/tmp/tcp-save-clip.apk"

    fun status(context: Context): SaveClipStatus {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(PACKAGE, 0) }.getOrNull() ?: return SaveClipStatus.Missing
        val version = info.versionName.orEmpty()
        val granted = pm.checkPermission("${context.packageName}.permission.TRIGGER", PACKAGE) == PackageManager.PERMISSION_GRANTED
        return if (granted) SaveClipStatus.Ready(version) else SaveClipStatus.NoPermission(version)
    }

    /** The trigger APK attached to release `v<version>`, or null if that release has none. */
    fun asset(version: String): SaveClipAsset? =
        parseAsset(JSONObject(Http.getText("$TAGS/v$version", 512 * 1024, "application/vnd.github+json")))

    internal fun parseAsset(release: JSONObject): SaveClipAsset? {
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name") == ASSET_NAME }
            ?.let { a -> a.optString("browser_download_url").takeIf { it.startsWith("https://") }?.let { SaveClipAsset(it, a.optLong("size")) } }
    }

    /** Downloads [asset] into the updates cache (shared with Android's installer) and checks it's TCP Save Clip. */
    fun download(context: Context, asset: SaveClipAsset): File {
        val dest = File(File(context.cacheDir, "updates"), ASSET_NAME)
        Http.download(asset.url, dest, MAX_APK_BYTES)
        if (asset.size > 0 && dest.length() != asset.size) {
            dest.delete()
            error("download incomplete")
        }
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(dest.path, 0) ?: error("not a valid APK")
        require(info.packageName == PACKAGE) { "the APK is for ${info.packageName}" }
        return dest
    }

    /**
     * Installs [apk] through the root shell. [replace] first removes an installed copy, so one signed with another key,
     * or installed before Command Panel, is installed fresh and gets the permission. Returns the installer's output, or
     * null when the root shell isn't available.
     */
    fun installWithRoot(privileged: PrivilegedAccess, apk: File, replace: Boolean): String? {
        val staged = privileged.shell("cp '${apk.absolutePath}' $STAGED && chmod 644 $STAGED && echo ok", timeoutMs = 30_000)?.trim()
        if (staged != "ok") return null
        val uninstall = if (replace) "pm uninstall $PACKAGE >/dev/null 2>&1; " else ""
        return privileged.shell("${uninstall}pm install -r -t $STAGED 2>&1; rm -f $STAGED", timeoutMs = 60_000)?.trim()
    }
}
