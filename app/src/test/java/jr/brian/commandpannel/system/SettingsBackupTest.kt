package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    @Test
    fun encodeDecodeRoundTripsExportedKeys() {
        val prefs = mapOf(
            "replace_stock_assistant" to true,
            "qs_enabled_tiles" to "focus_lock,wifi",
            "quick_launch_display" to 4,
            "overlay_look" to "AURORA_GLASS",
            "companion_follow_look" to true,
            TelemetryMonitor.PREF_TASK_FPS to true,
            "ignored_key" to "should not survive",   // not in the allow-list
        )
        val json = SettingsBackup.encode(prefs, "1.0")
        val decoded = SettingsBackup.decode(json)!!
        assertEquals(true, decoded["replace_stock_assistant"])
        assertEquals("focus_lock,wifi", decoded["qs_enabled_tiles"])
        assertEquals(4, decoded["quick_launch_display"])
        assertEquals("AURORA_GLASS", decoded["overlay_look"])
        assertEquals(true, decoded["companion_follow_look"])
        assertEquals(true, decoded[TelemetryMonitor.PREF_TASK_FPS])
        assertTrue("ignored_key" !in decoded)
    }

    @Test
    fun decodeRejectsForeignOrNewerBackups() {
        assertNull(SettingsBackup.decode("""{"format":"something-else","prefs":{}}"""))
        assertNull(SettingsBackup.decode("""{"format":"command-panel-backup","schemaVersion":99,"prefs":{}}"""))
        assertNull(SettingsBackup.decode("not json"))
    }

    @Test
    fun railGroupOrdersAreBackedUp() {
        val prefs = mapOf(
            "rail_order" to "general,diagnostics",
            "rail_group_order" to "advanced,panel,look,extras",
            "rail_sections_look" to "themes,appearance",
        )
        val decoded = SettingsBackup.decode(SettingsBackup.encode(prefs, "1.0"))!!
        assertEquals(prefs, decoded)
    }

    @Test
    fun onlyNamesExportWritesAreBackups() {
        assertTrue(SettingsBackup.isBackupName("command-panel-backup-20261121-134500.json"))
        // Planted files in the shared folder: shell metacharacters, spaces, other names, paths.
        assertFalse(SettingsBackup.isBackupName("x;reboot;.json"))
        assertFalse(SettingsBackup.isBackupName("command-panel-backup-20261121-134500.json;id"))
        assertFalse(SettingsBackup.isBackupName("command-panel-backup-\$(id)-134500.json"))
        assertFalse(SettingsBackup.isBackupName("command-panel-backup-20261121-134500 .json"))
        assertFalse(SettingsBackup.isBackupName("../command-panel-backup-20261121-134500.json"))
        assertFalse(SettingsBackup.isBackupName("notes.json"))
    }

    @Test
    fun listingSkipsPlantedNamesAndKeepsOrder() {
        val ls = """
            x;rm -rf sdcard;.json
            command-panel-backup-20261121-134500.json
            evil `id`.json
            command-panel-backup-20261001-090000.json
        """.trimIndent()
        assertEquals(
            listOf("command-panel-backup-20261121-134500.json", "command-panel-backup-20261001-090000.json"),
            SettingsBackup.names(ls),
        )
    }
}
