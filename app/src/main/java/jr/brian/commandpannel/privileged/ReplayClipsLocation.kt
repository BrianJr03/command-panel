package jr.brian.commandpannel.privileged

/**
 * Where replay clips are saved (#75): a storage volume and a folder on it. [volume] is `null` for internal storage
 * (`/storage/emulated/0`) or a removable volume's id (an SD card or USB drive, `/storage/<id>`). [relativeDir] follows
 * [ScreenRecorderEngine.normalizeReplayClipsRelativeDir]. Pure, so it is unit-tested.
 *
 * Stored in the `replay_clips_dir` pref (and each entry of `replay_known_dirs`) as [encode]: the folder alone for
 * internal storage, which is what every version before #75 wrote (so existing values need no migration), or
 * `<volume>:<folder>` for a removable volume. `:` can't appear in a folder, so the two never mix up; an older version
 * reading a removable value finds it invalid and uses TCP Clips.
 */
data class ReplayClipsLocation(val volume: String?, val relativeDir: String) {

    /** The volume's root: internal storage, or `/storage/<volume>`. */
    val root: String get() = ReplayVolumes.rootFor(volume)

    /** The clips folder's absolute path. */
    val path: String get() = "$root/$relativeDir"

    val isRemovable: Boolean get() = volume != null

    fun encode(): String = if (volume == null) relativeDir else "$volume:$relativeDir"

    companion object {
        /** TCP Clips on internal storage. */
        val DEFAULT = ReplayClipsLocation(null, ScreenRecorderEngine.DEFAULT_REPLAY_CLIPS_RELATIVE_DIR)

        /**
         * Builds a location, or `null` if [volume] isn't `null` or a removable volume id ([ReplayVolumes.isRemovableId])
         * or [relativeDir] isn't an allowed folder. The folder comes back normalized.
         */
        fun of(volume: String?, relativeDir: String?): ReplayClipsLocation? {
            if (volume != null && !ReplayVolumes.isRemovableId(volume)) return null
            val dir = ScreenRecorderEngine.normalizeReplayClipsRelativeDir(relativeDir) ?: return null
            return ReplayClipsLocation(volume, dir)
        }

        /** Reads an [encode]d value: a plain folder is internal storage. `null` for a missing or invalid value. */
        fun parse(raw: String?): ReplayClipsLocation? {
            if (raw.isNullOrBlank()) return null
            val colon = raw.indexOf(':')
            return if (colon < 0) of(null, raw) else of(raw.substring(0, colon), raw.substring(colon + 1))
        }
    }
}

/** Storage volumes clips may be saved on (#75). Pure, so it is unit-tested. */
object ReplayVolumes {
    /** Internal storage's tree document volume in the system folder picker. */
    const val PRIMARY = "primary"

    /**
     * A removable volume's id, as Android names `/storage/<id>` for a FAT32 or exFAT SD card or USB drive: two groups of
     * four uppercase hex digits (`3A0D-5000`). Anything else (lowercase, longer, `emulated`, `self`) is refused.
     */
    private val REMOVABLE_ID = Regex("^[0-9A-F]{4}-[0-9A-F]{4}$")

    fun isRemovableId(id: String?): Boolean = id != null && REMOVABLE_ID.matches(id)

    fun rootFor(volume: String?): String =
        if (volume == null) ScreenRecorderEngine.REPLAY_STORAGE_ROOT else "/storage/$volume"

    /**
     * The roots clips may be written under: internal storage ([PrivilegedHelperMain.DEFAULT_STORAGE_ROOTS]) plus
     * `/storage/<id>` for each [mounted] removable volume with a valid id. Ids that don't match are dropped.
     */
    fun storageRoots(mounted: Collection<String>): List<String> =
        PrivilegedHelperMain.DEFAULT_STORAGE_ROOTS + mounted.filter(::isRemovableId).distinct().sorted().map { "/storage/$it" }

    /**
     * A root shell command that creates [dir] (under [root]) only while [root] exists, so a card pulled after the
     * mounted check never gets a plain folder made in its place under `/storage`. Succeeds (exit 0) only if [dir] exists.
     */
    fun mkdirsCommand(root: String, dir: String): String =
        "[ -d ${RootShell.q(root)} ] && mkdir -p ${RootShell.q(dir)}"

    /** A volume as [android.os.storage.StorageManager] reports it, reduced to what [stateOf] needs. */
    data class Snapshot(val id: String?, val state: String, val removable: Boolean, val primary: Boolean)

    enum class State { MOUNTED, READ_ONLY, MISSING }

    /**
     * Whether removable volume [id] can take clips now: [State.MOUNTED] only for a removable, non-primary volume with
     * that exact id whose state is `mounted`; `mounted_ro` is [State.READ_ONLY]; anything else (absent, ejecting,
     * unmounted, an invalid id) is [State.MISSING].
     */
    fun stateOf(id: String, volumes: List<Snapshot>): State {
        if (!isRemovableId(id)) return State.MISSING
        val v = volumes.firstOrNull { it.id == id && it.removable && !it.primary } ?: return State.MISSING
        return when (v.state) {
            "mounted" -> State.MOUNTED
            "mounted_ro" -> State.READ_ONLY
            else -> State.MISSING
        }
    }

    /** The ids of every volume in [volumes] that [stateOf] calls [State.MOUNTED]. */
    fun mountedIds(volumes: List<Snapshot>): Set<String> =
        volumes.mapNotNull { it.id }.filter { stateOf(it, volumes) == State.MOUNTED }.toSet()

    /** What a folder picked in the system folder picker means for clips. */
    sealed interface Pick {
        data class Ok(val location: ReplayClipsLocation) : Pick
        /** Not a folder clips can go in (storage root, `Android/`, too deep, another provider). */
        data object Invalid : Pick
    }

    /** The picker's Documents shortcut: `home:<folder>` is `Documents/<folder>` on internal storage (#81). */
    const val HOME = "home"
    private const val HOME_DIR = "Documents"

    /**
     * Maps a picked folder's tree document id to a location: `primary:<folder>` is internal storage, `home:<folder>`
     * internal `Documents/<folder>` (#81), `<id>:<folder>` a removable volume (whether it is mounted is checked
     * separately). The folder must pass [ScreenRecorderEngine.normalizeReplayClipsRelativeDir] with room for the
     * per-screen subfolder.
     */
    fun fromTreeDocId(docId: String): Pick {
        val colon = docId.indexOf(':')
        if (colon <= 0) return Pick.Invalid
        val volumeId = docId.substring(0, colon)
        val path = docId.substring(colon + 1)
        val (volume, folder) = when {
            volumeId == PRIMARY -> null to path
            volumeId == HOME -> null to "$HOME_DIR/$path"
            isRemovableId(volumeId) -> volumeId to path
            else -> return Pick.Invalid
        }
        val location = ReplayClipsLocation.of(volume, folder) ?: return Pick.Invalid
        if (location.relativeDir.split('/').size >= ScreenRecorderEngine.MAX_REPLAY_DIR_DEPTH) return Pick.Invalid
        return Pick.Ok(location)
    }
}
