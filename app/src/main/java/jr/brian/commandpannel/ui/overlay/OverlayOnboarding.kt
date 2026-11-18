package jr.brian.commandpannel.ui.overlay

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.OverlayLayout
import jr.brian.commandpannel.ui.components.noRippleClickable
import jr.brian.commandpannel.ui.overlay.arrange.ArrangeController
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The overlay's first-run onboarding: a short, hands-on walk through the two gestures that are easy to miss, long-press
 * to arrange (then drag and drop) and the Quick Launch nav button. The hands-on steps move on by themselves once the
 * user does the gesture and leave the panel fully usable; the others dim it and only take taps on the callout.
 */
object OverlayOnboarding {
    /** The onboarding was finished or skipped. Not backed up, so a restored backup still gets it. */
    const val KEY_SEEN = "overlay_onboarding_seen"
}

/** One stop on the onboarding. [handsOn] steps wait for a gesture and let touches through to the panel. */
enum class OverlayOnboardingStep(@StringRes val titleRes: Int, @StringRes val bodyRes: Int, val handsOn: Boolean = false) {
    WELCOME(R.string.overlay_onboarding_welcome_title, R.string.overlay_onboarding_welcome_body),
    LONG_PRESS(R.string.overlay_onboarding_long_press_title, R.string.overlay_onboarding_long_press_body, handsOn = true),
    DRAG(R.string.overlay_onboarding_drag_title, R.string.overlay_onboarding_drag_body, handsOn = true),
    FINISH_ARRANGE(R.string.overlay_onboarding_finish_arrange_title, R.string.overlay_onboarding_finish_arrange_body, handsOn = true),
    QUICK_LAUNCH(R.string.overlay_onboarding_quick_launch_title, R.string.overlay_onboarding_quick_launch_body),
}

/**
 * Drawn over the whole overlay while [step] runs. [launchBounds] is the Launch nav button's root bounds (the Quick
 * Launch step's spotlight); [navAtTop] keeps the callout clear of the nav bar during the arrange steps.
 */
