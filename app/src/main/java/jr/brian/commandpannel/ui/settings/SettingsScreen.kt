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
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.TileIcon
import jr.brian.commandpannel.ui.components.painter
import kotlinx.coroutines.launch
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
import androidx.compose.runtime.getValue
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
    CompanionTheme(state) { SettingsContent(state, vm) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsContent(state: SettingsState, vm: SettingsViewModel) {
    var nav by remember { mutableStateOf(NavItem.GENERAL) }
    // Re-detect Better Dual Screen Assistant whenever the companion comes to the front (e.g. back from BDSA's app).
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshBdsa()
        onPauseOrDispose { }
    }
    // Theme Studio replaces the whole two-pane screen while it's open.
    var studio by remember { mutableStateOf<jr.brian.commandpannel.ui.theme.CustomLook?>(null) }
    studio?.let { look ->
        ThemeStudioScreen(look, state, vm, onClose = { studio = null })
        return
    }
    val scroll = rememberScrollState()
    LaunchedEffect(nav) { scroll.scrollTo(0) }
    // Root bounds of the scrolling pane (not its content), for drag-to-reorder edge auto-scroll.
    val viewport = remember { mutableStateOf(Rect.Zero) }
    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    Row(Modifier.fillMaxSize().then(backdrop).systemBarsPadding()) {
        NavRail(nav) { nav = it }
        PaddedBringIntoView {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .onGloballyPositioned { viewport.value = it.boundsInRoot() }
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
            // Can't be missed: while BDSA controls the hardware, every section opens with the standby banner.
            if (state.bdsa.active) BdsaStandbyBanner(state, vm)
            when (nav.category) {
                SettingsCategory.GENERAL -> General(state, vm)
                SettingsCategory.QUICK_TOGGLES -> QuickToggles(state, vm, scroll) { viewport.value }
                SettingsCategory.NAV_BAR -> NavBarSettings(state, vm, scroll) { viewport.value }
                SettingsCategory.APPEARANCE -> Appearance(state, vm)
                SettingsCategory.THEMES -> Themes(state, vm) { studio = it }
                SettingsCategory.DIAGNOSTICS -> Diagnostics(state, vm)
            }
        }
        }
    }
}

