package jr.brian.commandpannel.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import jr.brian.commandpannel.ui.components.noRippleClickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import androidx.compose.foundation.shape.RoundedCornerShape
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import jr.brian.commandpannel.ui.overlay.arrange.LocalPreviewHighlight
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import jr.brian.commandpannel.ui.theme.StudioDraft
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.DisposableEffect
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.system.FontImportException
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.overlay.OverlayTab
import jr.brian.commandpannel.ui.theme.BundledFont
import jr.brian.commandpannel.ui.theme.BackdropStyle
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.NavStyle
import jr.brian.commandpannel.ui.theme.SliderStyle
import jr.brian.commandpannel.ui.theme.GaugeStyle
import jr.brian.commandpannel.ui.theme.TileStyle
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.FontRef
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.LookTraits
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.specForLook
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle
import jr.brian.commandpannel.ui.theme.toSpec
import java.util.UUID

/** Starts a new, unsaved look on top of [base]. */
internal fun newCustomLook(name: String, base: TccUiLook) = CustomLook(id = UUID.randomUUID().toString(), name = name, baseLook = base)

/**
 * Theme Studio: a full-screen editor for one [CustomLook]. Controls scroll on the left; a live miniature of the
 * overlay stays pinned on the right, so every pick is visible as it's made. The draft also shows live on the real
 * overlay (and the companion, when it follows the look) through [StudioDraft]; leaving reverts to the applied look.
 * Save stores it, Save and apply also applies it. B / back asks before discarding unsaved changes.
 */
