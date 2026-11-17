package jr.brian.commandpannel.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/** Look-aware label text style (font family + letter spacing from the active look's traits). */
@Composable
fun tccTextStyle(): TextStyle {
    val traits = LocalTccLookSpec.current.traits
    return TextStyle(fontFamily = traits.labelFontFamily, letterSpacing = traits.labelLetterSpacing)
}

/** Look-aware display text style (big numerals). */
@Composable
fun tccDisplayTextStyle(): TextStyle {
    val traits = LocalTccLookSpec.current.traits
    return TextStyle(fontFamily = traits.displayFontFamily ?: traits.labelFontFamily)
}

/** Applies the active look's label casing (upper / lower / snake). */
@Composable
fun lookLabel(text: String): String = lookLabel(LocalTccLookSpec.current.traits, text)

/** Applies only the look's letter casing (used by canvas-drawn headers). */
fun LookTraits.applyLabelCase(text: String): String = when {
    uppercaseLabels -> text.uppercase()
    lowercaseLabels -> text.lowercase()
    else -> text
}

fun lookLabel(traits: LookTraits, text: String): String {
    var t = text
    if (traits.snakeCaseLabels) t = t.replace('\n', '_').replace(' ', '_')
    t = when {
        traits.uppercaseLabels -> t.uppercase()
        traits.lowercaseLabels -> t.lowercase()
        else -> t
    }
    return t
}

/**
 * Resolves a drawable for the active look. Command Panel's looks never swap in pixel-art icon
 * variants, so this is the identity, kept for parity with the renderers that call it.
 */
@DrawableRes
@Composable
fun tccDrawable(@DrawableRes res: Int): Int = res
