package jr.brian.commandpannel.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import jr.brian.commandpannel.R

/**
 * Bundled OFL font families used by the overlay looks (see assets/THIRD_PARTY_NOTICES.txt).
 * Each look opts into one via [LookTraits.labelFontFamily] / [LookTraits.displayFontFamily].
 */

/** Barlow Condensed (Cartridge labels). */
val TccCondensedFontFamily = FontFamily(
    Font(R.font.tcc_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.tcc_condensed_bold, FontWeight.Bold),
    Font(R.font.tcc_condensed_extrabold, FontWeight.ExtraBold),
)

/** Barlow Condensed ExtraBold for big display numerals. */
val TccCondensedDisplayFontFamily = FontFamily(Font(R.font.tcc_condensed_extrabold, FontWeight.ExtraBold))

/** JetBrains Mono (Tactical HUD / Terminal / Cockpit). */
val TccMonoFontFamily = FontFamily(
    Font(R.font.tcc_mono_regular, FontWeight.Normal),
    Font(R.font.tcc_mono_bold, FontWeight.Bold),
)

/** DSEG7 Classic seven-segment digits (Segment LCD display). */
val TccSegmentFontFamily = FontFamily(Font(R.font.tcc_segment_bold, FontWeight.Bold))

/** Baloo 2 ExtraBold (Sticker Book). */
val TccRoundedFontFamily = FontFamily(Font(R.font.tcc_rounded_bold, FontWeight.ExtraBold))

/** Black Ops One stencil (Industrial display). */
val TccStencilFontFamily = FontFamily(Font(R.font.tcc_stencil, FontWeight.Normal))

/** Bangers comic (Comic Panel). */
val TccComicFontFamily = FontFamily(Font(R.font.tcc_comic, FontWeight.Normal))

/** Michroma wide (Y2K Chrome display). */
val TccChromeWideFontFamily = FontFamily(Font(R.font.tcc_chrome_wide, FontWeight.Normal))

/** Literata serif (E-Ink). */
val TccSerifFontFamily = FontFamily(
    Font(R.font.tcc_serif_regular, FontWeight.Normal),
    Font(R.font.tcc_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
