package jr.brian.commandpannel.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import jr.brian.commandpannel.ui.components.noRippleClickable
import jr.brian.commandpannel.ui.components.tccCard
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.zIndex
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.TileIcon
import jr.brian.commandpannel.ui.components.painter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.WebAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlin.math.abs
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.components.OnboardingBackHandler
import jr.brian.commandpannel.ui.components.onboardingControls
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Companion settings screen on the top display. A modern, fully gamepad-navigable two-pane console: an icon nav rail
 * on the left (D-pad up/down moves between sections and auto-switches them) and the selected category's cards on the
 * right (D-pad traverses the items, A activates). Focused items brighten and scale up. By default it uses its own
 * [CompanionPalette.Default] dark style; with "Companion follows overlay look" on, [CompanionTheme] swaps in the
 * overlay's look, theme mode and accent, and the cards / backdrop / type below route through the look renderers.
 */

private enum class NavItem(val category: SettingsCategory, val icon: ImageVector, @StringRes val subtitleRes: Int) {
    GENERAL(SettingsCategory.GENERAL, Icons.Rounded.Tune, R.string.settings_cat_general_desc),
    NAV_BAR(SettingsCategory.NAV_BAR, Icons.Rounded.WebAsset, R.string.settings_cat_nav_bar_desc),
    QUICK_TOGGLES(SettingsCategory.QUICK_TOGGLES, Icons.Rounded.ViewAgenda, R.string.settings_cat_quick_toggles_desc),
    APPEARANCE(SettingsCategory.APPEARANCE, Icons.Rounded.Palette, R.string.settings_cat_appearance_desc),
    THEMES(SettingsCategory.THEMES, Icons.Rounded.Brush, R.string.settings_cat_themes_desc),
    DIAGNOSTICS(SettingsCategory.DIAGNOSTICS, Icons.Rounded.MonitorHeart, R.string.settings_cat_diagnostics_desc),
}

