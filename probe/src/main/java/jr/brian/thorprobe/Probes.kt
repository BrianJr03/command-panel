package jr.brian.thorprobe

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.io.File
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Capability probes for a NORMAL user-installed app on the AYN Thor.
 *
 * Every probe answers one question the stock-app teardown left as an inference:
 * can a non-system, non-platform-signed app reach the surface the stock
 * DualScreenAssistant uses from its android.uid.system process?
 *
 * Nothing here is destructive: writes are restored, windows are removed
 * immediately, and no vendor state is left changed.
 */
object Probes {

    enum class Verdict { PASS, FAIL, PARTIAL, INFO }

    data class Result(val name: String, val verdict: Verdict, val detail: String)

    private const val PSERVER = "PServerBinder"
    private const val SETTINGS_CONTROLLER = "SettingsController"

    fun runAll(ctx: Context, log: (Result) -> Unit): List<Result> {
        val results = mutableListOf<Result>()
        fun add(r: Result) { results.add(r); log(r) }

        add(environment(ctx))
        add(hiddenApiExemption())

        val pserverOk = pserverBinder(results, ::add)
        if (pserverOk) selfGrant(ctx, ::add)

        add(settingsRead(ctx))
        add(settingsWriteDirect(ctx))
        add(settingsWriteShell(ctx, pserverOk))

        // Root-shell fallbacks for the calls an untrusted_app uid can't make itself.
        add(rootFps(pserverOk))
        add(rootSetprop(pserverOk))

        add(fanProvider())
        add(mappingService(ctx))
        add(quickstep(ctx))

        windowProbes(ctx, ::add)

        add(displayBrightness(ctx))
        sysfsReads(::add)
        add(surfaceFlingerFps())

        return results
    }

    // ---- 0. Environment -----------------------------------------------------

    private fun environment(ctx: Context): Result {
        val uid = android.os.Process.myUid()
        val model = android.os.Build.MODEL
        val sdk = android.os.Build.VERSION.SDK_INT
        return Result(
            "Environment", Verdict.INFO,
            "uid=$uid pkg=${ctx.packageName} model=$model sdk=$sdk " +
                "target=${ctx.applicationInfo.targetSdkVersion}"
        )
    }

    // ---- 1. Hidden-API exemptions ------------------------------------------

    private fun hiddenApiExemption(): Result {
        return try {
            val classClass = Class::class.java
            val forName = classClass.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = classClass.getDeclaredMethod(
                "getDeclaredMethod", String::class.java, arrayOf<Class<*>>()::class.java
            )
            val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntime = getDeclaredMethod.invoke(
                vmRuntimeClass, "getRuntime", arrayOfNulls<Class<*>>(0)
            ) as Method
            val setExemptions = getDeclaredMethod.invoke(
                vmRuntimeClass, "setHiddenApiExemptions",
                arrayOf<Class<*>>(Array<String>::class.java)
            ) as Method
            val runtime = getRuntime.invoke(null)
            setExemptions.invoke(runtime, arrayOf("L"))
            Result("Hidden-API exemptions", Verdict.PASS, "setHiddenApiExemptions([\"L\"]) accepted")
        } catch (t: Throwable) {
            Result("Hidden-API exemptions", Verdict.FAIL, t.describe())
        }
    }

    // ---- 2. PServerBinder root shell ---------------------------------------

    private var pserver: IBinder? = null

