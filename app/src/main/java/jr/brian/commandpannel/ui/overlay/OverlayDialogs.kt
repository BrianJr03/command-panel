package jr.brian.commandpannel.ui.overlay

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.ui.res.stringResource
import jr.brian.commandpannel.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import jr.brian.commandpannel.ui.components.noRippleClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.system.FanControl
import jr.brian.commandpannel.ui.components.TccSliderRow
import jr.brian.commandpannel.ui.components.rememberStockPainter
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Debug builds only: lets scripts open a tile's dialog (`AssistantService.ACTION_DEBUG_OPEN_DIALOG`), and logs where
 * the fan dialog's Custom row and slider sit on the bottom screen so a script can tap them (the overlay never takes
 * focus, so key events can't reach it).
 */
object OverlayDebug {
    const val TAG = "OverlayDebug"
    internal val openDialog = MutableStateFlow<String?>(null)
}

/** Opens the dialog a script asked for through [OverlayDebug.openDialog]. A no-op outside debug builds. */
@Composable
fun DebugDialogRequests(onOpen: (OverlayDialogKind) -> Unit) {
    if (!BuildConfig.DEBUG) return
    LaunchedEffect(Unit) {
        OverlayDebug.openDialog.collect { id ->
            if (id == null) return@collect
            dialogForTile(id)?.let(onOpen)
            OverlayDebug.openDialog.value = null
        }
    }
}

/** Logs a node's bounds on screen (debug builds), once per change. */
private fun Modifier.debugBounds(name: String): Modifier = if (!BuildConfig.DEBUG) this else composed {
    var last by remember { mutableStateOf("") }
    onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        val text = "${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}"
        if (text != last) { last = text; android.util.Log.d(OverlayDebug.TAG, "bounds $name=$text") }
    }
}

private data class DialogOption(@StringRes val labelRes: Int, val icon: String, val value: Int)

private data class DialogSpec(
    val titleIcon: String,
    @StringRes val titleRes: Int,
    val options: List<DialogOption>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
    /** An option that keeps the dialog open when picked (the fan's Custom, which shows its slider). */
    val keepOpenValue: Int? = null,
)

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
            .noRippleClickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val colors = LocalTccColors.current
        Column(
            modifier = Modifier
                .width(330.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.dialogBg)
                .noRippleClickable(enabled = false) {}
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
                Text(stringResource(spec.titleRes), color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            // Picked here until the settings observer catches up, so Custom's slider shows right away.
            var selected by remember(kind, spec.selected) { mutableIntStateOf(spec.selected) }
            spec.options.forEach { opt ->
                OptionRow(opt, selected = opt.value == selected, modifier = Modifier.debugBounds("option_${opt.value}")) {
                    spec.onSelect(opt.value)
                    selected = opt.value
                    if (opt.value != spec.keepOpenValue) onDismiss()
                }
                // Like stock, Custom carries a fan speed slider while it's the selected mode.
                if (kind == OverlayDialogKind.FAN && opt.value == FanControl.CUSTOM && selected == FanControl.CUSTOM) {
                    FanSpeedSlider(state, actions)
                }
            }
        }
    }
}

/**
 * The Custom fan mode's speed slider, in the look's slider style. The label shows the duty the value asks for
 * (`fan_speed` as a percent of the range's top, 50 % to 100 % on a Thor). `fan_speed` is written once the slider
 * settles (drag released or tap), not on every step of a drag.
 */
@Composable
private fun FanSpeedSlider(state: OverlayUiState, actions: OverlayActions) {
    val range = state.fanSpeedRange
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    // Holds the written value until the settings observer brings it back, so the bar doesn't jump back meanwhile.
    var written by remember(state.fanSpeed) { mutableStateOf<Int?>(null) }
    val fraction = dragFraction ?: FanControl.speedToFraction(written ?: state.fanSpeed, range)
    val percent = FanControl.speedPercent(FanControl.fractionToSpeed(fraction, range), range)
    TccSliderRow(
        label = stringResource(R.string.dialog_fan_speed, percent),
        fraction = fraction,
        iconBase = "ic_fan_custom_mode",
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp).debugBounds("fan_slider"),
    ) { f, committed ->
        if (committed) {
            val speed = FanControl.fractionToSpeed(f, range)
            written = speed
            dragFraction = null
            if (speed != state.fanSpeed) actions.setFanSpeed(speed)
        } else {
            dragFraction = f
        }
    }
}

