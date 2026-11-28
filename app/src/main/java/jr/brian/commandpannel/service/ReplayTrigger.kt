package jr.brian.commandpannel.service

/** What a save-or-arm request (AYN double press, or an external trigger like TCP Save Clip) does. */
enum class ReplayTriggerAction { ARM, SAVE, NONE }

/**
 * Arms the Replay Buffer if it's off, otherwise saves a clip. Does nothing on [standby], while the other build or
 * Better Dual Screen Assistant is in control, or while the screen is off ([asleep], #91): the buffer is paused for
 * sleep then, and arming it would hold the device awake.
 */
fun replayTriggerAction(armed: Boolean, standby: Boolean, asleep: Boolean = false): ReplayTriggerAction = when {
    standby || asleep -> ReplayTriggerAction.NONE
    !armed -> ReplayTriggerAction.ARM
    else -> ReplayTriggerAction.SAVE
}
