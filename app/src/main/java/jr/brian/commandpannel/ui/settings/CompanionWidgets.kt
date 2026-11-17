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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gamepad-focusable building blocks shared by the companion screens (settings sections, Themes, the theme editor).
 * All of them route through the companion theming hooks, so they restyle with the active look.
 */

// ---- gamepad focus plumbing ----

@Composable
internal fun focusState(): MutableState<Boolean> = remember { mutableStateOf(false) }

/** Makes a node focusable, reports focus into [focused], and fires [onActivate] on A / center / enter. */
internal fun Modifier.focusControl(focused: MutableState<Boolean>, onActivate: () -> Unit): Modifier =
    onFocusChanged { focused.value = it.isFocused }
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown &&
                (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.ButtonA)
            ) { onActivate(); true } else false
        }
        .focusable()

// ---- theming hooks ----
// With "Companion follows overlay look" off, CompanionTheme provides the stock spec and the default palette, so
// every hook below resolves to the companion's original style.

/** The active companion colors. */
internal val p: CompanionPalette
    @Composable get() = LocalCompanionPalette.current

/** Corner shape for rows, buttons and chips: the look's control shape when themed, else [default] radius. */
@Composable
internal fun controlShape(default: Dp): Shape =
    if (LocalCompanionThemed.current) LocalTccLookSpec.current.slider.controlShape else RoundedCornerShape(default)

@Composable
internal fun cardShape(): Shape =
    if (LocalCompanionThemed.current) LocalTccLookSpec.current.panelCardShape else RoundedCornerShape(18.dp)

/** Scale-on-focus animation; snaps for looks that opt out of motion (E-Ink). */
@Composable
internal fun focusScale(focused: Boolean, to: Float, label: String): State<Float> =
    if (LocalTccLookSpec.current.traits.animate) animateFloatAsState(if (focused) to else 1f, label = label)
    else animateFloatAsState(if (focused) to else 1f, snap(), label = label)

@Composable
internal fun focusColor(target: Color, label: String): State<Color> =
    if (LocalTccLookSpec.current.traits.animate) animateColorAsState(target, label = label)
    else animateColorAsState(target, snap(), label = label)

/**
 * Card surface: the companion's bordered panel, or the active look's card renderer (glass, brutalist shadow, HUD
 * chamfer, neon tube, …) when themed. The accent focus ring is kept on every look so gamepad focus stays obvious.
 */
@Composable
internal fun Modifier.companionCard(focused: Boolean = false, focusable: Boolean = false): Modifier {
    val shape = cardShape()
    if (!LocalCompanionThemed.current) {
        val bg by focusColor(if (focused) p.surfaceHigh else p.surface, "cardBg")
        return clip(shape).background(bg)
            .border(if (focused) 2.dp else 1.dp, if (focused) p.accent else p.border, shape)
    }
    val card = tccCard(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors(), focused = focused && focusable)
    return if (focused) card.border(2.dp, p.accent, shape) else card
}

// ---- shared building blocks ----

/** A plain visual container (not a focus target); its rows are the focus targets. */
@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().companionCard().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
internal fun SectionHeader(text: String) {
    Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(3.dp, 13.dp).clip(CircleShape).background(p.accent))
        Spacer(Modifier.width(8.dp))
        Text(lookLabel(text).uppercase(), color = p.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, style = tccTextStyle())
    }
}

@Composable
internal fun CardTitle(text: String) =
    Text(lookLabel(text), color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = tccTextStyle())

@Composable
internal fun CardDesc(text: String) = Text(text, color = p.textSecondary, fontSize = 13.sp)

@Composable
internal fun InfoLine(text: String, tone: Color = p.textSecondary) = Text(text, color = tone, fontSize = 12.sp)

