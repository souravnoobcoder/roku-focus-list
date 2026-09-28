package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pixel maths behind `verticalAnchorOffset`: the scroll target, floating containment against a
 * window that starts below the offset, the lazy-list position a snap or a restore lands on, and
 * the guarantee that an offset of zero changes nothing.
 */
class RokuVerticalAnchorMathTest {

    // Eight 100px rows, 10px apart: tops at 0, 110, 220, … 770. A 400px viewport under 20px of top
    // padding leaves a 380px window.
    private val heights = FloatArray(8) { 100f }
    private val cum = FloatArray(heights.size) { it * 110f }

    private fun contain(anchor: Int, selected: Int, offset: Float) = containVerticalWindow(
        anchorRow = anchor,
        selectedRow = selected,
        rowCumOffsetPx = cum,
        rowHeightsPx = heights,
        viewportHeightPx = 400f,
        topPaddingPx = 20f,
        anchorOffsetPx = offset
    )

    // ── Scroll target ──

    @Test
    fun theTargetRowParksTheOffsetBelowTheTop() {
        assertEquals(260f, anchoredScrollPx(rowTopPx = 330f, anchorOffsetPx = 70f))
    }

    @Test
    fun rowsTheOffsetCannotLiftClearOfTheTopStayWhereTheyStart() {
        assertEquals(0f, anchoredScrollPx(rowTopPx = 0f, anchorOffsetPx = 70f), "the first row")
        assertEquals(0f, anchoredScrollPx(rowTopPx = 40f, anchorOffsetPx = 70f), "a row inside the offset")
    }

    @Test
    fun aZeroOffsetScrollsToTheRowItself() {
        for (top in cum) assertEquals(top, anchoredScrollPx(top, 0f))
    }

    // ── Floating containment ──

    @Test
    fun aZeroOffsetContainsExactlyAsBefore() {
        for (anchor in cum.indices) for (selected in cum.indices) {
            assertEquals(
                containVerticalWindow(anchor, selected, cum, heights, 400f, 20f),
                contain(anchor, selected, offset = 0f),
                "anchor $anchor, selected $selected"
            )
        }
    }

    @Test
    fun theWindowAdvancesFurtherBecauseItStartsLower() {
        // Row 4's bottom is at 540. Without an offset, window row 2 (scroll 220) shows down to 600.
        assertEquals(2, contain(anchor = 0, selected = 4, offset = 0f))
        // Parked 70px down, row 2 scrolls only to 150 and shows down to 530: row 4 would be cut
        // off, so the window has to start at row 3 (scroll 260, showing down to 640).
        assertEquals(3, contain(anchor = 0, selected = 4, offset = 70f))
    }

    @Test
    fun theWindowHoldsWhileTheSelectedRowStillFitsBelowTheAnchor() {
        // Window row 3 parked at 70px: scroll 260, visible to 640. Rows 3–4 fit, row 5 (bottom
        // 650) does not.
        assertEquals(3, contain(anchor = 3, selected = 4, offset = 70f))
        assertEquals(4, contain(anchor = 3, selected = 5, offset = 70f))
    }

    @Test
    fun aRowPeekingIntoTheOffsetBandIsAboveTheWindow() {
        // With window row 3 parked 70px down (scroll 260), the bottom 60px of row 2 shows in the
        // band. It is still above the window, so selecting it makes it the window row.
        assertEquals(2, contain(anchor = 3, selected = 2, offset = 70f))
    }

    @Test
    fun theTopOfTheColumnNeverScrollsForAnOffset() {
        // Rows 0–2 fit the unscrolled 380px window whatever the offset.
        for (selected in 0..2) assertEquals(0, contain(anchor = 0, selected = selected, offset = 70f))
    }

    @Test
    fun aRowTallerThanTheWindowParksOnTheAnchor() {
        val tall = floatArrayOf(100f, 500f, 100f)
        val tallCum = floatArrayOf(0f, 110f, 620f)
        assertEquals(1, containVerticalWindow(0, 1, tallCum, tall, 400f, 20f, anchorOffsetPx = 70f))
    }

    // ── Lazy-list position ──

    @Test
    fun theAnchoredPositionFallsInsideTheRowAbove() {
        val position = anchoredScrollPosition(row = 3, anchorOffsetPx = 70f) { 110f }
        assertEquals(2, position.index)
        assertEquals(40, position.offset, "row 2 is 110px tall with its gap; 70px of it stays above")
    }

    @Test
    fun anOffsetOfExactlyOnePitchStartsAtTheRowAbove() {
        val position = anchoredScrollPosition(row = 3, anchorOffsetPx = 110f) { 110f }
        assertEquals(2, position.index)
        assertEquals(0, position.offset)
    }

    @Test
    fun anOffsetWiderThanTheRowAboveWalksBackFurther() {
        val position = anchoredScrollPosition(row = 3, anchorOffsetPx = 150f) { 110f }
        assertEquals(1, position.index)
        assertEquals(70, position.offset)
    }

    @Test
    fun aRowNearTheTopStartsTheListAtItsTop() {
        val position = anchoredScrollPosition(row = 1, anchorOffsetPx = 500f) { 110f }
        assertEquals(0, position.index)
        assertEquals(0, position.offset)
    }

    @Test
    fun aZeroOffsetStartsAtTheRowItself() {
        val position = anchoredScrollPosition(row = 5, anchorOffsetPx = 0f) { error("not needed") }
        assertEquals(5, position.index)
        assertEquals(0, position.offset)
    }

    @Test
    fun aTallHeroAboveTakesTheOffsetOutOfItsOwnBottom() {
        // A 300px hero, then rails: parking row 1 40px down starts the list 270px into the hero.
        val pitches = floatArrayOf(310f, 110f, 110f)
        val position = anchoredScrollPosition(row = 1, anchorOffsetPx = 40f) { pitches[it] }
        assertEquals(0, position.index)
        assertEquals(270, position.offset)
    }

    @Test
    fun anAbsoluteOffsetResolvesToTheRowItFallsIn() {
        assertEquals(2, rowAtOrBefore(fromRow = 3, absolutePx = 260f, rowCumOffsetPx = cum))
        assertEquals(40, offsetInto(2, 260f, cum))
        assertEquals(3, rowAtOrBefore(fromRow = 3, absolutePx = 330f, rowCumOffsetPx = cum))
        assertEquals(0, offsetInto(3, 330f, cum))
        assertEquals(0, rowAtOrBefore(fromRow = 3, absolutePx = 0f, rowCumOffsetPx = cum))
    }

    @Test
    fun aMeasuredOffsetAHairAboveTheTargetStaysOnTheTarget() {
        assertEquals(3, rowAtOrBefore(fromRow = 3, absolutePx = 329.7f, rowCumOffsetPx = cum))
        assertEquals(0, offsetInto(3, 329.7f, cum))
    }
}
