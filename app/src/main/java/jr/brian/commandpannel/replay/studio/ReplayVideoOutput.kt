package jr.brian.commandpannel.replay.studio

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import jr.brian.commandpannel.R
import jr.brian.commandpannel.privileged.ScreenRecorderEngine.Region
import jr.brian.commandpannel.system.DisplayTopology
import jr.brian.commandpannel.ui.settings.LocalCompanionPalette

/**
 * Where the player's decoded video goes. The player owns the one [Player] (one decoder, so audio and every screen stay
 * in sync) and hands it to an output, which decides which surfaces the frames land on. [Content] is composed on the
 * top screen while the player exists; anything an output puts elsewhere (a window on the lower screen, a GL thread)
 * lives exactly as long as that composition.
 *
 * - [SingleSurfaceOutput]: the whole frame on the top screen, fit inside it (the default; a Both clip plays stacked).
 * - [LowerScreenOutput]: the whole frame on the lower screen (a Bottom clip, #47).
 * - [SplitScreenOutput]: a Both clip cropped back onto the two screens (#47).
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

/**
 * The whole frame on the lower screen, in a [LowerScreenWindow]; the top screen says where the video is and keeps the
 * controls. [onUnavailable] runs (main thread) when the window can't be shown, so the player falls back to the top.
 */
class LowerScreenOutput(private val context: Context, private val onUnavailable: () -> Unit) : ReplayVideoOutput {
    @Composable
    override fun Content(player: Player, aspectRatio: Float, modifier: Modifier) {
        DisposableEffect(player) {
            val window = LowerScreenWindow(context)
            val surface = if (window.show()) window.surfaceView else null
            if (surface == null) onUnavailable() else player.setVideoSurfaceView(surface)
            onDispose {
                surface?.let(player::clearVideoSurfaceView)
                window.hide()
            }
        }
        ElsewhereNote(R.string.player_on_lower, modifier)
    }
}

/**
 * A Both clip split back onto its screens: one decoder into a [SplitVideoRenderer], which crops the top region onto
 * the top screen and the bottom region onto a [LowerScreenWindow]. The crop comes from the Replay Buffer's layout for
 * the screens' current sizes ([bothCropRegions]); the activity reports the clip's frame size through [setVideoSize],
 * which says false when the frame doesn't match, and the player then plays it whole on top. [onUnavailable] runs (main
 * thread) when GL or the lower window fails.
 *
 * Main thread only.
 */
class SplitScreenOutput(private val context: Context, private val onUnavailable: () -> Unit) : ReplayVideoOutput {

    private val topology = DisplayTopology(context)
    private var renderer: SplitVideoRenderer? = null
    private var crop: Triple<Int, Int, Pair<Region, Region>>? = null

    /** The clip's frame size; false when it isn't a Both frame for these screens (the caller then plays on top). */
    fun setVideoSize(width: Int, height: Int): Boolean {
        val top = topology.topDisplay ?: return false
        val bottom = topology.bottomDisplay ?: return false
        val t = topology.realSize(top)
        val b = topology.realSize(bottom)
        val regions = bothCropRegions(width, height, t.x, t.y, b.x, b.y)
        if (regions == null) {
            Log.i(TAG, "split: a ${width}x$height frame isn't a Both layout for ${t.x}x${t.y} + ${b.x}x${b.y}")
            return false
        }
        Log.i(TAG, "split: ${width}x$height top=${regions.first} lower=${regions.second}")
        crop = Triple(width, height, regions)
        renderer?.setCrop(width, height, regions.first, regions.second)
        return true
    }

    @Composable
    override fun Content(player: Player, aspectRatio: Float, modifier: Modifier) {
        key(player) {
            val split = androidx.compose.runtime.remember { SplitVideoRenderer() }
            DisposableEffect(split) {
                val window = LowerScreenWindow(context)
                val started = split.start()
                val shown = started && window.show(slotCallback(split, SplitVideoRenderer.SLOT_LOWER))
                if (!shown) {
                    onUnavailable()
                } else {
                    renderer = split
                    crop?.let { (w, h, r) -> split.setCrop(w, h, r.first, r.second) }
                    split.input?.let(player::setVideoSurface)
                }
                onDispose {
                    split.input?.let(player::clearVideoSurface)
                    window.hide()
                    if (renderer === split) renderer = null
                    split.release()
                }
            }
            AndroidView(
                factory = { SurfaceView(it).apply { holder.addCallback(slotCallback(split, SplitVideoRenderer.SLOT_TOP)) } },
                modifier = modifier.fillMaxSize(),
            )
        }
    }

    /** Hands a surface's life to the renderer's [slot]. */
    private fun slotCallback(split: SplitVideoRenderer, slot: Int) = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
            split.setOutput(slot, holder.surface, width, height)
        override fun surfaceDestroyed(holder: SurfaceHolder) = split.setOutput(slot, null, 0, 0)
    }

    private companion object {
        const val TAG = "ReplayPlayer"
    }
}

/** The top screen's note while the video plays on the lower screen. */
@Composable
private fun ElsewhereNote(text: Int, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(text),
            color = LocalCompanionPalette.current.textSecondary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
