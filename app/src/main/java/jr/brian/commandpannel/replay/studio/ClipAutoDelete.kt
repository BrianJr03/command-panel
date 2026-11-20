package jr.brian.commandpannel.replay.studio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.system.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The "Auto-delete old clips" settings (Replay Buffer section). Off by default. With [enabled] on, each rule applies
 * only while its own switch is on: [ageOn] removes clips older than [ageDays], [sizeOn] keeps the clips under
 * [sizeGb] in total.
 */
data class AutoDeleteRules(
    val enabled: Boolean = false,
    val ageOn: Boolean = true,
    val ageDays: Int = DEFAULT_AGE_DAYS,
    val sizeOn: Boolean = false,
    val sizeGb: Int = DEFAULT_SIZE_GB,
) {
    /** The age limit in force, or `null` when there is none. */
    val maxAgeDays: Int? get() = ageDays.takeIf { enabled && ageOn && it > 0 }

    /** The size cap in force, in bytes, or `null` when there is none. */
    val maxTotalBytes: Long? get() = sizeGb.takeIf { enabled && sizeOn && it > 0 }?.let { it * BYTES_PER_GB }

    /** Whether any rule would delete something. */
    val active: Boolean get() = maxAgeDays != null || maxTotalBytes != null

    fun save(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean(PREF_ENABLED, enabled)
            .putBoolean(PREF_AGE_ON, ageOn)
            .putInt(PREF_AGE_DAYS, ageDays)
            .putBoolean(PREF_SIZE_ON, sizeOn)
            .putInt(PREF_SIZE_GB, sizeGb)
            .apply()
    }

    companion object {
        const val PREF_ENABLED = "replay_auto_delete"
        const val PREF_AGE_ON = "replay_auto_delete_age_on"
        const val PREF_AGE_DAYS = "replay_auto_delete_age_days"
        const val PREF_SIZE_ON = "replay_auto_delete_size_on"
        const val PREF_SIZE_GB = "replay_auto_delete_size_gb"

        /** The chip presets in settings. */
        val AGE_DAY_PRESETS = listOf(7, 14, 30, 90)
        val SIZE_GB_PRESETS = listOf(5, 10, 25, 50)

        const val DEFAULT_AGE_DAYS = 30
        const val DEFAULT_SIZE_GB = 25

        /** Decimal gigabytes, the unit Android's storage screen uses. */
        const val BYTES_PER_GB = 1_000_000_000L

        fun read(prefs: SharedPreferences) = AutoDeleteRules(
            enabled = prefs.getBoolean(PREF_ENABLED, false),
            ageOn = prefs.getBoolean(PREF_AGE_ON, true),
            ageDays = prefs.getInt(PREF_AGE_DAYS, DEFAULT_AGE_DAYS),
            sizeOn = prefs.getBoolean(PREF_SIZE_ON, false),
            sizeGb = prefs.getInt(PREF_SIZE_GB, DEFAULT_SIZE_GB),
        )
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * When a clip was made, for the age rule and the oldest-first order: the later of its file time and the time in its
 * name, so a clip only counts as old when both say so (a trimmed copy is as new as its file; an old clip copied back
 * in by hand isn't removed the moment it lands).
 */
internal fun clipAgeTimeMs(clip: ReplayClip): Long = maxOf(clip.modifiedMs, clip.recordedAtMs)

/**
 * Which [clips] the auto-delete rules remove, oldest first. Pure.
 *
 * - [maxAgeDays]: anything made more than that many days before [nowMs] goes.
 * - [maxTotalBytes]: then the oldest remaining clips go until the total is at or under the cap.
 * - [keepPath] (the clip just saved) is never picked, but still counts toward the total.
 *
 * Trimmed copies count like any other clip. `null` rules are off; with both off nothing is picked.
 */
fun pickClipsToDelete(
    clips: List<ReplayClip>,
    nowMs: Long,
    maxAgeDays: Int?,
    maxTotalBytes: Long?,
    keepPath: String? = null,
): List<ReplayClip> {
    if (maxAgeDays == null && maxTotalBytes == null) return emptyList()
    val oldestFirst = clips.distinctBy { it.path }.sortedWith(
        compareBy<ReplayClip> { clipAgeTimeMs(it) }.thenBy { it.recordedAtMs }.thenBy { it.name },
    )
    val picked = LinkedHashSet<String>()
    if (maxAgeDays != null) {
        val cutoff = nowMs - maxAgeDays * DAY_MS
        oldestFirst.filter { it.path != keepPath && clipAgeTimeMs(it) < cutoff }.mapTo(picked) { it.path }
    }
    if (maxTotalBytes != null) {
        var total = oldestFirst.filter { it.path !in picked }.sumOf { it.sizeBytes.coerceAtLeast(0) }
        for (clip in oldestFirst) {
            if (total <= maxTotalBytes) break
            if (clip.path == keepPath || clip.path in picked) continue
            picked += clip.path
            total -= clip.sizeBytes.coerceAtLeast(0)
        }
    }
    return oldestFirst.filter { it.path in picked }
}

/**
 * Runs the auto-delete rules over the clip library ([ClipRepository]): after each save (from
 * `ReplayBufferController.save`, see [afterSave]) and when Replay Studio opens. Only clip files are ever removed
 * ([ClipRepository.delete] refuses anything else). Logs what it removed under `ReplayStudio`.
 */
class ClipAutoDelete(context: Context, privileged: PrivilegedAccess) {

    private val app = context.applicationContext ?: context
    private val prefs = Prefs.of(app)
    private val repo = ClipRepository(app, privileged)

    /** Applies the saved rules. Returns the clips removed (empty when the rules are off or the shell is down). */
    suspend fun run(justSavedPath: String? = null, justSavedBytes: Long = 0L): List<ReplayClip> {
        val rules = AutoDeleteRules.read(prefs)
        if (!rules.active) return emptyList()
        return run(rules.maxAgeDays, rules.maxTotalBytes, justSavedPath, justSavedBytes)
    }

    /** Applies the given limits (debug scripts pass their own). */
    suspend fun run(
        maxAgeDays: Int?,
        maxTotalBytes: Long?,
        justSavedPath: String? = null,
        justSavedBytes: Long = 0L,
    ): List<ReplayClip> = lock.withLock {
        val listed = repo.list()
        if (listed == null) {
            Log.w(TAG, "auto-delete skipped: clips can't be listed")
            return@withLock emptyList()
        }
        val now = System.currentTimeMillis()
        // A clip saved seconds ago isn't listed yet (see ClipRepository.MIN_CLIP_AGE_MS) but takes up space.
        val justSaved = justSavedPath?.takeIf { p -> listed.none { it.path == p } }?.let { p ->
            parseReplayClip(p.substringAfterLast('/'), p.substringBeforeLast('/'), justSavedBytes, now)
        }
        val clips = if (justSaved != null) listed + justSaved else listed
        val doomed = pickClipsToDelete(clips, now, maxAgeDays, maxTotalBytes, justSavedPath)
        if (doomed.isEmpty()) return@withLock emptyList()
        val removed = doomed.filter { repo.delete(it) }
        Log.i(
            TAG,
            "auto-delete (age=${maxAgeDays ?: "off"}d, cap=${maxTotalBytes ?: "off"}B) removed " +
                "${removed.size}/${doomed.size} of ${clips.size} clips",
        )
        removed.forEach { Log.i(TAG, "auto-deleted ${it.sizeBytes} ${it.path}") }
        removed
    }

    companion object {
        private const val TAG = "ReplayStudio"

        /** One run at a time across the app: a save and Studio opening can both start one. */
        private val lock = Mutex()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * Starts a run in the background after a clip was saved to [path] ([bytes] long), so the save returns at
         * once. Does nothing when the rules are off.
         */
        fun afterSave(context: Context, privileged: PrivilegedAccess, path: String, bytes: Long) {
            if (!AutoDeleteRules.read(Prefs.of(context)).active) return
            scope.launch {
                runCatching { ClipAutoDelete(context, privileged).run(path, bytes) }
                    .onFailure { Log.w(TAG, "auto-delete after save failed", it) }
            }
        }
    }
}
