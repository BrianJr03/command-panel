package jr.brian.commandpannel.ui.theme

import androidx.annotation.FontRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * The overlay "looks" — the stock AYN surface plus the fork's additional themes. AUTO follows the
 * device-native stock look. Ported from the fork (feature 8); the per-look renderers live in
 * `ui/components/looks/`.
 */
enum class TccUiLook {
    AUTO,
    AURORA_GLASS, CARTRIDGE, TACTICAL_HUD,
    SOFT_CONSOLE, SOFT_CONSOLE_OLED, SEGMENT_LCD, BLOOM, NEON_ARCADE,
    TERMINAL, COCKPIT, STICKER_BOOK, INDUSTRIAL,
    JELLY, COMIC_PANEL, Y2K_CHROME, E_INK;

    val label: String get() = when (this) {
        AUTO -> "Auto (stock)"
        AURORA_GLASS -> "Aurora Glass"
        CARTRIDGE -> "Cartridge"
        TACTICAL_HUD -> "Tactical HUD"
        SOFT_CONSOLE -> "Soft Console"
        SOFT_CONSOLE_OLED -> "Soft Console OLED"
        SEGMENT_LCD -> "Segment LCD"
        BLOOM -> "Bloom"
        NEON_ARCADE -> "Neon Arcade"
        TERMINAL -> "Terminal"
        COCKPIT -> "Cockpit"
        STICKER_BOOK -> "Sticker Book"
        INDUSTRIAL -> "Industrial"
        JELLY -> "Jelly"
        COMIC_PANEL -> "Comic Panel"
        Y2K_CHROME -> "Y2K Chrome"
        E_INK -> "E-Ink"
    }

    companion object {
        fun fromKey(key: String?): TccUiLook = entries.firstOrNull { it.name == key } ?: AUTO
    }
}

/** How overlay panel cards are painted. Values after [CHAMFER_HUD] each belong to one look. */
enum class CardStyle {
    SOLID, GLASS, BRUTALIST, CHAMFER_HUD,
    SOFT_EXTRUDED, LCD_RECESSED, BLOOM_TONAL, NEON_TUBE,
    TERMINAL_FRAME, BEZEL, STICKER_NOTE, HAZARD_PLATE,
    JELLY, INK_PANEL, CHROME_RIM, HAIRLINE
}

enum class TileStyle {
    STOCK, PIXEL, GLASS_GLOW, BRUTALIST, HUD,
    SOFT_PRESS, LCD, BLOOM_BLOB, NEON,
    TERMINAL_ROW, SWITCH_LED, STICKER, PLATE,
    GEL, STARBURST, CHROME, INK_OUTLINE
}

enum class GaugeStyle {
    RING, GLOW_RING, SEGMENT_METER, SEGMENTED_ARC,
    INSET_RING, LCD_BAR, WAVY_ARC, NEON_RING,
    ASCII_BAR, NEEDLE_DIAL, STICKER_DONUT, RULER_BAR,
    CAPSULE, PIE, CHROME_RING, DITHER_BAR
}

enum class SliderStyle {
    STOCK, PIXEL, GLOW_GRADIENT, BRUTALIST, SEGMENTED,
    GROOVE, LCD_SEGMENTS, WAVY, NEON_TUBE,
    ASCII, FADER, STICKER, RULER,
    GEL, INK, CHROME, DITHER
}

enum class NavStyle {
    TILE, GLASS_PILL, INK_BLOCK, BRACKETS,
    PRESSED, LCD_INVERT, EXPRESSIVE_PILL, NEON_UNDERLINE,
    PROMPT_INVERT, LED_SWITCH, STICKER, HAZARD_UNDERLINE,
    GEL_PILL, STARBURST, FOIL_PILL, UNDERLINE_ITALIC
}

enum class BackdropStyle { SOLID, AURORA, PAPER_DOTS, HUD_GRID, LCD_PIXEL_GRID, SYNTH_GRID, HALFTONE }

enum class HeaderStyle { NONE, INK_STRIP, INDEXED_MONO, TITLED_FRAME }

/**
 * Rendering traits that distinguish looks beyond pure geometry. The stock look uses the defaults;
 * every non-default value opts a look into a dedicated renderer.
 */
@Immutable
data class LookTraits(
    val card: CardStyle = CardStyle.SOLID,
    val tile: TileStyle = TileStyle.STOCK,
    val gauge: GaugeStyle = GaugeStyle.RING,
    val slider: SliderStyle = SliderStyle.STOCK,
    val nav: NavStyle = NavStyle.TILE,
    val backdrop: BackdropStyle = BackdropStyle.SOLID,
    val header: HeaderStyle = HeaderStyle.NONE,
    val labelFontFamily: FontFamily? = null,
    val displayFontFamily: FontFamily? = null,
    /** Font resource for native `Paint` text (FPS readout); `null` keeps the look default. */
    @FontRes val canvasFontRes: Int? = null,
    val uppercaseLabels: Boolean = false,
    val lowercaseLabels: Boolean = false,
    val snakeCaseLabels: Boolean = false,
    val labelLetterSpacing: TextUnit = TextUnit.Unspecified,
    val pixelIcons: Boolean = false,
    val outlineWidth: Dp = 0.dp,
    val shadowOffset: Dp = 0.dp,
    val glow: Boolean = false,
    val scanlines: Boolean = false,
    /** Segment LCD: faint `8` "ghost" segments behind seven-segment numerals. */
    val ghostSegments: Boolean = false,
    /** `false` (E-Ink) snaps transitions instead of animating them. */
    val animate: Boolean = true,
    /** Soft Console: backdrop paints the soft surface so extruded cards stay visible on black. */
    val liftedBackdrop: Boolean = false,
    /** Soft Console OLED: backdrop is pure black on dark palettes; cards keep their fill. */
    val oledBackdrop: Boolean = false,
    /** Multiplier on pattern backdrop alpha (paper dots for Sticker Book). */
    val backdropPatternAlpha: Float = 1f,
)
