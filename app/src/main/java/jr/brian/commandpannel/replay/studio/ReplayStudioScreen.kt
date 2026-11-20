package jr.brian.commandpannel.replay.studio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.components.GamepadGlyph
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.settings.AccentButton
import jr.brian.commandpannel.ui.settings.Card
import jr.brian.commandpannel.ui.settings.CardDesc
import jr.brian.commandpannel.ui.settings.CardTitle
import jr.brian.commandpannel.ui.settings.Chip
import jr.brian.commandpannel.ui.settings.FocusRow
import jr.brian.commandpannel.ui.settings.InfoLine
import jr.brian.commandpannel.ui.settings.LocalCompanionThemed
import jr.brian.commandpannel.ui.settings.PaddedBringIntoView
import jr.brian.commandpannel.ui.settings.cardShape
import jr.brian.commandpannel.ui.settings.companionCard
import jr.brian.commandpannel.ui.settings.controlShape
import jr.brian.commandpannel.ui.settings.focusControl
import jr.brian.commandpannel.ui.settings.focusScale
import jr.brian.commandpannel.ui.settings.focusState
import jr.brian.commandpannel.ui.settings.p
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlinx.coroutines.launch
import java.io.File

/** Trim (#51): the action sheet's Trim entry opens the player in trim mode. False shows it disabled. */
internal const val STUDIO_TRIM_AVAILABLE = true

/** FileProvider authority suffix for shared clips (see `res/xml/clip_paths.xml`). */
private const val CLIPS_AUTHORITY_SUFFIX = ".clips"

/**
 * Replay Studio: every saved clip in a grid, newest first. Gamepad: A plays a clip, X opens its actions (Share, Trim,
 * Delete), B goes back. Touch: tap plays, long press opens the actions.
 */
