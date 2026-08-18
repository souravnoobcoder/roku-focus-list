package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals

class RokuRowSelectionTest {

    /** Rows 1 and 3 have nothing to select — a "Continue watching" rail that has not loaded. */
    private val gappy: (Int) -> Boolean = { it != 1 && it != 3 }

    @Test
    fun nextSelectableStepsOverEmptyRows() {
        assertEquals(2, nextSelectableRow(count = 5, from = 0, step = 1, isSelectable = gappy))
        assertEquals(4, nextSelectableRow(count = 5, from = 2, step = 1, isSelectable = gappy))
        assertEquals(2, nextSelectableRow(count = 5, from = 4, step = -1, isSelectable = gappy))
        assertEquals(0, nextSelectableRow(count = 5, from = 2, step = -1, isSelectable = gappy))
    }

    @Test
    fun nextSelectableReportsNoRowAtTheEnds() {
        assertEquals(-1, nextSelectableRow(count = 5, from = 4, step = 1, isSelectable = gappy))
        assertEquals(-1, nextSelectableRow(count = 5, from = 0, step = -1, isSelectable = gappy))
    }

    @Test
    fun nextSelectableReportsNoRowWhenEverythingIsEmpty() {
        assertEquals(-1, nextSelectableRow(count = 4, from = 0, step = 1) { false })
        assertEquals(-1, nextSelectableRow(count = 0, from = 0, step = 1) { true })
    }

    @Test
    fun nearestPrefersTheTargetItself() {
        assertEquals(2, nearestSelectableRow(count = 5, target = 2, isSelectable = gappy))
    }

    @Test
    fun nearestFallsForwardBeforeBackward() {
        // Row 1 is empty; row 2 and row 0 are equidistant, and forward wins.
        assertEquals(2, nearestSelectableRow(count = 5, target = 1, isSelectable = gappy))
    }

    @Test
    fun nearestFallsBackwardWhenNothingIsSelectableAhead() {
        assertEquals(2, nearestSelectableRow(count = 4, target = 3, isSelectable = gappy))
    }

    @Test
    fun nearestClampsAnOutOfRangeTargetBeforeSearching() {
        assertEquals(4, nearestSelectableRow(count = 5, target = 99, isSelectable = gappy))
        assertEquals(0, nearestSelectableRow(count = 5, target = -7, isSelectable = gappy))
    }

    @Test
    fun nearestReportsNoRowWhenEverythingIsEmpty() {
        assertEquals(-1, nearestSelectableRow(count = 4, target = 2) { false })
        assertEquals(-1, nearestSelectableRow(count = 0, target = 0) { true })
    }
}
