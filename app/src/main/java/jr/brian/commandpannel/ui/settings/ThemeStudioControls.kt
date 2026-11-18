package jr.brian.commandpannel.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jr.brian.commandpannel.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.compositeOver
import jr.brian.commandpannel.ui.theme.contrastRatio
import jr.brian.commandpannel.ui.theme.resolveLookPalette
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import jr.brian.commandpannel.ui.components.noRippleClickable
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomRenderParams
import jr.brian.commandpannel.ui.theme.FillKind
import jr.brian.commandpannel.ui.theme.GaugeColorMode
import jr.brian.commandpannel.ui.theme.GaugeKind
import jr.brian.commandpannel.ui.theme.GaugeStyle
import jr.brian.commandpannel.ui.theme.LineRole
import jr.brian.commandpannel.ui.theme.NavIndicator
import jr.brian.commandpannel.ui.theme.NavStyle
import jr.brian.commandpannel.ui.theme.SliderStyle
import jr.brian.commandpannel.ui.theme.TileStyle
import jr.brian.commandpannel.ui.theme.LabelCase
import jr.brian.commandpannel.ui.theme.PinnedColors
import jr.brian.commandpannel.ui.theme.ShapeDef
import jr.brian.commandpannel.ui.theme.ShapeKind
import jr.brian.commandpannel.ui.theme.TccLookSpec
import jr.brian.commandpannel.ui.theme.specForLook
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlin.math.roundToInt

/**
 * The from-scratch controls of [ThemeStudioScreen]: shapes, pinned colors, label text, effects and gauge fit. Every
 * control shows the effective value (the override, else the base look's) and offers "Look default" to clear it.
 */

// ---- Shapes ----

private class ShapeTarget(
    @StringRes val titleRes: Int,
    val get: (CustomLook) -> ShapeDef?,
    val set: (CustomLook, ShapeDef?) -> CustomLook,
    val allowCircle: Boolean = false,
    val part: StudioPart? = null,
)

private val SHAPE_TARGETS = listOf(
    ShapeTarget(R.string.theme_shape_cards, { it.panelCardShape }, { l, v -> l.copy(panelCardShape = v) }, part = StudioPart.CARDS),
    ShapeTarget(R.string.theme_shape_tiles, { it.tileShape }, { l, v -> l.copy(tileShape = v) }, allowCircle = true, part = StudioPart.TILES),
    ShapeTarget(R.string.theme_shape_slider_controls, { it.sliderControlShape }, { l, v -> l.copy(sliderControlShape = v) }, part = StudioPart.SLIDERS),
    ShapeTarget(R.string.theme_shape_slider_tracks, { it.sliderTrackShape }, { l, v -> l.copy(sliderTrackShape = v) }, part = StudioPart.SLIDERS),
    ShapeTarget(R.string.theme_shape_dialogs, { it.dialogShape }, { l, v -> l.copy(dialogShape = v) }),
    ShapeTarget(R.string.theme_shape_fps_badge, { it.fpsBadgeShape }, { l, v -> l.copy(fpsBadgeShape = v) }, allowCircle = true, part = StudioPart.FPS),
)

private val ShapeKind.labelRes: Int get() = when (this) {
    ShapeKind.ROUNDED -> R.string.theme_shape_rounded
    ShapeKind.RECT -> R.string.theme_shape_square
    ShapeKind.CHAMFER -> R.string.theme_shape_chamfer
    ShapeKind.SQUIRCLE -> R.string.theme_shape_squircle
    ShapeKind.PILL -> R.string.theme_shape_pill
    ShapeKind.CIRCLE -> R.string.theme_shape_circle
}

/** Starting size when a shape kind is first picked (dp, or the radius fraction for squircles). */
private fun ShapeKind.defaultSize(): Float = when (this) {
    ShapeKind.ROUNDED -> 16f
    ShapeKind.CHAMFER -> 9f
    ShapeKind.SQUIRCLE -> 0.3f
    else -> 0f
}

