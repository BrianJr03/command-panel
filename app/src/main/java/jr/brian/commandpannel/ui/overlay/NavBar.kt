package jr.brian.commandpannel.ui.overlay

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import jr.brian.commandpannel.system.NavBarButton
import jr.brian.commandpannel.ui.components.TccCard
import jr.brian.commandpannel.ui.components.TccIconButton
import jr.brian.commandpannel.ui.components.TileIcon

/**
 * Stock bottom navigation bar: Mode, Task, Settings, plus the fork's Quick Launch and App buttons, drawn in the
 * user's [order] (companion Nav Bar section). Launch only shows once an app is chosen; with [quickLaunchApp] set it
 * shows that app's icon and name instead of the stock glyph and "Launch". (Stock's hidden "app manage" button is not
 * shown.)
 */
@Composable
fun NavBar(
    activeTab: OverlayTab,
    modifier: Modifier = Modifier,
    quickLaunchLabel: String? = null,
    quickLaunchApp: ComponentName? = null,
    order: List<NavBarButton> = NavBarButton.entries,
    onMode: () -> Unit = {},
    onTask: () -> Unit = {},
    onLaunch: () -> Unit = {},
    onSettings: () -> Unit = {},
    onApp: () -> Unit = {},
) {
    TccCard(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 32.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val nav = jr.brian.commandpannel.ui.components.TccTileVariant.NAV_BAR
            val shown = order.filter { it != NavBarButton.LAUNCH || quickLaunchLabel != null }
            shown.forEachIndexed { index, button ->
                when (button) {
                    NavBarButton.MODE -> TccIconButton(
                        "ic_screen_control", "Mode", circleSize = 44.dp, active = activeTab == OverlayTab.MODE,
                        variant = nav, index = index, onClick = onMode,
                    )
                    NavBarButton.TASK -> TccIconButton("ic_task_manage", "Task", circleSize = 44.dp, variant = nav, index = index, onClick = onTask)
                    NavBarButton.LAUNCH -> if (quickLaunchApp != null) {
                        TccIconButton(
                            icon = TileIcon.App(quickLaunchApp), label = quickLaunchLabel ?: "Launch", circleSize = 44.dp,
                            iconInset = 44.dp * 0.14f, variant = nav, index = index, onClick = onLaunch,
                        )
                    } else {
                        TccIconButton("ic_app_manage", "Launch", circleSize = 44.dp, variant = nav, index = index, onClick = onLaunch)
                    }
                    NavBarButton.SETTINGS -> TccIconButton(
                        "ic_system_set", "Settings", circleSize = 44.dp, active = activeTab == OverlayTab.SETTINGS,
                        variant = nav, index = index, onClick = onSettings,
                    )
                    NavBarButton.APP -> TccIconButton(
                        icon = TileIcon.Vector(Icons.Filled.OpenInNew),
                        label = "App", circleSize = 44.dp, variant = nav, index = index, onClick = onApp,
                    )
                }
            }
        }
    }
}
