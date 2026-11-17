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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.FontImportException
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.overlay.OverlayTab
import jr.brian.commandpannel.ui.theme.BundledFont
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
 * overlay stays pinned on the right, so every pick is visible as it's made. B / back closes without saving.
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
    var previewMode by remember { mutableStateOf(if (state.themeMode == 3) 3 else 4) }
    val saved = state.customLooks.any { it.id == draft.id }
    val firstFocus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    val savedMsg = stringResource(R.string.theme_saved)
    val appliedMsg = stringResource(R.string.theme_applied)
    PaddedBringIntoView {
    Column(Modifier.fillMaxSize().then(backdrop).systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        // ---- top bar: back, name, save actions ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.theme_back), onClick = onClose)
            NameField(draft.name, Modifier.weight(1f)) { draft = draft.copy(name = it) }
            message?.let { InfoLine(it, p.success) }
            // Looks synced from a theme repo are replaced on update, so edits always save as the user's own copy.
            val fromRepo = draft.source != null
            fun ownCopy() = draft.copy(id = UUID.randomUUID().toString(), source = null)
            if (saved || fromRepo) {
                Chip(stringResource(R.string.theme_save_new)) {
                    val copy = ownCopy()
                    vm.saveCustomLook(copy); draft = copy; message = savedMsg
                }
            }
            if (!fromRepo) Chip(stringResource(R.string.theme_save)) { vm.saveCustomLook(draft); message = savedMsg }
            AccentButton(stringResource(R.string.theme_save_apply)) {
                val target = if (fromRepo) ownCopy() else draft
                vm.saveCustomLook(target, apply = true); draft = target; message = appliedMsg
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- controls ----
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Category bar: each category swaps the controls below; the preview always shows the whole look.
                ChipRow {
                    StudioCategory.entries.forEachIndexed { i, c ->
                        Box(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) {
                            OptionChip(stringResource(c.titleRes), category == c) { category = c }
                        }
                    }
                }
                val base = draft.baseSpec()
                val update: (CustomLook) -> Unit = { draft = it }
                when (category) {
                    StudioCategory.PARTS -> PartsControls(draft, update)
                    StudioCategory.CUSTOM -> CustomPartControls(draft, update)
                    StudioCategory.FONTS -> FontControls(draft, state, vm, onMessage = { message = it }, onChange = update)
                    StudioCategory.SHAPES -> ShapeControls(draft, update)
                    StudioCategory.COLORS -> ColorControls(draft, update)
                    StudioCategory.TEXT -> TextControls(draft, base, update)
                    StudioCategory.EFFECTS -> EffectControls(draft, base, update)
                    StudioCategory.FIT -> FitControls(draft, base, update)
                }
            }
            // ---- pinned live preview ----
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (preview) {
                    PreviewKind.CARDS -> CardsLookPreview(state, draft, width = PREVIEW_WIDTH, themeMode = previewMode)
                    else -> OverlayLookPreview(
                        state, draft.baseLook, draft, width = PREVIEW_WIDTH, themeMode = previewMode,
                        tab = if (preview == PreviewKind.MODE) OverlayTab.MODE else OverlayTab.SETTINGS,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewKind.entries.forEach { k ->
                        OptionChip(stringResource(k.titleRes), preview == k) { preview = k }
                    }
                    OptionChip(stringResource(R.string.theme_preview_dark), previewMode == 4) { previewMode = 4 }
                    OptionChip(stringResource(R.string.theme_preview_light), previewMode == 3) { previewMode = 3 }
                }
            }
        }
    }
    }
}

private val PREVIEW_WIDTH = 360.dp

