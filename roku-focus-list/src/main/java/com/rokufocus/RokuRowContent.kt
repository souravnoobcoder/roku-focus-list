package com.rokufocus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Internal pure content renderer: a LazyRow with programmatic scrolling.
 * No highlight, no focus handling, no visibleCount computation.
 * The caller ([RokuLazyRow] or [RokuLazyColumn]) handles all of that.
 */
@Composable
internal fun RokuRowContent(
    state: RokuFocusListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp,
    itemSpacing: Dp = 12.dp,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
) {
    if (state.itemCount == 0) return

    val lazyListState = rememberLazyListState()

    // Scroll when the visible window shifts
    val currentWindowStart = state.windowStart
    LaunchedEffect(currentWindowStart) {
        lazyListState.animateScrollToItem(currentWindowStart, 0)
    }

    LazyRow(
        state = lazyListState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        userScrollEnabled = false
    ) {
        items(state.itemCount) { index ->
            Box(modifier = Modifier.width(itemWidth)) {
                itemContent(index, index == state.selectedIndex)
            }
        }
    }
}
