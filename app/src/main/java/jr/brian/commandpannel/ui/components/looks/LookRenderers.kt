package jr.brian.commandpannel.ui.components.looks

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import jr.brian.commandpannel.ui.components.BuiltInCardRenderer
import jr.brian.commandpannel.ui.components.BuiltInGaugeRenderer
import jr.brian.commandpannel.ui.components.BuiltInNavRenderer
import jr.brian.commandpannel.ui.components.BuiltInSliderRenderer
import jr.brian.commandpannel.ui.components.BuiltInTileRenderer
import jr.brian.commandpannel.ui.components.TccTileVariant
import jr.brian.commandpannel.ui.theme.CardStyle
import jr.brian.commandpannel.ui.theme.GaugeStyle
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.NavStyle
import jr.brian.commandpannel.ui.theme.SliderStyle
import jr.brian.commandpannel.ui.theme.TccColorPalette
import jr.brian.commandpannel.ui.theme.TccLookColors
import jr.brian.commandpannel.ui.theme.TccLookSpec
import jr.brian.commandpannel.ui.theme.TileStyle
import jr.brian.commandpannel.ui.theme.tccLookColors

/*
 * Per-look renderer contracts. Every trait enum value resolves (one exhaustive `when` per enum,
 * below) to a renderer object. The original five looks use the `BuiltIn*Renderer`s next to the
 * widgets; each newer look owns its renderers in its own `*Renderers.kt` file in this package, so
 * looks can be implemented in parallel without touching the shared widgets.
 */

/** Spec, palette and look colors bundled for renderer calls. */
@Immutable
data class LookPaint(
    val spec: TccLookSpec,
    val colors: TccColorPalette,
    val lookColors: TccLookColors
)

/** [LookPaint] for the active look, for renderers that run inside composition. */
@Composable
fun currentLookPaint(): LookPaint = LookPaint(LocalTccLookSpec.current, LocalTccColors.current, tccLookColors())

// ---------------------------------------------------------------------------------------------
// Cards, dialogs and the FPS readout (keyed by CardStyle)
// ---------------------------------------------------------------------------------------------

/** Paints the big native-`Paint` FPS number. The default draws it plainly. */
interface ReadoutPainter {
    /** Draws [number] with [paint] (already sized and colored) at [x] / [baseline]. */
    fun drawNumber(canvas: Canvas, number: String, x: Float, baseline: Float, paint: Paint) {
        canvas.drawText(number, x, baseline, paint)
    }

    /** Draws after the unit text ends at [endX] (Terminal cursor). */
    fun drawAfter(canvas: Canvas, endX: Float, baseline: Float, paint: Paint) {}
}

object PlainReadoutPainter : ReadoutPainter

interface LookCardRenderer {
    /**
     * Overlay panel card; [fill] overrides the fill, [header] is a `[01] LABEL` caption and
     * [title] the card's short title (`fps`, `temp`, ...), also usable as a stable per-card seed.
     */
    fun card(
        base: Modifier,
        p: LookPaint,
        focused: Boolean,
        fill: Color?,
        shape: Shape,
        header: String?,
        title: String?
    ): Modifier

    /** Dialog surface (more opaque than cards so content behind never bleeds through). */
    fun dialog(base: Modifier, p: LookPaint): Modifier

    /** Text / icon color on cards; `null` keeps each widget's palette default. */
    fun content(p: LookPaint): Color? = null

    /** FPS mode badge background; `null` keeps the built-in badge chain. */
    fun fpsBadge(p: LookPaint, highRate: Boolean): Modifier? = null

    /** Transform applied to the FPS badge before it is clipped (Sticker Book's tilt). */
    fun fpsBadgeTransform(p: LookPaint): Modifier = Modifier

    /** FPS mode badge text style (font face, italics) built from the label style [base]. */
    fun fpsBadgeStyle(p: LookPaint, base: TextStyle): TextStyle = base

