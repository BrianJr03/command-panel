package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Companion settings screen on the top display. A modern two-pane console: an icon nav rail on the left and the
 * selected category's cards on the right. The companion chrome uses its own [Palette] (decoupled from the overlay
 * theme) so it always reads as a polished dark UI.
 */

/** Companion design tokens — a calm, modern dark palette with a vivid violet accent. */
private object Palette {
    val Bg = Color(0xFF0D0E12)
    val Rail = Color(0xFF0A0B0E)
    val Surface = Color(0xFF16181F)
    val SurfaceHigh = Color(0xFF20232C)
    val Border = Color(0xFF262A34)
    val Accent = Color(0xFF7C6FFF)
    val AccentSoft = Color(0x1F7C6FFF)
    val TextPrimary = Color(0xFFF1F2F6)
    val TextSecondary = Color(0xFF9AA1B2)
    val TextTertiary = Color(0xFF666D7E)
    val Danger = Color(0xFFF26069)
    val Success = Color(0xFF53D483)
}

private enum class NavItem(val category: SettingsCategory, val icon: ImageVector, val subtitle: String) {
    GENERAL(SettingsCategory.GENERAL, Icons.Rounded.Tune, "Core behaviour and companion features"),
    QUICK_TOGGLES(SettingsCategory.QUICK_TOGGLES, Icons.Rounded.ViewAgenda, "Pick and order the overlay's quick tiles"),
    APPEARANCE(SettingsCategory.APPEARANCE, Icons.Rounded.Palette, "Theme and look of the bottom-screen overlay"),
    DIAGNOSTICS(SettingsCategory.DIAGNOSTICS, Icons.Rounded.MonitorHeart, "Check the privileged access layer"),
}

@Composable
fun SettingsScreen(state: SettingsState, vm: SettingsViewModel) {
    var nav by remember { mutableStateOf(NavItem.GENERAL) }
    Row(Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding()) {
        NavRail(nav) { nav = it }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(nav.category.title, color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(nav.subtitle, color = Palette.TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            when (nav.category) {
                SettingsCategory.GENERAL -> General(state, vm)
                SettingsCategory.QUICK_TOGGLES -> QuickToggles(state, vm)
                SettingsCategory.APPEARANCE -> Appearance(state, vm)
                SettingsCategory.DIAGNOSTICS -> Diagnostics(state, vm)
            }
        }
    }
}

@Composable
private fun NavRail(selected: NavItem, onSelect: (NavItem) -> Unit) {
    Column(
        modifier = Modifier.width(248.dp).fillMaxHeight().background(Palette.Rail).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Palette.Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Command Panel", color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("AYN Thor companion", color = Palette.TextTertiary, fontSize = 11.sp)
            }
        }
        NavItem.entries.forEach { item ->
            val on = item == selected
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(if (on) Palette.AccentSoft else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, tint = if (on) Palette.Accent else Palette.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(13.dp))
                Text(
                    item.category.title,
                    color = if (on) Palette.TextPrimary else Palette.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---- shared modern building blocks ----

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(18.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun SectionHeader(text: String) {
    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 13.dp).clip(CircleShape).background(Palette.Accent))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), color = Palette.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CardTitle(text: String) = Text(text, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun CardDesc(text: String) = Text(text, color = Palette.TextSecondary, fontSize = 13.sp)

@Composable
private fun SwitchCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardTitle(title)
                CardDesc(description)
            }
            Spacer(Modifier.width(16.dp))
            AccentSwitch(checked, onChange)
        }
    }
}

@Composable
private fun AccentSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked = checked,
    onCheckedChange = onChange,
    colors = SwitchDefaults.colors(
        checkedTrackColor = Palette.Accent,
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Palette.SurfaceHigh,
        uncheckedBorderColor = Palette.Border,
        uncheckedThumbColor = Palette.TextTertiary,
    ),
)

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) Palette.SurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (selected) Palette.TextPrimary else Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Palette.Accent, unselectedColor = Palette.TextTertiary),
        )
    }
}

