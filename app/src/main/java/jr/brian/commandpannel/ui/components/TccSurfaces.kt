package jr.brian.commandpannel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.theme.LocalTccColors

/** Rounded panel surface used by every stock card (`t0/C3137a`: corner radius 20dp, panel background). */
@Composable
fun TccCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 20,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalTccColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(if (selected) colors.panelBackgroundSelect else colors.panelBackground),
        content = content,
    )
}
