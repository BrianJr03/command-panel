package jr.brian.commandpannel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The stock assistant's theme tokens (`r0/C3104a` → `TccColorsTheme*` styles). Values are taken verbatim from the
 * decompiled themes so the overlay matches the stock app pixel for pixel. `tcc_theme_mode` selects the palette:
 * 1 = system, 3 = light, 4 (and 2) = pure black.
 */
data class TccColors(
    val primaryBackground: Color,
    val panelBackground: Color,
    val panelBackgroundSelect: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val fourth: Color,
    val fifth: Color,
    val accent: Color,
    val dialogBg: Color,
    val lightning: Color,
    val panelText: Color,
    val viewSelectedBg: Color,
    /** True for pure-black/dark palettes; drives the look-color derivation. */
    val isDark: Boolean,
    /** Error / danger accent (Cockpit red-zone, etc.). */
    val error: Color = Color(0xFFF64646),
    /** Pinned second accent (Theme Studio); `null` derives it from [accent] in [resolveLookColors]. */
    val accent2: Color? = null,
) {
    // Aliases so the ported look renderers (written against the fork's palette) read naturally.
    val panelBackgroundSelected: Color get() = panelBackgroundSelect
    val dialogBackground: Color get() = dialogBg

    companion object {
        val Light = TccColors(
            primaryBackground = Color(0xFFFFFFFF),
            panelBackground = Color(0xFFF4F4F4),
            panelBackgroundSelect = Color(0xFFD5D5D5),
            primary = Color(0xFF434343),
            secondary = Color(0xFF939393),
            tertiary = Color(0xFFD0D0D0),
            fourth = Color(0xFFEBEBEB),
            fifth = Color(0xFFFDFDFD),
            accent = Color(0xFF8E84FF),
            dialogBg = Color(0xFFE7E7E7),
            lightning = Color(0xFF04AD29),
            panelText = Color(0xFF666666),
            viewSelectedBg = Color(0xFF454545),
            isDark = false,
        )

        val PureBlack = TccColors(
            primaryBackground = Color(0xFF000000),
            panelBackground = Color(0xFF161616),
            panelBackgroundSelect = Color(0xFF3A3A3A),
            primary = Color(0xFFFFFFFF),
            secondary = Color(0xFFE0E0E0),
            tertiary = Color(0xFFB0B0B0),
            fourth = Color(0xFF282828),
            fifth = Color(0xFF282828),
            accent = Color(0xCB6A0D83), // stock tcc_accent_color for PureBlack (alpha 0xcb)
            dialogBg = Color(0xFF1E1E1E),
            lightning = Color(0xFF03C82E),
            panelText = Color(0xFFFFFFFF),
            viewSelectedBg = Color(0xFF1A1A1A),
            isDark = true,
        )
    }
}

/** The fork's look renderers were written against a `TccColorPalette`; in Command Panel that is [TccColors]. */
typealias TccColorPalette = TccColors

/** Ring gauge colors (hardware panel), from the stock `HardwareInfoDisplayView`. */
object RingColors {
    val Cpu = Color(0xFFEE188C)
    val Gpu = Color(0xFF70E7FF)
    val Pwr = Color(0xFFE26E01)
    val Ram = Color(0xFF76CA40)
}

/** CPU-temp icon thresholds (°C) from `CpuTempInfoComponent.setIconColorByTemp`. */
object TempColors {
    val Normal = Color(0xFF818184)
    val Warm = Color(0xFFF6A6A6)
    val Hot = Color(0xFFF67474)
    val VeryHot = Color(0xFFF64646)

    fun forCelsius(c: Int): Color = when {
        c <= 50 -> Normal
        c <= 70 -> Warm
        c <= 85 -> Hot
        else -> VeryHot
    }
}

/**
 * Picks the palette from `tcc_theme_mode` (1 = follow system night, 3 = light, else pure black), then swaps in the
 * ambient-LED [accentOverride] when the LED→accent sync is on. Shared by the overlay and the companion.
 */
fun resolvePalette(themeMode: Int, nightActive: Boolean, accentOverride: Int?): TccColors {
    val base = when (themeMode) {
        3 -> TccColors.Light
        1 -> if (nightActive) TccColors.PureBlack else TccColors.Light
        else -> TccColors.PureBlack
    }
    return accentOverride?.let { base.copy(accent = Color(it)) } ?: base
}

val LocalTccColors: ProvidableCompositionLocal<TccColors> = staticCompositionLocalOf { TccColors.PureBlack }

@Composable
fun TccTheme(colors: TccColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTccColors provides colors, content = content)
}
