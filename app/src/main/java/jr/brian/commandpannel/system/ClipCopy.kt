package jr.brian.commandpannel.system

/**
 * The root command that puts a finished clip in its folder. It copies to a hidden partial file and renames it only once
 * the copy is complete, so a slow or interrupted copy never leaves a broken clip under a real clip name.
 */
internal object ClipCopy {
    /** Long enough for a large clip going to a slow SD card. */
    const val TIMEOUT_MS = 120_000

    private fun partPath(dst: String) = "${dst.substringBeforeLast('/')}/.${dst.substringAfterLast('/')}.part"

    /** Copies [src] to [dst] and prints [okMarker] on success. Never overwrites an existing [dst]. */
    fun command(src: String, dst: String, okMarker: String): String {
        val d = ThemeFolder.q(dst)
        val part = ThemeFolder.q(partPath(dst))
        return "[ ! -e $d ] && cp ${ThemeFolder.q(src)} $part && chmod 664 $part && mv $part $d && echo $okMarker"
    }

    /** Removes what a failed [command] for [dst] left behind. */
    fun cleanupCommand(dst: String): String = "rm -f ${ThemeFolder.q(partPath(dst))}"
}