@Composable
private fun NavRail(selected: NavItem, onSelect: (NavItem) -> Unit) {
    val requesters = remember { NavItem.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { runCatching { requesters.getValue(selected).requestFocus() } }
    val themed = LocalCompanionThemed.current
    val navShape = controlShape(13.dp)
    Column(
        // Themed rails are translucent so pattern backdrops (aurora, grids) read across the whole screen.
        modifier = Modifier.width(248.dp).fillMaxHeight().background(if (themed) p.rail.copy(alpha = 0.6f) else p.rail)
            // Coming back from the content pane (D-pad Left) lands on the selected section, not the geometrically
            // nearest rail item.
            .focusProperties { onEnter = { requesters.getValue(selected).requestFocus() } }
            .focusGroup()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 8.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconBadge(if (themed) LocalTccLookSpec.current.tile.buttonShape else RoundedCornerShape(11.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.app_name), color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = tccTextStyle())
                Text(lookLabel(stringResource(R.string.app_full_name)), color = p.textTertiary, fontSize = 11.sp, style = tccTextStyle())
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
private fun AppIconBadge(shape: Shape) {
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
        if (state.bdsa.active) stringResource(R.string.settings_replace_stock_paused, stringResource(R.string.bdsa_name))
        else stringResource(R.string.settings_replace_stock_desc),
        state.replaceStock, vm::setReplaceStock,
    )
    SwitchCard(
        stringResource(R.string.settings_battery_estimate),
        stringResource(R.string.settings_battery_estimate_desc),
        state.batteryEstimate, vm::setBatteryEstimate,
    )
    SwitchCard(
        stringResource(R.string.settings_hide_net_speed),
        stringResource(R.string.settings_hide_net_speed_desc),
        state.hideNetSpeed, vm::setHideNetSpeed,
    )
    SwitchCard(
        stringResource(R.string.settings_keep_on_screen),
        stringResource(R.string.settings_keep_on_screen_desc),
        state.keepOnScreen, vm::setKeepOnScreen,
    )
    Card {
        CardTitle(stringResource(R.string.settings_backup_title))
        CardDesc(stringResource(R.string.settings_backup_desc))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentButton(stringResource(R.string.settings_backup_export), onClick = vm::exportBackup)
            AccentButton(stringResource(R.string.settings_backup_restore), onClick = vm::restoreLatestBackup)
        }
        state.backupMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
    ReplayBufferCard(state, vm)
    Card {
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
    )
    Card {
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
        Spacer(Modifier.height(12.dp))
        AccentButton(stringResource(R.string.settings_replay_save_now), enabled = state.replayEnabled, onClick = vm::saveReplayClip)
        state.replayMessage?.let { Spacer(Modifier.height(10.dp)); InfoLine(it) }
    }
}

@Composable
private fun QuickLaunchCard(state: SettingsState, vm: SettingsViewModel) {
    Card {
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
    SectionHeader(stringResource(R.string.settings_order))
    OrderCard(
        items = state.orderedEnabledTiles,
        description = stringResource(R.string.settings_tile_order_desc),
        label = { jr.brian.commandpannel.system.QuickSettingTileId.fromKey(it)?.let { id -> stringResource(id.labelRes) } ?: it },
        onCommit = vm::reorderTiles,
        scroll = scroll,
        viewport = viewport,
    )
    jr.brian.commandpannel.system.TileCategory.entries.forEach { cat ->
        SectionHeader(stringResource(cat.titleRes))
        Card {
            jr.brian.commandpannel.system.QuickSettingTileId.entries.filter { it.category == cat }.forEach { tile ->
                val checked = tile.key in state.enabledTiles
                FocusRow(selected = false, onClick = { vm.setTileEnabled(tile.key, !checked) }) {
                    Text(stringResource(tile.labelRes), color = p.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    AccentSwitch(checked) { vm.setTileEnabled(tile.key, it) }
                }
            }
        }
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
    onCommit: (List<String>) -> Unit,
    scroll: ScrollState,
    viewport: () -> Rect,
) {
    val scope = rememberCoroutineScope()
    val reorder = remember(scroll) { ReorderListState(items, scope, scroll, viewport) }
    reorder.onCommit = onCommit
    LaunchedEffect(items) { reorder.sync(items) }
    Card {
        CardDesc(description)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(ORDER_ROW_SPACING)) {
            reorder.items.forEachIndexed { index, k -> key(k) { OrderRow(index, k, label(k), note(k), reorder) } }
        }
    }
}

private val ORDER_ROW_SPACING = 4.dp

@Composable
private fun OrderRow(index: Int, key: String, label: String, note: String?, reorder: ReorderListState) {
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
    SectionHeader(stringResource(R.string.settings_button_order))
    OrderCard(
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
    SectionHeader(stringResource(R.string.settings_quick_launch))
    QuickLaunchCard(state, vm)
    SwitchCard(
        stringResource(R.string.settings_show_app_on_launch),
        stringResource(R.string.settings_show_app_on_launch_desc),
        state.quickLaunchShowApp, vm::setQuickLaunchShowApp,
    )
}

// ---- Appearance ----

@Composable
private fun Appearance(state: SettingsState, vm: SettingsViewModel) {
    Card {
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
    )
    SwitchCard(
        stringResource(R.string.settings_rgb_accent),
        stringResource(R.string.settings_rgb_accent_desc),
        state.rgbAccentSync, vm::setRgbAccentSync,
    )
}

// ---- Diagnostics ----

@Composable
private fun Diagnostics(state: SettingsState, vm: SettingsViewModel) {
    Card {
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
