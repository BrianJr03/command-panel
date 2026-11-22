package jr.brian.commandpannel.privileged

/**
 * Quoting for the root shell commands the app builds. The rules, which `RootShellTest` checks in CI:
 *
 * - A plain command goes through [PrivilegedAccess.run] (`privileged.run("svc", "wifi", "disable")`): every argument
 *   is quoted, so nothing in it can be read as shell syntax.
 * - A script that needs shell syntax (`&&`, pipes, redirects, loops) goes through [PrivilegedAccess.shell], and every
 *   value in its string goes through [q]. The only bare `$…` allowed in a `shell("…")` literal is an UPPER_CASE
 *   constant or a `…Command(…)` builder (which quotes its own arguments, like [ReplayVolumes.mkdirsCommand]).
 * - Nothing else in the app quotes for the shell: there's one [q].
 */
object RootShell {
    /** [s] single-quoted for `/system/bin/sh`: safe for any text, including spaces, quotes, `$`, `;` and newlines. */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** [argv] each quoted and joined with spaces: one simple command, with no shell syntax coming from its arguments. */
    fun command(vararg argv: String): String = argv.joinToString(" ") { q(it) }
}
