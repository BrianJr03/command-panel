package jr.brian.commandpannel.ui.theme

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/**
 * Settings for the parametric `CUSTOM` part styles (Theme Studio's "blank canvas"). A part only reads its block when
 * its style is `CUSTOM`; the renderers live in `ui/components/looks/CustomRenderers.kt`. Colors are roles resolved
 * from the active palette, so custom parts still follow the accent and stay readable on light and dark themes.
 */
@Immutable
data class CustomRenderParams(
    val card: CustomCardParams = CustomCardParams(),
    val tile: CustomTileParams = CustomTileParams(),
    val gauge: CustomGaugeParams = CustomGaugeParams(),
    val slider: CustomSliderParams = CustomSliderParams(),
    val nav: CustomNavParams = CustomNavParams(),
)

/** What a surface is filled with. */
enum class FillKind { SURFACE, TONAL, ACCENT, GLASS, GRADIENT, NONE }

/** Which palette color a border or line uses. */
enum class LineRole { SUBTLE, TEXT, ACCENT, ACCENT2 }

@Immutable
data class CustomCardParams(
    val fill: FillKind = FillKind.SURFACE,
    /** `0..1` multiplier on the fill's alpha. */
    val fillOpacity: Float = 1f,
    val borderWidth: Float = 0f,
    val border: LineRole = LineRole.SUBTLE,
    /** Hard drop-shadow offset in dp (0 = none). */
    val shadow: Float = 0f,
    val glow: Boolean = false,
)

@Immutable
data class CustomTileParams(
    val fill: FillKind = FillKind.TONAL,
    val selectedFill: FillKind = FillKind.ACCENT,
    val borderWidth: Float = 0f,
    val border: LineRole = LineRole.SUBTLE,
    val glow: Boolean = false,
)

enum class GaugeKind { RING, ARC, SEGMENTS, BAR }

/** How the four telemetry gauges are colored. */
enum class GaugeColorMode { CLASSIC, ACCENT, SPECTRUM }

@Immutable
data class CustomGaugeParams(
    val kind: GaugeKind = GaugeKind.RING,
    /** Stroke thickness as a fraction of the gauge size. */
    val thickness: Float = 0.07f,
    val roundCaps: Boolean = true,
    val colors: GaugeColorMode = GaugeColorMode.CLASSIC,
    val glow: Boolean = false,
)

@Immutable
data class CustomSliderParams(
    val track: FillKind = FillKind.TONAL,
    val fill: FillKind = FillKind.ACCENT,
    val borderWidth: Float = 0f,
    val border: LineRole = LineRole.SUBTLE,
    val thumb: Boolean = false,
)

enum class NavIndicator { PILL, UNDERLINE, BLOCK, DOT }

@Immutable
data class CustomNavParams(
    val indicator: NavIndicator = NavIndicator.PILL,
)

// ---- JSON ----

internal object CustomRenderJson {
    fun encode(c: CustomRenderParams): JSONObject = JSONObject()
        .put("card", JSONObject().put("fill", c.card.fill.name).put("fillOpacity", c.card.fillOpacity.toDouble())
            .put("borderWidth", c.card.borderWidth.toDouble()).put("border", c.card.border.name)
            .put("shadow", c.card.shadow.toDouble()).put("glow", c.card.glow))
        .put("tile", JSONObject().put("fill", c.tile.fill.name).put("selectedFill", c.tile.selectedFill.name)
            .put("borderWidth", c.tile.borderWidth.toDouble()).put("border", c.tile.border.name).put("glow", c.tile.glow))
        .put("gauge", JSONObject().put("kind", c.gauge.kind.name).put("thickness", c.gauge.thickness.toDouble())
            .put("roundCaps", c.gauge.roundCaps).put("colors", c.gauge.colors.name).put("glow", c.gauge.glow))
        .put("slider", JSONObject().put("track", c.slider.track.name).put("fill", c.slider.fill.name)
            .put("borderWidth", c.slider.borderWidth.toDouble()).put("border", c.slider.border.name).put("thumb", c.slider.thumb))
        .put("nav", JSONObject().put("indicator", c.nav.indicator.name))

    /** Missing or unknown values keep their defaults; numbers are clamped to the editor's ranges. */
    fun decode(o: JSONObject?): CustomRenderParams {
        if (o == null) return CustomRenderParams()
        val d = CustomRenderParams()
        val card = o.optJSONObject("card")
        val tile = o.optJSONObject("tile")
        val gauge = o.optJSONObject("gauge")
        val slider = o.optJSONObject("slider")
        val nav = o.optJSONObject("nav")
        return CustomRenderParams(
            card = CustomCardParams(
                fill = card.enum("fill", d.card.fill),
                fillOpacity = card.float("fillOpacity", d.card.fillOpacity, 0f, 1f),
                borderWidth = card.float("borderWidth", d.card.borderWidth, 0f, 6f),
                border = card.enum("border", d.card.border),
                shadow = card.float("shadow", d.card.shadow, 0f, 12f),
                glow = card?.optBoolean("glow", d.card.glow) ?: d.card.glow,
            ),
            tile = CustomTileParams(
                fill = tile.enum("fill", d.tile.fill),
                selectedFill = tile.enum("selectedFill", d.tile.selectedFill),
                borderWidth = tile.float("borderWidth", d.tile.borderWidth, 0f, 6f),
                border = tile.enum("border", d.tile.border),
                glow = tile?.optBoolean("glow", d.tile.glow) ?: d.tile.glow,
            ),
            gauge = CustomGaugeParams(
                kind = gauge.enum("kind", d.gauge.kind),
                thickness = gauge.float("thickness", d.gauge.thickness, 0.02f, 0.2f),
                roundCaps = gauge?.optBoolean("roundCaps", d.gauge.roundCaps) ?: d.gauge.roundCaps,
                colors = gauge.enum("colors", d.gauge.colors),
                glow = gauge?.optBoolean("glow", d.gauge.glow) ?: d.gauge.glow,
            ),
            slider = CustomSliderParams(
                track = slider.enum("track", d.slider.track),
                fill = slider.enum("fill", d.slider.fill),
                borderWidth = slider.float("borderWidth", d.slider.borderWidth, 0f, 6f),
                border = slider.enum("border", d.slider.border),
                thumb = slider?.optBoolean("thumb", d.slider.thumb) ?: d.slider.thumb,
            ),
            nav = CustomNavParams(indicator = nav.enum("indicator", d.nav.indicator)),
        )
    }

    private inline fun <reified E : Enum<E>> JSONObject?.enum(key: String, default: E): E =
        this?.optString(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun JSONObject?.float(key: String, default: Float, min: Float, max: Float): Float =
        this?.optDouble(key)?.takeIf { !it.isNaN() }?.toFloat()?.coerceIn(min, max) ?: default
}
