package jr.brian.commandpannel.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import jr.brian.commandpannel.system.GradientPresets
import jr.brian.commandpannel.system.GradientRules
import jr.brian.commandpannel.system.GradientSpec
import jr.brian.commandpannel.system.GradientStyle
import jr.brian.commandpannel.ui.components.tccLookBackdrop
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.lookLabel
import jr.brian.commandpannel.ui.theme.tccDisplayTextStyle
import jr.brian.commandpannel.ui.theme.tccLookColors
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Tiles per row in the library. */
private const val TILES_PER_ROW = 3

/** [pendingFocus] value for the Back button. */
private const val FOCUS_BACK = "\u0000back"

/**
 * The user's saved gradient backgrounds (#67), opened from Appearance's "Create your own animated background" card.
 * One background shows at a time on every look; applying a gradient keeps the stored image, which "Use my image
 * instead" switches back to.
 *
 * Below the user's gradients (above them while there are none) sit the built-in presets (#69, [GradientPresets]): "Use"
 * saves an editable copy into the user's gradients and shows it. The two "Match my look" presets take the panel look's
 * accent and background each time the screen composes.
 *
 * Gamepad: focus starts on the showing gradient's Apply (else the first tile, else the first preset's Use, else Back);
 * the D-pad moves between the header's buttons, the tiles' Apply / Duplicate / Delete and the presets' Use; B goes
 * back. Delete takes two presses. When a button disappears (Use my image, a deleted tile) focus moves to its neighbor
 * (the first preset once no tiles are left) instead of the screen's first control. After Use, focus goes to the new
 * copy's Applied button.
 *
 * [onNew] and [onEdit] are the gradient editor's entry points (#68): null hides them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GradientLibraryScreen(
    state: SettingsState,
    vm: SettingsViewModel,
    onClose: () -> Unit,
    // TODO(#68): CompanionNavHost passes the editor's routes here.
    onNew: (() -> Unit)? = null,
    onEdit: ((GradientSpec) -> Unit)? = null,
) {
    BackHandler(onBack = onClose)
    val backFocus = remember { FocusRequester() }
    val applyFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun applyRequester(id: String) = applyFocus.getOrPut(id) { FocusRequester() }
    // The presets, named and (for "Match my look") colored by the panel's current look.
    val (lookColors, _) = rememberOverlayLook(state)
    val accent = lookColors.accent.toArgb()
    val lookBg = lookColors.primaryBackground.toArgb()
    val presetNames = GradientPresets.all.map { stringResource(it.nameRes) }
    val presets = remember(accent, lookBg, presetNames) {
        GradientPresets.all.mapIndexed { i, preset -> preset.spec(presetNames[i], accent, lookBg) }
    }
    val firstPreset = presets.firstOrNull()?.id
    // A tile or preset (by id) or [FOCUS_BACK] to focus once it's composed.
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.refreshGradients()
        vm.clearGradientMessage()
        val s = vm.state.value
        pendingFocus = s.activeGradientId?.takeIf { id -> s.gradients.any { it.id == id } }
            ?: s.gradients.firstOrNull()?.id ?: firstPreset ?: FOCUS_BACK
    }
    LaunchedEffect(pendingFocus, state.gradients) {
        val target = pendingFocus ?: return@LaunchedEffect
        if (target != FOCUS_BACK && state.gradients.none { it.id == target } && presets.none { it.id == target }) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        runCatching { (if (target == FOCUS_BACK) backFocus else applyRequester(target)).requestFocus() }
        pendingFocus = null
    }

    val showing = GradientLibrary.showing(state.activeGradientId, state.gradients, state.backgroundImage != null)
    val activeName = state.gradients.firstOrNull { it.id == state.activeGradientId }?.let { gradientName(it) }
    val backdrop = if (LocalCompanionThemed.current) {
        Modifier.tccLookBackdrop(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())
    } else {
        Modifier.background(p.bg)
    }
    PaddedBringIntoView {
        Column(
            Modifier.fillMaxSize().then(backdrop).systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 34.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.focusRequester(backFocus)) { Chip(stringResource(R.string.theme_back), onClick = onClose) }
                Text(
                    lookLabel(stringResource(R.string.gradient_library_title)), color = p.textPrimary, fontSize = 26.sp,
                    fontWeight = FontWeight.Bold, style = tccDisplayTextStyle(), modifier = Modifier.weight(1f),
                )
                onNew?.let { Chip(stringResource(R.string.gradient_new), onClick = it) }
            }
            Card {
                Text(
                    when (showing) {
                        GradientLibrary.Showing.GRADIENT -> stringResource(R.string.gradient_library_now_gradient, activeName.orEmpty())
                        GradientLibrary.Showing.IMAGE -> stringResource(R.string.gradient_library_now_image)
                        GradientLibrary.Showing.BACKDROP -> stringResource(R.string.gradient_library_now_backdrop)
                    },
                    color = p.textPrimary, fontSize = 15.sp, style = tccTextStyle(),
                )
                if (!state.animatedBackgrounds) InfoLine(stringResource(R.string.gradient_library_still_hint), p.textTertiary)
                if (GradientLibrary.canUseImage(state.activeGradientId, state.gradients, state.backgroundImage != null)) {
                    Spacer(Modifier.height(4.dp))
                    Chip(stringResource(R.string.settings_gradients_use_image)) {
                        // The button goes away: focus moves to the gradient that was showing.
                        val was = state.activeGradientId
                        vm.useBackgroundImage()
                        pendingFocus = was ?: FOCUS_BACK
                    }
                }
                state.gradientMessage?.let { InfoLine(it, p.accent) }
            }
            val presetSection: @Composable () -> Unit = {
                SectionHeader(stringResource(R.string.gradient_presets_title))
                InfoLine(stringResource(R.string.gradient_presets_desc))
                TileRows(presets) { preset, modifier ->
                    PresetTile(
                        spec = preset,
                        animationsOn = state.animatedBackgrounds,
                        useFocus = applyRequester(preset.id),
                        onUse = { vm.usePreset(preset)?.let { pendingFocus = it } },
                        modifier = modifier,
                    )
                }
            }
            if (state.gradients.isEmpty()) {
                presetSection()
                Card {
                    CardTitle(stringResource(R.string.gradient_library_empty_title))
                    CardDesc(stringResource(R.string.gradient_library_empty_desc))
                }
            } else {
                TileRows(state.gradients) { spec, modifier ->
                            GradientTile(
                                spec = spec,
                                active = spec.id == state.activeGradientId,
                                animationsOn = state.animatedBackgrounds,
                                applyFocus = applyRequester(spec.id),
                                onApply = { vm.applyGradient(spec.id) },
                                onEdit = onEdit?.let { edit -> { edit(spec) } },
                                onDuplicate = { vm.duplicateGradient(spec.id)?.let { pendingFocus = it } },
                                onDelete = {
                                    val next = GradientLibrary.focusAfterDelete(state.gradients.map { it.id }, spec.id)
                                    vm.deleteGradient(spec.id)
                                    applyFocus.remove(spec.id)
                                    pendingFocus = next ?: firstPreset ?: FOCUS_BACK
                                },
                                modifier = modifier,
                            )
                }
                presetSection()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** [items] as rows of [TILES_PER_ROW] equal-width tiles; [tile] gets the width modifier to apply. */
