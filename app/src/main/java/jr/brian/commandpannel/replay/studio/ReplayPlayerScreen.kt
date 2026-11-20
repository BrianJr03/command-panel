package jr.brian.commandpannel.replay.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.components.GamepadGlyph
import jr.brian.commandpannel.ui.settings.LocalCompanionPalette

/** What the player screen shows. Written by [ReplayPlayerActivity] on the main thread, read by the composables. */
@Stable
internal class ReplayPlayerState {
    var player by mutableStateOf<Player?>(null)
    /** Where the video goes for the current clip (#47); the activity swaps it when the route changes. */
    var output by mutableStateOf<ReplayVideoOutput>(SingleSurfaceOutput)
    var title by mutableStateOf("")
    /** 1-based place in the library, or 0 while unknown. */
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    /** The video's width / height, pixel aspect included. */
    var aspectRatio by mutableFloatStateOf(16f / 9f)
    var error by mutableStateOf<String?>(null)
    var controlsVisible by mutableStateOf(true)
    /** A short message at the top ("No older clips"), cleared by the activity. */
    var notice by mutableStateOf<String?>(null)
    /**
     * A highlighted span of the progress bar, as fractions of the clip (trim in/out points, #51). Null shows none.
     */
    var selection by mutableStateOf<ClosedFloatingPointRange<Float>?>(null)
    /** Trim mode (#51): the cut being set and what it's doing. Null when not trimming. */
    var trim by mutableStateOf<TrimUi?>(null)
}

/**
 * The player: video full screen on black, with controls along the bottom that fade out while playing. Input is handled
 * by the activity (the screen has nothing focusable), so the D-pad never wanders. [extraControls] is room in the
 * control bar for more actions and [extraInfo] a line above the progress bar (trim in/out and Save copy in #51). While
 * [ReplayPlayerState.trim] is set, the bar's own key hints step aside for [extraControls]. [onTap] is a touch on the
 * video.
 */
@Composable
internal fun ReplayPlayerScreen(
    state: ReplayPlayerState,
    output: ReplayVideoOutput,
    onTap: () -> Unit,
    extraInfo: @Composable () -> Unit = {},
    extraControls: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalCompanionPalette.current
    val videoDescription = stringResource(R.string.player_video_description)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        val player = state.player
        if (player != null && state.error == null) {
            output.Content(player, state.aspectRatio, Modifier.fillMaxSize().semantics { contentDescription = videoDescription })
        }
        val error = state.error
        when {
            error != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = palette.textPrimary, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.player_error_hint),
                    color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
            state.loading -> Text(
                stringResource(R.string.player_loading),
                color = palette.textSecondary, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center),
            )
        }
        state.notice?.let { notice ->
            Text(
                notice,
                color = palette.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(palette.surface.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = state.controlsVisible || error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlBar(state, showTimeline = error == null, extraInfo = extraInfo, extraControls = extraControls)
        }
    }
}

@Composable
private fun ControlBar(
    state: ReplayPlayerState,
    showTimeline: Boolean,
    extraInfo: @Composable () -> Unit,
    extraControls: @Composable RowScope.() -> Unit,
) {
    val palette = LocalCompanionPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.count > 0 && state.index > 0) {
                Text(stringResource(R.string.player_counter, state.index, state.count), color = palette.textSecondary, fontSize = 14.sp)
            }
        }
        extraInfo()
        if (showTimeline) {
            Spacer(Modifier.height(10.dp))
            val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            Timeline(fraction, state.selection, palette.accent, palette.border)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (showTimeline) {
                Text(
                    stringResource(R.string.player_time_of, clock(state.positionMs), clock(state.durationMs)),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            extraControls()
            if (state.trim == null) {
                if (showTimeline) {
                    GlyphHint(GamepadButton.A, stringResource(if (state.playing) R.string.player_hint_pause else R.string.player_hint_play))
                    KeyHint(stringResource(R.string.player_key_dpad), stringResource(R.string.player_hint_seek))
                }
                KeyHint(stringResource(R.string.player_key_shoulders), stringResource(R.string.player_hint_clips))
                GlyphHint(GamepadButton.B, stringResource(R.string.player_hint_close))
            }
        }
    }
}

/** The progress bar: played part in [accent], an optional [selection] span drawn over the track. */
@Composable
private fun Timeline(fraction: Float, selection: ClosedFloatingPointRange<Float>?, accent: Color, track: Color) {
    Box(Modifier.fillMaxWidth().height(4.dp).background(track, RoundedCornerShape(2.dp))) {
        selection?.let { span ->
            val start = span.start.coerceIn(0f, 1f)
            val width = (span.endInclusive.coerceIn(0f, 1f) - start).coerceAtLeast(0f)
            Row(Modifier.fillMaxWidth()) {
                if (start > 0f) Spacer(Modifier.weight(start))
                if (width > 0f) Box(Modifier.weight(width).height(4.dp).background(accent.copy(alpha = 0.4f)))
                if (1f - start - width > 0f) Spacer(Modifier.weight(1f - start - width))
            }
        }
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
    }
}

@Composable
internal fun GlyphHint(button: GamepadButton, label: String) {
    val palette = LocalCompanionPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        GamepadGlyph(button, palette.textPrimary)
        Spacer(Modifier.width(6.dp))
        Text(label, color = palette.textPrimary, fontSize = 14.sp)
    }
}

@Composable
internal fun KeyHint(keys: String, label: String) {
    val palette = LocalCompanionPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            keys,
            color = palette.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.5.dp, palette.textPrimary, RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = palette.textPrimary, fontSize = 14.sp)
    }
}

@Composable
internal fun clock(ms: Long): String {
    val (minutes, seconds) = clockParts(ms)
    return stringResource(R.string.player_time, minutes.toInt(), seconds.toInt())
}
