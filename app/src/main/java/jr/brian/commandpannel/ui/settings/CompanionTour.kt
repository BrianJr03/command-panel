package jr.brian.commandpannel.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.toSize
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.OnboardingBackHandler
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.components.onboardingControls
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The companion tour: a guided walk through every settings section on the real screen. Each step selects its section,
 * scrolls the pane to the part it describes, dims everything else and moves a spotlight (with a pulsing accent ring)
 * and a callout card onto it. Gamepad ([onboardingControls]): A next, B back (exits on the first step), Y skip. Touch
 * uses the callout's buttons.
 */

/** A region the tour can spotlight. [inPane] regions live in the scrolling content pane (the rest are on the rail). */
internal enum class TourSpot(val inPane: Boolean = true) {
    RAIL(false),
    RAIL_GENERAL(false), RAIL_NAV_BAR(false), RAIL_TILES(false), RAIL_APPEARANCE(false), RAIL_THEMES(false), RAIL_DIAGNOSTICS(false),
    REPLACE_STOCK, OVERLAY_EXTRAS, BACKUP, UPDATES, REPLAY, PREVIEW,
    BUTTON_ORDER, QUICK_LAUNCH,
    TILE_LABELS, TILE_ORDER, TILE_PICKER,
    OVERLAY_THEME, FOLLOW_LOOK, RGB_ACCENT,
    ACTIVE_LOOK, MIX, MY_LOOKS, PACKS, REPOS, BUILTIN,
    /** The selected row in the built-in list (followed while the overlay onboarding's theme step runs). */
    BUILTIN_SELECTED,
    PRIVILEGED,
    TOUR_REPLAY;

    companion object {
        fun railItem(category: SettingsCategory): TourSpot = when (category) {
            SettingsCategory.GENERAL -> RAIL_GENERAL
            SettingsCategory.NAV_BAR -> RAIL_NAV_BAR
            SettingsCategory.QUICK_TOGGLES -> RAIL_TILES
            SettingsCategory.APPEARANCE -> RAIL_APPEARANCE
            SettingsCategory.THEMES -> RAIL_THEMES
            SettingsCategory.DIAGNOSTICS -> RAIL_DIAGNOSTICS
        }
    }
}

