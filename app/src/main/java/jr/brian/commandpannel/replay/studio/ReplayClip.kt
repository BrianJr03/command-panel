package jr.brian.commandpannel.replay.studio

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The screen(s) a clip recorded. A [BOTH] clip has both screens stacked in one frame (top above bottom). */
enum class ClipOrigin { TOP, BOTTOM, BOTH }

/**
 * One saved Replay Buffer clip on shared storage, as Replay Studio lists it. Build one with [parseReplayClip].
 *
 * @property path absolute path, e.g. `/storage/emulated/0/TCP Clips/Top/Replay_20261119_210300_Top.mp4`.
 * @property name the file name.
 * @property origin the screen(s) it recorded, from the name's `_Top` / `_Bottom` / `_Both` suffix. The suffix wins
 * when the clip sits in another screen's subfolder (moved by hand): it was written at save time.
 * @property recordedAtMs when it was saved, from the timestamp in [name] (device time zone).
 * @property sizeBytes file size, 0 when not from a listing.
 * @property modifiedMs file modification time in ms, 0 when not from a listing.
 * @property isTrim a trimmed copy (`..._trim.mp4`) made by Studio.
 */
data class ReplayClip(
    val path: String,
    val name: String,
    val origin: ClipOrigin,
    val recordedAtMs: Long,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val isTrim: Boolean,
) {
    /** The folder holding the clip (no trailing slash). */
    val dir: String get() = path.substringBeforeLast('/')

    /** Stable identity for lists and caches: the path plus size and mtime, so a rewritten file counts as new. */
    val key: String get() = "$path|$sizeBytes|$modifiedMs"
}

/**
 * Clip file names: `Replay_<yyyyMMdd>_<HHmmss>[_<n>]_<Top|Bottom|Both>[_trim].mp4`. Covers Command Panel's saves,
 * TCP SC's (same naming), a numbered duplicate and Studio's trimmed copies.
 */
private val CLIP_NAME = Regex("^Replay_([0-9]{8})_([0-9]{6})(?:_[0-9]+)?_(Top|Bottom|Both)(_trim)?\\.mp4$")

/**
 * Parses a clip file [name] in folder [dir], or returns `null` for anything that isn't a clip (other files, bad
 * dates). [sizeBytes] and [modifiedMs] are carried through as-is. Pure; [timeZone] is for tests.
 */
fun parseReplayClip(
    name: String,
    dir: String,
    sizeBytes: Long = 0L,
    modifiedMs: Long = 0L,
    timeZone: TimeZone = TimeZone.getDefault(),
): ReplayClip? {
    val m = CLIP_NAME.matchEntire(name) ?: return null
    val (date, time, suffix, trim) = m.destructured
    val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        isLenient = false
        this.timeZone = timeZone
    }
    val captured = try {
        format.parse(date + time)?.time
    } catch (_: ParseException) {
        null
    } ?: return null
    val origin = when (suffix) {
        "Top" -> ClipOrigin.TOP
        "Bottom" -> ClipOrigin.BOTTOM
        else -> ClipOrigin.BOTH
    }
    return ReplayClip(
        path = "${dir.trimEnd('/')}/$name",
        name = name,
        origin = origin,
        recordedAtMs = captured,
        sizeBytes = sizeBytes,
        modifiedMs = modifiedMs,
        isTrim = trim.isNotEmpty(),
    )
}

/** Newest first: by save time, then the newer file (a trim after its source), then name for a stable order. */
fun sortReplayClips(clips: List<ReplayClip>): List<ReplayClip> =
    clips.sortedWith(
        compareByDescending<ReplayClip> { it.recordedAtMs }
            .thenByDescending { it.modifiedMs }
            .thenBy { it.name },
    )

/**
 * Parses the listing shell output: one `<size>|<mtime seconds>|<path>` line per file (`|` can't appear in a clip
 * folder, see `ScreenRecorderEngine.normalizeReplayClipsRelativeDir`). Drops non-clips, duplicates, and files
 * modified within [minAgeMs] before [nowMs] (a save may still be copying; mtimes are whole seconds, so the real
 * margin can be up to a second less). Returns them newest first. Pure.
 */
fun parseReplayClipListing(
    output: String,
    nowMs: Long,
    minAgeMs: Long = ClipRepository.MIN_CLIP_AGE_MS,
    timeZone: TimeZone = TimeZone.getDefault(),
): List<ReplayClip> {
    val clips = output.lineSequence().mapNotNull { line ->
        val parts = line.trim().split('|', limit = 3)
        if (parts.size != 3) return@mapNotNull null
        val size = parts[0].toLongOrNull() ?: return@mapNotNull null
        val modifiedMs = (parts[1].toLongOrNull() ?: return@mapNotNull null) * 1000L
        // A future mtime (clock changed) still lists; only a fresh one is held back.
        if (nowMs - modifiedMs in 0 until minAgeMs) return@mapNotNull null
        val path = parts[2]
        val slash = path.lastIndexOf('/')
        if (slash <= 0) return@mapNotNull null
        parseReplayClip(path.substring(slash + 1), path.substring(0, slash), size, modifiedMs, timeZone)
    }.distinctBy { it.path }.toList()
    return sortReplayClips(clips)
}

/** File-name stem for a clip's cached files (thumbnail, details, readable copy). Changes when the file does. */
fun replayClipCacheName(clip: ReplayClip): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1").digest(clip.key.toByteArray())
    return digest.take(10).joinToString("") { "%02x".format(it) }
}