/** A focusable surface that brightens + scales when focused and fires [onActivate] on A/center. */
@Composable
internal fun FocusCard(onActivate: () -> Unit, content: @Composable () -> Unit) {
    val f = focusState()
    val scale by focusScale(f.value, 1.015f, "cardScale")
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusControl(f, onActivate)
            .companionCard(focused = f.value, focusable = true)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
internal fun SwitchCard(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(onActivate = { onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardTitle(title)
                CardDesc(description)
            }
            Spacer(Modifier.width(16.dp))
            AccentSwitch(checked, onChange)
        }
    }
}

@Composable
internal fun AccentSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked = checked,
    onCheckedChange = onChange,
    colors = SwitchDefaults.colors(
        checkedTrackColor = p.accent,
        checkedThumbColor = p.onAccent,
        uncheckedTrackColor = p.surfaceHigh,
        uncheckedBorderColor = p.border,
        uncheckedThumbColor = p.textTertiary,
    ),
)

/** A focusable selectable row (brightens + scales when focused). */
@Composable
internal fun FocusRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val f = focusState()
    val shape = controlShape(12.dp)
    val scale by focusScale(f.value, 1.015f, "rowScale")
    val bg by focusColor(
        when { f.value -> p.surfaceHigh; selected -> p.accentSoft; else -> Color.Transparent },
        "rowBg",
    )
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .then(if (f.value) Modifier.border(1.5.dp, p.accent, shape) else Modifier)
            .focusControl(f, onClick)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusRow(selected, onClick) {
        Text(
            label, color = if (selected) p.textPrimary else p.textSecondary, fontSize = 15.sp,
            style = tccTextStyle(), modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.textTertiary),
        )
    }
}

/** A focusable action button. */
@Composable
internal fun AccentButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val f = focusState()
    val shape = controlShape(12.dp)
    val scale by focusScale(f.value, 1.05f, "btnScale")
    val bg = when { !enabled -> p.surfaceHigh; f.value -> p.accentFocused; else -> p.accent }
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .then(if (f.value) Modifier.border(1.5.dp, p.onAccent, shape) else Modifier)
            .then(if (enabled) Modifier.focusControl(f, onClick).noRippleClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            lookLabel(text), color = if (enabled) p.onAccent else p.textTertiary, fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold, style = tccTextStyle(),
        )
    }
}

/** A focusable compact pill (replay window, reorder arrows). */
@Composable
internal fun Chip(text: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val f = focusState()
    val shape = controlShape(10.dp)
    val scale by focusScale(f.value, 1.08f, "chipScale")
    val bg = when { !enabled -> p.surface; f.value -> p.accentSoft; selected -> p.accentSoft; else -> p.surfaceHigh }
    val fg = when { !enabled -> p.textTertiary; selected || f.value -> p.accent; else -> p.textSecondary }
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape).background(bg)
            .then(if (selected || f.value) Modifier.border(1.5.dp, p.accent, shape) else Modifier)
            .then(if (enabled) Modifier.focusControl(f, onClick).noRippleClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = tccTextStyle()) }
}

/** Gap kept between a focused card (border and focus scale included) and the top/bottom edge of the scrolling pane. */
internal val FOCUS_SCROLL_MARGIN = 28.dp

/**
 * [BringIntoViewSpec] that reserves [insetPx] at both edges of the viewport. The default spec counts anything inside
 * `[0, containerSize]` as visible and ignores content padding, so D-pad focus scrolling left the focused card's border
 * flush against the top/bottom of the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class PaddedBringIntoViewSpec(private val insetPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = offset - insetPx
        val trailingEdge = offset + size - (containerSize - insetPx)
        return when {
            leadingEdge >= 0 && trailingEdge <= 0 -> 0f
            // Taller than the padded window: leave it where it is rather than jitter between edges.
            leadingEdge < 0 && trailingEdge > 0 -> 0f
            abs(leadingEdge) < abs(trailingEdge) -> leadingEdge
            else -> trailingEdge
        }
    }
}

/**
 * Provides a [PaddedBringIntoViewSpec] to every scrollable inside [content] (vertical panes and horizontal chip rows),
 * so D-pad focus never parks the first/last item flush against the edge where its focus ring and scale get clipped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PaddedBringIntoView(margin: Dp = FOCUS_SCROLL_MARGIN, content: @Composable () -> Unit) {
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    val spec = remember(marginPx) { PaddedBringIntoViewSpec(marginPx) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** Built-in looks in alphabetical order of their display names (locale-aware), for every look list in the companion. */
@Composable
internal fun sortedLooks(): List<jr.brian.commandpannel.ui.theme.TccUiLook> {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    return remember(res.configuration) {
        val collator = java.text.Collator.getInstance(res.configuration.locales[0])
        jr.brian.commandpannel.ui.theme.TccUiLook.entries.sortedWith(compareBy(collator) { res.getString(it.labelRes) })
    }
}

