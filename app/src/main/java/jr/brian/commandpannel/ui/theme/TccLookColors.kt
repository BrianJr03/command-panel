package jr.brian.commandpannel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * Look-specific colors derived from the active [TccColorPalette] so the newer looks follow every
 * `AppThemeOption` accent (including Megingiard custom accents) on light, dark and pure-black
 * (AMOLED) palettes. AYN Smooth and Retroid Pixel never read these.
 *
 * Exception: the E-Ink fields ([einkInk], [einkPaper], [ditherOn]) deliberately ignore the accent
 * and use only the palette text and background, so that look stays monochrome on every palette.
 */
@Immutable
data class TccLookColors(
    val isPureBlack: Boolean,
    /** Cartridge outline / hard-shadow ink. */
    val outline: Color,
    val shadow: Color,
    val navBackground: Color,
    val navContent: Color,
    val stripBackground: Color,
    val stripContent: Color,
    /** Aurora Glass translucent card fills and rims. */
    val glassFill: Color,
    val glassFillFocused: Color,
    val glassRim: Color,
    val glassHighlight: Color,
    /** Contrast-clamped accent used for HUD line art and text. */
    val lineAccent: Color,
    val gridLine: Color,
    /** Complementary second accent (Cartridge alternating fills, meters). */
    val accent2: Color,
    val onAccent: Color,
    val onAccent2: Color,
    /** Soft Console putty surface (backdrop and card fill) and its dual soft shadows. */
    val softSurface: Color,
    val softShadowDark: Color,
    val softShadowLight: Color,
    /** Accent clamped against [softSurface] for selected icons, labels and gauge arcs. */
    val softAccent: Color,
    /** Segment LCD panel tint, contrast-clamped segment ink and unlit "ghost" segments. */
    val lcdPanel: Color,
    val lcdInk: Color,
    val lcdGhost: Color,
    /** Bloom tonal surface containers and the content color on them. */
    val bloomContainer: Color,
    val bloomContainerHigh: Color,
    val bloomOnContainer: Color,
    /** Neon Arcade tube colors (accent / [accent2]), panel tint, glow strength and horizon grid. */
    val neonTube: Color,
    val neonTube2: Color,
    val neonPanel: Color,
    val neonGlowAlpha: Float,
    val neonGrid: Color,
    /** Terminal text ink (contrast-clamped accent). */
    val terminalInk: Color,
    /** Cockpit instrument panel, bezel, screws, indicator LEDs and the red-zone tint. */
    val cockpitPanel: Color,
    val bezel: Color,
    val screw: Color,
    val ledOff: Color,
    val ledOn: Color,
    val dangerZone: Color,
    /** Sticker Book die-cut border, washi tape and drop shadow. */
    val stickerBorder: Color,
    val tape: Color,
    val stickerShadow: Color,
    /** Industrial hazard stripe color (contrast-clamped accent), its content, and metal plates. */
    val hazard: Color,
    val onHazard: Color,
    val metalFill: Color,
    val metalStroke: Color,
    /** Jelly gel gradients (accent and tonal) and the gloss highlight. */
    val gelTop: Color,
    val gelBottom: Color,
    val gelHighlight: Color,
    val gelTonalTop: Color,
    val gelTonalBottom: Color,
    /** Comic Panel ink outline, paper fill and halftone dots. */
    val comicInk: Color,
    val comicPaper: Color,
    val halftone: Color,
    /** Y2K Chrome metal stops (accent-tinted <= 6%), iridescent foil stops and their content. */
    val chromeHighlight: Color,
    val chromeLight: Color,
    val chromeMid: Color,
    val chromeHorizon: Color,
    val chromeDark: Color,
    val onChrome: Color,
    val foilColors: List<Color>,
    val onFoil: Color,
    /** E-Ink ink and paper: palette text and background only, never the accent. */
    val einkInk: Color,
    val einkPaper: Color,
    val ditherOn: Color
)

internal fun Color.contrastRatio(other: Color): Float {
    val l1 = luminance() + 0.05f
    val l2 = other.luminance() + 0.05f
    return max(l1, l2) / min(l1, l2)
}

