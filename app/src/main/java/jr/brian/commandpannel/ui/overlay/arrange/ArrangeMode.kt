package jr.brian.commandpannel.ui.overlay.arrange

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import jr.brian.commandpannel.system.DropTarget
import jr.brian.commandpannel.system.GridCell
import jr.brian.commandpannel.system.NavBarPosition
import jr.brian.commandpannel.system.OverlayCardGroup
import jr.brian.commandpannel.system.OverlayCardId
import jr.brian.commandpannel.system.OverlayLayout
import jr.brian.commandpannel.ui.theme.LocalTccColors
import jr.brian.commandpannel.ui.theme.LocalTccLookSpec
import jr.brian.commandpannel.ui.theme.tccTextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Spring used for cards sliding into their new slot and for cancelled drags returning home. */
private val ArrangeSpring = spring<Offset>(dampingRatio = 0.85f, stiffness = 350f)

/**
 * Drives Arrange mode: which card is lifted, which card is hovered as the drop target, and the
 * FLIP animation that slides every card from its old bounds to its new slot after a drop.
 *
 * Coarse state ([draggedCard], [hoveredTarget]) changes rarely; the per-frame finger offset lives
 * in [pointerOffset] and is only read inside the dragged card's graphics layer, so dragging never
 * recomposes the overlay.
 */
