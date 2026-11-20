package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import jr.brian.commandpannel.R
import jr.brian.commandpannel.replay.studio.ReplayStudioSummary
import jr.brian.commandpannel.replay.studio.ReplayStudioSummaryViewModel
import jr.brian.commandpannel.replay.studio.formatClipDate
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle

/** Preview width, matching the active-look preview at the top of Themes. */
private val STUDIO_PREVIEW_WIDTH = 250.dp

/** The look preview's corner at this width (ScaledPreview rounds 10 dp of a 538 dp overlay, scaled down). */
private val STUDIO_PREVIEW_CORNER = 10.dp * (250f / 538f)

/**
 * First card in Replay Buffer settings, styled like the active look at the top of Themes: the newest clip's thumbnail,
 * how many clips there are, when the newest was recorded, and Open Replay Studio. The card shows at once with a
 * placeholder; the clip listing loads off the main thread each time the section shows (and when coming back from
 * Studio). [buttonModifier] goes on the button, so focus can return there after Studio closes.
 */
@Composable
internal fun ReplayStudioCard(
    buttonModifier: Modifier,
    onOpen: () -> Unit,
    vm: ReplayStudioSummaryViewModel = hiltViewModel(),
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() } }

    val isNew = LocalNewBadges.current.state.cardNew(SettingsCategory.REPLAY, NewItems.CARD_REPLAY_STUDIO)
    val label = stringResource(R.string.settings_replay_studio)
    val title = when {
        !summary.loaded -> stringResource(R.string.studio_loading)
        summary.unavailable -> stringResource(R.string.studio_unavailable_title)
        summary.count == 0 -> stringResource(R.string.studio_empty_title)
        else -> pluralStringResource(R.plurals.studio_clip_count, summary.count, summary.count)
    }
    val a11y = if (summary.loaded && !summary.unavailable) {
        pluralStringResource(R.plurals.settings_replay_studio_a11y, summary.count, summary.count)
    } else {
        stringResource(R.string.settings_replay_studio_a11y_status, title)
    }
    Card(
        Modifier.tourTarget(TourSpot.REPLAY_STUDIO)
            .newCard(SettingsCategory.REPLAY, NewItems.CARD_REPLAY_STUDIO).newCornerDot(isNew, p.accent, p.bg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StudioPreview(summary)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row {
                        Text(lookLabel(label).uppercase(), color = p.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        // Zero-size: the card's corner dot, described for TalkBack.
                        if (isNew) NewBadgeLabel(label)
                    }
                    Text(title, color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = tccDisplayTextStyle())
                    val newest = summary.newest
                    if (newest != null) {
                        val date = remember(newest.recordedAtMs) { formatClipDate(newest.recordedAtMs) }
                        InfoLine(stringResource(R.string.settings_replay_studio_newest, date))
                    } else {
                        InfoLine(stringResource(R.string.settings_replay_studio_desc))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(buttonModifier) { AccentButton(stringResource(R.string.settings_replay_studio_open), onClick = onOpen) }
            }
        }
    }
}

/** The newest clip's thumbnail at 16:9, or the Replay Buffer icon on the card surface while there's none. */
@Composable
private fun StudioPreview(summary: ReplayStudioSummary) {
    Box(
        Modifier.width(STUDIO_PREVIEW_WIDTH).aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(STUDIO_PREVIEW_CORNER))
            .background(p.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = summary.thumbnail
        if (thumb != null) {
            Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Videocam, contentDescription = null, tint = p.textTertiary, modifier = Modifier.size(40.dp))
        }
    }
}
