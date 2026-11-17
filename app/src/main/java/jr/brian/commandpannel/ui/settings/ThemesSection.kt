package jr.brian.commandpannel.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import jr.brian.commandpannel.system.FolderPack
import jr.brian.commandpannel.system.FontNeeds
import jr.brian.commandpannel.system.ThemeFolder
import jr.brian.commandpannel.system.ThemeSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle

/**
 * Companion "Themes" section: the active look with a live preview, the user's saved looks, and the built-in looks.
 * Mixing and editing open [ThemeStudioScreen] through [onOpenStudio].
 */
@Composable
internal fun Themes(state: SettingsState, vm: SettingsViewModel, onOpenStudio: (CustomLook) -> Unit) {
    val activeName = state.overlayCustomLook?.name ?: stringResource(state.overlayLook.labelRes)
    val newName = stringResource(R.string.theme_new_look_name, state.customLooks.size + 1)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlayLookPreview(state, state.overlayLook, state.overlayCustomLook, width = 250.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lookLabel(stringResource(R.string.theme_active)).uppercase(), color = p.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(activeName, color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = tccDisplayTextStyle())
                state.overlayCustomLook?.let {
                    InfoLine(stringResource(R.string.theme_based_on, stringResource(it.baseLook.labelRes)))
                }
                Spacer(Modifier.height(6.dp))
                AccentButton(stringResource(R.string.theme_customize)) {
                    // Editing a saved look edits it in place; customizing a built-in starts a new look on top of it.
                    onOpenStudio(state.overlayCustomLook ?: newCustomLook(newName, state.overlayLook))
                }
            }
        }
    }
    FocusCard(onActivate = { onOpenStudio(newCustomLook(newName, TccUiLook.AUTO)) }) {
        CardTitle(stringResource(R.string.theme_mix))
        CardDesc(stringResource(R.string.theme_mix_desc))
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importLook) }
    LaunchedEffect(Unit) { vm.refreshThemeFolder() }

    state.pendingFonts?.let { FontDownloadCard(it, state.themeBusy, vm) }

    SectionHeader(stringResource(R.string.theme_my_looks))
    Card {
        CardDesc(stringResource(R.string.theme_share_desc, ThemeFolder.THEMES))
        Spacer(Modifier.height(4.dp))
        val allName = stringResource(R.string.theme_export_all_name)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.customLooks.isNotEmpty()) {
                Chip(stringResource(R.string.theme_export_all)) { vm.exportLooks(state.customLooks.filter { it.source == null }, allName) }
            }
            Chip(stringResource(R.string.theme_import)) { importer.launch(LOOK_MIME_TYPES) }
        }
        state.themeMessage?.let { InfoLine(it) }
    }
    if (state.customLooks.isEmpty()) {
        Card { CardDesc(stringResource(R.string.theme_my_looks_empty)) }
    }
    state.customLooks.forEach { look ->
        SavedLookCard(look, state, vm, onOpenStudio) { vm.exportLooks(listOf(look), look.name) }
    }

    SectionHeader(stringResource(R.string.theme_packs))
    Card {
        CardDesc(stringResource(if (state.themeFolderReady) R.string.theme_packs_desc else R.string.theme_packs_unavailable, ThemeFolder.ROOT))
        if (state.themeFolderReady && state.folderPacks.isEmpty()) InfoLine(stringResource(R.string.theme_packs_empty))
        Chip(stringResource(R.string.theme_packs_refresh)) { vm.refreshThemeFolder(checkUpdates = false) }
    }
    state.folderPacks.forEach { fp -> FolderPackCard(fp, state, vm, onOpenStudio) }

    SectionHeader(stringResource(R.string.theme_repos))
    Card {
        CardDesc(stringResource(R.string.theme_repos_desc))
        var link by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val follow = { if (link.isNotBlank() && !state.themeBusy) { vm.addSource(link); link = "" } }
            LinkField(link, Modifier.weight(1f), onSubmit = follow) { link = it }
            Chip(stringResource(R.string.theme_repo_add), enabled = link.isNotBlank() && !state.themeBusy, onClick = follow)
        }
        state.repoMessage?.let { InfoLine(it) }
    }
    state.themeSources.forEach { source -> SourceCard(source, state, vm) }

    SectionHeader(stringResource(R.string.theme_builtin))
    Card {
        CardDesc(stringResource(R.string.settings_overlay_look_desc))
        Spacer(Modifier.height(6.dp))
        sortedLooks().forEach { look ->
            RadioRow(stringResource(look.labelRes), state.overlayLookRef == LookRef.BuiltIn(look)) { vm.setLook(look) }
        }
    }
}

