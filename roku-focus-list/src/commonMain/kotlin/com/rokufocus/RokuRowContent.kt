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
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
    rowIndex: Int = 0,
    itemKey: ((index: Int) -> Any)? = null,
    itemContentDescription: ((index: Int) -> String?)? = null,
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
        items(
            count = state.itemCount,
            key = itemKey ?: { it }
        ) { index ->
            val isSelected = index == state.selectedIndex
            Box(
                modifier = Modifier
                    .width(itemWidth)
                    // The selected card is the one consumers scale up or decorate
                    // beyond its bounds; without lifting it, LazyRow's placement
                    // order draws the NEXT sibling over its trailing edge.
                    .zIndex(if (isSelected) 1f else 0f)
                    // Unmerged on purpose: merging here was measured on an API 31 TV emulator to
                    // drop this node's own contentDescription without actually absorbing the
                    // card's children, leaving a worse tree than not merging at all.
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = rowIndex,
                            rowSpan = 1,
                            columnIndex = index,
                            columnSpan = 1
                        )
                        selected = isSelected
                        itemContentDescription?.invoke(index)?.let { contentDescription = it }
                    }
            ) {
                itemContent(index, isSelected)
            }
        }
    }
}
