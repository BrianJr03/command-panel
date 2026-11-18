package jr.brian.commandpannel.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The look Theme Studio is editing, shown live on the overlay (and the companion, when it follows the look) instead
 * of the applied look, so each pick is seen full size. Null when Studio is closed: everything shows the applied look
 * again, so leaving without Save and apply reverts. The service and the companion share one process, so a flow
 * here reaches both.
 */
object StudioDraft {
    val look = MutableStateFlow<CustomLook?>(null)
}
