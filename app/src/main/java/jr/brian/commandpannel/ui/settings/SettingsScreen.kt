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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
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
import androidx.compose.ui.draw.rotate
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
    REPLAY(SettingsCategory.REPLAY, Icons.Rounded.Videocam, R.string.settings_cat_replay_desc),
    KEYBOARD(SettingsCategory.KEYBOARD, Icons.Rounded.Keyboard, R.string.settings_cat_keyboard_desc),
    DIAGNOSTICS(SettingsCategory.DIAGNOSTICS, Icons.Rounded.MonitorHeart, R.string.settings_cat_diagnostics_desc),
    EXPERIMENTAL(SettingsCategory.EXPERIMENTAL, Icons.Rounded.Science, R.string.settings_cat_experimental_desc),
}

private fun navItemOf(category: SettingsCategory): NavItem = NavItem.entries.first { it.category == category }

/** Each rail group's header icon. */
private val RailGroup.icon: ImageVector
    get() = when (this) {
        RailGroup.PANEL -> Icons.Rounded.Dashboard
        RailGroup.LOOK -> Icons.Rounded.ColorLens
        RailGroup.EXTRAS -> Icons.Rounded.Extension
        RailGroup.ADVANCED -> Icons.Rounded.Build
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
    // A section asked for from outside (the panel's Keyboard tile opens Keyboard), taken once.
    val requestedSection by jr.brian.commandpannel.CompanionPresence.requestedSection.collectAsState()
    LaunchedEffect(requestedSection) {
        val item = requestedSection?.let { key -> NavItem.entries.firstOrNull { it.category.key == key } } ?: return@LaunchedEffect
        nav = item
        jr.brian.commandpannel.CompanionPresence.requestedSection.value = null
    }
    // Hoisted so the content pane can send D-pad Left back to the selected rail item.
    val railRequesters = remember { NavItem.entries.associateWith { FocusRequester() } }
    val headerRequesters = remember { RailGroup.entries.associateWith { FocusRequester() } }
    // Focuses the selected section on the rail, or its group's header while that group is collapsed.
    val expandedNow by androidx.compose.runtime.rememberUpdatedState(state.railExpanded)
    val focusRail: () -> Unit = {
        val target = railFocusTarget(nav.category, expandedNow)
        runCatching {
            when (target) {
                is RailRow.Section -> railRequesters.getValue(navItemOf(target.category))
                is RailRow.Header -> headerRequesters.getValue(target.group)
            }.requestFocus()
        }
    }
    // Showing a section (picked on the rail, by the tour, or by the onboarding's theme step) opens its group.
    LaunchedEffect(nav) { RailGroup.of(nav.category)?.let { vm.setRailGroupExpanded(it, true) } }
    // Re-detect Better Dual Screen Assistant and the other build whenever the companion comes to the front (e.g. back
    // from BDSA's app).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshBdsa()
        vm.refreshSaveClip()
        vm.refreshThorKbm()
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
        else if (wasTouring) focusRail()
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
    Row(
        Modifier.fillMaxSize().then(backdrop).systemBarsPadding()
            // Y only has a job on a group header (pick it up / drop it) and in the tour. Anywhere else nothing handles
            // it, and the system turns an unhandled Y into Back, which sends Command Panel to the background.
            .onKeyEvent { it.key == Key.ButtonY },
    ) {
        // Like the pane: focus (and a section being moved) scrolls in with room for its focus ring and scale.
        PaddedBringIntoView {
            NavRail(
                nav, state.railLayout, state.railExpanded, railRequesters, headerRequesters,
                onToggle = vm::setRailGroupExpanded, onReorder = vm::reorderRailSections,
                onReorderGroups = vm::reorderRailGroups, focusSelected = focusRail,
            ) { nav = it }
        }
        PaddedBringIntoView {
        // Marks the pane's controls, so the one you leave is remembered for D-pad Right from the rail.
        CompositionLocalProvider(LocalInContentPane provides true) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .onGloballyPositioned { viewport.value = it.boundsInRoot() }
                // Up past the first control scrolls to the top (section title, the active look, …); Down past the
                // last scrolls to the end.
                // Left goes to a control on the same row, otherwise back to the selected rail item.
                .paneDpadNavigation(scroll) { focusRail() }
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
                SettingsCategory.REPLAY -> ReplayBufferCard(state, vm)
                SettingsCategory.KEYBOARD -> ThorKbmCard(state, vm)
                SettingsCategory.DIAGNOSTICS -> Diagnostics(state, vm)
                SettingsCategory.EXPERIMENTAL -> Experimental(state, vm)
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

/**
 * The settings rail: General on top, then the collapsible groups. Sections of a collapsed group aren't composed, so
 * focus can't land on them. Focusing a header doesn't change the pane; only sections do.
 */
@Composable
private fun NavRail(
    selected: NavItem,
    layout: RailLayout,
    expanded: Set<RailGroup>,
    requesters: Map<NavItem, FocusRequester>,
    headerRequesters: Map<RailGroup, FocusRequester>,
    onToggle: (RailGroup, Boolean) -> Unit,
    onReorder: (RailGroup, List<String>) -> Unit,
    onReorderGroups: (List<String>) -> Unit,
    focusSelected: () -> Unit,
    onSelect: (NavItem) -> Unit,
) {
    val focusedBounds = LocalFocusedBounds.current
    LaunchedEffect(Unit) { focusSelected() }
    val themed = LocalCompanionThemed.current
    val navShape = controlShape(13.dp)
    // Scrolls when the sections don't all fit; its bounds drive edge auto-scroll while a section is dragged.
    val railScroll = rememberScrollState()
    val railBounds = remember { mutableStateOf(Rect.Zero) }
    val scope = rememberCoroutineScope()
    // Groups move among themselves. While one is lifted every group shows collapsed, so the rows being moved are all
    // header height and the plain list reorder works as is.
    val groupKeys = layout.groups.map { it.key }
    val groupReorder = remember(railScroll) { ReorderListState(groupKeys, scope, railScroll) { railBounds.value } }
    groupReorder.onCommit = onReorderGroups
    LaunchedEffect(groupKeys) { groupReorder.sync(groupKeys) }
    val movingGroup = groupReorder.grabbed != null || groupReorder.dragged != null
    val shown = if (movingGroup) emptySet() else expanded
    // Sections move only inside their own group, so each group has its own reorder state.
    val reorders = RailGroup.entries.associateWith { g ->
        val keys = layout.sectionsOf(g).map { it.key }
        val r = remember(railScroll, g) { ReorderListState(keys, scope, railScroll) { railBounds.value } }
        r.onCommit = { onReorder(g, it) }
        LaunchedEffect(r, keys) { r.sync(keys) }
        r
    }
    val moving = movingGroup || reorders.values.any { it.grabbed != null }
    val rows = layout.visibleRows(shown)
    val motion = LocalTccLookSpec.current.traits.animate
    // The focused row's own bring-into-view stops it flush with the padded edge, which leaves the app header scrolled
    // off. Focusing the first (or last) visible row scrolls the rail all the way to that end instead. Skipped while a
    // section is picked up, since moving it already brings it into view.
    val edgeScroll: (String) -> Unit = { id ->
        val atTop = id == rows.firstOrNull()?.id
        if (!moving && (atTop || id == rows.lastOrNull()?.id)) scope.launch {
            // Wait a frame so this runs after (and cancels) the focus bring-into-view instead of being undone by it.
            androidx.compose.runtime.withFrameNanos { }
            val target = if (atTop) 0 else railScroll.maxValue
            if (motion) railScroll.animateScrollTo(target) else railScroll.scrollTo(target)
        }
    }
    Column(
        // Themed rails are translucent so pattern backdrops (aurora, grids) read across the whole screen.
        modifier = Modifier.width(248.dp).fillMaxHeight().background(if (themed) p.rail.copy(alpha = 0.6f) else p.rail)
            .tourTarget(TourSpot.RAIL)
            // Coming back from the content pane (D-pad Left) lands on the selected section (or its collapsed group's
            // header), not the geometrically nearest rail item.
            .focusProperties { onEnter = { focusSelected() } }
            .focusGroup()
            // D-pad Right returns to the control you left in the content pane, not just the nearest one.
            .onKeyEvent { e ->
                e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown && focusedBounds?.returnToPane() == true
            }
            .onGloballyPositioned { railBounds.value = it.boundsInRoot() }
            .verticalScroll(railScroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(RAIL_ITEM_SPACING),
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
        val pinned = navItemOf(RailGroup.PINNED)
        key(pinned.category.key) {
            RailItem(pinned, pinned == selected, requesters.getValue(pinned), null, navShape, onSelect) { edgeScroll(pinned.category.key) }
        }
        groupReorder.items.forEach { gk ->
            val g = RailGroup.fromKey(gk) ?: return@forEach
            // The whole group (header and its sections) is keyed, so it moves as one block. Keying only the header let
            // a reopened group's sections take its slot, which recreated the header and threw away its focus.
            key(gk) { RailGroupBlock(g, g in shown, selected, headerRequesters, requesters, reorders.getValue(g), groupReorder, navShape, onToggle, onSelect, edgeScroll) }
        }
    }
}

@Composable
private fun RailGroupBlock(
    g: RailGroup,
    open: Boolean,
    selected: NavItem,
    headerRequesters: Map<RailGroup, FocusRequester>,
    requesters: Map<NavItem, FocusRequester>,
    reorder: ReorderListState,
    groupReorder: ReorderListState,
    navShape: Shape,
    onToggle: (RailGroup, Boolean) -> Unit,
    onSelect: (NavItem) -> Unit,
    edgeScroll: (String) -> Unit,
) {
    val header = RailRow.Header(g).id
    RailGroupHeader(
        g, open, holdsSelected = RailGroup.of(selected.category) == g, headerRequesters.getValue(g), navShape,
        groupReorder, onToggle = { onToggle(g, it) },
    ) { edgeScroll(header) }
    if (!open) return
    reorder.items.forEach { k ->
        val item = NavItem.entries.firstOrNull { it.category.key == k } ?: return@forEach
        key(k) {
            RailItem(
                item, item == selected, requesters.getValue(item), reorder, navShape, onSelect,
                indent = true, onLeft = { runCatching { headerRequesters.getValue(g).requestFocus() } },
            ) { edgeScroll(k) }
        }
    }
}

/**
 * A rail group's header. A, a tap, or D-pad Right (while collapsed) opens it; A, a tap, or D-pad Left (while open)
 * closes it. D-pad Right on an open header goes to the pane, like a section. Focusing it doesn't change the pane.
 * Moving the group: Y picks it up (A is open / close here), the D-pad moves it, and A, Y or B drops it. Touch: drag the
 * trailing handle.
 */
@Composable
private fun RailGroupHeader(
    group: RailGroup,
    open: Boolean,
    holdsSelected: Boolean,
    requester: FocusRequester,
    shape: Shape,
    reorder: ReorderListState,
    onToggle: (Boolean) -> Unit,
    onFocused: () -> Unit,
) {
    val key = group.key
    val f = focusState()
    val lifted = reorder.isLifted(key)
    val grabbed = reorder.grabbed == key
    val scale by focusScale(f.value || lifted, if (lifted) 1.06f else 1.04f, "navScale")
    val spacingPx = with(LocalDensity.current) { RAIL_ITEM_SPACING.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val bring = remember { BringIntoViewRequester() }
    val handleCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Losing focus while picked up (e.g. a tap elsewhere) drops the group where it is.
    LaunchedEffect(f.value) { if (!f.value && reorder.grabbed == key) reorder.drop() }
    // Moving the header row to its new place drops its focus, so once the group is put down (gamepad or touch), focus
    // comes back to it instead of landing on the selected section.
    val wasLifted = remember { booleanArrayOf(false) }
    LaunchedEffect(lifted) {
        if (lifted) { wasLifted[0] = true; return@LaunchedEffect }
        if (!wasLifted[0]) return@LaunchedEffect
        wasLifted[0] = false
        // After its group's sections open again and the rail settles.
        repeat(2) { androidx.compose.runtime.withFrameNanos { } }
        runCatching { requester.requestFocus() }
    }
    // Where the header sat when a touch drag began, and its place in the list then. Groups above it fold as the drag
    // starts, which moves it up; the first layout after that shifts the drag by the same amount so the header stays
    // under the finger.
    val dragStart = remember { floatArrayOf(Float.NaN, -1f) }
    val headerTop = remember { floatArrayOf(0f) }
    // A collapsed group holding the selected section keeps its highlight, so you can see where you are.
    val bg by focusColor(
        when { f.value || lifted -> p.surfaceHigh; holdsSelected && !open -> p.accentSoft; else -> Color.Transparent },
        "navBg",
    )
    val motion = LocalTccLookSpec.current.traits.animate
    val turn by androidx.compose.animation.core.animateFloatAsState(
        if (open) 180f else 0f,
        if (motion) androidx.compose.animation.core.tween(180) else androidx.compose.animation.core.snap(),
        label = "railChevron",
    )
    val label = stringResource(group.titleRes)
    val lit = f.value || lifted || holdsSelected
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (holdsSelected) Modifier.tourTarget(TourSpot.RAIL_GROUP) else Modifier)
            .onSizeChanged { reorder.slotPx = it.height + spacingPx }
            // Before graphicsLayer, so this is the laid-out spot, without the drag translation.
            .onGloballyPositioned { c ->
                val top = c.positionInRoot().y
                val from = dragStart[0]
                if (!from.isNaN() && top != from) {
                    // Only the fold, not a slot swap (those already adjust the drag themselves).
                    if (reorder.dragged == key && reorder.items.indexOf(key) == dragStart[1].toInt()) reorder.shiftDrag(from - top)
                    dragStart[0] = Float.NaN
                }
                headerTop[0] = top
            }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationY = reorder.translationOf(key)
                scaleX = scale; scaleY = scale
                shadowElevation = if (lifted) 10.dp.toPx() else 0f
                this.shape = shape
            }
            .bringIntoViewRequester(bring)
            .clip(shape).background(bg)
            .then(
                when {
                    grabbed -> Modifier.border(2.dp, p.accent, shape)
                    f.value -> Modifier.border(1.5.dp, p.accent, shape)
                    else -> Modifier
                },
            )
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (grabbed) return@onKeyEvent when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        reorder.moveGrabbed(up = e.key == Key.DirectionUp)
                        scope.launch { bring.bringIntoView() }
                        true
                    }
                    // Stays on the rail while a group is picked up.
                    Key.DirectionLeft, Key.DirectionRight -> true
                    Key.ButtonY, Key.Back, Key.ButtonB, Key.Escape -> { reorder.drop(); true }
                    else -> false
                }
                when (e.key) {
                    Key.ButtonY -> { reorder.grab(key); true }
                    Key.DirectionRight -> if (!open) { onToggle(true); true } else false
                    Key.DirectionLeft -> if (open) { onToggle(false); true } else false
                    else -> false
                }
            }
            .focusControl(f) { if (grabbed) reorder.drop() else onToggle(!open) }
            .noRippleClickable {
                // Closing by touch while one of its sections has focus: keep focus on the rail, on this header.
                if (open) runCatching { requester.requestFocus() }
                onToggle(!open)
            }
            .padding(start = 14.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(group.icon, null, tint = if (lit) p.accent else p.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(13.dp))
        Text(
            lookLabel(label),
            color = if (lit) p.textPrimary else p.textSecondary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            style = tccTextStyle(),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = stringResource(if (open) R.string.settings_rail_collapse else R.string.settings_rail_expand, label),
            tint = if (f.value) p.accent else p.textTertiary,
            modifier = Modifier.size(30.dp).rotate(turn).padding(5.dp),
        )
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.settings_rail_move, label),
            tint = if (lifted || f.value) p.accent else p.textTertiary,
            modifier = Modifier.size(30.dp)
                .onGloballyPositioned { handleCoords[0] = it }
                .pointerInput(key) {
                    detectDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Its sections fold away while it moves: keep focus on the rail, on this header.
                            runCatching { requester.requestFocus() }
                            dragStart[0] = headerTop[0]
                            dragStart[1] = reorder.items.indexOf(key).toFloat()
                            reorder.startDrag(key, handleCoords[0]?.localToRoot(it)?.y ?: 0f)
                        },
                        onDrag = { change, amount -> change.consume(); reorder.dragBy(amount.y) },
                        onDragEnd = { dragStart[0] = Float.NaN; reorder.endDrag() },
                        onDragCancel = { dragStart[0] = Float.NaN; reorder.endDrag() },
                    )
                }
                .padding(5.dp),
        )
    }
}

