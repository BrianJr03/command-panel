package jr.brian.commandpannel.replay.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipSavedNoticeTest {

    @Test
    fun `a saved path is announced`() {
        val path = "/storage/emulated/0/Movies/TCP Clips/Top/clip_20261119_120000.mp4"
        assertEquals(path, ClipSavedNotice.savedPath(path))
        assertEquals(path, ClipSavedNotice.savedPath("$path\n"))
    }

    @Test
    fun `errors and empty results are not`() {
        assertNull(ClipSavedNotice.savedPath("error:write_failed"))
        assertNull(ClipSavedNotice.savedPath(""))
        assertNull(ClipSavedNotice.savedPath("  "))
        assertNull(ClipSavedNotice.savedPath(null))
    }

    @Test
    fun `the notification names the clip file`() {
        assertEquals("clip_2.mp4", ClipSavedNotice.displayName("/storage/emulated/0/Movies/TCP Clips/Both/clip_2.mp4"))
        assertEquals("clip.mp4", ClipSavedNotice.displayName("clip.mp4"))
    }
}
