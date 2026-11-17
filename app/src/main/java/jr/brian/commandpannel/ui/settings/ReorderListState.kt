package jr.brian.commandpannel.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drag-and-drop state for the companion's order lists (quick tiles, nav bar buttons). Touch: the dragged row follows
 * the finger, swapping with a neighbor once it crosses half a slot, and the screen auto-scrolls near the viewport
 * edges. Gamepad: A picks a row up ([grab]), D-pad moves it ([moveGrabbed]), A / B drops it ([drop]). Neighbors
 * slide into place; the new order is committed through [onCommit] when the drag ends.
 */
@Stable
internal class ReorderListState(
    initial: List<String>,
    private val scope: CoroutineScope,
    private val scroll: ScrollState,
    /** Root-coordinate bounds of the scrolling viewport, for edge auto-scroll. */
    private val viewport: () -> Rect,
) {
    val items = mutableStateListOf<String>().apply { addAll(initial) }

    /** Row being dragged by touch. */
    var dragged by mutableStateOf<String?>(null)
        private set

    /** Row picked up with the gamepad. */
    var grabbed by mutableStateOf<String?>(null)
        private set

    private var dragOffset by mutableFloatStateOf(0f)

    /** Row height plus the spacing between rows, in px; set by the rows as they measure. */
    var slotPx = 0f
    var onCommit: (List<String>) -> Unit = {}

    private val shifts = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()
    private var startOrder: List<String> = emptyList()
    private var pointerRootY = 0f
    private var autoScroll: Job? = null

    private fun shiftOf(key: String) = shifts.getOrPut(key) { Animatable(0f) }

    /** Vertical translation for [key]: the finger offset while dragged, else its slide-into-place animation. */
    fun translationOf(key: String): Float = if (key == dragged) dragOffset else shiftOf(key).value

    fun isLifted(key: String) = key == dragged || key == grabbed

    /** Adopts an order from outside (tile toggled on/off, restore) unless a reorder is in progress. */
    fun sync(keys: List<String>) {
        if (dragged != null || grabbed != null || items.toList() == keys) return
        items.clear()
        items.addAll(keys)
    }

    fun startDrag(key: String, pointerRootY: Float) {
        if (grabbed != null) drop()
        startOrder = items.toList()
        dragged = key
        dragOffset = 0f
        this.pointerRootY = pointerRootY
        autoScroll?.cancel()
        autoScroll = scope.launch { autoScrollLoop() }
    }

    fun dragBy(dy: Float) {
        if (dragged == null) return
        dragOffset += dy
        pointerRootY += dy
        settle()
    }

    fun endDrag() {
        autoScroll?.cancel()
        val key = dragged ?: return
        val residual = dragOffset
        dragged = null
        dragOffset = 0f
        // Ease the dropped row from where the finger left it into its slot.
        scope.launch { shiftOf(key).run { snapTo(residual); animateTo(0f, spring()) } }
        commitIfChanged()
    }

    fun grab(key: String) {
        startOrder = items.toList()
        grabbed = key
    }

    /** Moves the grabbed row one slot; returns `true` when a row is grabbed (so the D-pad press is consumed). */
    fun moveGrabbed(up: Boolean): Boolean {
        val key = grabbed ?: return false
        val from = items.indexOf(key)
        val to = if (up) from - 1 else from + 1
        if (to in items.indices) swap(from, to, animateMover = true)
        return true
    }

    fun drop() {
        if (grabbed == null) return
        grabbed = null
        commitIfChanged()
    }

    private fun commitIfChanged() {
        val now = items.toList()
        if (now != startOrder) onCommit(now)
    }

    /** Swaps the dragged row past neighbors while it sits more than half a slot away from its own. */
    private fun settle() {
        val key = dragged ?: return
        if (slotPx <= 0f) return
        var i = items.indexOf(key)
        while (dragOffset > slotPx / 2 && i < items.lastIndex) { swap(i, i + 1, animateMover = false); dragOffset -= slotPx; i++ }
        while (dragOffset < -slotPx / 2 && i > 0) { swap(i, i - 1, animateMover = false); dragOffset += slotPx; i-- }
    }

    /** Moves the row at [from] to [to]; the displaced neighbor (and optionally the mover) slide from their old spot. */
    private fun swap(from: Int, to: Int, animateMover: Boolean) {
        val mover = items[from]
        val other = items[to]
        items[to] = mover
        items[from] = other
        val step = if (to > from) slotPx else -slotPx
        slideFrom(other, step)
        if (animateMover) slideFrom(mover, -step)
    }

    private fun slideFrom(key: String, offset: Float) {
        scope.launch { shiftOf(key).run { snapTo(value + offset); animateTo(0f, spring()) } }
    }

    private suspend fun autoScrollLoop() {
        while (scope.isActive) {
            withFrameNanos { }
            val vp = viewport()
            if (vp.height <= 0f) continue
            val edge = vp.height * 0.12f
            val speed = when {
                pointerRootY < vp.top + edge -> -MAX_SCROLL_PX * ((vp.top + edge - pointerRootY) / edge).coerceIn(0f, 1f)
                pointerRootY > vp.bottom - edge -> MAX_SCROLL_PX * ((pointerRootY - (vp.bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed == 0f) continue
            val used = scroll.scrollBy(speed)
            if (used != 0f) {
                // Content moved under a stationary finger; keep the row under it.
                dragOffset += used
                settle()
            }
        }
    }

    private companion object {
        const val MAX_SCROLL_PX = 22f
    }
}
