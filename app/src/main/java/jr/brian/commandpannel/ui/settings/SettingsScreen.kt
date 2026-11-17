package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccTheme

/**
 * Companion settings screen on the top display (the stock app has none). Two-pane: a category rail on the left and
 * the selected category's content on the right. Hosts stock-coexistence, appearance and diagnostics now; the fork
 * features' settings are added here in phase 7.
 */
@Composable
fun SettingsScreen(state: SettingsState, vm: SettingsViewModel) {
    val colors = if (state.themeMode == 3) TccColors.Light else TccColors.PureBlack
    TccTheme(colors) {
        val c = LocalTccColors.current
        var category by remember { mutableStateOf(SettingsCategory.GENERAL) }
        Row(
            Modifier.fillMaxSize().background(c.primaryBackground).systemBarsPadding(),
        ) {
            Column(
                modifier = Modifier.width(200.dp).fillMaxHeight().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Command Panel", color = c.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SettingsCategory.entries.forEach { cat ->
                    Text(
                        cat.title,
                        color = if (cat == category) c.primary else c.secondary,
                        fontSize = 16.sp,
                        fontWeight = if (cat == category) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (cat == category) c.panelBackgroundSelect else c.primaryBackground)
                            .clickable { category = cat }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (category) {
                    SettingsCategory.GENERAL -> General(state, vm)
                    SettingsCategory.QUICK_TOGGLES -> QuickToggles(state, vm)
                    SettingsCategory.APPEARANCE -> Appearance(state, vm)
                    SettingsCategory.DIAGNOSTICS -> Diagnostics(state, vm)
                }
            }
        }
    }
}

@Composable
private fun General(state: SettingsState, vm: SettingsViewModel) {
    if (state.appPickerOpen) {
        AppPicker(state, vm)
        return
    }
    SectionHeader("General")
    SwitchCard(
        title = "Replace Stock Assistant",
        description = "Suppress the built-in dual-screen assistant so Command Panel handles the AYN button. " +
            "Turning this off restores the stock assistant.",
        checked = state.replaceStock,
        onChange = vm::setReplaceStock,
    )
    SwitchCard(
        title = "Battery Estimate",
        description = "Show the estimated time to empty (or to full / the 80% limit while charging) after the " +
            "battery percentage in the overlay status bar.",
        checked = state.batteryEstimate,
        onChange = vm::setBatteryEstimate,
    )
    SwitchCard(
        title = "Hide Network Speed",
        description = "Hide the download-speed readout in the overlay status bar.",
        checked = state.hideNetSpeed,
        onChange = vm::setHideNetSpeed,
    )
    SwitchCard(
        title = "Keep Apps That Are On Screen",
        description = "When Clear All Tasks runs, keep apps showing on either screen, their companion screens " +
            "and the home app. Turn off to clear everything except this app, the stock assistant and the system.",
        checked = state.keepOnScreen,
        onChange = vm::setKeepOnScreen,
    )
    QuickLaunchCard(state, vm)
    Card {
        val c = LocalTccColors.current
        Text("Backup & Restore", color = c.primary, fontSize = 16.sp)
        Text("Export the companion settings to a JSON file, or restore the most recent backup.",
            color = c.secondary, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::exportBackup) { Text("Export") }
            Button(onClick = vm::restoreLatestBackup) { Text("Restore latest") }
        }
        state.backupMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.secondary, fontSize = 12.sp)
        }
    }
    ReplayBufferCard(state, vm)
    Card {
        Text("Overlay preview", color = LocalTccColors.current.primary, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::showOverlay) { Text("Open overlay on bottom screen") }
    }
}

