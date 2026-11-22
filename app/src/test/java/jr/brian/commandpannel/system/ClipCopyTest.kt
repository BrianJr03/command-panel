package jr.brian.commandpannel.system

import jr.brian.commandpannel.privileged.helper.ShellRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [ClipCopy]'s commands against the host's `/bin/sh`. */
class ClipCopyTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun sh(cmd: String) = ShellRunner.run(listOf("/bin/sh", "-c", cmd), 5_000).second

    private fun part(dst: File) = File(dst.parentFile, ".${dst.name}.part")

    @Test
    fun aFinishedCopyIsInPlaceWithNoPartialFile() {
        val src = folder.newFile("tmp.mp4").apply { writeText("clip") }
        val dst = File(folder.newFolder("it's clips"), "Replay_20261121_120000_Top.mp4")
        assertTrue(sh(ClipCopy.command(src.path, dst.path, "OK")).contains("OK"))
        assertEquals("clip", dst.readText())
        assertFalse(part(dst).exists())
    }

    @Test
    fun anExistingClipIsNeverOverwritten() {
        val src = folder.newFile("tmp.mp4").apply { writeText("new") }
        val dst = folder.newFile("Replay_20261121_120000_Top.mp4").apply { writeText("old") }
        assertFalse(sh(ClipCopy.command(src.path, dst.path, "OK")).contains("OK"))
        assertEquals("old", dst.readText())
    }

    @Test
    fun aFailedCopyLeavesNoClipAndCleanupRemovesThePartialFile() {
        val dst = File(folder.root, "Replay_20261121_120000_Top.mp4")
        assertFalse(sh(ClipCopy.command("/no/such/file", dst.path, "OK")).contains("OK"))
        assertFalse(dst.exists())
        part(dst).writeText("partial")
        sh(ClipCopy.cleanupCommand(dst.path))
        assertFalse(part(dst).exists())
    }
}