@Composable
internal fun ShapeControls(draft: CustomLook, onChange: (CustomLook) -> Unit) {
    Card {
        CardDesc(stringResource(R.string.theme_shapes_desc))
        SHAPE_TARGETS.forEach { target ->
            val current = target.get(draft)
            Column(Modifier.studioPart(target.part), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ControlLabel(target.titleRes)
            ChipRow {
                OptionChip(stringResource(R.string.theme_look_default), current == null) { onChange(target.set(draft, null)) }
                ShapeKind.entries.filter { it != ShapeKind.CIRCLE || target.allowCircle }.forEach { kind ->
                    OptionChip(stringResource(kind.labelRes), current?.kind == kind) {
                        onChange(target.set(draft, ShapeDef(kind, current?.takeIf { it.kind == kind }?.size ?: kind.defaultSize())))
                    }
                }
            }
            if (current != null) when (current.kind) {
                ShapeKind.ROUNDED -> Stepper(R.string.theme_shape_radius, current.size, 0f, 32f, 2f, ValueUnit.DP) {
                    onChange(target.set(draft, current.copy(size = it)))
                }
                ShapeKind.CHAMFER -> Stepper(R.string.theme_shape_cut, current.size, 2f, 20f, 1f, ValueUnit.DP) {
                    onChange(target.set(draft, current.copy(size = it)))
                }
                ShapeKind.SQUIRCLE -> Stepper(R.string.theme_shape_roundness, current.size, 0.1f, 0.5f, 0.05f, ValueUnit.PERCENT) {
                    onChange(target.set(draft, current.copy(size = it)))
                }
                else -> Unit
            }
            }
        }
    }
}

// ---- Colors ----

private val ACCENT_SWATCHES = listOf(
    0xFF8E84FF, 0xFF7C6FFF, 0xFF4F8CFF, 0xFF22B8F0, 0xFF19C2A0, 0xFF3DDC84, 0xFFB5E61D,
    0xFFFFD54F, 0xFFFF9F43, 0xFFFF6B4A, 0xFFF64646, 0xFFFF4F9A, 0xFFD45CFF, 0xFFE8E8E8,
).map { Color(it) }

private val SURFACE_SWATCHES = listOf(
    0xFF000000, 0xFF0B0D12, 0xFF12141A, 0xFF1A1625, 0xFF0E1A14, 0xFF1C1712, 0xFF22262E,
    0xFFF7F1E3, 0xFFFFFFFF, 0xFFF4F4F4, 0xFFEEF2F7, 0xFFF3EDF7, 0xFFE9F3EC, 0xFFD9DEE6,
).map { Color(it) }

private class ColorRole(
    @StringRes val titleRes: Int,
    val swatches: List<Color>,
    val get: (PinnedColors) -> Color?,
    val set: (PinnedColors, Color?) -> PinnedColors,
)

private val COLOR_ROLES = listOf(
    ColorRole(R.string.theme_color_accent, ACCENT_SWATCHES, { it.accent }, { c, v -> c.copy(accent = v) }),
    ColorRole(R.string.theme_color_accent2, ACCENT_SWATCHES, { it.accent2 }, { c, v -> c.copy(accent2 = v) }),
    ColorRole(R.string.theme_color_background, SURFACE_SWATCHES, { it.background }, { c, v -> c.copy(background = v) }),
    ColorRole(R.string.theme_color_panel, SURFACE_SWATCHES, { it.panel }, { c, v -> c.copy(panel = v) }),
)

@Composable
internal fun ColorControls(draft: CustomLook, ledColor: Int?, onChange: (CustomLook) -> Unit) {
    Card {
        CardDesc(stringResource(R.string.theme_colors_desc))
        // A pick that makes text or the accent hard to see on the panel gets a gentle warning.
        val palette = resolveLookPalette(4, nightActive = true, accentOverride = null, pinned = draft.colors)
        val panel = palette.panelBackground.compositeOver(palette.primaryBackground)
        if (palette.primary.contrastRatio(panel) < 4.5f) InfoLine(stringResource(R.string.theme_contrast_text), p.danger)
        if (palette.accent.contrastRatio(panel) < 3f) InfoLine(stringResource(R.string.theme_contrast_accent), p.danger)
        COLOR_ROLES.forEach { role ->
            val current = role.get(draft.colors)
            ControlLabel(role.titleRes)
            ChipRow {
                OptionChip(stringResource(R.string.theme_color_follow), current == null) {
                    onChange(draft.copy(colors = role.set(draft.colors, null)))
                }
                role.swatches.forEach { color ->
                    Swatch(color, current == color) { onChange(draft.copy(colors = role.set(draft.colors, color))) }
                }
                // The accents can also take the Thor's LED color.
                if (ledColor != null && role.swatches === ACCENT_SWATCHES) {
                    Chip(stringResource(R.string.theme_color_led)) { onChange(draft.copy(colors = role.set(draft.colors, Color(ledColor)))) }
                }
            }
            if (current != null) ColorTuning(current) { onChange(draft.copy(colors = role.set(draft.colors, it))) }
        }
    }
}

