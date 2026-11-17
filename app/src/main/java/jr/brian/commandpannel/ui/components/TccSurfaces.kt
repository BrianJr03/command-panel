package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.tccLookColors

/**
 * Rounded panel surface used by every card. The stock look paints the original corner-radius panel;
 * other looks delegate to their [CardStyle] renderer via [tccCard].
 */
@Composable
fun TccCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalTccColors.current
    val spec = LocalTccLookSpec.current
    val lookColors = tccLookColors()
    // Stock keeps the exact per-card corner radius; looks use their own panel shape.
    val shape = if (spec.look == TccUiLook.AUTO) RoundedCornerShape(cornerRadius.dp) else spec.panelCardShape
    Box(
        modifier = modifier.tccCard(spec, colors, lookColors, focused = selected, shape = shape),
        content = content,
    )
}
