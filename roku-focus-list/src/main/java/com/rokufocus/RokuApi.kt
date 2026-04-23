package com.rokufocus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
// roku-focus-list — Public API
//
// Two styles for each component:
//   1. DSL-based  — state managed internally, LazyRow-like builder syntax
//   2. State-based — caller provides RokuFocusListState for full control
// ═══════════════════════════════════════════════════════════════════════════════

// ─── RokuLazyRow (DSL — auto-measured width) ────────────────────────────────

/**
 * Roku-style fixed-focus horizontal list with **DSL item builder**.
 *
 * Item width is **auto-measured** from the first item — just like LazyRow,
 * items size themselves. State is managed internally.
 *
 * ```
 * RokuLazyRow(
 *     contentPadding = PaddingValues(horizontal = 48.dp),
 *     itemSpacing = 14.dp,
 * ) {
 *     items(movies) { movie, isFocused ->
 *         MovieCard(movie = movie, isFocused = isFocused)
 *     }
 * }
 * ```
 *
 * @param modifier Modifier applied to the outer container.
 * @param config Navigation behavior (animation, key repeat, haptics, wrap-around).
 * @param contentPadding Padding around the row content.
 * @param itemSpacing Horizontal gap between items.
 * @param focusSlot Which visible slot the highlight sits at (0 = leftmost).
 * @param focusHighlight Composable that renders the focus border.
 * @param onItemSelected Called when the selected item changes.
 * @param onItemClicked Called on Enter/DpadCenter press.
 * @param onFocusEnter Called when this row gains focus.
 * @param onFocusExit Called when this row loses focus.
 * @param content Item declarations via [RokuItemScope.items].
 */
@Composable
fun RokuLazyRow(
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 12.dp,
    focusSlot: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    content: RokuItemScope.() -> Unit
) {
    val scope = RokuItemScope().apply(content)
    if (scope.itemCount == 0) return
    val state = rememberRokuFocusListState(itemCount = scope.itemCount, focusSlot = focusSlot)
    val density = LocalDensity.current
    val itemContent = scope.itemContent ?: return

    // Auto-measure first item to determine width
    var measuredWidthPx by remember { mutableIntStateOf(0) }

    if (measuredWidthPx == 0) {
        // Invisible measurement: compose one item to capture its natural width.
        // Do NOT apply caller's modifier here — it may contain focusRequester/padding
        // that should only apply to the real row.
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = 0f }
                .onSizeChanged { measuredWidthPx = it.width }
        ) {
            itemContent(0, false)
        }
    } else {
        val itemWidth = with(density) { measuredWidthPx.toDp() }
        RokuLazyRowImpl(
            state = state,
            modifier = modifier,
            config = config,
            contentPadding = contentPadding,
            itemWidth = itemWidth,
            itemSpacing = itemSpacing,
            focusHighlight = focusHighlight,
            onItemSelected = onItemSelected,
            onItemClicked = onItemClicked,
            onFocusEnter = onFocusEnter,
            onFocusExit = onFocusExit,
            itemContent = itemContent
        )
    }
}

// ─── RokuLazyRow (State-based — explicit width) ─────────────────────────────

/**
 * Roku-style fixed-focus horizontal list with **external state control**.
 *
 * Use when you need programmatic selection (`state.scrollTo(index)`)
 * or explicit item width.
 *
 * @param state Row state created via [rememberRokuFocusListState].
 * @param itemWidth Fixed width of each item.
 * @param modifier Modifier applied to the outer container.
 * @param config Navigation behavior (animation, key repeat, haptics, wrap-around).
 * @param contentPadding Padding around the row content.
 * @param itemSpacing Horizontal gap between items.
 * @param focusHighlight Composable that renders the focus border.
 * @param onItemSelected Called when the selected item changes.
 * @param onItemClicked Called on Enter/DpadCenter press.
 * @param onFocusEnter Called when this row gains focus.
 * @param onFocusExit Called when this row loses focus.
 * @param itemContent Composable for each item. Receives `index` and `isFocused`.
 */
@Composable
fun RokuLazyRow(
    state: RokuFocusListState,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 12.dp,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
) {
    RokuLazyRowImpl(
        state = state,
        modifier = modifier,
        config = config,
        contentPadding = contentPadding,
        itemWidth = itemWidth,
        itemSpacing = itemSpacing,
        focusHighlight = focusHighlight,
        onItemSelected = onItemSelected,
        onItemClicked = onItemClicked,
        onFocusEnter = onFocusEnter,
        onFocusExit = onFocusExit,
        itemContent = itemContent
    )
}

// ─── RokuLazyColumn (DSL) ────────────────────────────────────────────────────

