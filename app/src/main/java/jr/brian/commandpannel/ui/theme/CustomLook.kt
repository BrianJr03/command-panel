package jr.brian.commandpannel.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.components.ChamferShape
import jr.brian.commandpannel.ui.components.SquircleShape
import org.json.JSONObject

/**
 * A user-built Look (Theme Studio). Every field is an override on top of [baseLook]'s spec; `null` inherits the base,
 * so "Terminal with Aurora tiles" is just `CustomLook(baseLook = TERMINAL, tile = GLASS_GLOW)`. Persisted as JSON in
 * the `custom_looks` pref and selected with `overlay_look = "custom:<id>"` (see [LookRef]).
 */
@Immutable
data class CustomLook(
    val id: String,
    val name: String,
    val baseLook: TccUiLook = TccUiLook.AUTO,
    val card: CardStyle? = null,
    val tile: TileStyle? = null,
    val gauge: GaugeStyle? = null,
    val slider: SliderStyle? = null,
    val nav: NavStyle? = null,
    /** The pattern behind the panel, and the backup for [backgroundImage] (shown while the image is off or can't show). */
    val backdrop: BackdropStyle? = null,
    /** A picture (or animation) over the backdrop; `null` for none. */
    val backgroundImage: BackgroundImage? = null,
    val header: HeaderStyle? = null,
    val panelCardShape: ShapeDef? = null,
    val tileShape: ShapeDef? = null,
    val sliderControlShape: ShapeDef? = null,
    val sliderTrackShape: ShapeDef? = null,
    val dialogShape: ShapeDef? = null,
    val fpsBadgeShape: ShapeDef? = null,
    val labelFont: FontRef? = null,
    val displayFont: FontRef? = null,
    val labelCase: LabelCase? = null,
    val letterSpacingEm: Float? = null,
    val glow: Boolean? = null,
    val scanlines: Boolean? = null,
    val outlineWidth: Float? = null,
    val shadowOffset: Float? = null,
    val animate: Boolean? = null,
    val liftedBackdrop: Boolean? = null,
    val oledBackdrop: Boolean? = null,
    val backdropPatternAlpha: Float? = null,
    val blurBehind: Boolean? = null,
    val blurRadius: Float? = null,
    val blurTint: Float? = null,
    val ghostSegments: Boolean? = null,
    val ringDiameterRatio: Float? = null,
    val ringTitleScale: Float? = null,
    val ringValueScale: Float? = null,
    val ringUnitScale: Float? = null,
    val infoValueScale: Float? = null,
    val qsLabelFontSize: Float? = null,
    val colors: PinnedColors = PinnedColors(),
    /** Settings for the parts set to their `CUSTOM` style. */
    val custom: CustomRenderParams = CustomRenderParams(),
    /** The theme repo this look syncs from (`ThemeSources` id), or `null` for the user's own looks. */
    val source: String? = null,
) {
    /** `true` when any part uses the parametric `CUSTOM` style. */
    val usesCustomParts: Boolean
        get() = card == CardStyle.CUSTOM || tile == TileStyle.CUSTOM || gauge == GaugeStyle.CUSTOM ||
            slider == SliderStyle.CUSTOM || nav == NavStyle.CUSTOM

    val ref: LookRef get() = LookRef.Custom(id)
}

/**
 * A background image file in the app's background store ([jr.brian.commandpannel.system.BackgroundImageStore]).
 * [fileName] is unique per import and is also its name in `TCP Themes/images`, so a theme pack can find the same file
 * on another device. [animated] is a GIF or animated WebP.
 */
@Immutable
data class BackgroundImage(val fileName: String, val animated: Boolean = false)

enum class ShapeKind { ROUNDED, RECT, CHAMFER, SQUIRCLE, PILL, CIRCLE }

/** A serializable shape: [size] is the corner radius / chamfer cut in dp, or the squircle radius fraction. */
@Immutable
data class ShapeDef(val kind: ShapeKind, val size: Float = 0f) {
    fun toShape(): Shape = when (kind) {
        ShapeKind.ROUNDED -> RoundedCornerShape(aynDp(size))
        ShapeKind.RECT -> RectangleShape
        ShapeKind.CHAMFER -> ChamferShape(aynDp(size))
        ShapeKind.SQUIRCLE -> SquircleShape(size.coerceIn(0.05f, 0.5f))
        ShapeKind.PILL -> RoundedCornerShape(percent = 50)
        ShapeKind.CIRCLE -> CircleShape
    }
}

