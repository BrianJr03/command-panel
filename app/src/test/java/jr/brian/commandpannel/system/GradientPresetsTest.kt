package jr.brian.commandpannel.system

import android.content.SharedPreferences
import jr.brian.commandpannel.R
import jr.brian.commandpannel.ui.settings.GradientLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GradientPresetsTest {
    private val darkAccent = 0xFF8E84FF.toInt()
    private val darkBg = 0xFF000000.toInt()
    private val lightBg = 0xFFFFFFFF.toInt()

    private val copy: (String) -> String = { "$it copy" }
    private val copyN: (String, Int) -> String = { n, i -> "$n copy $i" }

    private fun specs(accent: Int = darkAccent, bg: Int = darkBg) =
        GradientPresets.all.map { it.spec("Name ${it.key}", accent, bg) }

    @Test
    fun `there are ten to twelve fixed presets plus two that match the look`() {
        val (look, fixed) = GradientPresets.all.partition { it.fromLook }
        assertEquals(2, look.size)
        assertTrue(fixed.size in 10..12)
    }

    @Test
    fun `every preset is already clean, on dark and light looks`() {
        for (s in specs() + specs(bg = lightBg) + specs(accent = 0xFF00FF00.toInt(), bg = 0x00112233)) {
            assertEquals(s.id, s, GradientRules.normalize(s))
            assertTrue(s.id, s.stops.size in GradientRules.MIN_STOPS..GradientRules.MAX_STOPS)
            assertTrue(s.id, s.stops.all { (it.color ushr 24) == 0xFF })
            assertTrue(s.id, s.speed > 0f && s.speed <= 1f)
        }
    }

    @Test
    fun `ids and names are unique and stable`() {
        val all = GradientPresets.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.nameRes }.toSet().size)
        assertTrue(all.all { it.id == "preset:${it.key}" })
        assertEquals(all.first(), GradientPresets.find(all.first().id))
    }

    @Test
    fun `presets use a spread of styles and motions`() {
        assertEquals(GradientStyle.entries.toSet(), GradientPresets.all.map { it.style }.toSet())
        assertEquals(GradientMotion.entries.toSet(), GradientPresets.all.map { it.motion }.toSet())
    }

    @Test
    fun `every preset name resolves to a string`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val fields = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        for (p in GradientPresets.all) {
            val name = fields[p.nameRes]
            assertNotNull(p.key, name)
            assertTrue(name!!, name.startsWith("gradient_preset_"))
            val text = Regex("<string name=\"$name\">([^<]*)</string>").find(xml)?.groupValues?.get(1)
            assertTrue(name, !text.isNullOrBlank() && text.length <= GradientRules.MAX_NAME)
        }
    }

    @Test
    fun `match my look follows the accent and background`() {
        val match = GradientPresets.all.first { it.key == "match_look" }
        val dark = match.spec("M", darkAccent, darkBg)
        val light = match.spec("M", darkAccent, lightBg)
        assertEquals(darkBg, dark.stops.first().color)
        assertEquals(lightBg, light.stops.first().color)
        assertNotEquals(dark.stops, light.stops)
        assertNotEquals(dark.stops, match.spec("M", 0xFF00C853.toInt(), darkBg).stops)
    }

    @Test
    fun `use saves a copy under a new id and applies it`() {
        val prefs = MemPrefs()
        val preset = specs().first { it.id == "preset:sunset" }.copy(name = "Sunset")
        val saved = GradientLibrary.usePreset(prefs, preset, "new-1", copy, copyN)!!
        assertEquals("new-1", saved.id)
        assertEquals("Sunset", saved.name)
        assertEquals(preset.copy(id = "new-1"), saved)
        assertEquals(listOf(saved), GradientStore.all(prefs))
        assertEquals("new-1", BackgroundSetting.activeGradientId(prefs))
        assertEquals(PanelBackground.Gradient(saved), BackgroundSetting.read(prefs))
    }

    @Test
    fun `using a preset twice numbers the copy, ignoring case`() {
        val prefs = MemPrefs()
        val preset = specs().first { it.id == "preset:ocean" }.copy(name = "Deep Ocean")
        GradientLibrary.usePreset(prefs, preset, "a", copy, copyN)
        val b = GradientLibrary.usePreset(prefs, preset, "b", copy, copyN)!!
        val c = GradientLibrary.usePreset(prefs, preset, "c", copy, copyN)!!
        assertEquals("Deep Ocean copy", b.name)
        assertEquals("Deep Ocean copy 2", c.name)
        assertEquals(listOf("a", "b", "c"), GradientStore.all(prefs).map { it.id })
        assertEquals("c", BackgroundSetting.activeGradientId(prefs))

        val taken = listOf(preset.copy(id = "x", name = "deep ocean"))
        assertEquals("Deep Ocean copy", GradientLibrary.presetCopy(preset, taken, "y", copy, copyN).name)
    }
}

/** Just enough SharedPreferences for the gradient prefs. */
private class MemPrefs : SharedPreferences {
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
        override fun putInt(key: String, value: Int) = apply { this@MemPrefs.values[key] = value }
        override fun putLong(key: String, value: Long) = apply { this@MemPrefs.values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { this@MemPrefs.values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { this@MemPrefs.values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
