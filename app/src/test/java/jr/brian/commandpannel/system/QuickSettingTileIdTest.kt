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
    fun reorderEnabledAppliesDraggedOrder() {
        val enabled = "focus_lock,performance,fan"
        val order = QuickSettingTileId.reorderEnabled(null, enabled, listOf("fan", "focus_lock", "performance"))
        assertEquals(listOf("fan", "focus_lock", "performance"), QuickSettingTileId.enabledKeys(enabled, order))
    }

    @Test
    fun reorderEnabledKeepsDisabledTilesInTheirSlots() {
        // performance is disabled and sits between the two enabled tiles; it must not move.
        val enabled = "focus_lock,fan"
        val order = QuickSettingTileId.reorderEnabled(null, enabled, listOf("fan", "focus_lock"))
        assertEquals(listOf("fan", "performance", "focus_lock"), QuickSettingTileId.orderedKeys(order).take(3))
    }

    @Test
    fun reorderEnabledIgnoresNonPermutations() {
        val enabled = "focus_lock,fan"
        val before = QuickSettingTileId.orderedKeys(null).joinToString(",")
        assertEquals(before, QuickSettingTileId.reorderEnabled(null, enabled, listOf("fan")))
        assertEquals(before, QuickSettingTileId.reorderEnabled(null, enabled, listOf("fan", "wifi")))
    }

    @Test
    fun toggleKeepsAtLeastOneEnabled() {
        var csv = QuickSettingTileId.entries.map { it.key }.joinToString(",")
        QuickSettingTileId.entries.forEach { csv = QuickSettingTileId.toggle(csv, it.key, enable = false) }
        assertEquals(1, QuickSettingTileId.enabledKeys(csv).size)
    }

    @Test
    fun keyboardTileIsOffByDefaultAndJoinsASavedOrderAtTheEnd() {
        // A tile order and enabled list saved before the Keyboard tile existed (#41).
        val savedOrder = QuickSettingTileId.entries.filter { it != QuickSettingTileId.KEYBOARD }.reversed().joinToString(",") { it.key }
        val savedEnabled = "fan,wifi"
        assertTrue("keyboard" !in QuickSettingTileId.enabledKeys(null))
        assertEquals("keyboard", QuickSettingTileId.orderedKeys(savedOrder).last())
        assertEquals(listOf("wifi", "fan"), QuickSettingTileId.enabledKeys(savedEnabled, savedOrder))
        val withKeyboard = QuickSettingTileId.toggle(savedEnabled, "keyboard", enable = true)
        assertEquals(listOf("wifi", "fan", "keyboard"), QuickSettingTileId.enabledKeys(withKeyboard, savedOrder))
    }
}
