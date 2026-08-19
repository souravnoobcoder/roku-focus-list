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
    internal var itemContentDescription: ((Int) -> String?)? = null
        private set
    internal var itemContent: (@Composable (index: Int, isFocused: Boolean) -> Unit)? = null
        private set

    /**
     * Add [count] items. Each item receives its `index` and `isFocused` state.
     *
     * @param count Number of items.
     * @param key Optional stable key for efficient recomposition (like LazyRow's `key`).
     * @param contentDescription Optional description of each item for accessibility services.
     *   The selected item's description is surfaced on the list, which is the node screen readers
     *   see.
     * @param itemContent Composable for each item.
     */
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentDescription: ((index: Int) -> String?)? = null,
        itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
    ) {
        this.itemCount = count
        this.itemKey = key
        this.itemContentDescription = contentDescription
        this.itemContent = itemContent
    }

    /**
     * Add items from a [List]. Each item receives the data object and `isFocused` state.
     *
     * @param items The data list.
     * @param key Optional stable key derived from each item (e.g., `{ it.id }`).
     * @param contentDescription Optional description of each item for accessibility services
     *   (e.g., `{ it.title }`).
     * @param itemContent Composable for each item.
     */
    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        contentDescription: ((item: T) -> String?)? = null,
        itemContent: @Composable (item: T, isFocused: Boolean) -> Unit
    ) {
        val list = items
        this.itemCount = list.size
        this.itemKey = if (key != null) { index -> key(list[index]) } else null
        this.itemContentDescription = if (contentDescription != null) {
            { index -> list.getOrNull(index)?.let(contentDescription) }
        } else {
            null
        }
        this.itemContent = { index, isFocused -> itemContent(list[index], isFocused) }
    }
}

/**
 * Scope for declaring rows inside [RokuLazyColumn].
 *
 * Usage:
 * ```
 * RokuLazyColumn(...) {
 *     row(
 *         itemWidth = 580.dp, itemHeight = 310.dp, itemSpacing = 20.dp,
 *         headerHeight = 30.dp, key = "hero",
 *         header = { isFocused -> Text("Hero", ...) }
 *     ) {
 *         items(heroMovies) { movie, isFocused ->
 *             BannerCard(movie = movie, isFocused = isFocused)
 *         }
 *     }
 *
 *     customRow(height = 44.dp, key = "chips") { isRowFocused ->
 *         GenreChips(isRowFocused = isRowFocused)
 *     }
 * }
 * ```
 */
@RokuDsl
class RokuLazyColumnScope internal constructor() {
    internal val rows = mutableListOf<RowSpec>()

    internal sealed class RowSpec {
        abstract val key: Any?
        abstract val headerHeight: Dp

        class Items(
            override val key: Any?,
            override val headerHeight: Dp,
            val itemWidth: Dp,
            val itemHeight: Dp,
            val itemSpacing: Dp,
            val contentPadding: PaddingValues,
            val focusSlot: Int,
            val initialIndex: Int,
            val focusMode: RokuFocusMode,
            val header: (@Composable (isRowFocused: Boolean) -> Unit)?,
            val itemCount: Int,
            val itemKey: ((Int) -> Any)?,
            val itemContentDescription: ((Int) -> String?)?,
            val itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
        ) : RowSpec()

        class Custom(
            override val key: Any?,
            override val headerHeight: Dp,
            val height: Dp,
            val showHighlight: Boolean,
            val header: (@Composable (isRowFocused: Boolean) -> Unit)?,
            val onKeyEvent: ((RokuNavKey) -> Boolean)?,
            val content: @Composable (isRowFocused: Boolean) -> Unit
        ) : RowSpec()
    }