@Composable
internal fun ThemeStudioScreen(
    initial: CustomLook,
    state: SettingsState,
    vm: SettingsViewModel,
    onClose: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var message by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf(StudioCategory.PARTS) }
    var preview by remember { mutableStateOf(PreviewKind.SETTINGS) }
    // The preview follows the part being edited, until the user picks a preview by hand (for this category).
    var previewPinned by remember(category) { mutableStateOf(false) }
    var focusedPart by remember { mutableStateOf<StudioPart?>(null) }
    val onFocusPart: (StudioPart?) -> Unit = { part ->
        focusedPart = part
        if (part != null && !previewPinned) preview = part.preview
    }
    var previewMode by remember { mutableStateOf(if (state.themeMode == 3) 3 else 4) }
    val saved = state.customLooks.any { it.id == draft.id }
    // One per category chip. Controls that vanish while focused (switching category, a Reset or Use custom chip)
    // would drop focus onto Back, so focus goes back to the current category's chip instead.
    val categoryFocus = remember { StudioCategory.entries.associateWith { FocusRequester() } }
    val firstFocus = categoryFocus.getValue(StudioCategory.PARTS)
    // Select (−) jumps to the save button, Start (+) to Save and apply; A then presses it.
    val saveFocus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    // The version last saved (or opened): Back only asks to discard when the draft differs from it. A resumed draft
    // starts out unsaved: its baseline is the saved look (or, for one never saved, nothing).
    var lastSaved by remember(initial.id) {
        mutableStateOf(
            if (state.studioResumed) state.customLooks.firstOrNull { it.id == initial.id } ?: initial.resetAll().copy(name = "")
            else initial,
        )
    }
    // Undo / redo over every edit (quick runs of changes count as one step).
    val history = remember(initial.id) { StudioHistory<CustomLook>() }
    var historyTick by remember { mutableIntStateOf(0) }
    fun edit(next: CustomLook) {
        if (next == draft) return
        history.record(draft, android.os.SystemClock.uptimeMillis())
        draft = next
        historyTick++
    }
    fun undo() { history.undo(draft)?.let { draft = it; historyTick++ } }
    fun redo() { history.redo(draft)?.let { draft = it; historyTick++ } }
    var confirmDiscard by remember { mutableStateOf(false) }
    // The first time Studio opens, a tip says L3 / R3 undo and redo, since the buttons are icons only (#63).
    val tipPrefs = jr.brian.commandpannel.system.Prefs.of(androidx.compose.ui.platform.LocalContext.current)
    var showUndoTip by remember { mutableStateOf(!tipPrefs.getBoolean(StudioUndoTip.KEY_SEEN, false)) }
    val dirty = draft != lastSaved
    val requestClose: () -> Unit = { if (draft != lastSaved) confirmDiscard = true else onClose() }
    LaunchedEffect(draft) { StudioDraft.look.value = draft }
    // Unsaved work is kept (half a second after the last change) in case the app closes; any normal exit forgets it.
    LaunchedEffect(draft, lastSaved) {
        kotlinx.coroutines.delay(500)
        vm.persistStudioDraft(if (draft != lastSaved) draft else null)
    }
    DisposableEffect(Unit) {
        onDispose {
            StudioDraft.look.value = null
            vm.persistStudioDraft(null)
        }
    }
    // The NavHost's own back handling can run first, so B / Back keys are caught in the key handler below, and this
    // (for the back gesture) is only enabled while there's something to lose: enabled after the NavHost's, it wins.
    BackHandler(enabled = dirty) { confirmDiscard = true }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    // "Saved" is a moment's confirmation, not a status.
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(3_000); message = null } }
    // Each category opens at its top, and Up from its first control brings the category bar back into view.
    val controlsScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(category) { controlsScroll.scrollTo(0) }
    val refocusCategory: () -> Unit = {
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { categoryFocus.getValue(category).requestFocus() }
        }
    }

    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    val savedMsg = stringResource(R.string.theme_saved)
    // The Thor's LED color, offered as an accent pick (read once; it's the system setting the LED picker writes).
    val context = androidx.compose.ui.platform.LocalContext.current
    val ledColor = remember { jr.brian.commandpannel.privileged.ThorRgbController.accentOverride(context, syncEnabled = true) }
    PaddedBringIntoView {
    Column(
        Modifier.fillMaxSize().then(backdrop).systemBarsPadding()
            // Shortcuts from anywhere in Studio: Y dark, X light, L1 / R1 step through Settings, Mode and Cards, L2 / R2
            // through the categories.
            .onPreviewKeyEvent { e ->
                val kinds = PreviewKind.entries
                val cats = StudioCategory.entries
                val action: (() -> Unit)? = when (e.key) {
                    Key.ButtonY -> { { previewMode = 4 } }
                    Key.ButtonX -> { { previewMode = 3 } }
                    Key.ButtonL1 -> { { preview = kinds[Math.floorMod(preview.ordinal - 1, kinds.size)]; previewPinned = true } }
                    Key.ButtonR1 -> { { preview = kinds[(preview.ordinal + 1) % kinds.size]; previewPinned = true } }
                    Key.ButtonL2 -> { { category = cats[Math.floorMod(category.ordinal - 1, cats.size)]; refocusCategory() } }
                    Key.ButtonR2 -> { { category = cats[(category.ordinal + 1) % cats.size]; refocusCategory() } }
                    Key.ButtonThumbLeft -> { { undo() } }
                    Key.ButtonThumbRight -> { { redo() } }
                    Key.ButtonSelect -> { { runCatching { saveFocus.requestFocus() } } }
                    Key.ButtonStart -> { { runCatching { applyFocus.requestFocus() } } }
                    else -> null
                }
                // B / Back on release (so the key-up doesn't reach the screen underneath once Studio is gone).
                if (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) requestClose()
                    return@onPreviewKeyEvent true
                }
                if (action != null && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) action()
                action != null
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // ---- top bar: back, name, save actions ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.theme_back), onClick = requestClose)
            // Read through the tick, so these update with the history.
            val canUndo = historyTick.let { history.canUndo }
            val canRedo = historyTick.let { history.canRedo }
            // Icons only, so the look's name has room (#63); L3 / R3 do the same, as the first-run tip says.
            IconChip(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.theme_undo), enabled = canUndo) { undo() }
            IconChip(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.theme_redo), enabled = canRedo) { redo() }
            GamepadTextField(
                value = draft.name,
                onValueChange = { edit(draft.copy(name = it.take(40))) },
                textStyle = tccDisplayTextStyle().merge(TextStyle(color = p.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)),
                modifier = Modifier.weight(1f),
                verticalPadding = 8.dp,
            )
            message?.let { InfoLine(it, p.success) }
            // Looks synced from a theme repo are replaced on update, so edits always save as the user's own copy.
            val fromRepo = draft.source != null
            // The raw patterns (no args given), formatted per candidate below.
            val copyOf = stringResource(R.string.theme_copy_name)
            val copyOfN = stringResource(R.string.theme_copy_name_n)
            val untitled = stringResource(R.string.theme_new_look_name, state.customLooks.size + 1)
            // A blank name falls back to the last saved one (or a numbered "My look").
            fun named(l: CustomLook) = l.copy(name = nonBlankName(l.name) { lastSaved.name.ifBlank { untitled } })
            // A copy gets a name no saved look has yet ("Glacier copy", "Glacier copy 2", …).
            fun ownCopy(): CustomLook {
                val base = named(draft)
                val name = copyName(
                    base.name, state.customLooks.map { it.name },
                    copy = { String.format(copyOf, it) },
                    copyN = { n, i -> String.format(copyOfN, n, i) },
                )
                return base.copy(id = UUID.randomUUID().toString(), source = null, name = name)
            }
            if (saved || fromRepo) {
                // Select's target when there's no plain Save (a repo look only saves as a copy).
                Box(if (fromRepo) Modifier.focusRequester(saveFocus) else Modifier) {
                    Chip(stringResource(R.string.theme_save_new), glyph = if (fromRepo) GamepadButton.SELECT else null) {
                        val copy = ownCopy()
                        vm.saveCustomLook(copy); draft = copy; lastSaved = copy; message = savedMsg
                    }
                }
            }
            if (!fromRepo) {
                Box(Modifier.focusRequester(saveFocus)) {
                    Chip(stringResource(R.string.theme_save), glyph = GamepadButton.SELECT) {
                        val target = named(draft)
                        vm.saveCustomLook(target); draft = target; lastSaved = target; message = savedMsg
                    }
                }
            }
            Box(Modifier.focusRequester(applyFocus)) { AccentButton(stringResource(R.string.theme_save_apply), glyph = GamepadButton.START) {
                val target = if (fromRepo) ownCopy() else named(draft)
                // Done editing: apply it and leave Studio (the overlay now shows the applied look for real).
                vm.saveCustomLook(target, apply = true); lastSaved = target; onClose()
            } }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- controls ----
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(controlsScroll).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Category bar: each category swaps the controls below; the preview always shows the whole look.
                // Categories where this look differs from its base look (they get a Reset chip).
                val changed = draft.changedCategories()
                ChipRow(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { controlsScroll.animateScrollTo(0) } }) {
                    StudioCategory.entries.forEachIndexed { i, c ->
                        Box(Modifier.focusRequester(categoryFocus.getValue(c))) {
                            OptionChip(stringResource(c.titleRes), category == c) { category = c }
                        }
                    }
                }
                val base = draft.baseSpec()
                val update: (CustomLook) -> Unit = { edit(it) }
                CompositionLocalProvider(LocalStudioPartFocus provides onFocusPart, LocalStudioRefocus provides refocusCategory) {
                when (category) {
                    StudioCategory.PARTS -> PartsControls(draft, update)
                    StudioCategory.CUSTOM -> CustomPartControls(draft, update)
                    StudioCategory.FONTS -> FontControls(draft, state, vm, onMessage = { message = it }, onChange = update)
                    StudioCategory.SHAPES -> ShapeControls(draft, update)
                    StudioCategory.COLORS -> ColorControls(draft, ledColor, update)
                    StudioCategory.TEXT -> TextControls(draft, base, update)
                    StudioCategory.EFFECTS -> EffectControls(draft, base, update)
                    StudioCategory.FIT -> FitControls(draft, base, update)
                }
                }
                // Parts has its own reset (and Reset everything) in its card.
                if (category != StudioCategory.PARTS && category in changed) {
                    Chip(stringResource(R.string.theme_reset_category, stringResource(category.titleRes))) {
                        edit(draft.resetCategory(category))
                        refocusCategory()
                    }
                }
            }
            // ---- pinned live preview ----
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The cards the focused control edits are outlined in the preview.
                CompositionLocalProvider(LocalPreviewHighlight provides (focusedPart?.cards ?: emptySet())) {
                    when (preview) {
                        PreviewKind.CARDS -> CardsLookPreview(state, draft, width = PREVIEW_WIDTH, themeMode = previewMode)
                        else -> OverlayLookPreview(
                            state, draft.baseLook, draft, width = PREVIEW_WIDTH, themeMode = previewMode,
                            tab = if (preview == PreviewKind.MODE) OverlayTab.MODE else OverlayTab.SETTINGS,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewKind.entries.forEach { k ->
                        OptionChip(stringResource(k.titleRes), preview == k) { preview = k; previewPinned = true }
                    }
                    OptionChip(stringResource(R.string.theme_preview_dark), previewMode == 4) { previewMode = 4 }
                    OptionChip(stringResource(R.string.theme_preview_light), previewMode == 3) { previewMode = 3 }
                }
                InfoLine(stringResource(R.string.theme_preview_buttons_hint), p.textTertiary)
            }
        }
    }
    }
    if (showUndoTip) {
        StudioUndoTipDialog(onDismiss = {
            tipPrefs.edit().putBoolean(StudioUndoTip.KEY_SEEN, true).apply()
            showUndoTip = false
        })
    }
    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeep = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onClose() },
        )
    }
}

