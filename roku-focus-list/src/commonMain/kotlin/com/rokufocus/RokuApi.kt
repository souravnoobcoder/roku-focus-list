package com.rokufocus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.takeOrElse

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
 * items size themselves. State is managed internally; use the state-based overload when you need
 * to read or drive the selection from outside.
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
 * @param config Navigation behavior (animation, key repeat, haptics, wrap-around, focus escape).
 * @param contentPadding Padding around the row content.
 * @param itemSpacing Horizontal gap between items.
 * @param focusSlot Which visible slot the highlight sits at (0 = leftmost). Ignored in
 *   [RokuFocusMode.Floating].
 * @param initialIndex Item selected the first time the row's state is created. It is remembered as
 *   a request, so an index that only becomes valid once items arrive is honored then rather than
 *   clamped away.
 * @param focusHighlight Renders the focus border. See [RokuHighlightScope].
 * @param onItemSelected Called when the selected item changes.
 * @param onItemClicked Called on Enter/DpadCenter press.
 * @param onFocusEnter Called when this row gains focus.
 * @param onFocusExit Called when this row loses focus.
 * @param focusMode How the highlight relates to scrolling: parked at [focusSlot] while content
 *   scrolls ([RokuFocusMode.Static]), or walking the visible items and scrolling only at the
 *   window's edges ([RokuFocusMode.Floating]).
 * @param content Item declarations via [RokuItemScope.items].
 */
@Composable
fun RokuLazyRow(
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 12.dp,
    focusSlot: Int = 0,
    initialIndex: Int = 0,
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    focusMode: RokuFocusMode = RokuFocusMode.Static,
    content: RokuItemScope.() -> Unit
) {
    val scope = RokuItemScope().apply(content)

    // Remembered before the empty-list bail-out below: a row whose data momentarily empties must
    // come back to the item it was on, not to a freshly created state.
    val state = rememberRokuFocusListState(
        itemCount = scope.itemCount,
        initialIndex = initialIndex,
        focusSlot = focusSlot,
        focusMode = focusMode
    )
    val density = LocalDensity.current
    val itemContent = scope.itemContent
    if (scope.itemCount == 0 || itemContent == null) return

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
            itemKey = scope.itemKey,
            itemContentDescription = scope.itemContentDescription,
            itemContent = itemContent
        )
    }
}

// ─── RokuLazyRow (State-based — explicit width) ─────────────────────────────

/**
 * Roku-style fixed-focus horizontal list with **external state control**.
 *
 * Use when you need programmatic selection (`state.scrollTo(index)`), focus control
 * (`state.requestFocus()`), or explicit item width.
 *
 * @param state Row state created via [rememberRokuFocusListState].
 * @param itemWidth Fixed width of each item.
 * @param modifier Modifier applied to the outer container.
 * @param config Navigation behavior (animation, key repeat, haptics, wrap-around, focus escape).
 * @param contentPadding Padding around the row content.
 * @param itemSpacing Horizontal gap between items.
 * @param focusHighlight Renders the focus border. See [RokuHighlightScope].
 * @param onItemSelected Called when the selected item changes.
 * @param onItemClicked Called on Enter/DpadCenter press.
 * @param onFocusEnter Called when this row gains focus.
 * @param onFocusExit Called when this row loses focus.
 * @param itemKey Stable key per item, following `LazyRow`'s `key` contract.
 * @param itemContentDescription Describes an item to accessibility services. The selected item's
 *   description is surfaced on the row, which is the node screen readers see.
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
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    itemKey: ((index: Int) -> Any)? = null,
    itemContentDescription: ((index: Int) -> String?)? = null,
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
        itemKey = itemKey,
        itemContentDescription = itemContentDescription,
        itemContent = itemContent
    )
}

// ─── RokuLazyColumn (DSL) ────────────────────────────────────────────────────

/**
 * OTT-style vertical + horizontal navigation with **DSL row builder**.
 *
 * Each [row][RokuLazyColumnScope.row] declares its own card dimensions, header, and items — or
 * omits the dimensions and lets the column measure them from the first item, so any composable
 * fits without size bookkeeping. [customRow][RokuLazyColumnScope.customRow] drops in anything
 * that is not a rail of equal cards.
 * Per-row selection state is managed internally; the column's own selection is hoistable through
 * [state].
 *
 * ```
 * RokuLazyColumn(rowSpacing = 8.dp) {
 *     row(
 *         key = "hero",
 *         itemWidth = 580.dp, itemHeight = 310.dp,
 *         header = { Text("Hero") }
 *     ) {
 *         items(heroMovies) { movie, isFocused -> BannerCard(movie, isFocused) }
 *     }
 *     row(
 *         key = "trending",
 *         itemWidth = 220.dp, itemHeight = 140.dp,
 *         header = { Text("Trending") }
 *     ) {
 *         items(trendingMovies) { movie, isFocused -> MovieCard(movie, isFocused) }
 *     }
 * }
 * ```
 *
 * @param modifier Modifier applied to the outer container.
 * @param state Which row is selected. Hoist it to read or drive the selection, or to move platform
 *   focus onto the column with [RokuColumnState.requestFocus].
 * @param config Navigation behavior (animation, key repeat, acceleration, wrap-around, focus escape).
 * @param contentPadding Vertical padding around the column content.
 * @param rowSpacing Vertical gap between rows.
 * @param focusHighlight Renders the focus border. See [RokuHighlightScope].
 * @param onItemSelected Called when selection changes. Receives `(rowIndex, itemIndex)`.
 * @param onItemClicked Called on Enter/DpadCenter. Receives `(rowIndex, itemIndex)`.
 * @param onFocusEnter Called when the column gains focus.
 * @param onFocusExit Called when the column loses focus.
 * @param verticalFocusMode How the highlight relates to vertical scrolling: pinned to the top row
 *   while rows scroll behind it ([RokuFocusMode.Static]), or walking the visible rows and
 *   scrolling only at the window's edges ([RokuFocusMode.Floating]). Each row's horizontal mode is
 *   its own `focusMode`; the two axes are independent.
 * @param content Row declarations via [RokuLazyColumnScope.row] and
 *   [RokuLazyColumnScope.customRow].
 */