/** Fine-tunes a picked color: hue, saturation and brightness steppers, and an exact hex value. */
@Composable
private fun ColorTuning(color: Color, onChange: (Color) -> Unit) {
    val hsv = argbToHsv(color.toArgb())
    Stepper(R.string.theme_color_hue, hsv.h, 0f, 350f, 10f, ValueUnit.DEGREES) { onChange(Color(hsvToArgb(hsv.copy(h = it)))) }
    Stepper(R.string.theme_color_saturation, hsv.s, 0f, 1f, 0.05f, ValueUnit.PERCENT) { onChange(Color(hsvToArgb(hsv.copy(s = it)))) }
    Stepper(R.string.theme_color_brightness, hsv.v, 0f, 1f, 0.05f, ValueUnit.PERCENT) { onChange(Color(hsvToArgb(hsv.copy(v = it)))) }
    ControlLabel(R.string.theme_color_hex)
    // Applied as soon as it's six hex digits; re-synced when the color changes another way.
    var hex by remember(color) { mutableStateOf(formatHexColor(color.toArgb())) }
    GamepadTextField(
        value = hex,
        onValueChange = { text -> hex = text.take(7); parseHexColor(hex)?.let { onChange(Color(it)) } },
        textStyle = TextStyle(color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.width(180.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
    )
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val f = focusState()
    val scale by focusScale(f.value, 1.15f, "swatchScale")
    val hex = "#%06X".format(color.toArgb() and 0xFFFFFF)
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .size(38.dp)
            .then(if (selected || f.value) Modifier.border(2.5.dp, if (f.value) p.textPrimary else p.accent, CircleShape) else Modifier)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, p.border, CircleShape)
            .semantics { contentDescription = hex }
            .focusControl(f, onClick)
            .noRippleClickable(onClick = onClick),
    )
}

// ---- Text ----

private val LabelCase.labelRes: Int get() = when (this) {
    LabelCase.NONE -> R.string.theme_case_none
    LabelCase.UPPER -> R.string.theme_case_upper
    LabelCase.LOWER -> R.string.theme_case_lower
    LabelCase.SNAKE -> R.string.theme_case_snake
}

@Composable
internal fun TextControls(draft: CustomLook, base: TccLookSpec, onChange: (CustomLook) -> Unit) {
    Card {
        CardDesc(stringResource(R.string.theme_text_desc))
        ControlLabel(R.string.theme_text_case)
        ChipRow {
            OptionChip(stringResource(R.string.theme_look_default), draft.labelCase == null) { onChange(draft.copy(labelCase = null)) }
            LabelCase.entries.forEach { case ->
                OptionChip(stringResource(case.labelRes), draft.labelCase == case) { onChange(draft.copy(labelCase = case)) }
            }
        }
        val baseSpacing = base.traits.labelLetterSpacing.let { if (it.isEm) it.value else 0f }
        OverridableStepper(R.string.theme_text_spacing, draft.letterSpacingEm, baseSpacing, -0.05f, 0.3f, 0.01f, ValueUnit.EM) {
            onChange(draft.copy(letterSpacingEm = it))
        }
        OverridableStepper(R.string.theme_text_tile_size, draft.qsLabelFontSize, base.tile.qsLabelFontSize.value, 8f, 13f, 0.5f, ValueUnit.SP, part = StudioPart.TILES) {
            onChange(draft.copy(qsLabelFontSize = it))
        }
    }
}

// ---- Effects ----

private class Toggle(@StringRes val titleRes: Int, val get: (CustomLook) -> Boolean?, val base: (TccLookSpec) -> Boolean, val set: (CustomLook, Boolean?) -> CustomLook)