    /**
     * Declare a horizontal row with fixed-size items.
     *
     * Sizing is optional: leave [itemWidth] / [itemHeight] / [headerHeight] out and the column
     * measures them from the first item (and the header) by composing it invisibly once, the same
     * way the DSL [RokuLazyRow] auto-measures. Items still share one size — the first item's.
     * Passing explicit sizes skips that measuring pass, which is worth doing on screens with very
     * many rows.
     *
     * @param itemWidth Width of each card in this row. Omit to measure it from the first item.
     * @param itemHeight Height of each card (used for highlight sizing). Omit to measure it from
     *   the first item.
     * @param itemSpacing Horizontal gap between cards.
     * @param contentPadding Horizontal padding around the row content.
     * @param headerHeight Height of the [header] composable. Omit to measure the header; an
     *   explicit value must match the actual rendered height.
     * @param focusSlot Which visible slot the highlight sits at (0 = leftmost). Ignored in
     *   [RokuFocusMode.Floating].
     * @param initialIndex Item selected the first time this row's state is created. It is
     *   remembered as a request, so an index that only becomes valid once items arrive is honored
     *   then rather than clamped away.
     * @param key Stable identity for this row, following `LazyColumn`'s `key` contract. Supply one
     *   whenever rows can be inserted, removed, filtered or reordered: the row's selection state is
     *   remembered against this key, so without it selection stays attached to the *position* and
     *   silently moves to a different row. Keys must be unique within the column and savable.
     *   It sits after the sizing parameters so that 1.x positional calls keep their meaning.
     * @param focusMode How this row's highlight relates to horizontal scrolling; see
     *   [RokuFocusMode]. [focusSlot] only applies in [RokuFocusMode.Static].
     * @param header Optional composable rendered above the row. Receives `isRowFocused`.
     * @param content Item declarations via [RokuItemScope.items].
     */
    fun row(
        itemWidth: Dp = Dp.Unspecified,
        itemHeight: Dp = Dp.Unspecified,
        itemSpacing: Dp = 14.dp,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        headerHeight: Dp = Dp.Unspecified,
        focusSlot: Int = 0,
        initialIndex: Int = 0,
        key: Any? = null,
        focusMode: RokuFocusMode = RokuFocusMode.Static,
        header: (@Composable (isRowFocused: Boolean) -> Unit)? = null,
        content: RokuItemScope.() -> Unit
    ) {
        val scope = RokuItemScope().apply(content)
        rows.add(
            RowSpec.Items(
                key = key,
                headerHeight = headerHeight,
                itemWidth = itemWidth,
                itemHeight = itemHeight,
                itemSpacing = itemSpacing,
                contentPadding = contentPadding,
                focusSlot = focusSlot,
                initialIndex = initialIndex,
                focusMode = focusMode,
                header = header,
                itemCount = scope.itemCount,
                itemKey = scope.itemKey,
                itemContentDescription = scope.itemContentDescription,
                itemContent = scope.itemContent ?: { _, _ -> }
            )
        )
    }

    /**
     * Declare a row the column does not lay out — a hero pager, a chip strip, a multi-line grid,
     * anything that is not a rail of equal cards.
     *
     * The column keeps owning vertical navigation: UP/DOWN still moves between rows, the column
     * still scrolls to bring this row into view, and the global highlight still animates to this
     * row's Y. While the row is selected, LEFT/RIGHT/ENTER are handed to [onKeyEvent] instead of
     * being applied to a row of cards. Return `true` to consume the key, or `false` to let the
     * column apply its edge-escape policy — that is how the row says "I am at my own edge, let
     * focus leave".
     *
     * @param height Height of [content]. The column needs it up front to place rows and the
     *   highlight, so [content] must render at exactly this height.
     * @param headerHeight Height of the [header] composable. Must match its rendered height.
     * @param showHighlight Whether the column's global highlight is drawn over this row. Off by
     *   default, because a custom row usually draws its own focus treatment.
     * @param key Stable identity for this row, as on [row].
     * @param header Optional composable rendered above the row. Receives `isRowFocused`.
     * @param onKeyEvent Receives LEFT/RIGHT/ENTER while this row is selected.
     * @param content The row body. Receives whether this row is the focused one.
     */
    fun customRow(
        height: Dp,
        headerHeight: Dp = 0.dp,
        showHighlight: Boolean = false,
        key: Any? = null,
        header: (@Composable (isRowFocused: Boolean) -> Unit)? = null,
        onKeyEvent: ((navKey: RokuNavKey) -> Boolean)? = null,
        content: @Composable (isRowFocused: Boolean) -> Unit
    ) {
        rows.add(
            RowSpec.Custom(
                key = key,
                headerHeight = headerHeight,
                height = height,
                showHighlight = showHighlight,
                header = header,
                onKeyEvent = onKeyEvent,
                content = content
            )
        )
    }
}