@Composable
internal fun ReplayStudioScreen(onClose: () -> Unit, vm: ReplayStudioViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val backFocus = remember { FocusRequester() }
    val cardFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun requesterFor(path: String) = cardFocus.getOrPut(path) { FocusRequester() }
    var sheetFor by remember { mutableStateOf<ReplayClip?>(null) }
    var confirmDelete by remember { mutableStateOf<ReplayClip?>(null) }
    // A deleted clip and the card that takes focus once it has left the grid (null: the Back chip).
    var afterDelete by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var focusedFirst by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)
    // Read the folder each time Studio comes back to the front (first open, or back from the player).
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refresh() } }
    LaunchedEffect(state.loading, state.clips.isEmpty()) {
        if (state.loading || focusedFirst) return@LaunchedEffect
        focusedFirst = true
        // Let the grid lay out its first cards before focusing one.
        repeat(2) { withFrameNanos { } }
        val first = state.clips.firstOrNull()
        runCatching { (first?.let { requesterFor(it.path) } ?: backFocus).requestFocus() }
    }
    LaunchedEffect(state.clips, afterDelete) {
        val (deleted, target) = afterDelete ?: return@LaunchedEffect
        if (state.clips.any { it.path == deleted }) return@LaunchedEffect
        afterDelete = null
        repeat(2) { withFrameNanos { } }
        runCatching { (target?.let { requesterFor(it) } ?: backFocus).requestFocus() }
    }
    // A delete that failed keeps focus where it is.
    LaunchedEffect(state.message) { if (state.message != null) afterDelete = null }
    val refocus: (ReplayClip) -> Unit = { clip -> runCatching { requesterFor(clip.path).requestFocus() } }

    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    PaddedBringIntoView {
        Column(
            Modifier.fillMaxSize().then(backdrop).systemBarsPadding()
                // X and Y do nothing outside a card; left unhandled, the system can turn them into Back.
                .onKeyEvent { it.key == Key.ButtonX || it.key == Key.ButtonY }
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.focusRequester(backFocus)) { Chip(stringResource(R.string.studio_back), onClick = onClose) }
                Text(
                    lookLabel(stringResource(R.string.studio_title)), color = p.textPrimary, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, style = tccDisplayTextStyle(), modifier = Modifier.weight(1f),
                )
                if (state.clips.isNotEmpty()) {
                    InfoLine(pluralStringResource(R.plurals.studio_clip_count, state.clips.size, state.clips.size))
                }
            }
            ControlsHint()
            state.message?.let { InfoLine(it, p.danger) }
            when {
                state.loading -> InfoLine(stringResource(R.string.studio_loading))
                state.unavailable -> Card {
                    CardTitle(stringResource(R.string.studio_unavailable_title))
                    CardDesc(stringResource(R.string.studio_unavailable_desc))
                }
                state.clips.isEmpty() -> Card {
                    CardTitle(stringResource(R.string.studio_empty_title))
                    CardDesc(stringResource(R.string.studio_empty_desc))
                }
                else -> ClipGrid(
                    clips = state.clips,
                    vm = vm,
                    requesterFor = ::requesterFor,
                    onPlay = { clip -> vm.clearMessage(); ReplayPlayerActivity.launch(context, clip.path) },
                    onOptions = { clip -> vm.clearMessage(); sheetFor = clip },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    sheetFor?.let { clip ->
        ClipActionSheet(
            clip = clip,
            onDismiss = { sheetFor = null; refocus(clip) },
            onShare = {
                sheetFor = null
                refocus(clip)
                scope.launch {
                    val file = vm.shareableFile(clip) ?: return@launch
                    if (!shareClip(context, file)) vm.shareFailed()
                }
            },
            onTrim = {
                sheetFor = null
                refocus(clip)
                vm.clearMessage()
                ReplayPlayerActivity.launch(context, clip.path, trim = true)
            },
            onDelete = { sheetFor = null; confirmDelete = clip },
        )
    }
    confirmDelete?.let { clip ->
        DeleteClipDialog(
            clip = clip,
            onCancel = { confirmDelete = null; refocus(clip) },
            onConfirm = {
                confirmDelete = null
                refocus(clip)
                afterDelete = clip.path to focusAfterDelete(state.clips.map { it.path }, clip.path)
                vm.delete(clip)
            },
        )
    }
}

/** "A Play  X Options  B Back", so the buttons are discoverable. */
@Composable
private fun ControlsHint() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            GamepadButton.A to R.string.studio_hint_play,
            GamepadButton.X to R.string.studio_hint_options,
            GamepadButton.B to R.string.studio_hint_back,
        ).forEach { (button, label) ->
            GamepadGlyph(button, p.textTertiary, tccTextStyle())
            Text(stringResource(label), color = p.textTertiary, fontSize = 12.sp, style = tccTextStyle())
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun ClipGrid(
    clips: List<ReplayClip>,
    vm: ReplayStudioViewModel,
    requesterFor: (String) -> FocusRequester,
    onPlay: (ReplayClip) -> Unit,
    onOptions: (ReplayClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grid = rememberLazyGridState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = grid,
        modifier = modifier.fillMaxWidth(),
        // Room at the edges for the focused card's ring and scale.
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(clips, key = { it.path }) { clip ->
            ClipCard(
                clip, vm,
                Modifier.focusRequester(requesterFor(clip.path)),
                onPlay = { onPlay(clip) },
                onOptions = { onOptions(clip) },
            )
        }
    }
}

private const val GRID_COLUMNS = 3

/** One clip: its thumbnail with the screen badge and length, and when it was recorded. */
@Composable
private fun ClipCard(
    clip: ReplayClip,
    vm: ReplayStudioViewModel,
    modifier: Modifier,
    onPlay: () -> Unit,
    onOptions: () -> Unit,
) {
    val f = focusState()
    val scale by focusScale(f.value, 1.04f, "clipScale")
    val thumb by produceState(vm.cachedThumbnail(clip), clip.path, clip.modifiedMs) {
        if (value == null) value = vm.thumbnail(clip)
    }
    val duration by produceState(vm.cachedDuration(clip), clip.path, clip.modifiedMs) {
        if (value == null) value = vm.duration(clip)
    }
    val date = remember(clip.recordedAtMs) { formatClipDate(clip.recordedAtMs) }
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onKeyEvent { e ->
                if (e.key != Key.ButtonX) return@onKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) onOptions()
                true
            }
            .focusControl(f, onPlay)
            .pointerInput(clip.path) { detectTapGestures(onTap = { onPlay() }, onLongPress = { onOptions() }) }
            .companionCard(focused = f.value, focusable = true)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val thumbShape = controlShape(10.dp)
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(thumbShape).background(p.surfaceHigh),
        ) {
            thumb?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Pill(stringResource(clip.origin.badgeRes()), Modifier.align(Alignment.TopStart).padding(8.dp))
            duration?.let { Pill(formatClipDuration(it), Modifier.align(Alignment.BottomEnd).padding(8.dp)) }
        }
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                date, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = tccTextStyle(),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (clip.isTrim) InfoLine(stringResource(R.string.studio_badge_trimmed), p.accent)
        }
    }
}