@Composable
private fun OptionRow(option: DialogOption, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalTccColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onClick)
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
        Text(stringResource(option.labelRes), color = colors.primary, fontSize = 16.sp, modifier = Modifier.weight(1f))
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
        "ic_screen_focus_lock_mode", R.string.key_focus_mode_title_text,
        listOf(
            DialogOption(R.string.key_focus_mode_touch_switchable, "ic_screen_focus_lock_switchable", 0),
            DialogOption(R.string.key_focus_mode_lock_on_upper_screen, "ic_screen_focus_lock_upper", 1),
            DialogOption(R.string.key_focus_mode_lock_on_lower_screen, "ic_screen_focus_lock_lower", 2),
        ),
        state.focusLock, actions::setFocusLock,
    )
    OverlayDialogKind.PERFORMANCE -> DialogSpec(
        "ic_qs_performance", R.string.text_performance_mode_title,
        listOf(
            DialogOption(R.string.text_performance_mode_normal, "ic_normal_mode", 0),
            DialogOption(R.string.text_performance_mode_performance, "ic_performance_mode", 1),
            DialogOption(R.string.text_performance_mode_height_performance, "ic_height_performance_mode", 2),
        ),
        state.performanceMode, actions::setPerformance,
    )
    OverlayDialogKind.FAN -> DialogSpec(
        "ic_qs_fan_control", R.string.text_fan_control_title,
        // Stock: perf 0 hides Custom; perf 1 hides Off + Custom; perf 2 hides Off + Quiet.
        FanControl.options(state.performanceMode).map(::fanOption),
        state.fanMode, actions::setFanMode,
        keepOpenValue = FanControl.CUSTOM,
    )
    OverlayDialogKind.CONTROLLER -> DialogSpec(
        "ic_qs_controller_style", R.string.text_handle_style_title,
        listOf(
            DialogOption(R.string.fan_option_odin_style, "ic_controller_odin_style", 1),
            DialogOption(R.string.fan_option_xbox_style, "ic_controller__xbox_style", 0),
            DialogOption(R.string.fan_option_disable, "ic_ban_on_use", 2),
        ),
        state.controllerStyle, actions::setControllerStyle,
    )
    OverlayDialogKind.THEME -> DialogSpec(
        "ic_qs_theme_mode", R.string.text_theme_mode_title,
        listOf(
            DialogOption(R.string.text_system_theme, "ic_qs_system_theme", 1),
            DialogOption(R.string.text_light_theme, "ic_qs_light_theme", 3),
            DialogOption(R.string.text_dark_theme, "ic_qs_dark_theme", 4),
        ),
        state.themeMode, actions::setThemeMode,
    )
    OverlayDialogKind.TRIGGER -> DialogSpec(
        "ic_qs_controller_style", R.string.tile_name_trigger_mode,
        listOf(
            DialogOption(R.string.dialog_trigger_analog, "", 1),
            DialogOption(R.string.dialog_trigger_digital, "", 0),
            DialogOption(R.string.dialog_trigger_both, "", 2),
        ),
        state.triggerMode, actions::setTriggerMode,
    )
    OverlayDialogKind.SCREENSHOT -> DialogSpec(
        "ic_qs_clean", R.string.tile_name_screenshot,
        listOf(
            DialogOption(R.string.dialog_screenshot_top, "", 0),
            DialogOption(R.string.dialog_screenshot_bottom, "", 1),
            DialogOption(R.string.dialog_screenshot_both, "", 2),
        ),
        selected = -1, onSelect = actions::takeScreenshot,
    )
    OverlayDialogKind.NONE -> error("no dialog")
}

private fun fanOption(mode: Int): DialogOption = when (mode) {
    FanControl.OFF -> DialogOption(R.string.text_fan_control_off, "ic_ban_on_use", mode)
    FanControl.QUIET -> DialogOption(R.string.text_fan_control_low_speed, "ic_fan_quiet_mode", mode)
    FanControl.SMART -> DialogOption(R.string.text_fan_control_smart, "ic_fan_smart_mode", mode)
    FanControl.SPORT -> DialogOption(R.string.text_fan_control_medium_speed, "ic_fan_sport_mode", mode)
    else -> DialogOption(R.string.text_fan_control_custom, "ic_fan_custom_mode", mode)
}
