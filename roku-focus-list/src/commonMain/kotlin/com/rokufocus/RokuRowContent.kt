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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
 * @param itemCount How many items the LazyRow lays out. Taken from the composition that produced
 *   [itemKey] and [itemContent], never read from [state]: the state's count is snapshot state, and
 *   the LazyRow re-derives its items the moment a new count is applied, before this composable has
 *   been handed the lambdas that go with it, so a grown row would ask the previous lambdas for an
 *   index their list does not have.
 * @param rowFocused Read per item, inside a `derivedStateOf`, and merged into the `isFocused`
 *   value handed to [itemContent]. A lambda rather than a Boolean so a row focus flip invalidates
 *   only the selected item instead of replacing this composable's parameters.
 * @param focusedItemModifier Applied to the wrapper of the item shown as focused and to no other,
 *   so a touchpad lean costs the row one layer, on one card, and nothing without a touchpad.
 */
@Composable
internal fun RokuRowContent(
    state: RokuFocusListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp,
    itemSpacing: Dp = 12.dp,
    rowIndex: Int = 0,
    itemKey: ((index: Int) -> Any)? = null,
    itemContentDescription: ((index: Int) -> String?)? = null,
    rowFocused: () -> Boolean = AlwaysFocused,
    focusedItemModifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
) {
    if (itemCount == 0) return

    // The freshest rowFocused, readable from inside the long-lived per-item deriveds below. When
    // a keyed move hands this composable a new lambda (its row shifted position in the column),
    // the deriveds observe the swap through this State — the instance they captured on first
    // composition would keep answering for the row's original position.
    val currentRowFocused = rememberUpdatedState(rowFocused)

    // Laid out at the window the state already holds, so a row that opens on a remembered card —
    // a screen returned to — is drawn in position from its first frame instead of composing at
    // item 0 and scrolling there. Read without observation: subscribing this composable to
    // `windowStart` would recompose it on every scroll, which is exactly what the snapshotFlow
    // below exists to avoid.
    val initialWindow = remember {
        Snapshot.withoutReadObservation { state.windowStart }
    }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialWindow)
    val scrollAnimator = remember(lazyListState) { RokuScrollAnimator() }

    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidth.toPx() }
    val itemSpacingPx = with(density) { itemSpacing.toPx() }

    // Scroll when the visible window shifts. Collected from a snapshotFlow rather than read in
    // composition, so a window move touches only the scroll position — this composable never
    // recomposes for it and the item subtrees stay skippable. collectLatest cancels the in-flight
    // animation on the next move; RokuScrollAnimator carries its velocity into the new one, so a
    // run of quick steps scrolls as one continuous motion rather than restarting from rest each
    // time. Everything here reads layoutInfo, never snapshot state, so no composition subscribes.
    LaunchedEffect(state, lazyListState, itemWidthPx, itemSpacingPx) {
        val stepPx = itemWidthPx + itemSpacingPx
        // The first window this pass sees is where the row already is — or, if a restored
        // `LazyListState` disagrees with a restored anchor, where it belongs. Landed on, never
        // travelled to: animating it is a rail sweeping across the screen on arrival.
        var landed = false
        snapshotFlow { state.windowStart }.collectLatest { windowStart ->
            if (!landed) {
                landed = true
                if (lazyListState.firstVisibleItemIndex != windowStart ||
                    lazyListState.firstVisibleItemScrollOffset != 0
                ) {
                    scrollAnimator.reset()
                    lazyListState.scrollToItem(windowStart)
                }
                return@collectLatest
            }
            val info = lazyListState.layoutInfo
            val viewportPx = info.viewportSize.width.toFloat()
            val currentPx = lazyListState.absoluteOffsetPx(stepPx)
            val totalContentPx = info.beforeContentPadding + info.afterContentPadding +
                state.itemCount * itemWidthPx + (state.itemCount - 1) * itemSpacingPx
            val targetPx = lazyListState.targetOffsetPx(
                index = windowStart,
                currentPx = currentPx,
                estimatedPx = windowStart * stepPx,
                maxScrollPx = totalContentPx - viewportPx
            )
            scrollAnimator.scrollToIndex(lazyListState, windowStart, currentPx, targetPx, viewportPx)
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
            count = itemCount,
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
                    .then(if (showAsFocused) focusedItemModifier else Modifier)
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