/**
 * OTT-style vertical + horizontal navigation with **DSL row builder**.
 *
 * Each [row][RokuLazyColumnScope.row] declares its own card dimensions,
 * header, and items. State for each row is managed internally.
 *
 * ```
 * RokuLazyColumn(rowSpacing = 8.dp) {
 *     row(itemWidth = 580.dp, itemHeight = 310.dp,
 *         header = { Text("Hero") }
 *     ) {
 *         items(heroMovies) { movie, isFocused -> BannerCard(movie, isFocused) }
 *     }
 *     row(itemWidth = 220.dp, itemHeight = 140.dp,
 *         header = { Text("Trending") }
 *     ) {
 *         items(trendingMovies) { movie, isFocused -> MovieCard(movie, isFocused) }
 *     }
 * }
 * ```
 *
 * @param modifier Modifier applied to the outer container.
 * @param config Navigation behavior (animation, key repeat, acceleration, wrap-around).
 * @param contentPadding Vertical padding around the column content.
 * @param rowSpacing Vertical gap between rows.
 * @param initialRowIndex Which row to focus on first.
 * @param focusHighlight Composable that renders the focus border.
 * @param onItemSelected Called when selection changes. Receives `(rowIndex, itemIndex)`.
 * @param onItemClicked Called on Enter/DpadCenter. Receives `(rowIndex, itemIndex)`.
 * @param content Row declarations via [RokuLazyColumnScope.row].
 */
@Composable
fun RokuLazyColumn(
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    initialRowIndex: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    content: RokuLazyColumnScope.() -> Unit
) {
    val scope = RokuLazyColumnScope().apply(content)

    val rowConfigs = scope.rows.mapIndexed { i, spec ->
        val state = rememberRokuFocusListState(
            itemCount = spec.itemCount,
            focusSlot = spec.focusSlot
        )
        RokuColumnRowConfig(
            state = state,
            itemWidth = spec.itemWidth,
            itemHeight = spec.itemHeight,
            itemSpacing = spec.itemSpacing,
            contentPadding = spec.contentPadding,
            headerHeight = spec.headerHeight
        )
    }

    RokuLazyColumnImpl(
        rows = rowConfigs,
        modifier = modifier,
        config = config,
        contentPadding = contentPadding,
        rowSpacing = rowSpacing,
        initialRowIndex = initialRowIndex,
        focusHighlight = focusHighlight,
        onItemSelected = onItemSelected,
        onItemClicked = onItemClicked,
        rowHeader = { rowIndex, isRowFocused ->
            scope.rows.getOrNull(rowIndex)?.header?.invoke(isRowFocused)
        },
        itemContent = { rowIndex, itemIndex, isFocused ->
            scope.rows.getOrNull(rowIndex)?.itemContent?.invoke(itemIndex, isFocused)
        }
    )
}

// ─── RokuLazyColumn (State-based) ────────────────────────────────────────────

/**
 * OTT-style vertical + horizontal navigation with **external state control**.
 *
 * Use when you need programmatic access to per-row selection.
 *
 * @param rows List of row configurations, each with its own [RokuFocusListState].
 * @param modifier Modifier applied to the outer container.
 * @param config Navigation behavior (animation, key repeat, acceleration, wrap-around).
 * @param contentPadding Vertical padding around the column content.
 * @param rowSpacing Vertical gap between rows.
 * @param initialRowIndex Which row to focus on first.
 * @param focusHighlight Composable that renders the focus border.
 * @param onItemSelected Called when selection changes. Receives `(rowIndex, itemIndex)`.
 * @param onItemClicked Called on Enter/DpadCenter. Receives `(rowIndex, itemIndex)`.
 * @param rowHeader Optional composable above each row. Height **must** match [RokuColumnRowConfig.headerHeight].
 * @param itemContent Composable for each item. Receives `(rowIndex, itemIndex, isFocused)`.
 */
@Composable
fun RokuLazyColumn(
    rows: List<RokuColumnRowConfig>,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    initialRowIndex: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    rowHeader: (@Composable (rowIndex: Int, isRowFocused: Boolean) -> Unit)? = null,
    itemContent: @Composable (rowIndex: Int, itemIndex: Int, isFocused: Boolean) -> Unit
) {
    RokuLazyColumnImpl(
        rows = rows,
        modifier = modifier,
        config = config,
        contentPadding = contentPadding,
        rowSpacing = rowSpacing,
        initialRowIndex = initialRowIndex,
        focusHighlight = focusHighlight,
        onItemSelected = onItemSelected,
        onItemClicked = onItemClicked,
        rowHeader = rowHeader,
        itemContent = itemContent
    )
}
