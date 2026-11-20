package jr.brian.commandpannel.system


/**
 * The Background image tile (#58): turns the panel's background off (the image, or a gradient since #64), so the panel
 * shows the look's backdrop (and a GIF stops decoding), without opening settings. Pure, for tests.
 */
object BackgroundImageTile {
    /** On (the default) shows the background; survives restarts and is part of settings backups. */
    const val PREF = "background_image_on"

    /** The tile does something only while a background is set (on any look); otherwise it is grayed out. */
    fun available(background: PanelBackground?): Boolean = background != null

    /** Lit when there's a background and it's turned on. */
    fun active(background: PanelBackground?, on: Boolean): Boolean = available(background) && on

    /** The background the panel should draw, or `null` for the backdrop: tile off, or a blur-behind look (Aurora Blur). */
    fun shown(background: PanelBackground?, on: Boolean, blurBehind: Boolean): PanelBackground? =
        background?.takeIf { on && !blurBehind }
}
