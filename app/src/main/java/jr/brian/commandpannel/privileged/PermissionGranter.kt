package jr.brian.commandpannel.privileged

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Grants Command Panel its own privileged permissions and appops once at startup, through [VendorRootShell].
 *
 * Each item is checked first and only granted when actually missing, so a normal launch issues no shell commands.
 * The set mirrors what the stock assistant holds by virtue of its system uid; Command Panel, as a user app, asks
 * for them explicitly.
 */
object PermissionGranter {

    private val RUNTIME_PERMISSIONS = listOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.PACKAGE_USAGE_STATS",
    )
    private val APPOPS = listOf("SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "GET_USAGE_STATS")

    data class Result(val granted: List<String>, val failed: List<String>, val alreadyHeld: Int)

    fun ensureGranted(context: Context): Result {
        val pkg = context.packageName
        val granted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var held = 0

        for (perm in RUNTIME_PERMISSIONS) {
            if (context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
                held++; continue
            }
            if (VendorRootShell.exec(RootShell.command("pm", "grant", pkg, perm)) != null &&
                context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
            ) granted.add(perm) else failed.add(perm)
        }

        if (canWriteSystemSettings(context)) {
            held++
        } else {
            VendorRootShell.exec(RootShell.command("appops", "set", pkg, "WRITE_SETTINGS", "allow"))
            if (canWriteSystemSettings(context)) granted.add("appop:WRITE_SETTINGS") else failed.add("appop:WRITE_SETTINGS")
        }
        for (op in APPOPS.filter { it != "WRITE_SETTINGS" }) {
            VendorRootShell.exec(RootShell.command("appops", "set", pkg, op, "allow"))
            granted.add("appop:$op")
        }

        // Keep the app process off the Doze idle list so the overlay service is not frozen. The whitelist only lets the
        // service run; it doesn't keep the device awake. That is safe because the service goes quiet with the screen
        // off: the Replay Buffer pauses for sleep (#91), and the panel's status, telemetry and battery pollers stop
        // until screen on (#92). Anything new that runs while the screen is off must do the same.
        VendorRootShell.exec(RootShell.command("dumpsys", "deviceidle", "whitelist", "+$pkg"))

        return Result(granted, failed, held)
    }

    fun canWriteSystemSettings(context: Context): Boolean =
        Settings.System.canWrite(context)
}