@Composable
private fun ReplayBufferCard(state: SettingsState, vm: SettingsViewModel) {
    val c = LocalTccColors.current
    SwitchCard(
        title = "Replay Buffer",
        description = "Continuously keep the last few seconds of the top screen in memory so you can save a clip " +
            "after something happens. Uses root screen capture (no cast prompt).",
        checked = state.replayEnabled,
        onChange = vm::setReplayEnabled,
    )
    Card {
        Text("Save last", color = c.primary, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 90).forEach { secs ->
                val on = state.replayWindowSeconds == secs
                TextButton(onClick = { vm.setReplayWindowSeconds(secs) }) {
                    Text("${secs}s", color = if (on) c.accent else c.secondary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Quality", color = c.primary, fontSize = 15.sp)
        jr.brian.commandpannel.privileged.ReplayBitratePreset.entries.forEach { preset ->
            RadioRow(preset.label, state.replayPreset == preset) { vm.setReplayPreset(preset) }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::saveReplayClip, enabled = state.replayEnabled) { Text("Save clip now") }
        state.replayMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = c.secondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuickLaunchCard(state: SettingsState, vm: SettingsViewModel) {
    val c = LocalTccColors.current
    Card {
        Text("Quick Launch", color = c.primary, fontSize = 16.sp)
        Text(
            "Add a Launch button to the overlay nav bar that opens a chosen app on a chosen screen.",
            color = c.secondary, fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("App: ${state.quickLaunchLabel ?: "None"}", color = c.primary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::openAppPicker) { Text("Choose app") }
            if (state.quickLaunchLabel != null) {
                Button(onClick = vm::clearQuickLaunch) { Text("Clear") }
            }
        }
        if (state.quickLaunchLabel != null) {
            Spacer(Modifier.height(8.dp))
            Text("Open on:", color = c.secondary, fontSize = 13.sp)
            jr.brian.commandpannel.system.QuickLaunchDisplay.entries.forEach { d ->
                RadioRow(d.label, state.quickLaunchDisplay == d) { vm.setQuickLaunchDisplay(d) }
            }
        }
    }
}

@Composable
private fun AppPicker(state: SettingsState, vm: SettingsViewModel) {
    val c = LocalTccColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Choose an app", color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Button(onClick = vm::closeAppPicker) { Text("Back") }
    }
    if (state.apps.isEmpty()) {
        Text("Loading…", color = c.secondary, fontSize = 14.sp)
    } else {
        state.apps.forEach { app ->
            Text(
                app.label,
                color = c.primary,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.pickApp(app.component) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun QuickToggles(state: SettingsState, vm: SettingsViewModel) {
    val c = LocalTccColors.current
    Text(
        "Choose which quick-setting tiles appear in the overlay and in what order. Stock tiles are on by default; " +
            "Android System and Tools tiles are extras.",
        color = c.secondary, fontSize = 13.sp,
    )
    SectionHeader("Order")
    Card {
        state.orderedEnabledTiles.forEachIndexed { index, key ->
            val tile = jr.brian.commandpannel.system.QuickSettingTileId.fromKey(key) ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}. ${tile.label}", color = c.primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(enabled = index > 0, onClick = { vm.moveTile(key, up = true) }) { Text("▲") }
                TextButton(
                    enabled = index < state.orderedEnabledTiles.lastIndex,
                    onClick = { vm.moveTile(key, up = false) },
                ) { Text("▼") }
            }
        }
    }
    jr.brian.commandpannel.system.TileCategory.entries.forEach { cat ->
        SectionHeader(cat.title)
        Card {
            jr.brian.commandpannel.system.QuickSettingTileId.entries.filter { it.category == cat }.forEach { tile ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tile.label, color = c.primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = tile.key in state.enabledTiles,
                        onCheckedChange = { vm.setTileEnabled(tile.key, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun Appearance(state: SettingsState, vm: SettingsViewModel) {
    SectionHeader("Appearance")
    Card {
        Text("Overlay theme", color = LocalTccColors.current.primary, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        listOf(1 to "Follow system", 3 to "Light", 4 to "Dark").forEach { (mode, label) ->
            RadioRow(label, state.themeMode == mode) { vm.setThemeMode(mode) }
        }
    }

    SectionHeader("Overlay look")
    Card {
        Text(
            "The surface style for the bottom-screen overlay. Auto uses the stock AYN look.",
            color = LocalTccColors.current.secondary, fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        jr.brian.commandpannel.ui.theme.TccUiLook.entries.forEach { look ->
            RadioRow(look.label, state.overlayLook == look) { vm.setLook(look) }
        }
    }
    SwitchCard(
        title = "Sync Stick LEDs to Theme",
        description = "Mirror the overlay theme's accent colour onto the AYN Thor's joystick LEDs.",
        checked = state.rgbAccentSync,
        onChange = vm::setRgbAccentSync,
    )

    SectionHeader("Overlay arrangement")
    val l = state.overlayLayout
    SwitchCard(
        title = "Nav bar at top",
        description = "Place the Mode / Launch / Settings bar directly under the status bar instead of at the bottom.",
        checked = l.navBarPosition == jr.brian.commandpannel.system.NavBarPosition.TOP,
        onChange = vm::setNavBarTop,
    )
    SwitchCard(
        title = "Sliders above mode panel",
        description = "Mode tab: put the brightness/volume cards above the dual-screen mode buttons.",
        checked = l.modeRowsSwapped,
        onChange = { vm.toggleModeRows() },
    )
    SwitchCard(
        title = "Swap brightness / volume",
        description = "Mode tab: place the volume card on the left and brightness on the right.",
        checked = l.modeHalfOrder.first() == jr.brian.commandpannel.system.OverlayCardId.VOLUME,
        onChange = { vm.toggleModeHalves() },
    )
    SwitchCard(
        title = "Hardware row on top",
        description = "Settings tab: put the temp/fan/rings row above the frame-rate and quick-tile cards.",
        checked = l.settingsRowsSwapped,
        onChange = { vm.toggleSettingsRows() },
    )
    SwitchCard(
        title = "Swap settings columns",
        description = "Settings tab: put the wide cards (quick tiles, rings) on the left.",
        checked = l.settingsColsSwapped,
        onChange = { vm.toggleSettingsCols() },
    )
    SwitchCard(
        title = "Swap temp / fan",
        description = "Settings tab: place the fan tile before the temperature tile.",
        checked = l.settingsQuarterOrder.first() == jr.brian.commandpannel.system.OverlayCardId.FAN,
        onChange = { vm.toggleQuarters() },
    )
    Card {
        Button(onClick = vm::resetLayout, enabled = !l.isDefault) { Text("Reset arrangement") }
    }
}

@Composable
private fun Diagnostics(state: SettingsState, vm: SettingsViewModel) {
    SectionHeader("Diagnostics")
    Card {
        Button(onClick = vm::runDiagnostics, enabled = !state.diagnosticsRunning) {
            Text(if (state.diagnosticsRunning) "Running…" else "Run privileged-layer check")
        }
        Spacer(Modifier.height(8.dp))
        state.diagnostics.forEach { (text, ok) ->
            val mark = if (ok == true) "[PASS] " else if (ok == false) "[FAIL] " else "[info] "
            Text("$mark$text", color = LocalTccColors.current.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), color = LocalTccColors.current.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = LocalTccColors.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.panelBackground).padding(16.dp),
    ) { content() }
}

@Composable
private fun SwitchCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalTccColors.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = c.primary, fontSize = 16.sp)
                Text(description, color = c.secondary, fontSize = 13.sp)
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent),
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalTccColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = c.primary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (selected) "●" else "○", color = if (selected) c.accent else c.secondary, fontSize = 16.sp)
    }
}