private val TOGGLES = listOf(
    Toggle(R.string.theme_effect_glow, { it.glow }, { it.traits.glow }, { l, v -> l.copy(glow = v) }),
    Toggle(R.string.theme_effect_scanlines, { it.scanlines }, { it.traits.scanlines }, { l, v -> l.copy(scanlines = v) }),
    Toggle(R.string.theme_effect_motion, { it.animate }, { it.traits.animate }, { l, v -> l.copy(animate = v) }),
    Toggle(R.string.theme_effect_lifted, { it.liftedBackdrop }, { it.traits.liftedBackdrop }, { l, v -> l.copy(liftedBackdrop = v) }),
    Toggle(R.string.theme_effect_oled, { it.oledBackdrop }, { it.traits.oledBackdrop }, { l, v -> l.copy(oledBackdrop = v) }),
    Toggle(R.string.theme_effect_ghost, { it.ghostSegments }, { it.traits.ghostSegments }, { l, v -> l.copy(ghostSegments = v) }),
)

@Composable
internal fun EffectControls(draft: CustomLook, base: TccLookSpec, onChange: (CustomLook) -> Unit) {
    Card {
        CardDesc(stringResource(R.string.theme_effects_desc))
        TOGGLES.forEach { t ->
            val v = t.get(draft)
            ControlLabel(t.titleRes)
            ChipRow {
                val def = stringResource(if (t.base(base)) R.string.theme_on else R.string.theme_off)
                OptionChip(stringResource(R.string.theme_look_default_value, def), v == null) { onChange(t.set(draft, null)) }
                OptionChip(stringResource(R.string.theme_on), v == true) { onChange(t.set(draft, true)) }
                OptionChip(stringResource(R.string.theme_off), v == false) { onChange(t.set(draft, false)) }
            }
        }
        OverridableStepper(R.string.theme_effect_outline, draft.outlineWidth, base.traits.outlineWidth.value, 0f, 6f, 0.5f, ValueUnit.DP, part = StudioPart.CARDS) {
            onChange(draft.copy(outlineWidth = it))
        }
        OverridableStepper(R.string.theme_effect_shadow, draft.shadowOffset, base.traits.shadowOffset.value, 0f, 10f, 1f, ValueUnit.DP, part = StudioPart.CARDS) {
            onChange(draft.copy(shadowOffset = it))
        }
        OverridableStepper(R.string.theme_effect_pattern, draft.backdropPatternAlpha, base.traits.backdropPatternAlpha, 0f, 1f, 0.1f, ValueUnit.PERCENT, part = StudioPart.BACKDROP) {
            onChange(draft.copy(backdropPatternAlpha = it))
        }
    }
}

// ---- Fit ----

@Composable
internal fun FitControls(draft: CustomLook, base: TccLookSpec, onChange: (CustomLook) -> Unit) {
    val b = base.settingsTab
    Card(Modifier.studioPart(StudioPart.FIT)) {
        CardDesc(stringResource(R.string.theme_fit_desc))
        OverridableStepper(R.string.theme_fit_ring_size, draft.ringDiameterRatio, b.ringDiameterRatio, 0.6f, 1f, 0.02f, ValueUnit.PERCENT) {
            onChange(draft.copy(ringDiameterRatio = it))
        }
        OverridableStepper(R.string.theme_fit_ring_value, draft.ringValueScale, b.ringValueScale, 0.12f, 0.4f, 0.01f, ValueUnit.PERCENT) {
            onChange(draft.copy(ringValueScale = it))
        }
        OverridableStepper(R.string.theme_fit_ring_title, draft.ringTitleScale, b.ringTitleScale, 0.06f, 0.2f, 0.01f, ValueUnit.PERCENT) {
            onChange(draft.copy(ringTitleScale = it))
        }
        OverridableStepper(R.string.theme_fit_ring_unit, draft.ringUnitScale, b.ringUnitScale, 0.06f, 0.2f, 0.01f, ValueUnit.PERCENT) {
            onChange(draft.copy(ringUnitScale = it))
        }
        OverridableStepper(R.string.theme_fit_info_value, draft.infoValueScale, b.infoValueScale, 0.5f, 1.3f, 0.05f, ValueUnit.PERCENT) {
            onChange(draft.copy(infoValueScale = it))
        }
    }
}

