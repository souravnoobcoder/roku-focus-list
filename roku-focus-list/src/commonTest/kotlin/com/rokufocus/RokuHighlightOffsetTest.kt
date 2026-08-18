package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals

class RokuHighlightOffsetTest {

    private val itemWidth = 100f
    private val spacing = 10f
    private val startPad = 20f
    private val endPad = 40f
    private val viewport = 500f

    private fun offsetFor(state: RokuFocusListState) = computeHighlightOffsetPx(
        state = state,
        itemWidthPx = itemWidth,
        itemSpacingPx = spacing,
        startPaddingPx = startPad,
        endPaddingPx = endPad,
        viewportWidthPx = viewport
    )

    @Test
    fun highlightSitsAtStartPaddingPlusSlotStepWhileScrollHasRoom() {
        val s = RokuFocusListState(itemCount = 20).also { it.visibleCount = 4 }

        s.scrollTo(0)
        assertEquals(20f, offsetFor(s))

        s.scrollTo(5)
        assertEquals(20f, offsetFor(s), "window shifts, highlight stays in slot 0")
    }

    @Test
    fun overflowCorrectionShiftsHighlightWhenLazyRowClampsAtTheEnd() {
        // totalContent = 20 + 20*100 + 19*10 + 40 = 2250; maxScroll = 1750
        // windowStart clamps at 16, desiredScroll = 16*110 = 1760 -> overflow = 10
        val s = RokuFocusListState(itemCount = 20).also { it.visibleCount = 4 }

        s.scrollTo(16)
        assertEquals(30f, offsetFor(s))

        s.scrollTo(19)
        assertEquals(360f, offsetFor(s))
    }

    @Test
    fun emptyListReportsStartPadding() {
        val s = RokuFocusListState(itemCount = 0).also { it.visibleCount = 4 }
        assertEquals(startPad, offsetFor(s))
    }

    @Test
    fun shortListThatFitsEntirelyNeverOverflows() {
        val s = RokuFocusListState(itemCount = 2).also { it.visibleCount = 4 }

        s.scrollTo(1)
        assertEquals(20f + spacing + itemWidth, offsetFor(s))
    }
}
