package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RokuFloatingWindowTest {

    private fun floating(itemCount: Int, visible: Int, initialIndex: Int = 0) =
        RokuFocusListState(
            itemCount = itemCount,
            initialIndex = initialIndex,
            focusMode = RokuFocusMode.Floating
        ).also { it.visibleCount = visible }

    // ── Horizontal: the window holds still while the selection walks it ──

    @Test
    fun highlightWalksTheWindowWithoutScrolling() {
        val s = floating(itemCount = 20, visible = 5)

        for (i in 0..4) {
            s.scrollTo(i)
            assertEquals(0, s.windowStart, "selection $i is inside the window")
            assertEquals(i, s.highlightSlot)
        }
    }

    @Test
    fun windowAdvancesByExactlyOneWhenSelectionExitsForward() {
        val s = floating(itemCount = 20, visible = 5)
        s.scrollTo(4)
        assertEquals(0, s.windowStart)

        s.moveNext()
        assertEquals(1, s.windowStart)
        assertEquals(4, s.highlightSlot, "selection stays last visible")
    }

    @Test
    fun windowRetreatsByExactlyOneWhenSelectionExitsBackward() {
        val s = floating(itemCount = 20, visible = 5)
        s.scrollTo(10)
        assertEquals(6, s.windowStart)

        s.scrollTo(6)
        assertEquals(6, s.windowStart, "moving back inside the window does not scroll")
        assertEquals(0, s.highlightSlot)

        s.movePrevious()
        assertEquals(5, s.windowStart)
        assertEquals(0, s.highlightSlot, "selection stays first visible")
    }

    @Test
    fun anExternalJumpLandsWithTheSelectionVisible() {
        val s = floating(itemCount = 40, visible = 5)

        s.scrollTo(20)
        assertEquals(16, s.windowStart, "forward jump: selection is last visible")
        assertEquals(4, s.highlightSlot)

        s.scrollTo(3)
        assertEquals(3, s.windowStart, "backward jump: selection is first visible")
        assertEquals(0, s.highlightSlot)
    }

    @Test
    fun wrapAroundSnapsTheWindowInOneHop() {
        val s = floating(itemCount = 20, visible = 5)
        val config = DefaultRokuFocusConfig.copy(wrapAround = true)
        s.scrollTo(19)
        assertEquals(15, s.windowStart)

        assertTrue(moveWithinRow(s, config, forward = true))
        assertEquals(0, s.selectedIndex)
        assertEquals(0, s.windowStart)

        assertTrue(moveWithinRow(s, config, forward = false))
        assertEquals(19, s.selectedIndex)
        assertEquals(15, s.windowStart)
    }

    @Test
    fun viewportShrinkKeepsTheSelectionVisible() {
        val s = floating(itemCount = 20, visible = 5)
        s.scrollTo(10)
        assertEquals(6, s.windowStart)

        s.visibleCount = 3
        assertEquals(8, s.windowStart)
        assertEquals(2, s.highlightSlot)
    }

    @Test
    fun aShrunkListClampsTheWindowWithoutForgettingIt() {
        val s = floating(itemCount = 30, visible = 5)
        s.scrollTo(12)
        assertEquals(8, s.windowStart)

        s.updateItemCount(6)
        assertEquals(5, s.selectedIndex)
        assertEquals(1, s.windowStart, "clamped while the list is short")

        s.updateItemCount(30)
        assertEquals(12, s.selectedIndex, "pending request re-applied")
        assertEquals(8, s.windowStart, "raw anchor survived the shrink")
    }

    @Test
    fun aPendingRequestContainsTheWindowWhenItBecomesReachable() {
        val s = floating(itemCount = 2, visible = 5)
        s.scrollTo(12)
        assertEquals(0, s.windowStart)

        s.updateItemCount(30)
        assertEquals(12, s.selectedIndex)
        assertEquals(8, s.windowStart)
        assertEquals(4, s.highlightSlot)
    }

    @Test
    fun focusSlotHasNoEffectWhileFloating() {
        val s = RokuFocusListState(itemCount = 20, focusSlot = 2, focusMode = RokuFocusMode.Floating)
            .also { it.visibleCount = 5 }

        s.scrollTo(3)
        assertEquals(0, s.windowStart, "a static slot-2 state would have scrolled to 1")
        assertEquals(3, s.highlightSlot)
    }

    // ── Horizontal: the existing highlight-offset math follows the walking slot ──

    @Test
    fun highlightOffsetWalksInsideTheWindowAndCorrectsAtTheTail() {
        // Same geometry as RokuHighlightOffsetTest: totalContent = 2250, maxScroll = 1750.
        val s = floating(itemCount = 20, visible = 4)
        fun offset() = computeHighlightOffsetPx(s, 100f, 10f, 20f, 40f, 500f)

        s.scrollTo(2)
        assertEquals(0, s.windowStart)
        assertEquals(20f + 2 * 110f, offset(), "no scroll, the highlight itself moved")

        s.scrollTo(19)
        assertEquals(16, s.windowStart)
        assertEquals(360f, offset(), "identical to the static tail, overflow correction included")
    }

    // ── Vertical: pixel-based containment over heterogeneous row heights ──

    // heights include a zero-height empty row (index 2); cumulative offsets use 10px spacing,
    // mirroring how RokuLazyColumn builds its geometry.
    private val heights = floatArrayOf(100f, 100f, 0f, 200f, 100f, 100f)
    private val cum = FloatArray(heights.size).also {
        for (i in 1 until heights.size) it[i] = it[i - 1] + heights[i - 1] + 10f
    }

    private fun contain(anchor: Int, selected: Int) = containVerticalWindow(
        anchorRow = anchor,
        selectedRow = selected,
        rowCumOffsetPx = cum,
        rowHeightsPx = heights,
        viewportHeightPx = 400f,
        topPaddingPx = 20f
    )

    @Test
    fun verticalWindowHoldsWhileTheSelectedRowFits() {
        assertEquals(0, contain(anchor = 0, selected = 0))
        assertEquals(0, contain(anchor = 0, selected = 2))
        assertEquals(4, contain(anchor = 4, selected = 5))
    }

    @Test
    fun verticalWindowAdvancesMinimallyWhenTheSelectedRowIsCutOff() {
        // Row 3 bottom = 430 > 380 visible → first anchor whose top is within reach is row 1.
        assertEquals(1, contain(anchor = 0, selected = 3))
        // Row 5 bottom = 650; rows 2 and 3 still leave it cut off, row 4 is the minimal fit.
        assertEquals(4, contain(anchor = 1, selected = 5))
    }

    @Test
    fun verticalWindowRetreatsToTheSelectedRow() {
        assertEquals(1, contain(anchor = 4, selected = 1))
    }

    @Test
    fun aRowTallerThanTheViewportTopAlignsLikeStatic() {
        val tall = floatArrayOf(100f, 500f, 100f)
        val tallCum = floatArrayOf(0f, 110f, 620f)
        assertEquals(
            1,
            containVerticalWindow(0, 1, tallCum, tall, viewportHeightPx = 400f, topPaddingPx = 20f)
        )
    }

    @Test
    fun anOutOfRangeAnchorIsClampedNotTrusted() {
        assertEquals(4, contain(anchor = 99, selected = 4))
        assertEquals(0, containVerticalWindow(0, 0, FloatArray(0), FloatArray(0), 400f, 20f))
    }

    // ── Savers: mode and anchors survive the round trip ──

    @Test
    fun rowSaverRoundTripsModeAndAnchor() {
        val s = RokuFocusListState(itemCount = 30, initialIndex = 12, focusMode = RokuFocusMode.Floating)
        s.visibleCount = 5
        assertEquals(12, s.windowStart)

        val restored = roundTrip(RokuFocusListState.Saver, s)
        assertEquals(RokuFocusMode.Floating, restored.focusMode)
        assertEquals(12, restored.windowAnchor)
        assertEquals(12, restored.requestedIndex)

        restored.updateItemCount(30)
        restored.visibleCount = 5
        assertEquals(12, restored.selectedIndex)
        assertEquals(12, restored.windowStart)
    }

    @Test
    fun columnSaverRoundTripsTheWindowAnchorRow() {
        val state = RokuColumnState()
        state.syncRows(10) { true }
        state.moveToRow(6)
        state.windowAnchorRow = 4

        val restored = roundTrip(RokuColumnState.Saver, state)
        assertEquals(6, restored.requestedRowIndex)
        assertEquals(4, restored.windowAnchorRow)
    }
}