// ---- shared bits ----

/** The base look's spec, which every "Look default" value comes from. */
internal fun CustomLook.baseSpec(): TccLookSpec = specForLook(baseLook)

@Composable
internal fun ControlLabel(@StringRes res: Int) =
    Text(
        jr.brian.commandpannel.ui.theme.lookLabel(stringResource(res)), color = p.textSecondary, fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold, style = tccTextStyle(), modifier = Modifier.padding(top = 4.dp),
    )

/** A − value + row for a number; [onChange] gets the clamped, step-rounded value. */
@Composable
private fun Stepper(
    @StringRes titleRes: Int,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    unit: ValueUnit,
    part: StudioPart? = null,
    trailing: @Composable () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(min, max)
    // Latest value and callback for the held-down repeat, which outlives a single composition.
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val down = { if (current > min + step / 2) change(snap(current - step)) }
    val up = { if (current < max - step / 2) change(snap(current + step)) }
    Column(Modifier.studioPart(part), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    ControlLabel(titleRes)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // At a limit the button greys out but keeps focus, so the next press doesn't jump away (to Back). Holding A
        // repeats through the key's own auto-repeat; holding a finger repeats through repeatWhileHeld.
        Box(Modifier.repeatWhileHeld(down)) {
            Chip(stringResource(R.string.theme_decrease), enabled = value > min + step / 2, focusableWhenDisabled = true, onClick = down)
        }
        // Centered between − and +; a longer value widens the slot rather than wrapping.
        Text(
            unit.format(value), color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.widthIn(min = 64.dp),
        )
        Box(Modifier.repeatWhileHeld(up)) {
            Chip(stringResource(R.string.theme_increase), enabled = value < max - step / 2, focusableWhenDisabled = true, onClick = up)
        }
        Spacer(Modifier.width(4.dp))
        trailing()
    }
    }
}

/**
 * Repeats [onStep] while a finger stays down: after 400 ms, then faster down to every 60 ms. The first step is the
 * child's own click; once repeating starts, the release is consumed so it doesn't add one more step.
 */
private fun Modifier.repeatWhileHeld(onStep: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var wait = 400L
        var repeated = false
        while (true) {
            // Released (or canceled) before the wait ran out: stop.
            val ended = withTimeoutOrNull(wait) {
                waitForUpOrCancellation(PointerEventPass.Initial).also { if (repeated) it?.consume() }
                true
            }
            if (ended != null) break
            onStep()
            repeated = true
            wait = (wait * 0.7f).toLong().coerceAtLeast(60L)
        }
    }
}

/** A [Stepper] over an override: shows [override] or [baseValue], and offers a reset back to the look default. */
@Composable
private fun OverridableStepper(
    @StringRes titleRes: Int,
    override: Float?,
    baseValue: Float,
    min: Float,
    max: Float,
    step: Float,
    unit: ValueUnit,
    part: StudioPart? = null,
    onChange: (Float?) -> Unit,
) {
    Stepper(
        titleRes, override ?: baseValue, min, max, step, unit, part,
        trailing = {
            if (override != null) Chip(stringResource(R.string.theme_look_default)) { onChange(null) }
            else InfoLine(stringResource(R.string.theme_look_default))
        },
        onChange = onChange,
    )
}

internal enum class ValueUnit(@StringRes val res: Int) {
    DP(R.string.theme_value_dp), SP(R.string.theme_value_sp), EM(R.string.theme_value_em), PERCENT(R.string.theme_value_percent),
    DEGREES(R.string.theme_value_degrees);

    @Composable
    fun format(v: Float): String = stringResource(
        res,
        when (this) {
            DP -> if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)
            SP -> "%.1f".format(v)
            EM -> "%.2f".format(v)
            PERCENT -> (v * 100).roundToInt().toString()
            DEGREES -> v.roundToInt().toString()
        },
    )
}

// ---- Custom parts ----

