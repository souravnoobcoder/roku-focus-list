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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
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
/**
 * @param rowFocused Read per item, inside a `derivedStateOf`, and merged into the `isFocused`
 *   value handed to [itemContent]. A lambda rather than a Boolean so a row focus flip invalidates
 *   only the selected item instead of replacing this composable's parameters.
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
    rowFocused: () -> Boolean = AlwaysFocused,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
) {
    if (state.itemCount == 0) return

    // The freshest rowFocused, readable from inside the long-lived per-item deriveds below. When
    // a keyed move hands this composable a new lambda (its row shifted position in the column),
    // the deriveds observe the swap through this State — the instance they captured on first
    // composition would keep answering for the row's original position.
    val currentRowFocused = rememberUpdatedState(rowFocused)

    val lazyListState = rememberLazyListState()

    // Scroll when the visible window shifts. Collected from a snapshotFlow rather than read in
    // composition, so a window move touches only the scroll position — this composable never
    // recomposes for it and the item subtrees stay skippable. collectLatest keeps the old
    // restart-on-change semantics: a repeat press cancels the in-flight animation.
    LaunchedEffect(state, lazyListState) {
        snapshotFlow { state.windowStart }.collectLatest { windowStart ->
            lazyListState.animateScrollToItem(windowStart, 0)
        }
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
            // Derived per item: a selection change recomposes the two items whose value flipped,
            // not every visible item that happens to read selectedIndex. Keyed on index because a
            // keyed item that moves position keeps its composition — a keyless remember would go
            // on comparing against the position the item was born at.
            val isSelected by remember(index) { derivedStateOf { index == state.selectedIndex } }
            val showAsFocused by remember(index) {
                derivedStateOf { index == state.selectedIndex && currentRowFocused.value() }
            }
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
                itemContent(index, showAsFocused)
            }
        }
    }
}

/** Shared default so every parameterless call site keeps one stable lambda instance. */
private val AlwaysFocused: () -> Boolean = { true }
