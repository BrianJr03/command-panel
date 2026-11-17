package jr.brian.commandpannel.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.TextUnit
import jr.brian.commandpannel.ui.components.ChamferShape
import jr.brian.commandpannel.ui.components.SquircleShape

/**
 * Command Panel's native look spec. The fork built its looks on an elaborate upstream spec; here we
 * keep only the fields Command Panel's overlay actually consumes — the panel/tile/slider/dialog
 * shapes, the ring-gauge ratios, the FPS-badge shape, and the [LookTraits] that pick each look's
 * renderers. Every look is [StockLookSpec] `.copy(...)` with its own overrides (ported from the
 * fork, feature 8).
 */
@Immutable
data class TileLookSpec(
    val buttonShape: Shape = CircleShape,
    val qsLabelFontSize: TextUnit = aynSp(10f),
    /** Nav-bar icon-box size and the gap above it, used by look nav renderers to place decorations. */
    val navTileSize: androidx.compose.ui.unit.Dp = aynDp(44f),
    val compactTopGap: androidx.compose.ui.unit.Dp = aynDp(4f),
)

@Immutable
data class SliderLookSpec(
    val controlShape: Shape = RoundedCornerShape(12),
    val trackShape: Shape = RoundedCornerShape(percent = 50),
)

@Immutable
data class DialogLookSpec(
    val dialogShape: Shape = RoundedCornerShape(16),
)

@Immutable
data class SettingsTabLookSpec(
    val ringDiameterRatio: Float = 0.86f,
    val ringTitleScale: Float = 0.13f,
    /** Height fraction of the ring's top band that centers the title; `null` keeps the default. */
    val ringTitleBandFraction: Float? = null,
    val ringValueScale: Float = 0.30f,
    val ringUnitScale: Float = 0.13f,
    /** Multiplier on the TEMP/FAN info-card value size (22sp base); < 1 for wide display fonts. */
    val infoValueScale: Float = 1f,
    val fpsBadgeShape: Shape = RoundedCornerShape(50),
)

@Immutable
data class TccLookSpec(
    val look: TccUiLook,
    val panelCardShape: Shape = RoundedCornerShape(16),
    val tile: TileLookSpec = TileLookSpec(),
    val slider: SliderLookSpec = SliderLookSpec(),
    val settingsTab: SettingsTabLookSpec = SettingsTabLookSpec(),
    val dialog: DialogLookSpec = DialogLookSpec(),
    val traits: LookTraits = LookTraits(),
    /** Settings for any part whose style is `CUSTOM` (Theme Studio looks only); `null` on built-in looks. */
    val custom: CustomRenderParams? = null,
)

private val PillShape = RoundedCornerShape(percent = 50)

/** The stock AYN look — Command Panel's default surface; all other looks copy from it. */
val StockLookSpec = TccLookSpec(
    look = TccUiLook.AUTO,
    slider = SliderLookSpec(controlShape = RoundedCornerShape(aynDp(15f)), trackShape = RoundedCornerShape(aynDp(15f))),
    settingsTab = SettingsTabLookSpec(fpsBadgeShape = RoundedCornerShape(aynDp(17f))),
)

val AuroraGlassLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.AURORA_GLASS,
    panelCardShape = RoundedCornerShape(aynDp(23f)),
    tile = StockLookSpec.tile.copy(buttonShape = SquircleShape(0.30f)),
    slider = StockLookSpec.slider.copy(controlShape = RoundedCornerShape(aynDp(15f)), trackShape = PillShape),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(22f))),
    traits = LookTraits(
        card = CardStyle.GLASS, tile = TileStyle.GLASS_GLOW, gauge = GaugeStyle.GLOW_RING,
        slider = SliderStyle.GLOW_GRADIENT, nav = NavStyle.GLASS_PILL, backdrop = BackdropStyle.AURORA, glow = true,
    ),
)

val CartridgeLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.CARTRIDGE,
    panelCardShape = RectangleShape,
    tile = StockLookSpec.tile.copy(buttonShape = RectangleShape, qsLabelFontSize = aynSp(12f)),
    slider = SliderLookSpec(controlShape = RectangleShape, trackShape = RectangleShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RectangleShape),
    dialog = DialogLookSpec(dialogShape = RectangleShape),
    traits = LookTraits(
        card = CardStyle.BRUTALIST, tile = TileStyle.BRUTALIST, gauge = GaugeStyle.SEGMENT_METER,
        slider = SliderStyle.BRUTALIST, nav = NavStyle.INK_BLOCK, backdrop = BackdropStyle.PAPER_DOTS,
        header = HeaderStyle.INK_STRIP, labelFontFamily = TccCondensedFontFamily,
        displayFontFamily = TccCondensedDisplayFontFamily, canvasFontRes = jr.brian.commandpannel.R.font.tcc_condensed_extrabold,
        uppercaseLabels = true, outlineWidth = androidx.compose.ui.unit.Dp(3f), shadowOffset = androidx.compose.ui.unit.Dp(6f),
    ),
)

val TacticalHudLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.TACTICAL_HUD,
    panelCardShape = ChamferShape(aynDp(9f)),
    tile = StockLookSpec.tile.copy(buttonShape = ChamferShape(aynDp(6f)), qsLabelFontSize = aynSp(9.5f)),
    slider = SliderLookSpec(controlShape = RectangleShape, trackShape = RectangleShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RectangleShape),
    dialog = DialogLookSpec(dialogShape = ChamferShape(aynDp(9f))),
    traits = LookTraits(
        card = CardStyle.CHAMFER_HUD, tile = TileStyle.HUD, gauge = GaugeStyle.SEGMENTED_ARC,
        slider = SliderStyle.SEGMENTED, nav = NavStyle.BRACKETS, backdrop = BackdropStyle.HUD_GRID,
        header = HeaderStyle.INDEXED_MONO, labelFontFamily = TccMonoFontFamily,
        displayFontFamily = TccCondensedDisplayFontFamily, canvasFontRes = jr.brian.commandpannel.R.font.tcc_condensed_extrabold,
        uppercaseLabels = true, labelLetterSpacing = androidx.compose.ui.unit.TextUnit(0.04f, androidx.compose.ui.unit.TextUnitType.Em),
        glow = true, scanlines = true,
    ),
)

val SoftConsoleLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.SOFT_CONSOLE,
    panelCardShape = RoundedCornerShape(aynDp(20f)),
    tile = StockLookSpec.tile.copy(buttonShape = CircleShape),
    slider = StockLookSpec.slider.copy(trackShape = PillShape),
    settingsTab = StockLookSpec.settingsTab.copy(
        fpsBadgeShape = PillShape, ringTitleBandFraction = 0.58f, ringTitleScale = 0.11f,
    ),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(20f))),
    traits = LookTraits(
        card = CardStyle.SOFT_EXTRUDED, tile = TileStyle.SOFT_PRESS, gauge = GaugeStyle.INSET_RING,
        slider = SliderStyle.GROOVE, nav = NavStyle.PRESSED, backdrop = BackdropStyle.SOLID, liftedBackdrop = true,
    ),
)

val SoftConsoleOledLookSpec: TccLookSpec = SoftConsoleLookSpec.copy(
    look = TccUiLook.SOFT_CONSOLE_OLED,
    traits = SoftConsoleLookSpec.traits.copy(liftedBackdrop = false, oledBackdrop = true),
)

val SegmentLcdLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.SEGMENT_LCD,
    panelCardShape = RoundedCornerShape(aynDp(3f)),
    tile = StockLookSpec.tile.copy(buttonShape = RoundedCornerShape(aynDp(2f)), qsLabelFontSize = aynSp(9.5f)),
    slider = SliderLookSpec(controlShape = RoundedCornerShape(aynDp(3f)), trackShape = RoundedCornerShape(aynDp(2f))),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RoundedCornerShape(aynDp(2f))),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(4f))),
    traits = LookTraits(
        card = CardStyle.LCD_RECESSED, tile = TileStyle.LCD, gauge = GaugeStyle.LCD_BAR,
        slider = SliderStyle.LCD_SEGMENTS, nav = NavStyle.LCD_INVERT, backdrop = BackdropStyle.LCD_PIXEL_GRID,
        labelFontFamily = TccMonoFontFamily, displayFontFamily = TccSegmentFontFamily,
        canvasFontRes = jr.brian.commandpannel.R.font.tcc_segment_bold, uppercaseLabels = true, ghostSegments = true,
    ),
)

val BloomLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.BLOOM,
    panelCardShape = RoundedCornerShape(aynDp(26f)),
    tile = StockLookSpec.tile.copy(buttonShape = RoundedCornerShape(percent = 30)),
    slider = StockLookSpec.slider.copy(controlShape = RoundedCornerShape(aynDp(18f)), trackShape = PillShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = PillShape),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(28f))),
    traits = LookTraits(
        card = CardStyle.BLOOM_TONAL, tile = TileStyle.BLOOM_BLOB, gauge = GaugeStyle.WAVY_ARC,
        slider = SliderStyle.WAVY, nav = NavStyle.EXPRESSIVE_PILL, backdrop = BackdropStyle.SOLID,
    ),
)

val NeonArcadeLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.NEON_ARCADE,
    panelCardShape = RoundedCornerShape(aynDp(8f)),
    tile = StockLookSpec.tile.copy(buttonShape = CircleShape),
    slider = StockLookSpec.slider.copy(trackShape = PillShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = PillShape),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(10f))),
    traits = LookTraits(
        card = CardStyle.NEON_TUBE, tile = TileStyle.NEON, gauge = GaugeStyle.NEON_RING,
        slider = SliderStyle.NEON_TUBE, nav = NavStyle.NEON_UNDERLINE, backdrop = BackdropStyle.SYNTH_GRID,
        displayFontFamily = TccCondensedDisplayFontFamily, canvasFontRes = jr.brian.commandpannel.R.font.tcc_condensed_extrabold, glow = true,
    ),
)

val TerminalLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.TERMINAL,
    panelCardShape = RectangleShape,
    tile = StockLookSpec.tile.copy(buttonShape = RectangleShape, qsLabelFontSize = aynSp(9.5f)),
    slider = SliderLookSpec(controlShape = RectangleShape, trackShape = RectangleShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RectangleShape),
    dialog = DialogLookSpec(dialogShape = RectangleShape),
    traits = LookTraits(
        card = CardStyle.TERMINAL_FRAME, tile = TileStyle.TERMINAL_ROW, gauge = GaugeStyle.ASCII_BAR,
        slider = SliderStyle.ASCII, nav = NavStyle.PROMPT_INVERT, backdrop = BackdropStyle.SOLID,
        header = HeaderStyle.TITLED_FRAME, labelFontFamily = TccMonoFontFamily, displayFontFamily = TccMonoFontFamily,
        canvasFontRes = jr.brian.commandpannel.R.font.tcc_mono_bold, lowercaseLabels = true, snakeCaseLabels = true,
    ),
)

val CockpitLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.COCKPIT,
    panelCardShape = RoundedCornerShape(aynDp(10f)),
    tile = StockLookSpec.tile.copy(buttonShape = RoundedCornerShape(aynDp(8f))),
    slider = SliderLookSpec(controlShape = RoundedCornerShape(aynDp(6f)), trackShape = RoundedCornerShape(aynDp(3f))),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RoundedCornerShape(aynDp(4f))),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(12f))),
    traits = LookTraits(
        card = CardStyle.BEZEL, tile = TileStyle.SWITCH_LED, gauge = GaugeStyle.NEEDLE_DIAL,
        slider = SliderStyle.FADER, nav = NavStyle.LED_SWITCH, backdrop = BackdropStyle.SOLID,
        displayFontFamily = TccMonoFontFamily, canvasFontRes = jr.brian.commandpannel.R.font.tcc_mono_bold,
    ),
)

val StickerBookLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.STICKER_BOOK,
    panelCardShape = RoundedCornerShape(aynDp(14f)),
    tile = StockLookSpec.tile.copy(buttonShape = RoundedCornerShape(aynDp(12f))),
    slider = StockLookSpec.slider.copy(trackShape = PillShape),
    // The rounded-bold font is tall, so give the gauge column more room and trim the readout so CPU/GPU/PWR/RAM
    // labels don't clip at the top.
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = PillShape, ringDiameterRatio = 0.94f, ringValueScale = 0.26f, ringTitleScale = 0.12f, ringUnitScale = 0.12f),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(16f))),
    traits = LookTraits(
        card = CardStyle.STICKER_NOTE, tile = TileStyle.STICKER, gauge = GaugeStyle.STICKER_DONUT,
        slider = SliderStyle.STICKER, nav = NavStyle.STICKER, backdrop = BackdropStyle.PAPER_DOTS,
        labelFontFamily = TccRoundedFontFamily, displayFontFamily = TccRoundedFontFamily,
        canvasFontRes = jr.brian.commandpannel.R.font.tcc_rounded_bold, backdropPatternAlpha = 0.5f,
    ),
)

val IndustrialLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.INDUSTRIAL,
    panelCardShape = RoundedCornerShape(aynDp(2f)),
    tile = StockLookSpec.tile.copy(buttonShape = RoundedCornerShape(aynDp(2f)), qsLabelFontSize = aynSp(9.5f)),
    slider = SliderLookSpec(controlShape = RectangleShape, trackShape = RectangleShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RectangleShape),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(2f))),
    traits = LookTraits(
        card = CardStyle.HAZARD_PLATE, tile = TileStyle.PLATE, gauge = GaugeStyle.RULER_BAR,
        slider = SliderStyle.RULER, nav = NavStyle.HAZARD_UNDERLINE, backdrop = BackdropStyle.SOLID,
        labelFontFamily = TccMonoFontFamily, displayFontFamily = TccStencilFontFamily,
        canvasFontRes = jr.brian.commandpannel.R.font.tcc_stencil, uppercaseLabels = true,
    ),
)

val JellyLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.JELLY,
    panelCardShape = RoundedCornerShape(aynDp(18f)),
    tile = StockLookSpec.tile.copy(buttonShape = CircleShape),
    slider = StockLookSpec.slider.copy(trackShape = PillShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = PillShape),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(20f))),
    traits = LookTraits(
        card = CardStyle.JELLY, tile = TileStyle.GEL, gauge = GaugeStyle.CAPSULE,
        slider = SliderStyle.GEL, nav = NavStyle.GEL_PILL, backdrop = BackdropStyle.SOLID,
    ),
)

val ComicPanelLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.COMIC_PANEL,
    panelCardShape = RectangleShape,
    tile = StockLookSpec.tile.copy(buttonShape = CircleShape, qsLabelFontSize = aynSp(12f)),
    slider = StockLookSpec.slider.copy(trackShape = PillShape),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = RoundedCornerShape(aynDp(10f))),
    dialog = DialogLookSpec(dialogShape = RectangleShape),
    traits = LookTraits(
        card = CardStyle.INK_PANEL, tile = TileStyle.STARBURST, gauge = GaugeStyle.PIE,
        slider = SliderStyle.INK, nav = NavStyle.STARBURST, backdrop = BackdropStyle.HALFTONE,
        labelFontFamily = TccComicFontFamily, displayFontFamily = TccComicFontFamily,
        canvasFontRes = jr.brian.commandpannel.R.font.tcc_comic, uppercaseLabels = true,
        labelLetterSpacing = androidx.compose.ui.unit.TextUnit(0.04f, androidx.compose.ui.unit.TextUnitType.Em),
        outlineWidth = androidx.compose.ui.unit.Dp(3f),
    ),
)

