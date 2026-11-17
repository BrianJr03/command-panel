package jr.brian.commandpannel.ui.settings

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

    SectionHeader(stringResource(R.string.theme_my_looks))
    if (state.customLooks.isEmpty()) {
        Card { CardDesc(stringResource(R.string.theme_my_looks_empty)) }
    }
    state.customLooks.forEach { look -> SavedLookCard(look, state, vm, onOpenStudio) }

    SectionHeader(stringResource(R.string.theme_builtin))
    Card {
        CardDesc(stringResource(R.string.settings_overlay_look_desc))
        Spacer(Modifier.height(6.dp))
        TccUiLook.entries.forEach { look ->
            RadioRow(stringResource(look.labelRes), state.overlayLookRef == LookRef.BuiltIn(look)) { vm.setLook(look) }
        }
    }
}

@Composable
private fun SavedLookCard(look: CustomLook, state: SettingsState, vm: SettingsViewModel, onOpenStudio: (CustomLook) -> Unit) {
    val active = state.overlayLookRef == look.ref
    var confirmDelete by remember(look.id) { mutableStateOf(false) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlayLookPreview(state, look.baseLook, look, width = 150.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CardTitle(look.name)
                InfoLine(stringResource(R.string.theme_based_on, stringResource(look.baseLook.labelRes)))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(if (active) R.string.theme_applied else R.string.theme_apply), selected = active) {
                        vm.selectLook(look.ref)
                    }
                    Chip(stringResource(R.string.theme_edit)) { onOpenStudio(look) }
                    // Two presses, so a stray A on the gamepad can't wipe a look.
                    Chip(stringResource(if (confirmDelete) R.string.theme_delete_confirm else R.string.theme_delete), selected = confirmDelete) {
                        if (confirmDelete) vm.deleteCustomLook(look.id) else confirmDelete = true
                    }
                }
            }
        }
    }
}