@Composable
fun RokuLazyColumn(
    modifier: Modifier = Modifier,
    state: RokuColumnState = rememberRokuColumnState(),
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    verticalFocusMode: RokuFocusMode = RokuFocusMode.Static,
    content: RokuLazyColumnScope.() -> Unit
) {
    val scope = RokuLazyColumnScope().apply(content)

    // All-or-nothing, so a half-keyed column can never have a consumer key collide with the
    // positional fallback of a neighbouring row.
    val allKeyed = scope.rows.isNotEmpty() && scope.rows.all { it.key != null }

    // Sizes measured for rows that omitted them, keyed the same way row state is, so a measured
    // size follows its row across insertions and reorders. Measured once per row and kept: the
    // first item's size is the row's size, exactly as in the auto-measured [RokuLazyRow].
    val measuredItemSizes = remember { mutableStateMapOf<Any, DpSize>() }
    val measuredHeaderHeights = remember { mutableStateMapOf<Any, Dp>() }

    val resolvedRows = scope.rows.mapIndexed { index, spec ->
        when (spec) {
            is RokuLazyColumnScope.RowSpec.Items -> {
                val rowId: Any = if (allKeyed) spec.key!! else index
                val rowState = key(rowId) {
                    rememberRokuFocusListState(
                        itemCount = spec.itemCount,
                        initialIndex = spec.initialIndex,
                        focusSlot = spec.focusSlot,
                        focusMode = spec.focusMode
                    )
                }
                val measured = measuredItemSizes[rowId]
                val itemWidth = spec.itemWidth.takeOrElse { measured?.width ?: Dp.Unspecified }
                val itemHeight = spec.itemHeight.takeOrElse { measured?.height ?: Dp.Unspecified }
                val headerHeight = when {
                    spec.headerHeight.isSpecified -> spec.headerHeight
                    spec.header == null -> 0.dp
                    else -> measuredHeaderHeights[rowId] ?: Dp.Unspecified
                }
                RokuResolvedRow.Items(
                    key = spec.key,
                    header = spec.header,
                    config = RokuColumnRowConfig(
                        state = rowState,
                        itemWidth = itemWidth.takeOrElse { 0.dp },
                        itemHeight = itemHeight.takeOrElse { 0.dp },
                        itemSpacing = spec.itemSpacing,
                        contentPadding = spec.contentPadding,
                        headerHeight = headerHeight.takeOrElse { 0.dp },
                        key = spec.key,
                        itemContentDescription = spec.itemContentDescription
                    ),
                    itemKey = spec.itemKey,
                    awaitingMeasure = spec.itemCount > 0 &&
                        (itemWidth.isUnspecified || itemHeight.isUnspecified || headerHeight.isUnspecified)
                )
            }

            is RokuLazyColumnScope.RowSpec.Custom -> RokuResolvedRow.Custom(
                key = spec.key,
                headerHeight = spec.headerHeight,
                contentHeight = spec.height,
                showHighlight = spec.showHighlight,
                header = spec.header,
                onKeyEvent = spec.onKeyEvent,
                content = spec.content
            )
        }
    }

    RokuColumnAutoMeasure(
        rows = scope.rows,
        allKeyed = allKeyed,
        measuredItemSizes = measuredItemSizes,
        measuredHeaderHeights = measuredHeaderHeights,
        modifier = modifier
    ) {
        RokuLazyColumnImpl(
            rows = resolvedRows,
            state = state,
            modifier = Modifier.fillMaxSize(),
            config = config,
            contentPadding = contentPadding,
            rowSpacing = rowSpacing,
            verticalFocusMode = verticalFocusMode,
            focusHighlight = focusHighlight,
            onItemSelected = onItemSelected,
            onItemClicked = onItemClicked,
            onFocusEnter = onFocusEnter,
            onFocusExit = onFocusExit,
            rowHeader = null,
            itemContent = { rowIndex, itemIndex, isFocused ->
                val spec = scope.rows.getOrNull(rowIndex)
                if (spec is RokuLazyColumnScope.RowSpec.Items) spec.itemContent(itemIndex, isFocused)
            }
        )
    }
}

