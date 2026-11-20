package jr.brian.commandpannel.replay.studio

import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player

/**
 * Where the player's decoded video goes. The player owns the one [Player] (one decoder, so audio and every screen stay
 * in sync) and hands it to an output, which decides which surfaces the frames land on.
 *
 * [SingleSurfaceOutput] is the default: the whole frame on one surface on the player's screen, fit inside it. A Both
 * clip is recorded stacked (top screen over bottom), so it plays that way, scaled to fit. #47 adds an output that
 * renders into its own texture and crops the two halves onto the top screen and a Presentation on the lower one.
 */
interface ReplayVideoOutput {
    /** Draws the video of [player], whose frames are [aspectRatio] (width / height) wide. */
    @Composable
    fun Content(player: Player, aspectRatio: Float, modifier: Modifier)
}

/** The whole frame on one [SurfaceView], centered and fit to the space it's given. */
object SingleSurfaceOutput : ReplayVideoOutput {
    @Composable
    override fun Content(player: Player, aspectRatio: Float, modifier: Modifier) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // A new player (the activity makes one each time it comes back) gets a fresh surface view.
            key(player) {
                AndroidView(
                    factory = { SurfaceView(it).also(player::setVideoSurfaceView) },
                    onRelease = { player.clearVideoSurfaceView(it) },
                    modifier = Modifier.aspectRatio(aspectRatio.takeIf { it > 0f && it.isFinite() } ?: DEFAULT_ASPECT),
                )
            }
        }
    }

    private const val DEFAULT_ASPECT = 16f / 9f
}
