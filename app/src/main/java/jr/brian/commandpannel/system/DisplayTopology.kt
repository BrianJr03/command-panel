package jr.brian.commandpannel.system

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Resolves the Thor's two displays. Top is always `Display.DEFAULT_DISPLAY` (0); the bottom is the first display
 * with a non-zero id (4 on the Thor). Matches the stock app's `ScreenUtil.findFirstExternalDisplay`.
 */
class DisplayTopology(context: Context) {
    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    val topDisplay: Display? get() = dm.getDisplay(Display.DEFAULT_DISPLAY)
    val bottomDisplay: Display? get() = dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

    /** Real pixel size of a display (full panel, not the app-usable area). */
    @Suppress("DEPRECATION")
    fun realSize(display: Display): Point = Point().also { display.getRealSize(it) }
}