/**
 * Hosts the column plus one invisible measurer per auto-sized row that has not reported yet.
 * Structurally constant — always this Box, whether anything needs measuring or not — because
 * swapping the tree's shape would discard the remembered row states inside the column.
 */
@Composable
private fun RokuColumnAutoMeasure(
    rows: List<RokuLazyColumnScope.RowSpec>,
    allKeyed: Boolean,
    measuredItemSizes: MutableMap<Any, DpSize>,
    measuredHeaderHeights: MutableMap<Any, Dp>,
    modifier: Modifier,
    column: @Composable () -> Unit
) {
    val density = LocalDensity.current
    Box(modifier = modifier) {
        rows.forEachIndexed { index, spec ->
            if (spec !is RokuLazyColumnScope.RowSpec.Items) return@forEachIndexed
            val rowId: Any = if (allKeyed) spec.key!! else index

            val needsItemMeasure = spec.itemCount > 0 &&
                (spec.itemWidth.isUnspecified || spec.itemHeight.isUnspecified) &&
                measuredItemSizes[rowId] == null
            if (needsItemMeasure) {
                key(rowId) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = 0f }
                            .onSizeChanged { size ->
                                measuredItemSizes[rowId] = with(density) {
                                    DpSize(size.width.toDp(), size.height.toDp())
                                }
                            }
                    ) {
                        spec.itemContent(0, false)
                    }
                }
            }

            val header = spec.header
            val needsHeaderMeasure = header != null &&
                spec.headerHeight.isUnspecified &&
                measuredHeaderHeights[rowId] == null
            if (needsHeaderMeasure) {
                key(rowId, "header") {
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = 0f }
                            .onSizeChanged { size ->
                                measuredHeaderHeights[rowId] = with(density) { size.height.toDp() }
                            }
                    ) {
                        header(false)
                    }
                }
            }
        }

        column()
    }
}

// ─── RokuLazyColumn (State-based) ────────────────────────────────────────────

/**
 * OTT-style vertical + horizontal navigation with **external state control**.
 *
 * Use when you need programmatic access to per-row selection. Custom rows are a DSL feature; this
 * overload renders card rails only.
 *
 * @param rows List of row configurations, each with its own [RokuFocusListState].
 * @param modifier Modifier applied to the outer container.
 * @param state Which row is selected. Hoist it to read or drive the selection, or to move platform
 *   focus onto the column with [RokuColumnState.requestFocus].
 * @param config Navigation behavior (animation, key repeat, acceleration, wrap-around, focus escape).
 * @param contentPadding Vertical padding around the column content.
 * @param rowSpacing Vertical gap between rows.
 * @param focusHighlight Renders the focus border. See [RokuHighlightScope].
 * @param onItemSelected Called when selection changes. Receives `(rowIndex, itemIndex)`.
 * @param onItemClicked Called on Enter/DpadCenter. Receives `(rowIndex, itemIndex)`.
 * @param onFocusEnter Called when the column gains focus.
 * @param onFocusExit Called when the column loses focus.
 * @param rowHeader Optional composable above each row. Height **must** match [RokuColumnRowConfig.headerHeight].
 * @param verticalFocusMode How the highlight relates to vertical scrolling: pinned to the top row
 *   while rows scroll behind it ([RokuFocusMode.Static]), or walking the visible rows and
 *   scrolling only at the window's edges ([RokuFocusMode.Floating]). Each row's horizontal mode is
 *   its state's `focusMode`; the two axes are independent.
 * @param itemContent Composable for each item. Receives `(rowIndex, itemIndex, isFocused)`.
 */
@Composable
fun RokuLazyColumn(
    rows: List<RokuColumnRowConfig>,
    modifier: Modifier = Modifier,
    state: RokuColumnState = rememberRokuColumnState(),
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    rowHeader: (@Composable (rowIndex: Int, isRowFocused: Boolean) -> Unit)? = null,
    verticalFocusMode: RokuFocusMode = RokuFocusMode.Static,
    itemContent: @Composable (rowIndex: Int, itemIndex: Int, isFocused: Boolean) -> Unit
) {
    val resolvedRows = rows.map { rowConfig ->
        RokuResolvedRow.Items(key = rowConfig.key, header = null, config = rowConfig)
    }

    RokuLazyColumnImpl(
        rows = resolvedRows,
        state = state,
        modifier = modifier,
        config = config,
        contentPadding = contentPadding,
        rowSpacing = rowSpacing,
        verticalFocusMode = verticalFocusMode,
        focusHighlight = focusHighlight,
        onItemSelected = onItemSelected,
        onItemClicked = onItemClicked,
        onFocusEnter = onFocusEnter,
        onFocusExit = onFocusExit,
        rowHeader = rowHeader,
        itemContent = itemContent
    )
}
