package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuRowMovementTest {

    private val plain = RokuFocusConfig()
    private val wrapping = RokuFocusConfig(wrapAround = true)

    private fun row(itemCount: Int, at: Int = 0) =
        RokuFocusListState(itemCount = itemCount, initialIndex = at).also { it.visibleCount = 4 }

    @Test
    fun movesOneStepAtATime() {
        val state = row(itemCount = 5, at = 2)

        assertTrue(moveWithinRow(state, plain, forward = true))
        assertEquals(3, state.selectedIndex)

        assertTrue(moveWithinRow(state, plain, forward = false))
        assertEquals(2, state.selectedIndex)
    }

    @Test
    fun reportsNoMovementAtTheEdgesSoTheCallerCanReleaseFocus() {
        val last = row(itemCount = 5, at = 4)
        assertFalse(moveWithinRow(last, plain, forward = true))
        assertEquals(4, last.selectedIndex)

        val first = row(itemCount = 5)
        assertFalse(moveWithinRow(first, plain, forward = false))
        assertEquals(0, first.selectedIndex)
    }

    @Test
    fun wrapAroundJumpsBetweenTheEnds() {
        val last = row(itemCount = 5, at = 4)
        assertTrue(moveWithinRow(last, wrapping, forward = true))
        assertEquals(0, last.selectedIndex)

        val first = row(itemCount = 5)
        assertTrue(moveWithinRow(first, wrapping, forward = false))
        assertEquals(4, first.selectedIndex)
    }

    @Test
    fun aSingleItemRowNeverWraps() {
        val only = row(itemCount = 1)
        assertFalse(moveWithinRow(only, wrapping, forward = true))
        assertFalse(moveWithinRow(only, wrapping, forward = false))
        assertEquals(0, only.selectedIndex)
    }

    @Test
    fun anEmptyRowNeverMoves() {
        val empty = row(itemCount = 0)
        assertFalse(moveWithinRow(empty, wrapping, forward = true))
        assertFalse(moveWithinRow(empty, plain, forward = false))
    }
}