@Composable
fun SettingsScreen(state: SettingsState, vm: SettingsViewModel) {
    val focusedBounds = remember { FocusedBounds() }
    CompanionTheme(state) {
        CompositionLocalProvider(LocalFocusedBounds provides focusedBounds) {
            CompanionNavHost(state, vm)
            // Update / What's New dialog, over whichever screen is showing.
            state.updateDialog?.let { UpdateDialog(it, vm) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsContent(
    state: SettingsState,
    vm: SettingsViewModel,
    openStudio: (jr.brian.commandpannel.ui.theme.CustomLook) -> Unit,
    openThemes: (ThemeLibraryTab) -> Unit,
) {
    // Saveable, so coming back from Theme Studio or the Themes library lands on the same section (and scroll).
    var nav by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(NavItem.GENERAL) }
    // Hoisted so the content pane can send D-pad Left back to the selected rail item.
    val railRequesters = remember { NavItem.entries.associateWith { FocusRequester() } }
    // Re-detect Better Dual Screen Assistant and the other build whenever the companion comes to the front (e.g. back
    // from BDSA's app).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshBdsa()
        vm.refreshSaveClip()
        onPauseOrDispose { }
    }
    val scroll = rememberScrollState()
    val focusedBounds = LocalFocusedBounds.current
    // The section the pane was last reset for: returning to this screen keeps its scroll instead of jumping to the top.
    var scrolledFor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<NavItem?>(null) }
    LaunchedEffect(nav) {
        if (scrolledFor == nav) return@LaunchedEffect
        scrolledFor = nav
        scroll.scrollTo(0)
        // A new section: the previously focused control is gone, so Right from the rail picks the nearest one.
        focusedBounds?.lastInPane = null
    }
    // Root bounds of the scrolling pane (not its content), for drag-to-reorder edge auto-scroll.
    val viewport = remember { mutableStateOf(Rect.Zero) }
    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    // Back from the Themes library, focus goes back to the deck card that opened it.
    val deckFocus = remember { FocusRequester() }
    var returnToDeck by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!returnToDeck) return@LaunchedEffect
        returnToDeck = false
        // After the rail's own first-focus request.
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        runCatching { deckFocus.requestFocus() }
    }
    val tourTargets = remember { TourTargets() }
    val touring = state.tourStep != null
    TourDriver(state.tourStep, tourTargets, scroll, viewport, onSection = { cat -> nav = NavItem.entries.first { it.category == cat } })
    // While the overlay onboarding's theme step runs, the Themes section follows each preset tapped on the bottom screen.
    val overlayStep by jr.brian.commandpannel.ui.overlay.OverlayOnboarding.activeStep.collectAsState()
    val followingLook = overlayStep == jr.brian.commandpannel.ui.overlay.OverlayOnboardingStep.THEME && !touring
    LookFollowDriver(
        followingLook, state.overlayLookRef, tourTargets, scroll, viewport,
        onThemes = { nav = NavItem.entries.first { it.category == SettingsCategory.THEMES } },
    )
    // When the tour ends, gamepad focus goes back to the selected section.
    var wasTouring by remember { mutableStateOf(false) }
    LaunchedEffect(touring) {
        if (touring) wasTouring = true
        else if (wasTouring) runCatching { railRequesters.getValue(nav).requestFocus() }
    }
    // The overlay window can't take focus, so while its onboarding is on screen the companion catches A / B / Y for it.
    // Only while the overlay is listening: hidden mid-onboarding, the companion keeps its own controls.
    val overlayListening by jr.brian.commandpannel.ui.overlay.OverlayOnboarding.commands.subscriptionCount.collectAsState()
    val forwardKeys = overlayStep != null && overlayListening > 0 && !touring
    val forward: (jr.brian.commandpannel.ui.overlay.OnboardingCommand) -> Unit = {
        jr.brian.commandpannel.ui.overlay.OverlayOnboarding.commands.tryEmit(it)
    }
    OnboardingBackHandler(enabled = forwardKeys) { forward(jr.brian.commandpannel.ui.overlay.OnboardingCommand.BACK) }
    Box(
        Modifier.fillMaxSize().onboardingControls(
            enabled = forwardKeys,
            onNext = { forward(jr.brian.commandpannel.ui.overlay.OnboardingCommand.NEXT) },
            onBack = { forward(jr.brian.commandpannel.ui.overlay.OnboardingCommand.BACK) },
            onSkip = { forward(jr.brian.commandpannel.ui.overlay.OnboardingCommand.SKIP) },
            // On the theme step the D-pad and stick move between its three looks; elsewhere they step as usual.
            onDirection = if (overlayStep == jr.brian.commandpannel.ui.overlay.OverlayOnboardingStep.THEME) { d ->
                forward(
                    if (d < 0) jr.brian.commandpannel.ui.overlay.OnboardingCommand.OPTION_PREV
                    else jr.brian.commandpannel.ui.overlay.OnboardingCommand.OPTION_NEXT,
                )
            } else null,
        ),
    ) {
    CompositionLocalProvider(LocalTourTargets provides if (touring || followingLook) tourTargets else null) {
    Row(Modifier.fillMaxSize().then(backdrop).systemBarsPadding()) {
        NavRail(nav, railRequesters) { nav = it }
        PaddedBringIntoView {
        // Marks the pane's controls, so the one you leave is remembered for D-pad Right from the rail.
        CompositionLocalProvider(LocalInContentPane provides true) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .onGloballyPositioned { viewport.value = it.boundsInRoot() }
                // Up past the first control scrolls to the top (section title, the active look, …); Down past the
                // last scrolls to the end.
                // Left goes to a control on the same row, otherwise back to the selected rail item.
                .paneDpadNavigation(scroll) { runCatching { railRequesters.getValue(nav).requestFocus() } }
                .verticalScroll(scroll)
                .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                lookLabel(stringResource(nav.category.titleRes)), color = p.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                style = tccDisplayTextStyle(),
            )
            Text(stringResource(nav.subtitleRes), color = p.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            // Can't be missed: while BDSA (or the other build) controls the hardware, every section opens with the
            // standby banner. BDSA wins over the other build, since it keeps Command Panel on standby either way.
            if (state.bdsa.active) BdsaStandbyBanner(state, vm)
            else if (state.siblingOwner != null) SiblingStandbyBanner(state, vm)
            when (nav.category) {
                SettingsCategory.GENERAL -> General(state, vm)
                SettingsCategory.QUICK_TOGGLES -> QuickToggles(state, vm, scroll) { viewport.value }
                SettingsCategory.NAV_BAR -> NavBarSettings(state, vm, scroll) { viewport.value }
                SettingsCategory.APPEARANCE -> Appearance(state, vm)
                SettingsCategory.THEMES -> Themes(state, vm, openStudio, deckFocus) {
                    returnToDeck = true
                    openThemes(ThemeLibraryTab.LOCAL)
                }
                SettingsCategory.DIAGNOSTICS -> Diagnostics(state, vm)
            }
        }
        }
        }
    }
    }
    CompanionTourOverlay(
        stepIndex = state.tourStep,
        targets = tourTargets,
        paneBounds = { viewport.value },
        onNext = vm::tourNext,
        onBack = vm::tourBack,
        onSkip = vm::endTour,
    )
    CompanionLookSpotlight(followingLook, tourTargets, paneBounds = { viewport.value })
    }
}

