package jr.brian.commandpannel.service

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the save-or-arm decision shared by the AYN double press and the external trigger. */
class ReplayTriggerTest {

    @Test fun armedSaves() = assertEquals(ReplayTriggerAction.SAVE, replayTriggerAction(armed = true, standby = false))

    @Test fun unarmedArms() = assertEquals(ReplayTriggerAction.ARM, replayTriggerAction(armed = false, standby = false))

    @Test fun standbyDoesNothing() {
        assertEquals(ReplayTriggerAction.NONE, replayTriggerAction(armed = true, standby = true))
        assertEquals(ReplayTriggerAction.NONE, replayTriggerAction(armed = false, standby = true))
    }

    @Test fun screenOffDoesNothing() {
        // The buffer is paused for sleep (#91): a press neither arms it nor saves.
        assertEquals(ReplayTriggerAction.NONE, replayTriggerAction(armed = false, standby = false, asleep = true))
        assertEquals(ReplayTriggerAction.NONE, replayTriggerAction(armed = true, standby = false, asleep = true))
    }
}
