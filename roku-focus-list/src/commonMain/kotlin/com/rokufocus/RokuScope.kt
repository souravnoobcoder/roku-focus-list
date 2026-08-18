package com.rokufocus

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
// DSL scopes for RokuLazyRow and RokuLazyColumn
// ═══════════════════════════════════════════════════════════════════════════════

@DslMarker
annotation class RokuDsl

/**
 * Scope for declaring items inside [RokuLazyRow] or inside a [RokuLazyColumnScope.row].
 *
 * Usage:
 * ```
 * // Items size themselves — width auto-measured from first item
 * items(movies) { movie, isFocused ->
 *     MovieCard(movie = movie, isFocused = isFocused)
 * }
 * ```
 */
@RokuDsl
class RokuItemScope internal constructor() {
    internal var itemCount: Int = 0
        private set
    internal var itemKey: ((Int) -> Any)? = null
        private set
    internal var itemContent: (@Composable (index: Int, isFocused: Boolean) -> Unit)? = null
        private set

    /**
     * Add [count] items. Each item receives its `index` and `isFocused` state.
     *
     * @param count Number of items.
     * @param key Optional stable key for efficient recomposition (like LazyRow's `key`).
     * @param itemContent Composable for each item.
     */
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
    ) {
        this.itemCount = count
        this.itemKey = key
        this.itemContent = itemContent
    }

    /**
     * Add items from a [List]. Each item receives the data object and `isFocused` state.
     *
     * @param items The data list.
     * @param key Optional stable key derived from each item (e.g., `{ it.id }`).
     * @param itemContent Composable for each item.
     */
    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        itemContent: @Composable (item: T, isFocused: Boolean) -> Unit
    ) {
        val list = items
        this.itemCount = list.size
        this.itemKey = if (key != null) { index -> key(list[index]) } else null
        this.itemContent = { index, isFocused -> itemContent(list[index], isFocused) }
    }
}

/**
 * Scope for declaring rows inside [RokuLazyColumn].
 *
 * Usage:
 * ```
 * RokuLazyColumn(...) {
 *     row(itemWidth = 580.dp, itemHeight = 310.dp, itemSpacing = 20.dp,
 *         headerHeight = 30.dp,
 *         header = { isFocused -> Text("Hero", ...) }
 *     ) {
 *         items(heroMovies) { movie, isFocused ->
 *             BannerCard(movie = movie, isFocused = isFocused)
 *         }
 *     }
 * }
 * ```
 */
@RokuDsl
class RokuLazyColumnScope internal constructor() {
    internal val rows = mutableListOf<RowSpec>()

    internal class RowSpec(
        val itemWidth: Dp,
        val itemHeight: Dp,
        val itemSpacing: Dp,
        val contentPadding: PaddingValues,
        val headerHeight: Dp,
        val focusSlot: Int,
        val header: (@Composable (isRowFocused: Boolean) -> Unit)?,
        val itemCount: Int,
        val itemKey: ((Int) -> Any)?,
        val itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
    )

    /**
     * Declare a horizontal row with fixed-size items.
     *
     * @param itemWidth Width of each card in this row.
     * @param itemHeight Height of each card (used for highlight sizing).
     * @param itemSpacing Horizontal gap between cards.
     * @param contentPadding Horizontal padding around the row content.
     * @param headerHeight Height of the [header] composable. Must match actual rendered height.
     * @param focusSlot Which visible slot the highlight sits at (0 = leftmost).
     * @param header Optional composable rendered above the row. Receives `isRowFocused`.
     * @param content Item declarations via [RokuItemScope.items].
     */
    fun row(
        itemWidth: Dp,
        itemHeight: Dp,
        itemSpacing: Dp = 14.dp,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        headerHeight: Dp = 0.dp,
        focusSlot: Int = 0,
        header: (@Composable (isRowFocused: Boolean) -> Unit)? = null,
        content: RokuItemScope.() -> Unit
    ) {
        val scope = RokuItemScope().apply(content)
        rows.add(
            RowSpec(
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                itemSpacing = itemSpacing,
                contentPadding = contentPadding,
                headerHeight = headerHeight,
                focusSlot = focusSlot,
                header = header,
                itemCount = scope.itemCount,
                itemKey = scope.itemKey,
                itemContent = scope.itemContent ?: { _, _ -> }
            )
        )
    }
}
