package jr.brian.commandpannel.ui.settings

import androidx.annotation.StringRes
import jr.brian.commandpannel.R

/**
 * What the rail's bottom hint says while a group or section is picked up: "Moving [nameRes]" over [controlsRes] (the
 * gamepad controls, or "release to drop" for a touch drag).
 */
internal data class RailMoveHint(@StringRes val nameRes: Int, @StringRes val controlsRes: Int)

/**
 * The hint for the rail row picked up with the gamepad ([grabbed]) or dragged by touch ([dragged]), by key (a group
 * key or a section key); null when nothing is lifted or the key is unknown. A touch drag wins, since starting one drops
 * any gamepad pick-up. Pure, so it is unit-tested.
 */
internal fun railMoveHint(grabbed: String?, dragged: String?): RailMoveHint? {
    val key = dragged ?: grabbed ?: return null
    val nameRes = RailGroup.fromKey(key)?.titleRes
        ?: SettingsCategory.entries.firstOrNull { it.key == key }?.titleRes
        ?: return null
    val controls = if (dragged != null) R.string.settings_rail_move_hint_touch else R.string.settings_rail_move_hint_gamepad
    return RailMoveHint(nameRes, controls)
}