@Composable
private fun SavedLookCard(
    look: CustomLook,
    state: SettingsState,
    vm: SettingsViewModel,
    onOpenStudio: (CustomLook) -> Unit,
    onExport: () -> Unit,
) {
    val active = state.overlayLookRef == look.ref
    var confirmDelete by remember(look.id) { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlayLookPreview(state, look.baseLook, look, width = 150.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardTitle(look.name)
                InfoLine(stringResource(R.string.theme_based_on, stringResource(look.baseLook.labelRes)))
                look.source?.let { id ->
                    InfoLine(stringResource(R.string.theme_from_repo, state.themeSources.firstOrNull { it.id == id }?.name ?: id), p.accent)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(if (active) R.string.theme_applied else R.string.theme_apply), selected = active) {
                        vm.selectLook(look.ref)
                    }
                    Chip(stringResource(R.string.theme_edit)) { onOpenStudio(look) }
                    Chip(stringResource(R.string.theme_export), onClick = onExport)
                    // Two presses, so a stray A on the gamepad can't wipe a look.
                    Chip(stringResource(if (confirmDelete) R.string.theme_delete_confirm else R.string.theme_delete), selected = confirmDelete) {
                        if (confirmDelete) vm.deleteCustomLook(look.id) else confirmDelete = true
                    }
                }
            }
        }
    }
}

private val LOOK_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Offers to download the fonts a pack lists (name, host and license) instead of falling back to the default font. */
@Composable
private fun FontDownloadCard(needs: FontNeeds, busy: Boolean, vm: SettingsViewModel) {
    Card {
        CardTitle(stringResource(R.string.theme_fonts_download_title))
        CardDesc(stringResource(R.string.theme_fonts_download_desc))
        needs.downloadable.forEach { (file, src) ->
            val host = runCatching { java.net.URI(src.url).host }.getOrNull().orEmpty()
            InfoLine(stringResource(R.string.theme_fonts_download_item, file, host, src.license ?: stringResource(R.string.theme_license_unknown)))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton(stringResource(R.string.theme_fonts_download), enabled = !busy, onClick = vm::downloadPendingFonts)
            Chip(stringResource(R.string.theme_fonts_skip), onClick = vm::dismissPendingFonts)
        }
    }
}

/** A pack in the TCP Themes folder: its info, and its looks (with previews) once expanded. */
@Composable
private fun FolderPackCard(fp: FolderPack, state: SettingsState, vm: SettingsViewModel, onOpenStudio: (CustomLook) -> Unit) {
    val pack = fp.pack
    var expanded by remember(fp.path) { mutableStateOf(false) }
    Card {
        CardTitle(pack?.name ?: fp.path.substringAfterLast('/'))
        if (pack == null) {
            InfoLine(stringResource(R.string.theme_pack_unreadable), p.danger)
            return@Card
        }
        InfoLine(listOfNotNull(pack.author, pluralStringResource(R.plurals.theme_pack_look_count, pack.looks.size, pack.looks.size)).joinToString(" · "))
        pack.description?.let { CardDesc(it) }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(if (expanded) R.string.theme_pack_hide else R.string.theme_pack_show)) { expanded = !expanded }
            Chip(stringResource(R.string.theme_pack_add_all)) { vm.addPackLooks(pack) }
        }
        if (expanded) pack.looks.forEach { look ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OverlayLookPreview(state, look.baseLook, look, width = 150.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(look.name, color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.theme_pack_add)) { vm.addPackLooks(pack, listOf(look)) }
                        Chip(stringResource(R.string.theme_pack_start_from)) { vm.startFromPackLook(pack, look, onOpenStudio) }
                    }
                }
            }
        }
    }
}

/** A followed theme repo: its link, last sync, and update / remove. */
@Composable
private fun SourceCard(source: ThemeSource, state: SettingsState, vm: SettingsViewModel) {
    var confirmRemove by remember(source.id) { mutableStateOf(false) }
    val count = state.customLooks.count { it.source == source.id }
    Card {
        CardTitle(source.name)
        InfoLine(source.url)
        InfoLine(
            if (source.lastSync == 0L) stringResource(R.string.theme_repo_never_synced)
            else stringResource(
                R.string.theme_repo_synced,
                android.text.format.DateUtils.getRelativeTimeSpanString(source.lastSync).toString(),
                pluralStringResource(R.plurals.theme_pack_look_count, count, count),
            ),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.theme_repo_update), enabled = !state.themeBusy) { vm.syncSource(source.id) }
            Chip(stringResource(if (confirmRemove) R.string.theme_repo_remove_confirm else R.string.theme_repo_remove), selected = confirmRemove) {
                if (confirmRemove) vm.removeSource(source.id) else confirmRemove = true
            }
        }
    }
}

/** Single-line field for pasting a theme repo link (opens the keyboard when focused). */
@Composable
private fun LinkField(value: String, modifier: Modifier, onSubmit: () -> Unit, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = controlShape(10.dp)
    Box(
        modifier.clip(shape)
            .background(if (focused) p.surfaceHigh else p.surfaceHigh.copy(alpha = 0.5f))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) p.accent else p.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) Text(stringResource(R.string.theme_repo_hint), color = p.textTertiary, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(300)) },
            singleLine = true,
            textStyle = TextStyle(color = p.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            // The keyboard's Go key follows the link (taps outside the field don't land while the keyboard is up).
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}
