package com.lukeneedham.poetrybook.pageturn

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.toSize
import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlBandGeometryEngine
import com.lukeneedham.poetrybook.pageturn.geometry.PageCurlGeometryEngine
import com.lukeneedham.poetrybook.pageturn.geometry.Point
import com.lukeneedham.poetrybook.pageturn.geometry.calculateReturningPage
import com.lukeneedham.poetrybook.pageturn.turn.TurnDirection
import com.lukeneedham.poetrybook.pageturn.turn.decideSettleTarget
import kotlin.math.abs
import com.lukeneedham.poetrybook.pageturn.geometry.Rect as CurlRect

/**
 * A book that shows [pageCount] full-size pages, one at a time. Swiping across the page peels it
 * off with a page-curl effect; [state] can also turn pages programmatically.
 *
 * Pages are ordinary composables. While a turn is in progress, the pages involved are drawn
 * through [GraphicsLayer]s so the curl can fold the live content without re-laying it out.
 *
 * @param gesturesEnabled when false the pages can't be swiped, e.g. while a page is being edited.
 */
@Composable
fun PageTurnBook(
    state: PageTurnState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    page: @Composable (index: Int) -> Unit,
) {
    SideEffect { state.pageCount = pageCount }

    val layers = remember { mutableMapOf<Int, GraphicsLayer>() }
    val geometryEngine = remember { PageCurlGeometryEngine() }
    val bandEngine = remember { PageCurlBandGeometryEngine() }
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // Only the target page index matters to composition; the pointer position changes every
    // frame during a turn and is read only while drawing.
    val targetPage by remember(state) { derivedStateOf { state.turn?.target } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { state.pageSize = it.toSize() }
            .drawWithContent {
                drawContent()
                state.turn?.let { turn ->
                    drawTurn(turn, state.currentPage, layers, geometryEngine, bandEngine)
                }
            }
            .pointerInput(state, gesturesEnabled, touchSlop) {
                if (!gesturesEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (state.isTurning) {
                        waitUntilReleased(down.id)
                        return@awaitEachGesture
                    }

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var direction: TurnDirection? = null
                    var displacement = Offset.Zero
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            displacement = change.position - down.position

                            if (direction == null) {
                                if (!change.pressed) return@awaitEachGesture
                                // Something else, such as a scrolling poem, took the gesture.
                                if (change.isConsumed) return@awaitEachGesture
                                val horizontal = abs(displacement.x)
                                val vertical = abs(displacement.y)
                                if (vertical >= touchSlop && vertical > horizontal) {
                                    waitUntilReleased(down.id)
                                    return@awaitEachGesture
                                }
                                if (horizontal >= touchSlop && horizontal >= vertical * 0.6f) {
                                    val candidate = if (displacement.x < 0f) {
                                        TurnDirection.FORWARD
                                    } else {
                                        TurnDirection.BACKWARD
                                    }
                                    if (!state.canTurn(candidate)) {
                                        waitUntilReleased(down.id)
                                        return@awaitEachGesture
                                    }
                                    direction = candidate
                                    state.beginDrag(candidate, down.position.y.toDouble())
                                }
                            }

                            if (direction != null) {
                                state.drag(displacement.x, displacement.y)
                                change.consume()
                                if (!change.pressed) break
                            }
                        }
                    } finally {
                        direction?.let {
                            val velocity = tracker.calculateVelocity()
                            state.settle(
                                decideSettleTarget(
                                    direction = it,
                                    pointerDisplacement = Point(
                                        displacement.x.toDouble(),
                                        displacement.y.toDouble(),
                                    ),
                                    velocityPxPerSecond = Point(
                                        velocity.x.toDouble(),
                                        velocity.y.toDouble(),
                                    ),
                                    pageWidthPx = state.pageSize.width.toDouble(),
                                    config = state.physics,
                                ),
                            )
                        }
                    }
                }
            },
    ) {
        val visible = listOfNotNull(state.currentPage, targetPage).distinct()
        visible.forEach { index ->
            key(index) {
                RecordedPage(index = index, state = state, layers = layers) { page(index) }
            }
        }
    }
}

/**
 * Draws [content] normally while the book is at rest, and otherwise only records it into a layer
 * for [drawTurn] to fold.
 */
@Composable
private fun RecordedPage(
    index: Int,
    state: PageTurnState,
    layers: MutableMap<Int, GraphicsLayer>,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(index, layer) {
        onDispose { if (layers[index] === layer) layers.remove(index) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                layers[index] = layer
                if (state.turn == null) drawLayer(layer)
            },
    ) {
        content()
    }
}

private fun DrawScope.drawTurn(
    turn: ActiveTurn,
    currentPage: Int,
    layers: Map<Int, GraphicsLayer>,
    geometryEngine: PageCurlGeometryEngine,
    bandEngine: PageCurlBandGeometryEngine,
) {
    val currentLayer = layers[currentPage]
    val targetLayer = layers[turn.target]
    if (currentLayer == null || targetLayer == null) {
        // The incoming page hasn't been recorded yet; hold the page that is.
        (currentLayer ?: targetLayer)?.let { drawLayer(it) }
        return
    }

    val bounds = CurlRect(0.0, 0.0, size.width.toDouble(), size.height.toDouble())
    val forward = turn.direction == TurnDirection.FORWARD
    val geometry = if (forward) {
        geometryEngine.calculate(
            pageBounds = bounds,
            pointer = turn.pointer,
            direction = TurnDirection.FORWARD,
            origin = turn.origin,
            edgeAnchorY = turn.anchorY,
        )
    } else {
        // Going back rewinds a forward turn: the previous page swings in from the left binding
        // and lands flat on top of the current one.
        geometryEngine.calculateReturningPage(
            pageBounds = bounds,
            pointer = turn.pointer,
            origin = turn.origin,
            edgeAnchorY = turn.anchorY,
        )
    }

    val base = if (forward) targetLayer else currentLayer
    val moving = if (forward) currentLayer else targetLayer
    val movingBack = if (forward) targetLayer else currentLayer

    drawLayer(base)
    val flatPath = geometry.flatFront.toPath()
    if (!flatPath.isEmpty) {
        clipPath(flatPath) { drawLayer(moving) }
    }
    drawCurl(
        geometry = geometry,
        bounds = bounds.toRect(),
        back = movingBack,
        bandEngine = bandEngine,
    )
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitUntilReleased(
    pointerId: PointerId,
) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
    }
}

private fun CurlRect.toRect(): androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect(
    left = left.toFloat(),
    top = top.toFloat(),
    right = right.toFloat(),
    bottom = bottom.toFloat(),
)
