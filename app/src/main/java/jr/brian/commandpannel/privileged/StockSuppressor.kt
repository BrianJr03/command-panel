package jr.brian.commandpannel.privileged

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
 * All process control goes through [PrivilegedAccess] (root). Pure helpers are unit-tested.
 */
class StockSuppressor(private val privileged: PrivilegedAccess) {

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

    private fun pids(): List<String> = parsePids(privileged.shell("pidof $STOCK_PKG"))

    fun suppress(): Boolean {
        privileged.shell("am stopservice -n $TCC_SERVICE")
        privileged.shell("pm disable $TCC_SERVICE")
        privileged.shell("pm enable $STOCK_PKG")

        pids().takeIf { it.isNotEmpty() }?.let {
            privileged.shell("kill -9 ${it.joinToString(" ")}")
            Thread.sleep(RESPAWN_WAIT_MS)
        }
        val fresh = pids()
        if (fresh.isNotEmpty()) privileged.shell("kill -STOP ${fresh.joinToString(" ")}")

        // Never leave anything disabled on disk.
        privileged.shell("pm enable $STOCK_PKG")
        privileged.shell("pm enable $TCC_SERVICE")

        // The freshly stopped process may have respawned again; make sure the live one is stopped.
        val verify = pids()
        if (verify.isNotEmpty()) privileged.shell("kill -STOP ${verify.joinToString(" ")}")
        return isSuppressed()
    }

    fun restore(): Boolean {
        privileged.shell("pm enable $STOCK_PKG")
        privileged.shell("pm enable $TCC_SERVICE")
        pids().takeIf { it.isNotEmpty() }?.let { privileged.shell("kill -9 ${it.joinToString(" ")}") }
        privileged.shell("am startservice -n $TCC_SERVICE")
        return !isSuppressed()
    }

    fun isSuppressed(): Boolean {
        val pid = pids().firstOrNull() ?: return false
        return isStoppedStat(privileged.shell("cat /proc/$pid/stat"))
    }

    /** Fork fix: unfreeze briefly so the frozen process can drain a queued broadcast (e.g. SCREEN_OFF) without ANR. */
    fun continueProcess() {
        pids().takeIf { it.isNotEmpty() }?.let { privileged.shell("kill -CONT ${it.joinToString(" ")}") }
    }

    fun stopProcess() {
        pids().takeIf { it.isNotEmpty() }?.let { privileged.shell("kill -STOP ${it.joinToString(" ")}") }
    }
}