/**
 * Opens Themes and scrolls the selected built-in look to the middle of the pane, again each time [look] changes, so
 * the spotlight can glide onto it.
 */
@Composable
private fun LookFollowDriver(
    active: Boolean,
    look: LookRef,
    targets: TourTargets,
    scroll: ScrollState,
    viewport: State<Rect>,
    onThemes: () -> Unit,
) {
    val motion = LocalTccLookSpec.current.traits.animate
    LaunchedEffect(active) { if (active) onThemes() }
    LaunchedEffect(active, look) {
        if (!active) return@LaunchedEffect
        // Wait for the section (and the newly selected row) to lay out before measuring.
        kotlinx.coroutines.withTimeoutOrNull(800) {
            androidx.compose.runtime.snapshotFlow { targets.rectOf(TourSpot.BUILTIN_SELECTED) }.first { it != null }
        } ?: return@LaunchedEffect
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        val rect = targets.rectOf(TourSpot.BUILTIN_SELECTED) ?: return@LaunchedEffect
        val to = (scroll.value + rect.center.y - viewport.value.center.y).roundToInt().coerceIn(0, scroll.maxValue)
        if (to == scroll.value) return@LaunchedEffect
        if (motion) scroll.animateScrollTo(to, androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        else scroll.scrollTo(to)
    }
}

/**
 * Moves the screen to each tour step: selects its section, then scrolls the pane so the spotlit part sits near the
 * top, leaving room for the callout underneath.
 */
@Composable
private fun TourDriver(
    stepIndex: Int?,
    targets: TourTargets,
    scroll: ScrollState,
    viewport: State<Rect>,
    onSection: (SettingsCategory) -> Unit,
) {
    val motion = LocalTccLookSpec.current.traits.animate
    val topInset = with(LocalDensity.current) { FOCUS_SCROLL_MARGIN.toPx() }
    LaunchedEffect(stepIndex) {
        val step = stepIndex?.let(TOUR_STEPS::get) ?: return@LaunchedEffect
        step.section?.let(onSection)
        val spot = step.spot?.takeIf { it.inPane } ?: return@LaunchedEffect
        // Wait for the section to compose and lay out (and its scroll reset to land) before measuring.
        kotlinx.coroutines.withTimeoutOrNull(800) {
            androidx.compose.runtime.snapshotFlow { targets.rectOf(spot) }.first { it != null }
        } ?: return@LaunchedEffect
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        val rect = targets.rectOf(spot) ?: return@LaunchedEffect
        val to = (scroll.value + rect.top - viewport.value.top - topInset).roundToInt().coerceIn(0, scroll.maxValue)
        if (to == scroll.value) return@LaunchedEffect
        if (motion) scroll.animateScrollTo(to, androidx.compose.animation.core.tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        else scroll.scrollTo(to)
    }
}

@Composable
private fun NavRail(selected: NavItem, requesters: Map<NavItem, FocusRequester>, onSelect: (NavItem) -> Unit) {
    val focusedBounds = LocalFocusedBounds.current
    LaunchedEffect(Unit) { runCatching { requesters.getValue(selected).requestFocus() } }
    val themed = LocalCompanionThemed.current
    val navShape = controlShape(13.dp)
    Column(
        // Themed rails are translucent so pattern backdrops (aurora, grids) read across the whole screen.
        modifier = Modifier.width(248.dp).fillMaxHeight().background(if (themed) p.rail.copy(alpha = 0.6f) else p.rail)
            .tourTarget(TourSpot.RAIL)
            // Coming back from the content pane (D-pad Left) lands on the selected section, not the geometrically
            // nearest rail item.
            .focusProperties { onEnter = { requesters.getValue(selected).requestFocus() } }
            .focusGroup()
            // D-pad Right returns to the control you left in the content pane, not just the nearest one.
            .onKeyEvent { e ->
                e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown && focusedBounds?.returnToPane() == true
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconBadge(if (themed) LocalTccLookSpec.current.tile.buttonShape else RoundedCornerShape(11.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                // Debug builds show their launcher name ("TCP Debug"), so the two installs are easy to tell apart.
                Text(stringResource(if (BuildConfig.DEBUG) R.string.app_name else R.string.app_full_name), color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = tccTextStyle())
                Text(lookLabel(stringResource(R.string.navigation_system_set_text)), color = p.textTertiary, fontSize = 11.sp, style = tccTextStyle())
            }
        }
        NavItem.entries.forEach { item ->
            val on = item == selected
            val f = focusState()
            val scale by focusScale(f.value, 1.04f, "navScale")
            val bg by focusColor(
                when { f.value -> p.surfaceHigh; on -> p.accentSoft; else -> Color.Transparent },
                "navBg",
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .tourTarget(TourSpot.railItem(item.category))
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(navShape).background(bg)
                    .then(if (f.value) Modifier.border(1.5.dp, p.accent, navShape) else Modifier)
                    .focusRequester(requesters.getValue(item))
                    .onFocusChanged { if (it.isFocused) onSelect(item) }
                    .focusControl(f) { onSelect(item) }
                    .noRippleClickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, null, tint = if (on || f.value) p.accent else p.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(13.dp))
                Text(
                    lookLabel(stringResource(item.category.titleRes)),
                    color = if (on || f.value) p.textPrimary else p.textSecondary,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    style = tccTextStyle(),
                )
            }
        }
    }
}

/**
 * The launcher icon (its adaptive background + foreground layers) clipped to [shape]. The background is drawn at the
 * adaptive 108/72 ratio so it fills the badge; the foreground is drawn smaller so its tiles get breathing room.
 */
@Composable
internal fun AppIconBadge(shape: Shape) {
    Box(Modifier.size(38.dp).clip(shape), contentAlignment = Alignment.Center) {
        val full = 38.dp * 108f / 72f
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(full))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(full * 0.72f))
    }
}

