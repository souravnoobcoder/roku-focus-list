package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A floating window must never be contained against a viewport nobody has measured.
 *
 * The constructor and `updateItemCount` both run before a composable has measured anything, and
 * the placeholder viewport is one item — a size with exactly one legal anchor, the selection. So
 * containing against it collapsed the anchor onto the selected item, and the row then scrolled
 * that item to its leading edge as if it were [RokuFocusMode.Static]. Every screen that reopened
 * on a remembered card did it: the card came back focused, and the rail slid it to the start.
 */
class RokuRestoredWindowTest {

    // ── Rails ──

    @Test
    fun openingOnACardInsideTheFirstWindowDoesNotScrollTheRow() {
        val row = RokuFocusListState(
            itemCount = 40,
            initialIndex = 4,
            focusMode = RokuFocusMode.Floating
        )
        // What the composable reports once it has measured the viewport.
        row.visibleCount = 7

        assertEquals(0, row.windowStart, "card 4 is already visible; the row has nowhere to go")
        assertEquals(4, row.highlightSlot, "the highlight sits on it where it stands")
    }

    @Test
    fun aRestoredAnchorSurvivesTheItemCountArrivingBeforeTheViewport() {
        // rememberRokuFocusListState restores with itemCount 0 and then pushes the real count
        // during composition — both before the row is measured.
        val restored = roundTrip(
            RokuFocusListState.Saver,
            RokuFocusListState(itemCount = 40, initialIndex = 4, focusMode = RokuFocusMode.Floating)
                .also { it.visibleCount = 7 }
        )
        restored.updateItemCount(40)
        restored.visibleCount = 7

        assertEquals(0, restored.windowStart, "the saved window came back intact")
        assertEquals(4, restored.highlightSlot)
    }

    @Test
    fun aDeepRestoredAnchorSurvivesTheSameOrdering() {
        val deep = RokuFocusListState(
            itemCount = 40,
            initialIndex = 26,
            focusMode = RokuFocusMode.Floating
        ).also { it.visibleCount = 7 }
        assertEquals(20, deep.windowStart, "scrolled deep into the row before leaving")

        val restored = roundTrip(RokuFocusListState.Saver, deep)
        restored.updateItemCount(40)
        restored.visibleCount = 7

        assertEquals(20, restored.windowStart, "and it comes back to the same window")
        assertEquals(6, restored.highlightSlot)
    }

    @Test
    fun aSelectionBeyondTheFirstWindowIsStillContainedOnceMeasured() {
        val row = RokuFocusListState(
            itemCount = 40,
            initialIndex = 30,
            focusMode = RokuFocusMode.Floating
        )
        row.visibleCount = 7

        assertEquals(24, row.windowStart, "card 30 cannot be shown from window 0")
        assertEquals(6, row.highlightSlot, "so it arrives at the last visible slot")
    }

    @Test
    fun aViewportThatGenuinelyFitsOneItemStillContains() {
        val row = RokuFocusListState(
            itemCount = 40,
            initialIndex = 4,
            focusMode = RokuFocusMode.Floating
        )
        // Equal to the placeholder, so only the fact that it is the first report distinguishes it.
        row.visibleCount = 1

        assertEquals(4, row.windowStart, "one slot has exactly one legal window")
        assertEquals(0, row.highlightSlot)
    }

    @Test
    fun anUnmeasuredRowLeavesItsAnchorAlone() {
        val row = RokuFocusListState(
            itemCount = 40,
            initialIndex = 30,
            focusMode = RokuFocusMode.Floating
        )

        assertEquals(0, row.windowAnchor, "nothing has been measured, so nothing is decided")
    }

    @Test
    fun anExplicitViewportIsTakenAtItsWord() {
        val row = RokuFocusListState(
            itemCount = 40,
            initialIndex = 30,
            visibleCount = 7,
            focusMode = RokuFocusMode.Floating
        )

        assertEquals(24, row.windowStart, "a caller who measured for us is believed immediately")
    }

    @Test
    fun staticRowsAreUnaffected() {
        val row = RokuFocusListState(
            itemCount = 40,
            initialIndex = 4,
            focusMode = RokuFocusMode.Static
        )
        row.visibleCount = 7

        assertEquals(4, row.windowStart, "Static still parks the selection at its slot")
        assertEquals(0, row.highlightSlot)
    }

    // ── Grids ──

    @Test
    fun openingOnACellInsideTheFirstWindowDoesNotScrollTheGrid() {
        val grid = RokuGridState(itemCount = 60, columns = 4, initialIndex = 6)
        grid.visibleRows = 4

        assertEquals(0, grid.windowStartRow, "row 1 of four visible rows needs no scroll")
        assertEquals(1, grid.highlightRowSlot)
    }

    @Test
    fun aRestoredGridAnchorSurvivesTheColumnCountArrivingBeforeTheViewport() {
        val deep = RokuGridState(itemCount = 60, columns = 4, initialIndex = 40)
            .also { it.visibleRows = 4 }
        assertEquals(7, deep.windowStartRow)

        val restored = roundTrip(RokuGridState.Saver, deep)
        restored.updateItemCount(60)
        restored.updateColumns(4)
        restored.visibleRows = 4

        assertEquals(7, restored.windowStartRow, "the saved window came back intact")
        assertEquals(3, restored.highlightRowSlot)
    }

    @Test
    fun aGridRowBeyondTheFirstWindowIsStillContainedOnceMeasured() {
        val grid = RokuGridState(itemCount = 60, columns = 4, initialIndex = 40)
        grid.visibleRows = 4

        assertEquals(7, grid.windowStartRow, "row 10 cannot be shown from row 0")
        assertEquals(3, grid.highlightRowSlot)
    }
}