/** The bundled OFL families (see [TccFonts]); [DEFAULT] is the system font. */
enum class BundledFont(val family: FontFamily?, @StringRes val labelRes: Int) {
    DEFAULT(null, R.string.font_system),
    CONDENSED(TccCondensedFontFamily, R.string.font_condensed),
    CONDENSED_DISPLAY(TccCondensedDisplayFontFamily, R.string.font_condensed_display),
    MONO(TccMonoFontFamily, R.string.font_mono),
    SEGMENT(TccSegmentFontFamily, R.string.font_segment),
    ROUNDED(TccRoundedFontFamily, R.string.font_rounded),
    STENCIL(TccStencilFontFamily, R.string.font_stencil),
    COMIC(TccComicFontFamily, R.string.font_comic),
    CHROME_WIDE(TccChromeWideFontFamily, R.string.font_chrome_wide),
    SERIF(TccSerifFontFamily, R.string.font_serif),
}

/** A font choice: a bundled family, or a user-imported file (resolved at runtime; missing files fall back). */
sealed interface FontRef {
    data class Bundled(val font: BundledFont) : FontRef
    /** [fileName] lets a look from a theme pack find the same font file on another device. */
    data class User(val id: String, val displayName: String, val fileName: String = "$displayName.ttf") : FontRef
}

enum class LabelCase { NONE, UPPER, LOWER, SNAKE }

/** Optional fixed colors; anything left `null` follows the theme mode and accent like the built-in looks. */
@Immutable
data class PinnedColors(
    val accent: Color? = null,
    val accent2: Color? = null,
    val background: Color? = null,
    val panel: Color? = null,
) {
    val isEmpty: Boolean get() = accent == null && accent2 == null && background == null && panel == null
}

/** What `overlay_look` points at: a built-in [TccUiLook] or a saved [CustomLook] (`custom:<id>`). */
sealed interface LookRef {
    val key: String

    data class BuiltIn(val look: TccUiLook) : LookRef {
        override val key: String get() = look.name
    }

    data class Custom(val id: String) : LookRef {
        override val key: String get() = PREFIX + id
    }

    companion object {
        const val PREFIX = "custom:"

        /** Unset means [TccUiLook.DEFAULT]; an unknown name falls back to the stock look. */
        fun parse(key: String?): LookRef = when {
            key == null -> BuiltIn(TccUiLook.DEFAULT)
            key.startsWith(PREFIX) -> Custom(key.removePrefix(PREFIX))
            else -> BuiltIn(TccUiLook.fromKey(key))
        }
    }
}

/** Resolves a user font id to its family (or `null` when the file is gone, which falls back to the default font). */
fun interface UserFontResolver {
    fun family(id: String): FontFamily?

    companion object {
        val None = UserFontResolver { null }
    }
}

private fun FontRef.family(fonts: UserFontResolver): FontFamily? = when (this) {
    is FontRef.Bundled -> font.family
    is FontRef.User -> fonts.family(id)
}

