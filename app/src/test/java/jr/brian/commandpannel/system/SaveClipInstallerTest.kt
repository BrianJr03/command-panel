package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.update.SaveClipInstaller
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SaveClipInstallerTest {

    private fun release(vararg assets: Pair<String, String>) = JSONObject().put(
        "assets",
        org.json.JSONArray(assets.map { (name, url) -> JSONObject().put("name", name).put("browser_download_url", url).put("size", 20126) }),
    )

    @Test
    fun `finds the trigger next to the app APK`() {
        val asset = SaveClipInstaller.parseAsset(
            release(
                "command-panel-0.7.1.apk" to "https://github.com/x/command-panel-0.7.1.apk",
                "tcp-sc-0.7.1.apk" to "https://github.com/x/tcp-sc-0.7.1.apk",
            ),
            "0.7.1",
        )
        assertEquals("https://github.com/x/tcp-sc-0.7.1.apk", asset?.url)
        assertEquals(20126L, asset?.size)
    }

    @Test
    fun `a release without the trigger has no asset`() {
        assertNull(SaveClipInstaller.parseAsset(release("command-panel-0.7.0.apk" to "https://github.com/x/a.apk"), "0.7.0"))
        assertNull(SaveClipInstaller.parseAsset(JSONObject(), "0.7.0"))
    }

    @Test
    fun `only the trigger for this version counts`() {
        assertNull(SaveClipInstaller.parseAsset(release("tcp-sc-0.7.1.apk" to "https://github.com/x/tcp-sc-0.7.1.apk"), "0.7.2"))
    }

    @Test
    fun `only https downloads are used`() {
        assertNull(SaveClipInstaller.parseAsset(release("tcp-sc-0.7.1.apk" to "http://github.com/x/tcp-sc-0.7.1.apk"), "0.7.1"))
    }

    private val ours = "ab".repeat(32)
    private val theirs = "cd".repeat(32)

    @Test
    fun `accepts Save Clip signed with this build's key`() {
        SaveClipInstaller.check(SaveClipInstaller.PACKAGE, listOf(ours), listOf(ours))
        // Digests compare however they're written (colons, case).
        SaveClipInstaller.check(SaveClipInstaller.PACKAGE, listOf(ours), listOf(ours.uppercase().chunked(2).joinToString(":")))
    }

    @Test
    fun `refuses another key, an extra key, another package or an unreadable own key`() {
        assertThrows(IllegalArgumentException::class.java) {
            SaveClipInstaller.check(SaveClipInstaller.PACKAGE, listOf(theirs), listOf(ours))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaveClipInstaller.check(SaveClipInstaller.PACKAGE, listOf(ours, theirs), listOf(ours))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaveClipInstaller.check(SaveClipInstaller.PACKAGE, emptyList(), listOf(ours))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaveClipInstaller.check("com.example.other", listOf(ours), listOf(ours))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaveClipInstaller.check(SaveClipInstaller.PACKAGE, listOf(ours), emptyList())
        }
    }
}
