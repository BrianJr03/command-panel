package jr.brian.commandpannel.system

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSourcesDefaultTest {

    @Test
    fun `a fresh install follows TCP Themes first`() {
        val prefs = MemoryPrefs()
        ThemeSources.save(prefs, ThemeSources.parse("https://github.com/someone/other-looks")!!)
        ThemeSources.seedDefault(prefs, fresh = true)
        val sources = ThemeSources.all(prefs)
        assertEquals(ThemeSources.default.id, sources.first().id)
        assertEquals("TCP Themes", sources.first().name)
        assertEquals(2, sources.size)
    }

    @Test
    fun `an existing install isn't seeded`() {
        val prefs = MemoryPrefs()
        ThemeSources.seedDefault(prefs, fresh = false)
        assertTrue(ThemeSources.all(prefs).isEmpty())
    }

    @Test
    fun `removing the default sticks`() {
        val prefs = MemoryPrefs()
        ThemeSources.seedDefault(prefs, fresh = true)
        ThemeSources.remove(prefs, ThemeSources.default.id)
        ThemeSources.seedDefault(prefs, fresh = true)
        assertTrue(ThemeSources.all(prefs).isEmpty())
    }
}

/** Just enough SharedPreferences for ThemeSources (strings and booleans, applied immediately). */
private class MemoryPrefs : SharedPreferences {
    val values = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = apply { this@MemoryPrefs.values[key] = value }
        override fun putLong(key: String, value: Long) = apply { this@MemoryPrefs.values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { this@MemoryPrefs.values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { this@MemoryPrefs.values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