/** "Discard your changes?" on Back with unsaved edits. Focus starts on Keep editing, and B keeps editing too. */
@Composable
private fun DiscardChangesDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    Dialog(onDismissRequest = onKeep, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val keep = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { keep.requestFocus() } }
        Column(
            Modifier.width(520.dp)
                // Opaque base under the look's card (which may be translucent glass), as in the update dialog.
                .clip(cardShape()).background(p.bg)
                .companionCard()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.theme_discard_title), color = p.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            CardDesc(stringResource(R.string.theme_discard_body))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.focusRequester(keep)) {
                    AccentButton(stringResource(R.string.theme_discard_keep), glyph = GamepadButton.B, onClick = onKeep)
                }
                Chip(stringResource(R.string.theme_discard_confirm), onClick = onDiscard)
            }
        }
    }
}

private val PREVIEW_WIDTH = 336.dp

internal enum class StudioCategory(@StringRes val titleRes: Int) {
    PARTS(R.string.theme_cat_parts),
    CUSTOM(R.string.theme_cat_custom),
    FONTS(R.string.theme_cat_fonts),
    SHAPES(R.string.theme_cat_shapes),
    COLORS(R.string.theme_cat_colors),
    TEXT(R.string.theme_cat_text),
    EFFECTS(R.string.theme_cat_effects),
    FIT(R.string.theme_cat_fit),
}

