package jr.brian.commandpannel.privileged

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ReplayRingBufferTest {

    private val keyFlag = 1 // MediaCodec.BUFFER_FLAG_KEY_FRAME

    /** Deterministic packet bytes so slices can be checked for corruption. */
    private fun expectedByte(i: Int, size: Int, isVideo: Boolean): Byte =
        (i * 31 + size + if (isVideo) 0 else 5).toByte()

    private fun payload(@Suppress("UNUSED_PARAMETER") ptsUs: Long, isVideo: Boolean, size: Int): ByteBuffer {
        val buf = ByteBuffer.allocate(size)
        for (i in 0 until size) buf.put(i, expectedByte(i, size, isVideo))
        return buf
    }

    private fun ReplayRingBuffer.video(ptsUs: Long, key: Boolean = false, size: Int = 100) =
        append(true, payload(ptsUs, true, size), 0, size, ptsUs, if (key) keyFlag else 0, key)

    private fun ReplayRingBuffer.audio(ptsUs: Long, size: Int = 20) =
        append(false, payload(ptsUs, false, size), 0, size, ptsUs, 0, false)

    /** 30 fps video with a keyframe every second, 50 audio packets per second, for [seconds]. */
    private fun ReplayRingBuffer.fill(seconds: Int, videoSize: Int = 100) {
        val frameUs = 33_333L
        val frames = seconds * 30
        var audioPts = 0L
        for (f in 0 until frames) {
            val pts = f * frameUs
            video(pts, key = f % 30 == 0, size = videoSize)
            while (audioPts <= pts) {
                audio(audioPts)
                audioPts += 20_000L
            }
        }
    }

    private fun clip(ring: ReplayRingBuffer, windowUs: Long = ring.windowUs) =
        ring.slice(windowUs) as ReplayRingBuffer.Slice.Clip

    @Test
    fun `time eviction keeps window plus keyframe slack`() {
        val ring = ReplayRingBuffer(capacityBytes = 8 * 1024 * 1024, windowUs = 5_000_000L, slackUs = 2_000_000L)
        ring.fill(seconds = 20)
        val buffered = ring.bufferedDurationUs()
        assertTrue("buffered=$buffered", buffered in 6_900_000L..7_000_000L)
    }

    @Test
    fun `byte cap evicts oldest packets when the arena is full`() {
        // 30 fps x 1000 B video + audio ~ 31 KB/s; a 64 KB arena holds only ~2 s
        val ring = ReplayRingBuffer(capacityBytes = 64 * 1024, windowUs = 30_000_000L)
        ring.fill(seconds = 10, videoSize = 1000)
        assertTrue(ring.usedBytes() <= 64 * 1024)
        val buffered = ring.bufferedDurationUs()
        assertTrue("buffered=$buffered", buffered in 1_000_000L..2_200_000L)
        // Newest packets survive
        assertTrue(clip(ring, 1_000_000L).packets.isNotEmpty())
    }

    @Test
    fun `slice starts at the latest keyframe at or before the window start`() {
        val ring = ReplayRingBuffer(capacityBytes = 8 * 1024 * 1024, windowUs = 5_000_000L)
        ring.fill(seconds = 12)
        val c = clip(ring)
        val first = c.packets.first { it.isVideo }
        assertTrue(first.isKeyFrame)
        assertEquals(0L, first.ptsUs)
        // newest video ~ 11.97 s, window start ~ 6.97 s -> keyframe at 6 s -> ~5.97 s clip
        assertTrue("duration=${c.durationUs}", c.durationUs in 5_000_000L..6_100_000L)
    }

    @Test
    fun `slice falls back to the first keyframe after the window start`() {
        val ring = ReplayRingBuffer(capacityBytes = 8 * 1024 * 1024, windowUs = 10_000_000L)
        // Only 3 s buffered, first keyframe at 0.5 s
        for (f in 0 until 90) {
            val pts = f * 33_333L
            ring.video(pts, key = f == 15 || f == 45 || f == 75)
        }
        val c = clip(ring)
        assertEquals(0L, c.packets.first().ptsUs)
        assertTrue(c.packets.first().isKeyFrame)
        assertEquals(75, c.packets.size) // frames 15..89
    }

    @Test
    fun `audio before the first keyframe is dropped and timestamps are rebased`() {
        val ring = ReplayRingBuffer(capacityBytes = 8 * 1024 * 1024, windowUs = 2_000_000L)
        ring.fill(seconds = 6)
        val c = clip(ring)
        assertTrue(c.packets.all { it.ptsUs >= 0L })
        val audio = c.packets.filter { !it.isVideo }
        assertTrue(audio.isNotEmpty())
        assertTrue(c.hasAudio)
        // first audio packet is the first one at or after the keyframe (3.0 s on the 20 ms grid)
        assertTrue(audio.first().ptsUs in 0L..20_000L)
        // per-track timestamps stay monotonic
        for (track in listOf(true, false)) {
            val pts = c.packets.filter { it.isVideo == track }.map { it.ptsUs }
            assertEquals(pts.sorted(), pts)
        }
    }

    @Test
    fun `slice copies packet bytes intact across arena wrap-around`() {
        val ring = ReplayRingBuffer(capacityBytes = 40 * 1024, windowUs = 60_000_000L)
        ring.fill(seconds = 8, videoSize = 700) // forces several wraps
        val c = clip(ring, 1_500_000L)
        assertTrue(c.packets.size > 40)
        for (p in c.packets) {
            for (i in 0 until p.size) {
                assertEquals(expectedByte(i, p.size, p.isVideo), c.data.get(p.offset + i))
            }
        }
    }

    @Test
    fun `empty buffer and missing keyframe return clear errors`() {
        val ring = ReplayRingBuffer(capacityBytes = 1024 * 1024, windowUs = 5_000_000L)
        assertEquals(
            ReplayRingBuffer.Slice.Failure(ReplayRingBuffer.SliceError.EMPTY),
            ring.slice()
        )
        ring.audio(0L)
        assertEquals(
            ReplayRingBuffer.Slice.Failure(ReplayRingBuffer.SliceError.EMPTY),
            ring.slice()
        )
        repeat(10) { ring.video(it * 33_333L, key = false) }
        assertEquals(
            ReplayRingBuffer.Slice.Failure(ReplayRingBuffer.SliceError.NO_KEYFRAME),
            ring.slice()
        )
    }

    @Test
    fun `oversized packets are rejected and clear empties the ring`() {
        val ring = ReplayRingBuffer(capacityBytes = 1024, windowUs = 5_000_000L)
        assertFalse(ring.video(0L, key = true, size = 2048))
        assertTrue(ring.video(0L, key = true, size = 512))
        assertEquals(512L, ring.usedBytes())
        ring.clear()
        assertEquals(0L, ring.usedBytes())
        assertEquals(0, ring.packetCount())
    }

    @Test
    fun `replay clip path validation only accepts Replay_ names in allowed folders`() {
        val good = ScreenRecorderEngine.buildReplayClipPath("20261114_101500", ScreenRecorderEngine.TARGET_BOTH)
        assertEquals("/storage/emulated/0/TCP Clips/Replay_20261114_101500_Both.mp4", good)
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath(good))
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath("/sdcard/game_clips/Replay_20261114_101500_Top.mp4"))
        val bad = listOf(
            "/storage/emulated/0/game_clips/ScreenRecord_20261114_101500_Top.mp4",
            "/storage/emulated/0/game_clips/Replay_evil.mp4",
            "/storage/emulated/0/game_clips/../Replay_20261114_101500_Top.mp4",
            "/storage/emulated/0/Replay_20261114_101500_Top.mp4",
            "/data/local/tmp/Replay_20261114_101500_Top.mp4",
            "/storage/emulated/0/game_clips/Replay_20261114_101500_Top.mp4.png"
        )
        for (path in bad) assertFalse(path, ScreenRecorderEngine.isValidReplayClipOutputPath(path))
    }

    @Test
    fun `user clip folders are validated and confined to internal shared storage`() {
        val norm = ScreenRecorderEngine::normalizeReplayClipsRelativeDir
        assertEquals("game_clips", norm("game_clips"))
        assertEquals("Movies/My Clips", norm("/Movies/My Clips/"))
        assertEquals("DCIM/Replays (Thor)", norm("DCIM/Replays (Thor)"))
        for (bad in listOf("", "/", "..", "Movies/../..", "Movies/./x", ".hidden", "Movies/.nomedia",
            "Android/data/com.evil", "android/obb", "a/b/c/d/e/f/g/h/i", "bad:name", "tab\tname", "x".repeat(65))) {
            assertEquals(bad, null, norm(bad))
        }

        val custom = ScreenRecorderEngine.buildReplayClipPath(
            "20261114_101500",
            ScreenRecorderEngine.TARGET_TOP,
            baseDir = "/storage/emulated/0/Movies/My Clips"
        )
        assertEquals("/storage/emulated/0/Movies/My Clips/Replay_20261114_101500_Top.mp4", custom)
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath(custom))
        assertTrue(ScreenRecorderEngine.isValidReplayClipOutputPath("/sdcard/DCIM/Replays/Replay_20261114_101500_Both.mp4"))
        for (bad in listOf(
            "/storage/emulated/0/Replay_20261114_101500_Top.mp4",
            "/storage/emulated/0/Android/data/x/Replay_20261114_101500_Top.mp4",
            "/storage/emulated/0/Movies//Replay_20261114_101500_Top.mp4",
            "/storage/emulated/0/.secret/Replay_20261114_101500_Top.mp4",
            "/storage/emulated/1/Movies/Replay_20261114_101500_Top.mp4"
        )) {
            assertFalse(bad, ScreenRecorderEngine.isValidReplayClipOutputPath(bad))
        }
    }

    @Test
    fun `symlinked clip folder escaping shared storage is rejected`() {
        val root = java.nio.file.Files.createTempDirectory("bdsa-replay-root").toFile()
        val outside = java.nio.file.Files.createTempDirectory("bdsa-replay-outside").toFile()
        try {
            java.nio.file.Files.createSymbolicLink(java.io.File(root, "clips").toPath(), outside.toPath())
            val path = "${root.path}/clips/Replay_20261114_101500_Top.mp4"
            assertFalse(
                PrivilegedHelperMain.isValidScopedMediaOutputPath(
                    path, listOf("clips"), ".mp4", storageRoots = listOf(root.path)
                )
            )
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun `ram estimates follow presets and respect the 160 MB ceiling`() {
        val top = ScreenRecorderEngine.TARGET_TOP
        val both = ScreenRecorderEngine.TARGET_BOTH
        fun mb(target: Int, preset: ReplayBitratePreset, window: Int) =
            ScreenRecorderEngine.estimateReplayBufferBytes(target, 60, preset, window, withAudio = true) / (1024 * 1024)

        assertEquals(8_000_000, ScreenRecorderEngine.replayVideoBitRate(top, 60, ReplayBitratePreset.BALANCED))
        // (8 + 0.192) Mbps / 8 x (30 + 2) s x 1.25 = ~39 MiB
        assertEquals(39L, mb(top, ReplayBitratePreset.BALANCED, 30))
        assertTrue(mb(top, ReplayBitratePreset.LOW, 30) < mb(top, ReplayBitratePreset.BALANCED, 30))
        assertEquals(
            ScreenRecorderEngine.MAX_REPLAY_BUFFER_BYTES,
            ScreenRecorderEngine.estimateReplayBufferBytes(both, 60, ReplayBitratePreset.HIGH, 90, true)
        )
        assertEquals(
            30,
            ScreenRecorderEngine.effectiveReplayWindowSeconds(top, 60, ReplayBitratePreset.BALANCED, 30, true)
        )
        val cappedWindow = ScreenRecorderEngine.effectiveReplayWindowSeconds(both, 60, ReplayBitratePreset.HIGH, 90, true)
        assertTrue("capped=$cappedWindow", cappedWindow in 35..45)
        assertEquals(30, ScreenRecorderEngine.normalizeReplayWindowSeconds(31))
        assertEquals(90, ScreenRecorderEngine.normalizeReplayWindowSeconds(500))
        assertEquals(15, ScreenRecorderEngine.normalizeReplayWindowSeconds(-3))
    }

    @Test
    fun `bitrate presets round trip with a safe default`() {
        for (p in ReplayBitratePreset.entries) assertEquals(p, ReplayBitratePreset.fromKey(p.key))
        assertEquals(ReplayBitratePreset.BALANCED, ReplayBitratePreset.fromKey("ultra"))
    }

    @Test
    fun `picked folders map to clip folders with room for the screen subfolder`() {
        val pick = ScreenRecorderEngine::replayClipsDirFromTreeDocId
        assertEquals("Movies/My Clips", pick("primary:Movies/My Clips"))
        assertEquals("TCP Clips", pick("primary:TCP Clips"))
        for (bad in listOf("primary:", "1234-5678:DCIM", "primary:Android/data/x", "primary:.hidden", "Movies", "primary:a/b/c/d/e/f/g/h")) {
            assertEquals(bad, null, pick(bad))
        }
        assertEquals("a/b/c/d/e/f/g", pick("primary:a/b/c/d/e/f/g"))
    }

    @Test
    fun `a folder on an SD card or USB drive is told apart from other refused folders`() {
        val other = ScreenRecorderEngine::isOtherVolumeTreeDocId
        for (sd in listOf("1A2B-3C4D:DCIM", "1A2B-3C4D:", "ABCD-1234:Movies/Clips")) {
            assertTrue(sd, other(sd))
            assertEquals(sd, null, ScreenRecorderEngine.replayClipsDirFromTreeDocId(sd))
        }
        for (notSd in listOf("primary:Movies", "primary:", "primary:Android/data/x", "home:Clips", "Movies", "", ":x")) {
            assertFalse(notSd, other(notSd))
        }
    }

    @Test
    fun `clips go in a per-screen subfolder that passes validation`() {
        val modes = mapOf(
            ScreenRecorderEngine.TARGET_TOP to "Top",
            ScreenRecorderEngine.TARGET_BOTTOM to "Bottom",
            ScreenRecorderEngine.TARGET_BOTH to "Both",
        )
        for ((mode, folder) in modes) {
            assertEquals(folder, ScreenRecorderEngine.replayTargetFolder(mode))
            for (dir in listOf(ScreenRecorderEngine.DEFAULT_REPLAY_CLIPS_RELATIVE_DIR, "a/b/c/d/e/f/g")) {
                val path = ScreenRecorderEngine.buildReplayClipPath(
                    "20261118_130500", mode, baseDir = "${ScreenRecorderEngine.REPLAY_STORAGE_ROOT}/$dir/$folder",
                )
                assertTrue(path, ScreenRecorderEngine.isValidReplayClipOutputPath(path))
            }
        }
    }
}
