package jr.brian.commandpannel.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * How comfortable an Aurora Glass backdrop is to read over. Harmonious, low-to-mid saturation
 * gradients stay behind the content; clashing complementary neons pull focus away from text.
 */
enum class AuroraReadability(val label: String) {
    COMFORTABLE("Easy on the eyes"),
    BALANCED("Balanced"),
    STRAINING("Hard to focus")
}

/**
 * Preset color combinations for the Aurora Glass backdrop blobs (top-left, right, bottom).
 * [colors] `null` derives the blobs from the active palette accent ([MATCH_ACCENT]).
 */
enum class AuroraGradientPreset(
    val id: Int,
    val displayName: String,
    val colors: List<Color>?,
    val readability: AuroraReadability,
    val description: String,
    /** Swaps the dark palette base for true black (see [toAuroraOled]); light palettes keep theirs. */
    val trueBlack: Boolean = false
) {
    MATCH_ACCENT(
        0, "Match Accent", null, AuroraReadability.COMFORTABLE,
        "Follows your theme accent with two neighboring hues, so the backdrop always fits the palette"
    ),
    NORTHERN_LIGHTS(
        1, "Northern Lights", listOf(Color(0xFF00E5C8), Color(0xFF3DDC97), Color(0xFF7B61FF)),
        AuroraReadability.COMFORTABLE,
        "Cool, neighboring teal, green and violet blend smoothly and stay calm behind white text"
    ),
    DEEP_OCEAN(
        2, "Deep Ocean", listOf(Color(0xFF1E6BFF), Color(0xFF00B4FF), Color(0xFF3A2BD9)),
        AuroraReadability.COMFORTABLE,
        "A single family of blues; low hue contrast keeps your focus on the tiles"
    ),
    FROST(
        3, "Frost", listOf(Color(0xFF8FA6C4), Color(0xFFC3D0E2), Color(0xFF66789A)),
        AuroraReadability.COMFORTABLE,
        "Low-saturation steel tones give the clearest text and the least glare in the dark"
    ),
    OLED_BLACK(
        10, "OLED Black", listOf(Color(0xFF2A2A34), Color(0xFF1E2230), Color(0xFF26222E)),
        AuroraReadability.COMFORTABLE,
        "True black base with a faint charcoal glow; unlit OLED pixels give maximum contrast and save power",
        trueBlack = true
    ),
    FOREST(
        4, "Forest", listOf(Color(0xFF2EBF6B), Color(0xFF14A38B), Color(0xFF9CCF3A)),
        AuroraReadability.COMFORTABLE,
        "Soft greens are restful for long sessions and read well on both light and dark palettes"
    ),
    TWILIGHT(
        5, "Twilight", listOf(Color(0xFF6A5CFF), Color(0xFFB05CFF), Color(0xFFFF6EC7)),
        AuroraReadability.BALANCED,
        "Violet into pink is vivid but harmonious; a little more eye-catching behind labels"
    ),
    SUNSET(
        6, "Sunset", listOf(Color(0xFFFF7A45), Color(0xFFFF3D7F), Color(0xFFFFC24B)),
        AuroraReadability.BALANCED,
        "Warm hues feel lively and draw the eye more than cool ones; fine for short glances"
    ),
    NEON_RAVE(
        7, "Neon Rave", listOf(Color(0xFFFF00E5), Color(0xFF00FFE0), Color(0xFFFFF200)),
        AuroraReadability.STRAINING,
        "Fully saturated complementary neons vibrate against each other and compete with text"
    ),
    FIRE_AND_ICE(
        8, "Fire & Ice", listOf(Color(0xFFFF2D20), Color(0xFF00D4FF), Color(0xFFFF8A00)),
        AuroraReadability.STRAINING,
        "Red beside cyan makes edges appear to float (chromostereopsis), which makes focusing tiring"
    ),
    ACID(
        9, "Acid", listOf(Color(0xFFB6FF00), Color(0xFFFF00A8), Color(0xFF00FFA2)),
        AuroraReadability.STRAINING,
        "Near-maximum lime and hot pink glare on OLED and overpower small labels"
    );

    /** Backdrop blob colors for this preset, derived from [accent] for [MATCH_ACCENT]. */
    fun resolveColors(accent: Color): List<Color> =
        colors ?: listOf(accent, accent.withHueShift(40f), accent.withHueShift(-55f))

    companion object {
        fun fromId(id: Int): AuroraGradientPreset = entries.firstOrNull { it.id == id } ?: MATCH_ACCENT

        /** Maps a persisted preset id onto a known preset, falling back to [MATCH_ACCENT]. */
        fun normalize(id: Int): Int = fromId(id).id
    }
}