/** What the pinned preview shows: an overlay tab, or every card at each of its sizes. */
internal enum class PreviewKind(@StringRes val titleRes: Int) {
    SETTINGS(R.string.theme_preview_settings),
    MODE(R.string.theme_preview_mode),
    CARDS(R.string.theme_preview_cards),
}

/** One look part (cards, tiles, …): its options are the styles the built-in looks use, named after the first look that uses each. */
private class Part<T>(
    @StringRes val titleRes: Int,
    val of: (LookTraits) -> T,
    val set: (CustomLook, T) -> CustomLook,
    /** The part's parametric style, offered last (its settings live in the Custom category); `null` for none. */
    custom: T? = null,
    /** Styles no built-in look uses, with their own names; listed among the presets. */
    val extras: List<Pair<T, Int>> = emptyList(),
    /** What it edits in the preview (which tab to show, which cards to outline). */
    val studio: StudioPart? = null,
    /** Whether the look overrides this part (shown as "Changed"). */
    val isSet: (CustomLook) -> Boolean = { false },
) {
    /** Presets, each named after the first built-in look (in declaration order) that uses it. */
    val presets: List<Pair<T, Int>> = TccUiLook.entries.map { of(specForLook(it).traits) to it.labelRes }.distinctBy { it.first }
    val custom: Pair<T, Int>? = custom?.let { it to R.string.theme_part_custom }
}

