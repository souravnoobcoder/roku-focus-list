package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuFocusListStateTest {

    private fun state(itemCount: Int, visible: Int, focusSlot: Int = 0) =
        RokuFocusListState(itemCount = itemCount, focusSlot = focusSlot)
            .also { it.visibleCount = visible }

    @Test
    fun windowFollowsSelectionUntilTheListRunsOut() {
        val s = state(itemCount = 20, visible = 5)

        s.scrollTo(3)
        assertEquals(3, s.windowStart)
        assertEquals(0, s.highlightSlot)

        s.scrollTo(15)
        assertEquals(15, s.windowStart)
        assertEquals(0, s.highlightSlot)
    }

    @Test
    fun highlightWalksForwardOnceScrollIsClamped() {
        val s = state(itemCount = 20, visible = 5)

        s.scrollTo(16)
        assertEquals(15, s.windowStart)
        assertEquals(1, s.highlightSlot)

        s.scrollTo(19)
        assertEquals(15, s.windowStart)
        assertEquals(4, s.highlightSlot)
    }

    @Test
    fun focusSlotOffsetsTheWindowWithoutLeavingBlankSpace() {
        val s = state(itemCount = 20, visible = 5, focusSlot = 2)

        s.scrollTo(0)
        assertEquals(0, s.windowStart)
        assertEquals(0, s.highlightSlot)

        s.scrollTo(2)
        assertEquals(0, s.windowStart)
        assertEquals(2, s.highlightSlot)

        s.scrollTo(10)
        assertEquals(8, s.windowStart)
        assertEquals(2, s.highlightSlot)

        s.scrollTo(19)
        assertEquals(15, s.windowStart)
        assertEquals(4, s.highlightSlot)
    }

    @Test
    fun moveStopsAtBothEnds() {
        val s = state(itemCount = 3, visible = 3)

        assertFalse(s.canScrollBackward)
        assertFalse(s.movePrevious())
        assertEquals(0, s.selectedIndex)

        assertTrue(s.moveNext())
        assertTrue(s.moveNext())
        assertEquals(2, s.selectedIndex)

        assertFalse(s.canScrollForward)
        assertFalse(s.moveNext())
        assertEquals(2, s.selectedIndex)
    }

    @Test
    fun scrollToCoercesOutOfRangeIndices() {
        val s = state(itemCount = 10, visible = 4)

        s.scrollTo(99)
        assertEquals(9, s.selectedIndex)

        s.scrollTo(-5)
        assertEquals(0, s.selectedIndex)
    }

    @Test
    fun updateItemCountKeepsSelectionInRange() {
        val s = state(itemCount = 10, visible = 4)
        s.scrollTo(9)

        s.updateItemCount(4)
        assertEquals(3, s.selectedIndex)

        s.updateItemCount(0)
        assertEquals(0, s.selectedIndex)
        assertEquals(0, s.windowStart)
        assertEquals(0, s.highlightSlot)
    }

    @Test
    fun initialIndexIsClampedToItemCount() {
        val s = RokuFocusListState(itemCount = 5, initialIndex = 42)
        assertEquals(4, s.selectedIndex)
    }
}