private enum class StudioCategory(@StringRes val titleRes: Int) {
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
private enum class PreviewKind(@StringRes val titleRes: Int) {
    SETTINGS(R.string.theme_preview_settings),
    MODE(R.string.theme_preview_mode),
    CARDS(R.string.theme_preview_cards),
}

@Composable
private fun NameField(name: String, modifier: Modifier, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = controlShape(10.dp)
    BasicTextField(
        value = name,
        onValueChange = { onChange(it.take(40)) },
        singleLine = true,
        textStyle = tccDisplayTextStyle().merge(TextStyle(color = p.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)),
        cursorBrush = SolidColor(p.accent),
        modifier = modifier
            .clip(shape)
            .background(if (focused) p.surfaceHigh else p.surface.copy(alpha = 0.6f))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) p.accent else p.border, shape)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** One look part (cards, tiles, …): its options are the styles the built-in looks use, named after the first look that uses each. */
private class Part<T>(
    @StringRes val titleRes: Int,
    val of: (LookTraits) -> T,
    val set: (CustomLook, T) -> CustomLook,
    /** The part's parametric style, offered last (its settings live in the Custom category); `null` for none. */
    custom: T? = null,
) {
    /** Presets, each named after the first built-in look (in declaration order) that uses it. */
    val presets: List<Pair<T, Int>> = TccUiLook.entries.map { of(specForLook(it).traits) to it.labelRes }.distinctBy { it.first }
    val custom: Pair<T, Int>? = custom?.let { it to R.string.theme_part_custom }
}

private val PARTS: List<Part<*>> = listOf(
    Part(R.string.theme_part_cards, { it.card }, { l, v -> l.copy(card = v) }, CardStyle.CUSTOM),
    Part(R.string.theme_part_tiles, { it.tile }, { l, v -> l.copy(tile = v) }, TileStyle.CUSTOM),
    Part(R.string.theme_part_gauges, { it.gauge }, { l, v -> l.copy(gauge = v) }, GaugeStyle.CUSTOM),
    Part(R.string.theme_part_sliders, { it.slider }, { l, v -> l.copy(slider = v) }, SliderStyle.CUSTOM),
    Part(R.string.theme_part_nav, { it.nav }, { l, v -> l.copy(nav = v) }, NavStyle.CUSTOM),
    Part(R.string.theme_part_backdrop, { it.backdrop }, { l, v -> l.copy(backdrop = v) }),
    Part(R.string.theme_part_header, { it.header }, { l, v -> l.copy(header = v) }),
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
        Chip(stringResource(R.string.theme_reset_parts)) {
            onChange(draft.copy(card = null, tile = null, gauge = null, slider = null, nav = null, backdrop = null, header = null))
        }
    }
}

@Composable
private fun <T> PartRow(part: Part<T>, traits: LookTraits, draft: CustomLook, onChange: (CustomLook) -> Unit) {
    val current = part.of(traits)
    Text(lookLabelText(part.titleRes), color = p.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())
    ChipRow {
        // Presets alphabetically, then Custom last.
        (sortedByLabel(part.presets) + listOfNotNull(part.custom)).forEach { (value, labelRes) ->
            OptionChip(stringResource(labelRes), current == value) { onChange(part.set(draft, value)) }
        }
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
                    Chip(stringResource(R.string.theme_font_remove, font.displayName)) { vm.deleteFont(font.id) }
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
internal fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val f = focusState()
    val shape = controlShape(10.dp)
    val scale by focusScale(f.value, 1.08f, "optionScale")
    val bg = when { selected && f.value -> p.accentFocused; selected -> p.accent; f.value -> p.surfaceHigh; else -> p.surfaceHigh.copy(alpha = 0.5f) }
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .then(if (f.value) Modifier.border(2.dp, if (selected) p.onAccent else p.accent, shape) else Modifier)
            .focusControl(f, onClick).noRippleClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) p.onAccent else if (f.value) p.textPrimary else p.textSecondary,
            fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, style = tccTextStyle(),
        )
    }
}

/** A single-line, horizontally scrolling row of chips (D-pad left/right walks it and scrolls the focused chip in). */
@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    // Content padding leaves room for the focused chip's scale and ring at both ends; the padded bring-into-view spec
    // (inherited from the studio / settings pane) keeps D-pad scrolling from parking an end chip flush at the edge.
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = CHIP_ROW_INSET),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

private val CHIP_ROW_INSET = 10.dp

@Composable
private fun lookLabelText(@StringRes res: Int): String = jr.brian.commandpannel.ui.theme.lookLabel(stringResource(res))
