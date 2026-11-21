package jr.brian.commandpannel.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.GradientMotion
import jr.brian.commandpannel.system.GradientPresets
import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientSpec
import jr.brian.commandpannel.system.GradientStyle
import jr.brian.commandpannel.ui.components.GamepadButton
import jr.brian.commandpannel.ui.components.GradientBackgroundLayer
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/** Gradient Studio's tabs, switched with L2 / R2 or the tab row. */
internal enum class GradientStudioTab(@StringRes val titleRes: Int) {
    COLORS(R.string.gradient_studio_tab_colors),
    MOTION(R.string.gradient_studio_tab_motion),
    FINE_TUNE(R.string.gradient_studio_tab_fine_tune),
    PRESETS(R.string.gradient_studio_tab_presets),
}

/** The bottom screen's shape (1240 x 1080), so the preview is framed like the panel. */
private const val PANEL_ASPECT = 1240f / 1080f

/**
 * Gradient Studio (#68): a full-screen editor for one [GradientSpec], opened from the gradient library (New gradient,
 * Edit, or Resume). It has Theme Studio's frame: Back, undo / redo, the name, Save (Select) and Save and apply (Start)
 * on top; the controls scroll on the left, a live preview of the panel's background stays on the right. The preview
 * uses the panel's renderer and clock, and moves only while Experimental's Animated backgrounds is on.
 *
 * Gamepad: focus starts on the Colors tab. L2 / R2 switch tabs, L1 / R1 pick the previous / next color, L3 / R3 undo /
 * redo, Select / Start jump to Save / Save and apply. B / Back asks before discarding unsaved changes. Unsaved work is
 * kept (as Theme Studio's is) in case the app closes, and offered back in the library.
 */
