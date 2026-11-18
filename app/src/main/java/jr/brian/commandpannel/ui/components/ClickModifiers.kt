package jr.brian.commandpannel.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Clickable with no ripple / press indication. Used across the overlay and companion so taps don't flash a
 * Material ripple (handheld UI: focus highlight and scale provide the feedback instead).
 */
fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * Shrinks and nudges the element down while a finger is on it (the Home app's key press: 0.92 scale, 3dp drop,
 * 100 ms). It only watches the pointer, so put it before the clickable. A drag that a parent takes over (a pager
 * swipe) ends the press.
 */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(100), label = "pressScale")
    val offsetY by animateDpAsState(if (pressed) 3.dp else 0.dp, tween(100), label = "pressOffset")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = offsetY.toPx()
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            pressed = true
            // Final pass: by then a scrolling parent has consumed the drag it claimed.
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
            } while (event.changes.any { it.pressed && !it.isConsumed })
            pressed = false
        }
    }
}
