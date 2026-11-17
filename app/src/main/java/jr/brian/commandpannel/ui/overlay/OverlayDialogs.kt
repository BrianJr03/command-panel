package jr.brian.commandpannel.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.ui.components.rememberStockPainter
import jr.brian.commandpannel.ui.components.stockIconColorFilter
import jr.brian.commandpannel.ui.theme.LocalTccColors

/** Which quick-tile modal dialog is open over the overlay. */
enum class OverlayDialogKind { NONE, FOCUS, PERFORMANCE, FAN, CONTROLLER, THEME, TRIGGER, SCREENSHOT }

/** Maps a modal tile id to its dialog, or null for instant-action tiles. */
fun dialogForTile(id: String): OverlayDialogKind? = when (id) {
    "focus_lock" -> OverlayDialogKind.FOCUS
    "performance" -> OverlayDialogKind.PERFORMANCE
    "fan" -> OverlayDialogKind.FAN
    "controller" -> OverlayDialogKind.CONTROLLER
    "theme" -> OverlayDialogKind.THEME
    "trigger_mode" -> OverlayDialogKind.TRIGGER
    "screenshot" -> OverlayDialogKind.SCREENSHOT
    else -> null
}

private data class DialogOption(val label: String, val icon: String, val value: Int)

private data class DialogSpec(val titleIcon: String, val title: String, val options: List<DialogOption>, val selected: Int, val onSelect: (Int) -> Unit)

@Composable
fun OverlayDialogLayer(
    kind: OverlayDialogKind,
    state: OverlayUiState,
    actions: OverlayActions,
    onDismiss: () -> Unit,
) {
    if (kind == OverlayDialogKind.NONE) return
    val spec = specFor(kind, state, actions)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val colors = LocalTccColors.current
        Column(
            modifier = Modifier
                .width(330.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.dialogBg)
                .clickable(enabled = false) {}
                .padding(vertical = 16.dp, horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Image(
                    painter = rememberStockPainter(spec.titleIcon),
                    contentDescription = null,
                    colorFilter = stockIconColorFilter(colors.primary),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(spec.title, color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            spec.options.forEach { opt ->
                OptionRow(opt, selected = opt.value == spec.selected) {
                    spec.onSelect(opt.value)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: DialogOption, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalTccColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.icon.isNotEmpty()) {
            Image(
                painter = rememberStockPainter(option.icon),
                contentDescription = null,
                colorFilter = stockIconColorFilter(colors.primary),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(option.label, color = colors.primary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(colors.accent)
                    else Modifier.border(2.dp, colors.secondary, CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", color = Color.White, fontSize = 13.sp)
        }
    }
}

private fun specFor(kind: OverlayDialogKind, state: OverlayUiState, actions: OverlayActions): DialogSpec = when (kind) {
    OverlayDialogKind.FOCUS -> DialogSpec(
        "ic_screen_focus_lock_mode", "Focus Mode",
        listOf(
            DialogOption("Auto-lock", "ic_screen_focus_lock_switchable", 0),
            DialogOption("Top screen", "ic_screen_focus_lock_upper", 1),
            DialogOption("Bottom screen", "ic_screen_focus_lock_lower", 2),
        ),
        state.focusLock, actions::setFocusLock,
    )
    OverlayDialogKind.PERFORMANCE -> DialogSpec(
        "ic_qs_performance", "Performance Mode",
        listOf(
            DialogOption("Standard Performance", "ic_normal_mode", 0),
            DialogOption("Medium Performance", "ic_performance_mode", 1),
            DialogOption("High Performance", "ic_height_performance_mode", 2),
        ),
        state.performanceMode, actions::setPerformance,
    )
    OverlayDialogKind.FAN -> DialogSpec(
        "ic_qs_fan_control", "Fan Control",
        buildList {
            // Stock: perf 1 hides Off; perf 2 hides Off + Quiet; perf 0 hides Custom.
            if (state.performanceMode == 0) add(DialogOption("Turned Off", "ic_ban_on_use", 0))
            if (state.performanceMode < 2) add(DialogOption("Quiet Mode", "ic_fan_quiet_mode", 1))
            add(DialogOption("Smart Mode", "ic_fan_smart_mode", 4))
            add(DialogOption("Sports Mode", "ic_fan_sport_mode", 5))
            if (state.performanceMode != 0) add(DialogOption("Custom", "ic_fan_custom_mode", 6))
        },
        state.fanMode, actions::setFanMode,
    )
    OverlayDialogKind.CONTROLLER -> DialogSpec(
        "ic_qs_controller_style", "Handle Style",
        listOf(
            DialogOption("Standard Style", "ic_controller_odin_style", 1),
            DialogOption("XBox Style", "ic_controller__xbox_style", 0),
            DialogOption("Ban On Use", "ic_ban_on_use", 2),
        ),
        state.controllerStyle, actions::setControllerStyle,
    )
    OverlayDialogKind.THEME -> DialogSpec(
        "ic_qs_theme_mode", "Theme Mode",
        listOf(
            DialogOption("System Theme", "ic_qs_system_theme", 1),
            DialogOption("Light Theme", "ic_qs_light_theme", 3),
            DialogOption("Dark Theme", "ic_qs_dark_theme", 4),
        ),
        state.themeMode, actions::setThemeMode,
    )
    OverlayDialogKind.TRIGGER -> DialogSpec(
        "ic_qs_controller_style", "L2/R2 Trigger Mode",
        listOf(
            DialogOption("Analog", "", 1),
            DialogOption("Digital", "", 0),
            DialogOption("Both", "", 2),
        ),
        state.triggerMode, actions::setTriggerMode,
    )
    OverlayDialogKind.SCREENSHOT -> DialogSpec(
        "ic_qs_clean", "Screenshot",
        listOf(
            DialogOption("Top Screen", "", 0),
            DialogOption("Bottom Screen", "", 1),
            DialogOption("Both Screens", "", 2),
        ),
        selected = -1, onSelect = actions::takeScreenshot,
    )
    OverlayDialogKind.NONE -> error("no dialog")
}
