package jr.brian.commandpannel.system

import android.content.Context
import android.os.storage.StorageManager
import android.util.Log
import jr.brian.commandpannel.privileged.ReplayClipsLocation
import jr.brian.commandpannel.privileged.ReplayVolumes

/**
 * The device side of [ReplayVolumes] (#75): which removable volumes (SD card, USB drive) are mounted now, from
 * [StorageManager], and where clips actually go. A volume counts as mounted only when Android reports it `mounted`
 * (not read-only) with a mount point, and root writes also check that `/storage/<id>` exists before creating folders
 * ([ReplayVolumes.mkdirsCommand]), so a stale id never makes root create a folder in place of the card.
 */
object ReplayStorage {
    private const val TAG = "ReplayStorage"

    /** Where clips go now and why, for a saved [chosen] location. */
    data class Resolved(
        val location: ReplayClipsLocation,
        /** [chosen] is on a removable volume that isn't mounted and writable now, so [location] is TCP Clips. */
        val fellBack: Boolean,
        /** The chosen volume's state ([ReplayVolumes.State.MOUNTED] for internal storage). */
        val state: ReplayVolumes.State,
    )

    fun snapshots(context: Context): List<ReplayVolumes.Snapshot> = runCatching {
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        sm.storageVolumes.map { v ->
            // No mount point (Android gives none while it isn't mounted) counts as missing, whatever the state says.
            // Writes check the folder again as root before creating anything ([ReplayVolumes.mkdirsCommand]).
            val state = if (v.directory == null) "missing" else v.state
            ReplayVolumes.Snapshot(v.uuid, state, v.isRemovable, v.isPrimary)
        }
    }.getOrElse {
        Log.w(TAG, "couldn't read storage volumes", it)
        emptyList()
    }

    /** Removable volume ids that can take clips right now. */
    fun mountedIds(context: Context): Set<String> = ReplayVolumes.mountedIds(snapshots(context))

    fun state(context: Context, volume: String): ReplayVolumes.State = ReplayVolumes.stateOf(volume, snapshots(context))

    /** The location clips go to for [chosen] (`null` is TCP Clips). The chosen location itself is never changed. */
    fun resolve(context: Context, chosen: ReplayClipsLocation?): Resolved {
        val loc = chosen ?: ReplayClipsLocation.DEFAULT
        val volume = loc.volume ?: return Resolved(loc, fellBack = false, state = ReplayVolumes.State.MOUNTED)
        val state = state(context, volume)
        return if (state == ReplayVolumes.State.MOUNTED) Resolved(loc, false, state)
        else Resolved(ReplayClipsLocation.DEFAULT, fellBack = true, state = state)
    }

    /** The system's name for a mounted removable [volume] ("SanDisk SD card", "USB drive"), or `null`. */
    fun description(context: Context, volume: String): String? = runCatching {
        context.getSystemService(StorageManager::class.java)?.storageVolumes
            ?.firstOrNull { it.uuid == volume }?.getDescription(context)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
