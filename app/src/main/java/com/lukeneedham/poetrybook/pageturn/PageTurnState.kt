package com.lukeneedham.poetrybook.pageturn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import com.lukeneedham.common.debug.AnimationSpeed
import com.lukeneedham.common.debug.scale
import com.lukeneedham.common.ui.LocalAnimationSpeed
import com.lukeneedham.poetrybook.pageturn.geometry.Point
import com.lukeneedham.poetrybook.pageturn.turn.CurlOrigin
import com.lukeneedham.poetrybook.pageturn.turn.PageTurnPhysicsConfig
import com.lukeneedham.poetrybook.pageturn.turn.SettleTarget
import com.lukeneedham.poetrybook.pageturn.turn.TurnDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A page that is part-way between two pages. [pointer] is the curl handle in page coordinates and
 * [anchorY] is where on the outer edge the finger grabbed the sheet.
 */
internal data class ActiveTurn(
    val direction: TurnDirection,
    val target: Int,
    val pointer: Point,
    val origin: CurlOrigin,
    val anchorY: Double,
)

/**
 * Holds which page is showing and drives the page-turn animation between pages.
 *
 * Every page is full-size and the book is always shown one page at a time, like TurnThePage's
 * single-page presentation.
 */
@Stable
class PageTurnState internal constructor(
    initialPage: Int,
    private val scope: CoroutineScope,
) {
    var currentPage: Int by mutableIntStateOf(initialPage)
        private set

    internal var turn: ActiveTurn? by mutableStateOf(null)
        private set

    internal var pageSize: Size = Size.Zero
    internal var pageCount: Int = 0

    private var settleJob: Job? = null

    val isTurning: Boolean
        get() = turn != null

    internal val physics = PageTurnPhysicsConfig()

    /** The debug animation speed, applied to how long a turn takes to settle. Kept up to date by [rememberPageTurnState]. */
    internal var animationSpeed: AnimationSpeed = AnimationSpeed.X1

    internal fun canTurn(direction: TurnDirection): Boolean = when (direction) {
        TurnDirection.FORWARD -> currentPage < pageCount - 1
        TurnDirection.BACKWARD -> currentPage > 0
    }

    /** Shows [page] immediately, cancelling any turn in progress. */
    fun snapTo(page: Int) {
        settleJob?.cancel()
        turn = null
        currentPage = page.coerceAtLeast(0)
    }

    /** A far-away page being got ready for [animateTo], so it exists before its turn starts. */
    internal var warmPage: Int? by mutableStateOf(null)
        private set

    /** Turns pages, with the curl animation, until [page] is showing. */
    fun animateTo(page: Int) {
        if (turn != null || page == currentPage) return
        if (pageSize.width <= 0f || pageSize.height <= 0f) {
            snapTo(page)
            return
        }
        if (abs(page - currentPage) <= 1) {
            startTurn(page)
            return
        }
        // The pages either side of the current one are always ready, but a far page isn't. Have it
        // laid out and recorded first, so the turn doesn't start by building a page mid-animation.
        val from = currentPage
        scope.launch {
            warmPage = page
            withFrameNanos { }
            withFrameNanos { }
            if (turn == null && currentPage == from) startTurn(page)
            warmPage = null
        }
    }

    private fun startTurn(page: Int) {
        val direction = if (page > currentPage) TurnDirection.FORWARD else TurnDirection.BACKWARD
        val width = pageSize.width.toDouble()
        val bottom = pageSize.height.toDouble()
        val grabX = if (direction == TurnDirection.FORWARD) width else 0.0
        val nudge = if (direction == TurnDirection.FORWARD) -1.5 else 1.5
        turn = ActiveTurn(
            direction = direction,
            target = page,
            pointer = Point(grabX + nudge, bottom - 1.5),
            origin = CurlOrigin.LOWER_OUTER,
            anchorY = bottom,
        )
        settle(SettleTarget.COMPLETE)
    }

    /** Turns one page forward ([delta] 1) or back (-1), if there is a page to turn to. */
    internal fun turnBy(delta: Int) {
        val direction = if (delta > 0) TurnDirection.FORWARD else TurnDirection.BACKWARD
        if (canTurn(direction)) animateTo(currentPage + delta)
    }

    internal fun beginDrag(direction: TurnDirection, anchorY: Double) {
        settleJob?.cancel()
        val target = if (direction == TurnDirection.FORWARD) currentPage + 1 else currentPage - 1
        turn = ActiveTurn(
            direction = direction,
            target = target,
            pointer = Point(grabX(direction), anchorY),
            origin = CurlOrigin.OUTER_EDGE,
            anchorY = anchorY,
        )
    }

    /** [dx] and [dy] are the finger's displacement since the drag started. */
    internal fun drag(dx: Float, dy: Float) {
        val active = turn ?: return
        val maxVertical = pageSize.width * VERTICAL_DRAG_RATIO
        turn = active.copy(
            pointer = Point(
                x = grabX(active.direction) + dx,
                y = active.anchorY + dy.coerceIn(-maxVertical, maxVertical),
            ),
        )
    }

    internal fun settle(target: SettleTarget) {
        val active = turn ?: return
        val width = pageSize.width.toDouble()
        val grabX = grabX(active.direction)
        // Completing a turn carries the handle across the page to the mirrored position.
        val endX = when {
            target == SettleTarget.CANCEL -> grabX
            active.direction == TurnDirection.FORWARD -> -width
            else -> 2.0 * width
        }
        val start = active.pointer
        val end = Point(endX, active.anchorY)
        val duration = animationSpeed.scale(
            if (target == SettleTarget.COMPLETE) {
                physics.maxSettleDurationMs
            } else {
                (physics.maxSettleDurationMs * 0.72f).roundToInt()
            }.coerceAtLeast(physics.minSettleDurationMs),
        )
        val easing = if (target == SettleTarget.COMPLETE) {
            CubicBezierEasing(0.18f, 0.72f, 0.18f, 1f)
        } else {
            CubicBezierEasing(0.22f, 0.78f, 0.34f, 1f)
        }

        settleJob?.cancel()
        settleJob = scope.launch {
            Animatable(0f).animateTo(1f, tween(duration, easing = easing)) {
                turn = active.copy(
                    pointer = Point(
                        x = start.x + (end.x - start.x) * value,
                        y = start.y + (end.y - start.y) * value,
                    ),
                )
            }
            if (target == SettleTarget.COMPLETE) currentPage = active.target
            turn = null
        }
    }

    private fun grabX(direction: TurnDirection): Double =
        if (direction == TurnDirection.FORWARD) pageSize.width.toDouble() else 0.0

    private companion object {
        /** Limits how far the curl handle can wander vertically, relative to page width. */
        const val VERTICAL_DRAG_RATIO = 0.14f
    }
}

@Composable
fun rememberPageTurnState(initialPage: Int = 0): PageTurnState {
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(
        scope,
        saver = Saver(
            save = { it.currentPage },
            restore = { PageTurnState(it, scope) },
        ),
    ) { PageTurnState(initialPage, scope) }
    val animationSpeed = LocalAnimationSpeed.current
    SideEffect { state.animationSpeed = animationSpeed }
    return state
}
