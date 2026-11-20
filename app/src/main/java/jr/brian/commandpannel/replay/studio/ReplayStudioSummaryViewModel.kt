package jr.brian.commandpannel.replay.studio

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jr.brian.commandpannel.privileged.PrivilegedAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the Replay Studio card in Replay Buffer settings shows. */
data class ReplayStudioSummary(
    /** False until the first listing comes back (the card shows a placeholder meanwhile). */
    val loaded: Boolean = false,
    /** The privileged shell is off, so the clips folder can't be read. */
    val unavailable: Boolean = false,
    val count: Int = 0,
    val newest: ReplayClip? = null,
    /** The newest clip's thumbnail, once loaded. */
    val thumbnail: ImageBitmap? = null,
)

/**
 * Feeds the Replay Studio card in the Replay Buffer section: how many clips there are, the newest one and its
 * thumbnail. Loads off the main thread, so the settings pane never waits on the shell; the last result stays while a
 * refresh runs.
 */
@HiltViewModel
class ReplayStudioSummaryViewModel @Inject constructor(
    app: Application,
    privileged: PrivilegedAccess,
) : AndroidViewModel(app) {

    private val repo = ClipRepository(app, privileged)

    private val _summary = MutableStateFlow(ReplayStudioSummary())
    val summary: StateFlow<ReplayStudioSummary> = _summary.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val clips = runCatching { repo.list() }.getOrNull()
            if (clips == null) {
                _summary.value = ReplayStudioSummary(loaded = true, unavailable = true)
                return@launch
            }
            val newest = clips.firstOrNull()
            val previous = _summary.value
            // Keep the thumbnail while the newest clip is unchanged, so a refresh doesn't flash the placeholder.
            val keepThumb = previous.newest?.path == newest?.path && previous.newest?.modifiedMs == newest?.modifiedMs
            _summary.value = ReplayStudioSummary(
                loaded = true,
                count = clips.size,
                newest = newest,
                thumbnail = if (keepThumb) previous.thumbnail else null,
            )
            if (newest == null || (keepThumb && previous.thumbnail != null)) return@launch
            val image = withContext(Dispatchers.IO) { runCatching { repo.thumbnail(newest)?.let { decodeClipThumbnail(it) } }.getOrNull() }
            if (image != null) _summary.update { if (it.newest?.path == newest.path) it.copy(thumbnail = image) else it }
        }
    }
}