/**
 * One rail section. Focus selects it; A picks it up, the D-pad moves it (within its group), and A (or B) drops it,
 * like Button Order. Touch: tap selects, and dragging the trailing handle moves it. With no [reorder] (General, which
 * is pinned) it can't be moved. [onLeft] is where D-pad Left goes (a grouped section's header).
 */
@Composable
private fun RailItem(
    item: NavItem,
    on: Boolean,
    requester: FocusRequester,
    reorder: ReorderListState?,
    shape: Shape,
    onSelect: (NavItem) -> Unit,
    indent: Boolean = false,
    onLeft: (() -> Unit)? = null,
    onFocused: () -> Unit,
) {
    val key = item.category.key
    val f = focusState()
    val lifted = reorder?.isLifted(key) == true
    val grabbed = reorder?.grabbed == key
    val scale by focusScale(f.value || lifted, if (lifted) 1.06f else 1.04f, "navScale")
    val bg by focusColor(
        when { f.value || lifted -> p.surfaceHigh; on -> p.accentSoft; else -> Color.Transparent },
        "navBg",
    )
    val spacingPx = with(LocalDensity.current) { RAIL_ITEM_SPACING.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val bring = remember { BringIntoViewRequester() }
    val handleCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // During the tour, the section it's on scrolls into view on the rail, so its spotlight isn't off screen.
    val touring = LocalTourTargets.current != null
    LaunchedEffect(on, touring) {
        if (!on || !touring) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        bring.bringIntoView()
    }
    // Losing focus while picked up (e.g. a tap elsewhere) drops the section where it is.
    LaunchedEffect(f.value) { if (!f.value && reorder?.grabbed == key) reorder.drop() }
    Row(
        modifier = Modifier.fillMaxWidth()
            .tourTarget(TourSpot.railItem(item.category))
            .onSizeChanged { reorder?.slotPx = it.height + spacingPx }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationY = reorder?.translationOf(key) ?: 0f
                scaleX = scale; scaleY = scale
                shadowElevation = if (lifted) 10.dp.toPx() else 0f
                this.shape = shape
            }
            .bringIntoViewRequester(bring)
            .clip(shape).background(bg)
            .then(
                when {
                    grabbed -> Modifier.border(2.dp, p.accent, shape)
                    f.value -> Modifier.border(1.5.dp, p.accent, shape)
                    else -> Modifier
                },
            )
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) { onSelect(item); onFocused() } }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (!grabbed || reorder == null) {
                    // Left from a grouped section goes up to its header.
                    return@onKeyEvent if (e.key == Key.DirectionLeft && onLeft != null) { onLeft(); true } else false
                }
                when (e.key) {
                    Key.DirectionUp, Key.DirectionDown -> {
                        reorder.moveGrabbed(up = e.key == Key.DirectionUp)
                        scope.launch { bring.bringIntoView() }
                        true
                    }
                    // Stays on the rail while a section is picked up.
                    Key.DirectionLeft, Key.DirectionRight -> true
                    Key.Back, Key.ButtonB, Key.Escape -> { reorder.drop(); true }
                    else -> false
                }
            }
            .focusControl(f) { reorder?.let { if (grabbed) it.drop() else it.grab(key) } }
            .noRippleClickable { onSelect(item) }
            .padding(start = if (indent) 26.dp else 14.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = if (on || f.value || lifted) p.accent else p.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(13.dp))
        Text(
            lookLabel(stringResource(item.category.titleRes)),
            color = if (on || f.value || lifted) p.textPrimary else p.textSecondary,
            fontSize = 15.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            style = tccTextStyle(),
            modifier = Modifier.weight(1f),
        )
        // General is pinned: no handle, but the same room, so every row is the same height.
        if (reorder == null) { Spacer(Modifier.size(30.dp)); return@Row }
        Icon(
            Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.settings_rail_move, stringResource(item.category.titleRes)),
            tint = if (lifted || f.value) p.accent else p.textTertiary,
            // Small enough that a row is no taller than before the handle, so every section fits on the rail.
            modifier = Modifier.size(30.dp)
                .onGloballyPositioned { handleCoords[0] = it }
                .pointerInput(key) {
                    detectDragGestures(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            reorder.startDrag(key, handleCoords[0]?.localToRoot(it)?.y ?: 0f)
                        },
                        onDrag = { change, amount -> change.consume(); reorder.dragBy(amount.y) },
                        onDragEnd = reorder::endDrag,
                        onDragCancel = reorder::endDrag,
                    )
                }
                .padding(5.dp),
        )
    }
}