/** One stop on the tour: the [section] to show (null keeps the current one) and the [spot] to light (null: none). */
internal class TourStep(
    val section: SettingsCategory?,
    val spot: TourSpot?,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private fun sectionIntro(category: SettingsCategory, @StringRes body: Int) =
    TourStep(category, TourSpot.railItem(category), category.titleRes, body)

internal val TOUR_STEPS: List<TourStep> = run {
    val general = SettingsCategory.GENERAL
    val navBar = SettingsCategory.NAV_BAR
    val tiles = SettingsCategory.QUICK_TOGGLES
    val appearance = SettingsCategory.APPEARANCE
    val themes = SettingsCategory.THEMES
    val diagnostics = SettingsCategory.DIAGNOSTICS
    listOf(
        TourStep(null, null, R.string.tour_welcome_title, R.string.tour_welcome_body),
        TourStep(general, TourSpot.RAIL, R.string.tour_rail_title, R.string.tour_rail_body),

        sectionIntro(general, R.string.tour_general_body),
        TourStep(general, TourSpot.REPLACE_STOCK, R.string.settings_replace_stock, R.string.tour_replace_stock_body),
        TourStep(general, TourSpot.OVERLAY_EXTRAS, R.string.tour_overlay_extras_title, R.string.tour_overlay_extras_body),
        TourStep(general, TourSpot.BACKUP, R.string.settings_backup_title, R.string.tour_backup_body),
        TourStep(general, TourSpot.UPDATES, R.string.settings_update_title, R.string.tour_updates_body),
        TourStep(general, TourSpot.REPLAY, R.string.tile_name_replay_buffer, R.string.tour_replay_body),
        TourStep(general, TourSpot.PREVIEW, R.string.settings_preview_title, R.string.tour_preview_body),

        sectionIntro(navBar, R.string.tour_nav_bar_body),
        TourStep(navBar, TourSpot.BUTTON_ORDER, R.string.settings_button_order, R.string.tour_button_order_body),
        TourStep(navBar, TourSpot.QUICK_LAUNCH, R.string.settings_quick_launch, R.string.tour_quick_launch_body),

        sectionIntro(tiles, R.string.tour_tiles_body),
        TourStep(tiles, TourSpot.TILE_LABELS, R.string.settings_hide_tile_labels, R.string.tour_tile_labels_body),
        TourStep(tiles, TourSpot.TILE_ORDER, R.string.tour_tile_order_title, R.string.tour_tile_order_body),
        TourStep(tiles, TourSpot.TILE_PICKER, R.string.tour_tile_picker_title, R.string.tour_tile_picker_body),

        sectionIntro(appearance, R.string.tour_appearance_body),
        TourStep(appearance, TourSpot.OVERLAY_THEME, R.string.settings_overlay_theme, R.string.tour_overlay_theme_body),
        TourStep(appearance, TourSpot.FOLLOW_LOOK, R.string.settings_companion_follow_look, R.string.tour_follow_look_body),
        TourStep(appearance, TourSpot.RGB_ACCENT, R.string.settings_rgb_accent, R.string.tour_rgb_accent_body),

        sectionIntro(themes, R.string.tour_themes_body),
        TourStep(themes, TourSpot.ACTIVE_LOOK, R.string.theme_active, R.string.tour_active_look_body),
        TourStep(themes, TourSpot.MIX, R.string.theme_mix, R.string.tour_mix_body),
        TourStep(themes, TourSpot.MY_LOOKS, R.string.theme_my_looks, R.string.tour_my_looks_body),
        TourStep(themes, TourSpot.PACKS, R.string.theme_packs, R.string.tour_packs_body),
        TourStep(themes, TourSpot.REPOS, R.string.theme_repos, R.string.tour_repos_body),
        TourStep(themes, TourSpot.BUILTIN, R.string.theme_builtin, R.string.tour_builtin_body),

        sectionIntro(diagnostics, R.string.tour_diagnostics_body),
        TourStep(diagnostics, TourSpot.PRIVILEGED, R.string.settings_privileged_layer, R.string.tour_privileged_body),

        TourStep(general, TourSpot.TOUR_REPLAY, R.string.tour_finish_title, R.string.tour_finish_body),
    )
}

// ---- target registry ----

/**
 * Where each [TourSpot] is on screen, in root coordinates. Every node tagged with [tourTarget] reports its unclipped
 * bounds; nodes sharing a spot (a section header and its cards) are joined into one rect.
 */
@Stable
internal class TourTargets {
    private val nodes = mutableStateMapOf<Any, Pair<TourSpot, Rect>>()

    fun rectOf(spot: TourSpot): Rect? {
        var out: Rect? = null
        for ((s, r) in nodes.values) {
            if (s != spot) continue
            out = out?.let { Rect(min(it.left, r.left), min(it.top, r.top), max(it.right, r.right), max(it.bottom, r.bottom)) } ?: r
        }
        return out
    }

    fun put(token: Any, spot: TourSpot, rect: Rect) {
        if (nodes[token] != spot to rect) nodes[token] = spot to rect
    }

    fun remove(token: Any) {
        nodes.remove(token)
    }
}

/** The overlap of two rects, or null when they don't overlap. */
private fun Rect.intersectOrNull(other: Rect): Rect? = if (overlaps(other)) intersect(other) else null

/** Provided only while the tour runs, so [tourTarget] costs nothing otherwise. */
internal val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/** Marks this element as (part of) [spot] for the tour's spotlight. */
@Composable
internal fun Modifier.tourTarget(spot: TourSpot): Modifier {
    val targets = LocalTourTargets.current ?: return this
    val token = remember { Any() }
    DisposableEffect(targets, spot) { onDispose { targets.remove(token) } }
    // Unclipped bounds: a card scrolled partly out of the pane still reports where it really is, so the tour can
    // work out how far to scroll.
    return onGloballyPositioned { targets.put(token, spot, Rect(it.positionInRoot(), it.size.toSize())) }
}

// ---- overlay ----

/**
 * The tour layer, drawn over the whole companion screen. [stepIndex] null hides it (it fades out). [paneBounds] is the
 * content pane's root bounds; pane spots are clipped to it so the spotlight never spills past the scrolling area.
 */
@Composable
internal fun CompanionTourOverlay(
    stepIndex: Int?,
    targets: TourTargets,
    paneBounds: () -> Rect,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val motion = LocalTccLookSpec.current.traits.animate
    // Keeps showing the last step while the layer fades out.
    val last = remember { IntArray(1) }
    val index = stepIndex?.also { last[0] = it } ?: last[0]
    AnimatedVisibility(
        visible = stepIndex != null,
        enter = if (motion) fadeIn(tween(380)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(260)) else ExitTransition.None,
    ) {
        TourLayer(index, targets, paneBounds, motion, onNext, onBack, onSkip)
    }
}

@Composable
private fun TourLayer(
    index: Int,
    targets: TourTargets,
    paneBounds: () -> Rect,
    motion: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = TOUR_STEPS[index]
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }

    val holePad: Float
    val gap: Float
    val margin: Float
    val minHole: Float
    val calloutWidth = 420.dp
    val calloutWidthPx: Float
    with(density) {
        holePad = 8.dp.toPx(); gap = 16.dp.toPx(); margin = 14.dp.toPx(); minHole = 56.dp.toPx()
        calloutWidthPx = calloutWidth.toPx()
    }
    val w = layerSize.width.toFloat()
    val h = layerSize.height.toFloat()
    val ch = calloutSize.height.toFloat()
    val cw = min(calloutWidthPx, w - 2 * margin)

    // The spotlight's target, in this layer's coordinates.
    val spot = step.spot
    var hole: Rect? = spot?.let(targets::rectOf)
        ?.let { if (spot.inPane) it.intersectOrNull(paneBounds()) else it }
        ?.translate(-origin)
        ?.inflate(holePad)
    // Where the callout goes: beside rail spots, below (or above) pane spots, centered with no spot. On the short top
    // screen a tall target (a long list) leaves no room, so its spotlight is cut short to fit the callout underneath.
    val calloutTarget: IntOffset = hole.let { r ->
        when {
            r == null || layerSize == IntSize.Zero -> IntOffset(((w - cw) / 2).roundToInt(), ((h - ch) / 2).roundToInt())
            spot?.inPane == false -> IntOffset(
                (r.right + gap * 1.5f).coerceAtMost(w - cw - margin).roundToInt(),
                (r.center.y - ch / 2).coerceIn(margin, max(margin, h - ch - margin)).roundToInt(),
            )
            else -> {
                val x = (r.center.x - cw / 2).coerceIn(margin, max(margin, w - cw - margin))
                val y = when {
                    r.bottom + gap + ch <= h - margin -> r.bottom + gap
                    r.top - gap - ch >= margin -> r.top - gap - ch
                    else -> {
                        val capped = r.copy(bottom = max(h - margin - ch - gap, r.top + minHole))
                        hole = capped
                        capped.bottom + gap
                    }
                }
                IntOffset(x.roundToInt(), y.roundToInt())
            }
        }
    }

    // Callout position: placed without animation the first time (it's hidden until then), then springs along.
    val calloutAnim = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(calloutTarget, calloutSize) {
        if (calloutSize == IntSize.Zero || layerSize == IntSize.Zero) return@LaunchedEffect
        if (!placed || !motion) {
            calloutAnim.snapTo(calloutTarget)
            placed = true
        } else {
            calloutAnim.animateTo(calloutTarget, spring(dampingRatio = 0.85f, stiffness = 300f))
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OnboardingBackHandler(onBack = onBack)

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot(); layerSize = it.size }
            .focusRequester(focus)
            // Takes every gamepad key so nothing under the tour moves; volume and other system keys pass through.
            .onboardingControls(onNext = onNext, onBack = onBack, onSkip = onSkip)
            .focusable()
            // Taps outside the callout don't reach the settings underneath.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            },
    ) {
        Spotlight(hole, layerSize, motion)
        TourCallout(
            index = index,
            motion = motion,
            modifier = Modifier
                .offset { calloutAnim.value }
                .width(with(density) { cw.coerceAtLeast(0f).toDp() })
                .onSizeChanged { calloutSize = it }
                .graphicsLayer { alpha = if (placed) 1f else 0f },
            onNext = onNext,
            onBack = onBack,
            onSkip = onSkip,
        )
    }
}

