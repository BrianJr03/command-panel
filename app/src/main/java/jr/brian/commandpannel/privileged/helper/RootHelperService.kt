package jr.brian.commandpannel.privileged.helper

import android.os.Bundle
import android.os.IBinder
import android.util.Log
import kotlin.system.exitProcess

/**
 * [IRootHelper] implementation running inside the root helper process. Display brightness uses the hidden
 * `DisplayManagerGlobal` / `IDisplayManager` methods, reached reflectively (exemptions are set in main()).
 */
class RootHelperService : IRootHelper.Stub() {
    private val tag = "RootHelperService"

    @Volatile
    private var clientToken: IBinder? = null
    private val deathRecipient = IBinder.DeathRecipient {
        Log.i(tag, "client died; helper exiting")
        exitProcess(0)
    }

    override fun protocolVersion(): Int = HelperProtocol.VERSION

    override fun identity(): Bundle = Bundle().apply {
        putInt("uid", android.os.Process.myUid())
        putInt("pid", android.os.Process.myPid())
        putString("context", runCatching {
            java.io.File("/proc/self/attr/current").readText().trim().trim('\u0000')
        }.getOrDefault(""))
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
        clientToken = token
        runCatching { token.linkToDeath(deathRecipient, 0) }
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
}
