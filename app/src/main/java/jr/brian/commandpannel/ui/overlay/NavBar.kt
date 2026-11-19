package jr.brian.commandpannel.ui.overlay

import jr.brian.commandpannel.R
import androidx.compose.ui.res.stringResource
import android.content.ComponentName
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
 * shown.) [forceLaunch] shows Launch with no app chosen (the first-run onboarding points at it), and
 * [onLaunchPositioned] reports where it is. Each installed Panel plugin ([plugins]) adds a button after the user's
 * order, shown only while one is installed; picking it calls [onPlugin] with the plugin's key.
 *
 * While the buttons fit they're spaced evenly, like stock. When they don't (several plugins, say) the row scrolls
 * sideways by touch instead of squeezing them, with [EDGE_PADDING] at both ends so the first and last aren't cut off.
 * [scrollEnabled] is off in Arrange mode, so dragging the bar always wins over scrolling it.
 */
@Composable
fun NavBar(
    activeTab: OverlayTab,
    modifier: Modifier = Modifier,
    quickLaunchLabel: String? = null,
    quickLaunchApp: ComponentName? = null,
    forceLaunch: Boolean = false,
    onLaunchPositioned: (Rect) -> Unit = {},
    order: List<NavBarButton> = NavBarButton.entries,
    onMode: () -> Unit = {},
    onTask: () -> Unit = {},
    onLaunch: () -> Unit = {},
    onSettings: () -> Unit = {},
    onApp: () -> Unit = {},
    plugins: List<PanelPluginButton> = emptyList(),
    onPlugin: (key: String) -> Unit = {},
    scrollEnabled: Boolean = true,
) {
    TccCard(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scroll = rememberScrollState()
            Row(
                // Padding after the scroll is content padding: it scrolls with the row and keeps the end buttons clear of
                // the card's edge at rest. The minimum width fills the bar, so buttons that fit spread out as before.
                modifier = Modifier.fillMaxHeight()
                    .horizontalScroll(scroll, enabled = scrollEnabled)
                    .padding(horizontal = EDGE_PADDING, vertical = 5.dp)
                    .then(
                        if (constraints.hasBoundedWidth) Modifier.widthIn(min = (maxWidth - EDGE_PADDING * 2).coerceAtLeast(0.dp))
                        else Modifier
                    ),
                horizontalArrangement = NavBarArrangement(minGap = MIN_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val nav = jr.brian.commandpannel.ui.components.TccTileVariant.NAV_BAR
                val shown = order.filter { it != NavBarButton.LAUNCH || quickLaunchLabel != null || forceLaunch }
                val launchModifier = Modifier.onGloballyPositioned { onLaunchPositioned(it.boundsInRoot()) }
                shown.forEachIndexed { index, button ->
                    when (button) {
                        NavBarButton.MODE -> TccIconButton(
                            "ic_screen_control", stringResource(R.string.navigation_screen_control_text), circleSize = 44.dp, active = activeTab == OverlayTab.MODE,
                            variant = nav, index = index, onClick = onMode,
                        )
                        NavBarButton.TASK -> TccIconButton("ic_task_manage", stringResource(R.string.navigation_task_manage_text), circleSize = 44.dp, variant = nav, index = index, onClick = onTask)
                        NavBarButton.LAUNCH -> if (quickLaunchApp != null) {
                            TccIconButton(
                                // Default inset, like the other buttons: the app icon gets the same box, so it matches their size
                                // and its label lines up with theirs.
                                icon = TileIcon.App(quickLaunchApp), label = quickLaunchLabel ?: stringResource(R.string.nav_launch), circleSize = 44.dp,
                                modifier = launchModifier, variant = nav, index = index, onClick = onLaunch,
                            )
                        } else {
                            TccIconButton("ic_app_manage", stringResource(R.string.nav_launch), circleSize = 44.dp, modifier = launchModifier, variant = nav, index = index, onClick = onLaunch)
                        }
                        NavBarButton.SETTINGS -> TccIconButton(
                            "ic_system_set", stringResource(R.string.navigation_system_set_text), circleSize = 44.dp, active = activeTab == OverlayTab.SETTINGS,
                            variant = nav, index = index, onClick = onSettings,
                        )
                        NavBarButton.APP -> TccIconButton(
                            icon = TileIcon.Vector(Icons.Filled.OpenInNew),
                            label = stringResource(R.string.nav_app), circleSize = 44.dp, variant = nav, index = index, onClick = onApp,
                        )
                    }
                }
                plugins.forEachIndexed { i, plugin ->
                    TccIconButton(
                        icon = TileIcon.Plugin(plugin.service), label = plugin.label, circleSize = 44.dp,
                        contentDescription = stringResource(R.string.nav_plugin_open, plugin.label),
                        variant = nav, index = shown.size + i, onClick = { onPlugin(plugin.key) },
                    )
                }
            }
        }
    }
}

/** Room at both ends of the nav bar's row (stock's side padding). */
private val EDGE_PADDING = 32.dp

/** The smallest gap between nav buttons before the row scrolls instead. */
private val MIN_GAP = 12.dp

/** A Panel plugin's nav bar button: its service, the tile label, and the key the session uses. */
data class PanelPluginButton(val service: ComponentName, val label: String) {
    val key: String get() = service.flattenToString()
}
