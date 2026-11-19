package jr.brian.commandpannel.service

/**
 * The physical AYN button's three-state logic, modeled exactly on the stock assistant
 * (`TccKeyReceiver` -> `TccServices`). Pure and side-effect-free except through the injected callbacks, so it is
 * unit-tested without Android.
 *
 * Broadcast `action.tcc.button.key.event` carries `key_action_down` and `key_isLongPress`:
 *  - down, not long  -> no-op (never toggle on key-down, or the following key-up closes it again)
 *  - down, long      -> consume the press, hide the overlay, blank the bottom screen. If a screen is already
 *                       blanked by us ([isBlanked]: either one), wake it instead (still consumed), so a firmware that
 *                       only ever sends "down+long" can still get the screen back (#14). A repeat within the same hold
 *                       is ignored.
 *  - up              -> if a long press was consumed, reset and stop; else ignored on the keyguard; else if a
 *                       screen is blanked, wake it; else toggle the overlay (debounced)
 *
 * With [doublePressEnabled] on, that key-up action waits [doublePressWindowMs] (via [schedule]) for a second press;
 * a second key-up in time cancels it and calls [onDoublePress] instead. A long press cancels a pending press.
 *
 * Blank/wake are wired to real screen-mode control in phase 4; phase 2 passes no-ops for them.
 */
class AynButtonMachine(
    private val isKeyguardLocked: () -> Boolean,
    private val isBlanked: () -> Boolean,
    private val onToggleOverlay: () -> Unit,
    private val onHideOverlay: () -> Unit,
    private val onBlankBottom: () -> Unit,
    private val onWake: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 400L,
    private val doublePressEnabled: () -> Boolean = { false },
    private val onDoublePress: () -> Unit = {},
    /** Runs the block after the delay; returns a function that cancels it. */
    private val schedule: (delayMs: Long, block: () -> Unit) -> (() -> Unit) = { _, block -> block(); {} },
    private val doublePressWindowMs: Long = 300L,
    /**
     * A long-press down this soon after the previous one, with no key-up between, is the same hold repeating rather
     * than a new press, so it's ignored and can't flip a screen it just blanked back on.
     */
    private val longPressRepeatMs: Long = 500L,
) {
    private var longPressConsumed = false
    private var lastLongDownAt = 0L
    private var lastToggleAt = 0L
    private var cancelPending: (() -> Unit)? = null

    fun onEvent(down: Boolean, longPress: Boolean) {
        if (down) {
            if (longPress) {
                val t = now()
                // Same hold repeating (no key-up since the last long-press down): already handled, keep it consumed.
                val repeat = longPressConsumed && t >= lastLongDownAt && t - lastLongDownAt < longPressRepeatMs
                lastLongDownAt = t
                longPressConsumed = true
                if (repeat) return
                cancelPending?.invoke()
                cancelPending = null
                if (isBlanked()) {
                    // Already blanked by us: wake instead of blanking again. Its key-up, if any, is swallowed.
                    onWake()
                    return
                }
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
        if (doublePressEnabled()) {
            val pending = cancelPending
            if (pending != null) {
                pending()
                cancelPending = null
                onDoublePress()
            } else {
                var fired = false
                val cancel = schedule(doublePressWindowMs) {
                    fired = true
                    cancelPending = null
                    singlePress()
                }
                if (!fired) cancelPending = cancel
            }
            return
        }
        singlePress()
    }

    private fun singlePress() {
        if (isBlanked()) {
            onWake()
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