@Composable
internal fun GradientStudioScreen(
    initial: GradientSpec,
    state: SettingsState,
    vm: SettingsViewModel,
    onClose: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var tab by remember { mutableStateOf(GradientStudioTab.COLORS) }
    var selectedStop by remember(initial.id) { mutableIntStateOf(0) }
    val stop = selectedStop.coerceIn(0, draft.stops.lastIndex)
    var message by remember { mutableStateOf<String?>(null) }
    val saved = state.gradients.any { it.id == draft.id }
    // The version last saved (or opened): Back only asks when the draft differs. A resumed draft starts out unsaved.
    var lastSaved by remember(initial.id) {
        mutableStateOf(
            if (state.gradientDraftResumed) state.gradients.firstOrNull { it.id == initial.id } ?: initial.copy(stops = emptyList())
            else initial,
        )
    }
    val history = remember(initial.id) { StudioHistory<GradientSpec>() }
    var historyTick by remember { mutableIntStateOf(0) }
    fun edit(next: GradientSpec) {
        if (next == draft) return
        history.record(draft, android.os.SystemClock.uptimeMillis())
        draft = next
        historyTick++
    }
    fun undo() { history.undo(draft)?.let { draft = it; historyTick++ } }
    fun redo() { history.redo(draft)?.let { draft = it; historyTick++ } }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dirty = draft != lastSaved
    val requestClose: () -> Unit = { if (draft != lastSaved) confirmDiscard = true else onClose() }
    // Unsaved work is kept half a second after the last change; any normal exit forgets it.
    LaunchedEffect(draft, lastSaved) {
        kotlinx.coroutines.delay(500)
        vm.persistGradientDraft(if (draft != lastSaved) draft else null)
    }
    DisposableEffect(Unit) { onDispose { vm.persistGradientDraft(null) } }
    // Always through requestClose, so a clean exit by gesture also clears the open draft (closeGradientStudio).
    BackHandler { requestClose() }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(3_000); message = null } }

    val tabFocus = remember { GradientStudioTab.entries.associateWith { FocusRequester() } }
    val saveFocus = remember { FocusRequester() }
    val applyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tabFocus.getValue(GradientStudioTab.COLORS).requestFocus() } }
    val controlsScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab) { controlsScroll.scrollTo(0) }
    val refocusTab: () -> Unit = {
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { tabFocus.getValue(tab).requestFocus() }
        }
    }

    // The Thor's LED color, offered as a stop color (the system setting the LED picker writes).
    val context = androidx.compose.ui.platform.LocalContext.current
    val ledColor = remember { jr.brian.commandpannel.privileged.ThorRgbController.accentOverride(context, syncEnabled = true) }
    val (lookColors, _) = rememberOverlayLook(state)
    val lookAccent = lookColors.accent.toArgb()
    val lookBg = lookColors.primaryBackground.toArgb()

    val savedMsg = stringResource(R.string.theme_saved)
    val copyOf = stringResource(R.string.theme_copy_name)
    val copyOfN = stringResource(R.string.theme_copy_name_n)
    val untitled = stringResource(R.string.gradient_new_name, state.gradients.size + 1)
    // A blank name falls back to the last saved one (or a numbered "My gradient").
    fun named(g: GradientSpec) = g.copy(name = nonBlankName(g.name) { lastSaved.name.ifBlank { untitled } })
    fun ownCopy(): GradientSpec {
        val base = named(draft)
        return GradientLibrary.duplicate(
            base, state.gradients, UUID.randomUUID().toString(), fallbackName = untitled,
            copy = { String.format(copyOf, it) }, copyN = { n, i -> String.format(copyOfN, n, i) },
        )
    }

    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    PaddedBringIntoView {
    Column(
        Modifier.fillMaxSize().then(backdrop).systemBarsPadding()
            .onPreviewKeyEvent { e ->
                val tabs = GradientStudioTab.entries
                val action: (() -> Unit)? = when (e.key) {
                    Key.ButtonL2 -> { { tab = tabs[Math.floorMod(tab.ordinal - 1, tabs.size)]; refocusTab() } }
                    Key.ButtonR2 -> { { tab = tabs[(tab.ordinal + 1) % tabs.size]; refocusTab() } }
                    Key.ButtonL1 -> { { selectedStop = (stop - 1).coerceAtLeast(0) } }
                    Key.ButtonR1 -> { { selectedStop = (stop + 1).coerceAtMost(draft.stops.lastIndex) } }
                    Key.ButtonThumbLeft -> { { undo() } }
                    Key.ButtonThumbRight -> { { redo() } }
                    Key.ButtonSelect -> { { runCatching { saveFocus.requestFocus() } } }
                    Key.ButtonStart -> { { runCatching { applyFocus.requestFocus() } } }
                    else -> null
                }
                // B / Back on release, so the key-up doesn't reach the library once Studio is gone.
                if (e.key == Key.ButtonB || e.key == Key.Back || e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) requestClose()
                    return@onPreviewKeyEvent true
                }
                if (action != null && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) action()
                action != null
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // ---- top bar ----
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(stringResource(R.string.theme_back), onClick = requestClose)
            val canUndo = historyTick.let { history.canUndo }
            val canRedo = historyTick.let { history.canRedo }
            IconChip(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.theme_undo), enabled = canUndo) { undo() }
            IconChip(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.theme_redo), enabled = canRedo) { redo() }
            GamepadTextField(
                value = draft.name,
                onValueChange = { edit(draft.copy(name = it.take(GradientRules.MAX_NAME))) },
                textStyle = tccDisplayTextStyle().merge(TextStyle(color = p.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)),
                modifier = Modifier.weight(1f),
                placeholder = untitled,
                verticalPadding = 8.dp,
            )
            message?.let { InfoLine(it, p.success) }
            if (saved) {
                Chip(stringResource(R.string.theme_save_new)) {
                    vm.saveGradient(ownCopy())?.let { draft = it; lastSaved = it; message = savedMsg }
                }
            }
            Box(Modifier.focusRequester(saveFocus)) {
                Chip(stringResource(R.string.theme_save), glyph = GamepadButton.SELECT) {
                    vm.saveGradient(named(draft))?.let { draft = it; lastSaved = it; message = savedMsg }
                }
            }
            Box(Modifier.focusRequester(applyFocus)) {
                AccentButton(stringResource(R.string.theme_save_apply), glyph = GamepadButton.START) {
                    // Done editing: save, show it behind the panel, and go back to the library.
                    vm.saveGradient(named(draft), apply = true)?.let { lastSaved = it; draft = it; onClose() }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // ---- controls ----
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(controlsScroll).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ChipRow(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { controlsScroll.animateScrollTo(0) } }) {
                    GradientStudioTab.entries.forEach { t ->
                        Box(Modifier.focusRequester(tabFocus.getValue(t))) {
                            OptionChip(stringResource(t.titleRes), tab == t) { tab = t }
                        }
                    }
                }
                when (tab) {
                    GradientStudioTab.COLORS -> ColorsTab(
                        draft, stop, ledColor,
                        onSelect = { selectedStop = it },
                        onShuffle = { edit(draft.copy(stops = GradientStudioRules.shuffle(kotlin.random.Random.Default))) },
                        onFromLook = { edit(GradientStudioRules.fromLook(draft, lookAccent, lookBg)) },
                        onChange = { stops, sel -> edit(draft.copy(stops = stops)); selectedStop = sel },
                    )
                    GradientStudioTab.MOTION -> MotionTab(draft, state.animatedBackgrounds) { edit(it) }
                    GradientStudioTab.FINE_TUNE -> FineTuneTab(
                        draft, stop,
                        onSelect = { selectedStop = it },
                        onChange = { next, sel -> edit(next); selectedStop = sel },
                    )
                    GradientStudioTab.PRESETS -> PresetsTab(draft, lookAccent, lookBg) { edit(it) }
                }
            }
            // ---- pinned live preview ----
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioPreview(draft, animate = state.animatedBackgrounds)
                if (!GradientStudioRules.isReadable(draft.stops)) InfoLine(stringResource(R.string.gradient_studio_contrast), p.danger)
                if (!state.animatedBackgrounds) InfoLine(stringResource(R.string.gradient_library_still_hint), p.textTertiary)
                InfoLine(stringResource(R.string.gradient_studio_buttons_hint), p.textTertiary)
            }
        }
    }
    }
    if (confirmDiscard) {
        DiscardChangesDialog(
            onKeep = { confirmDiscard = false },
            onDiscard = { confirmDiscard = false; onClose() },
            bodyRes = R.string.gradient_discard_body,
        )
    }
}