private val RAIL_ITEM_SPACING = 6.dp

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
    // First after the main switch: the way to save clips from Back Button Hero.
    SaveClipCard(state, vm)
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
        val soundNote = jr.brian.commandpannel.privileged.ReplaySoundStatus.note(
            soundOn = state.replayGameAudio || state.replayMic,
            helperDown = state.replayHelperDown,
            armed = state.replayEnabled,
            videoOnly = state.replaySoundVideoOnly,
        )
        soundNote?.let { note ->
            val text = when (note) {
                jr.brian.commandpannel.privileged.ReplaySoundStatus.Note.NO_HELPER -> R.string.settings_replay_sound_no_helper
                jr.brian.commandpannel.privileged.ReplaySoundStatus.Note.VIDEO_ONLY -> R.string.settings_replay_sound_video_only
            }
            Spacer(Modifier.height(8.dp))
            InfoLine(stringResource(text), p.danger)
        }
    }
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
 * TCP SC (TCP Save Clip), the trigger app Back Button Hero launches to save a clip: whether it's installed and paired
 * with this build. Install / Reinstall fetches the one from this version's release; once it's ready, its status pill
 * says it's already installed when pressed. Debug builds pair only with a locally built trigger (signature
 * permission), so they get a note instead.
 */
@Composable
private fun SaveClipCard(state: SettingsState, vm: SettingsViewModel) {
    val status = state.saveClip
    Card(Modifier.tourTarget(TourSpot.SAVE_CLIP)) {
        CardTitle(stringResource(R.string.settings_save_clip_title))
        CardDesc(stringResource(R.string.settings_save_clip_desc))
        Spacer(Modifier.height(10.dp))
        when {
            status is jr.brian.commandpannel.system.update.SaveClipStatus.Ready && !BuildConfig.DEBUG ->
                // installSaveClip only toasts that it's already installed.
                SaveClipAction(stringResource(R.string.settings_save_clip_ready, status.version), filled = false, onClick = vm::installSaveClip)
            status is jr.brian.commandpannel.system.update.SaveClipStatus.Ready ->
                Text(stringResource(R.string.settings_save_clip_ready, status.version), color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
            else -> Text(
                stringResource(
                    if (status is jr.brian.commandpannel.system.update.SaveClipStatus.NoPermission) R.string.settings_save_clip_no_permission
                    else R.string.settings_save_clip_missing,
                ),
                color = if (status is jr.brian.commandpannel.system.update.SaveClipStatus.NoPermission) p.textPrimary else p.textSecondary,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(),
            )
        }
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(6.dp))
            InfoLine(stringResource(R.string.settings_save_clip_debug_note))
        } else if (status !is jr.brian.commandpannel.system.update.SaveClipStatus.Ready) {
            Spacer(Modifier.height(10.dp))
            // Stays pressable while busy (a disabled control drops gamepad focus); installSaveClip ignores repeats.
            SaveClipAction(
                stringResource(
                    when {
                        state.saveClipBusy -> R.string.settings_save_clip_installing
                        status is jr.brian.commandpannel.system.update.SaveClipStatus.NoPermission -> R.string.settings_save_clip_reinstall
                        else -> R.string.settings_save_clip_install
                    },
                ),
                filled = true,
                onClick = vm::installSaveClip,
            )
        }
        state.saveClipMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
}

