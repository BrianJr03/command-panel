package jr.brian.commandpannel.ui.settings

/**
 * Theme Studio's undo / redo: snapshots of the draft before each change. Changes within [coalesceMs] of the last one
 * join it (a held stepper or a typed name is one step); at most [cap] steps are kept. A new change clears redo.
 */
internal class StudioHistory<T>(private val cap: Int = 50, private val coalesceMs: Long = 600) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    private var lastAt = Long.MIN_VALUE

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Call before a change, with the value it replaces. */
    fun record(before: T, nowMs: Long) {
        if (undo.isEmpty() || lastAt == Long.MIN_VALUE || nowMs - lastAt > coalesceMs) {
            undo.addLast(before)
            if (undo.size > cap) undo.removeFirst()
        }
        redo.clear()
        lastAt = nowMs
    }

    /** The value before the last change (and [current] becomes redoable), or null when there's nothing to undo. */
    fun undo(current: T): T? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        lastAt = Long.MIN_VALUE
        return previous
    }

    fun redo(current: T): T? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        lastAt = Long.MIN_VALUE
        return next
    }
}