/**
 * The dimmed layer with a rounded hole over [hole] (in the layer's coordinates) and a pulsing accent ring. The hole
 * closes to a point when there's nothing to show, and glides and resizes between targets.
 */
@Composable
private fun Spotlight(hole: Rect?, layerSize: IntSize, motion: Boolean) {
    val density = LocalDensity.current
    val accent = p.accent
    val w = layerSize.width.toFloat()
    val h = layerSize.height.toFloat()
    val holeTarget = hole ?: Rect(Offset(w / 2, h / 2), Offset(w / 2, h / 2))
    val holeAnim = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val holeStarted = remember { BooleanArray(1) }
    LaunchedEffect(holeTarget, layerSize) {
        if (layerSize == IntSize.Zero) return@LaunchedEffect
        if (!holeStarted[0]) {
            holeStarted[0] = true
            holeAnim.snapTo(Rect(holeTarget.center, holeTarget.center))
        }
        holeAnim.animateTo(holeTarget, if (motion) spring(dampingRatio = 0.82f, stiffness = 260f) else snap())
    }
    val pulse = if (motion) {
        rememberInfiniteTransition(label = "tourPulse").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tourPulse",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val radius = with(density) { 18.dp.toPx() }
    val ring = with(density) { 2.dp.toPx() }
    val halo = with(density) { 3.dp.toPx() }
    val haloReach = with(density) { 8.dp.toPx() }
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(Color.Black.copy(alpha = 0.66f))
        val r = holeAnim.value
        if (r.width < 1f || r.height < 1f) return@Canvas
        drawRoundRect(Color.Black, r.topLeft, r.size, CornerRadius(radius), blendMode = BlendMode.Clear)
        val t = pulse.value
        val grow = ring + t * haloReach
        drawRoundRect(
            accent.copy(alpha = 0.5f * (1f - t)),
            topLeft = r.topLeft - Offset(grow, grow),
            size = Size(r.width + 2 * grow, r.height + 2 * grow),
            cornerRadius = CornerRadius(radius + grow),
            style = Stroke(halo),
        )
        drawRoundRect(accent, r.topLeft, r.size, CornerRadius(radius), style = Stroke(ring))
    }
}

