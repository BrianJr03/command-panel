package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.update.ReleaseInfo
import kotlinx.coroutines.launch

/** What the update dialog is showing. */
sealed interface UpdatePhase {
    /** A newer release: its notes, with Download and install / Later / Skip this version. */
    data class Offer(val release: ReleaseInfo) : UpdatePhase
    /** [progress] is 0..1, or negative while the size is unknown. */
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdatePhase
    /** The install is running; with root, Command Panel restarts on its own. */
    data class Installing(val release: ReleaseInfo) : UpdatePhase
    /** Something failed; [canUseInstaller] offers Android's installer for an already downloaded APK. */
    data class Failed(val release: ReleaseInfo, val message: String, val canUseInstaller: Boolean) : UpdatePhase
    /** Patch notes for the version just installed (or reopened from the Updates card). */
    data class WhatsNew(val version: String, val notes: String) : UpdatePhase
}

/**
 * The update / What's New dialog. Gamepad-friendly: focus starts on the main button, D-pad up/down scroll the notes,
 * left/right move between buttons, and B closes it (except while downloading or installing).
 */
@Composable
internal fun UpdateDialog(phase: UpdatePhase, vm: SettingsViewModel) {
    val busy = phase is UpdatePhase.Downloading || phase is UpdatePhase.Installing
    Dialog(
        onDismissRequest = { if (!busy) vm.dismissUpdate() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy, dismissOnClickOutside = false),
    ) {
        val notesScroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val primary = remember { FocusRequester() }
        LaunchedEffect(phase::class) { runCatching { primary.requestFocus() } }
        Column(
            Modifier.width(780.dp).heightIn(max = 430.dp)
                // Opaque base under the look's card (which may be translucent glass) so the screen behind doesn't show.
                .clip(cardShape()).background(p.bg)
                .companionCard()
                .onPreviewKeyEvent { e ->
                    val dy = when (e.key) {
                        Key.DirectionUp -> -220f
                        Key.DirectionDown -> 220f
                        else -> return@onPreviewKeyEvent false
                    }
                    if (e.type == KeyEventType.KeyDown) scope.launch { notesScroll.animateScrollBy(dy) }
                    true
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val (title, subtitle) = when (phase) {
                is UpdatePhase.WhatsNew -> stringResource(R.string.whats_new_title, phase.version) to null
                is UpdatePhase.Offer -> stringResource(R.string.update_available_title, phase.release.version) to
                    stringResource(R.string.update_available_desc)
                is UpdatePhase.Downloading -> stringResource(R.string.update_downloading, phase.release.version) to null
                is UpdatePhase.Installing -> stringResource(R.string.update_installing, phase.release.version) to
                    stringResource(R.string.update_installing_desc)
                is UpdatePhase.Failed -> stringResource(R.string.update_failed_title) to phase.message
            }
            Text(title, color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            subtitle?.let { CardDesc(it) }

            val notes = when (phase) {
                is UpdatePhase.WhatsNew -> phase.notes
                is UpdatePhase.Offer -> phase.release.notes
                else -> null
            }
            if (notes != null) {
                Box(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(notesScroll)) {
                    if (notes.isBlank()) CardDesc(stringResource(R.string.update_no_notes)) else MarkdownText(notes)
                }
            }
            if (phase is UpdatePhase.Downloading) {
                Spacer(Modifier.height(6.dp))
                if (phase.progress < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.accent, trackColor = p.surfaceHigh)
                } else {
                    LinearProgressIndicator({ phase.progress }, Modifier.fillMaxWidth(), color = p.accent, trackColor = p.surfaceHigh)
                    InfoLine(stringResource(R.string.update_progress, (phase.progress * 100).toInt()))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (phase) {
                    is UpdatePhase.Offer -> {
                        Box(Modifier.focusRequester(primary)) {
                            AccentButton(stringResource(R.string.update_download), onClick = vm::downloadAndInstall)
                        }
                        Chip(stringResource(R.string.update_later), onClick = vm::dismissUpdate)
                        Chip(stringResource(R.string.update_skip), onClick = vm::skipUpdate)
                    }
                    is UpdatePhase.Failed -> {
                        Box(Modifier.focusRequester(primary)) {
                            if (phase.canUseInstaller) AccentButton(stringResource(R.string.update_use_installer), onClick = vm::installWithSystemInstaller)
                            else AccentButton(stringResource(R.string.update_retry), onClick = vm::downloadAndInstall)
                        }
                        if (phase.release.pageUrl.isNotBlank()) Chip(stringResource(R.string.update_open_page), onClick = vm::openReleasePage)
                        Chip(stringResource(R.string.update_close), onClick = vm::dismissUpdate)
                    }
                    is UpdatePhase.WhatsNew -> Box(Modifier.focusRequester(primary)) {
                        AccentButton(stringResource(R.string.whats_new_done), onClick = vm::dismissUpdate)
                    }
                    // Nothing to press while downloading / installing; focus stays in the dialog.
                    else -> Box(Modifier.focusRequester(primary).padding(1.dp)) {}
                }
            }
        }
    }
}
