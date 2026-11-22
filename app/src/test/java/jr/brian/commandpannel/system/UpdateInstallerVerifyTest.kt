package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.update.UpdateInstaller
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallerVerifyTest {
    private val pkg = "jr.brian.commandpannel"
    private val ours = "ab".repeat(32)
    private val theirs = "cd".repeat(32)

    private fun check(
        packageName: String? = pkg,
        signers: List<String> = listOf(ours),
        ownSigners: List<String> = listOf(ours),
        version: String = "0.14.0",
        expected: String? = "0.14.0",
    ) = UpdateInstaller.check(packageName, pkg, signers, ownSigners, version, expected)

    @Test
    fun `accepts an update signed with this build's key`() {
        check()
        check(version = "0.14.0-debug", expected = "0.14.0") // suffixes don't count
        check(expected = null) // the debug test APK has no expected version
        check(ownSigners = listOf(ours.uppercase().chunked(2).joinToString(":")))
    }

    @Test
    fun `refuses another key, another package, another version or an unreadable own key`() {
        listOf(
            { check(signers = listOf(theirs)) },
            { check(signers = listOf(ours, theirs)) },
            { check(signers = emptyList()) },
            { check(ownSigners = emptyList()) },
            { check(packageName = "com.example.other") },
            { check(packageName = null) },
            { check(version = "0.13.9") },
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it() } }
    }

    @Test
    fun `the detached install checks the hash before installing`() {
        val sha = "0f".repeat(32)
        val script = UpdateInstaller.detachedInstallScript(sha, "$pkg/jr.brian.commandpannel.MainActivity")
        assertTrue(script, script.startsWith("if [ \"\$(sha256sum "))
        assertTrue(script, script.contains("= '$sha' ]; then pm install -r -t "))
        // On a mismatch nothing is installed and the companion isn't relaunched.
        assertTrue(script, script.contains("else echo 'Failure [staged copy changed]'; false; fi"))
        assertTrue(script, script.endsWith("am start -n '$pkg/jr.brian.commandpannel.MainActivity'"))
    }
}
