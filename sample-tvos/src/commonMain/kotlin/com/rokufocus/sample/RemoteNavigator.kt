package com.rokufocus.sample

import com.rokufocus.RokuColumnState
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuFocusListState
import com.rokufocus.rokuMoveBy
import com.rokufocus.rokuMoveRowsBy
import com.rokufocus.stepsForVelocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

internal enum class PanAxis { Horizontal, Vertical }

/**
 * Turns a stream of [RemotePanEvent]s into selection moves the way the tvOS focus engine does.
 *
 * Two phases, both of which the native rails in Apple TV+ have and a lift-off-only reading of
 * the gesture cannot reproduce:
 *
 * - **Drag.** While the finger is down, every [stepPoints] of travel along the locked axis moves
 *   the selection one item, so the highlight follows the thumb. Travel that arrives faster than
 *   one item per report is applied as a single coalesced [rokuMoveBy] rather than a burst.
 * - **Fling.** At lift-off, a velocity at or above [RokuFocusConfig.swipeVelocityThreshold]
 *   coasts on for [stepsForVelocity] more items, one at a time on a decelerating schedule, so
 *   the last items arrive slower than the first. Touching the pad again stops the coast, as it
 *   stops a native scroll view.
 *
 * Direction is the screen's: swiping right moves the selection to the right, swiping down moves
 * it down.
 */
internal class RemoteNavigator(
    private val scope: CoroutineScope,
    private val config: (PanAxis) -> RokuFocusConfig,
    private val columnState: RokuColumnState,
    private val rowStates: () -> List<RokuFocusListState>,
    private val stepPoints: (PanAxis) -> Float,
    private val onReport: (String) -> Unit,
) {
    private var axis: PanAxis? = null
    private var totalX = 0f
    private var totalY = 0f
    private var travel = 0f
    private var draggedSteps = 0
    private var fling: Job? = null

    fun onEvent(event: RemotePanEvent) {
        when (event) {
            RemotePanEvent.Began -> begin()
            is RemotePanEvent.Changed -> drag(event.dx, event.dy)
            is RemotePanEvent.Ended -> release(event.velocityX, event.velocityY)
            RemotePanEvent.Cancelled -> axis = null
        }
    }

    private fun begin() {
        fling?.cancel()
        axis = null
        totalX = 0f
        totalY = 0f
        travel = 0f
        draggedSteps = 0
    }

    private fun drag(dx: Float, dy: Float) {
        totalX += dx
        totalY += dy
        val current = axis
        if (current == null) {
            if (totalX * totalX + totalY * totalY < AxisLockPoints * AxisLockPoints) return
            val locked = dominant(totalX, totalY)
            axis = locked
            travel = along(locked, totalX, totalY)
        } else {
            travel += along(current, dx, dy)
        }
        stepFromTravel()
    }

    private fun stepFromTravel() {
        val current = axis ?: return
        val step = stepPoints(current)
        if (step <= 0f) return
        val count = (abs(travel) / step).toInt()
        if (count == 0) return
        val direction = if (travel > 0f) 1 else -1
        travel -= direction * count * step
        if (move(current, direction * count)) draggedSteps += count
    }

    private fun release(velocityX: Float, velocityY: Float) {
        val current = axis ?: dominant(velocityX, velocityY)
        val velocity = along(current, velocityX, velocityY)
        val flingConfig = config(current)
        val flingSteps =
            if (abs(velocity) < flingConfig.swipeVelocityThreshold) 0 else flingConfig.stepsForVelocity(velocity)

        val distance = along(current, totalX, totalY)
        val arrows = if (current == PanAxis.Horizontal) "◀▶" else "▲▼"
        onReport(
            "$arrows drag ${distance.toInt()} pt → $draggedSteps step${plural(draggedSteps)} · " +
                "flick ${abs(velocity).toInt()} pt/s → $flingSteps step${plural(flingSteps)}"
        )
        println("[roku] pan axis=$current drag=${distance.toInt()}pt dragged=$draggedSteps velocity=${velocity.toInt()} fling=$flingSteps")

        if (flingSteps == 0) return
        val direction = if (velocity > 0f) 1 else -1
        fling = scope.launch {
            for (step in 1..flingSteps) {
                delay(flingDelayMs(step))
                if (!move(current, direction)) break
            }
        }
    }

    private fun move(axis: PanAxis, steps: Int): Boolean {
        var moved = false
        val moveConfig = config(axis)
        when (axis) {
            PanAxis.Horizontal -> rowStates().getOrNull(columnState.selectedRowIndex)?.let { row ->
                rokuMoveBy(row, moveConfig, steps, onSelected = { moved = true })
            }

            PanAxis.Vertical -> rokuMoveRowsBy(columnState, moveConfig, steps, onSelected = { moved = true })
        }
        println("[roku] move axis=$axis steps=$steps moved=$moved row=${columnState.selectedRowIndex}")
        return moved
    }

    private fun dominant(x: Float, y: Float): PanAxis =
        if (abs(x) >= abs(y)) PanAxis.Horizontal else PanAxis.Vertical

    private fun along(axis: PanAxis, x: Float, y: Float): Float =
        if (axis == PanAxis.Horizontal) x else y

    private fun plural(count: Int): String = if (count == 1) "" else "s"

    private fun flingDelayMs(step: Int): Long =
        (FirstFlingDelayMs * FlingDelayGrowth.pow(step - 1)).toLong()
}

/** Travel before a contact commits to an axis; below this a touch is a rest or a click roll. */
private const val AxisLockPoints = 24f

/** Gap before the first coasting item, then each gap is this much longer than the last. */
private const val FirstFlingDelayMs = 90f
private const val FlingDelayGrowth = 1.5f
