package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.privileged.helper.HelperProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the helper protocol constants the app and the root process both depend on. */
class HelperProtocolTest {

    @Test
    fun mainClassMatchesTheHelperEntryPoint() {
        // RootHelperMain is launched by name via app_process; a rename must update the constant.
        assertEquals("jr.brian.commandpannel.privileged.helper.RootHelperMain", HelperProtocol.MAIN_CLASS)
    }

    @Test
    fun serviceNameIsPerApplicationId() {
        // debug and release must register distinct ServiceManager names so they never collide.
        assertEquals("jr.brian.commandpannel.root_helper", HelperProtocol.serviceName("jr.brian.commandpannel"))
        assertEquals(
            "jr.brian.commandpannel.debug.root_helper",
            HelperProtocol.serviceName("jr.brian.commandpannel.debug"),
        )
    }

    @Test
    fun protocolVersionIsPositive() {
        assertTrue(HelperProtocol.VERSION >= 1)
    }

    @Test
    fun reconnectBackoffDoublesUpToTheCap() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..6).map(HelperProtocol::backoffMs))
        assertEquals(30_000L, HelperProtocol.backoffMs(40)) // no shift overflow
    }
}
