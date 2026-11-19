package jr.brian.commandpannel.system

import jr.brian.commandpannel.plugin.PanelPluginTrust
import jr.brian.commandpannel.system.update.ThorKbmInstaller
import jr.brian.commandpannel.system.update.ThorKbmStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThorKbmInstallerTest {

    private fun release(tag: String, vararg assets: Pair<String, String>) = JSONObject().put("tag_name", tag).put(
        "assets",
        JSONArray(assets.map { (name, url) -> JSONObject().put("name", name).put("browser_download_url", url).put("size", 2623588) }),
    )

    /** Trimmed from `releases/latest` of BrianJr03/Thor-Ergo-Android (v2.2.1). */
    private val latest = """
        {
          "tag_name": "v2.2.1",
          "name": "ThorKBM v2.2.1",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "ThorKBM-v2.2.1.apk",
              "size": 2623588,
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "https://github.com/BrianJr03/Thor-Ergo-Android/releases/download/v2.2.1/ThorKBM-v2.2.1.apk"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `reads the APK and version from the latest release`() {
        val r = ThorKbmInstaller.parseRelease(JSONObject(latest))
        assertEquals("2.2.1", r?.version)
        assertEquals("https://github.com/BrianJr03/Thor-Ergo-Android/releases/download/v2.2.1/ThorKBM-v2.2.1.apk", r?.url)
        assertEquals(2623588L, r?.size)
    }

    @Test
    fun `only the APK named for the release tag counts`() {
        assertNull(ThorKbmInstaller.parseRelease(release("v2.2.1", "ThorKBM-v2.2.0.apk" to "https://github.com/x/a.apk")))
        assertNull(ThorKbmInstaller.parseRelease(release("v2.2.1", "app-release.apk" to "https://github.com/x/a.apk")))
        assertNull(ThorKbmInstaller.parseRelease(release("v2.2.1")))
    }

    @Test
    fun `a missing or odd tag has no release`() {
        assertNull(ThorKbmInstaller.parseRelease(JSONObject()))
        assertNull(ThorKbmInstaller.parseRelease(release("latest", "ThorKBM-vlatest.apk" to "https://github.com/x/a.apk")))
        assertNull(ThorKbmInstaller.parseRelease(release("v2.2.1/../x", "ThorKBM-v2.2.1/../x.apk" to "https://github.com/x/a.apk")))
    }

    @Test
    fun `a suffixed tag still matches its APK`() {
        assertEquals(
            "2.3.0-beta1",
            ThorKbmInstaller.parseRelease(release("v2.3.0-beta1", "ThorKBM-v2.3.0-beta1.apk" to "https://github.com/x/a.apk"))?.version,
        )
    }

    @Test
    fun `only https downloads are used`() {
        assertNull(ThorKbmInstaller.parseRelease(release("v2.2.1", "ThorKBM-v2.2.1.apk" to "http://github.com/x/a.apk")))
    }

    @Test
    fun `a ThorKBM APK signed with its release key passes`() {
        ThorKbmInstaller.check("jr.brian.thorkbm", listOf(PanelPluginTrust.THORKBM_CERT_SHA256), "2.2.1", "2.2.1")
    }

    @Test
    fun `another package, key or version is rejected`() {
        val key = listOf(PanelPluginTrust.THORKBM_CERT_SHA256)
        assertThrows(IllegalArgumentException::class.java) { ThorKbmInstaller.check("jr.brian.thorkbm.debug", key, "2.2.1", "2.2.1") }
        assertThrows(IllegalArgumentException::class.java) { ThorKbmInstaller.check("jr.brian.commandpannel", key, "2.2.1", "2.2.1") }
        assertThrows(IllegalArgumentException::class.java) { ThorKbmInstaller.check("jr.brian.thorkbm", listOf("00".repeat(32)), "2.2.1", "2.2.1") }
        assertThrows(IllegalArgumentException::class.java) { ThorKbmInstaller.check("jr.brian.thorkbm", emptyList(), "2.2.1", "2.2.1") }
        assertThrows(IllegalArgumentException::class.java) { ThorKbmInstaller.check("jr.brian.thorkbm", key, "2.2.0", "2.2.1") }
    }

    @Test
    fun `a debug build alone leaves the release installable, and Open uses it`() {
        val status = ThorKbmStatus(releaseVersion = null, debugVersion = "2.3.0-debug")
        assertFalse(status.releaseInstalled)
        assertEquals("jr.brian.thorkbm.debug", status.openPackage)
    }

    @Test
    fun `Open prefers the release when both are installed`() {
        val status = ThorKbmStatus(releaseVersion = "2.2.1", debugVersion = "2.3.0-debug")
        assertTrue(status.releaseInstalled)
        assertEquals("jr.brian.thorkbm", status.openPackage)
    }

    @Test
    fun `nothing installed has nothing to open`() {
        assertFalse(ThorKbmStatus().releaseInstalled)
        assertNull(ThorKbmStatus().openPackage)
    }

    @Test
    fun `asset name follows ThorKBM's release naming`() {
        assertEquals("ThorKBM-v2.2.1.apk", ThorKbmInstaller.assetName("2.2.1"))
    }
}
