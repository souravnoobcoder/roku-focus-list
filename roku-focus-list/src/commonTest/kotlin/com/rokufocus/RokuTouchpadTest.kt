package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuTouchpadTest {

    private class RecordingTarget(
        private val stepPx: Float = 300f,
        private val canMove: (steps: Int) -> Boolean = { true },
        private val canMoveRows: (steps: Int) -> Boolean = canMove
    ) : RokuTouchTarget {
        val itemMoves = mutableListOf<Int>()
        val rowMoves = mutableListOf<Int>()

        override fun stepPx(orientation: Orientation): Float = stepPx

        override fun moveItems(steps: Int): Boolean {
            itemMoves += steps
            return canMove(steps)
        }

        override fun moveRows(steps: Int): Boolean {
            rowMoves += steps
            return canMoveRows(steps)
        }

        override fun canMove(orientation: Orientation, forward: Boolean): Boolean {
            val steps = if (forward) 1 else -1
            return if (orientation == Orientation.Horizontal) canMove(steps) else canMoveRows(steps)
        }
    }

    /** One item per full step on both axes and no acceleration, so the arithmetic reads plainly. */
    private val plain = RokuTouchpadConfig(itemStepFraction = 1f, rowStepFraction = 1f, maxGain = 1f)

    private fun touchpad(target: RokuTouchTarget, config: RokuTouchpadConfig = plain): RokuTouchpad =
        RokuTouchpad(config).also { it.bind(target) }

    private fun RokuTouchpad.drag(dx: Float, dy: Float, speed: Float = 1000f) =
        panChanged(dx, dy, if (dx != 0f) speed else 0f, if (dy != 0f) speed else 0f)

    @Test
    fun aBrushOfThePadLeansTheCardWithoutMovingFocus() {
        val target = RecordingTarget()
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 8f, dy = 0f)

        assertTrue(target.itemMoves.isEmpty(), "below the axis lock nothing moves")
        assertTrue(pad.lean.x > 0f && pad.lean.y == 0f, "but the card already leans the way the thumb went: ${pad.lean}")
        assertTrue(pad.isDragging)
    }

    @Test
    fun oneStepOfTravelMovesOneItemAndLeavesTheRemainderAsLean() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 310f, dy = 0f)

        assertEquals(listOf(1), target.itemMoves)
        assertEquals(10f / 300f, pad.lean.x, 0.001f)
    }

    @Test
    fun travelFasterThanOneItemPerReportIsOneCoalescedMove() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 950f, dy = 0f)

        assertEquals(listOf(3), target.itemMoves, "three items, one call — not three calls")
    }

    @Test
    fun liftingTheThumbStopsEverythingAndSpringsTheCardBack() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 310f, dy = 0f, speed = 20_000f)
        pad.panEnded()

        assertEquals(listOf(1), target.itemMoves, "a hard flick adds nothing after lift-off")
        assertEquals(Offset.Zero, pad.lean)
        assertFalse(pad.isDragging)
    }

    @Test
    fun directionIsTheScreensOnBothAxes() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = -310f, dy = 0f)
        assertEquals(listOf(-1), target.itemMoves, "swipe left goes toward the start")

        val down = RecordingTarget(stepPx = 300f)
        val vertical = touchpad(down)
        vertical.panBegan()
        vertical.drag(dx = 0f, dy = 320f)
        assertEquals(listOf(1), down.rowMoves, "swipe down goes to the next row")
        assertTrue(down.itemMoves.isEmpty())
    }

    @Test
    fun theAxisLocksOnTheFirstDominantDirection() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 30f, dy = 5f)
        pad.drag(dx = 0f, dy = 400f)

        assertTrue(target.rowMoves.isEmpty(), "a horizontal contact ignores later vertical drift")
    }

    @Test
    fun aFastThumbCoversMoreGroundThroughTheGain() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(
            target,
            plain.copy(gainStartVelocity = 4000f, gainMaxVelocity = 8000f, maxGain = 2f)
        )
        pad.panBegan()
        pad.drag(dx = 160f, dy = 0f, speed = 9000f)

        assertEquals(listOf(1), target.itemMoves, "160 units at double gain is a full step")
    }

    @Test
    fun anEdgePinsTheLeanAndPullingBackMovesAfterOneStep() {
        // A right-hand edge: moving toward the start still works.
        val target = RecordingTarget(stepPx = 300f, canMove = { it < 0 })
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 310f, dy = 0f)
        assertEquals(1f, pad.lean.x, "full pull the moment the edge refuses")

        pad.drag(dx = 900f, dy = 0f)
        assertEquals(listOf(1), target.itemMoves, "pushing further is dropped, not retried")
        assertEquals(1f, pad.lean.x, "and the lean stays at full pull")

        pad.drag(dx = -400f, dy = 0f)
        assertEquals(listOf(1, -1), target.itemMoves, "one step of travel back moves back — no wind-up to unwind")
        assertEquals(-100f / 300f, pad.lean.x, 0.001f)
    }

    @Test
    fun aNewTouchStartsClean() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.panBegan()
        pad.drag(dx = 250f, dy = 0f)
        pad.panEnded()
        pad.panBegan()
        pad.drag(dx = 100f, dy = 0f)

        assertTrue(target.itemMoves.isEmpty(), "pending travel from the last contact does not carry over")
        assertEquals(100f / 300f, pad.lean.x, 0.001f)
    }

    @Test
    fun pxPerUnitScalesStepsIntoTheHostsUnits() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target).apply { pxPerUnit = 2f }
        pad.panBegan()
        pad.drag(dx = 160f, dy = 0f)

        assertEquals(listOf(1), target.itemMoves, "a 300 px pitch is 150 points on a 2x screen")
    }

    @Test
    fun theStepFractionsShortenEachAxisIndependently() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target, plain.copy(itemStepFraction = 0.5f, rowStepFraction = 1f))
        pad.panBegan()
        pad.drag(dx = 160f, dy = 0f)
        assertEquals(listOf(1), target.itemMoves, "half a pitch sideways is a step")

        val rows = RecordingTarget(stepPx = 300f)
        val vertical = touchpad(rows, plain.copy(itemStepFraction = 0.5f, rowStepFraction = 1f))
        vertical.panBegan()
        vertical.drag(dx = 0f, dy = 160f)
        assertTrue(rows.rowMoves.isEmpty(), "but rows still need a full pitch")
        vertical.drag(dx = 0f, dy = 160f)
        assertEquals(listOf(1), rows.rowMoves)
    }

    @Test
    fun aReportWithoutABeganStartsAContact() {
        val target = RecordingTarget(stepPx = 300f)
        val pad = touchpad(target)
        pad.drag(dx = 310f, dy = 0f)

        assertTrue(pad.isDragging)
        assertEquals(listOf(1), target.itemMoves)
    }

    @Test
    fun withoutAFocusedComponentNothingMovesAndTheCardStaysFlat() {
        val pad = RokuTouchpad(plain)
        pad.panBegan()
        pad.drag(dx = 900f, dy = 0f)

        assertEquals(Offset.Zero, pad.lean, "no step size to lean against")
        pad.panEnded()
    }

    @Test
    fun unbindingAnotherTargetLeavesTheBoundOneInPlace() {
        val bound = RecordingTarget(stepPx = 300f)
        val other = RecordingTarget(stepPx = 300f)
        val pad = touchpad(bound)
        pad.unbind(other)
        pad.panBegan()
        pad.drag(dx = 310f, dy = 0f)

        assertEquals(listOf(1), bound.itemMoves)
        assertTrue(other.itemMoves.isEmpty())
    }

    @Test
    fun theKeyGuardOwnsTheKeyForASwipeItApplied() {
        val pad = touchpad(RecordingTarget(stepPx = 300f))
        val now = RokuClock.uptimeMillis()
        assertFalse(pad.swallowsKey(Orientation.Horizontal, forward = true, now), "a fresh touchpad has swallowed nothing")

        pad.panBegan()
        pad.drag(dx = 310f, dy = 0f)
        pad.panEnded()
        val after = RokuClock.uptimeMillis()
        assertTrue(
            pad.swallowsKey(Orientation.Horizontal, forward = true, after),
            "the fork's key for a swipe that already moved the selection is its echo"
        )
        assertFalse(
            pad.swallowsKey(Orientation.Horizontal, forward = true, after + KeyLeakWindowMs + 1),
            "and a real press later is let through"
        )
    }

    @Test
    fun theKeyGuardOwnsTheKeyWhileAContactCanStillMove() {
        // The fork may dispatch its key mid-contact, before the thumb has travelled a full step.
        val pad = touchpad(RecordingTarget(stepPx = 300f))
        pad.panBegan()
        pad.drag(dx = 40f, dy = 0f)
        assertTrue(
            pad.swallowsKey(Orientation.Horizontal, forward = true, RokuClock.uptimeMillis()),
            "the selection can move right under the thumb, so the key is the touchpad's"
        )
        pad.panEnded()
    }

    @Test
    fun theKeyGuardLetsTheKeyThroughAtAnOpenEdge() {
        // Nothing to the left: the swipe pins, and the fork's Left key has to reach the key
        // handler so focusEscape can let focus leave the list.
        val pad = touchpad(RecordingTarget(stepPx = 300f, canMove = { steps -> steps > 0 }))
        pad.panBegan()
        pad.drag(dx = -310f, dy = 0f)
        pad.panEnded()
        val now = RokuClock.uptimeMillis()
        assertFalse(pad.swallowsKey(Orientation.Horizontal, forward = false, now), "Left escapes the row")
        assertTrue(pad.swallowsKey(Orientation.Horizontal, forward = true, now), "Right is still the touchpad's")
    }

    @Test
    fun theKeyGuardLetsAKeyAcrossTheRowsAxisThrough() {
        // A lone rail cannot move vertically, so a vertical flick's key is how focus leaves it.
        val pad = touchpad(RecordingTarget(stepPx = 300f, canMoveRows = { false }))
        pad.panBegan()
        pad.drag(dx = 0f, dy = 310f)
        pad.panEnded()
        assertFalse(
            pad.swallowsKey(Orientation.Vertical, forward = true, RokuClock.uptimeMillis()),
            "Down leaves the rail"
        )
    }

    @Test
    fun theKeyGuardIsInertWithoutAFocusedComponent() {
        val pad = RokuTouchpad(plain)
        pad.panBegan()
        pad.drag(dx = 310f, dy = 0f)
        pad.panEnded()
        assertFalse(
            pad.swallowsKey(Orientation.Horizontal, forward = true, RokuClock.uptimeMillis()),
            "with nothing bound the key is the only thing that can move focus"
        )
    }

    @Test
    fun aStepFloorKeepsSmallItemsFromFlyingUnderTheThumb() {
        // 40-unit keys: at the default fraction a key is 26 units of travel. With a 120-unit
        // floor it takes 120, whatever the pitch, and a pitch of 0 still never moves.
        val keys = RecordingTarget(stepPx = 40f)
        val pad = touchpad(keys, RokuTouchpadConfig(itemStepFraction = 0.65f, maxGain = 1f, minItemStepUnits = 120f))
        pad.panBegan()
        pad.drag(dx = 100f, dy = 0f)
        assertEquals(emptyList(), keys.itemMoves, "less than the floor leans, it does not move")
        pad.drag(dx = 25f, dy = 0f)
        assertEquals(listOf(1), keys.itemMoves, "the floor is one key")
        pad.panEnded()

        val rail = RecordingTarget(stepPx = 0f)
        val railPad = touchpad(rail, RokuTouchpadConfig(minRowStepUnits = 120f, maxGain = 1f))
        railPad.panBegan()
        railPad.drag(dx = 0f, dy = 400f)
        assertEquals(emptyList(), rail.rowMoves, "no pitch means no step, floor or not")
        railPad.panEnded()
    }

    @Test
    fun theGainIsUnityBelowTheStartSpeedAndMaxAboveTheTopSpeed() {
        val config = RokuTouchpadConfig(gainStartVelocity = 2500f, gainMaxVelocity = 12000f, maxGain = 2f)
        assertEquals(1f, config.gain(0f))
        assertEquals(1f, config.gain(2500f))
        assertEquals(2f, config.gain(12000f))
        assertEquals(2f, config.gain(50_000f))
        assertEquals(1.5f, config.gain(7250f), 0.001f, "smoothstep is 0.5 at the midpoint")

        val samples = (0..40).map { config.gain(it * 400f) }
        assertEquals(samples.sorted(), samples, "gain never drops as the thumb speeds up: $samples")
    }

    @Test
    fun aDegenerateGainRangeStillAnswers() {
        val flat = RokuTouchpadConfig(gainStartVelocity = 5000f, gainMaxVelocity = 5000f, maxGain = 2f)
        assertEquals(1f, flat.gain(4999f))
        assertEquals(2f, flat.gain(5000f))
    }
}