private val PARTS: List<Part<*>> = listOf(
    Part(R.string.theme_part_cards, { it.card }, { l, v -> l.copy(card = v) }, CardStyle.CUSTOM, studio = StudioPart.CARDS, isSet = { it.card != null }),
    Part(R.string.theme_part_tiles, { it.tile }, { l, v -> l.copy(tile = v) }, TileStyle.CUSTOM, studio = StudioPart.TILES, isSet = { it.tile != null }),
    Part(R.string.theme_part_gauges, { it.gauge }, { l, v -> l.copy(gauge = v) }, GaugeStyle.CUSTOM, studio = StudioPart.GAUGES, isSet = { it.gauge != null }),
    Part(R.string.theme_part_sliders, { it.slider }, { l, v -> l.copy(slider = v) }, SliderStyle.CUSTOM, studio = StudioPart.SLIDERS, isSet = { it.slider != null }),
    Part(R.string.theme_part_nav, { it.nav }, { l, v -> l.copy(nav = v) }, NavStyle.CUSTOM, studio = StudioPart.NAV, isSet = { it.nav != null }),
    Part(
        R.string.theme_part_backdrop, { it.backdrop }, { l, v -> l.copy(backdrop = v) },
        extras = listOf(
            BackdropStyle.GRADIENT_WASH to R.string.theme_backdrop_gradient_wash,
            BackdropStyle.TOPOGRAPHIC to R.string.theme_backdrop_topographic,
            BackdropStyle.HONEYCOMB to R.string.theme_backdrop_honeycomb,
            BackdropStyle.STARFIELD to R.string.theme_backdrop_starfield,
        ),
        studio = StudioPart.BACKDROP,
        isSet = { it.backdrop != null },
    ),
    Part(R.string.theme_part_header, { it.header }, { l, v -> l.copy(header = v) }, studio = StudioPart.HEADERS, isSet = { it.header != null }),
)

@Composable
private fun PartsControls(draft: CustomLook, onChange: (CustomLook) -> Unit) {
    Card {
        ControlLabel(R.string.theme_start_from)
        CardDesc(stringResource(R.string.theme_start_from_desc))
        ChipRow {
            sortedLooks().forEach { look ->
                OptionChip(stringResource(look.labelRes), draft.baseLook == look) { onChange(draft.copy(baseLook = look)) }
            }
        }
    }
    Card {
        CardDesc(stringResource(R.string.theme_parts_desc))
        val traits = draft.toSpec().traits
        PARTS.forEach { part -> PartRow(part, traits, draft, onChange) }
        Spacer(Modifier.height(4.dp))
        // One per line: side by side, the long Reset parts squeezed Reset everything until it wrapped.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.theme_reset_parts)) { onChange(draft.resetCategory(StudioCategory.PARTS)) }
            // Every category at once, back to the base look (the name stays).
            if (draft.changedCategories().isNotEmpty()) Chip(stringResource(R.string.theme_reset_all)) { onChange(draft.resetAll()) }
        }
    }
}

@Composable
private fun <T> PartRow(part: Part<T>, traits: LookTraits, draft: CustomLook, onChange: (CustomLook) -> Unit) {
    val current = part.of(traits)
    Column(Modifier.studioPart(part.studio), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lookLabelText(part.titleRes), color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
            if (part.isSet(draft)) InfoLine(stringResource(R.string.theme_changed), p.accent)
        }
        // Each option shows a tiny sample of the part in that style (in this look's colors), where one reads at chip size.
        val palette = remember(draft.colors) { resolveLookPalette(4, nightActive = true, accentOverride = null, pinned = draft.colors) }
        ChipRow {
            // Presets (and extras) alphabetically, then Custom last.
            (sortedByLabel(part.presets + part.extras) + listOfNotNull(part.custom)).forEach { (value, labelRes) ->
                val sample: (@Composable () -> Unit)? = part.studio?.takeIf { it in SAMPLED_PARTS }?.let { kind ->
                    { PartSample(kind, remember(draft, value) { part.set(draft, value).toSpec() }, palette) }
                }
                OptionChip(stringResource(labelRes), current == value, leading = sample) { onChange(part.set(draft, value)) }
            }
        }
    }
}

/** The parts whose option chips carry a sample (sliders and headers don't read at chip size). */
private val SAMPLED_PARTS = setOf(StudioPart.CARDS, StudioPart.TILES, StudioPart.GAUGES, StudioPart.NAV, StudioPart.BACKDROP)

