package com.rokufocus.sample

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteNavigatorTest {

    private class RecordingTarget(private val canMove: (steps: Int) -> Boolean = { true }) : SwipeTarget {
        val itemMoves = mutableListOf<Int>()
        val rowMoves = mutableListOf<Int>()

        override fun moveItems(steps: Int): Boolean {
            itemMoves += steps
            return canMove(steps)
        }

        override fun moveRows(steps: Int): Boolean {
            rowMoves += steps
            return canMove(steps)
        }
    }

    private val hints = mutableListOf<Offset>()
    private val reports = mutableListOf<String>()

    private fun navigator(
        target: SwipeTarget,
        step: Float = 300f,
        gain: (Float) -> Float = { 1f },
    ) = RemoteNavigator(
        target = target,
        stepPoints = { step },
        dragGain = gain,
        onHint = { hints += it },
        onReport = { reports += it },
    )

    private fun RemoteNavigator.drag(dx: Float, dy: Float, speed: Float = 1000f) =
        onEvent(RemotePanEvent.Changed(dx, dy, if (dx != 0f) speed else 0f, if (dy != 0f) speed else 0f))

    @Test
    fun aBrushOfThePadLeansTheCardWithoutMovingFocus() {
        val target = RecordingTarget()
        val n = navigator(target)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 8f, dy = 0f)

        assertTrue(target.itemMoves.isEmpty(), "below the axis lock nothing moves")
        val lean = hints.last()
        assertTrue(lean.x > 0f && lean.y == 0f, "but the card already leans the way the thumb went: $lean")
    }

    @Test
    fun oneStepOfTravelMovesOneItemAndLeavesTheRemainderAsLean() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 310f, dy = 0f)

        assertEquals(listOf(1), target.itemMoves)
        assertEquals(10f / 300f, hints.last().x, 0.001f)
    }

    @Test
    fun travelFasterThanOneItemPerReportIsOneCoalescedMove() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 950f, dy = 0f)

        assertEquals(listOf(3), target.itemMoves, "three items, one call — not three calls")
    }

    @Test
    fun liftingTheThumbStopsEverythingAndSpringsTheCardBack() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 310f, dy = 0f)
        n.onEvent(RemotePanEvent.Ended(velocityX = 20_000f, velocityY = 0f))

        assertEquals(listOf(1), target.itemMoves, "a hard flick adds nothing after lift-off")
        assertEquals(Offset.Zero, hints.last())
        assertEquals(1, reports.size)
    }

    @Test
    fun directionIsTheScreensOnBothAxes() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = -310f, dy = 0f)
        assertEquals(listOf(-1), target.itemMoves, "swipe left goes toward the start")

        val down = RecordingTarget()
        val m = navigator(down, step = 300f)
        m.onEvent(RemotePanEvent.Began)
        m.drag(dx = 0f, dy = 320f)
        assertEquals(listOf(1), down.rowMoves, "swipe down goes to the next row")
        assertTrue(down.itemMoves.isEmpty())
    }

    @Test
    fun theAxisLocksOnTheFirstDominantDirection() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 30f, dy = 5f)
        n.drag(dx = 0f, dy = 400f)

        assertTrue(target.rowMoves.isEmpty(), "a horizontal contact ignores later vertical drift")
    }

    @Test
    fun aFastThumbCoversMoreGroundThroughTheGain() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f, gain = { speed -> if (speed > 5000f) 2f else 1f })
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 160f, dy = 0f, speed = 9000f)

        assertEquals(listOf(1), target.itemMoves, "160 pt at double gain is a full step")
    }

    @Test
    fun anEdgePinsTheLeanAndPullingBackMovesAfterOneStep() {
        // A right-hand edge: moving toward the start still works.
        val target = RecordingTarget(canMove = { it < 0 })
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 310f, dy = 0f)
        assertEquals(1f, hints.last().x, "full pull the moment the edge refuses")

        n.drag(dx = 900f, dy = 0f)
        assertEquals(listOf(1), target.itemMoves, "pushing further is dropped, not retried")
        assertEquals(1f, hints.last().x, "and the lean stays at full pull")

        n.drag(dx = -400f, dy = 0f)
        assertEquals(listOf(1, -1), target.itemMoves, "one step of travel back moves back — no wind-up to unwind")
        assertEquals(-100f / 300f, hints.last().x, 0.001f)
    }

    @Test
    fun aNewTouchStartsClean() {
        val target = RecordingTarget()
        val n = navigator(target, step = 300f)
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 250f, dy = 0f)
        n.onEvent(RemotePanEvent.Ended(0f, 0f))
        n.onEvent(RemotePanEvent.Began)
        n.drag(dx = 100f, dy = 0f)

        assertTrue(target.itemMoves.isEmpty(), "pending travel from the last contact does not carry over")
        assertEquals(abs(100f / 300f), hints.last().x, 0.001f)
    }
}
