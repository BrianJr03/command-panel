package jr.brian.commandpannel.system

import jr.brian.commandpannel.ui.theme.BackgroundImage
import jr.brian.commandpannel.ui.theme.CustomLook

/**
 * The Background image tile (#58): turns the current look's background image off, so the panel shows the look's
 * backup backdrop (and a GIF stops decoding), without opening Theme Studio. Pure, for tests.
 */
object BackgroundImageTile {
    /** On (the default) shows the image; survives restarts and is part of settings backups. */
    const val PREF = "background_image_on"

    /** The tile does something only when the current look has an image; otherwise it is grayed out. */
    fun available(look: CustomLook?): Boolean = look?.backgroundImage != null

    /** Lit when there's an image and it's turned on. */
    fun active(look: CustomLook?, on: Boolean): Boolean = available(look) && on

    /** The image the panel should draw, or `null` for the backdrop: tile off, or a blur-behind look (Aurora Blur). */
    fun shown(image: BackgroundImage?, on: Boolean, blurBehind: Boolean): BackgroundImage? =
        image?.takeIf { on && !blurBehind }
}