// ---- General ----

@Composable
private fun General(state: SettingsState, vm: SettingsViewModel) {
    BdsaInactiveNote(state, vm)
    SwitchCard(
        stringResource(R.string.settings_replace_stock),
        when {
            state.bdsa.active -> stringResource(R.string.settings_replace_stock_paused, stringResource(R.string.bdsa_name))
            state.siblingOwner != null -> stringResource(R.string.settings_replace_stock_sibling, state.siblingOwner)
            else -> stringResource(R.string.settings_replace_stock_desc)
        },
        state.replaceStock, vm::setReplaceStock,
        Modifier.tourTarget(TourSpot.REPLACE_STOCK),
    )
    SwitchCard(
        stringResource(R.string.settings_battery_estimate),
        stringResource(R.string.settings_battery_estimate_desc),
        state.batteryEstimate, vm::setBatteryEstimate,
        Modifier.tourTarget(TourSpot.OVERLAY_EXTRAS),
    )
    SwitchCard(
        stringResource(R.string.settings_hide_net_speed),
        stringResource(R.string.settings_hide_net_speed_desc),
        state.hideNetSpeed, vm::setHideNetSpeed,
        Modifier.tourTarget(TourSpot.OVERLAY_EXTRAS),
    )
    SwitchCard(
        stringResource(R.string.settings_keep_on_screen),
        stringResource(R.string.settings_keep_on_screen_desc),
        state.keepOnScreen, vm::setKeepOnScreen,
        Modifier.tourTarget(TourSpot.OVERLAY_EXTRAS),
    )
    Card(Modifier.tourTarget(TourSpot.BACKUP)) {
        CardTitle(stringResource(R.string.settings_backup_title))
        CardDesc(stringResource(R.string.settings_backup_desc))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentButton(stringResource(R.string.settings_backup_export), onClick = vm::exportBackup)
            AccentButton(stringResource(R.string.settings_backup_restore), onClick = vm::restoreLatestBackup)
        }
        state.backupMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
    UpdatesCard(state, vm)
    ReplayBufferCard(state, vm)
    Card(Modifier.tourTarget(TourSpot.PREVIEW)) {
        CardTitle(stringResource(R.string.settings_preview_title))
        CardDesc(stringResource(R.string.settings_preview_desc))
        Spacer(Modifier.height(10.dp))
        AccentButton(stringResource(R.string.settings_preview_open), onClick = vm::showOverlay)
    }
}

