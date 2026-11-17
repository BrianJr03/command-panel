package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.BdsaPresence

/**
 * Shown at the top of every companion section while Better Dual Screen Assistant controls the hardware: explains
 * that Command Panel is standing by, and offers to open BDSA, disable it (two presses), or re-check.
 */
@Composable
internal fun BdsaStandbyBanner(state: SettingsState, vm: SettingsViewModel) {
    val name = stringResource(R.string.bdsa_name)
    var confirmDisable by remember(state.bdsa.packageName) { mutableStateOf(false) }
    Card {
        CardTitle(stringResource(R.string.bdsa_active_title, name))
        CardDesc(stringResource(R.string.bdsa_active_desc, name))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton(stringResource(R.string.bdsa_open), onClick = vm::openBdsa)
            // Two presses, so a stray A can't disable another app.
            Chip(stringResource(if (confirmDisable) R.string.bdsa_disable_confirm else R.string.bdsa_disable), selected = confirmDisable) {
                if (confirmDisable) vm.disableBdsa() else confirmDisable = true
            }
            Chip(stringResource(R.string.bdsa_check_again)) { vm.refreshBdsa() }
        }
        state.bdsaMessage?.let { InfoLine(it) }
    }
}

/** In General while BDSA is installed but not in control: says Command Panel is in charge, and offers re-enabling a BDSA it disabled. */
@Composable
internal fun BdsaInactiveNote(state: SettingsState, vm: SettingsViewModel) {
    if (state.bdsa.presence != BdsaPresence.INSTALLED_INACTIVE) return
    val name = stringResource(R.string.bdsa_name)
    Card {
        CardDesc(stringResource(if (state.bdsaDisabledByUs != null) R.string.bdsa_disabled_by_us_desc else R.string.bdsa_inactive_desc, name))
        if (state.bdsaDisabledByUs != null) {
            Spacer(Modifier.height(4.dp))
            Chip(stringResource(R.string.bdsa_reenable), onClick = vm::reenableBdsa)
        }
        state.bdsaMessage?.let { InfoLine(it) }
    }
}
