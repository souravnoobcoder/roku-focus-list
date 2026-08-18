package com.rokufocus

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for a single item row inside the state-based [RokuLazyColumn].
 *
 * @param headerHeight The explicit height of the row header rendered by [RokuLazyColumn]'s
 *   `rowHeader`. Must match the actual rendered header height for correct highlight Y positioning.
 *   Set to 0.dp if no header is used.
 * @param key Stable identity for the row, following the same contract as `LazyColumn`'s `key`.
 *   Supply one when rows can be inserted, removed or reordered, so per-row state follows the row
 *   rather than its position. Keys must be unique and savable across the whole column.
 * @param itemContentDescription Describes an item to accessibility services. The description of
 *   the selected item is surfaced on the column, which is the node screen readers see.
 */
@Stable
data class RokuColumnRowConfig(
    val state: RokuFocusListState,
    val itemWidth: Dp,
    val itemHeight: Dp,
    val itemSpacing: Dp = 14.dp,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val headerHeight: Dp = 0.dp,
    val key: Any? = null,
    val itemContentDescription: ((index: Int) -> String?)? = null
)