    /** FPS mode badge text color; `null` keeps the built-in color chain. */
    fun fpsBadgeText(p: LookPaint, highRate: Boolean): Color? = null

    /** Big FPS number decoration (glow, ghost segments, cursor, chrome shader, ink stroke). */
    @Composable
    fun rememberReadoutPainter(p: LookPaint): ReadoutPainter = PlainReadoutPainter

    /** Compose numerals on cards (TEMP / FAN values); looks can add ghosts, glow or brushes. */
    @Composable
    fun ValueText(
        text: String,
        color: Color,
        fontSize: TextUnit,
        style: TextStyle,
        fontWeight: FontWeight,
        modifier: Modifier
    ) {
        Text(text = text, color = color, fontSize = fontSize, style = style, fontWeight = fontWeight, maxLines = 1, modifier = modifier)
    }
}

fun CardStyle.renderer(): LookCardRenderer = when (this) {
    CardStyle.SOLID -> BuiltInCardRenderer.Solid
    CardStyle.GLASS -> BuiltInCardRenderer.Glass
    CardStyle.BRUTALIST -> BuiltInCardRenderer.Brutalist
    CardStyle.CHAMFER_HUD -> BuiltInCardRenderer.ChamferHud
    CardStyle.SOFT_EXTRUDED -> SoftConsoleCard
    CardStyle.LCD_RECESSED -> SegmentLcdCard
    CardStyle.BLOOM_TONAL -> BloomCard
    CardStyle.NEON_TUBE -> NeonArcadeCard
    CardStyle.TERMINAL_FRAME -> TerminalCard
    CardStyle.BEZEL -> CockpitCard
    CardStyle.STICKER_NOTE -> StickerBookCard
    CardStyle.HAZARD_PLATE -> IndustrialCard
    CardStyle.JELLY -> JellyCard
    CardStyle.INK_PANEL -> ComicPanelCard
    CardStyle.CHROME_RIM -> Y2kChromeCard
    CardStyle.HAIRLINE -> EInkCard
    CardStyle.CUSTOM -> CustomCard
}

// ---------------------------------------------------------------------------------------------
// Tiles (keyed by TileStyle)
// ---------------------------------------------------------------------------------------------

/** Everything a tile renderer needs to replace the whole icon + label column. */
@Immutable
class TileContentState(
    val paint: LookPaint,
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val iconBitmap: ImageBitmap?,
    val selected: Boolean,
    val index: Int,
    val variant: TccTileVariant,
    val columnWidth: Dp,
    val tileBoxSize: Dp,
    val iconBoxSize: Dp,
    val labelStyle: TextStyle
)

interface LookTileRenderer {
    /** Tile chrome around the icon box. */
    fun tile(base: Modifier, p: LookPaint, selected: Boolean, index: Int): Modifier

    fun iconTint(p: LookPaint, selected: Boolean, index: Int): Color

    /** Tile label color; `null` keeps primary (selected) / secondary. */
    fun labelColor(p: LookPaint, selected: Boolean): Color? = null

    /**
     * Replaces the whole icon + label column of non-nav tiles while keeping its grid cell
     * (Terminal `[x] label` text cells). `null` keeps the standard layout.
     */
    val content: (@Composable (TileContentState) -> Unit)? get() = null
}

fun TileStyle.renderer(): LookTileRenderer = when (this) {
    TileStyle.STOCK -> BuiltInTileRenderer.Stock
    TileStyle.PIXEL -> BuiltInTileRenderer.Pixel
    TileStyle.GLASS_GLOW -> BuiltInTileRenderer.GlassGlow
    TileStyle.BRUTALIST -> BuiltInTileRenderer.Brutalist
    TileStyle.HUD -> BuiltInTileRenderer.Hud
    TileStyle.SOFT_PRESS -> SoftConsoleTile
    TileStyle.LCD -> SegmentLcdTile
    TileStyle.BLOOM_BLOB -> BloomTile
    TileStyle.NEON -> NeonArcadeTile
    TileStyle.TERMINAL_ROW -> TerminalTile
    TileStyle.SWITCH_LED -> CockpitTile
    TileStyle.STICKER -> StickerBookTile
    TileStyle.PLATE -> IndustrialTile
    TileStyle.GEL -> JellyTile
    TileStyle.STARBURST -> ComicPanelTile
    TileStyle.CHROME -> Y2kChromeTile
    TileStyle.INK_OUTLINE -> EInkTile
    TileStyle.CUSTOM -> CustomTile
}

