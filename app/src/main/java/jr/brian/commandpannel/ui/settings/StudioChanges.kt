package jr.brian.commandpannel.ui.settings

import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomRenderParams
import jr.brian.commandpannel.ui.theme.PinnedColors

/** The Theme Studio categories where this look differs from its base look (the base look itself doesn't count). */
internal fun CustomLook.changedCategories(): Set<StudioCategory> = StudioCategory.entries.filter { changed(it) }.toSet()

private fun CustomLook.changed(c: StudioCategory): Boolean = when (c) {
    StudioCategory.PARTS -> listOf(card, tile, gauge, slider, nav, backdrop, header).any { it != null }
    StudioCategory.CUSTOM -> custom != CustomRenderParams()
    StudioCategory.FONTS -> labelFont != null || displayFont != null
    StudioCategory.SHAPES -> listOf(panelCardShape, tileShape, sliderControlShape, sliderTrackShape, dialogShape, fpsBadgeShape).any { it != null }
    StudioCategory.COLORS -> colors != PinnedColors()
    StudioCategory.TEXT -> labelCase != null || letterSpacingEm != null || qsLabelFontSize != null
    StudioCategory.EFFECTS -> listOf(glow, scanlines, animate, liftedBackdrop, oledBackdrop, ghostSegments, outlineWidth, shadowOffset, backdropPatternAlpha).any { it != null }
    StudioCategory.FIT -> listOf(ringDiameterRatio, ringValueScale, ringTitleScale, ringUnitScale, infoValueScale).any { it != null }
}

/** This look with one category back to the base look's values. */
internal fun CustomLook.resetCategory(c: StudioCategory): CustomLook = when (c) {
    StudioCategory.PARTS -> copy(card = null, tile = null, gauge = null, slider = null, nav = null, backdrop = null, header = null)
    StudioCategory.CUSTOM -> copy(custom = CustomRenderParams())
    StudioCategory.FONTS -> copy(labelFont = null, displayFont = null)
    StudioCategory.SHAPES -> copy(panelCardShape = null, tileShape = null, sliderControlShape = null, sliderTrackShape = null, dialogShape = null, fpsBadgeShape = null)
    StudioCategory.COLORS -> copy(colors = PinnedColors())
    StudioCategory.TEXT -> copy(labelCase = null, letterSpacingEm = null, qsLabelFontSize = null)
    StudioCategory.EFFECTS -> copy(
        glow = null, scanlines = null, animate = null, liftedBackdrop = null, oledBackdrop = null, ghostSegments = null,
        outlineWidth = null, shadowOffset = null, backdropPatternAlpha = null,
    )
    StudioCategory.FIT -> copy(ringDiameterRatio = null, ringValueScale = null, ringTitleScale = null, ringUnitScale = null, infoValueScale = null)
}

/** Everything back to the base look, keeping the look's identity (id, name, base look, repo). */
internal fun CustomLook.resetAll(): CustomLook = CustomLook(id = id, name = name, baseLook = baseLook, source = source)