/**
 * ThorKBM, the keyboard and mouse app: Install fetches its latest release once; after that the card shows the
 * installed version and Open, and ThorKBM's own updater takes over. Status and Install follow the release only; a
 * debug build gets a note, and Open falls back to it when it's the only one.
 */
@Composable
private fun ThorKbmCard(state: SettingsState, vm: SettingsViewModel) {
    Card {
        // The preview sits beside the details so the whole card fits on the top screen; on a card too narrow for that
        // (a wide look, a big font scale) it goes above them instead of squeezing the text.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            if (maxWidth >= THORKBM_PREVIEW_WIDTH + THORKBM_PREVIEW_GAP + THORKBM_MIN_DETAILS_WIDTH * fontScale) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThorKbmDetails(state, vm, previewAbove = false)
                    }
                    Spacer(Modifier.width(THORKBM_PREVIEW_GAP))
                    ThorKbmPreview()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { ThorKbmDetails(state, vm, previewAbove = true) }
            }
        }
    }
}

/** The ThorKBM card's text and buttons, with the preview between the description and the status when [previewAbove]. */
@Composable
private fun ThorKbmDetails(state: SettingsState, vm: SettingsViewModel, previewAbove: Boolean) {
    val status = state.thorKbm
    CardTitle(stringResource(R.string.settings_thorkbm_title))
    CardDesc(stringResource(R.string.settings_thorkbm_desc))
    InfoLine(stringResource(R.string.settings_thorkbm_plugin_note), tone = p.textTertiary)
    if (previewAbove) {
        Spacer(Modifier.height(10.dp))
        ThorKbmPreview()
    }
    Spacer(Modifier.height(10.dp))
    Text(
        status.releaseVersion?.let { stringResource(R.string.settings_thorkbm_installed, it) }
            ?: stringResource(R.string.settings_thorkbm_missing),
        color = if (status.releaseInstalled) p.accent else p.textSecondary,
        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(),
    )
    status.debugVersion?.let {
        Spacer(Modifier.height(6.dp))
        InfoLine(stringResource(R.string.settings_thorkbm_debug_also, it))
    }
    Spacer(Modifier.height(10.dp))
    // Wraps on a narrow column (Open on GitHub drops to its own row); the D-pad moves across and down between them.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!status.releaseInstalled) {
            // Stays pressable while busy (a disabled control drops gamepad focus); installThorKbm ignores repeats.
            SaveClipAction(
                stringResource(if (state.thorKbmBusy) R.string.settings_thorkbm_installing else R.string.settings_thorkbm_install),
                filled = true,
                onClick = vm::installThorKbm,
            )
        }
        if (status.openPackage != null) {
            SaveClipAction(stringResource(R.string.settings_thorkbm_open), filled = status.releaseInstalled, onClick = vm::openThorKbm)
        }
        SaveClipAction(stringResource(R.string.settings_thorkbm_github), filled = false, onClick = vm::openThorKbmGitHub)
    }
    state.thorKbmMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
}

