package jr.brian.commandpannel.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where [scrollPaneTo] puts its target: near the top (the tour, leaving room for its callout) or in the middle. */
internal enum class PaneAlign { TOP, CENTER }

/** The pane's scroll between sections and targets (the tour's and jumps'): 520 ms, easing in and out. */
private val PaneScrollSpec = tween<Float>(520, easing = FastOutSlowInEasing)

/** How long a jump's spotlight stays on its target before fading. */
internal const val JUMP_HOLD_MS = 1_500L

/**
 * The pane scroll that puts [target] at [align] in [viewport] (root coordinates, [topInset] px below the top for
 * [PaneAlign.TOP]), from [current] and clamped to `0..max`. Pure, for tests.
 */
internal fun paneScrollTarget(current: Int, max: Int, target: Rect, viewport: Rect, align: PaneAlign, topInset: Float = 0f): Int {
    val delta = when (align) {
        PaneAlign.TOP -> target.top - viewport.top - topInset
        PaneAlign.CENTER -> target.center.y - viewport.center.y
    }
    return (current + delta).roundToInt().coerceIn(0, max.coerceAtLeast(0))
}

/** Waits (up to 800 ms) for [spot] to be composed and laid out, plus two frames for a section's scroll reset to land. */
internal suspend fun awaitTarget(targets: TourTargets, spot: TourSpot): Rect? {
    withTimeoutOrNull(800) { snapshotFlow { targets.rectOf(spot) }.first { it != null } } ?: return null
    repeat(2) { withFrameNanos { } }
    return targets.rectOf(spot)
}

/**
 * Scrolls the settings pane so [spot] sits at [align]: animated when the look animates ([motion]), else in one jump.
 * Shared by the tour, the onboarding's look follower and [PaneJump]s. Returns false when [spot] never showed up.
 */
internal suspend fun scrollPaneTo(
    targets: TourTargets,
    spot: TourSpot,
    scroll: ScrollState,
    viewport: State<Rect>,
    motion: Boolean,
    align: PaneAlign,
    topInset: Float = 0f,
): Boolean {
    val rect = awaitTarget(targets, spot) ?: return false
    val to = paneScrollTarget(scroll.value, scroll.maxValue, rect, viewport.value, align, topInset)
    if (to == scroll.value) return true
    if (motion) scroll.animateScrollTo(to, PaneScrollSpec) else scroll.scrollTo(to)
    return true
}

/**
 * A link to a control in another section (#61): [section] opens (its rail group expands with it), the pane scrolls
 * [spot] to the middle, the spotlight glides onto it and stays for [JUMP_HOLD_MS] before fading, and focus lands on it
 * so A works it. The target control is marked with [jumpTarget].
 */
@Immutable
internal data class PaneJump(val section: SettingsCategory, val spot: TourSpot)

/** Focus requesters for jump targets, one per spot. */
@Stable
internal class JumpTargets {
    private val requesters = mutableMapOf<TourSpot, FocusRequester>()

    fun requester(spot: TourSpot): FocusRequester = requesters.getOrPut(spot) { FocusRequester() }
}

internal val LocalJumpTargets = staticCompositionLocalOf<JumpTargets?> { null }

/** Marks this control as where a [PaneJump] to [spot] lands: the spotlight finds it and focus moves to it. */
@Composable
internal fun Modifier.jumpTarget(spot: TourSpot): Modifier {
    val jumps = LocalJumpTargets.current
    return tourTarget(spot).then(if (jumps != null) Modifier.focusRequester(jumps.requester(spot)) else Modifier)
}

/**
 * Carries out [jump]: switches to its section ([onSection]), shows the spotlight ([onSpotlight]) while scrolling to
 * the target, focuses it, holds, then fades the spotlight and calls [onDone]. With the look's animation off it all
 * happens without motion, like the tour.
 */
@Composable
internal fun JumpDriver(
    jump: PaneJump?,
    targets: TourTargets,
    jumps: JumpTargets,
    scroll: ScrollState,
    viewport: State<Rect>,
    onSection: (SettingsCategory) -> Unit,
    onSpotlight: (TourSpot?) -> Unit,
    onDone: () -> Unit,
) {
    val motion = LocalTccLookSpec.current.traits.animate
    LaunchedEffect(jump) {
        val j = jump ?: return@LaunchedEffect
        onSection(j.section)
        if (awaitTarget(targets, j.spot) != null) {
            // On before the scroll, so the spotlight glides with the target into the middle.
            onSpotlight(j.spot)
            scrollPaneTo(targets, j.spot, scroll, viewport, motion, PaneAlign.CENTER)
            runCatching { jumps.requester(j.spot).requestFocus() }
            delay(JUMP_HOLD_MS)
            onSpotlight(null)
        }
        onDone()
    }
}