@Composable
private fun AccentButton(text: String, enabled: Boolean = true, onClick: () -> Unit) = Button(
    onClick = onClick,
    enabled = enabled,
    colors = ButtonDefaults.buttonColors(
        containerColor = Palette.Accent,
        contentColor = Color.White,
        disabledContainerColor = Palette.SurfaceHigh,
        disabledContentColor = Palette.TextTertiary,
    ),
    shape = RoundedCornerShape(12.dp),
) { Text(text, fontWeight = FontWeight.SemiBold) }

/** Small pill used for discrete choices (replay window, reorder arrows). */
@Composable
private fun Chip(text: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (selected) Palette.AccentSoft else Palette.SurfaceHigh
    val fg = when {
        !enabled -> Palette.TextTertiary
        selected -> Palette.Accent
        else -> Palette.TextSecondary
    }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg)
            .then(if (selected) Modifier.border(1.dp, Palette.Accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun InfoLine(text: String, tone: Color = Palette.TextSecondary) =
    Text(text, color = tone, fontSize = 12.sp)

// ---- General ----

@Composable
private fun General(state: SettingsState, vm: SettingsViewModel) {
    if (state.appPickerOpen) {
        AppPicker(state, vm)
        return
    }
    SwitchCard(
        "Replace Stock Assistant",
        "Suppress the built-in dual-screen assistant so Command Panel handles the AYN button. Turning this off " +
            "restores the stock assistant.",
        state.replaceStock, vm::setReplaceStock,
    )
    SwitchCard(
        "Battery Estimate",
        "Show the estimated time to empty (or to full / the 80% limit while charging) after the battery percentage " +
            "in the overlay status bar.",
        state.batteryEstimate, vm::setBatteryEstimate,
    )
    SwitchCard(
        "Hide Network Speed",
        "Hide the download-speed readout in the overlay status bar.",
        state.hideNetSpeed, vm::setHideNetSpeed,
    )
    SwitchCard(
        "Keep Apps That Are On Screen",
        "When Clear All Tasks runs, keep apps showing on either screen, their companion screens and the home app. " +
            "Turn off to clear everything except this app, the stock assistant and the system.",
        state.keepOnScreen, vm::setKeepOnScreen,
    )
    QuickLaunchCard(state, vm)
    Card {
        CardTitle("Backup & Restore")
        CardDesc("Export the companion settings to a JSON file, or restore the most recent backup.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentButton("Export", onClick = vm::exportBackup)
            AccentButton("Restore latest", onClick = vm::restoreLatestBackup)
        }
        state.backupMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
    ReplayBufferCard(state, vm)
    Card {
        CardTitle("Overlay preview")
        CardDesc("Open the overlay on the bottom screen to preview your settings.")
        Spacer(Modifier.height(10.dp))
        AccentButton("Open overlay on bottom screen", onClick = vm::showOverlay)
    }
}

@Composable
private fun ReplayBufferCard(state: SettingsState, vm: SettingsViewModel) {
    SwitchCard(
        "Replay Buffer",
        "Continuously keep the last few seconds of the top screen in memory so you can save a clip after something " +
            "happens. Uses root screen capture (no cast prompt).",
        state.replayEnabled, vm::setReplayEnabled,
    )
    Card {
        CardTitle("Save last")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(15, 30, 60, 90).forEach { secs ->
                Chip("${secs}s", selected = state.replayWindowSeconds == secs) { vm.setReplayWindowSeconds(secs) }
            }
        }
        Spacer(Modifier.height(14.dp))
        CardTitle("Quality")
        jr.brian.commandpannel.privileged.ReplayBitratePreset.entries.forEach { preset ->
            RadioRow(preset.label, state.replayPreset == preset) { vm.setReplayPreset(preset) }
        }
        Spacer(Modifier.height(12.dp))
        AccentButton("Save clip now", enabled = state.replayEnabled, onClick = vm::saveReplayClip)
        state.replayMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
}

@Composable
private fun QuickLaunchCard(state: SettingsState, vm: SettingsViewModel) {
    Card {
        CardTitle("Quick Launch")
        CardDesc("Add a Launch button to the overlay nav bar that opens a chosen app on a chosen screen.")
        Spacer(Modifier.height(10.dp))
        Text("App: ${state.quickLaunchLabel ?: "None"}", color = Palette.TextPrimary, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentButton("Choose app", onClick = vm::openAppPicker)
            if (state.quickLaunchLabel != null) AccentButton("Clear", onClick = vm::clearQuickLaunch)
        }
        if (state.quickLaunchLabel != null) {
            Spacer(Modifier.height(10.dp))
            CardDesc("Open on:")
            jr.brian.commandpannel.system.QuickLaunchDisplay.entries.forEach { d ->
                RadioRow(d.label, state.quickLaunchDisplay == d) { vm.setQuickLaunchDisplay(d) }
            }
        }
    }
}

@Composable
private fun AppPicker(state: SettingsState, vm: SettingsViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Choose an app", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        AccentButton("Back", onClick = vm::closeAppPicker)
    }
    Spacer(Modifier.height(6.dp))
    if (state.apps.isEmpty()) {
        CardDesc("Loading…")
    } else {
        Card {
            state.apps.forEach { app ->
                Text(
                    app.label, color = Palette.TextPrimary, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { vm.pickApp(app.component) }.padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ---- Quick Toggles ----

@Composable
private fun QuickToggles(state: SettingsState, vm: SettingsViewModel) {
    SectionHeader("Order")
    Card {
        state.orderedEnabledTiles.forEachIndexed { index, key ->
            val tile = jr.brian.commandpannel.system.QuickSettingTileId.fromKey(key) ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", color = Palette.TextTertiary, fontSize = 13.sp, modifier = Modifier.width(26.dp))
                Text(tile.label, color = Palette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Chip("▲", enabled = index > 0) { vm.moveTile(key, up = true) }
                Spacer(Modifier.width(8.dp))
                Chip("▼", enabled = index < state.orderedEnabledTiles.lastIndex) { vm.moveTile(key, up = false) }
            }
        }
    }
    jr.brian.commandpannel.system.TileCategory.entries.forEach { cat ->
        SectionHeader(cat.title)
        Card {
            jr.brian.commandpannel.system.QuickSettingTileId.entries.filter { it.category == cat }.forEach { tile ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tile.label, color = Palette.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    AccentSwitch(tile.key in state.enabledTiles) { vm.setTileEnabled(tile.key, it) }
                }
            }
        }
    }
}

// ---- Appearance ----

@Composable
private fun Appearance(state: SettingsState, vm: SettingsViewModel) {
    Card {
        CardTitle("Overlay theme")
        Spacer(Modifier.height(6.dp))
        listOf(1 to "Follow system", 3 to "Light", 4 to "Dark").forEach { (mode, label) ->
            RadioRow(label, state.themeMode == mode) { vm.setThemeMode(mode) }
        }
    }
    SectionHeader("Overlay look")
    Card {
        CardDesc("The surface style for the bottom-screen overlay. Auto uses the stock AYN look.")
        Spacer(Modifier.height(6.dp))
        jr.brian.commandpannel.ui.theme.TccUiLook.entries.forEach { look ->
            RadioRow(look.label, state.overlayLook == look) { vm.setLook(look) }
        }
    }
    SwitchCard(
        "Sync Stick LEDs to Theme",
        "Mirror the overlay theme's accent colour onto the AYN Thor's joystick LEDs.",
        state.rgbAccentSync, vm::setRgbAccentSync,
    )
}

// ---- Diagnostics ----

@Composable
private fun Diagnostics(state: SettingsState, vm: SettingsViewModel) {
    Card {
        CardTitle("Privileged layer")
        CardDesc("Verify hidden-API access, the PServerBinder root shell and the root helper.")
        Spacer(Modifier.height(10.dp))
        AccentButton(if (state.diagnosticsRunning) "Running…" else "Run check", enabled = !state.diagnosticsRunning, onClick = vm::runDiagnostics)
        if (state.diagnostics.isNotEmpty()) Spacer(Modifier.height(12.dp))
        state.diagnostics.forEach { (text, ok) ->
            val (mark, tone) = when (ok) {
                true -> "PASS" to Palette.Success
                false -> "FAIL" to Palette.Danger
                else -> "INFO" to Palette.TextTertiary
            }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mark, color = tone, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp))
                Text(text, color = Palette.TextPrimary, fontSize = 13.sp)
            }
        }
    }
}
