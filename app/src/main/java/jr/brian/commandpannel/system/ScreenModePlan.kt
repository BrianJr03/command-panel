package jr.brian.commandpannel.system

/**
 * Pure decision logic for [ScreenModeController]: given what we blanked ourselves (persisted, so it survives a
 * service restart) and the new `dual_screen_display_mode`, says which panel to blank, which to restore and what to
 * do with the focus-lock settings.
 *
 * The rule is "only undo what we did": a panel's brightness is restored only if we dropped it (a saved level exists),
 * and focus lock is restored only if we saved it on leaving dual. A fresh start in dual therefore touches nothing.
 */
object ScreenModePlan {

    enum class Panel { TOP, BOTTOM }

    /** Where the focus-lock values to restore later come from, when we save them. */
    enum class FocusSave {
        /** Already saved (or dual mode): keep what is there. */
        NONE,
        /** Real dual to single transition: save the user's current values. */
        CURRENT,
        /** Started straight into single mode with nothing saved: the prior values are unknown, so restore 0. */
        DEFAULT,
    }

    /** What we have changed ourselves and must undo when dual returns. */
    data class State(
        val topBlanked: Boolean = false,
        val bottomBlanked: Boolean = false,
        val focusSaved: Boolean = false,
    )

    data class Plan(
        /** Panel to cover and drop to backlight 0, or null in dual. */
        val blank: Panel? = null,
        /** Snapshot [blank]'s level before dropping it. False when it's already dropped (its current level is 0). */
        val snapshot: Boolean = false,
        /** Panels to bring back to their saved level, bottom first. Only panels we dropped. */
        val restore: List<Panel> = emptyList(),
        val saveFocus: FocusSave = FocusSave.NONE,
        val restoreFocus: Boolean = false,
    )

    /** Null for an unknown mode (nothing to do). [fromDual] is true only for an observed dual to single change. */
    fun plan(state: State, mode: Int, fromDual: Boolean): Plan? = when (mode) {
        0 -> Plan(
            restore = listOfNotNull(
                Panel.BOTTOM.takeIf { state.bottomBlanked },
                Panel.TOP.takeIf { state.topBlanked },
            ),
            restoreFocus = state.focusSaved,
        )
        1 -> Plan(
            blank = Panel.BOTTOM,
            snapshot = !state.bottomBlanked,
            restore = listOfNotNull(Panel.TOP.takeIf { state.topBlanked }),
            saveFocus = focusSave(state, fromDual),
        )
        2 -> Plan(
            blank = Panel.TOP,
            snapshot = !state.topBlanked,
            restore = listOfNotNull(Panel.BOTTOM.takeIf { state.bottomBlanked }),
            saveFocus = focusSave(state, fromDual),
        )
        else -> null
    }

    /** A brightness that is worth restoring to: lit, finite and in range. Anything else means "leave it alone". */
    fun isRestorable(level: Float?): Boolean =
        level != null && level.isFinite() && level > MIN_LIT_BRIGHTNESS && level <= 1f

    /** `screen_brightness` (0..255) for a restored top-panel level, matching the slider's own conversion. */
    fun toScreenBrightnessSetting(level: Float): Int = (level.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)

    private fun focusSave(state: State, fromDual: Boolean): FocusSave = when {
        state.focusSaved -> FocusSave.NONE
        fromDual -> FocusSave.CURRENT
        else -> FocusSave.DEFAULT
    }

    /** Below this, a level is treated as "already off" and never used as a restore target. */
    const val MIN_LIT_BRIGHTNESS = 0.01f
}
