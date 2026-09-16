package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RokuRowEntryTest {

    // A rail of 220 px cards, 14 px gaps, 48 px start padding, six visible.
    private val windowLeft = 48f
    private val card = 220f
    private val step = 234f
    private val visible = 6

    private fun slotUnder(centreX: Float) = slotUnder(centreX, windowLeft, card, step, visible)

    private fun centreOfSlot(slot: Int) = windowLeft + slot * step + card / 2f

    @Test
    fun aPointInsideACardPicksThatCard() {
        assertEquals(2, slotUnder(centreOfSlot(2)))
        assertEquals(2, slotUnder(windowLeft + 2 * step + 1f), "just inside the left edge")
        assertEquals(2, slotUnder(windowLeft + 2 * step + card - 1f), "just inside the right edge")
        assertEquals(0, slotUnder(centreOfSlot(0)))
    }

    @Test
    fun aPointInAGapGoesToTheNearerCard() {
        val gapStart = windowLeft + step + card
        assertEquals(1, slotUnder(gapStart + 3f), "close to card 1's trailing edge")
        assertEquals(2, slotUnder(gapStart + 11f), "close to card 2's leading edge")
    }

    @Test
    fun beyondTheWindowClampsToItsEnds() {
        assertEquals(visible - 1, slotUnder(5000f))
        assertEquals(0, slotUnder(10f), "left of the first card")
    }

    @Test
    fun degenerateGeometryAnswersTheFirstSlot() {
        assertEquals(0, slotUnder(600f, windowLeft, card, 0f, visible))
        assertEquals(0, slotUnder(600f, windowLeft, card, step, 0))
    }

    @Test
    fun landingInsideTheWindowNeverMovesTheWindow() {
        val row = RokuFocusListState(itemCount = 30, focusMode = RokuFocusMode.Floating)
        row.visibleCount = visible
        row.scrollTo(9)
        val windowBefore = row.windowStart
        assertEquals(4, windowBefore, "containment parked the window at 4..9")

        // Entering at every visible slot selects an on-screen card and leaves the window alone.
        for (slot in 0 until visible) {
            row.scrollTo((row.windowStart + slot).coerceIn(0, row.itemCount - 1))
            assertEquals(windowBefore, row.windowStart, "slot $slot must not scroll the row")
            assertEquals(windowBefore + slot, row.selectedIndex)
        }
    }

    @Test
    fun aShortRowLandsOnItsLastCardWithoutScrolling() {
        val row = RokuFocusListState(itemCount = 3, focusMode = RokuFocusMode.Floating)
        row.visibleCount = visible
        val slot = slotUnder(centreOfSlot(5))
        row.scrollTo((row.windowStart + slot).coerceIn(0, row.itemCount - 1))
        assertEquals(2, row.selectedIndex)
        assertEquals(0, row.windowStart)
    }

    private class RecordingEntry {
        val calls = mutableListOf<Pair<Int, Int>>()
        var rowWhenAsked = -1
        fun install(state: RokuColumnState) {
            state.rowEntry = { from, to ->
                calls += from to to
                rowWhenAsked = state.selectedRowIndex
            }
        }
    }

    private fun column(rows: Int = 4): RokuColumnState =
        RokuColumnState().also { it.syncRows(rows) { true } }

    @Test
    fun aDpadStepIntoARowResolvesTheLandingBeforeTheRowChanges() {
        val state = column()
        val entry = RecordingEntry().also { it.install(state) }
        state.stepToRow(2)

        assertEquals(listOf(0 to 2), entry.calls)
        assertEquals(0, entry.rowWhenAsked, "the row being left is still current when asked, so its highlight X is readable")
        assertEquals(2, state.selectedRowIndex)
    }

    @Test
    fun aMultiRowMoveResolvesOnceFromTheRowLeftToTheRowReached() {
        val state = column()
        val entry = RecordingEntry().also { it.install(state) }
        assertTrue(state.moveRowsBy(2))

        assertEquals(listOf(0 to 2), entry.calls, "intermediate rows are not visited")
    }

    @Test
    fun aWrapAroundEntryIsResolvedToo() {
        val state = column()
        val entry = RecordingEntry().also { it.install(state) }
        state.moveToRow(3)
        assertTrue(state.moveRowsBy(1, wrapAround = true))

        assertEquals(listOf(3 to 0), entry.calls)
    }

    @Test
    fun aProgrammaticMoveToRowLeavesTheEnteredRowAlone() {
        val state = column()
        val entry = RecordingEntry().also { it.install(state) }
        state.moveToRow(2)
        state.stepToRow(2)

        assertTrue(entry.calls.isEmpty(), "neither a plain jump nor a step onto the same row asks")
    }

    @Test
    fun withoutAResolverStepsStillMove() {
        val state = column()
        state.stepToRow(1)
        assertEquals(1, state.selectedRowIndex)
    }

    @Test
    fun spatialEntryIsTheDefaultAndPositionalCallsKeepTheirMeaning() {
        assertEquals(RokuRowEntry.Spatial, RokuFocusConfig().rowEntry)
        val positional = RokuFocusConfig(
            DefaultRokuFocusConfig.highlightAnimationSpec, 150L, 3, 50L, true, false, RokuFocusEscape.None
        )
        assertTrue(positional.wrapAround)
        assertEquals(RokuFocusEscape.None, positional.focusEscape)
        assertEquals(RokuRowEntry.Spatial, positional.rowEntry)
        assertEquals(null, positional.verticalAnimationSpec)
        assertEquals(null, positional.verticalKeyRepeatDelayMs)
    }
}
