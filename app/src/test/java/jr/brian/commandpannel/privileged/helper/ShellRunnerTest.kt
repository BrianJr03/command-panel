package jr.brian.commandpannel.privileged.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** [ShellRunner] against the host's `/bin/sh`. */
class ShellRunnerTest {
    private fun sh(cmd: String, timeoutMs: Long = 5_000) = ShellRunner.run(listOf("/bin/sh", "-c", cmd), timeoutMs)

    @Test
    fun returnsExitCodeAndBothStreams() {
        val (exit, out, err) = sh("echo hello; echo oops >&2; exit 3")
        assertEquals(3, exit)
        assertEquals("hello\n", out)
        assertEquals("oops\n", err)
    }

    @Test
    fun aHangingCommandIsKilledAtTheTimeout() {
        val start = System.nanoTime()
        val (exit, _, err) = sh("sleep 30", timeoutMs = 300)
        val tookMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(-1, exit)
        assertTrue(err.startsWith("timeout"))
        assertTrue("took $tookMs ms", tookMs < 5_000)
    }

    @Test
    fun aTimeoutAlsoStopsWhatTheCommandStarted() {
        val marker = File.createTempFile("shell-runner", ".marker").apply { delete() }
        val (exit, _, _) = sh("sh -c 'sleep 1; touch ${marker.absolutePath}'; true", timeoutMs = 200)
        assertEquals(-1, exit)
        Thread.sleep(1_500)
        assertFalse(marker.exists())
    }

    @Test
    fun lotsOfStderrDoesNotStallStdout() {
        // More than a pipe buffer (64 KiB) on stderr before anything on stdout used to deadlock.
        val (exit, out, err) = sh("i=0; while [ \$i -lt 4000 ]; do echo 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' >&2; i=\$((i+1)); done; echo done")
        assertEquals(0, exit)
        assertEquals("done\n", out)
        assertTrue(err.length > 64 * 1024)
    }

    @Test
    fun aMissingBinaryIsAFailureNotACrash() {
        val (exit, _, _) = ShellRunner.run(listOf("/no/such/binary"), 1_000)
        assertEquals(-1, exit)
    }
}
