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

    @Test
    fun everyValueInAShellLiteralIsQuoted() {
        val violations = ShellLiteralGuard.scan(File("src/main/java"))
        assertEquals(
            "quote these with RootShell.q, or use privileged.run(…) (see RootShell)",
            emptyList<ShellLiteralGuard.Violation>(),
            violations,
        )
    }

    @Test
    fun theGuardCatchesUnquotedValues() {
        val code = """
            fun shell(script: String) = Unit
            suspend fun f() {
                privileged.shell("cp ${'$'}src ${'$'}{RootShell.q(dst)} && ${'$'}{a.b}" +
                    " ${'$'}LIST_END ${'$'}{if (on) "disable" else "enable"}")
                shell("ok ${'$'}{LookPackJson.EXTENSION} ${'$'}{ReplayVolumes.mkdirsCommand(r, p)} \${'$'}d")
                VendorRootShell.exec("pm grant ${'$'}pkg x")
                privileged.shell(RootShell.command("rm", path))
            }
        """.trimIndent()
        val found = ShellLiteralGuard.scan("T.kt", code).map { it.line to it.expression }
        assertEquals(
            listOf(3 to "src", 3 to "a.b", 4 to "if (on) \"disable\" else \"enable\"", 6 to "pkg"),
            found,
        )
    }
}
