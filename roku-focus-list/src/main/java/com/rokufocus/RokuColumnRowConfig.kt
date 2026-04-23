package com.rokufocus

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for a single row inside [RokuLazyColumn].
 *
 * @param headerHeight The explicit height of the row header rendered by [RokuLazyColumn.rowHeader].
 *   Must match the actual rendered header height for correct highlight Y positioning.
 *   Set to 0.dp if no header is used.
 */
@Stable
data class RokuColumnRowConfig(
    val state: RokuFocusListState,
    val itemWidth: Dp,
    val itemHeight: Dp,
    val itemSpacing: Dp = 14.dp,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val headerHeight: Dp = 0.dp
)