/**
 * Follows the overlay onboarding's theme step on the companion: while [active], the spotlight sits on the selected
 * built-in look and glides to each preset the user taps on the bottom screen. Touches and keys pass through.
 */
@Composable
internal fun CompanionLookSpotlight(active: Boolean, targets: TourTargets, paneBounds: () -> Rect) {
    val motion = LocalTccLookSpec.current.traits.animate
    AnimatedVisibility(
        visible = active,
        enter = if (motion) fadeIn(tween(380)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(260)) else ExitTransition.None,
    ) {
        val pad = with(LocalDensity.current) { 8.dp.toPx() }
        var origin by remember { mutableStateOf(Offset.Zero) }
        var layerSize by remember { mutableStateOf(IntSize.Zero) }
        val hole = targets.rectOf(TourSpot.BUILTIN_SELECTED)?.intersectOrNull(paneBounds())?.translate(-origin)?.inflate(pad)
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); layerSize = it.size }) {
            Spotlight(hole, layerSize, motion)
        }
    }
}

@Composable
private fun TourCallout(
    index: Int,
    motion: Boolean,
    modifier: Modifier,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    val count = TOUR_STEPS.size
    val lastStep = index == count - 1
    val progress by animateFloatAsState(
        (index + 1f) / count, if (motion) spring(stiffness = 300f) else snap(), label = "tourProgress",
    )
    Column(
        modifier
            // Opaque base under the look's card (which may be translucent glass), as in the update dialog.
            .clip(cardShape()).background(p.bg)
            .companionCard()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                if (!motion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(260, delayMillis = 70)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it / 5 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -dir * it / 5 }) using
                        SizeTransform(clip = false)
                }
            },
            label = "tourStep",
        ) { i -> TourStepText(TOUR_STEPS[i]) }

        // Progress through the whole tour.
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(p.surfaceHigh)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(p.accent))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.tour_step_count, index + 1, count),
                color = p.textTertiary, fontSize = 12.sp, style = tccTextStyle(), modifier = Modifier.weight(1f),
            )
            if (!lastStep) Chip(stringResource(R.string.tour_skip), glyph = GamepadButton.Y, onClick = onSkip)
            if (index > 0) Chip(stringResource(R.string.tour_back), glyph = GamepadButton.B, onClick = onBack)
            AccentButton(stringResource(if (lastStep) R.string.tour_done else R.string.tour_next), glyph = GamepadButton.A, onClick = onNext)
        }
    }
}

@Composable
private fun TourStepText(step: TourStep) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (step.spot == null) {
            AppIconBadge(RoundedCornerShape(11.dp))
            Spacer(Modifier.height(4.dp))
        }
        // Which section this step is in, as a small eyebrow over the title.
        step.section?.takeIf { step.spot != TourSpot.TOUR_REPLAY }?.let {
            Text(
                lookLabel(stringResource(it.titleRes)).uppercase(),
                color = p.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, style = tccTextStyle(),
            )
        }
        val title = if (step.titleRes == R.string.tour_welcome_title) {
            stringResource(step.titleRes, stringResource(R.string.app_full_name))
        } else {
            stringResource(step.titleRes)
        }
        Text(lookLabel(title), color = p.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = tccDisplayTextStyle())
        Text(stringResource(step.bodyRes), color = p.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
