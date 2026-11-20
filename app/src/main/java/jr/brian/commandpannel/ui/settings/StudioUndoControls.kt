package jr.brian.commandpannel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.components.GamepadGlyph
import jr.brian.commandpannel.ui.components.noRippleClickable
import jr.brian.commandpannel.ui.theme.tccTextStyle

/** Theme Studio's one-time tip that L3 / R3 undo and redo (#63): shown the first time Studio opens, never again. */
internal object StudioUndoTip {
    /** Set once the tip is dismissed. Device-local like the tour's seen flag, so not part of settings backups. */
    const val KEY_SEEN = "studio_undo_tip_seen"
}

/**
 * A square, icon-only version of [Chip] (Theme Studio's Undo and Redo, #63): the same shape, colors, focus ring and
 * scale, so it follows the look like the chips next to it. [contentDescription] names it for screen readers. Disabled
 * it stays focusable, doing nothing, so it doesn't drop focus while the history empties under it.
 */
@Composable
internal fun IconChip(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val f = focusState()
    val shape = controlShape(10.dp)
    val scale by focusScale(f.value, 1.08f, "iconChipScale")
    val bg = when { !enabled -> p.surface; f.value -> p.accentSoft; else -> p.surfaceHigh }
    val fg = when { !enabled -> p.textTertiary; f.value -> p.accent; else -> p.textSecondary }
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .then(if (f.value) Modifier.border(1.5.dp, p.accent, shape) else Modifier)
            .then(if (enabled) Modifier.focusControl(f, onClick).noRippleClickable(onClick = onClick) else Modifier.focusControl(f) {})
            // Same height as a one-line Chip (9 dp + 20 dp line + 9 dp).
            .padding(horizontal = 11.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(20.dp))
    }
}

/**
 * The tip itself: what L3 and R3 do, with a Got it button that has focus. A, B, Back or a tap outside dismiss it. The
 * keys act on release and are all consumed here, so B's release never reaches Studio (where it would close Studio).
 */
@Composable
internal fun StudioUndoTipDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val ok = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { ok.requestFocus() } }
        Column(
            Modifier.width(460.dp)
                .onPreviewKeyEvent { e ->
                    val dismissKey = e.key == Key.ButtonA || e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape ||
                        e.key == Key.Enter || e.key == Key.DirectionCenter
                    if (dismissKey && e.type == KeyEventType.KeyUp) onDismiss()
                    dismissKey
                }
                // Opaque base under the look's card (which may be translucent glass), as in the discard dialog.
                .clip(cardShape()).background(p.bg)
                .companionCard()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.theme_undo_tip_title), color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            CardDesc(stringResource(R.string.theme_undo_tip_body))
            TipRow(GamepadButton.L3, stringResource(R.string.theme_undo))
            TipRow(GamepadButton.R3, stringResource(R.string.theme_redo))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.focusRequester(ok)) {
                AccentButton(stringResource(R.string.theme_undo_tip_ok), glyph = GamepadButton.A, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun TipRow(button: GamepadButton, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GamepadGlyph(button, p.textSecondary, tccTextStyle())
        Text(label, color = p.textSecondary, fontSize = 14.sp, style = tccTextStyle(), modifier = Modifier.padding(vertical = 2.dp))
    }
}
