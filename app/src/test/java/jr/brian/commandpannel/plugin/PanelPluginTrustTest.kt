package jr.brian.commandpannel.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelPluginTrustTest {

    private val thorKbm = PanelPluginTrust.THORKBM_CERT_SHA256

    @Test
    fun `version meta-data reads as an int or a numeric string`() {
        assertEquals(1, PanelPluginTrust.parseVersion(1))
        assertEquals(2, PanelPluginTrust.parseVersion(" 2 "))
    }

    @Test
    fun `missing, zero, negative or non-numeric versions are invalid`() {
        assertNull(PanelPluginTrust.parseVersion(null))
        assertNull(PanelPluginTrust.parseVersion(0))
        assertNull(PanelPluginTrust.parseVersion(-1))
        assertNull(PanelPluginTrust.parseVersion("one"))
        assertNull(PanelPluginTrust.parseVersion(1.0f))
    }

    @Test
    fun `a plugin newer than this build is ignored`() {
        assertEquals(1, PanelPluginTrust.sessionVersion(1, supported = 1))
        assertNull(PanelPluginTrust.sessionVersion(2, supported = 1))
        assertNull(PanelPluginTrust.sessionVersion(null, supported = 1))
    }

    @Test
    fun `an older plugin speaks its own version`() {
        assertEquals(1, PanelPluginTrust.sessionVersion(1, supported = 3))
    }

    @Test
    fun `this build speaks the contract's version`() {
        assertEquals(PanelPluginContract.VERSION, PanelPluginTrust.SUPPORTED_VERSION)
    }

    @Test
    fun `digests match in apksigner, keytool or uppercase form`() {
        assertTrue(PanelPluginTrust.isTrusted(listOf(thorKbm)))
        assertTrue(PanelPluginTrust.isTrusted(listOf(thorKbm.uppercase())))
        assertTrue(PanelPluginTrust.isTrusted(listOf(thorKbm.chunked(2).joinToString(":"))))
    }

    @Test
    fun `unknown, missing or mixed signers aren't trusted`() {
        assertFalse(PanelPluginTrust.isTrusted(listOf("00".repeat(32))))
        assertFalse(PanelPluginTrust.isTrusted(emptyList()))
        assertFalse(PanelPluginTrust.isTrusted(listOf(thorKbm, "00".repeat(32))))
    }

    @Test
    fun `ThorKBM's release key is the only allowlisted one`() {
        assertEquals(setOf(thorKbm), PanelPluginTrust.ALLOWED_CERTS)
        assertTrue(thorKbm.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `sha256 hex matches a known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            PanelPluginTrust.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun `the contract's values stay fixed`() {
        assertEquals("jr.brian.commandpanel.plugin.action.PANEL_PLUGIN", PanelPluginContract.ACTION_PANEL_PLUGIN)
        assertEquals("jr.brian.commandpanel.plugin.VERSION", PanelPluginContract.META_VERSION)
        assertEquals(listOf(1, 2, 101, 102), listOf(PanelPluginContract.MSG_SHOW, PanelPluginContract.MSG_HIDE, PanelPluginContract.MSG_SHOWN, PanelPluginContract.MSG_CLOSED))
        assertEquals(3_000L, PanelPluginContract.SHOWN_TIMEOUT_MS)
    }
}
