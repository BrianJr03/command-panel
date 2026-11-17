package jr.brian.commandpannel.system

import android.content.SharedPreferences
import jr.brian.commandpannel.ui.theme.CustomLook
import jr.brian.commandpannel.ui.theme.CustomLookJson
import jr.brian.commandpannel.ui.theme.LookRef
import jr.brian.commandpannel.ui.theme.TccUiLook

/**
 * Saved Theme Studio looks, stored as a JSON array in the `custom_looks` pref (included in [SettingsBackup]). The
 * overlay's [SettingsRepository] re-reads prefs on every change, so saving or applying a look restyles it live.
 */
object CustomLookStore {
    const val KEY = "custom_looks"
    private const val LOOK_KEY = "overlay_look"

    fun all(prefs: SharedPreferences): List<CustomLook> = CustomLookJson.decodeList(prefs.getString(KEY, null))

    fun find(prefs: SharedPreferences, id: String): CustomLook? = all(prefs).firstOrNull { it.id == id }

    /** Inserts or replaces [look] by id. */
    fun save(prefs: SharedPreferences, look: CustomLook) {
        val looks = all(prefs)
        val next = if (looks.any { it.id == look.id }) looks.map { if (it.id == look.id) look else it } else looks + look
        prefs.edit().putString(KEY, CustomLookJson.encodeList(next)).apply()
    }

    /** Removes [id]; if it was the active look, the overlay falls back to the look it was based on. */
    fun delete(prefs: SharedPreferences, id: String) {
        val looks = all(prefs)
        val removed = looks.firstOrNull { it.id == id } ?: return
        val edit = prefs.edit().putString(KEY, CustomLookJson.encodeList(looks - removed))
        if (selected(prefs) == LookRef.Custom(id)) edit.putString(LOOK_KEY, removed.baseLook.name)
        edit.apply()
    }

    fun selected(prefs: SharedPreferences): LookRef = LookRef.parse(prefs.getString(LOOK_KEY, null))

    fun select(prefs: SharedPreferences, ref: LookRef) {
        prefs.edit().putString(LOOK_KEY, ref.key).apply()
    }

    /**
     * The active look as (base look, custom overrides). A dangling `custom:` id (deleted, or a restored backup without
     * its looks) resolves to the stock look.
     */
    fun resolve(prefs: SharedPreferences): Pair<TccUiLook, CustomLook?> = when (val ref = selected(prefs)) {
        is LookRef.BuiltIn -> ref.look to null
        is LookRef.Custom -> find(prefs, ref.id)?.let { it.baseLook to it } ?: (TccUiLook.AUTO to null)
    }
}
