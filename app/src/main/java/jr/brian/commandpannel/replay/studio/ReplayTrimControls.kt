package jr.brian.commandpannel.replay.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.settings.LocalCompanionPalette

/** Where trim mode is: setting the cut, playing it back, saving the copy, or offering to share the saved copy. */
internal enum class TrimPhase { EDIT, PREVIEW, SAVING, SAVED }

/**
 * What the player's trim mode shows (#51). [savedName] and [earlyMs] describe the copy once [phase] is
 * [TrimPhase.SAVED]: its file name, and how far before the in point it starts (snapped to a keyframe).
 */
internal data class TrimUi(
    val points: TrimPoints = TrimPoints(),
    val phase: TrimPhase = TrimPhase.EDIT,
    val savedName: String? = null,
    val earlyMs: Long = 0L,
)

/** Smallest keyframe snap worth telling the user about. */
private const val EARLY_NOTE_MIN_MS = 100L

/** The line above the progress bar in trim mode: the cut, or the saved copy and the Share offer. */
@Composable
internal fun TrimInfo(state: ReplayPlayerState) {
    val trim = state.trim ?: return
    val palette = LocalCompanionPalette.current
    Column {
        Spacer(Modifier.height(6.dp))
        when (trim.phase) {
            TrimPhase.SAVED -> {
                Text(
                    stringResource(R.string.player_trim_saved, trim.savedName.orEmpty()),
                    color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                if (trim.earlyMs >= EARLY_NOTE_MIN_MS) {
                    Text(
                        stringResource(R.string.player_trim_saved_early, trim.earlyMs / 1000f),
                        color = palette.textSecondary, fontSize = 13.sp,
                    )
                }
                Text(stringResource(R.string.player_trim_saved_offer), color = palette.textPrimary, fontSize = 14.sp)
            }
            else -> {
                val duration = state.durationMs
                val outMs = trim.points.out(duration).takeIf { it != TrimPoints.END } ?: duration
                val status = when (trim.phase) {
                    TrimPhase.PREVIEW -> stringResource(R.string.player_trim_previewing)
                    TrimPhase.SAVING -> stringResource(R.string.player_trim_saving)
                    else -> stringResource(R.string.player_trim_title)
                }
                Text(status, color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.player_trim_span,
                        clock(trim.points.inMs),
                        clock(outMs),
                        clock((outMs - trim.points.inMs).coerceAtLeast(0L)),
                    ),
                    color = palette.textPrimary, fontSize = 14.sp,
                )
                Text(stringResource(R.string.player_trim_keyframe_note), color = palette.textSecondary, fontSize = 13.sp)
            }
        }
    }
}

/** Trim mode's key hints, in place of the player's own. Nothing shows while the copy saves. */
@Composable
internal fun RowScope.TrimControls(state: ReplayPlayerState) {
    val trim = state.trim ?: return
    when (trim.phase) {
        TrimPhase.SAVING -> Unit
        TrimPhase.SAVED -> {
            GlyphHint(GamepadButton.A, stringResource(R.string.player_hint_share))
            GlyphHint(GamepadButton.B, stringResource(R.string.player_hint_done))
        }
        TrimPhase.EDIT, TrimPhase.PREVIEW -> {
            GlyphHint(GamepadButton.A, stringResource(if (state.playing) R.string.player_hint_pause else R.string.player_hint_play))
            KeyHint(stringResource(R.string.player_key_dpad), stringResource(R.string.player_hint_seek_fine))
            KeyHint(stringResource(R.string.player_key_l1), stringResource(R.string.player_hint_set_in))
            KeyHint(stringResource(R.string.player_key_r1), stringResource(R.string.player_hint_set_out))
            GlyphHint(GamepadButton.X, stringResource(R.string.player_hint_preview))
            GlyphHint(GamepadButton.Y, stringResource(R.string.player_hint_save_copy))
            GlyphHint(GamepadButton.B, stringResource(R.string.player_hint_done))
        }
    }
}

/** The normal player's hint for entering trim mode. */
@Composable
internal fun RowScope.TrimEntryHint(state: ReplayPlayerState) {
    if (state.trim == null && state.error == null) GlyphHint(GamepadButton.Y, stringResource(R.string.player_hint_trim))
}