@Composable
private fun ReplayBufferCard(state: SettingsState, vm: SettingsViewModel) {
    SwitchCard(
        stringResource(R.string.tile_name_replay_buffer),
        stringResource(R.string.settings_replay_desc),
        state.replayEnabled, vm::setReplayEnabled,
        Modifier.tourTarget(TourSpot.REPLAY),
    )
    Card(Modifier.tourTarget(TourSpot.REPLAY)) {
        CardTitle(stringResource(R.string.settings_replay_options))
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            stringResource(R.string.settings_replay_double_press),
            stringResource(R.string.settings_replay_double_press_desc),
            state.replayDoublePress, vm::setReplayDoublePress,
        )
        SwitchRow(
            stringResource(R.string.settings_replay_game_audio),
            stringResource(R.string.settings_replay_game_audio_desc),
            state.replayGameAudio, vm::setReplayGameAudio,
        )
        SwitchRow(
            stringResource(R.string.settings_replay_mic),
            stringResource(R.string.settings_replay_mic_desc),
            state.replayMic, vm::setReplayMic,
        )
    }
    SaveClipCard(state, vm)
    Card(Modifier.tourTarget(TourSpot.REPLAY)) {
        CardTitle(stringResource(R.string.settings_replay_capture))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            jr.brian.commandpannel.privileged.ReplayTarget.entries.forEach { target ->
                Chip(stringResource(target.labelRes), selected = state.replayTarget == target) { vm.setReplayTarget(target) }
            }
        }
        Spacer(Modifier.height(14.dp))
        CardTitle(stringResource(R.string.settings_replay_save_last))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(15, 30, 60, 90).forEach { secs ->
                Chip(stringResource(R.string.settings_replay_seconds, secs), selected = state.replayWindowSeconds == secs) { vm.setReplayWindowSeconds(secs) }
            }
        }
        Spacer(Modifier.height(14.dp))
        CardTitle(stringResource(R.string.settings_replay_quality))
        jr.brian.commandpannel.privileged.ReplayBitratePreset.entries.forEach { preset ->
            RadioRow(stringResource(preset.labelRes), state.replayPreset == preset) { vm.setReplayPreset(preset) }
        }
        Spacer(Modifier.height(14.dp))
        CardTitle(stringResource(R.string.settings_replay_location))
        Spacer(Modifier.height(10.dp))
        // The system folder picker; clips are written by the root helper, so no lasting folder permission is needed.
        val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> if (uri != null) vm.onReplayFolderPicked(uri) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.settings_replay_location_default), selected = state.replayClipsDir == null) { vm.setReplayClipsDir(null) }
            Chip(stringResource(R.string.settings_replay_location_custom), selected = state.replayClipsDir != null) { pickFolder.launch(null) }
            Chip(stringResource(R.string.settings_replay_location_open)) { vm.openReplayClipsFolder() }
        }
        Spacer(Modifier.height(8.dp))
        InfoLine(stringResource(R.string.settings_replay_location_current, replayClipsPath(state.replayClipsDir)))
        Spacer(Modifier.height(12.dp))
        AccentButton(stringResource(R.string.settings_replay_save_now), enabled = state.replayEnabled, onClick = vm::saveReplayClip)
        state.replayMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
}

/**
 * TCP Save Clip, the trigger app Back Button Hero launches to save a clip: whether it's installed and paired with this
 * build, and an Install / Reinstall button that fetches the one from this version's release. Debug builds pair only
 * with a locally built trigger (signature permission), so they get a note instead of the button.
 */
@Composable
private fun SaveClipCard(state: SettingsState, vm: SettingsViewModel) {
    Card(Modifier.tourTarget(TourSpot.SAVE_CLIP)) {
        CardTitle(stringResource(R.string.settings_save_clip_title))
        CardDesc(stringResource(R.string.settings_save_clip_desc))
        Spacer(Modifier.height(10.dp))
        val status = state.saveClip
        val (line, tone) = when (status) {
            is jr.brian.commandpannel.system.update.SaveClipStatus.Ready ->
                stringResource(R.string.settings_save_clip_ready, status.version) to p.accent
            is jr.brian.commandpannel.system.update.SaveClipStatus.NoPermission ->
                stringResource(R.string.settings_save_clip_no_permission) to p.textPrimary
            jr.brian.commandpannel.system.update.SaveClipStatus.Missing ->
                stringResource(R.string.settings_save_clip_missing) to p.textSecondary
        }
        Text(line, color = tone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(6.dp))
            InfoLine(stringResource(R.string.settings_save_clip_debug_note))
        } else if (status !is jr.brian.commandpannel.system.update.SaveClipStatus.Ready) {
            Spacer(Modifier.height(10.dp))
            // Stays enabled while busy (a disabled button drops gamepad focus); installSaveClip ignores repeats.
            AccentButton(
                stringResource(
                    when {
                        state.saveClipBusy -> R.string.settings_save_clip_installing
                        status is jr.brian.commandpannel.system.update.SaveClipStatus.NoPermission -> R.string.settings_save_clip_reinstall
                        else -> R.string.settings_save_clip_install
                    },
                ),
                onClick = vm::installSaveClip,
            )
        }
        state.saveClipMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
}