@Composable
internal fun OverlayOnboardingLayer(
    step: OverlayOnboardingStep,
    arrange: ArrangeController,
    layout: OverlayLayout,
    navAtTop: Boolean,
    launchBounds: Rect?,
    onStep: (OverlayOnboardingStep) -> Unit,
    onEnd: () -> Unit,
) {
    val motion = LocalTccLookSpec.current.traits.animate
    val colors = LocalTccColors.current
    val density = LocalDensity.current

    // ---- auto-advance on the gestures ----
    LaunchedEffect(step, arrange.isArranging) {
        if (step == OverlayOnboardingStep.LONG_PRESS && arrange.isArranging) onStep(OverlayOnboardingStep.DRAG)
    }
    // Leaving Arrange mode mid-way skips ahead; reopening the overlay (a fresh, idle Arrange) starts the gesture over.
    val sawArranging = remember(step) { BooleanArray(1) }
    LaunchedEffect(step, arrange.isArranging) {
        if (step != OverlayOnboardingStep.DRAG && step != OverlayOnboardingStep.FINISH_ARRANGE) return@LaunchedEffect
        if (arrange.isArranging) {
            sawArranging[0] = true
        } else {
            onStep(if (sawArranging[0]) OverlayOnboardingStep.QUICK_LAUNCH else OverlayOnboardingStep.LONG_PRESS)
        }
    }
    val layoutAtStep = remember(step) { layout }
    LaunchedEffect(step, layout) {
        if (step == OverlayOnboardingStep.DRAG && layout != layoutAtStep) onStep(OverlayOnboardingStep.FINISH_ARRANGE)
    }

    // Next does the gesture's job when the user would rather not.
    val onNext: () -> Unit = {
        when (step) {
            OverlayOnboardingStep.WELCOME -> onStep(OverlayOnboardingStep.LONG_PRESS)
            OverlayOnboardingStep.LONG_PRESS -> arrange.enter()
            OverlayOnboardingStep.DRAG -> onStep(OverlayOnboardingStep.FINISH_ARRANGE)
            OverlayOnboardingStep.FINISH_ARRANGE -> arrange.exit()
            OverlayOnboardingStep.QUICK_LAUNCH -> onEnd()
        }
    }
    val onSkip: () -> Unit = {
        arrange.exit()
        onEnd()
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val margin: Float
    val gap: Float
    val holePad: Float
    // Clears the nav bar (84dp + its 12dp spacer) or the Arrange toolbar on the other side.
    val navClearance: Float
    val calloutWidth = 400.dp
    val calloutWidthPx: Float
    with(density) {
        margin = 16.dp.toPx(); gap = 14.dp.toPx(); holePad = 6.dp.toPx(); navClearance = 108.dp.toPx()
        calloutWidthPx = calloutWidth.toPx()
    }
    val w = layerSize.width.toFloat()
    val h = layerSize.height.toFloat()
    val cw = min(calloutWidthPx, w - 2 * margin)
    val ch = calloutSize.height.toFloat()

    val hole: Rect? = launchBounds?.takeIf { step == OverlayOnboardingStep.QUICK_LAUNCH }
        ?.translate(-origin)?.inflate(holePad)
    val x = ((w - cw) / 2).coerceAtLeast(0f)
    val y = when {
        hole != null -> if (navAtTop) hole.bottom + gap else hole.top - gap - ch
        step.handsOn -> if (navAtTop) h - margin - ch else h - navClearance - ch
        else -> (h - ch) / 2
    }.coerceIn(margin, max(margin, h - ch - margin))

    val pulse = if (motion && hole != null) {
        rememberInfiniteTransition(label = "onboardingPulse").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "onboardingPulse",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val dim by animateFloatAsState(if (step.handsOn) 0f else 0.62f, if (motion) tween(260) else snap(), label = "onboardingDim")
    // Out of the way while a card is being dragged, so the drop target underneath is visible.
    val calloutAlpha by animateFloatAsState(
        if (arrange.draggedCard != null) 0f else 1f, if (motion) tween(160) else snap(), label = "onboardingCallout",
    )

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot(); layerSize = it.size }
            // Hands-on steps leave the panel usable; the others don't let taps reach it.
            .then(
                if (step.handsOn) Modifier
                else Modifier.pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                },
            ),
    ) {
        val radius = with(density) { 16.dp.toPx() }
        val ring = with(density) { 2.dp.toPx() }
        val halo = with(density) { 3.dp.toPx() }
        val haloReach = with(density) { 8.dp.toPx() }
        if (dim > 0f) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color.Black.copy(alpha = dim))
                val r = hole ?: return@Canvas
                drawRoundRect(Color.Black, r.topLeft, r.size, CornerRadius(radius), blendMode = BlendMode.Clear)
                val t = pulse.value
                val grow = ring + t * haloReach
                drawRoundRect(
                    colors.accent.copy(alpha = 0.5f * (1f - t)),
                    topLeft = r.topLeft - Offset(grow, grow),
                    size = Size(r.width + 2 * grow, r.height + 2 * grow),
                    cornerRadius = CornerRadius(radius + grow),
                    style = Stroke(halo),
                )
                drawRoundRect(colors.accent, r.topLeft, r.size, CornerRadius(radius), style = Stroke(ring))
            }
        }
        OnboardingCallout(
            step = step,
            motion = motion,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(with(density) { cw.coerceAtLeast(0f).toDp() })
                .onSizeChanged { calloutSize = it }
                // Hidden until measured, so it doesn't jump into place.
                .graphicsLayer { alpha = if (calloutSize == IntSize.Zero) 0f else calloutAlpha },
            onNext = onNext,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun OnboardingCallout(
    step: OverlayOnboardingStep,
    motion: Boolean,
    modifier: Modifier,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalTccColors.current
    val steps = OverlayOnboardingStep.entries
    val last = step == steps.last()
    // No pointer handling of its own: during the drag step a card can still be dropped underneath it.
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.dialogBg)
            .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (motion) {
                    fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                } else {
                    EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "onboardingStep",
        ) { s ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val title = if (s == OverlayOnboardingStep.WELCOME) {
                    stringResource(s.titleRes, stringResource(R.string.app_full_name))
                } else {
                    stringResource(s.titleRes)
                }
                Text(title, color = colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, style = tccDisplayTextStyle())
                Text(stringResource(s.bodyRes), color = colors.secondary, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.tour_step_count, step.ordinal + 1, steps.size),
                color = colors.tertiary, fontSize = 12.sp, style = tccTextStyle(), modifier = Modifier.weight(1f),
            )
            if (!last) OnboardingButton(stringResource(R.string.overlay_onboarding_skip), emphasized = false, onClick = onSkip)
            OnboardingButton(stringResource(if (last) R.string.tour_done else R.string.tour_next), emphasized = true, onClick = onNext)
        }
    }
}

@Composable
private fun OnboardingButton(label: String, emphasized: Boolean, onClick: () -> Unit) {
    val colors = LocalTccColors.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .then(if (emphasized) Modifier.background(colors.accent) else Modifier.border(1.dp, colors.secondary, shape))
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (emphasized) Color.White else colors.primary,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, style = tccTextStyle(),
        )
    }
}
