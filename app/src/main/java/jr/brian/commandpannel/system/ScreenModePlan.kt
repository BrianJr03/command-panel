package jr.brian.commandpannel.system

/**
 * Pure decision logic for [ScreenModeController]. Screen off is Command Panel's own state now (#35): the vendor's
 * `dual_screen_display_mode` stays 0, because on a panel that mode marks "off" the firmware force-dims every app window
 * to alpha 0.8, so no cover could be black there. Instead a black view covers the blanked panel while the vendor still
 * thinks both are on.
 *
 * The blank state uses the Mode tab's numbers: [NONE] (dual), [BLANK_BOTTOM] ("only top screen") and [BLANK_TOP]
 * ("only bottom screen"). Focus lock is saved when a panel is blanked from dual and restored when it comes back, and
 * only what we changed is undone: a fresh start with nothing blanked touches nothing.
 */
object ScreenModePlan {

    enum class Panel { TOP, BOTTOM }

    /** Both screens on. */
    const val NONE = 0
    /** The bottom screen is covered ("only top screen"). */
    const val BLANK_BOTTOM = 1
    /** The top screen is covered ("only bottom screen"). */
    const val BLANK_TOP = 2

    /** Where the focus-lock values to restore later come from, when we save them. */
    enum class FocusSave {
        /** Already saved (or nothing blanked): keep what is there. */
        NONE,
        /** Blanking from dual: save the user's current values. */
        CURRENT,
        /** Started straight into a blank with nothing saved: the prior values are unknown, so restore 0. */
        DEFAULT,
    }

    data class Plan(
        /** Panel to cover with the black view, or null in dual. */
        val cover: Panel? = null,
        val saveFocus: FocusSave = FocusSave.NONE,
        val restoreFocus: Boolean = false,
        /** `screen_focus_lock` while [cover] is up: the visible screen (1 = top, 2 = bottom). Null in dual. */
        val focusLock: Int? = null,
    )

    /**
     * What to do for blank state [blank]. [focusSaved] says focus lock already holds our values; [fromNone] is true
     * only for a change from dual (so the current values are the user's own). Null for an unknown state.
     */
    fun plan(blank: Int, focusSaved: Boolean, fromNone: Boolean): Plan? {
        val panel = blankedPanel(blank)
        return when {
            blank == NONE -> Plan(restoreFocus = focusSaved)
            panel == null -> null
            else -> Plan(
                cover = panel,
                saveFocus = when {
                    focusSaved -> FocusSave.NONE
                    fromNone -> FocusSave.CURRENT
                    else -> FocusSave.DEFAULT
                },
                focusLock = focusLockWhileCovered(panel),
            )
        }
    }

    /** The panel [blank] covers, or null in dual (and for an unknown state). */
    fun blankedPanel(blank: Int): Panel? = when (blank) {
        BLANK_BOTTOM -> Panel.BOTTOM
        BLANK_TOP -> Panel.TOP
        else -> null
    }

    /** `screen_focus_lock` while [covered] is blanked: the other, visible screen, as the vendor's single-screen modes do. */
    fun focusLockWhileCovered(covered: Panel): Int = if (covered == Panel.BOTTOM) 1 else 2

    /** What covers a blanked screen (#39). */
    enum class Cover {
        /** Nothing is blanked. */
        NONE,
        /**
         * The app's black window only: no root helper. The status bar draws above it, and apps that hide overlay
         * windows (Android Settings) hide it while they're in front.
         */
        APP,
        /**
         * The app's window (it eats the touches) plus the helper's black SurfaceFlinger layer above everything, status
         * bar included, which nothing in WindowManager can hide.
         */
        APP_AND_LAYER,
    }

    /** Which cover [blank] gets: the helper's layer on top whenever the helper is connected. */
    fun coverFor(blank: Int, helperConnected: Boolean): Cover = when {
        blankedPanel(blank) == null -> Cover.NONE
        helperConnected -> Cover.APP_AND_LAYER
        else -> Cover.APP
    }

    /** The AYN long press: wakes a blanked screen, else blanks the bottom one. */
    fun afterLongPress(blank: Int): Int = if (blank != NONE) NONE else BLANK_BOTTOM

    /**
     * The vendor's mode as seen at start or changed by something other than us (an older Command Panel, the stock
     * assistant, a vendor shortcut): its 1 or 2 is adopted as our own blank state, after which the vendor mode is put
     * back to 0. Null leaves the blank state alone.
     */
    fun adoptVendorMode(vendorMode: Int): Int? = vendorMode.takeIf { it == BLANK_BOTTOM || it == BLANK_TOP }

    // ---- brightness levels saved by older versions (#36) ----

    /** Levels an older version dropped and saved, to bring back once (bottom first). */
    data class Saved(val top: Boolean = false, val bottom: Boolean = false)

    /** The panels whose level an older version saved, bottom first: restored once, whatever is blanked now. */
    fun legacyRestores(saved: Saved): List<Panel> = listOfNotNull(
        Panel.BOTTOM.takeIf { saved.bottom },
        Panel.TOP.takeIf { saved.top },
    )

    /**
     * Whether a saved level has to stay saved after a restore attempt (#36): only a usable level that the helper
     * didn't take (it was down, or the call failed). Forgetting it then would leave the panel dim for good.
     */
    fun keepSavedLevel(saved: Float, applied: Boolean): Boolean = isRestorable(saved) && !applied

    /** A brightness that is worth restoring to: lit, finite and in range. Anything else means "leave it alone". */
    fun isRestorable(level: Float?): Boolean =
        level != null && level.isFinite() && level > MIN_LIT_BRIGHTNESS && level <= 1f

    /** `screen_brightness` (0..255) for a restored top-panel level, matching the slider's own conversion. */
    fun toScreenBrightnessSetting(level: Float): Int = (level.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)

    /** Below this, a level is treated as "already off" and never used as a restore target. */
    const val MIN_LIT_BRIGHTNESS = 0.01f
}