@Composable
private fun QuickLaunchCard(state: SettingsState, vm: SettingsViewModel) {
    Card(Modifier.tourTarget(TourSpot.QUICK_LAUNCH)) {
        CardTitle(stringResource(R.string.settings_quick_launch))
        CardDesc(stringResource(R.string.settings_quick_launch_desc))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.quickLaunchComponent?.takeIf { state.quickLaunchLabel != null }?.let { cn ->
                Image(TileIcon.App(cn).painter(), null, Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(state.quickLaunchLabel ?: stringResource(R.string.settings_no_app_chosen), color = p.textPrimary, fontSize = 15.sp, style = tccTextStyle())
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentButton(stringResource(R.string.settings_choose_app), onClick = vm::openAppPicker)
            if (state.quickLaunchLabel != null) AccentButton(stringResource(R.string.settings_clear), onClick = vm::clearQuickLaunch)
        }
        if (state.quickLaunchLabel != null) {
            Spacer(Modifier.height(10.dp))
            CardDesc(stringResource(R.string.settings_open_on))
            jr.brian.commandpannel.system.QuickLaunchDisplay.entries.forEach { d ->
                RadioRow(stringResource(d.labelRes), state.quickLaunchDisplay == d) { vm.setQuickLaunchDisplay(d) }
            }
        }
    }
}

@Composable
private fun AppPicker(state: SettingsState, vm: SettingsViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_choose_an_app), color = p.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        AccentButton(stringResource(R.string.settings_back), onClick = vm::closeAppPicker)
    }
    Spacer(Modifier.height(6.dp))
    if (state.apps.isEmpty()) {
        CardDesc(stringResource(R.string.settings_loading))
    } else {
        Card {
            state.apps.forEach { app ->
                RadioRow(app.label, selected = false) { vm.pickApp(app.component) }
            }
        }
    }
}

// ---- Quick Toggles ----

@Composable
private fun QuickToggles(state: SettingsState, vm: SettingsViewModel, scroll: ScrollState, viewport: () -> Rect) {
    SwitchCard(
        stringResource(R.string.settings_hide_tile_labels),
        stringResource(R.string.settings_hide_tile_labels_desc),
        state.hideTileLabels, vm::setHideTileLabels,
        Modifier.tourTarget(TourSpot.TILE_LABELS),
    )
    SectionHeader(stringResource(R.string.settings_order), Modifier.tourTarget(TourSpot.TILE_ORDER))
    OrderCard(
        modifier = Modifier.tourTarget(TourSpot.TILE_ORDER),
        items = state.orderedEnabledTiles,
        description = stringResource(R.string.settings_tile_order_desc),
        label = { jr.brian.commandpannel.system.QuickSettingTileId.fromKey(it)?.let { id -> stringResource(id.labelRes) } ?: it },
        leading = { key -> jr.brian.commandpannel.system.QuickSettingTileId.fromKey(key)?.let { QuickToggleIcon(state, it) } },
        onCommit = vm::reorderTiles,
        scroll = scroll,
        viewport = viewport,
    )
    jr.brian.commandpannel.system.TileCategory.entries.forEach { cat ->
        SectionHeader(stringResource(cat.titleRes), Modifier.tourTarget(TourSpot.TILE_PICKER))
        Card(Modifier.tourTarget(TourSpot.TILE_PICKER)) {
            jr.brian.commandpannel.system.QuickSettingTileId.entries.filter { it.category == cat }.forEach { tile ->
                val checked = tile.key in state.enabledTiles
                FocusRow(selected = false, onClick = { vm.setTileEnabled(tile.key, !checked) }) {
                    QuickToggleIcon(state, tile)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(tile.labelRes), color = p.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    AccentSwitch(checked) { vm.setTileEnabled(tile.key, it) }
                }
            }
        }
    }
}

/**
 * A quick toggle's icon as the overlay draws it: the overlay look's tile chrome and tint, and the dark / light art for
 * its theme, so it changes along with the look. (Its on / off state isn't shown; this is a settings row.)
 */
@Composable
private fun QuickToggleIcon(state: SettingsState, tile: jr.brian.commandpannel.system.QuickSettingTileId) {
    OverlayLookScope(state) {
        jr.brian.commandpannel.ui.components.TccIconButton(
            icon = tile.icon,
            label = stringResource(tile.labelRes),
            circleSize = 34.dp,
            showLabel = false,
        )
    }
}

/**
 * A drag-and-drop order list (touch: handle or long-press; gamepad: A to pick up, D-pad, A to drop). [items] are
 * keys; [label] names each row and [note] adds an optional second line. [onCommit] receives the new order.
 */