@Stable
class ArrangeController internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
    private val currentLayout: () -> OverlayLayout,
    private val commitLayout: (OverlayLayout) -> Unit
) {
    var isArranging by mutableStateOf(false)
        private set
    var draggedCard by mutableStateOf<OverlayCardId?>(null)
        private set
    var hoveredTarget by mutableStateOf<DropTarget?>(null)
        private set

    internal val pointerOffset = mutableStateOf(Offset.Zero)

    private val bounds = HashMap<OverlayCardId, Rect>()
    private val emptyCellBounds = HashMap<DropTarget.Empty, Rect>()
    private val flipAnimations = HashMap<OverlayCardId, Animatable<Offset, AnimationVector2D>>()
    private var pendingFlip: MutableMap<OverlayCardId, Offset>? = null
    private var dragStartInRoot = Offset.Zero
    private var contentBounds = Rect.Zero

    fun enter() {
        if (isArranging) return
        isArranging = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun exit() {
        draggedCard?.let { cancelDrag(it) }
        isArranging = false
    }

    fun reset() {
        commitWithFlip(OverlayLayout.DEFAULT)
    }

    /** True while any of [cards] is being dragged, so their containers can be raised above siblings. */
    fun isDraggingAny(cards: Collection<OverlayCardId>): Boolean = draggedCard in cards

    internal fun onContentPositioned(rect: Rect) {
        contentBounds = rect
    }

    internal fun register(card: OverlayCardId, animation: Animatable<Offset, AnimationVector2D>) {
        flipAnimations[card] = animation
    }

    internal fun unregister(card: OverlayCardId) {
        flipAnimations.remove(card)
        bounds.remove(card)
    }

    internal fun onEmptyPositioned(group: OverlayCardGroup, cell: GridCell, rect: Rect) {
        emptyCellBounds[DropTarget.Empty(group, cell)] = rect
    }

    internal fun clearEmpty(group: OverlayCardGroup, cell: GridCell) {
        emptyCellBounds.remove(DropTarget.Empty(group, cell))
    }

    internal fun onPositioned(card: OverlayCardId, rect: Rect) {
        bounds[card] = rect
        val oldTopLeft = pendingFlip?.remove(card) ?: return
        val delta = oldTopLeft - rect.topLeft
        if (delta.getDistance() < 0.5f) return
        val animation = flipAnimations[card] ?: return
        // The snapshot already includes any in-flight offset, so the card restarts exactly where it
        // was drawn. UNDISPATCHED snaps before this frame draws, so it never flashes at its new slot.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(delta)
            animation.animateTo(Offset.Zero, ArrangeSpring)
        }
    }

    internal fun startDrag(card: OverlayCardId, touchInCard: Offset) {
        val cardBounds = bounds[card] ?: return
        draggedCard = card
        hoveredTarget = null
        pointerOffset.value = Offset.Zero
        dragStartInRoot = cardBounds.topLeft + touchInCard
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun dragBy(card: OverlayCardId, amount: Offset) {
        if (draggedCard != card) return
        pointerOffset.value += amount
        if (card == OverlayCardId.NAV_BAR) return
        val finger = dragStartInRoot + pointerOffset.value
        val cardTarget = bounds.entries.firstOrNull { (id, rect) ->
            id != card && id.group == card.group && rect.contains(finger)
        }?.key
        val target: DropTarget? = when {
            cardTarget != null -> DropTarget.Card(cardTarget)
            else -> emptyCellBounds.entries.firstOrNull { (key, rect) ->
                key.group == card.group && rect.contains(finger)
            }?.key
        }
        if (target != hoveredTarget) {
            hoveredTarget = target
            if (target != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    internal fun endDrag(card: OverlayCardId) {
        if (draggedCard != card) return
        val layout = currentLayout()
        val newLayout = if (card == OverlayCardId.NAV_BAR) {
            val fingerY = dragStartInRoot.y + pointerOffset.value.y
            layout.withNavBarPosition(
                if (fingerY < contentBounds.center.y) NavBarPosition.TOP else NavBarPosition.BOTTOM
            )
        } else {
            hoveredTarget?.let { layout.applyDrop(card, it) } ?: layout
        }
        if (newLayout != layout) {
            commitWithFlip(newLayout)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            returnHome(card)
        }
        clearDrag()
    }

    internal fun cancelDrag(card: OverlayCardId) {
        if (draggedCard != card) return
        returnHome(card)
        clearDrag()
    }

    /** Records every card's current on-screen position, then applies [layout] so cards slide over. */
    private fun commitWithFlip(layout: OverlayLayout) {
        if (layout == currentLayout()) return
        val lifted = draggedCard
        val snapshot = HashMap<OverlayCardId, Offset>()
        for ((card, rect) in bounds) {
            val inFlight = flipAnimations[card]?.value ?: Offset.Zero
            val finger = if (card == lifted) pointerOffset.value else Offset.Zero
            snapshot[card] = rect.topLeft + inFlight + finger
        }
        // From here on the lifted card's finger offset is carried by its FLIP animation.
        pointerOffset.value = Offset.Zero
        pendingFlip = snapshot
        commitLayout(layout)
        // Cards that didn't move never get a new position callback; drop their entries after layout.
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            if (pendingFlip === snapshot) pendingFlip = null
        }
    }

    private fun returnHome(card: OverlayCardId) {
        val offset = pointerOffset.value
        pointerOffset.value = Offset.Zero
        val animation = flipAnimations[card] ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(animation.value + offset)
            animation.animateTo(Offset.Zero, ArrangeSpring)
        }
    }

    private fun clearDrag() {
        draggedCard = null
        hoveredTarget = null
    }
}

@Composable
fun rememberArrangeController(
    layout: OverlayLayout,
    onLayoutChange: (OverlayLayout) -> Unit
): ArrangeController {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val latestLayout by rememberUpdatedState(layout)
    val latestOnLayoutChange by rememberUpdatedState(onLayoutChange)
    return remember(scope, haptics) {
        ArrangeController(
            scope = scope,
            haptics = haptics,
            currentLayout = { latestLayout },
            commitLayout = { latestOnLayoutChange(it) }
        )
    }
}

/** Reports the bounds of the area the nav bar can be dropped in (used to pick top vs bottom). */
fun Modifier.arrangeContentArea(controller: ArrangeController): Modifier =
    onGloballyPositioned { controller.onContentPositioned(it.boundsInRoot()) }

/** Raises a container above its siblings while one of its [cards] is lifted, so the card draws on top. */
fun Modifier.arrangeLayer(controller: ArrangeController, cards: Collection<OverlayCardId>): Modifier =
    zIndex(if (controller.isDraggingAny(cards)) 1f else 0f)

/**
 * Enters Arrange mode on long-press without stealing taps from children: the gesture is observed
 * in the Initial pass, and only consumed once the long-press fires so the child's click is cancelled.
 */
fun Modifier.longPressToArrange(controller: ArrangeController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (controller.isArranging) return@awaitEachGesture
            val releasedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    ) {
                        break
                    }
                }
            }
            if (releasedEarly != null) return@awaitEachGesture
            controller.enter()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * A whole overlay card that can be dragged onto another card in Arrange mode.
 *
 * [modifier] carries the card's slot layout (weight, size, gap padding); [content] draws the card
 * itself. The slot's minimum constraints are passed through, so content fills slots with a fixed
 * size (weights, fillMax*) but still wraps in wrap-content slots like the AYN nav bar. While
 * arranging, a transparent layer on top takes all touches so the card's controls stay inert.
 */
