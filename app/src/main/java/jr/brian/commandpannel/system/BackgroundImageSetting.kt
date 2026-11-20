package jr.brian.commandpannel.system

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable

/**
 * A background image file in the app's background store ([BackgroundImageStore]). [fileName] is unique per import.
 * [animated] is a GIF or animated WebP.
 */
@Immutable
data class BackgroundImage(val fileName: String, val animated: Boolean = false)

/**
 * The panel's background image (#61): one image for every look, built-in or custom, set in Look > Appearance. Each
 * look's own backdrop is its backup. Both prefs are part of settings backups (the image file itself isn't, so a restore
 * on another device shows the backdrop until an image is picked again).
 */
object BackgroundImageSetting {
    const val KEY_FILE = "background_image"
    const val KEY_ANIMATED = "background_image_animated"
    /** Experimental (#61): GIF and animated WebP backgrounds play (off by default; off shows their first frame). */
    const val KEY_ANIMATIONS_ON = "animated_backgrounds"

    fun animationsOn(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_ANIMATIONS_ON, false)

    /** The stored image, whether or not it's the active background (see [BackgroundSetting.read] for that). */
    fun read(prefs: SharedPreferences): BackgroundImage? =
        BackgroundImageRules.safeName(prefs.getString(KEY_FILE, null))?.let { BackgroundImage(it, prefs.getBoolean(KEY_ANIMATED, false)) }

    /**
     * Saves [image] and makes it the active background ([BackgroundSetting], #64: picking an image shows it), or clears
     * it with `null` (an active gradient stays; otherwise the panel goes back to the backdrop).
     */
    fun write(prefs: SharedPreferences, image: BackgroundImage?) {
        val edit = prefs.edit()
        if (image == null) {
            edit.remove(KEY_FILE).remove(KEY_ANIMATED)
            if (prefs.getString(BackgroundSetting.KEY_SOURCE, null) == BackgroundSetting.SOURCE_IMAGE) edit.remove(BackgroundSetting.KEY_SOURCE)
        } else {
            edit.putString(KEY_FILE, image.fileName).putBoolean(KEY_ANIMATED, image.animated)
                .putString(BackgroundSetting.KEY_SOURCE, BackgroundSetting.SOURCE_IMAGE)
        }
        edit.apply()
    }
}
