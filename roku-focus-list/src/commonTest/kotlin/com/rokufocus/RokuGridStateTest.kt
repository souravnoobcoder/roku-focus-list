package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuGridStateTest {

    private fun grid(
        itemCount: Int,
        columns: Int = 4,
        visibleRows: Int = 3,
        initialIndex: Int = 0,
        focusMode: RokuFocusMode = RokuFocusMode.Floating
    ) = RokuGridState(itemCount, columns, initialIndex, focusMode = focusMode)
        .also { it.visibleRows = visibleRows }

    // ── shape ──

    @Test
    fun rowsAndCellCoordinatesAreDerivedFromTheLinearIndex() {
        val g = grid(itemCount = 10, columns = 4, initialIndex = 6)
        assertEquals(3, g.rowCount, "10 cells in rows of 4 is three rows, the last one partial")
        assertEquals(1, g.selectedRow)
        assertEquals(2, g.selectedColumn)
    }

    @Test
    fun anEmptyGridHasNoRowsAndSelectsNothing() {
        val g = grid(itemCount = 0)
        assertEquals(0, g.rowCount)
        assertEquals(0, g.selectedIndex)
        assertFalse(g.moveColumnsBy(1))
        assertFalse(g.moveRowsBy(1))
    }

    @Test
    fun floatingIsTheDefaultMode() {
        assertEquals(RokuFocusMode.Floating, RokuGridState(itemCount = 8, columns = 4).focusMode)
    }

    // ── horizontal ──

    @Test
    fun columnsMoveAlongTheRowAndStopAtItsEnds() {
        val g = grid(itemCount = 12, columns = 4, initialIndex = 5)
        assertTrue(g.moveColumnsBy(2))
        assertEquals(7, g.selectedIndex, "last cell of row 1")
        assertFalse(g.moveColumnsBy(1), "the row end is a wall without wrapAround")
        assertEquals(7, g.selectedIndex)

        assertTrue(g.moveColumnsBy(-9))
        assertEquals(4, g.selectedIndex, "clamped at the row's first cell, never into the row above")
    }

    @Test
    fun aShorterLastRowEndsWhereItsCellsEnd() {
        val g = grid(itemCount = 10, columns = 4, initialIndex = 8)
        assertTrue(g.moveColumnsBy(5))
        assertEquals(9, g.selectedIndex)
    }

    @Test
    fun wrapAroundFlowsAcrossRowsInReadingOrder() {
        val g = grid(itemCount = 12, columns = 4, initialIndex = 7)
        assertTrue(g.moveColumnsBy(1, wrapAround = true))
        assertEquals(8, g.selectedIndex, "the end of row 1 flows into the start of row 2")

        g.scrollTo(11)
        assertTrue(g.moveColumnsBy(1, wrapAround = true))
        assertEquals(0, g.selectedIndex, "the grid's last cell wraps to its first")

        assertTrue(g.moveColumnsBy(-1, wrapAround = true))
        assertEquals(11, g.selectedIndex)
    }

    @Test
    fun moveByIsReadingOrderRegardlessOfRows() {
        val g = grid(itemCount = 12, columns = 4, initialIndex = 2)
        assertTrue(g.moveBy(5))
        assertEquals(7, g.selectedIndex)
        assertTrue(g.moveBy(99))
        assertEquals(11, g.selectedIndex, "clamped at the last cell")
        assertEquals(11, g.requestedIndex, "no pending request past the end")
    }

    // ── vertical ──

    @Test
    fun rowsMoveKeepingTheColumn() {
        val g = grid(itemCount = 12, columns = 4, initialIndex = 1)
        assertTrue(g.moveRowsBy(2))
        assertEquals(9, g.selectedIndex)
        assertEquals(1, g.selectedColumn)
        assertFalse(g.moveRowsBy(1), "the last row is a wall without wrapAround")
    }

    @Test
    fun aShorterLastRowHandsOutItsLastCell() {
        val g = grid(itemCount = 10, columns = 4, initialIndex = 3)
        assertTrue(g.moveRowsBy(2))
        assertEquals(9, g.selectedIndex, "column 3 does not exist in the last row; its last cell does")
    }

    @Test
    fun rowsWrapOnlyFromTheEdgeBeingPushed() {
        val g = grid(itemCount = 12, columns = 4, initialIndex = 5)
        assertTrue(g.moveRowsBy(5, wrapAround = true))
        assertEquals(9, g.selectedIndex, "a jump from the middle stops at the last row")
        assertTrue(g.moveRowsBy(1, wrapAround = true))
        assertEquals(1, g.selectedIndex, "and the next one wraps to the first row, same column")
    }

    // ── floating window ──

    @Test
    fun theWindowHoldsWhileTheSelectedRowIsVisible() {
        val g = grid(itemCount = 40, columns = 4, visibleRows = 3)
        g.moveRowsBy(2)
        assertEquals(0, g.windowStartRow)
        assertEquals(2, g.highlightRowSlot)
    }

    @Test
    fun aMultiRowMoveContainsTheWindowInOneHop() {
        val g = grid(itemCount = 40, columns = 4, visibleRows = 3)
        assertTrue(g.moveRowsBy(6))
        assertEquals(6, g.selectedRow)
        assertEquals(4, g.windowStartRow, "the selected row is the last visible one")
        assertEquals(2, g.highlightRowSlot)

        assertTrue(g.moveRowsBy(-5))
        assertEquals(1, g.selectedRow)
        assertEquals(1, g.windowStartRow, "backward, the selected row becomes the first visible one")
        assertEquals(0, g.highlightRowSlot)
    }

    @Test
    fun staticParksTheSelectedRowAtTheTopUntilTheEnd() {
        val g = grid(itemCount = 40, columns = 4, visibleRows = 3, focusMode = RokuFocusMode.Static)
        g.moveRowsBy(4)
        assertEquals(4, g.windowStartRow)
        assertEquals(0, g.highlightRowSlot)

        g.moveRowsBy(5)
        assertEquals(9, g.selectedRow)
        assertEquals(7, g.windowStartRow, "clamped: only 10 rows exist")
        assertEquals(2, g.highlightRowSlot, "so the highlight walks down through the last rows")
    }

    @Test
    fun aShrunkGridClampsTheWindowWithoutForgettingIt() {
        val g = grid(itemCount = 40, columns = 4, visibleRows = 3)
        g.moveRowsBy(7)
        assertEquals(5, g.windowStartRow)

        g.updateItemCount(8)
        assertEquals(0, g.windowStartRow, "two rows fit entirely")
        assertEquals(7, g.selectedIndex, "selection coerced into what exists")

        g.updateItemCount(40)
        assertEquals(7, g.selectedRow, "the request was remembered")
        assertEquals(5, g.windowStartRow, "and so was the window")
    }

    @Test
    fun changingColumnsKeepsTheCellNotTheColumn() {
        val g = grid(itemCount = 40, columns = 4, initialIndex = 9)
        g.updateColumns(5)
        assertEquals(9, g.selectedIndex)
        assertEquals(1, g.selectedRow)
        assertEquals(4, g.selectedColumn)
    }

    // ── key repeat & saver ──

    @Test
    fun theMultiStepMovesResetTheKeyRepeatStreak() {
        val g = grid(itemCount = 40, columns = 4)
        val now = RokuClock.uptimeMillis()
        repeat(4) { g.keyRepeat.accept(now) }
        g.moveColumnsBy(1)
        assertEquals(0, g.keyRepeat.consecutivePresses)

        repeat(4) { g.keyRepeat.accept(now) }
        g.moveRowsBy(1)
        assertEquals(0, g.keyRepeat.consecutivePresses)

        repeat(4) { g.keyRepeat.accept(now) }
        g.moveNext()
        assertEquals(4, g.keyRepeat.consecutivePresses, "the D-pad step is the repeat path and keeps its streak")
    }

    @Test
    fun saverRoundTripsRequestColumnsModeAndAnchor() {
        val g = grid(itemCount = 40, columns = 4, visibleRows = 3, focusMode = RokuFocusMode.Static)
        g.scrollTo(57)
        g.windowAnchorRow = 3

        val restored = roundTrip(RokuGridState.Saver, g)

        assertEquals(57, restored.requestedIndex)
        assertEquals(4, restored.columns)
        assertEquals(RokuFocusMode.Static, restored.focusMode)
        assertEquals(3, restored.windowAnchorRow)
        assertEquals(0, restored.itemCount, "the count describes the data and is not saved")
    }
}
