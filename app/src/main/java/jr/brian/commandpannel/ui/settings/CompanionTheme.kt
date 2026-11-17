package jr.brian.commandpannel.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import jr.brian.commandpannel.system.UserFontStore
import jr.brian.commandpannel.ui.theme.toSpec
import jr.brian.commandpannel.ui.theme.withPinned
import jr.brian.commandpannel.ui.theme.LOOK_LINE_CONTRAST
import jr.brian.commandpannel.ui.theme.LOOK_TEXT_CONTRAST
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.StockLookSpec
import jr.brian.commandpannel.ui.theme.TccColors
import jr.brian.commandpannel.ui.theme.TccTheme
import jr.brian.commandpannel.ui.theme.clampContrast
import jr.brian.commandpannel.ui.theme.contrastRatio
import jr.brian.commandpannel.ui.theme.resolvePalette
import jr.brian.commandpannel.ui.theme.specForLook

/**
 * Colors of the companion settings screen. [Default] is the companion's own polished dark style; when the
 * "Companion follows overlay look" setting is on, [companionPaletteFrom] derives the same roles from the overlay
 * palette so the screen picks up the overlay's theme mode and accent.
 */
@Immutable
data class CompanionPalette(
    val bg: Color,
    val rail: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val border: Color,
    val accent: Color,
    val accentSoft: Color,
    /** Accent-filled button color while focused. */
    val accentFocused: Color,
    /** Content on [accent] fills (buttons, the app badge). */
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val danger: Color,
    val success: Color,
) {
    companion object {
        val Default = CompanionPalette(
            bg = Color(0xFF0D0E12),
            rail = Color(0xFF0A0B0E),
            surface = Color(0xFF16181F),
            surfaceHigh = Color(0xFF20232C),
            border = Color(0xFF262A34),
            accent = Color(0xFF7C6FFF),
            accentSoft = Color(0x1F7C6FFF),
            accentFocused = Color(0xFF9488FF),
            onAccent = Color.White,
            textPrimary = Color(0xFFF1F2F6),
            textSecondary = Color(0xFF9AA1B2),
            textTertiary = Color(0xFF666D7E),
            danger = Color(0xFFF26069),
            success = Color(0xFF53D483),
        )
    }
}

/** Maps the overlay palette onto the companion's color roles, keeping text and accent readable on its surfaces. */
fun companionPaletteFrom(colors: TccColors): CompanionPalette {
    val surface = colors.panelBackground
    val accent = colors.accent.copy(alpha = 1f).clampContrast(surface, LOOK_LINE_CONTRAST)
    val onAccent = if (accent.contrastRatio(Color.White) >= accent.contrastRatio(Color(0xFF141414))) Color.White else Color(0xFF141414)
    return CompanionPalette(
        bg = colors.primaryBackground,
        rail = lerp(colors.primaryBackground, surface, 0.5f),
        surface = surface,
        surfaceHigh = colors.panelBackgroundSelect,
        border = lerp(surface, colors.primary, 0.15f),
        accent = accent,
        accentSoft = accent.copy(alpha = 0.14f),
        accentFocused = lerp(accent, onAccent, 0.18f),
        onAccent = onAccent,
        textPrimary = colors.primary.clampContrast(surface, LOOK_TEXT_CONTRAST),
        textSecondary = colors.secondary.clampContrast(surface, LOOK_TEXT_CONTRAST),
        textTertiary = colors.tertiary.clampContrast(surface, LOOK_LINE_CONTRAST),
        danger = colors.error.clampContrast(surface, LOOK_LINE_CONTRAST),
        success = colors.lightning.clampContrast(surface, LOOK_LINE_CONTRAST),
    )
}

val LocalCompanionPalette: ProvidableCompositionLocal<CompanionPalette> = staticCompositionLocalOf { CompanionPalette.Default }

/** `true` while the companion follows the overlay look; switches cards / backdrop / type to the look renderers. */
val LocalCompanionThemed: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Themes the companion screen. Off (the default): the companion's own [CompanionPalette.Default] on the stock spec.
 * On: the overlay's look, theme mode and LED accent.
 */
@Composable
fun CompanionTheme(state: SettingsState, content: @Composable () -> Unit) {
    if (!state.companionFollowLook) {
        CompositionLocalProvider(
            LocalTccLookSpec provides StockLookSpec,
            LocalCompanionPalette provides CompanionPalette.Default,
            LocalCompanionThemed provides false,
            content = content,
        )
        return
    }
    val nightActive = (LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val colors = resolvePalette(state.themeMode, nightActive, state.accentOverride).withPinned(state.overlayCustomLook?.colors)
    val context = LocalContext.current
    val spec = remember(state.overlayLook, state.overlayCustomLook) {
        state.overlayCustomLook?.toSpec(UserFontStore.resolver(context)) ?: specForLook(state.overlayLook)
    }
    TccTheme(colors) {
        val palette = remember(colors) { companionPaletteFrom(colors) }
        CompositionLocalProvider(
            LocalTccLookSpec provides spec,
            LocalCompanionPalette provides palette,
            LocalCompanionThemed provides true,
            content = content,
        )
    }
}
