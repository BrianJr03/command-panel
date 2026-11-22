package jr.brian.commandpannel.privileged

/**
 * Quoting for the root shell commands the app builds. Every path, file name or value that goes into a
 * [PrivilegedAccess.shell] command goes through [q] (or [command]), and nothing else in the app quotes for the shell:
 * `RootShellQuotingTest` fails if another copy appears.
 *
 * Constants (package names, setting keys, our own markers) and numbers don't need quoting; anything that comes from a
 * user, a file name, a folder listing or another app always does.
 */
object RootShell {
    /** [s] single-quoted for `/system/bin/sh`: safe for any text, including spaces, quotes, `$`, `;` and newlines. */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** [argv] each quoted and joined with spaces: one simple command, with no shell syntax coming from its arguments. */
    fun command(vararg argv: String): String = argv.joinToString(" ") { q(it) }
}