/** A chip-sized sample of [part] drawn with [spec] (the look with that option applied) in [colors]. */
@Composable
private fun PartSample(part: StudioPart, spec: jr.brian.commandpannel.ui.theme.TccLookSpec, colors: jr.brian.commandpannel.ui.theme.TccColors) {
    jr.brian.commandpannel.ui.theme.TccTheme(colors) {
        CompositionLocalProvider(LocalTccLookSpec provides spec) {
            when (part) {
                StudioPart.CARDS -> jr.brian.commandpannel.ui.components.TccCard(Modifier.size(28.dp, 18.dp)) {}
                StudioPart.TILES -> jr.brian.commandpannel.ui.components.TccIconButton(
                    "ic_qs_vibrate", "", circleSize = 20.dp, active = true, showLabel = false,
                )
                StudioPart.GAUGES -> jr.brian.commandpannel.ui.components.RingGauge(0.65f, colors.accent, "", "", "", Modifier.size(21.dp))
                // Nav highlights draw at a fixed size whatever the button's size (Jelly's capsule, Neon's underline), so
                // draw one at full size and scale it into the chip instead of letting it spill over the label.
                StudioPart.NAV -> FitSample(NAV_SAMPLE_W, NAV_SAMPLE_H, jr.brian.commandpannel.ui.theme.aynDp(64f), jr.brian.commandpannel.ui.theme.aynDp(40f)) {
                    jr.brian.commandpannel.ui.components.TccIconButton(
                        "ic_system_set", "", circleSize = jr.brian.commandpannel.ui.theme.aynDp(32f), active = true, showLabel = false,
                        variant = jr.brian.commandpannel.ui.components.TccTileVariant.NAV_BAR,
                    )
                }
                StudioPart.BACKDROP -> Box(
                    Modifier.size(28.dp, 18.dp).clip(RoundedCornerShape(4.dp)).tccLookBackdrop(spec, colors, tccLookColors()),
                )
                else -> Unit
            }
        }
    }
}

private val NAV_SAMPLE_W = 30.dp
private val NAV_SAMPLE_H = 19.dp

/** Lays [content] out at [naturalW] x [naturalH] and scales it down to fit a [width] x [height] slot. */
@Composable
private fun FitSample(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    naturalW: androidx.compose.ui.unit.Dp,
    naturalH: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val scale = minOf(width / naturalW, height / naturalH)
    Box(Modifier.size(width, height), contentAlignment = Alignment.Center) {
        Box(
            Modifier.requiredSize(naturalW, naturalH).graphicsLayer { scaleX = scale; scaleY = scale },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun FontControls(
    draft: CustomLook,
    state: SettingsState,
    vm: SettingsViewModel,
    onMessage: (String) -> Unit,
    onChange: (CustomLook) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFont(uri) { result ->
            result.onSuccess { onMessage(context.getString(R.string.theme_font_imported, it.displayName)) }
                .onFailure { e -> onMessage(context.getString((e as? FontImportException)?.messageRes ?: R.string.msg_font_invalid)) }
        }
    }
    Card {
        CardDesc(stringResource(R.string.theme_fonts_desc))
        FontRow(R.string.theme_font_labels, draft.labelFont, state) { onChange(draft.copy(labelFont = it)) }
        FontRow(R.string.theme_font_numbers, draft.displayFont, state) { onChange(draft.copy(displayFont = it)) }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.theme_font_import)) { launcher.launch(FONT_MIME_TYPES) }
        }
        if (state.userFonts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(lookLabelText(R.string.theme_font_manage), color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
            ChipRow {
                state.userFonts.forEach { font ->
                    // Two presses, so a stray A can't remove a font.
                    var confirm by remember(font.id) { mutableStateOf(false) }
                    Chip(
                        stringResource(if (confirm) R.string.theme_font_remove_confirm else R.string.theme_font_remove, font.displayName),
                        selected = confirm,
                    ) { if (confirm) vm.deleteFont(font.id) else confirm = true }
                }
            }
        }
    }
}

private val FONT_MIME_TYPES = arrayOf(
    "font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream",
)