/** ThorKBM on the bottom screen, shown whether or not it's installed. Not a focus target. */
@Composable
private fun ThorKbmPreview() {
    // A black frame (the capture's own background) around the screenshot, so its keys and trackpad don't touch the
    // rounded border.
    Box(
        Modifier.size(THORKBM_PREVIEW_WIDTH, THORKBM_PREVIEW_HEIGHT).clip(cardShape())
            .background(THORKBM_PREVIEW_FRAME).padding(THORKBM_PREVIEW_MARGIN),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.thorkbm_preview),
            contentDescription = stringResource(R.string.settings_thorkbm_preview_desc),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * The ThorKBM preview's size (its asset is a 1240x1080 bottom-screen capture), sized so the card fits on the 1080px top
 * screen under the section header, and the narrowest the details column may get (times the font scale) beside it.
 */
private val THORKBM_PREVIEW_HEIGHT = 200.dp
private val THORKBM_PREVIEW_WIDTH = THORKBM_PREVIEW_HEIGHT * (1240f / 1080f)
private val THORKBM_PREVIEW_GAP = 20.dp
/** The frame between the screenshot and the preview's rounded border, in the capture's own black. */
private val THORKBM_PREVIEW_MARGIN = 10.dp
private val THORKBM_PREVIEW_FRAME = Color.Black
private val THORKBM_MIN_DETAILS_WIDTH = 220.dp

/**
 * The TCP SC and ThorKBM cards' controls: the Install button ([filled]) and the "Installed" status pill. Focus is unmistakable on
 * any look: a solid accent fill, a bright outline and a bigger scale.
 */
@Composable
private fun SaveClipAction(text: String, filled: Boolean, onClick: () -> Unit) {
    val f = focusState()
    val shape = controlShape(12.dp)
    val scale by focusScale(f.value, 1.08f, "saveClipScale")
    val bg by focusColor(
        when { f.value -> p.accentFocused; filled -> p.accent; else -> p.accentSoft },
        "saveClipBg",
    )
    val fg = if (f.value || filled) p.onAccent else p.accent
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .border(if (f.value) 2.5.dp else 1.5.dp, if (f.value) p.textPrimary else p.accent, shape)
            .focusControl(f, onClick)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(lookLabel(text), color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
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
            Spacer(Modifier.height(10.dp))
            CardDesc(stringResource(R.string.settings_after_launch))
            jr.brian.commandpannel.system.QuickLaunchAfter.entries.forEach { a ->
                RadioRow(stringResource(a.labelRes), state.quickLaunchAfter == a) { vm.setQuickLaunchAfter(a) }
            }
            val after = state.quickLaunchAfter
            if (after.effectiveFor(state.quickLaunchDisplay) != after) {
                CardDesc(stringResource(R.string.settings_after_launch_keep_open_note))
            } else if (after == jr.brian.commandpannel.system.QuickLaunchAfter.REOPEN) {
                CardDesc(stringResource(R.string.settings_after_launch_reopen_note))
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

// ---- Experimental ----

@Composable
private fun Experimental(state: SettingsState, vm: SettingsViewModel) {
    SwitchCard(
        stringResource(R.string.settings_task_fps),
        stringResource(R.string.settings_task_fps_desc),
        state.taskFps, vm::setTaskFps,
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
