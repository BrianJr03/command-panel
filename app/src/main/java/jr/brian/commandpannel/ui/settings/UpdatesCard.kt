package jr.brian.commandpannel.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.R

/** General → Updates: version, last check, "Check for updates" and "What's new" (plus a dialog preview on debug builds). */
@Composable
internal fun UpdatesCard(state: SettingsState, vm: SettingsViewModel) {
    Card {
        CardTitle(stringResource(R.string.settings_update_title))
        val version = BuildConfig.VERSION_NAME.substringBefore('-')
        val checked = when {
            state.updateLastCheck == 0L -> stringResource(R.string.settings_update_never_checked)
            System.currentTimeMillis() - state.updateLastCheck < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.settings_update_checked_now)
            else -> stringResource(R.string.settings_update_last_checked, DateUtils.getRelativeTimeSpanString(state.updateLastCheck).toString())
        }
        CardDesc(stringResource(R.string.settings_update_desc, version, checked))
        if (BuildConfig.DEBUG) InfoLine(stringResource(R.string.settings_update_debug_note))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Stays enabled while checking (a disabled button drops gamepad focus); checkForUpdates ignores repeats.
            AccentButton(
                stringResource(if (state.updateChecking) R.string.settings_update_checking else R.string.settings_update_check),
            ) { vm.checkForUpdates(manual = true) }
            Chip(stringResource(R.string.settings_update_whats_new), onClick = vm::showWhatsNew)
            if (BuildConfig.DEBUG) Chip(stringResource(R.string.settings_update_preview), onClick = vm::previewUpdate)
        }
        state.updateMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
}
