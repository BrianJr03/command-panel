package jr.brian.commandpannel.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers reading the AYN button broadcast like BDSA does (#14). */
class AynButtonEventTest {

    private fun parse(vararg extras: Pair<String, Any?>): AynButtonEvent {
        val map = mapOf(*extras)
        return AynButtonEvent.parse { map[it] }
    }

    @Test
    fun noExtras_isAShortKeyUp() {
        assertEquals(AynButtonEvent(down = false, longPress = false), parse())
    }

    @Test
    fun booleans_readAsBefore() {
        assertEquals(AynButtonEvent(true, false), parse("key_action_down" to true, "key_isLongPress" to false))
        assertEquals(AynButtonEvent(true, true), parse("key_action_down" to true, "key_isLongPress" to true))
        assertEquals(AynButtonEvent(false, false), parse("key_action_down" to false, "key_isLongPress" to false))
    }

    @Test
    fun missingDown_isKeyUp_evenWhenLong() {
        assertEquals(AynButtonEvent(false, true), parse("key_isLongPress" to true))
    }

    @Test
    fun missingLong_isNotLong() {
        assertEquals(AynButtonEvent(true, false), parse("key_action_down" to true))
    }

    @Test
    fun isLongPressAlias_isAccepted() {
        assertEquals(AynButtonEvent(true, true), parse("key_action_down" to true, "isLongPress" to true))
        assertEquals(
            AynButtonEvent(true, true),
            parse("key_action_down" to true, "key_isLongPress" to false, "isLongPress" to true),
        )
    }

    @Test
    fun stringValues_areRead() {
        assertEquals(AynButtonEvent(true, true), parse("key_action_down" to "true", "key_isLongPress" to "1"))
        assertEquals(AynButtonEvent(false, false), parse("key_action_down" to "false", "key_isLongPress" to "0"))
        assertEquals(AynButtonEvent(true, false), parse("key_action_down" to " TRUE ", "isLongPress" to "False"))
    }

    @Test
    fun intValues_areRead() {
        assertEquals(AynButtonEvent(true, true), parse("key_action_down" to 1, "key_isLongPress" to 1))
        assertEquals(AynButtonEvent(false, false), parse("key_action_down" to 0, "key_isLongPress" to 0))
    }

    @Test
    fun unparseableValues_countAsAbsent() {
        assertEquals(AynButtonEvent(false, false), parse("key_action_down" to "yes", "key_isLongPress" to 2))
        assertEquals(AynButtonEvent(false, false), parse("key_action_down" to 1.0, "isLongPress" to listOf(true)))
        assertEquals(AynButtonEvent(false, false), parse("key_action_down" to null, "key_isLongPress" to null))
    }
}
