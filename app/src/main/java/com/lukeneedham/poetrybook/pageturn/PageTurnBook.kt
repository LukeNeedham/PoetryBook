package com.lukeneedham.poetrybook.pageturn

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** Taps within this fraction of the page width from the left or right side turn the page. */
private const val TAP_EDGE_FRACTION = 0.33f

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
    // Pages can disappear (a poem deleted, another folder opened); never stay past the last one.
    LaunchedEffect(pageCount) {
        if (pageCount > 0 && state.currentPage > pageCount - 1 && !state.isTurning) {
            state.snapTo(pageCount - 1)
        }
    }

    val layers = remember { mutableMapOf<Int, GraphicsLayer>() }
    val geometryEngine = remember { PageCurlGeometryEngine() }
    val bandEngine = remember { PageCurlBandGeometryEngine() }
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val tapTimeout = viewConfiguration.longPressTimeoutMillis
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
            .pointerInput(state, gesturesEnabled, touchSlop, tapTimeout) {
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
                                if (!change.pressed) {
                                    // A quick tap on either edge turns the page, like in TurnThePage.
                                    val quick = change.uptimeMillis - down.uptimeMillis < tapTimeout
                                    if (quick && !change.isConsumed && displacement.getDistance() < touchSlop) {
                                        val edge = state.pageSize.width * TAP_EDGE_FRACTION
                                        when {
                                            down.position.x < edge -> state.turnBy(-1)
                                            down.position.x > state.pageSize.width - edge -> state.turnBy(1)
                                        }
                                    }
                                    return@awaitEachGesture
                                }
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
        // Both neighbours stay composed and recorded, so a turn can start on any frame without first
        // having to build the incoming page. The current page goes last so it is on top.
        val current = state.currentPage
        val others = listOfNotNull(current - 1, current + 1, targetPage)
            .filter { it != current && it in 0 until pageCount }
            .distinct()
        (others + current).forEach { index ->
            key(index) {
                RecordedPage(
                    index = index,
                    isCurrent = index == current,
                    state = state,
                    layers = layers,
                ) { page(index) }
            }
        }
    }
}

/**
 * Lays out [content] and records it into a layer for [drawTurn] to fold. Only the current page
 * is drawn, and only while no turn is in progress; the others are kept ready but unseen, and
 * can't be touched or read out by accessibility services.
 */
@Composable
private fun RecordedPage(
    index: Int,
    isCurrent: Boolean,
    state: PageTurnState,
    layers: MutableMap<Int, GraphicsLayer>,
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    DisposableEffect(index, layer) {
        onDispose { if (layers[index] === layer) layers.remove(index) }
    }
    // The turn changes every frame; whether there is one only at its start and end. Reading the
    // turn itself here would re-record the page on every frame of the animation.
    val turning by remember(state) { derivedStateOf { state.turn != null } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isCurrent) Modifier else Modifier.clearAndSetSemantics { })
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                layers[index] = layer
                if (isCurrent && !turning) drawLayer(layer)
            },
    ) {
        content()
        // A page waiting its turn takes no part in input.
        if (!isCurrent) Box(Modifier.matchParentSize().pointerInput(Unit) { })
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