/** Pure-Kotlin RGB -> HSV (`h` 0..360, `s`/`v` 0..1) so look colors behave identically in JVM tests. */
private fun Color.toHsv(): FloatArray {
    val maxC = max(red, max(green, blue))
    val minC = min(red, min(green, blue))
    val delta = maxC - minC
    val h = when {
        delta == 0f -> 0f
        maxC == red -> 60f * (((green - blue) / delta) % 6f)
        maxC == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return floatArrayOf((h + 360f) % 360f, if (maxC == 0f) 0f else delta / maxC, maxC)
}

private fun hsvColor(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val c = v * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha)
}

private fun Color.hueShifted(degrees: Float): Color {
    val hsv = toHsv()
    // Keep low-saturation accents (grays) visibly distinct from their complement.
    return hsvColor(
        ((hsv[0] + degrees) % 360f + 360f) % 360f,
        hsv[1].coerceAtLeast(0.55f),
        hsv[2].coerceAtLeast(0.75f)
    )
}

/**
 * Tactical HUD line color on dark palettes: a touch more saturation and brightness, then lifted
 * toward white until it holds >= 7:1 contrast so deep accents (blues, violets) read clearly.
 */
private fun Color.hudBoosted(background: Color): Color {
    val hsv = toHsv()
    var boosted = hsvColor(hsv[0], (hsv[1] * 1.12f).coerceAtMost(1f), hsv[2].coerceAtLeast(0.96f))
    var step = 0
    while (boosted.contrastRatio(background) < 7f && step < 6) {
        boosted = lerp(boosted, Color.White, 0.10f)
        step++
    }
    return boosted
}

/** Shifts [hue] degrees around the color wheel; used for Aurora backdrop blobs and gauges. */
fun Color.withHueShift(degrees: Float): Color = hueShifted(degrees)

private fun Color.contentOn(): Color = contrastingContentColor(dark = Color(0xFF141414), light = Color.White)

/**
 * Moves this color toward black (on light [background]s) or white (on dark ones) in small steps
 * until it holds [minRatio] contrast against [background]. Alpha is preserved.
 */
internal fun Color.clampContrast(background: Color, minRatio: Float): Color {
    val target = if (background.luminance() > 0.18f) Color.Black else Color.White
    var c = copy(alpha = 1f)
    var step = 0
    while (c.contrastRatio(background) < minRatio && step < 24) {
        c = lerp(c, target, 0.10f)
        step++
    }
    return c.copy(alpha = alpha)
}

/** Whichever of near-black or white contrasts more with this color (mid-tone safe). */
private fun Color.bestContentOn(): Color {
    val dark = Color(0xFF141414)
    return if (dark.contrastRatio(this) >= Color.White.contrastRatio(this)) dark else Color.White
}

/** Minimum contrast for look text / ink against its surface, and for line art and fills. */
internal const val LOOK_TEXT_CONTRAST: Float = 4.5f
internal const val LOOK_LINE_CONTRAST: Float = 3f

