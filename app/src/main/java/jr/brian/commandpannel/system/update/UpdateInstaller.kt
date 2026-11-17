package jr.brian.commandpannel.system.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.Http
import java.io.File

/**
 * Downloads and installs a Command Panel update. The preferred path installs silently through the root shell and
 * relaunches the companion; without root (or if that install fails) it hands the APK to Android's installer.
 */
object UpdateInstaller {
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    /** Root-readable staging copy and the install log, outside the app's private storage. */
    private const val STAGED = "/data/local/tmp/tcp-update.apk"
    const val INSTALL_LOG = "/data/local/tmp/tcp-update.log"

    private fun dir(context: Context) = File(context.cacheDir, "updates")

    fun apkFile(context: Context, version: String): File = File(dir(context), "command-panel-$version.apk")

    /** A locally placed APK used to exercise the install path on debug builds (see the Updates card). */
    fun testApk(context: Context): File = File(dir(context), "test-update.apk")

    /** Deletes downloaded APKs left over from earlier updates (keeps the debug test APK). */
    fun cleanup(context: Context) {
        dir(context).listFiles()?.filter { it.name != testApk(context).name }?.forEach { it.delete() }
    }

    /** Streams the release APK to the cache, reporting progress as 0..1 (or -1 when the size is unknown). Blocking. */
    fun download(context: Context, release: ReleaseInfo, onProgress: (Float) -> Unit): File {
        val url = requireNotNull(release.apkUrl) { "no APK in this release" }
        val dest = apkFile(context, release.version)
        Http.download(url, dest, MAX_APK_BYTES) { read, total ->
            val size = if (total > 0) total else release.apkSize
            onProgress(if (size > 0) (read.toFloat() / size).coerceIn(0f, 1f) else -1f)
        }
        if (release.apkSize > 0 && dest.length() != release.apkSize) {
            dest.delete()
            error("download incomplete")
        }
        return dest
    }

    /**
     * Checks [apk] is an installable update for this app: it parses, has this package name, and (when given) the
     * expected version. Returns the APK's versionName.
     */
    fun verify(context: Context, apk: File, expectedVersion: String?): String {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: error("not a valid APK")
        require(info.packageName == context.packageName) { "the APK is for ${info.packageName}" }
        val version = info.versionName.orEmpty()
        if (expectedVersion != null) {
            require(version.substringBefore('-') == expectedVersion.substringBefore('-')) { "the APK is version $version" }
        }
        return version
    }

    /**
     * Installs [apk] through the root shell and relaunches the companion. The install replaces this app, which kills
     * this process, so the work runs as a detached root command. Returns false when the root shell isn't available.
     */
    fun installWithRoot(context: Context, privileged: PrivilegedAccess, apk: File): Boolean {
        val pkg = context.packageName
        val activity = "$pkg/jr.brian.commandpannel.MainActivity"
        val staged = privileged.shell(
            "rm -f $INSTALL_LOG; cp '${apk.absolutePath}' $STAGED && chmod 644 $STAGED && echo ok", timeoutMs = 30_000,
        )?.trim()
        if (staged != "ok") return false
        privileged.shell(
            "nohup sh -c 'pm install -r -t $STAGED > $INSTALL_LOG 2>&1 && rm -f $STAGED && am start -n $activity' " +
                ">/dev/null 2>&1 &",
        ) ?: return false
        return true
    }

    /** The detached install's output so far, or null if there's none (yet). */
    fun installLog(privileged: PrivilegedAccess): String? = privileged.shell("cat $INSTALL_LOG 2>/dev/null")?.trim()?.ifEmpty { null }

    /** True when Android's installer can be used now; otherwise [installerPermissionIntent] lets the user allow it. */
    fun canUseSystemInstaller(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installerPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands [apk] to Android's package installer (the user confirms the install). */
    fun systemInstallerIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
