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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
        Row(Modifier.fillMaxSize().background(c.primaryBackground)) {
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
    QuickLaunchCard(state, vm)
    Card {
        Text("Overlay preview", color = LocalTccColors.current.primary, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::showOverlay) { Text("Open overlay on bottom screen") }
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
private fun Appearance(state: SettingsState, vm: SettingsViewModel) {
    SectionHeader("Appearance")
    Card {
        Text("Overlay theme", color = LocalTccColors.current.primary, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        listOf(1 to "Follow system", 3 to "Light", 4 to "Dark").forEach { (mode, label) ->
            RadioRow(label, state.themeMode == mode) { vm.setThemeMode(mode) }
        }
    }
    SwitchCard(
        title = "Sync Stick LEDs to Theme",
        description = "Mirror the overlay theme's accent colour onto the AYN Thor's joystick LEDs.",
        checked = state.rgbAccentSync,
        onChange = vm::setRgbAccentSync,
    )
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
