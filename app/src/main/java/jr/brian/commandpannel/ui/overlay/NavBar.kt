package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.ui.components.TccCard
import jr.brian.commandpannel.ui.components.TccIconButton

/** Stock bottom navigation bar: Mode, Task, Settings. (Stock's hidden "app manage" button is not shown.) */
@Composable
fun NavBar(
    activeTab: OverlayTab,
    modifier: Modifier = Modifier,
    onMode: () -> Unit = {},
    onTask: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    TccCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 40.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TccIconButton("ic_screen_control", "Mode", circleSize = 50.dp, active = activeTab == OverlayTab.MODE, onClick = onMode)
            TccIconButton("ic_task_manage", "Task", circleSize = 50.dp, onClick = onTask)
            TccIconButton("ic_system_set", "Settings", circleSize = 50.dp, active = activeTab == OverlayTab.SETTINGS, onClick = onSettings)
        }
    }
}