private val FillKind.labelRes: Int get() = when (this) {
    FillKind.SURFACE -> R.string.theme_fill_surface
    FillKind.TONAL -> R.string.theme_fill_tonal
    FillKind.ACCENT -> R.string.theme_fill_accent
    FillKind.GLASS -> R.string.theme_fill_glass
    FillKind.GRADIENT -> R.string.theme_fill_gradient
    FillKind.NONE -> R.string.theme_fill_none
}

private val LineRole.labelRes: Int get() = when (this) {
    LineRole.SUBTLE -> R.string.theme_line_subtle
    LineRole.TEXT -> R.string.theme_line_text
    LineRole.ACCENT -> R.string.theme_line_accent
    LineRole.ACCENT2 -> R.string.theme_line_accent2
}

private val GaugeKind.labelRes: Int get() = when (this) {
    GaugeKind.RING -> R.string.theme_gauge_ring
    GaugeKind.ARC -> R.string.theme_gauge_arc
    GaugeKind.SEGMENTS -> R.string.theme_gauge_segments
    GaugeKind.BAR -> R.string.theme_gauge_bar
}

private val GaugeColorMode.labelRes: Int get() = when (this) {
    GaugeColorMode.CLASSIC -> R.string.theme_gauge_colors_classic
    GaugeColorMode.ACCENT -> R.string.theme_gauge_colors_accent
    GaugeColorMode.SPECTRUM -> R.string.theme_gauge_colors_spectrum
}

private val NavIndicator.labelRes: Int get() = when (this) {
    NavIndicator.PILL -> R.string.theme_nav_pill
    NavIndicator.UNDERLINE -> R.string.theme_nav_underline
    NavIndicator.BLOCK -> R.string.theme_nav_block
    NavIndicator.DOT -> R.string.theme_nav_dot
}

/**
 * Settings for the parametric `CUSTOM` parts. A part that isn't set to Custom shows a shortcut that switches it, so
 * this category doubles as the "build it from scratch" entry point.
 */
