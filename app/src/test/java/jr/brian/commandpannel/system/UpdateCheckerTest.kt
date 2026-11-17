package jr.brian.commandpannel.system

import jr.brian.commandpannel.system.update.UpdateChecker
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer versions compare numerically`() {
        assertTrue(UpdateChecker.isNewer("0.1.1", "0.1.0"))
        assertTrue(UpdateChecker.isNewer("0.2", "0.1.9"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.10"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.0"))
    }

    @Test
    fun `same or older versions aren't updates`() {
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.1", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("0.1.0", "0.1.0-debug"))
        assertFalse(UpdateChecker.isNewer("0.0.9", "0.1.0"))
        assertFalse(UpdateChecker.isNewer("", "0.1.0"))
    }

    @Test
    fun `parses a release and picks the release APK`() {
        val json = JSONObject(
            """{"tag_name":"v0.2.0","body":"## Command Panel v0.2.0","html_url":"https://github.com/x/y/releases/tag/v0.2.0",
               "assets":[
                 {"name":"command-panel-0.2.0-debug.apk","browser_download_url":"https://example.com/debug.apk","size":10},
                 {"name":"notes.txt","browser_download_url":"https://example.com/notes.txt","size":1},
                 {"name":"command-panel-0.2.0.apk","browser_download_url":"https://example.com/release.apk","size":12345}
               ]}""",
        )
        val r = UpdateChecker.parse(json)
        assertEquals("0.2.0", r.version)
        assertEquals("https://example.com/release.apk", r.apkUrl)
        assertEquals("command-panel-0.2.0.apk", r.apkName)
        assertEquals(12345L, r.apkSize)
    }

    @Test
    fun `a release without an APK, or with an http link, has no download`() {
        assertNull(UpdateChecker.parse(JSONObject("""{"tag_name":"v0.2.0","assets":[]}""")).apkUrl)
        val http = JSONObject("""{"tag_name":"0.2.0","assets":[{"name":"command-panel-0.2.0.apk","browser_download_url":"http://x/a.apk"}]}""")
        assertNull(UpdateChecker.parse(http).apkUrl)
    }

    @Test
    fun `bundled notes match only their own version`() {
        val notes = "## Command Panel v0.1.0\n\nFirst packaged build."
        assertTrue(UpdateChecker.notesMatch(notes, "0.1.0"))
        assertTrue(UpdateChecker.notesMatch(notes, "0.1.0-debug"))
        assertFalse(UpdateChecker.notesMatch(notes, "0.1.1"))
        assertFalse(UpdateChecker.notesMatch(notes, "0.1"))
        assertFalse(UpdateChecker.notesMatch("no heading here", "0.1.0"))
    }
}
