package jr.brian.commandpannel.system

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelBackgroundTest {
    private val image = BackgroundImage("cat-1.gif", animated = true)
    private val aurora = GradientSpec("g1", "Aurora", listOf(GradientStop(0xFF00FF88.toInt(), 0f), GradientStop(0xFF2200FF.toInt(), 1f)))

    /** Prefs as a user from before #64 has them: an image, no `background_source`. */
    private fun prefsWithImage() = GradientPrefs().apply {
        values[BackgroundImageSetting.KEY_FILE] = image.fileName
        values[BackgroundImageSetting.KEY_ANIMATED] = true
    }

    @Test
    fun existingImageWithoutSourceMigratesToImage() {
        assertEquals(PanelBackground.Image(image), BackgroundSetting.read(prefsWithImage()))
        assertNull(BackgroundSetting.read(GradientPrefs()))
    }

    @Test
    fun applyingAGradientKeepsTheImage() {
        val prefs = prefsWithImage()
        GradientStore.save(prefs, aurora)
        BackgroundSetting.selectGradient(prefs, aurora.id)
        assertEquals(PanelBackground.Gradient(aurora), BackgroundSetting.read(prefs))
        assertEquals(image, BackgroundImageSetting.read(prefs))
        assertEquals(aurora.id, BackgroundSetting.activeGradientId(prefs))
        BackgroundSetting.selectImage(prefs)
        assertEquals(PanelBackground.Image(image), BackgroundSetting.read(prefs))
        assertNull(BackgroundSetting.activeGradientId(prefs))
    }

    @Test
    fun pickingAnImageMakesItActiveAndRemovingItKeepsAGradient() {
        val prefs = GradientPrefs()
        GradientStore.save(prefs, aurora)
        BackgroundSetting.selectGradient(prefs, aurora.id)
        BackgroundImageSetting.write(prefs, image)
        assertEquals(PanelBackground.Image(image), BackgroundSetting.read(prefs))
        BackgroundSetting.selectGradient(prefs, aurora.id)
        BackgroundImageSetting.write(prefs, null)
        assertEquals(PanelBackground.Gradient(aurora), BackgroundSetting.read(prefs))
        BackgroundSetting.selectImage(prefs)
        assertNull(BackgroundSetting.read(prefs))
    }

    @Test
    fun deletedOrMissingGradientFallsBackToTheImage() {
        val prefs = prefsWithImage()
        GradientStore.save(prefs, aurora)
        BackgroundSetting.selectGradient(prefs, aurora.id)
        GradientStore.delete(prefs, aurora.id)
        assertTrue(GradientStore.all(prefs).isEmpty())
        assertFalse(BackgroundSetting.KEY_SOURCE in prefs.values)
        assertEquals(PanelBackground.Image(image), BackgroundSetting.read(prefs))
        assertEquals(PanelBackground.Image(image), BackgroundSetting.resolve("gradient:gone", image, listOf(aurora)))
        assertNull(BackgroundSetting.resolve("gradient:gone", null, emptyList()))
    }

    @Test
    fun storeSavesNormalizedAndReplacesById() {
        val prefs = GradientPrefs()
        assertNull(GradientStore.save(prefs, aurora.copy(stops = aurora.stops.take(1))))
        GradientStore.save(prefs, aurora)
        GradientStore.save(prefs, aurora.copy(name = "Renamed", angle = 400f))
        GradientStore.save(prefs, aurora.copy(id = "g2"))
        assertEquals(listOf("Renamed", "Aurora"), GradientStore.all(prefs).map { it.name })
        assertEquals(40f, GradientStore.find(prefs, "g1")!!.angle)
    }

    @Test
    fun pruneKeepsTheStoredImageWhileAGradientIsActive() {
        val prefs = prefsWithImage()
        GradientStore.save(prefs, aurora)
        BackgroundSetting.selectGradient(prefs, aurora.id)
        assertEquals(setOf(image.fileName), BackgroundImageStore.keptNames(prefs, keep = null))
        val next = BackgroundImage("new-2.png")
        BackgroundImageSetting.write(prefs, next)
        assertEquals(setOf(next.fileName), BackgroundImageStore.keptNames(prefs, next))
        BackgroundImageSetting.write(prefs, null)
        assertTrue(BackgroundImageStore.keptNames(prefs, null).isEmpty())
    }

    @Test
    fun backupCarriesTheSourceAndGradients() {
        val prefs = prefsWithImage()
        GradientStore.save(prefs, aurora)
        BackgroundSetting.selectGradient(prefs, aurora.id)
        val decoded = SettingsBackup.decode(SettingsBackup.encode(prefs.values, "1.0"))!!
        val restored = GradientPrefs().apply { values.putAll(decoded) }
        assertEquals(PanelBackground.Gradient(aurora), BackgroundSetting.read(restored))
        assertEquals(image, BackgroundImageSetting.read(restored))
    }
}

/** Just enough SharedPreferences for the background prefs (strings and booleans, applied immediately). */
private class GradientPrefs : SharedPreferences {
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
        override fun putInt(key: String, value: Int) = apply { this@GradientPrefs.values[key] = value }
        override fun putLong(key: String, value: Long) = apply { this@GradientPrefs.values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { this@GradientPrefs.values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { this@GradientPrefs.values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
