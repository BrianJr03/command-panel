package jr.brian.commandpannel.privileged.helper

import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import jr.brian.commandpannel.privileged.StockSuppressor
import kotlin.system.exitProcess

/**
 * [IRootHelper] implementation running inside the root helper process. Display brightness uses the hidden
 * `DisplayManagerGlobal` / `IDisplayManager` methods, reached reflectively (exemptions are set in main()).
 *
 * When the app's client token dies the helper does not exit at once: it waits [HelperProtocol.GRACE_MS] for the app
 * to come back and reattach (a crash restart). If the package is uninstalled, or the grace runs out, it restores the
 * stock assistant when recovery is armed (so a frozen stock process never leaves the AYN button dead) and exits.
 */
class RootHelperService(private val appPackage: String) : IRootHelper.Stub() {
    private val tag = "RootHelperService"
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var clientToken: IBinder? = null
    @Volatile
    private var stockRecoveryArmed = false
    private var orphanedAtMs = 0L

    private val deathRecipient = IBinder.DeathRecipient {
        Log.i(tag, "client died; waiting ${HelperProtocol.GRACE_MS} ms for it to come back")
        main.post {
            clientToken = null
            orphanedAtMs = SystemClock.elapsedRealtime()
            main.removeCallbacks(graceTick)
            main.post(graceTick)
        }
    }

    private val graceTick = object : Runnable {
        override fun run() {
            val step = ClientGrace.next(
                reattached = clientToken != null,
                packageInstalled = isPackageInstalled(),
                elapsedMs = SystemClock.elapsedRealtime() - orphanedAtMs,
                graceMs = HelperProtocol.GRACE_MS,
                armed = stockRecoveryArmed,
            )
            when {
                clientToken != null -> Unit
                step == ClientGrace.Step.WAIT -> main.postDelayed(this, GRACE_TICK_MS)
                else -> {
                    if (step == ClientGrace.Step.RECOVER_AND_EXIT) recoverStock()
                    Log.i(tag, "client gone; helper exiting")
                    TaskFps.stop()
                    DisplayMirror.stop()
                    AudioCapture.stop()
                    exitProcess(0)
                }
            }
        }
    }

    private fun isPackageInstalled(): Boolean {
        val (exit, out, _) = RootHelperMain.runShell("pm path $appPackage", 5_000)
        // A timeout (-1) is not proof of an uninstall; only a clean "not found" is.
        return exit == -1 || out.contains("package:")
    }

    private fun recoverStock() {
        Log.i(tag, "restoring the stock assistant")
        val restored = runCatching {
            StockSuppressor { RootHelperMain.runShell(it, 5_000).second }.restore()
        }.getOrElse { Log.w(tag, "stock restore failed", it); false }
        Log.i(tag, "stock restored=$restored")
        releaseStockOwner()
    }

    /** Clears the debug / release owner record if it still names this app, so the other build doesn't wait on it. */
    private fun releaseStockOwner() {
        val key = jr.brian.commandpannel.system.SiblingBuild.KEY
        val owner = RootHelperMain.runShell("settings get global $key", 5_000).second.trim()
        if (owner == appPackage) RootHelperMain.runShell("settings delete global $key", 5_000)
    }

    override fun protocolVersion(): Int = HelperProtocol.VERSION

    override fun identity(): Bundle = Bundle().apply {
        putInt("uid", android.os.Process.myUid())
        putInt("pid", android.os.Process.myPid())
        putString("context", runCatching {
            java.io.File("/proc/self/attr/current").readText().trim().trim('\u0000')
        }.getOrDefault(""))
        putString("apk", System.getProperty("java.class.path").orEmpty())
    }

    override fun exec(command: String, timeoutMs: Int): Bundle {
        val (exit, out, err) = RootHelperMain.runShell(command, timeoutMs)
        return Bundle().apply {
            putInt("exit", exit)
            putString("out", out)
            putString("err", err)
        }
    }