@Composable
private fun TileRows(items: List<GradientSpec>, tile: @Composable (GradientSpec, Modifier) -> Unit) {
    items.chunked(TILES_PER_ROW).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { spec -> androidx.compose.runtime.key(spec.id) { tile(spec, Modifier.weight(1f)) } }
            repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * A built-in preset (#69): the same swatch and name as a saved gradient's tile, with one action, Use, which saves an
 * editable copy into the user's gradients and shows it.
 */
@Composable
private fun PresetTile(
    spec: GradientSpec,
    animationsOn: Boolean,
    useFocus: FocusRequester,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Column(
        modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .bringIntoViewOnFocus()
            .companionCard(focused = hasFocus)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GradientTilePreview(
            spec, animate = hasFocus && GradientRules.moves(spec, animationsOn),
            modifier = Modifier.fillMaxWidth().height(110.dp).clip(controlShape(10.dp)),
        )
        Text(spec.name, color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(), maxLines = 1)
        InfoLine(stringResource(R.string.gradient_tile_meta, stringResource(spec.style.labelRes), stringResource(spec.motion.labelRes)))
        Box(Modifier.focusRequester(useFocus)) { Chip(stringResource(R.string.gradient_preset_use), onClick = onUse) }
    }
}

/** [spec]'s name, or "Untitled gradient" for a blank one. */
@Composable
private fun gradientName(spec: GradientSpec): String = spec.name.ifBlank { stringResource(R.string.gradient_unnamed) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradientTile(
    spec: GradientSpec,
    active: Boolean,
    animationsOn: Boolean,
    applyFocus: FocusRequester,
    onApply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Two presses, so a stray A on the gamepad can't wipe a gradient. Leaving the tile cancels.
    var confirmDelete by remember(spec.id) { mutableStateOf(false) }
    Column(
        modifier
            .onFocusChanged { hasFocus = it.hasFocus; if (!it.hasFocus) confirmDelete = false }
            .bringIntoViewOnFocus()
            .companionCard(focused = hasFocus || active)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Only the focused tile moves; the rest show a still frame.
        GradientTilePreview(
            spec, animate = hasFocus && GradientRules.moves(spec, animationsOn),
            modifier = Modifier.fillMaxWidth().height(110.dp).clip(controlShape(10.dp)),
        )
        Text(gradientName(spec), color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle(), maxLines = 1)
        InfoLine(
            stringResource(
                R.string.gradient_tile_meta,
                stringResource(spec.style.labelRes),
                stringResource(if (spec.speed <= 0f) R.string.gradient_motion_still else spec.motion.labelRes),
            ),
        )
        if (active) InfoLine(stringResource(R.string.gradient_showing), p.accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.focusRequester(applyFocus)) {
                Chip(stringResource(if (active) R.string.theme_applied else R.string.theme_apply), selected = active) {
                    if (!active) onApply()
                }
            }
            onEdit?.let { Chip(stringResource(R.string.theme_edit), onClick = it) }
            Chip(stringResource(R.string.gradient_duplicate), onClick = onDuplicate)
            Chip(stringResource(if (confirmDelete) R.string.theme_delete_confirm else R.string.theme_delete), selected = confirmDelete) {
                if (confirmDelete) onDelete() else confirmDelete = true
            }
        }
    }
}

/**
 * A gradient's preview on a library tile. The swap-in point for the renderer (#66): once
 * `GradientBackgroundLayer(spec, modifier, animate)` lands, draw it here instead of the static swatch, at least for
 * [animate] (the focused tile, with Animated backgrounds on and a speed above 0). The other tiles can keep the swatch
 * or use the layer with `animate = false`.
 */
@Composable
internal fun GradientTilePreview(spec: GradientSpec, animate: Boolean, modifier: Modifier = Modifier) {
    // TODO(#66): replace with jr.brian.commandpannel.ui.components.GradientBackgroundLayer(spec, modifier, animate)
    // (ui/components/GradientBackground.kt). With animate = false it draws one still frame through a shared shader,
    // so every tile can use it; only the focused one passes animate = true.
    Box(modifier.gradientSwatch(spec))
}

/**
 * A cheap, still approximation of [spec] (no motion): linear along [GradientSpec.angle] (0 degrees runs left to right,
 * turning clockwise), radial from the center, and Aurora as the linear one with two soft glows of its end colors.
 */
internal fun Modifier.gradientSwatch(spec: GradientSpec): Modifier = drawWithCache {
    val stops = spec.stops.map { it.position to Color(it.color) }.toTypedArray()
    val c = Offset(size.width / 2f, size.height / 2f)
    val rad = Math.toRadians(spec.angle.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val linear = Brush.linearGradient(
        *stops,
        start = Offset(c.x - dx * half, c.y - dy * half),
        end = Offset(c.x + dx * half, c.y + dy * half),
    )
    val radius = size.maxDimension * 0.75f
    val main = when (spec.style) {
        GradientStyle.RADIAL -> Brush.radialGradient(*stops, center = c, radius = radius.coerceAtLeast(1f))
        GradientStyle.LINEAR, GradientStyle.AURORA -> linear
    }
    val glows = if (spec.style == GradientStyle.AURORA) {
        val first = Color(spec.stops.first().color)
        val last = Color(spec.stops.last().color)
        val r = (size.maxDimension * 0.6f).coerceAtLeast(1f)
        listOf(
            Brush.radialGradient(listOf(last.copy(alpha = 0.7f), Color.Transparent), center = Offset(size.width * 0.25f, size.height * 0.2f), radius = r),
            Brush.radialGradient(listOf(first.copy(alpha = 0.6f), Color.Transparent), center = Offset(size.width * 0.8f, size.height * 0.85f), radius = r),
        )
    } else {
        emptyList()
    }
    onDrawBehind {
        drawRect(main)
        glows.forEach { drawRect(it) }
    }
}