fun resolveLookColors(palette: TccColorPalette, look: TccUiLook): TccLookColors {
    val bg = palette.primaryBackground
    val isPureBlack = bg.luminance() < 0.01f
    val accent = palette.accent
    val accent2 = accent.hueShifted(150f)

    var lineAccent = if (look == TccUiLook.TACTICAL_HUD && palette.isDark) accent.hudBoosted(bg) else accent
    if (!palette.isDark) {
        // Darken toward black until line art keeps >= 3:1 contrast on light backgrounds.
        var step = 0
        while (lineAccent.contrastRatio(bg) < 3f && step < 10) {
            lineAccent = lerp(lineAccent, Color.Black, 0.12f)
            step++
        }
    }

    val dark = palette.isDark
    val ink = if (dark) Color(0xFFE8E8E8) else palette.primary

    // Soft Console: one continuous putty surface. Pure black is lifted to ~10% sRGB lightness
    // (Compose `lerp` mixes in Oklab, where 0.22 lands near #1A1A1A) so the dark shadow still has
    // something to darken.
    val softSurface = if (isPureBlack) lerp(lerp(bg, Color.White, 0.22f), accent, 0.02f) else bg
    val softShadowDark = if (dark) {
        lerp(softSurface, Color.Black, 0.65f).copy(alpha = 0.85f)
    } else {
        lerp(lerp(softSurface, Color.Black, 0.30f), accent, 0.06f).copy(alpha = 0.55f)
    }
    val softShadowLight = if (dark) lerp(softSurface, Color.White, 0.10f) else Color.White.copy(alpha = 0.95f)
    val softAccent = accent.clampContrast(softSurface, LOOK_LINE_CONTRAST)

    // Segment LCD: a reflective panel tinted ~8% toward the accent with contrast-clamped ink.
    val lcdPanel = lerp(bg, accent, 0.08f)
    val lcdInk = accent.clampContrast(lcdPanel, LOOK_TEXT_CONTRAST)
    val lcdGhost = lcdInk.copy(alpha = 0.10f)

    // Bloom: tonal containers (background mixed toward the accent).
    // Pure black is lifted first: a 12% mix toward a dark accent alone stays invisible on black.
    val bloomBase = if (isPureBlack) lerp(bg, Color.White, 0.16f) else bg
    // Pale accents (amber on light) barely tint the background, so containers keep a minimum step.
    val bloomContainer = lerp(bloomBase, accent, if (dark) 0.12f else 0.10f).clampContrast(bg, 1.06f)
    val bloomContainerHigh = lerp(bloomBase, accent, if (dark) 0.22f else 0.20f).clampContrast(bg, 1.12f)
    val bloomOnContainer = accent.clampContrast(bloomContainerHigh, LOOK_TEXT_CONTRAST)

    // Neon Arcade: two tube colors; light palettes get a weaker glow so it reads as an outline.
    val neonTube = accent.clampContrast(bg, LOOK_LINE_CONTRAST)
    val neonTube2 = accent2.clampContrast(bg, LOOK_LINE_CONTRAST)
    val neonPanel = if (dark) lerp(bg, accent, 0.04f) else lerp(bg, accent, 0.05f)
    val neonGlowAlpha = when {
        !dark -> 0.30f
        isPureBlack -> 0.85f
        else -> 0.70f
    }
    val neonGrid = if (dark) neonTube2.copy(alpha = 0.30f) else lineAccent.copy(alpha = 0.15f)

    // Terminal: console text is the accent, clamped for body-text contrast.
    val terminalInk = accent.clampContrast(bg, LOOK_TEXT_CONTRAST)

    // Cockpit: a dark instrument panel even on light palettes would fight the text colors, so the
    // panel follows the palette surface and the bezel / screws are derived from its text color.
    val cockpitPanel = if (dark) lerp(palette.panelBackground, Color.Black, 0.25f) else palette.panelBackground
    val bezel = lerp(cockpitPanel, palette.primary, if (dark) 0.22f else 0.30f)
    val screw = lerp(cockpitPanel, palette.primary, 0.40f)
    val ledOff = lerp(cockpitPanel, palette.primary, if (dark) 0.18f else 0.22f)
    val ledOn = accent.clampContrast(cockpitPanel, LOOK_LINE_CONTRAST)
    val dangerZone = palette.error.clampContrast(cockpitPanel, LOOK_LINE_CONTRAST)

    // Sticker Book: white die-cut edges on dark palettes, a very light accent tint on light ones.
    val stickerBorder = if (dark) Color.White else lerp(Color.White, accent, 0.06f)
    val tape = if (dark) {
        lerp(accent2, Color.White, 0.20f).copy(alpha = 0.50f)
    } else {
        // Pale second accents vanish on light panels; deepen them until the tape reads.
        accent2.clampContrast(palette.panelBackground, 1.8f).copy(alpha = 0.55f)
    }
    val stickerShadow = Color.Black.copy(alpha = if (dark) 0.45f else 0.18f)

    // Industrial: hazard stripes alternate with the background, so the accent is clamped against it.
    val hazard = accent.clampContrast(bg, LOOK_LINE_CONTRAST)
    val metalFill = lerp(palette.panelBackground, palette.primary, if (dark) 0.05f else 0.04f)
    val metalStroke = lerp(metalFill, palette.primary, 0.28f)

    // Jelly: accent gel and a tonal gel lifted off pure black so off tiles still read as gel.
    val gelBase = if (isPureBlack) lerp(bg, Color.White, 0.12f) else palette.panelBackground
    val gelTop = lerp(accent, Color.White, 0.35f)
    val gelHighlight = Color.White.copy(alpha = 0.35f)
    val gelTonalTop = lerp(gelBase, Color.White, if (dark) 0.14f else 0.55f)
    val gelTonalBottom = if (dark) gelBase else lerp(gelBase, Color.Black, 0.06f)

    // Comic Panel: near-black ink on light paper, near-white ink on dark paper.
    val comicPaper = if (dark) lerp(bg, Color.White, 0.10f) else lerp(bg, Color.White, 0.60f)
    val comicInk = if (dark) lerp(Color.White, comicPaper, 0.06f) else lerp(Color.Black, comicPaper, 0.08f)
    val halftone = accent2.copy(alpha = if (dark) 0.15f else 0.30f)

    // Y2K Chrome: neutral metal stops tinted <= 6% toward the accent; light palettes get a darker
    // horizon so the metal still reads on a light background.
    fun metal(level: Float) = lerp(lerp(Color.Black, Color.White, level), accent, 0.06f)
    val chromeHighlight = metal(0.97f)
    val chromeLight = metal(0.82f)
    val chromeHorizon = metal(if (dark) 0.30f else 0.18f)
    val chromeDark = metal(if (dark) 0.45f else 0.34f)
    val chromeMid = metal(0.66f)
    val onChrome = metal(0.12f)
    val onFoil = metal(0.08f)
    val foilColors = listOf(accent, accent.hueShifted(40f), accent2, accent.hueShifted(-40f))
        .map { it.clampContrast(onFoil, LOOK_TEXT_CONTRAST) }

    return TccLookColors(
        isPureBlack = isPureBlack,
        outline = ink,
        shadow = if (dark) Color(0xFF2A2A2A) else palette.primary,
        // On dark / AMOLED palettes the Cartridge ink bar must stay dark; inverting it would put
        // a light slab (and invisible light nav icons) on a black screen.
        navBackground = if (dark) Color(0xFF1C1C1C) else palette.primary,
        navContent = if (dark) palette.primary else palette.primaryBackground,
        stripBackground = if (dark) Color(0xFF1C1C1C) else palette.primary,
        stripContent = accent,
        glassFill = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f),
        glassFillFocused = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.78f),
        glassRim = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f),
        glassHighlight = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.90f),
        lineAccent = lineAccent,
        gridLine = lineAccent.copy(alpha = if (dark) 0.07f else 0.10f),
        accent2 = accent2,
        onAccent = accent.contentOn(),
        onAccent2 = accent2.contentOn(),
        softSurface = softSurface,
        softShadowDark = softShadowDark,
        softShadowLight = softShadowLight,
        softAccent = softAccent,
        lcdPanel = lcdPanel,
        lcdInk = lcdInk,
        lcdGhost = lcdGhost,
        bloomContainer = bloomContainer,
        bloomContainerHigh = bloomContainerHigh,
        bloomOnContainer = bloomOnContainer,
        neonTube = neonTube,
        neonTube2 = neonTube2,
        neonPanel = neonPanel,
        neonGlowAlpha = neonGlowAlpha,
        neonGrid = neonGrid,
        terminalInk = terminalInk,
        cockpitPanel = cockpitPanel,
        bezel = bezel,
        screw = screw,
        ledOff = ledOff,
        ledOn = ledOn,
        dangerZone = dangerZone,
        stickerBorder = stickerBorder,
        tape = tape,
        stickerShadow = stickerShadow,
        hazard = hazard,
        onHazard = hazard.bestContentOn(),
        metalFill = metalFill,
        metalStroke = metalStroke,
        gelTop = gelTop,
        gelBottom = accent,
        gelHighlight = gelHighlight,
        gelTonalTop = gelTonalTop,
        gelTonalBottom = gelTonalBottom,
        comicInk = comicInk,
        comicPaper = comicPaper,
        halftone = halftone,
        chromeHighlight = chromeHighlight,
        chromeLight = chromeLight,
        chromeMid = chromeMid,
        chromeHorizon = chromeHorizon,
        chromeDark = chromeDark,
        onChrome = onChrome,
        foilColors = foilColors,
        onFoil = onFoil,
        einkInk = palette.primary,
        einkPaper = bg,
        ditherOn = palette.primary
    )
}

@Composable
fun tccLookColors(): TccLookColors {
    val palette = LocalTccColors.current
    val look = LocalTccLookSpec.current.look
    return remember(palette, look) { resolveLookColors(palette, look) }
}
