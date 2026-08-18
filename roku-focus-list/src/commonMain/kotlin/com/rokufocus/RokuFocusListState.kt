package com.rokufocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.max

@Stable
class RokuFocusListState(
    itemCount: Int,
    initialIndex: Int = 0,
    visibleCount: Int = 1,
    val focusSlot: Int = 0
) {
    var selectedIndex by mutableIntStateOf(initialIndex.coerceIn(0, maxOf(0, itemCount - 1)))
        private set

    var itemCount by mutableIntStateOf(itemCount)
        private set

    /** How many items fit in the viewport. Auto-computed by [RokuLazyRow]. */
    var visibleCount by mutableIntStateOf(visibleCount)
        internal set

    val windowStart: Int
        get() {
            val ideal = selectedIndex - focusSlot
            return ideal.coerceIn(0, maxOf(0, itemCount - visibleCount))
        }

    val highlightSlot: Int
        get() = (selectedIndex - windowStart).coerceIn(0, maxOf(0, visibleCount - 1))

    val canScrollForward: Boolean
        get() = selectedIndex < itemCount - 1

    val canScrollBackward: Boolean
        get() = selectedIndex > 0

    fun moveNext(): Boolean {
        if (!canScrollForward) return false
        selectedIndex++
        return true
    }

    fun movePrevious(): Boolean {
        if (!canScrollBackward) return false
        selectedIndex--
        return true
    }

    fun scrollTo(index: Int) {
        selectedIndex = index.coerceIn(0, maxOf(0, itemCount - 1))
    }

    fun updateItemCount(newCount: Int) {
        itemCount = newCount
        if (newCount == 0) {
            selectedIndex = 0
        } else {
            selectedIndex = selectedIndex.coerceIn(0, newCount - 1)
        }
        // Ensure focusSlot is still valid for the new count
        // (windowStart/highlightSlot math depends on this)
    }
}

@Composable
fun rememberRokuFocusListState(
    itemCount: Int,
    initialIndex: Int = 0,
    focusSlot: Int = 0
): RokuFocusListState {
    val state = remember(focusSlot) {
        RokuFocusListState(
            itemCount = itemCount,
            initialIndex = initialIndex,
            focusSlot = focusSlot
        )
    }

    LaunchedEffect(itemCount) {
        state.updateItemCount(itemCount)
    }

    return state
}

/**
 * Computes the pixel X offset for the focus highlight within a row,
 * accounting for scroll clamping at the end of the list.
 *
 * When the desired scroll (to place windowStart at the leading edge) exceeds
 * the LazyRow's max scroll, items shift right. The overflow corrects the highlight
 * to track the actual item position.
 */
internal fun computeHighlightOffsetPx(
    state: RokuFocusListState,
    itemWidthPx: Float,
    itemSpacingPx: Float,
    startPaddingPx: Float,
    endPaddingPx: Float,
    viewportWidthPx: Float
): Float {
    if (state.itemCount == 0) return startPaddingPx
    val stepPx = itemWidthPx + itemSpacingPx
    val totalContentPx = startPaddingPx +
        state.itemCount * itemWidthPx +
        max(0, state.itemCount - 1) * itemSpacingPx +
        endPaddingPx
    val maxScrollPx = (totalContentPx - viewportWidthPx).coerceAtLeast(0f)
    val desiredScrollPx = state.windowStart * stepPx
    val scrollOverflowPx = (desiredScrollPx - maxScrollPx).coerceAtLeast(0f)
    return startPaddingPx + scrollOverflowPx + state.highlightSlot * stepPx
}
