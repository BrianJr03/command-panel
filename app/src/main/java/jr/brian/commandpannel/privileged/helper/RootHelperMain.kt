package jr.brian.commandpannel.privileged.helper

import android.os.IBinder
import android.os.Looper
import android.util.Log
import jr.brian.commandpannel.privileged.HiddenApi

/**
 * Entry point of the root helper. Launched by the app as:
 *
 *     CLASSPATH=<apk> app_process /system/bin \
 *         jr.brian.commandpannel.privileged.helper.RootHelperMain <appPackage> [<appUid>]
 *
 * `<appUid>` is the only uid (besides root) the helper answers; without it the helper looks it up with `pm`.
 *
 * It runs as the uid `PServerBinder` gives it (root on this firmware), builds the [IRootHelper] stub, and publishes
 * it under a per-app name in `ServiceManager`, where the app picks it up with `getService`. It then waits on a
 * Looper; it exits on `shutdown()`, or once the app's client token has died and no new client attached within the
 * grace period (see [RootHelperService]).
 *
 * This file is plain `main(args)` with no Android context — only framework internals reached via reflection — so it
 * is safe to run outside an app process.
 */
object RootHelperMain {
    private const val TAG = "RootHelperMain"

    @JvmStatic
    fun main(args: Array<String>) {
        val appPackage = args.firstOrNull() ?: run {
            Log.e(TAG, "missing app package arg")
            return
        }
        Looper.prepareMainLooper()
        HiddenApi.exempt()

        val binder = RootHelperService(appPackage, launchUid = args.getOrNull(1)?.toIntOrNull())
        if (!publishBinder(appPackage, binder)) {
            Log.e(TAG, "failed to publish binder; exiting")
            return
        }
        Log.i(TAG, "helper up, uid=${android.os.Process.myUid()}, service=${HelperProtocol.serviceName(appPackage)}")
        Looper.loop()
    }

    /** Registers the helper binder in ServiceManager under the app-specific name. */
    private fun publishBinder(appPackage: String, binder: IBinder): Boolean {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val addService = sm.getMethod("addService", String::class.java, IBinder::class.java)
            addService.invoke(null, HelperProtocol.serviceName(appPackage), binder)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "publishBinder failed", t)
            false
        }
    }

    /** Runs a command via /system/bin/sh as the helper's uid. See [ShellRunner] for the timeout. */
    fun runShell(command: String, timeoutMs: Int): Triple<Int, String, String> =
        ShellRunner.run(listOf("/system/bin/sh", "-c", command), timeoutMs.toLong())
}