@Composable
private fun OrderCard(
    items: List<String>,
    description: String,
    label: @Composable (String) -> String,
    note: @Composable (String) -> String? = { null },
    /** Optional icon drawn before each row's name. */
    leading: (@Composable (String) -> Unit)? = null,
    onCommit: (List<String>) -> Unit,
    scroll: ScrollState,
    viewport: () -> Rect,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val reorder = remember(scroll) { ReorderListState(items, scope, scroll, viewport) }
    reorder.onCommit = onCommit
    LaunchedEffect(items) { reorder.sync(items) }
    Card(modifier) {
        CardDesc(description)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ORDER_ROW_SPACING)) {
            reorder.items.forEachIndexed { index, k -> key(k) { OrderRow(index, k, label(k), note(k), reorder, leading?.let { { it(k) } }) } }
        }
    }
}

private val ORDER_ROW_SPACING = 4.dp

@Composable
private fun OrderRow(
    index: Int,
    key: String,
    label: String,
    note: String?,
    reorder: ReorderListState,
    leading: (@Composable () -> Unit)? = null,
) {
    val f = focusState()
    val lifted = reorder.isLifted(key)
    val grabbed = reorder.grabbed == key
    val shape = controlShape(12.dp)
    val scale by focusScale(lifted || f.value, if (lifted) 1.03f else 1.015f, "orderScale")
    val bg by focusColor(if (lifted || f.value) p.surfaceHigh else Color.Transparent, "orderBg")
    val spacingPx = with(LocalDensity.current) { ORDER_ROW_SPACING.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val bring = remember { BringIntoViewRequester() }
    val rowCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val handleCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Losing focus while picked up (e.g. touch elsewhere) drops the tile where it is.
    LaunchedEffect(f.value) { if (!f.value && reorder.grabbed == key) reorder.drop() }

    fun begin(coords: LayoutCoordinates?, local: Offset) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        reorder.startDrag(key, coords?.localToRoot(local)?.y ?: 0f)
    }

    Row(
        Modifier.fillMaxWidth()
            .onSizeChanged { reorder.slotPx = it.height + spacingPx }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationY = reorder.translationOf(key)
                scaleX = scale; scaleY = scale
                shadowElevation = if (lifted) 10.dp.toPx() else 0f
                this.shape = shape
            }
            .onGloballyPositioned { rowCoords[0] = it }
            .bringIntoViewRequester(bring)
            .clip(shape).background(bg)
            .then(
                when {
                    grabbed -> Modifier.border(2.dp, p.accent, shape)
                    f.value -> Modifier.border(1.5.dp, p.accent, shape)
                    else -> Modifier
                },
            )
            .onKeyEvent { e ->
                if (!grabbed || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        reorder.moveGrabbed(up = e.key == Key.DirectionUp)
                        scope.launch { bring.bringIntoView() }
                        true
                    }
                    Key.Back, Key.ButtonB, Key.Escape -> { reorder.drop(); true }
                    else -> false
                }
            }
            .focusControl(f) { if (grabbed) reorder.drop() else reorder.grab(key) }
            .pointerInput(key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { begin(rowCoords[0], it) },
                    onDrag = { change, amount -> change.consume(); reorder.dragBy(amount.y) },
                    onDragEnd = reorder::endDrag,
                    onDragCancel = reorder::endDrag,
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", color = p.textTertiary, fontSize = 13.sp, style = tccTextStyle(), modifier = Modifier.width(26.dp))
        leading?.let { it(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(lookLabel(label), color = p.textPrimary, fontSize = 15.sp, style = tccTextStyle())
            if (note != null) Text(note, color = p.textTertiary, fontSize = 12.sp)
        }
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.settings_drag_to_reorder),
            tint = if (lifted || f.value) p.accent else p.textTertiary,
            modifier = Modifier.size(36.dp)
                .onGloballyPositioned { handleCoords[0] = it }
                .pointerInput(key) {
                    detectDragGestures(
                        onDragStart = { begin(handleCoords[0], it) },
                        onDrag = { change, amount -> change.consume(); reorder.dragBy(amount.y) },
                        onDragEnd = reorder::endDrag,
                        onDragCancel = reorder::endDrag,
                    )
                }
                .padding(6.dp),
        )
    }
}

// ---- Nav Bar ----

