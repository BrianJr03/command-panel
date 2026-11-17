package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.privileged.helper.ClientGrace
import jr.brian.commandpannel.privileged.helper.ClientGrace.Step
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the root helper does once the app's client token has died. */
class ClientGraceTest {

    private fun next(reattached: Boolean = false, installed: Boolean = true, elapsedMs: Long = 0, armed: Boolean = true) =
        ClientGrace.next(reattached, installed, elapsedMs, graceMs = 10_000, armed = armed)

    @Test
    fun waitsWithinTheGracePeriod() {
        assertEquals(Step.WAIT, next(elapsedMs = 9_999))
    }

    @Test
    fun aReattachedClientAlwaysWins() {
        assertEquals(Step.WAIT, next(reattached = true, installed = false, elapsedMs = 60_000))
    }

    @Test
    fun uninstallRecoversAtOnceWhenArmed() {
        assertEquals(Step.RECOVER_AND_EXIT, next(installed = false, elapsedMs = 0))
        assertEquals(Step.EXIT, next(installed = false, elapsedMs = 0, armed = false))
    }

    @Test
    fun graceExpiryRecoversOnlyWhenArmed() {
        assertEquals(Step.RECOVER_AND_EXIT, next(elapsedMs = 10_000))
        assertEquals(Step.EXIT, next(elapsedMs = 10_000, armed = false))
    }
}
