package jr.brian.commandpannel.replay.studio

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import jr.brian.commandpannel.BuildConfig
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.privileged.ReplayClipsLocation
import jr.brian.commandpannel.privileged.ReplayVolumes
import jr.brian.commandpannel.system.ReplayStorage
import jr.brian.commandpannel.system.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Replay Studio's clip library: finds every saved Replay Buffer clip (Command Panel's and TCP SC's) in the current
 * clips folder and in every folder clips were saved to before ([PREF_KNOWN_DIRS]), and loads durations and
 * thumbnails ([ThumbnailCache]).
 *
 * Clips live on shared storage and are written by root, so listing and deleting go through the privileged shell, the
 * same as saving. Reads try the file directly first and fall back to a root copy in the app cache.
 *
 * All functions are main-safe (they switch to IO themselves). Cheap to create; one per screen is fine.
 */
class ClipRepository(context: Context, private val privileged: PrivilegedAccess) {

    private val app = context.applicationContext ?: context
    private val prefs: SharedPreferences = Prefs.of(app)
    private val thumbs = ThumbnailCache(app.cacheDir)
    private val readDir = File(app.cacheDir, READ_DIR)
    private val decodeDir = File(app.cacheDir, DECODE_DIR)

    /**
     * Absolute folders searched: the default, the current custom one, and every folder a clip was saved to, on
     * internal storage or a removable volume (#75; one that isn't mounted is skipped by the listing's `[ -d ]`).
     */
    fun searchDirs(): List<String> = searchDirs(prefs.getString(PREF_CLIPS_DIR, null), prefs.getStringSet(PREF_KNOWN_DIRS, emptySet()).orEmpty())

