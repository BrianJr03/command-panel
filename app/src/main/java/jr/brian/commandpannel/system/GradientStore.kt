package jr.brian.commandpannel.system

import android.content.SharedPreferences

/**
 * Saved gradient backgrounds (#64), stored as a JSON array in the `gradient_backgrounds` pref (included in
 * [SettingsBackup]). Global across looks. Which one is showing is [BackgroundSetting]'s job.
 */
object GradientStore {
    const val KEY = "gradient_backgrounds"

    fun all(prefs: SharedPreferences): List<GradientSpec> = GradientJson.decodeList(prefs.getString(KEY, null))

    fun find(prefs: SharedPreferences, id: String): GradientSpec? = all(prefs).firstOrNull { it.id == id }

    /** Inserts or replaces [spec] by id, normalized first. Returns what was saved, or `null` if [spec] isn't valid. */
    fun save(prefs: SharedPreferences, spec: GradientSpec): GradientSpec? {
        val clean = GradientRules.normalize(spec) ?: return null
        val list = all(prefs)
        val next = if (list.any { it.id == clean.id }) list.map { if (it.id == clean.id) clean else it } else list + clean
        prefs.edit().putString(KEY, GradientJson.encodeList(next)).apply()
        return clean
    }

    /** Removes [id]; if it was the active background, the panel goes back to the stored image (or the backdrop). */
    fun delete(prefs: SharedPreferences, id: String) {
        val list = all(prefs)
        if (list.none { it.id == id }) return
        val edit = prefs.edit().putString(KEY, GradientJson.encodeList(list.filterNot { it.id == id }))
        if (prefs.getString(BackgroundSetting.KEY_SOURCE, null) == BackgroundSetting.gradientSource(id)) {
            edit.remove(BackgroundSetting.KEY_SOURCE)
        }
        edit.apply()
    }
}
