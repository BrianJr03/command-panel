package jr.brian.commandpannel.system.update

import android.content.Context
import android.content.pm.PackageManager
import jr.brian.commandpannel.plugin.PanelPluginTrust
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootShell
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

/** The `tcp-sc-<version>.apk` asset of a release. */
data class SaveClipAsset(val url: String, val size: Long)

/**
 * Installs TCP Save Clip (the Replay Buffer shortcut) from the GitHub release matching this Command Panel version,
 * the way [UpdateInstaller] installs updates: through the root shell, or else Android's installer. Blocking calls; run
 * them off the main thread.
 */
object SaveClipInstaller {
    const val PACKAGE = "jr.brian.tcp.saveclip"
    /** The trigger's asset in release `v<version>`, named for that release like `command-panel-<version>.apk`. */
    fun assetName(version: String) = "tcp-sc-$version.apk"
    private const val TAGS = "https://api.github.com/repos/BrianJr03/command-panel/releases/tags"
    private const val MAX_APK_BYTES = 10L * 1024 * 1024
    private const val STAGED = "/data/local/tmp/tcp-sc.apk"

    fun status(context: Context): SaveClipStatus {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(PACKAGE, 0) }.getOrNull() ?: return SaveClipStatus.Missing
        val version = info.versionName.orEmpty()
        val granted = pm.checkPermission("${context.packageName}.permission.TRIGGER", PACKAGE) == PackageManager.PERMISSION_GRANTED
        return if (granted) SaveClipStatus.Ready(version) else SaveClipStatus.NoPermission(version)
    }

    /** The trigger APK attached to release `v<version>`, or null if that release has none. */
    fun asset(version: String): SaveClipAsset? =
        parseAsset(JSONObject(Http.getText("$TAGS/v$version", 512 * 1024, "application/vnd.github+json")), version)

    internal fun parseAsset(release: JSONObject, version: String): SaveClipAsset? {
        val assets = release.optJSONArray("assets") ?: return null
        return (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name") == assetName(version) }
            ?.let { a -> a.optString("browser_download_url").takeIf { it.startsWith("https://") }?.let { SaveClipAsset(it, a.optLong("size")) } }
    }

    /**
     * Downloads [asset] into the updates cache (shared with Android's installer) and [verify]s it. A file that fails the
     * check is deleted.
     */
    fun download(context: Context, asset: SaveClipAsset): File {
        val dest = File(File(context.cacheDir, "updates"), "tcp-sc.apk")
        Http.download(asset.url, dest, MAX_APK_BYTES)
        if (asset.size > 0 && dest.length() != asset.size) {
            dest.delete()
            error("download incomplete")
        }
        runCatching { verify(context, dest) }.onFailure { dest.delete(); throw it }
        return dest
    }

    /**
     * Checks [apk] is TCP Save Clip signed with this build's own key. The trigger permission is signature-level, so
     * only a copy signed like Command Panel can work anyway, and this pins what root may install (and may replace an
     * installed copy with) to the developer's key, the way ThorKBM's installer pins ThorKBM's.
     */
    fun verify(context: Context, apk: File) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("not a valid APK")
        val own = runCatching { pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
        check(info.packageName, PanelPluginTrust.signerDigests(info), PanelPluginTrust.signerDigests(own))
    }

    /** [verify]'s rules, without Android: the package and signers an APK reported, and this build's own signers. */
    internal fun check(packageName: String?, signers: List<String>, ownSigners: List<String>) {
        require(packageName == PACKAGE) { "the APK is for $packageName" }
        require(ownSigners.isNotEmpty()) { "couldn't read this build's signing key" }
        require(PanelPluginTrust.isTrusted(signers, ownSigners.map(PanelPluginTrust::normalizeDigest).toSet())) { "the APK isn't signed with Command Panel's key" }
    }

    /**
     * Installs [apk] (already [verify]d) through the root shell. The staged copy must hash the same as [apk], so what
     * root installs is the APK that was checked. [replace] first removes an installed copy, so one installed before
     * Command Panel (or signed with another key) is installed fresh and gets the permission. Returns the installer's
     * output, or null when the root shell isn't available.
     */
    suspend fun installWithRoot(privileged: PrivilegedAccess, apk: File, replace: Boolean): String? {
        val sha = ApkDigest.sha256(apk)
        val staged = privileged.shell(
            "cp ${RootShell.q(apk.absolutePath)} $STAGED && chmod 644 $STAGED && sha256sum $STAGED", timeoutMs = 30_000,
        )?.trim() ?: return null
        if (staged.substringBefore(' ') != sha) {
            privileged.run("rm", "-f", STAGED)
            return if (staged.isEmpty()) null else "staged copy doesn't match"
        }
        // One script, so the install can't be lost after the uninstall, and both get the long timeout.
        val install = "pm install -r -t $STAGED 2>&1; rm -f $STAGED"
        val script = if (replace) "pm uninstall $PACKAGE >/dev/null 2>&1; $install" else install
        return privileged.shell(script, timeoutMs = 60_000)?.trim()
    }
}
