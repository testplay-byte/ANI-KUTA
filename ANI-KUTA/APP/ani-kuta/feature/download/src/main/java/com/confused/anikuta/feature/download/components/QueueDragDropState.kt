package com.confused.anikuta.feature.download.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * ROUND 122 (R10): long-press drag-to-reorder for the download queue's
 * reorder card — the official Compose LazyColumn drag-drop pattern
 * (DragDropListState + onMove swaps + LazyListState item offsets).
 *
 * Works with VARIABLE-HEIGHT rows for free: the swap test compares the
 * dragged row's midpoint against every visible item's real
 * `offset..offset+size` band (never a uniform height), and the drag delta is
 * REBASED after each swap (`dragDelta -= target.offset - current.offset`) so
 * the row stays glued to the finger regardless of the heights crossed.
 *
 * The list is swapped LOCALLY by the caller's [onMove] (no VM work, no
 * persistence mid-drag — jank-free); persistence happens once on drag END
 * (the caller commits the full order — DownloadViewModel.commitReorder).
 */
@Stable
class QueueDragDropState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    /**
     * Updated on every recomposition via [rememberQueueDragDropState]'s
     * SideEffect — the lambda captures the caller's live local list, so it
     * must NOT be a remember key (a new lambda per recomposition would
     * otherwise recreate this state mid-drag and kill it).
     */
    var onMove: ((fromIndex: Int, toIndex: Int) -> Unit)? = null
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    /** The live pixel offset applied to the dragged row (graphicsLayer). */
    var draggingItemDelta by mutableStateOf(0f)
        private set

    private var dragDelta = 0f
    private val scrollChannel = Channel<Float>(Channel.CONFLATED)
    private var scrollJob: Job? = null

    fun onDragStart(index: Int) {
        draggingItemIndex = index
        dragDelta = 0f
        draggingItemDelta = 0f
        scrollJob = scope.launch {
            while (true) {
                val over = scrollChannel.receive()
                listState.scrollBy(over)
            }
        }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemDelta = 0f
        dragDelta = 0f
        scrollJob?.cancel()
        scrollJob = null
    }

    fun onDrag(offset: Offset) {
        val currentIndex = draggingItemIndex ?: return
        val current = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentIndex } ?: return
        dragDelta += offset.y
        draggingItemDelta = dragDelta
        val draggedMiddle = current.offset + dragDelta + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentIndex &&
                draggedMiddle.toInt() in item.offset..(item.offset + item.size)
        }
        if (target != null) {
            onMove?.invoke(currentIndex, target.index)
            // Rebase: keep the finger glued to the row it's dragging.
            dragDelta -= target.offset - current.offset
            draggingItemIndex = target.index
            draggingItemDelta = dragDelta
        } else {
            // Edge auto-scroll when dragging past the viewport ends.
            val startOffset = listState.layoutInfo.viewportStartOffset
            val endOffset = listState.layoutInfo.viewportEndOffset
            val draggedTop = current.offset + dragDelta
            val draggedBottom = current.offset + current.size + dragDelta
            val over = when {
                draggedTop < startOffset -> draggedTop - startOffset
                draggedBottom > endOffset -> draggedBottom - endOffset
                else -> 0f
            }
            if (over != 0f) scrollChannel.trySend(over / 4f)
        }
    }
}

/** Remembers a [QueueDragDropState] bound to [listState]. */
@Composable
fun rememberQueueDragDropState(
    listState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): QueueDragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(listState) { QueueDragDropState(listState, scope) }
    androidx.compose.runtime.SideEffect { state.onMove = onMove }
    return state
}
