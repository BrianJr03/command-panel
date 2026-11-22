package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.privileged.helper.HelperProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun onlyRootTheHelperAndTheAppMayCall() {
        val app = 10_123
        val helper = 0
        assertTrue(HelperProtocol.isAllowedCaller(0, appUid = app, helperUid = helper))
        assertTrue(HelperProtocol.isAllowedCaller(app, appUid = app, helperUid = helper))
        assertTrue(HelperProtocol.isAllowedCaller(1_000, appUid = app, helperUid = 1_000)) // a non-root helper itself
        assertFalse(HelperProtocol.isAllowedCaller(10_456, appUid = app, helperUid = helper)) // another app
        assertFalse(HelperProtocol.isAllowedCaller(2_000, appUid = app, helperUid = helper)) // adb shell
    }

    @Test
    fun anUnknownAppUidLetsNoAppIn() {
        // A failed `pm` lookup (-1) must not match some other caller.
        assertFalse(HelperProtocol.isAllowedCaller(-1, appUid = -1, helperUid = 0))
        assertFalse(HelperProtocol.isAllowedCaller(10_456, appUid = -1, helperUid = 0))
        assertTrue(HelperProtocol.isAllowedCaller(0, appUid = -1, helperUid = 0))
    }
}
