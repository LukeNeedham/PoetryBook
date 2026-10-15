package com.lukeneedham.poetrybook.ui.book

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Drag and drop reordering for the rows of a [LazyListState]-backed list whose items are keyed.
 * Give each row [reorderable] and read [draggedKey] to style the row being dragged.
 */
@Stable
internal class ReorderState(
    private val listState: LazyListState,
    private val haptics: HapticFeedback,
    private val onMoveState: androidx.compose.runtime.State<(Int, Int) -> Unit>,
    private val movableCountState: androidx.compose.runtime.State<Int>,
) {
    /** The key of the row being dragged, or null. */
    var draggedKey: Any? by mutableStateOf(null)
        private set

    /** Where the top of the dragged row is held by the finger, in the list's own coordinates. */
    private var dragTop by mutableFloatStateOf(0f)

    /** After asking for a move, the index the dragged row will have once the list has caught up. */
    private var expectedIndex: Int? = null

    private fun draggedItem() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggedKey }

    /** How far the dragged row has to be shifted from where the list laid it out, to sit under the finger. */
    internal fun translationOf(key: Any): Float {
        if (key != draggedKey) return 0f
        val item = draggedItem() ?: return 0f
        return dragTop - item.offset
    }

    internal fun start(key: Any) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        expectedIndex = null
        dragTop = item.offset.toFloat()
        draggedKey = key
    }

    internal fun dragBy(dy: Float) {
        val item = draggedItem() ?: return
        val info = listState.layoutInfo
        dragTop = (dragTop + dy).coerceIn(
            info.viewportStartOffset.toFloat(),
            (info.viewportEndOffset - item.size).toFloat().coerceAtLeast(info.viewportStartOffset.toFloat()),
        )
        swapIfNeeded()
    }

    internal fun stop() {
        draggedKey = null
        expectedIndex = null
    }

    /** Moves the dragged row past the neighbour whose span holds the middle of the dragged row. */
    private fun swapIfNeeded() {
        val dragged = draggedItem() ?: return
        // A move was asked for but the list hasn't laid itself out again; its positions are stale.
        val waitingFor = expectedIndex
        if (waitingFor != null) {
            if (dragged.index != waitingFor) return
            expectedIndex = null
        }
        val middle = dragTop + dragged.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != draggedKey &&
                it.index < movableCountState.value &&
                middle >= it.offset &&
                middle < it.offset + it.size
        } ?: return
        expectedIndex = target.index
        onMoveState.value(dragged.index, target.index)
    }

    /** Scrolls the list while the dragged row is held near its top or bottom edge. */
    internal suspend fun autoScroll(edgePx: Float, stepPx: Float) {
        while (true) {
            val dragged = draggedItem()
            if (draggedKey != null && dragged != null) {
                val info = listState.layoutInfo
                val delta = when {
                    dragTop < info.viewportStartOffset + edgePx -> -stepPx
                    dragTop + dragged.size > info.viewportEndOffset - edgePx -> stepPx
                    else -> 0f
                }
                if (delta != 0f) {
                    listState.scrollBy(delta)
                    swapIfNeeded()
                }
            }
            withFrameNanos { }
        }
    }
}

@Composable
internal fun rememberReorderState(
    listState: LazyListState,
    movableCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderState {
    val haptics = LocalHapticFeedback.current
    val onMoveState = rememberUpdatedState(onMove)
    val movableCountState = rememberUpdatedState(movableCount)
    val state = remember(listState) { ReorderState(listState, haptics, onMoveState, movableCountState) }
    val density = LocalDensity.current
    LaunchedEffect(state) {
        state.autoScroll(edgePx = with(density) { 48.dp.toPx() }, stepPx = with(density) { 8.dp.toPx() })
    }
    return state
}

/** Lets a row be picked up with a long press and dragged to another place, while [enabled]. */
internal fun Modifier.reorderable(state: ReorderState, key: Any, enabled: Boolean): Modifier =
    this
        .zIndex(if (state.draggedKey == key) 1f else 0f)
        .graphicsLayer { translationY = state.translationOf(key) }
        .pointerInput(state, key, enabled) {
            if (!enabled) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key) },
                onDragEnd = { state.stop() },
                onDragCancel = { state.stop() },
                onDrag = { change, amount ->
                    change.consume()
                    state.dragBy(amount.y)
                },
            )
        }