/**
 * The live preview: the panel's renderer at the bottom screen's shape, with a sample label in light text so the user
 * can judge readability. Swapped in for the library's focused tile too ([GradientTilePreview]).
 */
@Composable
private fun StudioPreview(spec: GradientSpec, animate: Boolean) {
    Box(Modifier.fillMaxWidth().aspectRatio(PANEL_ASPECT).clip(cardShape())) {
        GradientBackgroundLayer(spec, Modifier.fillMaxSize(), animate = animate)
        Text(
            stringResource(R.string.gradient_studio_preview_sample), color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, style = tccTextStyle(),
            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
        )
    }
}

/** The color stops as swatches in position order: A picks one (L1 / R1 from anywhere). */
@Composable
private fun StopRow(spec: GradientSpec, selected: Int, onSelect: (Int) -> Unit) {
    ChipRow {
        spec.stops.forEachIndexed { i, s -> Swatch(Color(s.color), i == selected) { onSelect(i) } }
    }
    InfoLine(
        stringResource(
            R.string.gradient_studio_stop_info, selected + 1, spec.stops.size, (spec.stops[selected].position * 100).roundToInt(),
        ),
    )
}

@Composable
private fun ColorsTab(
    draft: GradientSpec,
    selected: Int,
    ledColor: Int?,
    onSelect: (Int) -> Unit,
    onShuffle: () -> Unit,
    onFromLook: () -> Unit,
    onChange: (stops: List<jr.brian.commandpannel.system.GradientStop>, selected: Int) -> Unit,
) {
    Card {
        CardDesc(stringResource(R.string.gradient_studio_colors_desc))
        StopRow(draft, selected, onSelect)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // At a limit the button grays out but keeps focus, so the next press doesn't jump away.
            Chip(
                stringResource(R.string.gradient_studio_add_stop), enabled = draft.stops.size < GradientRules.MAX_STOPS,
                focusableWhenDisabled = true,
            ) { GradientStudioRules.addStop(draft.stops, selected)?.let { (s, i) -> onChange(s, i) } }
            Chip(
                stringResource(R.string.gradient_studio_remove_stop), enabled = draft.stops.size > GradientRules.MIN_STOPS,
                focusableWhenDisabled = true,
            ) { GradientStudioRules.removeStop(draft.stops, selected)?.let { (s, i) -> onChange(s, i) } }
            if (ledColor != null) {
                Chip(stringResource(R.string.theme_color_led)) { onChange(GradientStudioRules.setStopColor(draft.stops, selected, ledColor), selected) }
            }
        }
        ColorTuning(Color(draft.stops[selected].color)) { onChange(GradientStudioRules.setStopColor(draft.stops, selected, it.toArgb()), selected) }
    }
    Card {
        ControlLabel(R.string.gradient_studio_palette)
        CardDesc(stringResource(R.string.gradient_studio_palette_desc))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.gradient_studio_shuffle), onClick = onShuffle)
            Chip(stringResource(R.string.gradient_studio_from_look), onClick = onFromLook)
        }
    }
}

