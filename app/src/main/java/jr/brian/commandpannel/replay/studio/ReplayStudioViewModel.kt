package jr.brian.commandpannel.replay.studio

import android.app.Application
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jr.brian.commandpannel.R
import jr.brian.commandpannel.privileged.PrivilegedAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** What Replay Studio shows: loading, no privileged access, or the clips (newest first, maybe none). */
data class ReplayStudioState(
    val loading: Boolean = true,
    /** The privileged shell is off, so the clips folder can't be read. */
    val unavailable: Boolean = false,
    val clips: List<ReplayClip> = emptyList(),
    /** A short status line (a delete or share that failed). */
    val message: String? = null,
)

/**
 * Replay Studio's clips, from [ClipRepository]. Durations and thumbnails are loaded per card, off the main thread,
 * and cached here so scrolling back doesn't load them again.
 */
@HiltViewModel
class ReplayStudioViewModel @Inject constructor(
    app: Application,
    privileged: PrivilegedAccess,
) : AndroidViewModel(app) {

    private val repo = ClipRepository(app, privileged)

    private val _state = MutableStateFlow(ReplayStudioState())
    val state: StateFlow<ReplayStudioState> = _state.asStateFlow()

    private val durations = ConcurrentHashMap<String, Long>()
    private val thumbs = LruCache<String, ImageBitmap>(THUMB_CACHE_SIZE)
    // A grid's worth of cards asks at once; a few at a time keeps the root shell and the decoder from piling up.
    private val loads = Semaphore(MAX_PARALLEL_LOADS)

    private val app get() = getApplication<Application>()

    fun refresh() {
        viewModelScope.launch {
            val clips = withContext(Dispatchers.IO) { runCatching { repo.list() }.getOrNull() }
            _state.update {
                if (clips == null) it.copy(loading = false, unavailable = true, clips = emptyList())
                else it.copy(loading = false, unavailable = false, clips = clips)
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** The cached length of [clip], if it was loaded already. */
    fun cachedDuration(clip: ReplayClip): Long? = durations[key(clip)]

    suspend fun duration(clip: ReplayClip): Long? {
        durations[key(clip)]?.let { return it }
        val ms = loads.withPermit { withContext(Dispatchers.IO) { runCatching { repo.durationMs(clip) }.getOrNull() } }
        if (ms != null) durations[key(clip)] = ms
        return ms
    }

    fun cachedThumbnail(clip: ReplayClip): ImageBitmap? = thumbs.get(key(clip))

    suspend fun thumbnail(clip: ReplayClip): ImageBitmap? {
        thumbs.get(key(clip))?.let { return it }
        val image = loads.withPermit {
            withContext(Dispatchers.IO) {
                runCatching { repo.thumbnail(clip)?.let { decodeClipThumbnail(it) } }.getOrNull()
            }
        }
        if (image != null) thumbs.put(key(clip), image)
        return image
    }

    /** A file this app can read for sharing [clip] (the clip itself, or a copy when only root can read it). */
    suspend fun shareableFile(clip: ReplayClip): File? {
        val file = withContext(Dispatchers.IO) { runCatching { repo.readableFile(clip) }.getOrNull() }
        if (file == null) _state.update { it.copy(message = app.getString(R.string.studio_share_failed)) }
        return file
    }

    fun shareFailed() = _state.update { it.copy(message = app.getString(R.string.studio_share_failed)) }

    /** Deletes [clip]; on success it leaves the grid right away, then the folder is read again. */
    fun delete(clip: ReplayClip) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { repo.delete(clip) }.getOrDefault(false) }
            if (ok) {
                durations.remove(key(clip))
                thumbs.remove(key(clip))
                _state.update { s -> s.copy(clips = s.clips.filterNot { it.path == clip.path }, message = null) }
                refresh()
            } else {
                _state.update { it.copy(message = app.getString(R.string.studio_delete_failed)) }
            }
        }
    }

    /** A replaced file (same path, new contents) gets a fresh thumbnail and length. */
    private fun key(clip: ReplayClip) = "${clip.path}@${clip.modifiedMs}"

    private companion object {
        const val THUMB_CACHE_SIZE = 60
        const val MAX_PARALLEL_LOADS = 3
    }
}

/** Decodes a cached clip thumbnail at about grid-card size (also used by the Replay Studio settings card). */
internal fun decodeClipThumbnail(file: File, targetWidthPx: Int = 480): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val opts = BitmapFactory.Options().apply { inSampleSize = thumbnailSampleSize(bounds.outWidth, targetWidthPx) }
    return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
}