@Composable
fun ArrangeableCard(
    card: OverlayCardId,
    controller: ArrangeController,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Modifier) -> Unit
) {
    val colors = LocalTccColors.current
    val cardShape = LocalTccLookSpec.current.panelCardShape
    val flip = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    DisposableEffect(card, controller) {
        controller.register(card, flip)
        onDispose { controller.unregister(card) }
    }

    val isArranging = controller.isArranging
    val isLifted = controller.draggedCard == card
    val isTarget = controller.hoveredTarget.let { it is DropTarget.Card && it.id == card }
    val scale by animateFloatAsState(
        targetValue = when {
            isLifted -> 1.05f
            isTarget -> 0.94f
            isArranging -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "arrangeScale"
    )
    val outlineColor = when {
        isTarget -> colors.accent
        isArranging -> colors.accent.copy(alpha = 0.35f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { controller.onPositioned(card, it.boundsInRoot()) }
            .zIndex(if (isLifted) 1f else 0f)
            .graphicsLayer {
                val offset = flip.value + if (isLifted) controller.pointerOffset.value else Offset.Zero
                translationX = offset.x
                translationY = offset.y
                scaleX = scale
                scaleY = scale
                alpha = if (isLifted) 0.92f else 1f
            }
            .then(if (isArranging) Modifier.border(2.dp, outlineColor, cardShape) else Modifier),
        propagateMinConstraints = true
    ) {
        content(Modifier)
        if (isArranging) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(card, controller) {
                        try {
                            detectDragGestures(
                                onDragStart = { controller.startDrag(card, it) },
                                onDrag = { change, amount ->
                                    change.consume()
                                    controller.dragBy(card, amount)
                                },
                                onDragEnd = { controller.endDrag(card) },
                                onDragCancel = { controller.cancelDrag(card) }
                            )
                        } finally {
                            controller.cancelDrag(card)
                        }
                    }
            )
        }
    }
}

/**
 * An empty grid cell (a gap the user left). While arranging it registers itself as a drop target and shows a
 * droppable outline; otherwise it is just blank space holding the grid's shape.
 */
@Composable
fun EmptyCell(
    group: OverlayCardGroup,
    cell: GridCell,
    controller: ArrangeController,
    modifier: Modifier = Modifier,
) {
    if (!controller.isArranging) {
        Box(modifier)
        return
    }
    val colors = LocalTccColors.current
    val cardShape = LocalTccLookSpec.current.panelCardShape
    val isTarget = controller.hoveredTarget.let { it is DropTarget.Empty && it.group == group && it.cell == cell }
    DisposableEffect(group, cell, controller) {
        onDispose { controller.clearEmpty(group, cell) }
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { controller.onEmptyPositioned(group, cell, it.boundsInRoot()) }
            .clip(cardShape)
            .background(colors.accent.copy(alpha = if (isTarget) 0.18f else 0.06f))
            .border(2.dp, colors.accent.copy(alpha = if (isTarget) 0.9f else 0.3f), cardShape)
    )
}

/** Floating "Arrange layout" pill: tab switcher (the nav bar is draggable, not tappable), Reset, Done. */
@Composable
fun ArrangeToolbar(
    hint: String,
    tabLabels: List<String>,
    activeTab: Int,
    onSelectTab: (Int) -> Unit,
    resetLabel: String,
    isResetEnabled: Boolean,
    onReset: () -> Unit,
    doneLabel: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTccColors.current
    val pillShape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(pillShape)
            .background(colors.panelBackgroundSelected)
            .border(1.dp, colors.accent.copy(alpha = 0.6f), pillShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            color = colors.secondary,
            fontSize = 13.sp,
            style = tccTextStyle(),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        tabLabels.forEachIndexed { index, label ->
            ToolbarChip(label = label, emphasized = index == activeTab, enabled = true) { onSelectTab(index) }
        }
        Spacer(modifier = Modifier.width(6.dp))
        ToolbarChip(label = resetLabel, emphasized = false, enabled = isResetEnabled, onClick = onReset)
        ToolbarChip(label = doneLabel, emphasized = true, enabled = true, onClick = onDone)
    }
}

@Composable
private fun ToolbarChip(label: String, emphasized: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalTccColors.current
    val chipShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .clip(chipShape)
            .background(if (emphasized) colors.accent else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> colors.secondary.copy(alpha = 0.5f)
                emphasized -> colors.primaryBackground
                else -> colors.primary
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = tccTextStyle()
        )
    }
}
