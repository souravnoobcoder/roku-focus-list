package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuMoveByTest {

    private fun row(itemCount: Int, visible: Int = 4, initialIndex: Int = 0) =
        RokuFocusListState(itemCount = itemCount, initialIndex = initialIndex)
            .also { it.visibleCount = visible }

    private fun column(rowCount: Int, isSelectable: (Int) -> Boolean = { true }) =
        RokuColumnState().also { it.syncRows(rowCount, isSelectable) }

    // ── moveBy: bounds ──

    @Test
    fun moveByCoversSeveralItemsInOneMove() {
        val s = row(itemCount = 20)
        assertTrue(s.moveBy(5))
        assertEquals(5, s.selectedIndex)

        assertTrue(s.moveBy(-3))
        assertEquals(2, s.selectedIndex)
    }

    @Test
    fun moveByZeroIsANoOp() {
        val s = row(itemCount = 20, initialIndex = 4)
        assertFalse(s.moveBy(0))
        assertEquals(4, s.selectedIndex)
    }

    @Test
    fun moveByClampsAtBothEndsWithoutLeavingAPendingRequest() {
        val s = row(itemCount = 10, initialIndex = 8)

        assertTrue(s.moveBy(99))
        assertEquals(9, s.selectedIndex)
        assertEquals(
            9, s.requestedIndex,
            "a clipped swipe must not park a request past the end, or a later-growing row jumps"
        )

        assertTrue(s.moveBy(-99))
        assertEquals(0, s.selectedIndex)
        assertEquals(0, s.requestedIndex)
    }

    @Test
    fun moveByAtTheEdgeReportsNoMovement() {
        val s = row(itemCount = 10, initialIndex = 9)
        assertFalse(s.moveBy(3))
        assertEquals(9, s.selectedIndex)

        s.scrollTo(0)
        assertFalse(s.moveBy(-3))
        assertEquals(0, s.selectedIndex)
    }

    @Test
    fun moveByOnAnEmptyOrSingleItemRowDoesNothing() {
        val empty = row(itemCount = 0)
        assertFalse(empty.moveBy(4))
        assertEquals(0, empty.selectedIndex)

        val single = row(itemCount = 1)
        assertFalse(single.moveBy(4))
        assertFalse(single.moveBy(-4))
        assertEquals(0, single.selectedIndex)
    }

    // ── moveBy: wraparound ──

    @Test
    fun moveByWrapsOnlyFromTheEdgeItIsPushedAgainst() {
        val s = row(itemCount = 10, initialIndex = 7)

        assertTrue(s.moveBy(5, wrapAround = true))
        assertEquals(9, s.selectedIndex, "a flick from the middle stops at the end")

        assertTrue(s.moveBy(5, wrapAround = true))
        assertEquals(0, s.selectedIndex, "and only the next one wraps, exactly like single-step")

        assertTrue(s.moveBy(-2, wrapAround = true))
        assertEquals(9, s.selectedIndex)
    }

    @Test
    fun moveByDoesNotWrapWhenWrapAroundIsOff() {
        val s = row(itemCount = 10, initialIndex = 9)
        assertFalse(s.moveBy(4, wrapAround = false))
        assertEquals(9, s.selectedIndex)
    }

    @Test
    fun singleStepMovesAreUnchangedByTheNewPath() {
        val s = row(itemCount = 3)
        assertTrue(s.moveNext())
        assertEquals(1, s.selectedIndex)
        assertTrue(s.moveNext())
        assertFalse(s.moveNext())
        assertEquals(2, s.selectedIndex)

        assertTrue(s.movePrevious())
        assertEquals(1, s.selectedIndex)

        // moveBy(±1) must agree with them item for item
        val viaMoveBy = row(itemCount = 3)
        assertTrue(viaMoveBy.moveBy(1))
        assertEquals(1, viaMoveBy.selectedIndex)
        assertTrue(viaMoveBy.moveBy(1))
        assertFalse(viaMoveBy.moveBy(1))
        assertEquals(2, viaMoveBy.selectedIndex)
    }

    // ── the whole point: one selection change, one callback ──

    @Test
    fun aMultiStepMoveReportsTheSelectionExactlyOnce() {
        val s = row(itemCount = 50)
        val selections = mutableListOf<Int>()

        val consumed = rokuMoveBy(s, DefaultRokuFocusConfig, steps = 7, onSelected = { selections += it })

        assertTrue(consumed)
        assertEquals(
            listOf(7), selections,
            "seven items must arrive as ONE selection change, not seven — N callbacks means N " +
                "highlight animations, N prefetches and N saved-position writes downstream"
        )
    }

    @Test
    fun aClippedMoveStillReportsTheSelectionOnlyOnce() {
        val s = row(itemCount = 10, initialIndex = 8)
        val selections = mutableListOf<Int>()

        rokuMoveBy(s, DefaultRokuFocusConfig, steps = 5, onSelected = { selections += it })

        assertEquals(listOf(9), selections)
    }

    @Test
    fun aMoveThatChangesNothingNeverReportsASelection() {
        val s = row(itemCount = 10, initialIndex = 9)
        val selections = mutableListOf<Int>()
        var boundaryHits = 0

        rokuMoveBy(
            s, DefaultRokuFocusConfig, steps = 5,
            onSelected = { selections += it },
            onBoundaryHit = { boundaryHits++ }
        )

        assertTrue(selections.isEmpty())
        assertEquals(1, boundaryHits, "and the boundary is reported once, not once per step")
    }

    // ── edge semantics ──

    @Test
    fun aMultiStepMoveIntoAClosedEdgeDoesNotEscape() {
        val closed = DefaultRokuFocusConfig.copy(focusEscape = RokuFocusEscape.None)
        val s = row(itemCount = 10, initialIndex = 9)

        assertTrue(
            rokuMoveBy(s, closed, steps = 5),
            "a closed edge clamps and keeps the gesture"
        )
    }

    @Test
    fun aMultiStepMoveIntoAnOpenEdgeEscapesExactlyOnce() {
        val open = DefaultRokuFocusConfig.copy(focusEscape = RokuFocusEscape.All)
        val s = row(itemCount = 10, initialIndex = 9)

        assertFalse(rokuMoveBy(s, open, steps = 5), "an open edge lets focus travel onward")
    }

    @Test
    fun aPartlyConsumableMoveTakesWhatItCanThenAppliesTheEdgePolicy() {
        val open = DefaultRokuFocusConfig.copy(focusEscape = RokuFocusEscape.All)
        val s = row(itemCount = 10, initialIndex = 7)
        val selections = mutableListOf<Int>()

        // two steps available, three requested
        val consumed = rokuMoveBy(s, open, steps = 3, onSelected = { selections += it })

        assertEquals(9, s.selectedIndex, "it consumes what it can")
        assertEquals(listOf(9), selections, "still exactly one selection change")
        assertFalse(consumed, "and the edge policy is applied once for the clipped remainder")
    }

    @Test
    fun theOppositeEdgeIsJudgedByItsOwnPolicy() {
        // start open, end closed
        val config = DefaultRokuFocusConfig.copy(
            focusEscape = RokuFocusEscape(start = true, end = false, up = false, down = false)
        )
        val atEnd = row(itemCount = 10, initialIndex = 9)
        assertTrue(rokuMoveBy(atEnd, config, steps = 4), "end is closed")

        val atStart = row(itemCount = 10, initialIndex = 0)
        assertFalse(rokuMoveBy(atStart, config, steps = -4), "start is open")
    }

    @Test
    fun aFullyConsumedMoveNeverEscapesEvenWithEveryEdgeOpen() {
        val open = DefaultRokuFocusConfig.copy(focusEscape = RokuFocusEscape.All)
        val s = row(itemCount = 50)
        assertTrue(rokuMoveBy(s, open, steps = 9))
        assertEquals(9, s.selectedIndex)
    }

    // ── vertical ──

    @Test
    fun moveRowsByCoversSeveralRowsInOneMove() {
        val c = column(rowCount = 10)
        assertTrue(c.moveRowsBy(4))
        assertEquals(4, c.selectedRowIndex)

        assertTrue(c.moveRowsBy(-2))
        assertEquals(2, c.selectedRowIndex)
    }

    @Test
    fun moveRowsBySkipsRowsWithNothingToSelect() {
        // rows 1, 2 and 4 are empty rails
        val c = column(rowCount = 8) { it !in setOf(1, 2, 4) }
        assertTrue(c.moveRowsBy(2))
        assertEquals(
            5, c.selectedRowIndex,
            "two steps means two SELECTABLE rows (0 -> 3 -> 5), not two indices"
        )
    }

    @Test
    fun moveRowsByClampsAndReportsNoMovementAtTheEnd() {
        val c = column(rowCount = 5)
        assertTrue(c.moveRowsBy(99))
        assertEquals(4, c.selectedRowIndex)
        assertFalse(c.moveRowsBy(3))
        assertEquals(4, c.selectedRowIndex)
    }

    @Test
    fun moveRowsByDoesNothingOnAnEmptyOrUnselectableColumn() {
        assertFalse(column(rowCount = 0).moveRowsBy(3))
        assertFalse(column(rowCount = 4) { false }.moveRowsBy(3))
        assertFalse(column(rowCount = 5).moveRowsBy(0))
    }

    @Test
    fun moveRowsByWrapsOnlyFromTheEdgeWhenAsked() {
        val c = column(rowCount = 5)
        c.moveToRow(4)
        assertTrue(c.moveRowsBy(2, wrapAround = true))
        assertEquals(0, c.selectedRowIndex)

        assertTrue(c.moveRowsBy(-1, wrapAround = true))
        assertEquals(4, c.selectedRowIndex)
    }

    @Test
    fun aMultiRowMoveReportsTheRowExactlyOnce() {
        val c = column(rowCount = 20)
        val selections = mutableListOf<Int>()

        assertTrue(rokuMoveRowsBy(c, DefaultRokuFocusConfig, steps = 6, onSelected = { selections += it }))
        assertEquals(listOf(6), selections)
    }

    @Test
    fun aVerticalMoveIntoAClosedEdgeDoesNotEscape() {
        val closed = DefaultRokuFocusConfig.copy(focusEscape = RokuFocusEscape.None)
        val c = column(rowCount = 5)
        c.moveToRow(4)
        assertTrue(rokuMoveRowsBy(c, closed, steps = 3))

        val open = DefaultRokuFocusConfig.copy(focusEscape = RokuFocusEscape.All)
        val c2 = column(rowCount = 5)
        c2.moveToRow(4)
        assertFalse(rokuMoveRowsBy(c2, open, steps = 3))
    }

    @Test
    fun theVerticalEdgePolicyIsReadFromTheVerticalEdges() {
        // down open, end (horizontal) closed — the vertical move must read `down`, not `end`
        val config = DefaultRokuFocusConfig.copy(
            focusEscape = RokuFocusEscape(start = false, end = false, up = false, down = true)
        )
        val c = column(rowCount = 4)
        c.moveToRow(3)
        assertFalse(rokuMoveRowsBy(c, config, steps = 2))
    }

    // ── key-repeat arbitration ──

    @Test
    fun aSwipeClearsTheKeyRepeatStreakSoItCannotCompound() {
        val s = row(itemCount = 50)
        val now = RokuClock.uptimeMillis()
        repeat(6) { s.keyRepeat.accept(now) }
        assertEquals(6, s.keyRepeat.consecutivePresses)

        s.moveBy(4)
        assertEquals(
            0, s.keyRepeat.consecutivePresses,
            "a swipe mid-repeat must not stack with repeat acceleration"
        )
    }

    @Test
    fun theDpadPathKeepsBuildingItsStreak() {
        // moveNext/movePrevious are the key-repeat path; resetting there would stop acceleration
        // from ever engaging.
        val s = row(itemCount = 50)
        val now = RokuClock.uptimeMillis()
        repeat(4) { s.keyRepeat.accept(now) }

        s.moveNext()
        assertEquals(4, s.keyRepeat.consecutivePresses)

        moveWithinRow(s, DefaultRokuFocusConfig, forward = true)
        assertEquals(4, s.keyRepeat.consecutivePresses)
    }

    // ── moveItemsBy / rokuMoveItemsBy: driving the active row through the column ──

    @Test
    fun moveItemsByDrivesTheActiveRowAsOneMove() {
        val c = column(rowCount = 3)
        val active = row(itemCount = 20)
        c.activeRowState = active
        assertTrue(c.moveItemsBy(4))
        assertEquals(4, active.selectedIndex)
        assertFalse(c.moveItemsBy(0))
        assertEquals(4, active.selectedIndex)
    }

    @Test
    fun moveItemsByWithoutAnActiveRowDoesNothing() {
        val c = column(rowCount = 3)
        assertFalse(c.moveItemsBy(2))
        assertFalse(rokuMoveItemsBy(c, DefaultRokuFocusConfig, 2))
    }

    @Test
    fun rokuMoveItemsByReportsRowAndItemExactlyOnce() {
        val c = column(rowCount = 3).also { it.moveToRow(2) }
        val active = row(itemCount = 20)
        c.activeRowState = active
        val selections = mutableListOf<Pair<Int, Int>>()
        val consumed = rokuMoveItemsBy(c, DefaultRokuFocusConfig, 6, onSelected = { rowIndex, itemIndex ->
            selections += rowIndex to itemIndex
        })
        assertTrue(consumed)
        assertEquals(listOf(2 to 6), selections)
    }

    @Test
    fun rokuMoveItemsByAppliesTheEdgePolicyOnceForTheActiveRow() {
        val c = column(rowCount = 1)
        val active = row(itemCount = 5, initialIndex = 4)
        c.activeRowState = active
        var boundaryHits = 0
        val closed = RokuFocusConfig(focusEscape = RokuFocusEscape.None)
        val open = RokuFocusConfig(focusEscape = RokuFocusEscape.All)

        assertTrue(rokuMoveItemsBy(c, closed, 3, onBoundaryHit = { boundaryHits++ }))
        assertFalse(rokuMoveItemsBy(c, open, 3, onBoundaryHit = { boundaryHits++ }))
        assertEquals(2, boundaryHits, "one boundary report per move, not per step")
        assertEquals(4, active.selectedIndex)
    }

    @Test
    fun moveItemsByResetsTheColumnKeyRepeatStreak() {
        val c = column(rowCount = 3)
        c.activeRowState = row(itemCount = 20)
        val now = RokuClock.uptimeMillis()
        repeat(5) { c.keyRepeat.accept(now) }
        assertEquals(5, c.keyRepeat.consecutivePresses)
        assertTrue(c.moveItemsBy(1))
        assertEquals(0, c.keyRepeat.consecutivePresses)
    }
}