/** Builds the runtime spec: [baseLook]'s spec with every non-null override applied. */
fun CustomLook.toSpec(fonts: UserFontResolver = UserFontResolver.None): TccLookSpec {
    val base = specForLook(baseLook)
    val t = base.traits
    val traits = t.copy(
        card = card ?: t.card,
        tile = tile ?: t.tile,
        gauge = gauge ?: t.gauge,
        slider = slider ?: t.slider,
        nav = nav ?: t.nav,
        backdrop = backdrop ?: t.backdrop,
        header = header ?: t.header,
        labelFontFamily = if (labelFont != null) labelFont.family(fonts) else t.labelFontFamily,
        displayFontFamily = if (displayFont != null) displayFont.family(fonts) else t.displayFontFamily,
        uppercaseLabels = labelCase?.let { it == LabelCase.UPPER } ?: t.uppercaseLabels,
        lowercaseLabels = labelCase?.let { it == LabelCase.LOWER || it == LabelCase.SNAKE } ?: t.lowercaseLabels,
        snakeCaseLabels = labelCase?.let { it == LabelCase.SNAKE } ?: t.snakeCaseLabels,
        labelLetterSpacing = letterSpacingEm?.let { TextUnit(it, TextUnitType.Em) } ?: t.labelLetterSpacing,
        glow = glow ?: t.glow,
        scanlines = scanlines ?: t.scanlines,
        outlineWidth = outlineWidth?.let { Dp(it) } ?: t.outlineWidth,
        shadowOffset = shadowOffset?.let { Dp(it) } ?: t.shadowOffset,
        animate = animate ?: t.animate,
        // Soft extruded cards are invisible on a flat black backdrop unless it is lifted; pair them automatically.
        liftedBackdrop = liftedBackdrop ?: (t.liftedBackdrop || (card == CardStyle.SOFT_EXTRUDED && oledBackdrop != true && !t.oledBackdrop)),
        oledBackdrop = oledBackdrop ?: t.oledBackdrop,
        backdropPatternAlpha = backdropPatternAlpha ?: t.backdropPatternAlpha,
        blurBehind = blurBehind ?: t.blurBehind,
        blurRadiusDp = blurRadius ?: t.blurRadiusDp,
        blurTint = blurTint ?: t.blurTint,
        ghostSegments = ghostSegments ?: t.ghostSegments,
        backgroundImage = backgroundImage,
    )
    return base.copy(
        panelCardShape = panelCardShape?.toShape() ?: base.panelCardShape,
        tile = base.tile.copy(
            buttonShape = tileShape?.toShape() ?: base.tile.buttonShape,
            qsLabelFontSize = qsLabelFontSize?.let { aynSp(it) } ?: base.tile.qsLabelFontSize,
        ),
        slider = SliderLookSpec(
            controlShape = sliderControlShape?.toShape() ?: base.slider.controlShape,
            trackShape = sliderTrackShape?.toShape() ?: base.slider.trackShape,
        ),
        settingsTab = base.settingsTab.copy(
            ringDiameterRatio = ringDiameterRatio ?: base.settingsTab.ringDiameterRatio,
            ringTitleScale = ringTitleScale ?: base.settingsTab.ringTitleScale,
            ringValueScale = ringValueScale ?: base.settingsTab.ringValueScale,
            ringUnitScale = ringUnitScale ?: base.settingsTab.ringUnitScale,
            infoValueScale = infoValueScale ?: base.settingsTab.infoValueScale,
            fpsBadgeShape = fpsBadgeShape?.toShape() ?: base.settingsTab.fpsBadgeShape,
        ),
        dialog = DialogLookSpec(dialogShape = dialogShape?.toShape() ?: base.dialog.dialogShape),
        traits = traits,
        custom = if (usesCustomParts) custom else null,
    )
}

/**
 * Applies [pinned] colors to a palette. A pinned background that flips light/dark re-bases the text roles on the
 * matching stock palette so labels keep their contrast; the panel defaults to a step off the background.
 */
/**
 * The full palette for a look: [resolvePalette] for the theme mode, then the look's [pinned] colors, then the
 * ambient-LED [accentOverride] last, so with the LED→accent sync on the LED color wins over a pinned accent too
 * (a theme pack's background, panel and second accent are kept). Shared by the overlay, the companion and previews.
 */
fun resolveLookPalette(themeMode: Int, nightActive: Boolean, accentOverride: Int?, pinned: PinnedColors?): TccColors {
    val colors = resolvePalette(themeMode, nightActive, accentOverride = null).withPinned(pinned)
    return accentOverride?.let { colors.copy(accent = Color(it)) } ?: colors
}

fun TccColors.withPinned(pinned: PinnedColors?): TccColors {
    if (pinned == null || pinned.isEmpty) return this
    val bg = pinned.background
    val dark = bg?.let { it.luminance() < 0.4f } ?: isDark
    val textBase = if (dark == isDark) this else if (dark) TccColors.PureBlack else TccColors.Light
    val background = bg ?: primaryBackground
    val panel = pinned.panel
        // Compose lerps in Oklab, where ~20% toward white lifts pure black to about the stock #161616 panel.
        ?: if (bg != null) lerp(background, textBase.primary, if (dark) 0.20f else 0.05f) else panelBackground
    return textBase.copy(
        primaryBackground = background,
        panelBackground = panel,
        panelBackgroundSelect = if (pinned.panel != null || bg != null) lerp(panel, textBase.primary, 0.18f) else panelBackgroundSelect,
        dialogBg = if (bg != null || pinned.panel != null) panel else dialogBg,
        accent = pinned.accent ?: accent,
        accent2 = pinned.accent2 ?: accent2,
        isDark = dark,
    )
}

// ---- JSON (org.json, like SettingsBackup) ----

object CustomLookJson {
    const val FORMAT = "command-panel-look"
    const val VERSION = 1

