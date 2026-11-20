package jr.brian.commandpannel.system


/**
 * The Background image tile (#58): turns the background image off, so the panel shows the look's backdrop (and a GIF
 * stops decoding), without opening settings. Pure, for tests.
 */
object BackgroundImageTile {
    /** On (the default) shows the image; survives restarts and is part of settings backups. */
    const val PREF = "background_image_on"

    /** The tile does something only while an image is set (on any look); otherwise it is grayed out. */
    fun available(image: BackgroundImage?): Boolean = image != null

    /** Lit when there's an image and it's turned on. */
    fun active(image: BackgroundImage?, on: Boolean): Boolean = available(image) && on

    /** The image the panel should draw, or `null` for the backdrop: tile off, or a blur-behind look (Aurora Blur). */
    fun shown(image: BackgroundImage?, on: Boolean, blurBehind: Boolean): BackgroundImage? =
        image?.takeIf { on && !blurBehind }
}