// ---------------------------------------------------------------------------------------------
// Telemetry gauges (keyed by GaugeStyle)
// ---------------------------------------------------------------------------------------------

/**
 * One gauge reading. [fraction] is the animated value (`-1..1` when [bidirectional]); read it
 * inside draw scopes so 120 Hz animation only redraws instead of recomposing.
 */
class GaugeReading(
    val title: String,
    val valueText: String,
    val unitText: String,
    val color: Color,
    val bidirectional: Boolean,
    val fraction: () -> Float,
    /** Target fraction [fraction] is animating toward (for looks that run their own motion). */
    val target: Float = 0f
)

/** Per-gauge colors for the CPU / GPU / PWR / RAM telemetry card. */
@Immutable
data class GaugeRingColors(val cpu: Color, val gpu: Color, val pwr: Color, val ram: Color)

interface LookGaugeRenderer {
    fun ringColors(p: LookPaint): GaugeRingColors

    @Composable
    fun Gauge(reading: GaugeReading, modifier: Modifier)
}

fun GaugeStyle.renderer(): LookGaugeRenderer = when (this) {
    GaugeStyle.RING -> BuiltInGaugeRenderer.Ring
    GaugeStyle.GLOW_RING -> BuiltInGaugeRenderer.GlowRing
    GaugeStyle.SEGMENT_METER -> BuiltInGaugeRenderer.SegmentMeter
    GaugeStyle.SEGMENTED_ARC -> BuiltInGaugeRenderer.SegmentedArc
    GaugeStyle.INSET_RING -> SoftConsoleGauge
    GaugeStyle.LCD_BAR -> SegmentLcdGauge
    GaugeStyle.WAVY_ARC -> BloomGauge
    GaugeStyle.NEON_RING -> NeonArcadeGauge
    GaugeStyle.ASCII_BAR -> TerminalGauge
    GaugeStyle.NEEDLE_DIAL -> CockpitGauge
    GaugeStyle.STICKER_DONUT -> StickerBookGauge
    GaugeStyle.RULER_BAR -> IndustrialGauge
    GaugeStyle.CAPSULE -> JellyGauge
    GaugeStyle.PIE -> ComicPanelGauge
    GaugeStyle.CHROME_RING -> Y2kChromeGauge
    GaugeStyle.DITHER_BAR -> EInkGauge
    GaugeStyle.CUSTOM -> CustomGauge
}

// ---------------------------------------------------------------------------------------------
// Sliders (keyed by SliderStyle)
// ---------------------------------------------------------------------------------------------

@Immutable
data class SliderPaint(
    val look: LookPaint,
    /** `0..1` fill fraction. */
    val fraction: Float,
    /** Fill dimmed (auto-brightness on and not dragging). */
    val dimmed: Boolean,
    /** Palette fill color, already dimmed when [dimmed]. */
    val fillColor: Color,
    val displayProgress: Int,
    /** The fill reaches under the leading icon, so the icon should contrast the fill. */
    val overFill: Boolean
)

interface LookSliderRenderer {
    /** Background / border of the clipped track box. */
    fun track(p: SliderPaint): Modifier

    /** Fill drawn inside the track box, which is [trackWidth] wide. */
    @Composable
    fun Fill(p: SliderPaint, trackWidth: Dp)

    fun iconTint(p: SliderPaint): Color

    fun labelColor(p: SliderPaint): Color = p.look.colors.primary