    fun encode(look: CustomLook): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("id", look.id)
        put("name", look.name)
        put("baseLook", look.baseLook.name)
        look.card?.let { put("card", it.name) }
        look.tile?.let { put("tile", it.name) }
        look.gauge?.let { put("gauge", it.name) }
        look.slider?.let { put("slider", it.name) }
        look.nav?.let { put("nav", it.name) }
        look.backdrop?.let { put("backdrop", it.name) }
        look.backgroundImage?.let { put("backgroundImage", JSONObject().put("file", it.fileName).put("animated", it.animated)) }
        look.header?.let { put("header", it.name) }
        look.panelCardShape?.let { put("panelCardShape", it.toJson()) }
        look.tileShape?.let { put("tileShape", it.toJson()) }
        look.sliderControlShape?.let { put("sliderControlShape", it.toJson()) }
        look.sliderTrackShape?.let { put("sliderTrackShape", it.toJson()) }
        look.dialogShape?.let { put("dialogShape", it.toJson()) }
        look.fpsBadgeShape?.let { put("fpsBadgeShape", it.toJson()) }
        look.labelFont?.let { put("labelFont", it.toJson()) }
        look.displayFont?.let { put("displayFont", it.toJson()) }
        look.labelCase?.let { put("labelCase", it.name) }
        putOpt("letterSpacingEm", look.letterSpacingEm?.toDouble())
        putOpt("glow", look.glow)
        putOpt("scanlines", look.scanlines)
        putOpt("outlineWidth", look.outlineWidth?.toDouble())
        putOpt("shadowOffset", look.shadowOffset?.toDouble())
        putOpt("animate", look.animate)
        putOpt("liftedBackdrop", look.liftedBackdrop)
        putOpt("oledBackdrop", look.oledBackdrop)
        putOpt("backdropPatternAlpha", look.backdropPatternAlpha?.toDouble())
        putOpt("blurBehind", look.blurBehind)
        putOpt("blurRadius", look.blurRadius?.toDouble())
        putOpt("blurTint", look.blurTint?.toDouble())
        putOpt("ghostSegments", look.ghostSegments)
        putOpt("ringDiameterRatio", look.ringDiameterRatio?.toDouble())
        putOpt("ringTitleScale", look.ringTitleScale?.toDouble())
        putOpt("ringValueScale", look.ringValueScale?.toDouble())
        putOpt("ringUnitScale", look.ringUnitScale?.toDouble())
        putOpt("infoValueScale", look.infoValueScale?.toDouble())
        putOpt("qsLabelFontSize", look.qsLabelFontSize?.toDouble())
        if (look.custom != CustomRenderParams()) put("custom", CustomRenderJson.encode(look.custom))
        look.source?.let { put("source", it) }
        if (!look.colors.isEmpty) put("colors", JSONObject().apply {
            look.colors.accent?.let { put("accent", it.hex()) }
            look.colors.accent2?.let { put("accent2", it.hex()) }
            look.colors.background?.let { put("background", it.hex()) }
            look.colors.panel?.let { put("panel", it.hex()) }
        })
    }

    /** Parses a look, or `null` if [o] is not one. Unknown enum names inherit the base; numbers are clamped. */
    fun decode(o: JSONObject): CustomLook? {
        if (o.optString("format", FORMAT) != FORMAT || o.optInt("version", VERSION) > VERSION) return null
        val id = o.optString("id").ifBlank { return null }
        return CustomLook(
            id = id,
            name = o.optString("name").ifBlank { "Custom" }.take(40),
            baseLook = TccUiLook.fromKey(o.optString("baseLook", null)),
            card = o.enumOrNull<CardStyle>("card"),
            tile = o.enumOrNull<TileStyle>("tile"),
            gauge = o.enumOrNull<GaugeStyle>("gauge"),
            slider = o.enumOrNull<SliderStyle>("slider"),
            nav = o.enumOrNull<NavStyle>("nav"),
            backdrop = o.enumOrNull<BackdropStyle>("backdrop"),
            backgroundImage = o.optJSONObject("backgroundImage")?.let { b ->
                safeImageName(b.optString("file"))?.let { BackgroundImage(it, b.optBoolean("animated")) }
            },
            header = o.enumOrNull<HeaderStyle>("header"),
            panelCardShape = o.shapeOrNull("panelCardShape"),
            tileShape = o.shapeOrNull("tileShape"),
            sliderControlShape = o.shapeOrNull("sliderControlShape"),
            sliderTrackShape = o.shapeOrNull("sliderTrackShape"),
            dialogShape = o.shapeOrNull("dialogShape"),
            fpsBadgeShape = o.shapeOrNull("fpsBadgeShape"),
            labelFont = o.optJSONObject("labelFont")?.toFontRef(),
            displayFont = o.optJSONObject("displayFont")?.toFontRef(),
            labelCase = o.enumOrNull<LabelCase>("labelCase"),
            letterSpacingEm = o.floatOrNull("letterSpacingEm", -0.1f, 0.4f),
            glow = o.boolOrNull("glow"),
            scanlines = o.boolOrNull("scanlines"),
            outlineWidth = o.floatOrNull("outlineWidth", 0f, 8f),
            shadowOffset = o.floatOrNull("shadowOffset", 0f, 12f),
            animate = o.boolOrNull("animate"),
            liftedBackdrop = o.boolOrNull("liftedBackdrop"),
            oledBackdrop = o.boolOrNull("oledBackdrop"),
            backdropPatternAlpha = o.floatOrNull("backdropPatternAlpha", 0f, 1f),
            blurBehind = o.boolOrNull("blurBehind"),
            blurRadius = o.floatOrNull("blurRadius", BLUR_RADIUS_MIN, BLUR_RADIUS_MAX),
            blurTint = o.floatOrNull("blurTint", 0f, BLUR_TINT_MAX),
            ghostSegments = o.boolOrNull("ghostSegments"),
            ringDiameterRatio = o.floatOrNull("ringDiameterRatio", 0.5f, 1f),
            ringTitleScale = o.floatOrNull("ringTitleScale", 0.05f, 0.25f),
            ringValueScale = o.floatOrNull("ringValueScale", 0.1f, 0.45f),
            ringUnitScale = o.floatOrNull("ringUnitScale", 0.05f, 0.25f),
            infoValueScale = o.floatOrNull("infoValueScale", 0.4f, 1.4f),
            qsLabelFontSize = o.floatOrNull("qsLabelFontSize", 7f, 14f),
            colors = o.optJSONObject("colors")?.let {
                PinnedColors(
                    accent = it.colorOrNull("accent"),
                    accent2 = it.colorOrNull("accent2"),
                    background = it.colorOrNull("background"),
                    panel = it.colorOrNull("panel"),
                )
            } ?: PinnedColors(),
            custom = CustomRenderJson.decode(o.optJSONObject("custom")),
            source = o.optString("source").ifBlank { null },
        )
    }

    fun decodeList(json: String?): List<CustomLook> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { org.json.JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::decode) }
    }

    fun encodeList(looks: List<CustomLook>): String =
        org.json.JSONArray().apply { looks.forEach { put(encode(it)) } }.toString()

    private fun ShapeDef.toJson() = JSONObject().put("kind", kind.name).put("size", size.toDouble())

    private fun JSONObject.shapeOrNull(key: String): ShapeDef? {
        val s = optJSONObject(key) ?: return null
        val kind = enumValueOrNull<ShapeKind>(s.optString("kind")) ?: return null
        return ShapeDef(kind, s.optDouble("size", 0.0).toFloat().coerceIn(0f, 40f))
    }

    private fun FontRef.toJson(): JSONObject = when (this) {
        is FontRef.Bundled -> JSONObject().put("bundled", font.name)
        is FontRef.User -> JSONObject().put("file", fileName).put("user", displayName).put("id", id)
    }

    private fun JSONObject.toFontRef(): FontRef? = when {
        has("bundled") -> enumValueOrNull<BundledFont>(optString("bundled"))?.let { FontRef.Bundled(it) }
        // Packs reference user fonts by file name; older exports only had the display name.
        has("file") || has("user") -> {
            val file = optString("file")
            val name = optString("user").ifBlank { file.substringBeforeLast('.') }
            FontRef.User(optString("id"), name, file.ifBlank { "$name.ttf" })
        }
        else -> null
    }

    /** A background image name kept inside its folder (no path separators or parent references), or `null`. */
    internal fun safeImageName(name: String?): String? =
        name?.substringAfterLast('/')?.replace("..", "_")?.trim()?.take(120)?.ifBlank { null }

    private fun Color.hex(): String = "#%08X".format(toArgb())

    private fun JSONObject.colorOrNull(key: String): Color? {
        val s = optString(key).removePrefix("#").ifBlank { return null }
        val v = s.toLongOrNull(16) ?: return null
        return Color(if (s.length <= 6) (0xFF000000 or v) else v)
    }

    private inline fun <reified E : Enum<E>> enumValueOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private inline fun <reified E : Enum<E>> JSONObject.enumOrNull(key: String): E? =
        if (has(key)) enumValueOrNull<E>(optString(key)) else null

    private fun JSONObject.boolOrNull(key: String): Boolean? = if (has(key)) optBoolean(key) else null

    private fun JSONObject.floatOrNull(key: String, min: Float, max: Float): Float? =
        if (has(key)) optDouble(key).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(min, max) else null
}

/** Theme Studio's blur strength range (dp) and tint ceiling; a fully opaque tint would hide the blur. */
internal const val BLUR_RADIUS_MIN = 4f
internal const val BLUR_RADIUS_MAX = 64f
internal const val BLUR_TINT_MAX = 0.9f