/** A small label over the thumbnail; dark under light text, so it reads on any frame. */
@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(),
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** X on a card: Share, Trim, Delete. Focus starts on Share; B closes it. */
@Composable
private fun ClipActionSheet(
    clip: ReplayClip,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onTrim: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            studioDialogModifier(420),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                remember(clip.recordedAtMs) { formatClipDate(clip.recordedAtMs) }, color = p.textPrimary, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, style = tccTextStyle(),
            )
            InfoLine(clip.name)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.focusRequester(first)) { SheetAction(stringResource(R.string.studio_action_share), onClick = onShare) }
            SheetAction(
                stringResource(R.string.studio_action_trim),
                enabled = STUDIO_TRIM_AVAILABLE,
                note = if (STUDIO_TRIM_AVAILABLE) null else stringResource(R.string.studio_action_trim_soon),
                onClick = onTrim,
            )
            SheetAction(stringResource(R.string.studio_action_delete), tone = p.danger, onClick = onDelete)
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    enabled: Boolean = true,
    tone: Color = p.textPrimary,
    note: String? = null,
    onClick: () -> Unit,
) {
    if (enabled) {
        FocusRow(selected = false, onClick = onClick) {
            Text(text, color = tone, fontSize = 15.sp, fontWeight = FontWeight.Medium, style = tccTextStyle(), modifier = Modifier.weight(1f))
        }
    } else {
        // Not focusable: the D-pad steps over it.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = p.textTertiary, fontSize = 15.sp, style = tccTextStyle(), modifier = Modifier.weight(1f))
            note?.let { InfoLine(it, p.textTertiary) }
        }
    }
}

/** "Delete this clip?" Focus starts on Cancel, and B cancels too. */
@Composable
private fun DeleteClipDialog(clip: ReplayClip, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val cancel = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
        Column(studioDialogModifier(520), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.studio_delete_title), color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            CardDesc(stringResource(R.string.studio_delete_body, formatClipDate(clip.recordedAtMs)))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.focusRequester(cancel)) {
                    AccentButton(stringResource(R.string.studio_delete_cancel), glyph = GamepadButton.B, onClick = onCancel)
                }
                Chip(stringResource(R.string.studio_delete_confirm), onClick = onConfirm)
            }
        }
    }
}

/** Dialog surface: an opaque base under the look's card (which may be translucent glass), as in Theme Studio. */
@Composable
private fun studioDialogModifier(widthDp: Int): Modifier =
    Modifier.width(widthDp.dp).clip(cardShape()).background(p.bg).companionCard().padding(24.dp)

/** Opens the share sheet for [file] as an MP4. False when it couldn't be offered. Also the player's Share offer (#51). */
internal fun shareClip(context: Context, file: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, context.packageName + CLIPS_AUTHORITY_SUFFIX, file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser passes the grant on only through ClipData.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.studio_share_title)))
}.onFailure { Log.w("ReplayStudio", "couldn't share ${file.path}", it) }.isSuccess