@Composable
private fun FontRow(@StringRes titleRes: Int, current: FontRef?, state: SettingsState, onPick: (FontRef?) -> Unit) {
    Text(lookLabelText(titleRes), color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
    ChipRow {
        OptionChip(stringResource(R.string.theme_font_look_default), current == null) { onPick(null) }
        BundledFont.entries.forEach { font ->
            val ref = FontRef.Bundled(font)
            OptionChip(stringResource(font.labelRes), current == ref) { onPick(ref) }
        }
        state.userFonts.forEach { font ->
            val ref = FontRef.User(font.id, font.displayName, font.fileName)
            OptionChip(font.displayName, current == ref) { onPick(ref) }
        }
    }
}

/**
 * A pickable option. Unlike [Chip], the picked option is filled with the accent and focus is only a ring, so the
 * current choice stays readable while the D-pad moves across a row.
 */
@Composable
internal fun OptionChip(
    text: String,
    selected: Boolean,
    /** Drawn before the label (Parts options show a sample of the style). */
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val f = focusState()
    // Inside a ChipRow, the picked option is where D-pad entry lands and what the row first scrolls to.
    val row = LocalChipRowSelection.current
    val rowSelection = if (selected && row != null) {
        Modifier.focusRequester(row.requester).onPlaced { row.selectedX = it.positionInParent().x.roundToInt() }
    } else {
        Modifier
    }
    if (selected && row != null) DisposableEffect(row) { row.hasSelected = true; onDispose { row.hasSelected = false } }
    val shape = controlShape(10.dp)
    val scale by focusScale(f.value, 1.08f, "optionScale")
    val bg = when { selected && f.value -> p.accentFocused; selected -> p.accent; f.value -> p.surfaceHigh; else -> p.surfaceHigh.copy(alpha = 0.5f) }
    Box(
        rowSelection.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .then(if (f.value) Modifier.border(2.dp, if (selected) p.onAccent else p.accent, shape) else Modifier)
            .focusControl(f, onClick).noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.invoke()
            Text(
                text, color = if (selected) p.onAccent else if (f.value) p.textPrimary else p.textSecondary,
                fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, style = tccTextStyle(),
            )
        }
    }
}

/** Where a [ChipRow]'s picked [OptionChip] is, so the row can open on it and send D-pad entry to it. */
@Stable
internal class ChipRowSelection {
    val requester = FocusRequester()
    var hasSelected by mutableStateOf(false)
    var selectedX by mutableStateOf<Int?>(null)
}

internal val LocalChipRowSelection = androidx.compose.runtime.compositionLocalOf<ChipRowSelection?> { null }

/**
 * A single-line, horizontally scrolling row of chips (D-pad left/right walks it and scrolls the focused chip in). It
 * opens scrolled to the picked option, and D-pad entry from above or below lands on it rather than the nearest chip.
 */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    val selection = remember { ChipRowSelection() }
    val inset = with(LocalDensity.current) { (CHIP_ROW_INSET * 3).roundToPx() }
    // Only the first time: after that the row stays where the user scrolled it.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(selection.selectedX, scroll.maxValue) {
        val x = selection.selectedX ?: return@LaunchedEffect
        if (opened || scroll.maxValue == 0) return@LaunchedEffect
        opened = true
        scroll.scrollTo((x - inset).coerceIn(0, scroll.maxValue))
    }
    // Content padding leaves room for the focused chip's scale and ring at both ends; the padded bring-into-view spec
    // (inherited from the studio / settings pane) keeps D-pad scrolling from parking an end chip flush at the edge.
    CompositionLocalProvider(LocalChipRowSelection provides selection) {
        Row(
            modifier.fillMaxWidth()
                .focusProperties { onEnter = { if (selection.hasSelected) runCatching { selection.requester.requestFocus() } } }
                .focusGroup()
                .horizontalScroll(scroll).padding(vertical = 6.dp, horizontal = CHIP_ROW_INSET),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

private val CHIP_ROW_INSET = 10.dp

@Composable
private fun lookLabelText(@StringRes res: Int): String = jr.brian.commandpannel.ui.theme.lookLabel(stringResource(res))
