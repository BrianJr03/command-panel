package jr.brian.commandpannel.privileged

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Ephemerally suppresses the stock `com.odin.dualscreen.assistant` so Command Panel can own the AYN button, and
 * restores it cleanly. Opt-in only.
 *
 * The stock app is `persistent="true"` + `android.uid.system`, so `am force-stop` is ignored, and a bare `SIGSTOP`
 * while its `TccServices` overlay window is attached would freeze that window on the bottom screen. The sequence
 * (established from the stock teardown, NOTES.md §2/§8): transiently disable `TccServices`, `kill -9` so
 * WindowManager drops any stock window, wait for the persistent process to respawn (now unable to bind the disabled
 * service), `SIGSTOP` the fresh windowless process, then re-enable the service on disk — so nothing is ever left
 * disabled across an uninstall or reboot. Restore kills the frozen process and lets AMS respawn it normally.
 *
 * All process control goes through the root [shell] (the app passes [PrivilegedAccess.shell]; the root helper passes
 * its own shell to run [restore] after the app is gone). It suspends, so none of it can run on the main thread; the
 * helper and the app's teardown, which must wait, call it from `runBlocking`. Pure helpers are unit-tested.
 */
class StockSuppressor(private val shell: suspend (String) -> String?) {

    companion object {
        const val STOCK_PKG = "com.odin.dualscreen.assistant"
        const val TCC_SERVICE = "$STOCK_PKG/.services.TccServices"
        private const val RESPAWN_WAIT_MS = 400L

        /** Parses `pidof` output into numeric pids. */
        fun parsePids(raw: String?): List<String> =
            raw.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.all(Char::isDigit) }

        /** A process is suppressed when `/proc/<pid>/stat` reports the stopped state `) T `. */
        fun isStoppedStat(stat: String?): Boolean = stat?.contains(") T ") == true
    }

    /** One command with each argument quoted ([RootShell.command]). */
    private suspend fun run(vararg argv: String): String? = shell(RootShell.command(*argv))

    /** Sends [signal] (`-9`, `-STOP`, `-CONT`) to [pids], if there are any. */
    private suspend fun kill(signal: String, pids: List<String>) {
        if (pids.isNotEmpty()) run("kill", signal, *pids.toTypedArray())
    }

    private suspend fun pids(): List<String> = parsePids(run("pidof", STOCK_PKG))

    suspend fun suppress(): Boolean {
        run("am", "stopservice", "-n", TCC_SERVICE)
        run("pm", "disable", TCC_SERVICE)
        run("pm", "enable", STOCK_PKG)

        pids().takeIf { it.isNotEmpty() }?.let {
            kill("-9", it)
            // A cancel here would leave TccServices disabled on disk, and the AYN button dead.
            withContext(NonCancellable) { delay(RESPAWN_WAIT_MS) }
        }
        kill("-STOP", pids())

        // Never leave anything disabled on disk.
        run("pm", "enable", STOCK_PKG)
        run("pm", "enable", TCC_SERVICE)

        // The freshly stopped process may have respawned again; make sure the live one is stopped.
        kill("-STOP", pids())
        return isSuppressed()
    }

    suspend fun restore(): Boolean {
        run("pm", "enable", STOCK_PKG)
        run("pm", "enable", TCC_SERVICE)
        kill("-9", pids())
        run("am", "startservice", "-n", TCC_SERVICE)
        return !isSuppressed()
    }

    suspend fun isSuppressed(): Boolean {
        val pid = pids().firstOrNull() ?: return false
        return isStoppedStat(run("cat", "/proc/$pid/stat"))
    }

    /** Fork fix: unfreeze briefly so the frozen process can drain a queued broadcast (e.g. SCREEN_OFF) without ANR. */
    suspend fun continueProcess() = kill("-CONT", pids())

    suspend fun stopProcess() = kill("-STOP", pids())
}