val Y2kChromeLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.Y2K_CHROME,
    panelCardShape = RoundedCornerShape(aynDp(14f)),
    tile = StockLookSpec.tile.copy(buttonShape = CircleShape),
    slider = StockLookSpec.slider.copy(trackShape = PillShape),
    // Michroma is a wide display font, so the telemetry readouts need smaller sizes to fit.
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = PillShape, ringValueScale = 0.23f, infoValueScale = 0.72f),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(16f))),
    traits = LookTraits(
        card = CardStyle.CHROME_RIM, tile = TileStyle.CHROME, gauge = GaugeStyle.CHROME_RING,
        slider = SliderStyle.CHROME, nav = NavStyle.FOIL_PILL, backdrop = BackdropStyle.SOLID,
        displayFontFamily = TccChromeWideFontFamily, canvasFontRes = jr.brian.commandpannel.R.font.tcc_chrome_wide,
    ),
)

val EInkLookSpec: TccLookSpec = StockLookSpec.copy(
    look = TccUiLook.E_INK,
    panelCardShape = RoundedCornerShape(aynDp(4f)),
    tile = StockLookSpec.tile.copy(buttonShape = CircleShape),
    slider = SliderLookSpec(controlShape = RoundedCornerShape(aynDp(4f)), trackShape = RoundedCornerShape(aynDp(2f))),
    settingsTab = StockLookSpec.settingsTab.copy(fpsBadgeShape = PillShape),
    dialog = DialogLookSpec(dialogShape = RoundedCornerShape(aynDp(6f))),
    traits = LookTraits(
        card = CardStyle.HAIRLINE, tile = TileStyle.INK_OUTLINE, gauge = GaugeStyle.DITHER_BAR,
        slider = SliderStyle.DITHER, nav = NavStyle.UNDERLINE_ITALIC, backdrop = BackdropStyle.SOLID,
        labelFontFamily = TccSerifFontFamily, displayFontFamily = TccSerifFontFamily,
        canvasFontRes = jr.brian.commandpannel.R.font.tcc_serif_regular, animate = false,
    ),
)

/** Resolves a [TccUiLook] to its spec; AUTO (and anything unmapped) is the stock look. */
fun specForLook(look: TccUiLook): TccLookSpec = when (look) {
    TccUiLook.AUTO -> StockLookSpec
    TccUiLook.AURORA_GLASS -> AuroraGlassLookSpec
    TccUiLook.CARTRIDGE -> CartridgeLookSpec
    TccUiLook.TACTICAL_HUD -> TacticalHudLookSpec
    TccUiLook.SOFT_CONSOLE -> SoftConsoleLookSpec
    TccUiLook.SOFT_CONSOLE_OLED -> SoftConsoleOledLookSpec
    TccUiLook.SEGMENT_LCD -> SegmentLcdLookSpec
    TccUiLook.BLOOM -> BloomLookSpec
    TccUiLook.NEON_ARCADE -> NeonArcadeLookSpec
    TccUiLook.TERMINAL -> TerminalLookSpec
    TccUiLook.COCKPIT -> CockpitLookSpec
    TccUiLook.STICKER_BOOK -> StickerBookLookSpec
    TccUiLook.INDUSTRIAL -> IndustrialLookSpec
    TccUiLook.JELLY -> JellyLookSpec
    TccUiLook.COMIC_PANEL -> ComicPanelLookSpec
    TccUiLook.Y2K_CHROME -> Y2kChromeLookSpec
    TccUiLook.E_INK -> EInkLookSpec
}

val LocalTccLookSpec: ProvidableCompositionLocal<TccLookSpec> = staticCompositionLocalOf { StockLookSpec }
