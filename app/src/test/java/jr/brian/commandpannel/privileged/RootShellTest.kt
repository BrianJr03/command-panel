package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.privileged.helper.ShellRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** [RootShell] against the host's `/bin/sh`, and a guard that it stays the only shell quoting in the app. */
class RootShellTest {
    private fun echo(arg: String): String =
        ShellRunner.run(listOf("/bin/sh", "-c", "printf '%s' ${RootShell.q(arg)}"), 5_000).second

    @Test
    fun anyTextComesBackUnchanged() {
        listOf(
            "plain",
            "TCP Clips",
            "It's Clips",
            "'''",
            "a;reboot;b",
            "\$(id) `id` \$HOME",
            "x && y || z | w > out < in &",
            "new\nline",
            "back\\slash",
            "",
        ).forEach { assertEquals(it, echo(it)) }
    }

    @Test
    fun commandQuotesEachArgument() {
        assertEquals("'ls' '-1' 'It'\\''s Clips'", RootShell.command("ls", "-1", "It's Clips"))
    }

    @Test
    fun noOtherCodeQuotesForTheShell() {
        // Unit tests run in the module folder (app/).
        val src = File("src/main/java")
        assertTrue("sources not found from ${File(".").absolutePath}", src.isDirectory)
        val homegrown = Regex("""fun\s+(shellQuote|q)\s*\(|replace\("'", "'\\\\''"\)""")
        val offenders = src.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "RootShell.kt" }
            .filter { homegrown.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertEquals("quote with RootShell.q instead", emptyList<String>(), offenders)
    }
}
