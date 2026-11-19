package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.update.SaveClipInstaller
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
