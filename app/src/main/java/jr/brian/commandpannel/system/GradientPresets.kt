package jr.brian.commandpannel.system

import androidx.annotation.StringRes
import jr.brian.commandpannel.R

/**
 * A built-in gradient (#69), shown in the gradient library's Presets section. Using one saves an editable copy into
 * the user's gradients (see [jr.brian.commandpannel.ui.settings.GradientLibrary.usePreset]); a preset itself is never
 * stored. [key] is stable (it makes [id], `preset:<key>`); [nameRes] is the display name, and the copy's starting name.
 *
 * [fromLook] presets take their colors from the current look's accent and background ([spec]'s `accent` and
 * `background`), resolved when shown or used; the others ignore them.
 */
data class GradientPreset(
    val key: String,
    @StringRes val nameRes: Int,
    val style: GradientStyle,
    val angle: Float,
    val motion: GradientMotion,
    val speed: Float,
    val fromLook: Boolean = false,
    private val stops: (accent: Int, background: Int) -> List<GradientStop>,
) {
    val id: String get() = GradientPresets.ID_PREFIX + key

    /** This preset as a [GradientSpec] named [name], with "Match my look" colors from [accent] and [background]. */
    fun spec(name: String, accent: Int, background: Int): GradientSpec = GradientSpec(
        id = id,
        name = name,
        stops = stops(GradientRules.opaque(accent), GradientRules.opaque(background)),
        style = style,
        angle = angle,
        motion = motion,
        speed = speed,
    )
}

/**
 * The built-in presets, in library order: the two "Match my look" ones first, then fixed palettes. Seeded from the
 * Aurora Glass palettes ([jr.brian.commandpannel.ui.theme.AuroraGradientPreset]) but darkened to dark and mid tones,
 * so the panel's tiles and labels stay readable on top. Angles: 0 degrees runs left to right, turning clockwise (90 is
 * top to bottom). Every preset already passes [GradientRules.normalize] unchanged.
 */
object GradientPresets {
    const val ID_PREFIX = "preset:"

    private fun stops(vararg s: Pair<Float, Long>): List<GradientStop> = s.map { GradientStop(it.second.toInt(), it.first) }

    private fun fixed(vararg s: Pair<Float, Long>): (Int, Int) -> List<GradientStop> {
        val list = stops(*s)
        return { _, _ -> list }
    }

