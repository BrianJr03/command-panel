package jr.brian.commandpannel.privileged

import jr.brian.commandpannel.replay.studio.ClipRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayClipsLocationTest {
    private val sd = "3A0D-5000"
    private val clip = "Replay_20261120_101500_Top.mp4"

    private fun ok(docId: String) = (ReplayVolumes.fromTreeDocId(docId) as? ReplayVolumes.Pick.Ok)?.location

    @Test
    fun `an existing internal folder pref reads as internal storage, no migration needed`() {
        val loc = ReplayClipsLocation.parse("Movies/My Clips")!!
        assertNull(loc.volume)
        assertEquals("/storage/emulated/0/Movies/My Clips", loc.path)
        assertEquals("Movies/My Clips", loc.encode())
        assertEquals("/storage/emulated/0/TCP Clips", ReplayClipsLocation.DEFAULT.path)
        assertNull(ReplayClipsLocation.parse(null))
        assertNull(ReplayClipsLocation.parse(""))
    }

    @Test
    fun `a removable location round trips as volume and folder`() {
        val loc = ReplayClipsLocation.of(sd, "/DCIM/Replays/")!!
        assertEquals("DCIM/Replays", loc.relativeDir)
        assertEquals("/storage/3A0D-5000", loc.root)
        assertEquals("/storage/3A0D-5000/DCIM/Replays", loc.path)
        assertEquals("3A0D-5000:DCIM/Replays", loc.encode())
        assertEquals(loc, ReplayClipsLocation.parse(loc.encode()))
        assertTrue(loc.isRemovable)
        // A removable value is invalid to an older version (':' isn't allowed in a folder), which falls back to TCP Clips.
        assertNull(ScreenRecorderEngine.normalizeReplayClipsRelativeDir(loc.encode()))
    }

    @Test
    fun `bad stored values are refused`() {
        for (bad in listOf(
            "3a0d-5000:Clips", "3A0D-50000:Clips", "emulated:Clips", "self:Clips", "primary:Clips", "home:Clips",
            ":Clips", "3A0D-5000:", "3A0D-5000:../emulated/0", "3A0D-5000:Android/data/x", "3A0D-5000:a:b",
            "../3A0D-5000:Clips", "3A0D-5000/x:Clips",
        )) {
            assertNull(bad, ReplayClipsLocation.parse(bad))
        }
    }

    @Test
    fun `only exact removable volume ids are accepted`() {
        for (id in listOf("3A0D-5000", "0000-0000", "ABCD-1234")) assertTrue(id, ReplayVolumes.isRemovableId(id))
        for (id in listOf(null, "", "3a0d-5000", "3A0D5000", "3A0D-500", "3A0D-50000", " 3A0D-5000", "3A0D-5000/",
            "3A0D-5000\n", "emulated", "self", "primary", "0123456789ABCDEF", "..", "3A0D-5000/..")) {
            assertFalse("$id", ReplayVolumes.isRemovableId(id))
        }
    }

    @Test
    fun `picked folders map to clip folders with room for the screen subfolder`() {
        assertEquals(ReplayClipsLocation(null, "Movies/My Clips"), ok("primary:Movies/My Clips"))
        assertEquals(ReplayClipsLocation(null, "TCP Clips"), ok("primary:TCP Clips"))
        assertEquals(ReplayClipsLocation(null, "a/b/c/d/e/f/g"), ok("primary:a/b/c/d/e/f/g"))
        assertEquals(ReplayClipsLocation(sd, "DCIM"), ok("$sd:DCIM"))
        assertEquals(ReplayClipsLocation("ABCD-1234", "Movies/Clips"), ok("ABCD-1234:Movies/Clips"))
        for (bad in listOf(
            "primary:", "primary:Android/data/x", "primary:.hidden", "Movies", "primary:a/b/c/d/e/f/g/h", "",
            ":x", "$sd:", "$sd:Android/obb", "3a0d-5000:DCIM", "raw:/storage/emulated/0/Download", "msf:42",
            "emulated:0/Movies", "$sd:../../emulated/0/Movies",
        )) {
            assertEquals(bad, ReplayVolumes.Pick.Invalid, ReplayVolumes.fromTreeDocId(bad))
        }
    }

    @Test
    fun `folders picked under the Documents shortcut are internal Documents folders`() {
        assertEquals(ReplayClipsLocation(null, "Documents/Clips"), ok("home:Clips"))
        assertEquals(ReplayClipsLocation(null, "Documents/Games/Replays"), ok("home:Games/Replays"))
        assertEquals(ReplayClipsLocation(null, "Documents"), ok("home:"))
        assertEquals("/storage/emulated/0/Documents/Clips", ok("home:Clips")!!.path)
        // Documents takes one level, so the depth limit counts it.
        assertEquals(ReplayClipsLocation(null, "Documents/a/b/c/d/e/f"), ok("home:a/b/c/d/e/f"))
        for (bad in listOf("home:a/b/c/d/e/f/g", "home:../Android/data", "home:.hidden", "home:x/../..", "home:bad:name", "HOME:Clips")) {
            assertEquals(bad, ReplayVolumes.Pick.Invalid, ReplayVolumes.fromTreeDocId(bad))
        }
    }

    @Test
    fun `clip paths on a removable volume are valid only while it is mounted`() {
        val path = "/storage/$sd/DCIM/Replays/Top/$clip"
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath(path, setOf(sd)))
        assertFalse(ScreenRecorderEngine.isValidReplayClipOutputPath(path))
        assertFalse(ScreenRecorderEngine.isValidReplayClipOutputPath(path, setOf("ABCD-1234")))
        // Internal storage stays valid whatever is mounted.
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath("/storage/emulated/0/TCP Clips/Top/$clip", setOf(sd)))
    }

    @Test
    fun `removable clip paths reject traversal, fake ids and anything outside a clips folder`() {
        val mounted = setOf(sd)
        for (bad in listOf(
            "/storage/$sd/../emulated/0/TCP Clips/Top/$clip",
            "/storage/$sd/DCIM/../../emulated/0/Top/$clip",
            "/storage/$sd/./DCIM/$clip",
            "/storage/$sd/$clip",
            "/storage/$sd/Android/data/x/$clip",
            "/storage/$sd/.hidden/$clip",
            "/storage/$sd/DCIM//$clip",
            "/storage/$sd/DCIM/notes.txt",
            "/storage/$sd/DCIM/Replay_evil.mp4",
            "/storage/$sd/DCIM/$clip/x.mp4",
            "/storage/3a0d-5000/DCIM/$clip",
            "/storage/3A0D-50000/DCIM/$clip",
            "/storage/self/primary/DCIM/$clip",
            "/storage/emulated/1/DCIM/$clip",
            "/storage/DCIM/$clip",
            "/mnt/media_rw/$sd/DCIM/$clip",
            "/storage/${sd}x/DCIM/$clip",
            "/storage/$sd",
        )) {
            assertFalse(bad, ScreenRecorderEngine.isValidReplayClipOutputPath(bad, mounted))
        }
        // A bad id passed as "mounted" never becomes a root.
        for (fake in listOf("..", "emulated/0/..", "../data", "3A0D-5000/../..", "")) {
            assertEquals(fake, PrivilegedHelperMain.DEFAULT_STORAGE_ROOTS, ReplayVolumes.storageRoots(setOf(fake)))
        }
        assertFalse(ScreenRecorderEngine.isValidReplayClipOutputPath("/storage/../data/DCIM/$clip", setOf("../data")))
    }

    @Test
    fun `storage roots add only valid mounted ids, once each`() {
        assertEquals(
            PrivilegedHelperMain.DEFAULT_STORAGE_ROOTS + listOf("/storage/3A0D-5000", "/storage/ABCD-1234"),
            ReplayVolumes.storageRoots(listOf("ABCD-1234", "3A0D-5000", "3A0D-5000", "bad")),
        )
    }

    @Test
    fun `a volume is mounted only when removable, not primary and reported mounted`() {
        fun snap(id: String?, state: String, removable: Boolean = true, primary: Boolean = false) =
            ReplayVolumes.Snapshot(id, state, removable, primary)
        val volumes = listOf(
            snap(null, "mounted", removable = false, primary = true),
            snap(sd, "mounted"),
            snap("ABCD-1234", "mounted_ro"),
            snap("1111-2222", "unmounted"),
            snap("3333-4444", "mounted", removable = false),
            snap("5555-6666", "missing"),
        )
        assertEquals(ReplayVolumes.State.MOUNTED, ReplayVolumes.stateOf(sd, volumes))
        assertEquals(ReplayVolumes.State.READ_ONLY, ReplayVolumes.stateOf("ABCD-1234", volumes))
        assertEquals(ReplayVolumes.State.MISSING, ReplayVolumes.stateOf("1111-2222", volumes))
        assertEquals(ReplayVolumes.State.MISSING, ReplayVolumes.stateOf("3333-4444", volumes))
        assertEquals(ReplayVolumes.State.MISSING, ReplayVolumes.stateOf("5555-6666", volumes))
        assertEquals(ReplayVolumes.State.MISSING, ReplayVolumes.stateOf("9999-9999", volumes))
        assertEquals(ReplayVolumes.State.MISSING, ReplayVolumes.stateOf("3a0d-5000", volumes))
        assertEquals(setOf(sd), ReplayVolumes.mountedIds(volumes))
    }

    @Test
    fun `folders are made as root only while the volume root is there`() {
        assertEquals(
            "[ -d '/storage/3A0D-5000' ] && mkdir -p '/storage/3A0D-5000/It'\\''s Clips/Top'",
            ReplayVolumes.mkdirsCommand("/storage/3A0D-5000", "/storage/3A0D-5000/It's Clips/Top"),
        )
    }

    @Test
    fun `Studio lists clips on every known volume and deletes only clip files under a mounted root`() {
        assertEquals(
            listOf("/storage/emulated/0/TCP Clips", "/storage/3A0D-5000/DCIM", "/storage/emulated/0/Movies"),
            ClipRepository.searchDirs("3A0D-5000:DCIM", setOf("Movies", "3A0D-5000:DCIM", "TCP Clips", "bad:x", "../x")),
        )
        assertTrue(ClipRepository.isDeletableClipPath("/storage/$sd/DCIM/Top/$clip", setOf(sd)))
        assertTrue(ClipRepository.isDeletableClipPath("/storage/emulated/0/TCP Clips/Top/$clip", emptySet()))
        assertFalse(ClipRepository.isDeletableClipPath("/storage/$sd/DCIM/Top/$clip", emptySet()))
        assertFalse(ClipRepository.isDeletableClipPath("/storage/$sd/../emulated/0/Top/$clip", setOf(sd)))
        assertFalse(ClipRepository.isDeletableClipPath("/storage/$sd/DCIM/Top/photo.jpg", setOf(sd)))
    }
}
