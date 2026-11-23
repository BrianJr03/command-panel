package jr.brian.commandpannel.system

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccUiLook
import jr.brian.commandpannel.ui.theme.clampContrast
import jr.brian.commandpannel.ui.theme.resolveLookColors

private const val SECONDARY_INK_FADE = 0.25f
private const val TERTIARY_INK_FADE = 0.45f

// A client can't draw a look's frames, bevels or shadows, so its surfaces need a visible step of their own.
internal const val SHARED_SURFACE_STEP = 1.06f
internal const val SHARED_SURFACE_HIGH_STEP = 1.12f

/** The roles a built-in look paints itself, taken from the fills its card renderer uses. */
private class LookRoles(
    val background: Color,
    val surface: Color,
    /** Drawn at [highAmount] over the surface for `surface_high`, the way the look tints a focused card. */
    val highTint: Color,
    val highAmount: Float,
    val accent: Color,
    /** The look's single text color, for looks that draw text in one ink. `null` keeps the palette's text. */
    val ink: Color? = null,
    val danger: Color? = null,
)

/**
 * The palette to publish for built-in [look]. Only the shared theme uses this: the overlay and companion keep resolving
 * their own colors. Renderer-only looks return [colors] unchanged. With [ledAccent] on, the LED color stays the accent.
 */
internal fun publishedLookPalette(colors: TccColors, look: TccUiLook, ledAccent: Boolean): TccColors {
    val roles = lookRoles(colors, look) ?: return colors
    val background = roles.background.opaque()
    val surface = roles.surface.opaque().clampContrast(background, SHARED_SURFACE_STEP)
    val surfaceHigh = roles.highTint.opaque().copy(alpha = roles.highAmount).compositeOver(surface)
        .clampContrast(surface, SHARED_SURFACE_HIGH_STEP)
    val ink = roles.ink?.opaque()
    return colors.copy(
        primaryBackground = background,
        panelBackground = surface,
        panelBackgroundSelect = surfaceHigh,
        primary = ink ?: colors.primary.opaque(),
        secondary = ink?.let { lerp(it, surface, SECONDARY_INK_FADE) } ?: colors.secondary.opaque(),
        tertiary = ink?.let { lerp(it, surface, TERTIARY_INK_FADE) } ?: colors.tertiary.opaque(),
        accent = (if (ledAccent) colors.accent else roles.accent).opaque(),
        error = (roles.danger ?: colors.error).opaque(),
    )
}

private fun lookRoles(colors: TccColors, look: TccUiLook): LookRoles? {
    val lc = resolveLookColors(colors, look)
    val bg = colors.primaryBackground
    return when (look) {
        TccUiLook.SOFT_CONSOLE -> LookRoles(
            background = lc.softSurface,
            surface = lc.softSurface,
            highTint = lc.softAccent,
            highAmount = 0.06f,
            accent = lc.softAccent,
        )
        TccUiLook.SOFT_CONSOLE_OLED -> LookRoles(
            background = if (colors.isDark) Color.Black else lc.softSurface,
            surface = lc.softSurface,
            highTint = lc.softAccent,
            highAmount = 0.06f,
            accent = lc.softAccent,
        )
        // Composited, because lcdPanel's Oklab mix rounds to pure black on the pure-black palette.
        TccUiLook.SEGMENT_LCD -> LookRoles(
            background = bg,
            surface = lc.lcdInk.opaque().copy(alpha = 0.08f).compositeOver(bg.opaque()),
            highTint = lc.lcdInk,
            highAmount = 0.10f,
            accent = lc.lcdInk,
            ink = lc.lcdInk,
        )
        TccUiLook.BLOOM -> LookRoles(
            background = bg,
            surface = lc.bloomContainer,
            highTint = lc.bloomContainerHigh,
            highAmount = 1f,
            accent = lc.bloomOnContainer,
        )
        TccUiLook.NEON_ARCADE -> LookRoles(
            background = bg,
            surface = lc.neonPanel,
            highTint = lc.neonTube,
            highAmount = 0.10f,
            accent = lc.neonTube,
        )
        TccUiLook.TERMINAL -> LookRoles(
            background = bg,
            surface = bg,
            highTint = lc.terminalInk,
            highAmount = 0.08f,
            accent = lc.terminalInk,
            ink = lc.terminalInk,
        )
        TccUiLook.COCKPIT -> LookRoles(
            background = bg,
            surface = lc.cockpitPanel,
            highTint = lc.ledOn,
            highAmount = 0.06f,
            accent = lc.ledOn,
            danger = lc.dangerZone,
        )
        TccUiLook.INDUSTRIAL -> LookRoles(
            background = bg,
            surface = lc.metalFill,
            highTint = lc.hazard,
            highAmount = 0.08f,
            accent = lc.hazard,
        )
        TccUiLook.COMIC_PANEL -> LookRoles(
            background = bg,
            surface = lc.comicPaper,
            highTint = colors.accent,
            highAmount = 0.10f,
            accent = colors.accent,
            ink = lc.comicInk,
        )
        TccUiLook.E_INK -> LookRoles(
            background = lc.einkPaper,
            surface = lc.einkPaper,
            highTint = lc.einkInk,
            highAmount = 0.06f,
            accent = lc.einkInk,
            ink = lc.einkInk,
        )
        TccUiLook.AUTO,
        TccUiLook.AURORA_GLASS,
        TccUiLook.AURORA_BLUR,
        TccUiLook.CARTRIDGE,
        TccUiLook.TACTICAL_HUD,
        TccUiLook.STICKER_BOOK,
        TccUiLook.JELLY,
        TccUiLook.Y2K_CHROME -> null
    }
}

private fun Color.opaque(): Color = copy(alpha = 1f)