@Composable
private fun MotionTab(draft: GradientSpec, animationsOn: Boolean, onChange: (GradientSpec) -> Unit) {
    Card {
        ControlLabel(R.string.gradient_studio_style)
        ChipRow {
            GradientStyle.entries.forEach { s -> OptionChip(stringResource(s.labelRes), draft.style == s) { onChange(draft.copy(style = s)) } }
        }
        ControlLabel(R.string.gradient_studio_motion)
        ChipRow {
            GradientMotion.entries.forEach { m -> OptionChip(stringResource(m.labelRes), draft.motion == m) { onChange(draft.copy(motion = m)) } }
        }
        val idx = GradientStudioRules.speedIndex(draft.speed)
        val steps = GradientStudioRules.SPEED_STEPS
        StepRow(
            R.string.gradient_studio_speed, stringResource(steps[idx].labelRes),
            canDown = draft.speed > steps.first().speed, canUp = draft.speed < steps.last().speed,
            onDown = { onChange(draft.copy(speed = GradientStudioRules.stepSpeed(draft.speed, -1))) },
            onUp = { onChange(draft.copy(speed = GradientStudioRules.stepSpeed(draft.speed, 1))) },
        )
        if (draft.speed <= 0f) InfoLine(stringResource(R.string.gradient_studio_still_hint), p.textTertiary)
        else if (!animationsOn) InfoLine(stringResource(R.string.gradient_library_still_hint), p.textTertiary)
        StepRow(
            R.string.gradient_studio_angle, stringResource(R.string.theme_value_degrees, draft.angle.roundToInt().toString()),
            canDown = true, canUp = true,
            onDown = { onChange(draft.copy(angle = GradientStudioRules.stepAngle(draft.angle, -GradientStudioRules.ANGLE_STEP))) },
            onUp = { onChange(draft.copy(angle = GradientStudioRules.stepAngle(draft.angle, GradientStudioRules.ANGLE_STEP))) },
        )
        ChipRow {
            GradientStudioRules.ANGLE_PICKS.forEach { a ->
                OptionChip(stringResource(R.string.theme_value_degrees, a.roundToInt().toString()), draft.angle == a) { onChange(draft.copy(angle = a)) }
            }
        }
    }
}

@Composable
private fun FineTuneTab(
    draft: GradientSpec,
    selected: Int,
    onSelect: (Int) -> Unit,
    onChange: (GradientSpec, selected: Int) -> Unit,
) {
    Card {
        CardDesc(stringResource(R.string.gradient_studio_fine_desc))
        StopRow(draft, selected, onSelect)
        Stepper(
            R.string.gradient_studio_position, draft.stops[selected].position, 0f, 1f, GradientStudioRules.POSITION_STEP, ValueUnit.PERCENT,
        ) { pos ->
            val (stops, i) = GradientStudioRules.moveStop(draft.stops, selected, pos)
            onChange(draft.copy(stops = stops), i)
        }
        StepRow(
            R.string.gradient_studio_angle_exact, stringResource(R.string.theme_value_degrees, draft.angle.roundToInt().toString()),
            canDown = true, canUp = true,
            onDown = { onChange(draft.copy(angle = GradientStudioRules.stepAngle(draft.angle, -GradientStudioRules.FINE_ANGLE_STEP)), selected) },
            onUp = { onChange(draft.copy(angle = GradientStudioRules.stepAngle(draft.angle, GradientStudioRules.FINE_ANGLE_STEP)), selected) },
        )
        Stepper(
            R.string.gradient_studio_speed_exact, draft.speed, GradientRules.MIN_SPEED, GradientRules.MAX_SPEED,
            GradientStudioRules.FINE_SPEED_STEP, ValueUnit.PERCENT,
        ) { onChange(draft.copy(speed = it), selected) }
    }
}

@Composable
private fun PresetsTab(draft: GradientSpec, accent: Int, background: Int, onChange: (GradientSpec) -> Unit) {
    val names = GradientPresets.all.map { stringResource(it.nameRes) }
    val presets = remember(accent, background, names) { GradientPresets.all.mapIndexed { i, preset -> preset.spec(names[i], accent, background) } }
    Card {
        CardDesc(stringResource(R.string.gradient_studio_presets_desc))
        presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    val picked = GradientStudioRules.applyPreset(draft, preset) == draft
                    OptionChip(
                        preset.name, picked,
                        leading = { Box(Modifier.size(28.dp, 18.dp).clip(controlShape(4.dp)).gradientSwatch(preset)) },
                    ) { onChange(GradientStudioRules.applyPreset(draft, preset)) }
                }
            }
        }
    }
}

/**
 * A − value + row with a text value (named speeds, wrapping angles), [Stepper]'s look: the buttons gray out at a limit
 * but keep focus, and holding a finger repeats.
 */
@Composable
private fun StepRow(
    @StringRes titleRes: Int,
    text: String,
    canDown: Boolean,
    canUp: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    val downOk by rememberUpdatedState(canDown)
    val upOk by rememberUpdatedState(canUp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ControlLabel(titleRes)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.repeatWhileHeld { if (downOk) down() }) {
                Chip(stringResource(R.string.theme_decrease), enabled = canDown, focusableWhenDisabled = true) { down() }
            }
            Text(
                text, color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 96.dp),
            )
            Box(Modifier.repeatWhileHeld { if (upOk) up() }) {
                Chip(stringResource(R.string.theme_increase), enabled = canUp, focusableWhenDisabled = true) { up() }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
