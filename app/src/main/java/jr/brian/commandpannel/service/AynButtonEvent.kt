package jr.brian.commandpannel.service

/**
 * One `action.tcc.button.key.event` broadcast, read the way Better Dual Screen Assistant reads it
 * (`TccKeyReceiver` / `AssistantForegroundService.systemEventsReceiver`):
 *  - [down]: `key_action_down` when present, else false (a key-up). Absent must never mean "down", or a firmware that
 *    leaves the extras out turns every release into a long press that blanks the bottom screen.
 *  - [longPress]: `key_isLongPress` or its alias `isLongPress`; absent means false.
 *
 * On top of BDSA, values are read leniently: a Boolean, a String ("true"/"false"/"1"/"0") or an Int (1/0). Anything
 * else counts as absent. Pure, so it's unit-tested without Android.
 */
data class AynButtonEvent(val down: Boolean, val longPress: Boolean) {
    companion object {
        const val EXTRA_KEY_DOWN = "key_action_down"
        const val EXTRA_KEY_LONG = "key_isLongPress"
        const val EXTRA_KEY_LONG_ALIAS = "isLongPress"

        /** Parses from [extra], which returns the raw extra value for a key, or null when it's missing. */
        fun parse(extra: (String) -> Any?): AynButtonEvent {
            val down = truthy(extra(EXTRA_KEY_DOWN)) ?: false
            val long = truthy(extra(EXTRA_KEY_LONG)) == true || truthy(extra(EXTRA_KEY_LONG_ALIAS)) == true
            return AynButtonEvent(down, long)
        }

        /** The value as a Boolean, or null when it's missing or can't be read as one. */
        internal fun truthy(value: Any?): Boolean? = when (value) {
            is Boolean -> value
            is Int -> when (value) { 1 -> true; 0 -> false; else -> null }
            is String -> when (value.trim().lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> null
            }
            else -> null
        }
    }
}