@Composable
private fun NavBarSettings(state: SettingsState, vm: SettingsViewModel, scroll: ScrollState, viewport: () -> Rect) {
    if (state.appPickerOpen) {
        AppPicker(state, vm)
        return
    }
    SectionHeader(stringResource(R.string.settings_button_order), Modifier.tourTarget(TourSpot.BUTTON_ORDER))
    OrderCard(
        modifier = Modifier.tourTarget(TourSpot.BUTTON_ORDER),
        items = state.navOrder,
        description = stringResource(R.string.settings_button_order_desc),
        label = { key ->
            val button = jr.brian.commandpannel.system.NavBarButton.fromKey(key)
            if (button == jr.brian.commandpannel.system.NavBarButton.LAUNCH && state.quickLaunchLabel != null && state.quickLaunchShowApp) {
                state.quickLaunchLabel
            } else {
                button?.let { stringResource(it.labelRes) } ?: key
            }
        },
        note = { key ->
            when (jr.brian.commandpannel.system.NavBarButton.fromKey(key)) {
                jr.brian.commandpannel.system.NavBarButton.MODE -> stringResource(R.string.settings_nav_note_mode)
                jr.brian.commandpannel.system.NavBarButton.TASK -> stringResource(R.string.settings_nav_note_task)
                jr.brian.commandpannel.system.NavBarButton.LAUNCH ->
                    stringResource(if (state.quickLaunchLabel == null) R.string.settings_nav_note_launch_hidden else R.string.settings_quick_launch)
                jr.brian.commandpannel.system.NavBarButton.SETTINGS -> stringResource(R.string.settings_nav_note_settings)
                jr.brian.commandpannel.system.NavBarButton.APP -> stringResource(R.string.settings_nav_note_app)
                null -> null
            }
        },
        onCommit = vm::reorderNavBar,
        scroll = scroll,
        viewport = viewport,
    )
    SectionHeader(stringResource(R.string.settings_quick_launch), Modifier.tourTarget(TourSpot.QUICK_LAUNCH))
    QuickLaunchCard(state, vm)
    SwitchCard(
        stringResource(R.string.settings_show_app_on_launch),
        stringResource(R.string.settings_show_app_on_launch_desc),
        state.quickLaunchShowApp, vm::setQuickLaunchShowApp,
        Modifier.tourTarget(TourSpot.QUICK_LAUNCH),
    )
}

// ---- Appearance ----

@Composable
private fun Appearance(state: SettingsState, vm: SettingsViewModel) {
    Card(Modifier.tourTarget(TourSpot.OVERLAY_THEME)) {
        CardTitle(stringResource(R.string.settings_overlay_theme))
        Spacer(Modifier.height(6.dp))
        listOf(
            1 to R.string.settings_theme_follow_system, 3 to R.string.settings_theme_light, 4 to R.string.settings_theme_dark,
        ).forEach { (mode, label) ->
            RadioRow(stringResource(label), state.themeMode == mode) { vm.setThemeMode(mode) }
        }
    }
    SectionHeader(stringResource(R.string.settings_overlay_look))
    Card { CardDesc(stringResource(R.string.settings_overlay_look_moved)) }
    SwitchCard(
        stringResource(R.string.settings_companion_follow_look),
        stringResource(R.string.settings_companion_follow_look_desc),
        state.companionFollowLook, vm::setCompanionFollowLook,
        Modifier.tourTarget(TourSpot.FOLLOW_LOOK),
    )
    SwitchCard(
        stringResource(R.string.settings_rgb_accent),
        stringResource(R.string.settings_rgb_accent_desc),
        state.rgbAccentSync, vm::setRgbAccentSync,
        Modifier.tourTarget(TourSpot.RGB_ACCENT),
    )
}

// ---- Diagnostics ----

@Composable
private fun Diagnostics(state: SettingsState, vm: SettingsViewModel) {
    Card(Modifier.tourTarget(TourSpot.PRIVILEGED)) {
        CardTitle(stringResource(R.string.settings_privileged_layer))
        CardDesc(stringResource(R.string.settings_privileged_layer_desc))
        Spacer(Modifier.height(10.dp))
        AccentButton(stringResource(if (state.diagnosticsRunning) R.string.settings_running else R.string.settings_run_check), enabled = !state.diagnosticsRunning, onClick = vm::runDiagnostics)
        if (state.diagnostics.isNotEmpty()) Spacer(Modifier.height(12.dp))
        state.diagnostics.forEach { (text, ok) ->
            val (mark, tone) = when (ok) {
                true -> stringResource(R.string.diag_pass) to p.success
                false -> stringResource(R.string.diag_fail) to p.danger
                else -> stringResource(R.string.diag_info) to p.textTertiary
            }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(mark, color = tone, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.width(48.dp))
                Text(text, color = p.textPrimary, fontSize = 13.sp)
            }
        }
    }
}
