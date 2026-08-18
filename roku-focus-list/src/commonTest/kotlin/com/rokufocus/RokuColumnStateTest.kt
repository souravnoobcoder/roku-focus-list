package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuColumnStateTest {

    private fun RokuColumnState.withRows(count: Int, isSelectable: (Int) -> Boolean = { true }) =
        also { it.syncRows(count, isSelectable) }

    @Test
    fun selectionIsCoercedOnceRowsAreKnown() {
        val state = RokuColumnState(initialRowIndex = 7).withRows(3)
        assertEquals(2, state.selectedRowIndex)

        state.selectedRowIndex = -4
        assertEquals(0, state.selectedRowIndex)
    }

    @Test
    fun aRequestThatIsNotValidYetIsHonouredWhenTheRowsArrive() {
        // The screen asks to restore row 5 while a single placeholder row exists.
        val state = RokuColumnState().withRows(1)
        state.selectedRowIndex = 5
        assertEquals(0, state.selectedRowIndex, "clamped while only one row exists")

        state.syncRows(10) { true }
        assertEquals(5, state.selectedRowIndex, "restored once the rows arrived")
    }

    @Test
    fun navigatingReplacesThePendingRequestSoItNeverSnapsBack() {
        val state = RokuColumnState().withRows(1)
        state.selectedRowIndex = 5

        state.syncRows(10) { true }
        state.moveToRow(2)

        state.syncRows(20) { true }
        assertEquals(2, state.selectedRowIndex)
    }

    @Test
    fun selectionResolvesToTheNearestRowWithSomethingInIt() {
        val state = RokuColumnState(initialRowIndex = 1).withRows(4) { it != 1 }
        assertEquals(2, state.selectedRowIndex)
        assertTrue(state.hasSelectableRow)
    }

    @Test
    fun anEmptyColumnNeverReportsSomethingToHighlight() {
        val state = RokuColumnState().withRows(3) { false }
        assertFalse(state.hasSelectableRow)
        assertEquals(0, state.selectedRowIndex, "still a renderable index, never -1")

        val noRows = RokuColumnState()
        assertFalse(noRows.hasSelectableRow)
        assertEquals(0, noRows.selectedRowIndex)
    }

    @Test
    fun requestSurvivesRowsDisappearingAndComingBack() {
        val state = RokuColumnState().withRows(10)
        state.moveToRow(6)
        assertEquals(6, state.selectedRowIndex)

        state.syncRows(3) { true }
        assertEquals(2, state.selectedRowIndex)

        state.syncRows(10) { true }
        assertEquals(6, state.selectedRowIndex)
    }

    @Test
    fun saverRoundTripsTheRequestedRow() {
        val state = RokuColumnState().withRows(2)
        state.selectedRowIndex = 5
        assertEquals(1, state.selectedRowIndex, "clamped for now")

        val restored = roundTrip(RokuColumnState.Saver, state)
        assertEquals(5, restored.requestedRowIndex)

        restored.syncRows(8) { true }
        assertEquals(5, restored.selectedRowIndex)
    }

    @Test
    fun rowCountTracksWhatWasSynced() {
        val state = RokuColumnState()
        assertEquals(0, state.rowCount)
        state.syncRows(4) { true }
        assertEquals(4, state.rowCount)
    }
}