/** Sorts option pairs by their label resource's text (locale-aware). */
@Composable
internal fun <T> sortedByLabel(options: List<Pair<T, Int>>): List<Pair<T, Int>> {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    return remember(options, res.configuration) {
        val collator = java.text.Collator.getInstance(res.configuration.locales[0])
        options.sortedWith(compareBy(collator) { res.getString(it.second) })
    }
}

/**
 * A single-line text field that works like a button on the gamepad. D-pad focus lands on the field without entering
 * it, so up/down keep moving through the screen. A / center / Enter (or a tap) starts editing and opens the keyboard.
 * While editing, D-pad up/down or B / Back stop editing and hand focus back to the field instead of leaving the
 * screen. A raw text field would trap the D-pad (it moves the cursor) and let B close the companion.
 */
@Composable
internal fun GamepadTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    verticalPadding: Dp = 10.dp,
) {
    var editing by remember { mutableStateOf(false) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    val outer = focusState()
    val outerRequester = remember { FocusRequester() }
    val fieldRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val shape = controlShape(10.dp)
    val active = outer.value || editing
    fun stopEditing() {
        editing = false
        keyboard?.hide()
        runCatching { outerRequester.requestFocus() }
    }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldRequester.requestFocus() }
            keyboard?.show()
        }
    }
    // Newer Android routes Back through the back dispatcher rather than key events; catch it there too.
    androidx.activity.compose.BackHandler(enabled = editing) { stopEditing() }
    Box(
        modifier.clip(shape)
            .background(if (active) p.surfaceHigh else p.surfaceHigh.copy(alpha = 0.5f))
            .border(if (active) 1.5.dp else 1.dp, if (active) p.accent else p.border, shape)
            .focusRequester(outerRequester)
            .focusControl(outer) { editing = true }
            .padding(horizontal = 12.dp, vertical = verticalPadding),
    ) {
        if (value.isEmpty() && placeholder != null) Text(placeholder, color = p.textTertiary, style = textStyle)
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(p.accent),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(fieldRequester)
                // Only reachable while editing, so D-pad navigation never lands inside the text.
                .focusProperties { canFocus = editing }
                .onFocusChanged {
                    // Touching elsewhere ends editing too.
                    if (fieldHadFocus && !it.isFocused) editing = false
                    fieldHadFocus = it.isFocused
                }
                .onPreviewKeyEvent { e ->
                    when (e.key) {
                        // Consume both down and up so Back never reaches the activity.
                        Key.DirectionUp, Key.DirectionDown, Key.Back, Key.ButtonB, Key.Escape -> {
                            if (e.type == KeyEventType.KeyUp) stopEditing()
                            true
                        }
                        else -> false
                    }
                },
        )
        // While not editing, a tap anywhere on the field starts editing (the disabled text field would swallow it).
        if (!editing) Box(Modifier.matchParentSize().noRippleClickable { editing = true })
    }
}

/**
 * Scrolls this whole element into view (with the padded margins) whenever focus lands anywhere inside it. For
 * multi-control cards, so moving onto a card's buttons shows the entire card, not just the row of buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { if (it.hasFocus) scope.launch { requester.bringIntoView() } }
}