@Composable
internal fun CustomPartControls(draft: CustomLook, onChange: (CustomLook) -> Unit) {
    val c = draft.custom
    fun update(next: CustomRenderParams) = onChange(draft.copy(custom = next))

    CustomBlock(R.string.theme_part_cards, draft.card == CardStyle.CUSTOM, { onChange(draft.copy(card = CardStyle.CUSTOM)) }, StudioPart.CARDS) {
        val k = c.card
        EnumRow(R.string.theme_custom_fill, FillKind.entries, k.fill, { it.labelRes }) { update(c.copy(card = k.copy(fill = it))) }
        Stepper(R.string.theme_custom_opacity, k.fillOpacity, 0f, 1f, 0.1f, ValueUnit.PERCENT) { update(c.copy(card = k.copy(fillOpacity = it))) }
        Stepper(R.string.theme_custom_border_width, k.borderWidth, 0f, 6f, 0.5f, ValueUnit.DP) { update(c.copy(card = k.copy(borderWidth = it))) }
        if (k.borderWidth > 0f) EnumRow(R.string.theme_custom_border_color, LineRole.entries, k.border, { it.labelRes }) { update(c.copy(card = k.copy(border = it))) }
        Stepper(R.string.theme_custom_shadow, k.shadow, 0f, 12f, 1f, ValueUnit.DP) { update(c.copy(card = k.copy(shadow = it))) }
        BoolRow(R.string.theme_effect_glow, k.glow) { update(c.copy(card = k.copy(glow = it))) }
    }
    CustomBlock(R.string.theme_part_tiles, draft.tile == TileStyle.CUSTOM, { onChange(draft.copy(tile = TileStyle.CUSTOM)) }, StudioPart.TILES) {
        val k = c.tile
        EnumRow(R.string.theme_custom_fill, FillKind.entries, k.fill, { it.labelRes }) { update(c.copy(tile = k.copy(fill = it))) }
        EnumRow(R.string.theme_custom_selected_fill, FillKind.entries, k.selectedFill, { it.labelRes }) { update(c.copy(tile = k.copy(selectedFill = it))) }
        Stepper(R.string.theme_custom_border_width, k.borderWidth, 0f, 6f, 0.5f, ValueUnit.DP) { update(c.copy(tile = k.copy(borderWidth = it))) }
        if (k.borderWidth > 0f) EnumRow(R.string.theme_custom_border_color, LineRole.entries, k.border, { it.labelRes }) { update(c.copy(tile = k.copy(border = it))) }
        BoolRow(R.string.theme_custom_selected_glow, k.glow) { update(c.copy(tile = k.copy(glow = it))) }
    }
    CustomBlock(R.string.theme_part_gauges, draft.gauge == GaugeStyle.CUSTOM, { onChange(draft.copy(gauge = GaugeStyle.CUSTOM)) }, StudioPart.GAUGES) {
        val k = c.gauge
        EnumRow(R.string.theme_custom_gauge_shape, GaugeKind.entries, k.kind, { it.labelRes }) { update(c.copy(gauge = k.copy(kind = it))) }
        Stepper(R.string.theme_custom_thickness, k.thickness, 0.02f, 0.2f, 0.01f, ValueUnit.PERCENT) { update(c.copy(gauge = k.copy(thickness = it))) }
        BoolRow(R.string.theme_custom_round_caps, k.roundCaps) { update(c.copy(gauge = k.copy(roundCaps = it))) }
        EnumRow(R.string.theme_custom_gauge_colors, GaugeColorMode.entries, k.colors, { it.labelRes }) { update(c.copy(gauge = k.copy(colors = it))) }
        BoolRow(R.string.theme_effect_glow, k.glow) { update(c.copy(gauge = k.copy(glow = it))) }
    }
    CustomBlock(R.string.theme_part_sliders, draft.slider == SliderStyle.CUSTOM, { onChange(draft.copy(slider = SliderStyle.CUSTOM)) }, StudioPart.SLIDERS) {
        val k = c.slider
        EnumRow(R.string.theme_custom_track, FillKind.entries, k.track, { it.labelRes }) { update(c.copy(slider = k.copy(track = it))) }
        EnumRow(R.string.theme_custom_fill, FillKind.entries.filter { it != FillKind.NONE }, k.fill, { it.labelRes }) { update(c.copy(slider = k.copy(fill = it))) }
        Stepper(R.string.theme_custom_border_width, k.borderWidth, 0f, 6f, 0.5f, ValueUnit.DP) { update(c.copy(slider = k.copy(borderWidth = it))) }
        if (k.borderWidth > 0f) EnumRow(R.string.theme_custom_border_color, LineRole.entries, k.border, { it.labelRes }) { update(c.copy(slider = k.copy(border = it))) }
        BoolRow(R.string.theme_custom_thumb, k.thumb) { update(c.copy(slider = k.copy(thumb = it))) }
    }
    CustomBlock(R.string.theme_part_nav, draft.nav == NavStyle.CUSTOM, { onChange(draft.copy(nav = NavStyle.CUSTOM)) }, StudioPart.NAV) {
        EnumRow(R.string.theme_custom_nav_indicator, NavIndicator.entries, c.nav.indicator, { it.labelRes }) {
            update(c.copy(nav = c.nav.copy(indicator = it)))
        }
    }
}

/** One part's custom settings, or a one-press switch to Custom when the part uses a preset. */
@Composable
private fun CustomBlock(@StringRes titleRes: Int, active: Boolean, onUseCustom: () -> Unit, part: StudioPart, content: @Composable () -> Unit) {
    Card(Modifier.studioPart(part)) {
        CardTitle(stringResource(titleRes))
        if (active) content() else {
            CardDesc(stringResource(R.string.theme_custom_inactive))
            Spacer(Modifier.width(4.dp))
            Chip(stringResource(R.string.theme_custom_use), onClick = onUseCustom)
        }
    }
}

@Composable
private fun <E> EnumRow(@StringRes titleRes: Int, values: List<E>, current: E, label: (E) -> Int, onPick: (E) -> Unit) {
    ControlLabel(titleRes)
    ChipRow { values.forEach { v -> OptionChip(stringResource(label(v)), v == current) { onPick(v) } } }
}

@Composable
private fun BoolRow(@StringRes titleRes: Int, value: Boolean, onPick: (Boolean) -> Unit) {
    ControlLabel(titleRes)
    ChipRow {
        OptionChip(stringResource(R.string.theme_on), value) { onPick(true) }
        OptionChip(stringResource(R.string.theme_off), !value) { onPick(false) }
    }
}
