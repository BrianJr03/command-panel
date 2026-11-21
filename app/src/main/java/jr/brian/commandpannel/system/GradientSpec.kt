package jr.brian.commandpannel.system

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import jr.brian.commandpannel.R
import org.json.JSONArray
import org.json.JSONObject

/** One color of a gradient: an opaque ARGB [color] (alpha is ignored) at [position] in 0..1. */
@Immutable
data class GradientStop(val color: Int, val position: Float)

/** How the stops are laid out. [key] is stored in prefs and backups; never rename it. [labelRes] is its display name. */
enum class GradientStyle(val key: String, @StringRes val labelRes: Int) {
    LINEAR("linear", R.string.gradient_style_linear),
    RADIAL("radial", R.string.gradient_style_radial),
    AURORA("aurora", R.string.gradient_style_aurora);

    companion object {
        val DEFAULT = LINEAR
        fun fromKey(key: String?): GradientStyle? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How the gradient moves while Animated backgrounds is on. [key] is stored in prefs and backups; never rename it.
 * [labelRes] is its display name.
 */
enum class GradientMotion(val key: String, @StringRes val labelRes: Int) {
    DRIFT("drift", R.string.gradient_motion_drift),
    ROTATE("rotate", R.string.gradient_motion_rotate),
    BREATHE("breathe", R.string.gradient_motion_breathe),
    HUE_CYCLE("hue_cycle", R.string.gradient_motion_hue_cycle),
    WAVE("wave", R.string.gradient_motion_wave);

    companion object {
        val DEFAULT = DRIFT
        fun fromKey(key: String?): GradientMotion? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A saved gradient background (#64), global across looks like the background image. [angle] is in degrees (0 until
 * 360), [speed] is a multiplier of the motion's base rate (0 is still; the Experimental Animated backgrounds switch off
 * also shows it still). [links] is reserved for reactive gradients (#70) and round-trips untouched. Build or clean one
 * with [GradientRules.normalize]; the store and the codec only hold normalized specs.
 */
@Immutable
data class GradientSpec(
    val id: String,
    val name: String,
    val stops: List<GradientStop>,
    val style: GradientStyle = GradientStyle.DEFAULT,
    val angle: Float = 0f,
    val motion: GradientMotion = GradientMotion.DEFAULT,
    val speed: Float = GradientRules.DEFAULT_SPEED,
    val links: Map<String, String> = emptyMap(),
)

/**
 * The versioned JSON form of [GradientSpec], used by [GradientStore] and settings backups. Decoding never throws:
 * foreign, newer-version or broken input gives `null` (or is skipped in a list).
 */
object GradientJson {
    const val FORMAT = "command-panel-gradient"
    const val VERSION = 1

    fun encode(spec: GradientSpec): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("id", spec.id)
        put("name", spec.name)
        put("stops", JSONArray().apply {
            spec.stops.forEach { put(JSONObject().put("color", it.color).put("position", it.position.toDouble())) }
        })
        put("style", spec.style.key)
        put("angle", spec.angle.toDouble())
        put("motion", spec.motion.key)
        put("speed", spec.speed.toDouble())
        if (spec.links.isNotEmpty()) put("links", JSONObject().apply { spec.links.forEach { (k, v) -> put(k, v) } })
    }

    fun decode(json: String?): GradientSpec? =
        if (json.isNullOrBlank()) null else runCatching { decode(JSONObject(json)) }.getOrNull()

    fun decode(o: JSONObject): GradientSpec? = runCatching {
        if (o.optString("format", FORMAT) != FORMAT || o.optInt("version", VERSION) > VERSION) return null
        val arr = o.optJSONArray("stops") ?: return null
        val stops = (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            if (!s.has("color") || !s.has("position")) return@mapNotNull null
            GradientStop(s.optLong("color").toInt(), s.optDouble("position", Double.NaN).toFloat())
        }
        val links = o.optJSONObject("links")?.let { l -> l.keys().asSequence().associateWith { l.optString(it) } } ?: emptyMap()
        GradientRules.normalize(
            GradientSpec(
                id = o.optString("id"),
                name = o.optString("name"),
                stops = stops,
                style = GradientStyle.fromKey(o.optString("style")) ?: GradientStyle.DEFAULT,
                angle = o.optDouble("angle", 0.0).toFloat(),
                motion = GradientMotion.fromKey(o.optString("motion")) ?: GradientMotion.DEFAULT,
                speed = o.optDouble("speed", GradientRules.DEFAULT_SPEED.toDouble()).toFloat(),
                links = links,
            ),
        )
    }.getOrNull()

    fun decodeList(json: String?): List<GradientSpec> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::decode) }
    }

    fun encodeList(specs: List<GradientSpec>): String = JSONArray().apply { specs.forEach { put(encode(it)) } }.toString()
}
