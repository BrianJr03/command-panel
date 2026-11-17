package jr.brian.commandpannel.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/**
 * A tile/button icon source: either a stock app drawable (the 10 stock tiles, nav and mode buttons) or a Material
 * icon (the extra Android/Tools tiles the stock app never shipped drawables for).
 */
sealed interface TileIcon {
    data class Stock(val name: String) : TileIcon
    data class Vector(val image: ImageVector) : TileIcon
}

@Composable
fun TileIcon.painter(): Painter = when (this) {
    is TileIcon.Stock -> rememberStockPainter(name)
    is TileIcon.Vector -> rememberVectorPainter(image)
}
