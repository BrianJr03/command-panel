package jr.brian.commandpannel.system.update

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import jr.brian.commandpannel.plugin.PanelPluginTrust
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.RootShell
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
     * Checks [apk] is an installable update for this app: it parses, has this package name, is signed with this
     * build's own key, and (when given) is the expected version. Returns the APK's versionName.
     *
     * Android refuses an update signed with another key anyway; checking here means root never stages one, and the
     * user sees why instead of a bare `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
     */
    fun verify(context: Context, apk: File, expectedVersion: String?): String {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: error("not a valid APK")
        val own = runCatching { pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()
        val version = info.versionName.orEmpty()
        check(
            packageName = info.packageName,
            ownPackage = context.packageName,
            signers = PanelPluginTrust.signerDigests(info),
            ownSigners = PanelPluginTrust.signerDigests(own),
            version = version,
            expectedVersion = expectedVersion,
        )
        return version
    }

    /** [verify]'s rules, without Android. */
    internal fun check(
        packageName: String?,
        ownPackage: String,
        signers: List<String>,
        ownSigners: List<String>,
        version: String,
        expectedVersion: String?,
    ) {
        require(packageName == ownPackage) { "the APK is for $packageName" }
        require(ownSigners.isNotEmpty()) { "couldn't read this build's signing key" }
        require(PanelPluginTrust.isTrusted(signers, ownSigners.map(PanelPluginTrust::normalizeDigest).toSet())) {
            "the APK isn't signed with Command Panel's key"
        }
        if (expectedVersion != null) {
            require(version.substringBefore('-') == expectedVersion.substringBefore('-')) { "the APK is version $version" }
        }
    }

    /**
     * Installs [apk] (already [verify]d) through the root shell and relaunches the companion. The staged copy must hash
     * the same as [apk], so what root installs is the APK that was checked; the detached install checks it once more
     * right before `pm install`. The install replaces this app, which kills this process, so the work runs as a
     * detached root command. Returns false when the root shell isn't available or the staged copy doesn't match.
     */
    suspend fun installWithRoot(context: Context, privileged: PrivilegedAccess, apk: File): Boolean {
        // Debug device checks: the same no-root override as ThorKBM's, so updates go through Android's installer.
        if (ThorKbmInstaller.DebugOverrides.read(context).noRoot) return false
        val sha = ApkDigest.sha256(apk)
        val staged = privileged.shell(
            "rm -f $INSTALL_LOG; cp ${RootShell.q(apk.absolutePath)} $STAGED && chmod 644 $STAGED && sha256sum $STAGED",
            timeoutMs = 30_000,
        )?.trim()
        if (staged?.substringBefore(' ') != sha) {
            privileged.run("rm", "-f", STAGED)
            return false
        }
        val script = detachedInstallScript(sha, "${context.packageName}/jr.brian.commandpannel.MainActivity")
        privileged.shell("nohup sh -c ${RootShell.q(script)} >/dev/null 2>&1 &") ?: return false
        return true
    }

    /**
     * The script the detached root shell runs: install the staged APK only if it still hashes to [sha], then remove it
     * and open [activity]. Its output goes to [INSTALL_LOG]. Pure, so it's unit tested.
     */
    internal fun detachedInstallScript(sha: String, activity: String): String =
        "if [ \"\$(sha256sum $STAGED | cut -d ' ' -f 1)\" = ${RootShell.q(sha)} ]; " +
            "then pm install -r -t $STAGED; else echo 'Failure [staged copy changed]'; false; fi > $INSTALL_LOG 2>&1 && " +
            "rm -f $STAGED && am start -n ${RootShell.q(activity)}"

    /** The detached install's output so far, or null if there's none (yet). */
    suspend fun installLog(privileged: PrivilegedAccess): String? = privileged.run("cat", INSTALL_LOG)?.trim()?.ifEmpty { null }

    /** True when Android's installer can be used now; otherwise [installerPermissionIntent] lets the user allow it. */
    fun canUseSystemInstaller(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installerPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Hands [apk] to Android's package installer (the user confirms the install). Aimed at the system installer
     * itself, so other apps that open APKs (file managers, APK stores) never get a say through the "Open with"
     * chooser. Falls back to the plain intent only when no system installer resolves.
     */
    fun systemInstallerIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val handlers = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of((PackageManager.MATCH_SYSTEM_ONLY or PackageManager.MATCH_DEFAULT_ONLY).toLong()),
        ).mapNotNull { it.activityInfo }.map { a ->
            InstallerCandidate(a.packageName, a.name, system = a.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        }
        pickInstaller(handlers)?.let { intent.component = ComponentName(it.packageName, it.activityName) }
        return intent
    }

    /** An activity that opens APKs: its package, class, and whether it's part of the system image. */
    internal data class InstallerCandidate(val packageName: String, val activityName: String, val system: Boolean)

    /**
     * The installer to hand APKs to: a system app only, preferring Android's package installer (AOSP's
     * `com.android.packageinstaller` or Google's `com.google.android.packageinstaller`). Null when no system app
     * handles APKs.
     */
    internal fun pickInstaller(candidates: List<InstallerCandidate>): InstallerCandidate? {
        val system = candidates.filter { it.system }
        return system.firstOrNull { it.packageName.endsWith(".packageinstaller") } ?: system.firstOrNull()
    }
}
