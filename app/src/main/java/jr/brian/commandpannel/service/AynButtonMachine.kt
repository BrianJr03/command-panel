package jr.brian.commandpannel.service

/**
 * The physical AYN button's three-state logic, modelled exactly on the stock assistant
 * (`TccKeyReceiver` -> `TccServices`). Pure and side-effect-free except through the injected callbacks, so it is
 * unit-tested without Android.
 *
 * Broadcast `action.tcc.button.key.event` carries `key_action_down` and `key_isLongPress`:
 *  - down, not long  -> no-op (never toggle on key-down, or the following key-up closes it again)
 *  - down, long      -> consume the press, hide the overlay, blank the bottom screen
 *  - up              -> if a long press was consumed, reset and stop; else ignored on the keyguard; else if the
 *                       bottom screen is blanked, wake it; else toggle the overlay (debounced)
 *
 * Blank/wake are wired to real screen-mode control in phase 4; phase 2 passes no-ops for them.
 */
class AynButtonMachine(
    private val isKeyguardLocked: () -> Boolean,
    private val isBottomBlanked: () -> Boolean,
    private val onToggleOverlay: () -> Unit,
    private val onHideOverlay: () -> Unit,
    private val onBlankBottom: () -> Unit,
    private val onWakeBottom: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 400L,
) {
    private var longPressConsumed = false
    private var lastToggleAt = 0L

    fun onEvent(down: Boolean, longPress: Boolean) {
        if (down) {
            if (longPress) {
                longPressConsumed = true
                onHideOverlay()
                onBlankBottom()
            }
            return // key-down (short) is always a no-op
        }

        // key-up
        if (longPressConsumed) {
            longPressConsumed = false
            return
        }
        if (isKeyguardLocked()) return
        if (isBottomBlanked()) {
            onWakeBottom()
            return
        }
        // Stock's guard: toggle if the clock went back/equal (first press) or enough time has passed.
        val t = now()
        if (t <= lastToggleAt || t - lastToggleAt > debounceMs) {
            lastToggleAt = t
            onToggleOverlay()
        }
    }
}
