package jr.brian.commandpannel.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingTileIdTest {

    @Test
    fun defaultsAreTheTenStockTilesInRosterOrder() {
        assertEquals(
            listOf("focus_lock", "performance", "fan", "clear_memory", "controller",
                "charging_sep", "vibration", "ambient_led", "force_landscape", "theme"),
            QuickSettingTileId.enabledKeys(null),
        )
    }

    @Test
    fun toggleAddsAndRemovesKeepingRosterOrder() {
        val withWifi = QuickSettingTileId.toggle(null, "wifi", enable = true)
        assertTrue("wifi" in QuickSettingTileId.enabledKeys(withWifi))
        // wifi comes after the stock tiles in roster order
        assertEquals("theme", QuickSettingTileId.enabledKeys(withWifi).let { it[it.indexOf("wifi") - 1] })

        val withoutFan = QuickSettingTileId.toggle(withWifi, "fan", enable = false)
        assertTrue("fan" !in QuickSettingTileId.enabledKeys(withoutFan))
    }

    @Test
    fun moveEnabledReordersWithinEnabledTiles() {
        val enabled = "focus_lock,performance,fan"
        // move "fan" up past "performance"
        val order1 = QuickSettingTileId.moveEnabled(null, enabled, "fan", up = true)
        assertEquals(listOf("focus_lock", "fan", "performance"), QuickSettingTileId.enabledKeys(enabled, order1))
        // move it back down
        val order2 = QuickSettingTileId.moveEnabled(order1, enabled, "fan", up = false)
        assertEquals(listOf("focus_lock", "performance", "fan"), QuickSettingTileId.enabledKeys(enabled, order2))
    }

    @Test
    fun moveEnabledSkipsDisabledTilesInOrder() {
        // only focus_lock and fan enabled (performance disabled); moving fan up swaps it past focus_lock
        val enabled = "focus_lock,fan"
        val order = QuickSettingTileId.moveEnabled(null, enabled, "fan", up = true)
        assertEquals(listOf("fan", "focus_lock"), QuickSettingTileId.enabledKeys(enabled, order))
    }

    @Test
    fun toggleKeepsAtLeastOneEnabled() {
        var csv = QuickSettingTileId.entries.map { it.key }.joinToString(",")
        QuickSettingTileId.entries.forEach { csv = QuickSettingTileId.toggle(csv, it.key, enable = false) }
        assertEquals(1, QuickSettingTileId.enabledKeys(csv).size)
    }
}