    /**
     * Every clip, newest first, from one shell call over [searchDirs] and their Top/Bottom/Both subfolders. `null`
     * when the privileged shell is unavailable (show an unavailable state, not an empty one). Skips files written in
     * the last [MIN_CLIP_AGE_MS] (a save in progress). Clips that exist but can't be opened are still listed;
     * [durationMs] returns `null` for them. Also prunes cached files of clips that are gone.
     */
    suspend fun list(): List<ReplayClip>? = withContext(Dispatchers.IO) {
        val dirs = searchDirs()
        val quoted = dirs.joinToString(" ") { shellQuote(it) }
        // The end marker tells an empty listing from a failed or cut-short call.
        val out = privileged.shell(
            "for d in $quoted; do [ -d \"\$d\" ] && find \"\$d\" -maxdepth 2 -type f -name 'Replay_*.mp4' " +
                "-exec stat -c '%s|%Y|%n' {} + 2>/dev/null; done; echo $END_MARKER",
            LIST_TIMEOUT_MS,
        )
        if (out == null || !out.contains(END_MARKER)) {
            Log.w(TAG, "listing unavailable (shell ${if (out == null) "down" else "cut short"})")
            return@withContext null
        }
        val clips = parseReplayClipListing(out, System.currentTimeMillis())
        val live = clips.mapTo(HashSet()) { replayClipCacheName(it) }
        thumbs.prune(live)
        readDir.listFiles()?.forEach { f -> if (f.nameWithoutExtension !in live) runCatching { f.delete() } }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "listed ${clips.size} clips in ${dirs.size} folders")
            clips.forEach { Log.d(TAG, "clip ${it.origin} ${it.sizeBytes} ${it.path}") }
        }
        clips
    }

    /**
     * A file this app can read for [clip]: the clip itself when readable, else a root copy in the app cache (the
     * [MAX_READ_COPIES] newest are kept; a copy already open in a player keeps playing after it's trimmed). `null`
     * when neither works. Meant for the player; [durationMs] and [thumbnail] handle their own reads.
     */
    suspend fun readableFile(clip: ReplayClip): File? = withContext(Dispatchers.IO) {
        val direct = File(clip.path)
        if (canRead(direct)) return@withContext direct
        val copy = File(readDir, "${replayClipCacheName(clip)}.mp4")
        if (copy.length() == clip.sizeBytes && canRead(copy)) return@withContext copy
        readDir.mkdirs()
        readDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_READ_COPIES - 1)
            ?.forEach { runCatching { it.delete() } }
        rootCopy(clip, copy)
    }

    /** The clip's length, decoded once and cached. `null` when it can't be opened (Studio should hide it). */
    suspend fun durationMs(clip: ReplayClip): Long? = entry(clip)?.durationMs

    /** A cached JPEG of a frame about 1 s in. `null` when no frame could be decoded. */
    suspend fun thumbnail(clip: ReplayClip): File? = entry(clip)?.thumbnail

    /**
     * Deletes [clip] from storage (root `rm`, then a media-scanner broadcast so galleries drop it) and its cached
     * files. Returns whether the file is gone afterward. Refuses paths outside the storage root and non-clip names.
     */
    suspend fun delete(clip: ReplayClip): Boolean = withContext(Dispatchers.IO) {
        if (!isDeletablePath(clip.path)) {
            Log.w(TAG, "refusing to delete ${clip.path}")
            return@withContext false
        }
        val path = shellQuote(clip.path)
        val uri = shellQuote(Uri.fromFile(File(clip.path)).toString())
        val out = privileged.shell(
            "rm -f $path; [ -e $path ] || echo $GONE_MARKER; " +
                "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d $uri >/dev/null 2>&1",
        )
        val gone = out?.contains(GONE_MARKER) == true
        if (gone) {
            thumbs.drop(clip)
            runCatching { File(readDir, "${replayClipCacheName(clip)}.mp4").delete() }
        } else {
            Log.w(TAG, "delete failed for ${clip.path}")
        }
        gone
    }

    private suspend fun entry(clip: ReplayClip): ThumbnailCache.Entry? = withContext(Dispatchers.IO) {
        thumbs.cached(clip) ?: decodeSlots.withPermit { thumbs.cached(clip) ?: decode(clip) }
    }

    private fun decode(clip: ReplayClip): ThumbnailCache.Entry? {
        // Decoding gets its own temporary copy, so trimming the player's copies never pulls one out from under it.
        val direct = File(clip.path)
        val source = if (canRead(direct)) direct else {
            decodeDir.mkdirs()
            rootCopy(clip, File(decodeDir, "${replayClipCacheName(clip)}_${System.nanoTime()}.mp4")) ?: return null
        }
        return try {
            thumbs.decode(clip, source)
        } finally {
            if (source != direct) runCatching { source.delete() }
        }
    }

    /** Root-copies [clip] to [dest] in our cache, owned by our uid. Returns [dest] if we can read it, else `null`. */
    private fun rootCopy(clip: ReplayClip, dest: File): File? {
        val uid = android.os.Process.myUid()
        val dst = shellQuote(dest.absolutePath)
        // The marker, not just a readable file: a copy cut off by the timeout is readable but incomplete.
        val copied = privileged.shell(
            "cp ${shellQuote(clip.path)} $dst && chown $uid:$uid $dst && chmod 600 $dst && " +
                "(restorecon $dst >/dev/null 2>&1; true) && echo $COPIED_MARKER",
            jr.brian.commandpannel.system.ClipCopy.TIMEOUT_MS,
        )?.contains(COPIED_MARKER) == true
        if (copied && canRead(dest)) return dest
        Log.w(TAG, "no readable copy of ${clip.path}")
        runCatching { dest.delete() }
        return null
    }

    private fun canRead(file: File): Boolean =
        runCatching { FileInputStream(file).use { it.read() >= 0 } }.getOrDefault(false)

    private fun isDeletablePath(path: String): Boolean = isDeletableClipPath(path, ReplayStorage.mountedIds(app))

    /** Single-quotes [s] for the shell; folder names may contain an apostrophe. */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    companion object {
        private const val TAG = "ReplayStudio"

        const val PREF_CLIPS_DIR = "replay_clips_dir"

        /** Every clips folder (relative to the storage root) a clip was saved to. Never pruned when the pref clears. */
        const val PREF_KNOWN_DIRS = "replay_known_dirs"

        /** Files modified more recently than this aren't listed yet: a save may still be copying. */
        const val MIN_CLIP_AGE_MS = 3_000L

        /** Root copies kept for the player (two, so both screens can play at once). */
        const val MAX_READ_COPIES = 2

        private const val READ_DIR = "replay_read"
        private const val DECODE_DIR = "replay_decode"
        private const val LIST_TIMEOUT_MS = 10_000
        private const val COPIED_MARKER = "__CP_COPIED__"
        private const val END_MARKER = "__CP_LIST_END__"
        private const val GONE_MARKER = "__CP_GONE__"

        /** Caps concurrent decodes across instances: a grid asks for many clips at once and each holds a decoder. */
        private val decodeSlots = Semaphore(2)

        /** Remembers [location] as a clips folder so its clips stay listed after the user picks another one. */
        fun rememberDir(prefs: SharedPreferences, location: ReplayClipsLocation) {
            val dir = location.encode()
            val known = prefs.getStringSet(PREF_KNOWN_DIRS, emptySet()).orEmpty()
            if (dir in known) return
            prefs.edit().putStringSet(PREF_KNOWN_DIRS, known + dir).apply()
        }

        /**
         * [searchDirs]' rules, pure: TCP Clips, the [current] folder pref and every [known] one, each parsed with
         * [ReplayClipsLocation.parse] (plain folders are internal storage), as absolute paths without duplicates.
         */
        fun searchDirs(current: String?, known: Set<String>): List<String> = buildList {
            add(ReplayClipsLocation.DEFAULT)
            ReplayClipsLocation.parse(current)?.let(::add)
            known.sorted().forEach { raw -> ReplayClipsLocation.parse(raw)?.let(::add) }
        }.map { it.path }.distinct()

        /**
         * Whether root may delete [path]: a clip file name directly under a clips folder on internal storage or on one
         * of the [mounted] removable volumes, with no `.` or `..` segments. Pure.
         */
        fun isDeletableClipPath(path: String, mounted: Collection<String>): Boolean {
            if (ReplayVolumes.storageRoots(mounted).none { path.startsWith("$it/") }) return false
            if (path.split('/').any { it == ".." || it == "." }) return false
            return parseReplayClip(path.substringAfterLast('/'), path.substringBeforeLast('/')) != null
        }
    }
}
