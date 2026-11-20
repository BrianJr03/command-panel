package jr.brian.commandpannel.replay.studio

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.AndroidEntryPoint
import jr.brian.commandpannel.R
import jr.brian.commandpannel.privileged.PrivilegedAccess
import jr.brian.commandpannel.service.AssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Replay Studio's player (#46): plays a saved clip full screen on the top display, driven by the gamepad: B to
 * close, A play/pause, D-pad left/right seek 5 s, L1/R1 the previous/next clip in the library.
 *
 * Its own activity, not a companion route: [jr.brian.commandpannel.MainActivity] pins itself to display 0, and playing
 * a clip on the screen it was recorded on (#47) launches this on another display.
 *
 * While it's in front (started) the Replay Buffer is paused, so the clip's own video and sound aren't recorded; it
 * re-arms when the player stops. The service decides with [ReplayPauseGate] and also re-arms on its own if the player
 * stops checking in, so the buffer never stays off.
 */
@AndroidEntryPoint
class ReplayPlayerActivity : ComponentActivity() {

    @Inject lateinit var privileged: PrivilegedAccess

    private val clips by lazy { ClipRepository(applicationContext, privileged) }
    private val state = ReplayPlayerState()
    /** Where the video goes; #47 swaps in an output that splits a Both clip across the two screens. */
    private val output: ReplayVideoOutput = SingleSurfaceOutput

    private var player: ExoPlayer? = null
    private lateinit var currentPath: String
    /** Kept across stop / start (and recreation), so coming back resumes where it was. */
    private var resumePositionMs = 0L
    private var resumePlaying = true
    /** The library, newest first; loaded once. */
    private var library: List<ReplayClip>? = null

    private var loadJob: Job? = null
    private var hideJob: Job? = null
    private var noticeJob: Job? = null
    private var heartbeatJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = savedInstanceState?.getString(EXTRA_CLIP_PATH) ?: intent?.getStringExtra(EXTRA_CLIP_PATH)
        Log.i(TAG, "onCreate on display ${display?.displayId} clip=$path")
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        currentPath = path
        resumePositionMs = savedInstanceState?.getLong(KEY_POSITION) ?: 0L
        resumePlaying = savedInstanceState?.getBoolean(KEY_PLAYING) ?: true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent { ReplayPlayerScreen(state, output, onTap = ::showControls) }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    player?.let { p ->
                        state.positionMs = p.currentPosition.coerceAtLeast(0L)
                        state.durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                    }
                    delay(POSITION_POLL_MS)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val path = intent.getStringExtra(EXTRA_CLIP_PATH)?.takeIf { it.isNotBlank() } ?: return
        open(path)
    }

    override fun onStart() {
        super.onStart()
        if (!::currentPath.isInitialized) return
        isStarted = true
        tellService(AssistantService.ACTION_REPLAY_PLAYER_OPEN)
        heartbeatJob = lifecycleScope.launch {
            while (true) {
                delay(ReplayPauseGate.HEARTBEAT_MS)
                tellService(AssistantService.ACTION_REPLAY_PLAYER_OPEN)
            }
        }
        createPlayer()
        open(currentPath, resumePositionMs, resumePlaying)
    }

    override fun onStop() {
        if (!::currentPath.isInitialized) return super.onStop()
        heartbeatJob?.cancel()
        releasePlayer()
        isStarted = false
        tellService(AssistantService.ACTION_REPLAY_PLAYER_CLOSED)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::currentPath.isInitialized) return
        player?.let {
            resumePositionMs = it.currentPosition
            resumePlaying = it.playWhenReady
        }
        outState.putString(EXTRA_CLIP_PATH, currentPath)
        outState.putLong(KEY_POSITION, resumePositionMs)
        outState.putBoolean(KEY_PLAYING, resumePlaying)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = playerCommandFor(event.keyCode)
        if (event.action == KeyEvent.ACTION_DOWN) {
            showControls()
            if (command != null && (event.repeatCount == 0 || command.repeats())) handle(command)
        }
        // Our keys are used up (B / Back would otherwise also go through the system's Back); others behave as usual.
        return command != null || super.dispatchKeyEvent(event)
    }

    private fun handle(command: PlayerCommand) {
        val p = player
        when (command) {
            PlayerCommand.CLOSE -> finish()
            PlayerCommand.TOGGLE_PLAY -> p?.let {
                if (it.playbackState == Player.STATE_ENDED) {
                    it.seekTo(0L)
                    it.play()
                } else {
                    it.playWhenReady = !it.playWhenReady
                }
            }
            PlayerCommand.SEEK_BACK, PlayerCommand.SEEK_FORWARD -> p?.takeIf { state.error == null }?.let {
                val delta = if (command == PlayerCommand.SEEK_BACK) -PLAYER_SEEK_STEP_MS else PLAYER_SEEK_STEP_MS
                val duration = it.duration.takeIf { d -> d != C.TIME_UNSET } ?: -1L
                it.seekTo(seekTarget(it.currentPosition, duration, delta))
                state.positionMs = it.currentPosition
            }
            PlayerCommand.PREVIOUS_CLIP -> step(-1)
            PlayerCommand.NEXT_CLIP -> step(+1)
        }
        // Read by verify-46.sh.
        Log.i(TAG, "command $command playWhenReady=${p?.playWhenReady} pos=${p?.currentPosition}")
    }

    private fun createPlayer() {
        if (player != null) return
        val p = runCatching {
            ExoPlayer.Builder(this).build().apply {
                // Normal media focus: other audio pauses or ducks while a clip plays, and we pause for a call.
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                addListener(listener)
            }
        }.onFailure { Log.w(TAG, "player create failed", it) }.getOrNull()
        player = p
        state.player = p
        if (p == null) state.error = getString(R.string.player_error_playback)
    }

    private fun releasePlayer() {
        loadJob?.cancel()
        val p = player ?: return
        resumePositionMs = p.currentPosition
        resumePlaying = p.playWhenReady
        player = null
        state.player = null
        state.playing = false
        runCatching {
            p.removeListener(listener)
            p.release()
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            state.playing = isPlaying
            // Paused, the controls stay up; playing, they fade out after a moment.
            showControls()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) state.loading = false
            if (playbackState == Player.STATE_ENDED) showControls()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                state.aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "playback failed: ${error.errorCodeName} ($currentPath)", error)
            state.loading = false
            state.error = getString(R.string.player_error_playback)
            showControls()
        }
    }

    /** Loads [path] into the player, from [startMs]. A clip only the root shell can read is copied first. */
    private fun open(path: String, startMs: Long = 0L, play: Boolean = true) {
        currentPath = path
        state.title = File(path).name
        state.error = null
        state.loading = true
        showControls()
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val lib = library()
            val index = lib.indexOfFirst { it.path == path }
            state.index = index + 1
            state.count = lib.size
            val file = withContext(Dispatchers.IO) { readable(lib.getOrNull(index), path) }
            val p = player
            if (p == null) {
                // The player couldn't be created; a stop cancels this job, so it never lands here.
                state.loading = false
                state.error = getString(R.string.player_error_playback)
                return@launch
            }
            if (file == null) {
                Log.w(TAG, "no readable file for $path")
                state.loading = false
                state.error = getString(R.string.player_error_open)
                return@launch
            }
            runCatching {
                p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), startMs)
                p.playWhenReady = play
                p.prepare()
            }.onFailure {
                Log.w(TAG, "open failed for $path", it)
                state.loading = false
                state.error = getString(R.string.player_error_playback)
            }
        }
    }

    /**
     * A file the player can read. A library clip goes through [ClipRepository.readableFile]; a path outside the
     * library is only played when the app can read it directly, so the player never root-reads an arbitrary path.
     */
    private suspend fun readable(clip: ReplayClip?, path: String): File? = runCatching {
        if (clip != null) clips.readableFile(clip) else File(path).takeIf { it.isFile && it.canRead() }
    }.onFailure { Log.w(TAG, "resolve failed for $path", it) }.getOrNull()

    private suspend fun library(): List<ReplayClip> {
        library?.let { return it }
        val listed = runCatching { withContext(Dispatchers.IO) { clips.list() } }
            .onFailure { Log.w(TAG, "clip list failed", it) }
            .getOrNull()
        // null means the privileged shell isn't up yet; try again next time instead of caching nothing.
        return listed?.also { library = it } ?: emptyList()
    }

    /** L1 / R1: the clip [step] places away in the library (-1 newer, +1 older). */
    private fun step(step: Int) {
        lifecycleScope.launch {
            val lib = library()
            val next = neighborIndex(lib.map { it.path }, currentPath, step)
            if (next == null) {
                notice(getString(if (step < 0) R.string.player_no_newer else R.string.player_no_older))
            } else {
                open(lib[next].path)
            }
        }
    }

    private fun showControls() {
        state.controlsVisible = true
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(CONTROLS_HIDE_MS)
            if (player?.isPlaying == true && state.error == null) state.controlsVisible = false
        }
    }

    private fun notice(text: String) {
        state.notice = text
        noticeJob?.cancel()
        noticeJob = lifecycleScope.launch {
            delay(NOTICE_MS)
            state.notice = null
        }
    }

    /** Pause / resume requests for the Replay Buffer; only while the service runs (it's what owns the buffer). */
    private fun tellService(action: String) {
        if (!AssistantService.isRunning) return
        runCatching { startService(Intent(this, AssistantService::class.java).setAction(action)) }
            .onFailure { Log.w(TAG, "couldn't reach the service ($action)", it) }
    }

    companion object {
        private const val TAG = "ReplayPlayer"
        const val EXTRA_CLIP_PATH = "clip_path"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_PLAYING = "playing"
        private const val POSITION_POLL_MS = 250L
        private const val CONTROLS_HIDE_MS = 3_000L
        private const val NOTICE_MS = 2_000L

        /**
         * True while a player is started (in front). Same process as the service, which reads it as the "player alive"
         * signal for [ReplayPauseGate.onCheck].
         */
        @Volatile var isStarted = false
            private set

        /** Opens the player on [clipPath]; L1/R1 page through the folder's other clips. */
        fun launch(context: Context, clipPath: String) {
            val intent = Intent(context, ReplayPlayerActivity::class.java).putExtra(EXTRA_CLIP_PATH, clipPath)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            runCatching { context.startActivity(intent, options.toBundle()) }
                .onFailure { Log.w(TAG, "launch failed for $clipPath", it) }
        }
    }
}