    override fun getDisplayBrightness(displayId: Int): Float = DisplayBrightness.get(displayId)

    override fun setDisplayBrightness(displayId: Int, brightness: Float, temporary: Boolean) =
        DisplayBrightness.set(displayId, brightness, temporary)

    override fun attachClient(token: IBinder) {
        val linked = runCatching { token.linkToDeath(deathRecipient, 0) }.isSuccess
        if (!linked) return
        main.post {
            clientToken?.takeIf { it !== token }?.let { old -> runCatching { old.unlinkToDeath(deathRecipient, 0) } }
            clientToken = token
            main.removeCallbacks(graceTick)
        }
    }

    override fun shutdown() {
        Log.i(tag, "shutdown requested")
        exitProcess(0)
    }

    override fun removeTask(taskId: Int): Boolean = runCatching {
        val atm = Class.forName("android.app.ActivityTaskManager")
        val service = atm.getMethod("getService").invoke(null)
        service.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
            .invoke(service, taskId) as Boolean
    }.getOrElse {
        Log.w(tag, "removeTask($taskId) failed", it); false
    }

    override fun startDisplayMirror(surface: Surface, layerStack: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Boolean =
        DisplayMirror.start(surface, layerStack, srcW, srcH, dstW, dstH)

    override fun addDisplayMirror(surface: Surface, layerStack: Int, srcW: Int, srcH: Int, dstW: Int, dstH: Int): Boolean =
        DisplayMirror.add(surface, layerStack, srcW, srcH, dstW, dstH)

    override fun stopDisplayMirror() = DisplayMirror.stop()

    override fun startAudioCapture(sink: android.os.ParcelFileDescriptor, game: Boolean, mic: Boolean): Boolean {
        if (!callerIsApp()) {
            Log.w(tag, "startAudioCapture refused for uid ${android.os.Binder.getCallingUid()}")
            runCatching { sink.close() }
            return false
        }
        return AudioCapture.start(sink, game, mic)
    }

    override fun stopAudioCapture() {
        if (callerIsApp()) AudioCapture.stop()
    }

    /** The app's uid, from `pm`, looked up once; -1 if it can't be found. */
    private val appUid: Int by lazy {
        val out = RootHelperMain.runShell("pm list packages -U $appPackage", 5_000).second
        Regex("package:${Regex.escape(appPackage)} uid:(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    /** Audio capture can include the mic, so only the app itself (or root) may start or stop it. */
    private fun callerIsApp(): Boolean {
        val uid = android.os.Binder.getCallingUid()
        return uid == 0 || uid == appUid
    }

    override fun taskFps(displayId: Int): Bundle = TaskFps.sample(displayId)

    override fun stopTaskFps() = TaskFps.stop()

    override fun setStockRecoveryArmed(armed: Boolean) {
        stockRecoveryArmed = armed
    }

    override fun surfaceFlingerFrameCount(): Long {
        val sf = surfaceFlinger ?: return -1L
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            // Exactly what `service call SurfaceFlinger 1013` sends: the binder's own descriptor, no arguments.
            data.writeInterfaceToken(sf.interfaceDescriptor.orEmpty())
            if (!sf.transact(SF_FRAME_COUNTER, data, reply, 0)) return -1L
            reply.readInt().toLong() and 0xffffffffL
        } catch (t: Throwable) {
            Log.w(tag, "SurfaceFlinger $SF_FRAME_COUNTER failed", t)
            -1L
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    @Volatile
    private var sfBinder: IBinder? = null

    private val surfaceFlinger: IBinder?
        get() = sfBinder?.takeIf { it.isBinderAlive } ?: runCatching {
            Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "SurfaceFlinger") as? IBinder
        }.getOrNull().also { sfBinder = it }

    private companion object {
        const val GRACE_TICK_MS = 1_000L
        const val SF_FRAME_COUNTER = 1013
    }
}
