package jr.brian.commandpannel.system

import android.content.Context
import android.media.MediaScannerConnection
import jr.brian.commandpannel.privileged.PrivilegedAccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures per-display screenshots through the root shell (`screencap`), the way the stock assistant's Screenshot
 * tile does. SurfaceFlinger assigns a new physical display id each boot, so they are resolved at capture time from
 * `dumpsys SurfaceFlinger --display-id` by port (131 = top, 132 = bottom on the Thor).
 */
class Screenshotter(
    private val context: Context,
    private val privileged: PrivilegedAccess,
) {
    enum class Target(val label: String) { TOP("Top"), BOTTOM("Bottom"), BOTH("Both") }

    private val dir = "/sdcard/Pictures/Screenshots"

    companion object {
        /** Parses the SurfaceFlinger display dump into port -> physical display id. */
        fun parseDisplayIds(dump: String): Map<Int, Long> {
            val regex = Regex("""Display (\d+) \(HWC display \d+\): port=(\d+)""")
            return regex.findAll(dump).associate { m -> m.groupValues[2].toInt() to m.groupValues[1].toLong() }
        }
    }

    suspend fun capture(target: Target): List<String> {
        val ids = parseDisplayIds(privileged.run("dumpsys", "SurfaceFlinger", "--display-id").orEmpty())
        val top = ids[131]
        val bottom = ids[132]
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val saved = mutableListOf<String>()
        suspend fun shot(displayId: Long?, name: String) {
            displayId ?: return
            val path = "$dir/Screenshot_${ts}_$name.png"
            privileged.run("mkdir", "-p", dir)
            privileged.run("screencap", "-p", "-d", displayId.toString(), path)
            saved.add(path)
        }
        when (target) {
            Target.TOP -> shot(top, "Top")
            Target.BOTTOM -> shot(bottom, "Bottom")
            Target.BOTH -> { shot(top, "Top"); shot(bottom, "Bottom") }
        }
        if (saved.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, saved.toTypedArray(), arrayOf("image/png"), null)
        }
        return saved
    }
}