    private fun getPserver(): IBinder? {
        pserver?.let { if (it.isBinderAlive) return it }
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            (getService.invoke(null, PSERVER) as? IBinder).also { pserver = it }
        } catch (t: Throwable) {
            null
        }
    }

    fun exec(cmd: String, wait: Boolean = true): String? {
        val binder = getPserver() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, if (wait) "1" else "0"))
            binder.transact(0, data, reply, 0)
            val bytes = reply.createByteArray()
            if (bytes != null) String(bytes, StandardCharsets.UTF_8).trimEnd() else ""
        } catch (t: Throwable) {
            null
        } finally {
            data.recycle(); reply.recycle()
        }
    }

    private fun pserverBinder(results: MutableList<Result>, add: (Result) -> Unit): Boolean {
        val binder = getPserver()
        if (binder == null) {
            add(Result("PServerBinder lookup", Verdict.FAIL, "ServiceManager.getService returned null"))
            return false
        }
        val id = exec("id")
        if (id == null) {
            add(Result("PServerBinder lookup", Verdict.FAIL, "transact threw / binder dead"))
            return false
        }
        val enforce = exec("getenforce")
        add(Result("PServerBinder shell", Verdict.PASS, "id => $id ; getenforce => $enforce"))
        return true
    }

    // ---- 3. Self-grant via shell -------------------------------------------

    private fun selfGrant(ctx: Context, add: (Result) -> Unit) {
        val pkg = ctx.packageName
        val cmds = listOf(
            "appops set $pkg SYSTEM_ALERT_WINDOW allow",
            "appops set $pkg WRITE_SETTINGS allow",
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
        )
        val out = cmds.joinToString("\n") { "$it => ${exec(it) ?: "<null>"}" }
        add(Result("Self-grant via shell", Verdict.INFO, out))
    }

    // ---- 4. Settings.System read -------------------------------------------

    private val VENDOR_KEYS = listOf(
        "dual_screen_display_mode", "screen_focus_lock", "fan_mode",
        "secondary_screen_volume_level", "performance_mode", "temp_abxy_layout_mode"
    )

    private fun settingsRead(ctx: Context): Result {
        val cr = ctx.contentResolver
        val pairs = VENDOR_KEYS.map { k ->
            val v = try { Settings.System.getString(cr, k) } catch (t: Throwable) { "ERR" }
            "$k=$v"
        }
        return Result("Settings.System read", Verdict.PASS, pairs.joinToString(" "))
    }

    // ---- 5. Settings.System write (direct API) -----------------------------

    private fun settingsWriteDirect(ctx: Context): Result {
        val cr = ctx.contentResolver
        val key = "vibrate_on"
        return try {
            val original = Settings.System.getInt(cr, key, 1)
            val ok = Settings.System.putInt(cr, key, original) // write same value back
            if (ok) Result("Settings.System write (direct)", Verdict.PASS, "putInt($key=$original) ok")
            else Result("Settings.System write (direct)", Verdict.FAIL, "putInt returned false")
        } catch (t: Throwable) {
            Result("Settings.System write (direct)", Verdict.FAIL, t.describe())
        }
    }

    // ---- 6. Settings.System write (shell) ----------------------------------

    private fun settingsWriteShell(ctx: Context, pserverOk: Boolean): Result {
        if (!pserverOk) return Result("Settings.System write (shell)", Verdict.INFO, "skipped: no PServerBinder")
        val cr = ctx.contentResolver
        val key = "vibrate_on"
        val original = try { Settings.System.getInt(cr, key, 1) } catch (t: Throwable) { 1 }
        val out = exec("settings put system $key $original")
        val readBack = try { Settings.System.getInt(cr, key, -1) } catch (t: Throwable) { -1 }
        val ok = readBack == original
        return Result(
            "Settings.System write (shell)",
            if (ok) Verdict.PASS else Verdict.PARTIAL,
            "settings put system $key $original => '${out}' ; readback=$readBack"
        )
    }

    // ---- 6b. Root-shell fallbacks ------------------------------------------

    private fun rootFps(pserverOk: Boolean): Result {
        if (!pserverOk) return Result("FPS via root (SurfaceFlinger 1013)", Verdict.INFO, "skipped: no PServerBinder")
        val out = exec("service call SurfaceFlinger 1013")
        val ok = out != null && out.contains("Result") && !out.contains("not permitted")
        return Result(
            "FPS via root (SurfaceFlinger 1013)",
            if (ok) Verdict.PASS else Verdict.FAIL,
            (out ?: "<null>").replace("\n", " ")
        )
    }

    private fun rootSetprop(pserverOk: Boolean): Result {
        if (!pserverOk) return Result("setprop via root (touch disable)", Verdict.INFO, "skipped: no PServerBinder")
        val key = "sys.temp.disabled.touch"
        val current = exec("getprop $key") ?: ""
        val value = if (current.isBlank()) "0,0" else current
        exec("setprop $key $value") // write the same value back
        val after = exec("getprop $key")
        val ok = after != null
        return Result(
            "setprop via root (touch disable)",
            if (ok) Verdict.PASS else Verdict.FAIL,
            "$key before='$current' after='$after'"
        )
    }

    // ---- 7. Fan provider binder --------------------------------------------

    private fun fanProvider(): Result {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val controller = getService.invoke(null, SETTINGS_CONTROLLER) as? IBinder
                ?: return Result("Fan provider", Verdict.FAIL, "$SETTINGS_CONTROLLER not found")

            // getProvider("FanProvider") -> IExternalControlManager txn 1
            val d1 = Parcel.obtain(); val r1 = Parcel.obtain()
            val fanBinder: IBinder?
            try {
                d1.writeInterfaceToken("com.ro.settings.IExternalControlManager")
                d1.writeString("FanProvider")
                controller.transact(1, d1, r1, 0)
                r1.readException()
                fanBinder = r1.readStrongBinder()
            } finally { d1.recycle(); r1.recycle() }

            if (fanBinder == null) return Result("Fan provider", Verdict.PARTIAL, "controller reached, FanProvider binder null")

            // IFanControlProvider txn 3 -> readFloat (live duty)
            val d2 = Parcel.obtain(); val r2 = Parcel.obtain()
            try {
                d2.writeInterfaceToken("com.ro.settings.IFanControlProvider")
                fanBinder.transact(3, d2, r2, 0)
                r2.readException()
                val duty = r2.readFloat()
                Result("Fan provider", Verdict.PASS, "IFanControlProvider txn3 duty=$duty")
            } finally { d2.recycle(); r2.recycle() }
        } catch (t: Throwable) {
            Result("Fan provider", Verdict.FAIL, t.describe())
        }
    }

    // ---- 8. Mapping service bind -------------------------------------------

    private fun mappingService(ctx: Context): Result {
        val implicit = Intent("com.ro.mapping.action.MAPPING_SERVICE")
        val ri = ctx.packageManager.resolveService(implicit, 0)
            ?: return Result("Mapping service (HOME)", Verdict.FAIL, "resolveService returned null")
        val si = ri.serviceInfo
        val explicit = Intent(implicit).setClassName(si.packageName, si.name)
        return bindProbe(ctx, "Mapping service (HOME)", explicit, "(${si.packageName}/${si.name})")
    }

    // ---- 9. Quickstep bind -------------------------------------------------

    private fun quickstep(ctx: Context): Result {
        val recents = try {
            val res = android.content.res.Resources.getSystem()
            val id = res.getIdentifier("config_recentsComponentName", "string", "android")
            if (id != 0) res.getString(id) else null
        } catch (t: Throwable) { null }
        if (recents.isNullOrEmpty()) return Result("Quickstep bind", Verdict.INFO, "config_recentsComponentName empty")
        val cn = android.content.ComponentName.unflattenFromString(recents)
        val intent = Intent("android.intent.action.QUICKSTEP_SERVICE").setPackage(cn?.packageName)
        return bindProbe(ctx, "Quickstep bind", intent, "(recents=$recents)")
    }

    private fun bindProbe(ctx: Context, name: String, intent: Intent, extra: String = ""): Result {
        var bound = false
        val conn = object : ServiceConnection {
            override fun onServiceConnected(n: android.content.ComponentName?, s: IBinder?) {}
            override fun onServiceDisconnected(n: android.content.ComponentName?) {}
        }
        return try {
            bound = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            val v = if (bound) Verdict.PASS else Verdict.FAIL
            Result(name, v, "bindService=$bound $extra")
        } catch (t: Throwable) {
            Result(name, Verdict.FAIL, t.describe() + " " + extra)
        } finally {
            if (bound) try { ctx.unbindService(conn) } catch (_: Throwable) {}
        }
    }

    // ---- 10/11. Window probes ----------------------------------------------

    private fun windowProbes(ctx: Context, add: (Result) -> Unit) {
        val bottom = bottomDisplay(ctx)
        if (bottom == null) {
            add(Result("Bottom display", Verdict.FAIL, "no display with id != 0"))
            return
        }
        add(Result("Bottom display", Verdict.INFO, "id=${bottom.displayId} ${bottom.name}"))
        add(addViewProbe(ctx, bottom, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, "TYPE_APPLICATION_OVERLAY(2038)"))
        add(addViewProbe(ctx, bottom, 2021, "TYPE_BOOT_PROGRESS(2021)"))
    }

    private fun bottomDisplay(ctx: Context): Display? {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.firstOrNull { it.displayId != 0 }
    }

    private fun addViewProbe(ctx: Context, display: Display, type: Int, label: String): Probes.Result {
        val latch = CountDownLatch(1)
        var detail = "no result"
        var verdict = Verdict.FAIL
        Handler(Looper.getMainLooper()).post {
            var view: View? = null
            var wm: WindowManager? = null
            try {
                val dctx = ctx.createDisplayContext(display)
                wm = dctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                @Suppress("DEPRECATION")
                val size = android.graphics.Point().also { display.getRealSize(it) }
                val lp = WindowManager.LayoutParams(
                    64, 64, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.CENTER
                view = View(dctx)
                wm.addView(view, lp)
                verdict = Verdict.PASS
                detail = "addView ok on ${size.x}x${size.y}"
            } catch (t: Throwable) {
                verdict = Verdict.FAIL
                detail = t.describe()
            } finally {
                if (view != null) try { wm?.removeViewImmediate(view) } catch (_: Throwable) {}
                latch.countDown()
            }
        }
        latch.await(3, TimeUnit.SECONDS)
        return Result("Window: $label", verdict, detail)
    }

    // ---- 12. DisplayManager.setBrightness (hidden) -------------------------

    private fun displayBrightness(ctx: Context): Result {
        return try {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val m = dm.javaClass.getMethod("getBrightness", Int::class.javaPrimitiveType)
            val current = m.invoke(dm, 0) as Float
            val set = dm.javaClass.getMethod(
                "setBrightness", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType
            )
            set.invoke(dm, 0, current) // same value back
            Result("DisplayManager.setBrightness", Verdict.PASS, "get/setBrightness(0)=$current ok")
        } catch (t: Throwable) {
            Result("DisplayManager.setBrightness", Verdict.FAIL, t.describe())
        }
    }

    // ---- 13. sysfs telemetry reads -----------------------------------------

    private val SYSFS = listOf(
        "/sys/devices/virtual/thermal/thermal_zone48/temp",
        "/sys/devices/system/cpu/cpu7/cpufreq/cpuinfo_cur_freq",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/power_supply/battery/current_now"
    )

    private fun sysfsReads(add: (Result) -> Unit) {
        for (path in SYSFS) {
            val r = try {
                val f = File(path)
                if (!f.exists()) Result("sysfs ${f.name}", Verdict.FAIL, "$path does not exist")
                else {
                    val v = f.readText().trim()
                    Result("sysfs ${f.name}", Verdict.PASS, "$path => $v")
                }
            } catch (t: Throwable) {
                Result("sysfs ${File(path).name}", Verdict.FAIL, "$path => ${t.describe()}")
            }
            add(r)
        }
    }

    // ---- 14. SurfaceFlinger FPS counter ------------------------------------

    private fun surfaceFlingerFps(): Result {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("service", "call", "SurfaceFlinger", "1013"))
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor(2, TimeUnit.SECONDS)
            if (out.isNotEmpty() && out.contains("Result"))
                Result("SurfaceFlinger 1013", Verdict.PASS, out.replace("\n", " "))
            else
                Result("SurfaceFlinger 1013", Verdict.PARTIAL, "exec ran, out='${out.replace("\n", " ")}'")
        } catch (t: Throwable) {
            Result("SurfaceFlinger 1013", Verdict.FAIL, t.describe())
        }
    }

    private fun Throwable.describe(): String =
        "${this.javaClass.simpleName}: ${this.message ?: "(no message)"}"
}