    /** `false` hides the leading slider icon (Terminal ASCII bar). */
    val showIcon: Boolean get() = true
}

fun SliderStyle.renderer(): LookSliderRenderer = when (this) {
    SliderStyle.STOCK -> BuiltInSliderRenderer.Stock
    SliderStyle.PIXEL -> BuiltInSliderRenderer.Pixel
    SliderStyle.GLOW_GRADIENT -> BuiltInSliderRenderer.GlowGradient
    SliderStyle.BRUTALIST -> BuiltInSliderRenderer.Brutalist
    SliderStyle.SEGMENTED -> BuiltInSliderRenderer.Segmented
    SliderStyle.GROOVE -> SoftConsoleSlider
    SliderStyle.LCD_SEGMENTS -> SegmentLcdSlider
    SliderStyle.WAVY -> BloomSlider
    SliderStyle.NEON_TUBE -> NeonArcadeSlider
    SliderStyle.ASCII -> TerminalSlider
    SliderStyle.FADER -> CockpitSlider
    SliderStyle.STICKER -> StickerBookSlider
    SliderStyle.RULER -> IndustrialSlider
    SliderStyle.GEL -> JellySlider
    SliderStyle.INK -> ComicPanelSlider
    SliderStyle.CHROME -> Y2kChromeSlider
    SliderStyle.DITHER -> EInkSlider
    SliderStyle.CUSTOM -> CustomSlider
}

// ---------------------------------------------------------------------------------------------
// Nav bar (keyed by NavStyle)
// ---------------------------------------------------------------------------------------------

interface LookNavRenderer {
    /**
     * `true` draws the selection around the whole icon + label column (tile chrome is bare);
     * `false` ([NavStyle.TILE]) keeps the per-tile selection and ignores the members below.
     */
    val columnHighlight: Boolean

    val highlightShape: Shape

    /** Selection decoration around the item column (only applied while selected). */
    fun highlight(p: LookPaint): Modifier

    fun iconTint(p: LookPaint, selected: Boolean): Color

    fun textColor(p: LookPaint, selected: Boolean): Color = iconTint(p, selected)

    /** Label text after look casing (`> HOME` for HUD brackets). */
    fun label(text: String, selected: Boolean): String = text

    /** Nav label text style built from the label style [base] (E-Ink italic underline). */
    fun labelStyle(base: TextStyle, selected: Boolean): TextStyle = base

    /** `false` hides nav icons (Terminal prompt nav). */
    val showIcon: Boolean get() = true

    /** Nav label size multiplier; label-only navs (Terminal) scale up so the text fills the space an icon would. */
    val labelScale: Float get() = 1f

    /** Nav bar card fill; `null` keeps the card default. */
    fun barFill(p: LookPaint): Color? = null
}

fun NavStyle.renderer(): LookNavRenderer = when (this) {
    NavStyle.TILE -> BuiltInNavRenderer.Tile
    NavStyle.GLASS_PILL -> BuiltInNavRenderer.GlassPill
    NavStyle.INK_BLOCK -> BuiltInNavRenderer.InkBlock
    NavStyle.BRACKETS -> BuiltInNavRenderer.Brackets
    NavStyle.PRESSED -> SoftConsoleNav
    NavStyle.LCD_INVERT -> SegmentLcdNav
    NavStyle.EXPRESSIVE_PILL -> BloomNav
    NavStyle.NEON_UNDERLINE -> NeonArcadeNav
    NavStyle.PROMPT_INVERT -> TerminalNav
    NavStyle.LED_SWITCH -> CockpitNav
    NavStyle.STICKER -> StickerBookNav
    NavStyle.HAZARD_UNDERLINE -> IndustrialNav
    NavStyle.GEL_PILL -> JellyNav
    NavStyle.STARBURST -> ComicPanelNav
    NavStyle.FOIL_PILL -> Y2kChromeNav
    NavStyle.UNDERLINE_ITALIC -> EInkNav
    NavStyle.CUSTOM -> CustomNav
}
