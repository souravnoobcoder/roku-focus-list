package com.rokufocus.sample

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

internal enum class PanAxis { Horizontal, Vertical }

/**
 * Turns a stream of [RemotePanEvent]s into moves on a [SwipeTarget] the way the tvOS focus engine
 * does, and the way Apple TV+ feels as a result:
 *
 * - **Focus follows the thumb, and only the thumb.** Every [stepPoints] of travel along the locked
 *   axis moves the selection one item. Travel that arrives faster than one item per report is
 *   applied as a single coalesced move rather than a burst. Nothing moves after the finger lifts:
 *   there is no coast.
 * - **A fast finger covers more ground.** Travel is scaled by [dragGain], which rises smoothly with
 *   the finger's speed, so a hard swipe crosses several items while a careful one walks them.
 * - **Small movement is never lost.** Travel that has not yet reached a full step is reported
 *   through [onHint] as a fraction of a step in [-1, 1] per axis, so the focused card can lean
 *   toward the thumb and spring back — the native focus-movement hint. It starts with the very
 *   first report, before the contact has travelled far enough to commit to an axis, so even a
 *   brush of the pad is answered. At the end of a row the fraction pins at full pull instead of
 *   winding up.
 *
 * Direction is the screen's: swiping right moves the selection to the right, swiping down moves
 * it down.
 */
internal class RemoteNavigator(
    private val target: SwipeTarget,
    private val stepPoints: (PanAxis) -> Float,
    private val dragGain: (speed: Float) -> Float,
    private val onHint: (Offset) -> Unit,
    private val onReport: (String) -> Unit,
) {
    private var axis: PanAxis? = null
    private var totalX = 0f
    private var totalY = 0f
    private var travel = 0f
    private var draggedSteps = 0
    private var peakSpeed = 0f

    fun onEvent(event: RemotePanEvent) {
        when (event) {
            RemotePanEvent.Began -> begin()
            is RemotePanEvent.Changed -> drag(event.dx, event.dy, event.velocityX, event.velocityY)
            is RemotePanEvent.Ended -> release()
            RemotePanEvent.Cancelled -> release()
        }
    }

    private fun begin() {
        axis = null
        totalX = 0f
        totalY = 0f
        travel = 0f
        draggedSteps = 0
        peakSpeed = 0f
    }

    private fun drag(dx: Float, dy: Float, velocityX: Float, velocityY: Float) {
        totalX += dx
        totalY += dy
        val locked = axis
        val current: PanAxis
        val delta: Float
        if (locked == null) {
            if (totalX * totalX + totalY * totalY < AxisLockPoints * AxisLockPoints) {
                // Not committed to an axis yet, but the thumb is moving: lean, do not move.
                val guess = dominant(totalX, totalY)
                onHint(hintFor(guess, along(guess, totalX, totalY)))
                return
            }
            current = dominant(totalX, totalY)
            axis = current
            // The travel that locked the axis is real travel too.
            delta = along(current, totalX, totalY)
        } else {
            current = locked
            delta = along(current, dx, dy)
        }
        val speed = abs(along(current, velocityX, velocityY))
        if (speed > peakSpeed) peakSpeed = speed
        travel += delta * dragGain(speed)
        stepFromTravel(current)
        hint(current)
    }

    private fun stepFromTravel(current: PanAxis) {
        val step = stepPoints(current)
        if (step <= 0f) return
        val count = (abs(travel) / step).toInt()
        if (count == 0) return
        val direction = if (travel > 0f) 1 else -1
        if (move(current, direction * count)) {
            travel -= direction * count * step
            draggedSteps += count
        } else {
            // End of the row: hold the hint at full pull rather than letting travel wind up.
            travel = direction * (step - 1f)
        }
    }

    private fun hint(current: PanAxis) = onHint(hintFor(current, travel))

    private fun hintFor(axis: PanAxis, pendingTravel: Float): Offset {
        val step = stepPoints(axis)
        val fraction = if (step <= 0f) 0f else (pendingTravel / step).coerceIn(-1f, 1f)
        return if (axis == PanAxis.Horizontal) Offset(fraction, 0f) else Offset(0f, fraction)
    }

    private fun release() {
        onHint(Offset.Zero)
        val current = axis ?: return
        axis = null
        val distance = along(current, totalX, totalY)
        val arrows = if (current == PanAxis.Horizontal) "◀▶" else "▲▼"
        val unit = if (current == PanAxis.Horizontal) "card" else "row"
        val gain = (dragGain(peakSpeed) * 10).toInt() / 10f
        onReport(
            "$arrows drag ${distance.toInt()} pt → $draggedSteps $unit${plural(draggedSteps)} · " +
                "peak ${peakSpeed.toInt()} pt/s, gain ×$gain"
        )
        println("[roku] pan axis=$current drag=${distance.toInt()}pt dragged=$draggedSteps peak=${peakSpeed.toInt()} gain=$gain")
    }

    private fun move(axis: PanAxis, steps: Int): Boolean {
        val moved = when (axis) {
            PanAxis.Horizontal -> target.moveItems(steps)
            PanAxis.Vertical -> target.moveRows(steps)
        }
        println("[roku] move axis=$axis steps=$steps moved=$moved")
        return moved
    }

    private fun dominant(x: Float, y: Float): PanAxis =
        if (abs(x) >= abs(y)) PanAxis.Horizontal else PanAxis.Vertical

    private fun along(axis: PanAxis, x: Float, y: Float): Float =
        if (axis == PanAxis.Horizontal) x else y

    private fun plural(count: Int): String = if (count == 1) "" else "s"
}

/**
 * Travel before a contact commits to an axis and can move the selection. Below it the thumb only
 * leans the card, so a click that rolls a little never moves focus but still gets an answer.
 */
private const val AxisLockPoints = 16f
