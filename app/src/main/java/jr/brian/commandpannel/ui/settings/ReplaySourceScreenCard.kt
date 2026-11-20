package jr.brian.commandpannel.ui.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.replay.studio.PREF_PLAY_ON_SOURCE_SCREEN
import jr.brian.commandpannel.system.Prefs
import jr.brian.commandpannel.ui.components.noRippleClickable

/**
 * Replay Buffer section: "Play on the recorded screen" (#47), off by default. Reads and writes its pref itself (only
 * Replay Studio's player uses it), so it stays one self-contained card in the section.
 */
@Composable
internal fun ReplaySourceScreenCard() {
    val prefs = Prefs.of(LocalContext.current)
    var on by remember { mutableStateOf(prefs.getBoolean(PREF_PLAY_ON_SOURCE_SCREEN, false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == PREF_PLAY_ON_SOURCE_SCREEN) on = p.getBoolean(PREF_PLAY_ON_SOURCE_SCREEN, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val set: (Boolean) -> Unit = { value ->
        on = value
        prefs.edit().putBoolean(PREF_PLAY_ON_SOURCE_SCREEN, value).apply()
    }
    val isNew = LocalNewBadges.current.state.cardNew(SettingsCategory.REPLAY, NewItems.CARD_REPLAY_SOURCE_SCREEN)
    val title = stringResource(R.string.settings_replay_source_screen)
    FocusCard(
        onActivate = { set(!on) },
        modifier = Modifier
            .newCard(SettingsCategory.REPLAY, NewItems.CARD_REPLAY_SOURCE_SCREEN)
            .newCornerDot(isNew, p.accent, p.bg)
            .noRippleClickable { set(!on) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    CardTitle(title)
                    // Zero-size: the card's corner dot, described for TalkBack.
                    if (isNew) NewBadgeLabel(title)
                }
                CardDesc(stringResource(R.string.settings_replay_source_screen_desc))
            }
            Spacer(Modifier.width(16.dp))
            AccentSwitch(on, set)
        }
    }
}
