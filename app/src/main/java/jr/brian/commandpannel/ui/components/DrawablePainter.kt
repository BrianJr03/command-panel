package jr.brian.commandpannel.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.roundToInt

/**
 * A Compose [Painter] that draws an Android [Drawable]. Used for the AYN stock icons, which are loaded from the stock
 * app's [Resources][android.content.res.Resources] (see [jr.brian.commandpannel.privileged.StockResources]) and so
 * can't go through `painterResource`, which only accepts own-package resource ids.
 *
 * Stock icons are static `VectorDrawable`s, so no Animatable/invalidation handling is needed. Tint is forwarded via
 * [applyColorFilter] so the existing `ColorFilter.tint(...)` at call sites still colours the icon per look.
 */
class DrawablePainter(private val drawable: Drawable) : Painter() {

    override val intrinsicSize: Size =
        if (drawable.intrinsicWidth >= 0 && drawable.intrinsicHeight >= 0) {
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        } else {
            Size.Unspecified
        }

    override fun applyAlpha(alpha: Float): Boolean {
        drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        drawable.colorFilter = colorFilter?.asAndroidColorFilter()
        return true
    }

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
