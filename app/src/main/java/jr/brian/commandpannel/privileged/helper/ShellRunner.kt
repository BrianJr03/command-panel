package jr.brian.commandpannel.privileged.helper

import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Runs a process with a timeout that actually holds. It reads stdout and stderr on their own threads while it waits,
 * so a command that writes a lot to stderr can't fill the pipe and stall, and a command that hangs is killed once
 * [run]'s timeout passes instead of blocking the caller (a binder thread in the helper) forever.
 *
 * Plain JVM (no Android), so it's unit tested against `/bin/sh`.
 */
internal object ShellRunner {
    /** How long to wait for the output to finish draining once the process has exited (or been killed). */
    private const val DRAIN_MS = 1_000L

    /** The exit code of a command that timed out or couldn't start. A real exit code is never negative. */
    const val FAILED = -1

    /** Exit code, stdout, stderr. The exit code is [FAILED] on a timeout or when the process couldn't start. */
    fun run(argv: List<String>, timeoutMs: Long): Triple<Int, String, String> = try {
        val p = ProcessBuilder(argv).start()
        runCatching { p.outputStream.close() } // nothing is written to stdin; EOF lets commands that read it go on
        val out = Drain(p.inputStream, "shell-out")
        val err = Drain(p.errorStream, "shell-err")
        if (!p.waitFor(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)) {
            killTree(p)
            Triple(FAILED, out.await(DRAIN_MS), "timeout after ${timeoutMs}ms")
        } else {
            // A background child that kept the pipe open can't hold this up past DRAIN_MS.
            Triple(p.exitValue(), out.await(DRAIN_MS), err.await(DRAIN_MS))
        }
    } catch (t: Throwable) {
        Triple(FAILED, "", t.message ?: t.javaClass.simpleName)
    }

    /** Kills [p] and what it started, so a timed-out `a && b` can't keep working after it's reported as failed. */
    private fun killTree(p: Process) {
        // Listed first: once the shell is gone, its children are reparented. Android's Process has no pid() or
        // descendants(), but its toString() carries the pid, as the JVM's does.
        val children = PID.find(p.toString())?.groupValues?.get(1)?.let(::descendants).orEmpty()
        p.destroyForcibly()
        if (children.isNotEmpty()) runCatching {
            ProcessBuilder(listOf("kill", "-9") + children).start().waitFor(DRAIN_MS, TimeUnit.MILLISECONDS)
        }
    }

    private val PID = Regex("""pid=(\d+)""")

    /** Every process under [pid], from `pgrep -P`; empty when it can't be listed. */
    private fun descendants(pid: String): List<String> {
        val found = mutableListOf<String>()
        val pending = ArrayDeque(listOf(pid))
        while (pending.isNotEmpty()) {
            val children = runCatching {
                ProcessBuilder("pgrep", "-P", pending.removeFirst()).redirectErrorStream(true).start()
                    .inputStream.bufferedReader().use { it.readLines() }
            }.getOrDefault(emptyList()).map { it.trim() }.filter { line -> line.isNotEmpty() && line.all(Char::isDigit) }
            found += children
            pending += children
        }
        return found
    }

    /** Copies a stream into a buffer on a daemon thread; [await] returns what it has so far. */
    private class Drain(stream: InputStream, name: String) {
        private val buf = StringBuffer()
        private val thread = Thread({
            runCatching {
                stream.bufferedReader().use { r ->
                    val chunk = CharArray(8 * 1024)
                    while (true) {
                        val n = r.read(chunk)
                        if (n < 0) break
                        buf.append(chunk, 0, n)
                    }
                }
            }
        }, name).apply {
            isDaemon = true
            start()
        }

        fun await(ms: Long): String {
            thread.join(ms)
            return buf.toString()
        }
    }
}
