package jr.brian.commandpannel.ui.theme

import org.json.JSONArray
import org.json.JSONObject

/**
 * A theme pack: several Theme Studio looks in one `<name>.tcplooks.json` file, as kept in `TCP Themes/themes` and in
 * theme repos made from the template. Looks reference user fonts by file name ([FontRef.User.fileName]); [fonts] can
 * give each file an official download URL so a device without it can fetch it instead of falling back.
 */
data class LookPack(
    val name: String,
    val looks: List<CustomLook>,
    val author: String? = null,
    val description: String? = null,
    val fonts: Map<String, FontSource> = emptyMap(),
)

/** Where a font file can be downloaded from, and its license (shown before downloading). */
data class FontSource(val url: String, val license: String? = null)

object LookPackJson {
    const val FORMAT = "tcp-looks"
    const val VERSION = 1
    const val EXTENSION = ".tcplooks.json"
    /** Packs larger than this are rejected before parsing (downloads and folder reads). */
    const val MAX_BYTES = 1024 * 1024

    fun encode(pack: LookPack): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("name", pack.name)
        pack.author?.let { put("author", it) }
        pack.description?.let { put("description", it) }
        if (pack.fonts.isNotEmpty()) put("fonts", JSONObject().apply {
            pack.fonts.forEach { (file, src) ->
                put(file, JSONObject().put("url", src.url).apply { src.license?.let { put("license", it) } })
            }
        })
        put("looks", JSONArray().apply {
            pack.looks.forEach { look ->
                // Packs carry neither device-local font ids nor sync bookkeeping.
                put(CustomLookJson.encode(look.copy(source = null)).apply {
                    remove("format"); remove("version")
                    // Font ids are device-local; packs match fonts by file name.
                    listOf("labelFont", "displayFont").forEach { optJSONObject(it)?.remove("id") }
                })
            }
        })
    }.toString(2)

    /**
     * Parses a pack, or `null` if [json] is neither a pack nor a single look. A single look (the older `.cplook.json`
     * export) becomes a one-look pack named after it. Looks that fail to parse are skipped.
     */
    fun decode(json: String, fallbackName: String = "Pack"): LookPack? {
        if (json.length > MAX_BYTES) return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.optString("format") != FORMAT) {
            return CustomLookJson.decode(o)?.let { LookPack(name = it.name, looks = listOf(it)) }
        }
        if (o.optInt("version", VERSION) > VERSION) return null
        val arr = o.optJSONArray("looks") ?: return null
        val looks = (0 until arr.length()).mapNotNull { i ->
            val entry = arr.optJSONObject(i) ?: return@mapNotNull null
            // Hand-written entries may skip the id; derive a stable one from the name so updates still match.
            if (entry.optString("id").isBlank()) entry.put("id", slug(entry.optString("name")).ifBlank { "look-$i" })
            CustomLookJson.decode(entry)
        }
        val fonts = o.optJSONObject("fonts")?.let { f ->
            f.keys().asSequence().mapNotNull { file ->
                val src = f.optJSONObject(file) ?: return@mapNotNull null
                val url = src.optString("url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                file to FontSource(url, src.optString("license").ifBlank { null })
            }.toMap()
        }.orEmpty()
        return LookPack(
            name = o.optString("name").ifBlank { fallbackName }.take(60),
            looks = looks.distinctBy { it.id },
            author = o.optString("author").ifBlank { null }?.take(60),
            description = o.optString("description").ifBlank { null }?.take(300),
            fonts = fonts,
        )
    }

    private fun slug(name: String): String = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** The user font files [looks] reference. */
    fun fontFiles(looks: List<CustomLook>): Set<String> =
        looks.flatMap { listOfNotNull(it.labelFont, it.displayFont) }.filterIsInstance<FontRef.User>().map { it.fileName }.toSet()

    /** `My Look.tcplooks.json`, with characters file systems reject replaced. */
    fun fileName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "looks" } + EXTENSION
}
