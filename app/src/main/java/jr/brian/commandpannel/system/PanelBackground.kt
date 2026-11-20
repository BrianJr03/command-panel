package jr.brian.commandpannel.system

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable

/** What the panel draws over the look's backdrop (#64): one source at a time, for every look. */
@Immutable
sealed interface PanelBackground {
    @Immutable
    data class Image(val image: BackgroundImage) : PanelBackground

    @Immutable
    data class Gradient(val spec: GradientSpec) : PanelBackground
}

/**
 * The active background source (#64), in the `background_source` pref: [SOURCE_IMAGE] or `gradient:<id>`. Applying a
 * gradient keeps the stored image ([BackgroundImageSetting]), so the user can switch back to it.
 *
 * Migration: no `background_source` pref (everyone before #64) means the image, if one is stored. A gradient id that
 * no longer exists falls back the same way.
 */
object BackgroundSetting {
    const val KEY_SOURCE = "background_source"
    const val SOURCE_IMAGE = "image"
    private const val GRADIENT_PREFIX = "gradient:"

    fun gradientSource(id: String): String = GRADIENT_PREFIX + id

    /** The background the panel should show (before the tile and Aurora Blur), or `null` for the look's backdrop. */
    fun read(prefs: SharedPreferences): PanelBackground? {
        val source = prefs.getString(KEY_SOURCE, null)
        val gradients = if (source?.startsWith(GRADIENT_PREFIX) == true) GradientStore.all(prefs) else emptyList()
        return resolve(source, BackgroundImageSetting.read(prefs), gradients)
    }

    /** [read]'s rules, pure: [source] is the pref, [image] the stored image, [gradients] the saved ones. */
    fun resolve(source: String?, image: BackgroundImage?, gradients: List<GradientSpec>): PanelBackground? {
        if (source != null && source.startsWith(GRADIENT_PREFIX)) {
            val id = source.removePrefix(GRADIENT_PREFIX)
            gradients.firstOrNull { it.id == id }?.let { return PanelBackground.Gradient(it) }
        }
        return image?.let { PanelBackground.Image(it) }
    }

    /** Shows the stored image (if there is one). [BackgroundImageSetting.write] with an image also does this. */
    fun selectImage(prefs: SharedPreferences) {
        prefs.edit().putString(KEY_SOURCE, SOURCE_IMAGE).apply()
    }

    /** Shows the saved gradient [id]. The stored image is kept. */
    fun selectGradient(prefs: SharedPreferences, id: String) {
        prefs.edit().putString(KEY_SOURCE, gradientSource(id)).apply()
    }

    /** The active gradient's id, or `null` when the source isn't a gradient. */
    fun activeGradientId(prefs: SharedPreferences): String? =
        prefs.getString(KEY_SOURCE, null)?.takeIf { it.startsWith(GRADIENT_PREFIX) }?.removePrefix(GRADIENT_PREFIX)
}