    /** [a] moved [t] (0..1) of the way toward [b], per channel, opaque. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
        fun lerp(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
    }

    val all: List<GradientPreset> = listOf(
        // Match my look: the look's background with soft bands of its accent drifting through.
        GradientPreset(
            "match_look", R.string.gradient_preset_match_look, GradientStyle.AURORA, 120f, GradientMotion.DRIFT, 0.8f,
            fromLook = true,
        ) { accent, bg ->
            listOf(
                GradientStop(bg, 0f),
                GradientStop(mix(bg, accent, 0.45f), 0.4f),
                GradientStop(mix(bg, accent, 0.25f), 0.7f),
                GradientStop(bg, 1f),
            )
        },
        // My look glow: a gentle accent glow in the middle that breathes out to the look's background.
        GradientPreset(
            "match_glow", R.string.gradient_preset_match_glow, GradientStyle.RADIAL, 0f, GradientMotion.BREATHE, 0.9f,
            fromLook = true,
        ) { accent, bg ->
            listOf(
                GradientStop(mix(bg, accent, 0.5f), 0f),
                GradientStop(mix(bg, accent, 0.18f), 0.55f),
                GradientStop(bg, 1f),
            )
        },
        GradientPreset(
            "aurora", R.string.gradient_preset_aurora, GradientStyle.AURORA, 30f, GradientMotion.DRIFT, 0.9f,
            stops = fixed(0f to 0xFF071821, 0.35f to 0xFF0A5C55, 0.65f to 0xFF1E7A5A, 1f to 0xFF3A2F8C),
        ),
        GradientPreset(
            "ocean", R.string.gradient_preset_ocean, GradientStyle.LINEAR, 90f, GradientMotion.WAVE, 0.8f,
            stops = fixed(0f to 0xFF04122E, 0.4f to 0xFF0A3C7A, 0.7f to 0xFF0B5F94, 1f to 0xFF12215E),
        ),
        GradientPreset(
            "sunset", R.string.gradient_preset_sunset, GradientStyle.LINEAR, 90f, GradientMotion.DRIFT, 0.7f,
            stops = fixed(0f to 0xFF1E0E33, 0.4f to 0xFF5E1E4A, 0.75f to 0xFF9A3B3A, 1f to 0xFFB8682F),
        ),
        GradientPreset(
            "synthwave", R.string.gradient_preset_synthwave, GradientStyle.LINEAR, 90f, GradientMotion.WAVE, 1.0f,
            stops = fixed(0f to 0xFF0D0721, 0.45f to 0xFF2E1260, 0.75f to 0xFF6E1C6E, 1f to 0xFF9E2A6A),
        ),
        GradientPreset(
            "ember", R.string.gradient_preset_ember, GradientStyle.RADIAL, 0f, GradientMotion.BREATHE, 0.8f,
            stops = fixed(0f to 0xFF9A3A16, 0.45f to 0xFF5A1A10, 1f to 0xFF1A0806),
        ),
        GradientPreset(
            "forest", R.string.gradient_preset_forest, GradientStyle.AURORA, 150f, GradientMotion.DRIFT, 0.7f,
            stops = fixed(0f to 0xFF0A1C12, 0.4f to 0xFF1A4A2C, 0.7f to 0xFF2A6A44, 1f to 0xFF4A6A22),
        ),
        GradientPreset(
            "twilight", R.string.gradient_preset_twilight, GradientStyle.LINEAR, 45f, GradientMotion.ROTATE, 0.6f,
            stops = fixed(0f to 0xFF15113A, 0.4f to 0xFF3A2878, 0.75f to 0xFF6A348A, 1f to 0xFF8E4A86),
        ),
        GradientPreset(
            "nebula", R.string.gradient_preset_nebula, GradientStyle.AURORA, 60f, GradientMotion.HUE_CYCLE, 0.6f,
            stops = fixed(0f to 0xFF100820, 0.35f to 0xFF34145A, 0.65f to 0xFF0E4464, 1f to 0xFF4E1A52),
        ),
        GradientPreset(
            "lagoon", R.string.gradient_preset_lagoon, GradientStyle.RADIAL, 0f, GradientMotion.WAVE, 1.0f,
            stops = fixed(0f to 0xFF0E5A6A, 0.5f to 0xFF0A3648, 1f to 0xFF051C2A),
        ),
        GradientPreset(
            "frost", R.string.gradient_preset_frost, GradientStyle.LINEAR, 135f, GradientMotion.BREATHE, 1.0f,
            stops = fixed(0f to 0xFF232C3C, 0.45f to 0xFF3E4C64, 0.8f to 0xFF5E7090, 1f to 0xFF4A5874),
        ),
        GradientPreset(
            "desert", R.string.gradient_preset_desert, GradientStyle.LINEAR, 20f, GradientMotion.ROTATE, 0.6f,
            stops = fixed(0f to 0xFF2E1C12, 0.4f to 0xFF6A4026, 0.75f to 0xFF8E6234, 1f to 0xFF4E3222),
        ),
        GradientPreset(
            "midnight", R.string.gradient_preset_midnight, GradientStyle.RADIAL, 0f, GradientMotion.BREATHE, 1.0f,
            stops = fixed(0f to 0xFF1C2438, 0.6f to 0xFF0A0D18, 1f to 0xFF000000),
        ),
    )

    fun find(id: String): GradientPreset? = all.firstOrNull { it.id == id }
}
