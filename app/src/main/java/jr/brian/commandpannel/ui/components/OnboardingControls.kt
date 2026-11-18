package jr.brian.commandpannel.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R

/**
 * Gamepad controls shared by both onboardings (the companion tour and the overlay's first-run walk): A (or D-pad Right,
 * R1) for the next step, B (or D-pad Left, L1) for the previous one, Y to skip. Every other gamepad key is swallowed so
 * nothing underneath moves; volume and other system keys pass through. Pair it with [OnboardingBackHandler].
 *
 * B and Y act on release, so the key-up doesn't reach (and close) the companion once the onboarding is gone.
 *
 * With [onDirection] set, the D-pad (and the analog stick, which Android turns into D-pad keys when nothing handles its
 * motion) moves between a step's options instead: Left / Up gives -1, Right / Down +1.
 */
fun Modifier.onboardingControls(
    enabled: Boolean = true,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onDirection: ((Int) -> Unit)? = null,
): Modifier = if (!enabled) this else onPreviewKeyEvent { e ->
    val down = e.type == KeyEventType.KeyDown
    val up = e.type == KeyEventType.KeyUp
    val first = e.nativeKeyEvent.repeatCount == 0
    if (onDirection != null) {
        val delta = when (e.key) {
            Key.DirectionLeft, Key.DirectionUp -> -1
            Key.DirectionRight, Key.DirectionDown -> 1
            else -> 0
        }
        if (delta != 0) {
            if (down && first) onDirection(delta)
            return@onPreviewKeyEvent true
        }
    }
    when (e.key) {
        Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionRight, Key.ButtonR1 -> {
            if (down && e.nativeKeyEvent.repeatCount == 0) onNext()
            true
        }
        Key.ButtonB, Key.Back, Key.Escape, Key.DirectionLeft, Key.ButtonL1 -> {
            if (up) onBack()
            true
        }
        Key.ButtonY -> {
            if (up) onSkip()
            true
        }
        Key.DirectionUp, Key.DirectionDown, Key.Tab -> true
        else -> android.view.KeyEvent.isGamepadButton(e.nativeKeyEvent.keyCode)
    }
}

/** Newer Android sends Back through the back dispatcher rather than as a key event; this routes it to [onBack]. */
@Composable
fun OnboardingBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled) { onBack() }
}

/** A gamepad button shown as a badge before a control's label, so it's clear which button does what. */
enum class GamepadButton(@StringRes val letterRes: Int) {
    A(R.string.gamepad_button_a),
    B(R.string.gamepad_button_b),
    Y(R.string.gamepad_button_y),
    SELECT(R.string.gamepad_button_select),
    START(R.string.gamepad_button_start),
}

/** A round gamepad-button badge ("A", "B", "Y", "−", "+") in [color], drawn before a control's label. */
@Composable
fun GamepadGlyph(key: GamepadButton, color: Color, style: TextStyle = TextStyle.Default) {
    Box(Modifier.size(18.dp).border(1.5.dp, color, CircleShape), contentAlignment = Alignment.Center) {
        Text(stringResource(key.letterRes), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, style = style)
    }
}
