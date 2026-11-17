package jr.brian.commandpannel.ui.components

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * A tile/button icon source: either a stock app drawable (the 10 stock tiles, nav and mode buttons), a Material
 * icon (the extra Android/Tools tiles the stock app never shipped drawables for), or an installed app's launcher
 * icon (the Quick Launch nav button). [App] icons are full-colour and must not be tinted.
 */
sealed interface TileIcon {
    data class Stock(val name: String) : TileIcon
    data class Vector(val image: ImageVector) : TileIcon
    data class App(val component: ComponentName) : TileIcon
}

@Composable
fun TileIcon.painter(): Painter = when (this) {
    is TileIcon.Stock -> rememberStockPainter(name)
    is TileIcon.Vector -> rememberVectorPainter(image)
    is TileIcon.App -> {
        val context = LocalContext.current
        val bitmap = remember(component) {
            runCatching {
                context.packageManager.getActivityIcon(component).toBitmap(APP_ICON_PX, APP_ICON_PX).asImageBitmap()
            }.getOrNull()
        }
        // Uninstalled / unresolvable app: fall back to the stock app-manage glyph.
        if (bitmap != null) remember(bitmap) { BitmapPainter(bitmap) } else rememberStockPainter("ic_app_manage")
    }
}

private const val APP_ICON_PX = 144
