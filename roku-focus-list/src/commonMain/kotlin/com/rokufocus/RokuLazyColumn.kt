package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** Cached per-frame size animation spec — avoids allocation on every recomposition. */
private val HighlightSizeSpec: AnimationSpec<Float> = tween(durationMillis = 100, easing = FastOutSlowInEasing)

/**
 * The dp values the column's geometry depends on, per row. Compared by value so the expensive
 * pixel maths below only reruns when something that actually moves a row changes.
 */
private data class RowMetrics(
    val headerHeight: Dp,
    val contentHeight: Dp,
    val itemWidth: Dp,
    val itemSpacing: Dp,
    val startPadding: Dp,
    val endPadding: Dp
)

/** Plain holder, not snapshot state: nothing observes which row the column last marked focused. */
private class FocusedRowRef {
    var value: RokuFocusListState? = null
}

private class ColumnGeometry(
    val rowCumOffsetPx: FloatArray,
    val rowHeightsPx: FloatArray,
    val maxVerticalScrollPx: Float,
    val topPaddingPx: Float,
    val viewportHeightPx: Float
)

/** Cached density conversions for the active row — avoids recomputing 6 toPx() calls per frame. */
private class ActiveRowPx(
    val headerPx: Float,
    val contentHeightPx: Float,
    val itemWidthPx: Float,
    val itemSpacingPx: Float,
    val startPadPx: Float,
    val endPadPx: Float
)

@Composable
internal fun RokuLazyColumnImpl(
    rows: List<RokuResolvedRow>,
    state: RokuColumnState,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    verticalFocusMode: RokuFocusMode = RokuFocusMode.Static,
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    rowHeader: (@Composable (rowIndex: Int, isRowFocused: Boolean) -> Unit)? = null,
    itemContent: @Composable (rowIndex: Int, itemIndex: Int, isFocused: Boolean) -> Unit
) {
    // Published before anything reads selectedRowIndex, so the row it resolves to is always one
    // that exists and has something to select.
    state.syncRows(rows.size) { rows[it].isSelectable }

    if (rows.isEmpty()) {
        DisposableEffect(state) {
            onDispose { state.hasFocus = false }
        }
        return
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val lazyColumnState = rememberLazyListState()

    val selectedRowIndex = state.selectedRowIndex
    val activeRow = rows[selectedRowIndex]
    val activeItemIndex = rows.selectedItemIndexIn(selectedRowIndex)

    // Rows learn whether they render as focused, so consumers reading RokuFocusListState.hasFocus
    // see the same thing the header lambda does. The previously focused state is tracked so the
    // column can retract what it asserted even when that row has since left the list — a hoisted
    // state must never be left reading "focused" by a column that no longer renders it.
    val focusedRowState = (activeRow as? RokuResolvedRow.Items)
        ?.config?.state
        ?.takeIf { state.hasFocus }
    val focusedRowRef = remember { FocusedRowRef() }
    if (focusedRowRef.value !== focusedRowState) {
        focusedRowRef.value?.hasFocus = false
        focusedRowState?.hasFocus = true
        focusedRowRef.value = focusedRowState
    }
    DisposableEffect(state) {
        onDispose {
            state.hasFocus = false
            focusedRowRef.value?.hasFocus = false
            focusedRowRef.value = null
        }
    }

    val selectedDescription = (activeRow as? RokuResolvedRow.Items)
        ?.let { it.config.itemContentDescription?.invoke(it.config.state.selectedIndex) }

    val rowKeys: (Int) -> Any = remember(rows) {
        if (rows.all { it.key != null }) {
            { index -> rows[index].key!! }
        } else {
            { index -> index }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(state.focusRequester)
            .onFocusChanged { focusState ->
                val newFocus = focusState.hasFocus || focusState.isFocused
                if (newFocus != state.hasFocus) {
                    if (newFocus) onFocusEnter?.invoke() else onFocusExit?.invoke()
                    state.hasFocus = newFocus
                }
            }
            .focusable()
            .semantics {
                collectionInfo = CollectionInfo(rowCount = rows.size, columnCount = UnknownColumnCount)
                if (selectedDescription != null) {
                    contentDescription = selectedDescription
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .rokuColumnKeyHandler(rows, state, config, onItemSelected, onItemClicked)
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }

        // An item row with nothing in it renders nothing at all — no dangling header — so it must
        // contribute nothing to the geometry either, or every row below it lands at the wrong Y.
        val metrics = rows.map { row ->
            when (row) {
                is RokuResolvedRow.Items -> if (row.isSelectable) {
                    RowMetrics(
                        headerHeight = row.headerHeight,
                        contentHeight = row.contentHeight,
                        itemWidth = row.config.itemWidth,
                        itemSpacing = row.config.itemSpacing,
                        startPadding = row.config.contentPadding.calculateLeftPadding(layoutDirection),
                        endPadding = row.config.contentPadding.calculateRightPadding(layoutDirection)
                    )
                } else {
                    EmptyRowMetrics
                }

                is RokuResolvedRow.Custom -> RowMetrics(
                    headerHeight = row.headerHeight,
                    contentHeight = row.contentHeight,
                    itemWidth = 0.dp,
                    itemSpacing = 0.dp,
                    startPadding = 0.dp,
                    endPadding = 0.dp
                )
            }
        }

        // How many cards fit each rail, from the real viewport rather than the screen width.
        rows.forEachIndexed { index, row ->
            if (row is RokuResolvedRow.Items) {
                val rowMetrics = metrics[index]
                val available = maxWidth - rowMetrics.startPadding - rowMetrics.endPadding
                val denominator = rowMetrics.itemWidth + rowMetrics.itemSpacing
                val visible = if (denominator > 0.dp) {
                    ((available + rowMetrics.itemSpacing) / denominator).toInt().coerceAtLeast(1)
                } else 1
                if (row.config.state.visibleCount != visible) row.config.state.visibleCount = visible
            }
        }

        val geometry = remember(metrics, density, contentPadding, rowSpacing, maxHeight) {
            val topPx = with(density) { contentPadding.calculateTopPadding().toPx() }
            val bottomPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
            val spacingPx = with(density) { rowSpacing.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }

            val heights = FloatArray(metrics.size) { i ->
                with(density) { (metrics[i].headerHeight + metrics[i].contentHeight).toPx() }
            }
            val cumOffset = FloatArray(metrics.size)
            for (i in 1 until metrics.size) {
                cumOffset[i] = cumOffset[i - 1] + heights[i - 1] + spacingPx
            }

            val totalContent = topPx + heights.sum() + max(0, metrics.size - 1) * spacingPx + bottomPx
            ColumnGeometry(
                rowCumOffsetPx = cumOffset,
                rowHeightsPx = heights,
                maxVerticalScrollPx = (totalContent - viewportHeightPx).coerceAtLeast(0f),
                topPaddingPx = topPx,
                viewportHeightPx = viewportHeightPx
            )
        }

        // ── Vertical scroll + overflow correction ──
        val scrollTargetRow = if (verticalFocusMode == RokuFocusMode.Floating) {
            // Containment mirrors the horizontal anchor: written back on the writes that move the
            // selection relative to the window (which composition observes here, the only place
            // the pixel geometry exists), never derived in a getter — an unstored shift would
            // flip-flop. Compared against the clamped anchor so a purely data-driven shrink does
            // not overwrite what the raw value still remembers.
            val anchor = state.windowAnchorRow.coerceIn(0, rows.lastIndex)
            val contained = containVerticalWindow(
                anchorRow = anchor,
                selectedRow = selectedRowIndex,
                rowCumOffsetPx = geometry.rowCumOffsetPx,
                rowHeightsPx = geometry.rowHeightsPx,
                viewportHeightPx = geometry.viewportHeightPx,
                topPaddingPx = geometry.topPaddingPx
            )
            if (contained != anchor) state.windowAnchorRow = contained
            contained
        } else {
            selectedRowIndex
        }

        val desiredVerticalScrollPx = geometry.rowCumOffsetPx.getOrElse(scrollTargetRow) { 0f }
        val verticalScrollOverflowPx =
            (desiredVerticalScrollPx - geometry.maxVerticalScrollPx).coerceAtLeast(0f)

        // How far below the window row the selected row sits. Zero in Static, where the scroll
        // target is the selected row itself.
        val windowOffsetPx =
            geometry.rowCumOffsetPx.getOrElse(selectedRowIndex) { 0f } - desiredVerticalScrollPx

        // Keyed on the geometry as well as the scroll target: scrolling to the last row of a list
        // that is still loading gets clamped, and once later rows arrive the column would
        // otherwise sit at that clamped offset while the highlight maths assumed the unclamped one.
        LaunchedEffect(scrollTargetRow, geometry) {
            if (state.keyRepeat.consecutivePresses > config.keyRepeatAccelAfter) {
                lazyColumnState.scrollToItem(scrollTargetRow)
            } else {
                lazyColumnState.animateScrollToItem(scrollTargetRow)
            }
        }

        // ── Highlight position (density conversions cached per active row) ──
        val activeMetrics = metrics[selectedRowIndex]
        val activePx = remember(activeMetrics, density) {
            with(density) {
                ActiveRowPx(
                    headerPx = activeMetrics.headerHeight.toPx(),
                    contentHeightPx = activeMetrics.contentHeight.toPx(),
                    itemWidthPx = activeMetrics.itemWidth.toPx(),
                    itemSpacingPx = activeMetrics.itemSpacing.toPx(),
                    startPadPx = activeMetrics.startPadding.toPx(),
                    endPadPx = activeMetrics.endPadding.toPx()
                )
            }
        }

        val targetHighlightY =
            geometry.topPaddingPx + windowOffsetPx + verticalScrollOverflowPx + activePx.headerPx
        val targetHighlightX = if (activeRow is RokuResolvedRow.Items) {
            computeHighlightOffsetPx(
                activeRow.config.state, activePx.itemWidthPx, activePx.itemSpacingPx,
                activePx.startPadPx, activePx.endPadPx, viewportWidthPx
            )
        } else {
            0f
        }
        val targetHighlightWidth = if (activeRow is RokuResolvedRow.Items) {
            activePx.itemWidthPx
        } else {
            viewportWidthPx
        }

        // ── Animate highlight: full spec for position, fast tween for size ──
        val spec = config.highlightAnimationSpec
        val animatedX by animateFloatAsState(targetHighlightX, spec, label = "hl_x")
        val animatedY by animateFloatAsState(targetHighlightY, spec, label = "hl_y")
        val animatedWidth by animateFloatAsState(targetHighlightWidth, HighlightSizeSpec, label = "hl_w")
        val animatedHeight by animateFloatAsState(activePx.contentHeightPx, HighlightSizeSpec, label = "hl_h")

        // ── Render ──
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                userScrollEnabled = false
            ) {
                items(
                    count = rows.size,
                    key = rowKeys
                ) { rowIndex ->
                    val row = rows[rowIndex]
                    val isRowFocused = state.hasFocus && rowIndex == selectedRowIndex

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (row.isSelectable) {
                            val header = row.header
                            if (header != null) {
                                header(isRowFocused)
                            } else {
                                rowHeader?.invoke(rowIndex, isRowFocused)
                            }
                        }

                        when (row) {
                            is RokuResolvedRow.Items -> RokuRowContent(
                                state = row.config.state,
                                contentPadding = row.config.contentPadding,
                                itemWidth = row.config.itemWidth,
                                itemSpacing = row.config.itemSpacing,
                                rowIndex = rowIndex,
                                itemKey = row.itemKey,
                                itemContentDescription = row.config.itemContentDescription,
                                itemContent = { itemIndex, isFocused ->
                                    itemContent(rowIndex, itemIndex, isFocused && isRowFocused)
                                }
                            )

                            is RokuResolvedRow.Custom -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(row.contentHeight)
                            ) {
                                row.content(isRowFocused)
                            }
                        }
                    }
                }
            }

            // Single global highlight overlay
            if (activeRow.showHighlight && activeRow.isSelectable) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = animatedX
                            translationY = animatedY
                        }
                        .layout { measurable, _ ->
                            val w = animatedWidth.roundToInt().coerceAtLeast(0)
                            val h = animatedHeight.roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                ) {
                    RokuHighlightScopeImpl(
                        boxScope = this,
                        rowIndex = selectedRowIndex,
                        itemIndex = activeItemIndex
                    ).focusHighlight(state.hasFocus)
                }
            }
        }
    }
}

/** `CollectionInfo` treats a negative count as "unknown", which is right for ragged rails. */
private const val UnknownColumnCount = -1

/**
 * Where the vertical floating window must start so the selected row is fully visible, moved
 * minimally from [anchorRow]. The window is pixel-based, not row-based: rows have heterogeneous
 * heights and an empty row contributes zero, so "visible" means the selected row's
 * `[top, top + height]` span fits between the window row's top and the viewport bottom.
 */
internal fun containVerticalWindow(
    anchorRow: Int,
    selectedRow: Int,
    rowCumOffsetPx: FloatArray,
    rowHeightsPx: FloatArray,
    viewportHeightPx: Float,
    topPaddingPx: Float
): Int {
    if (rowCumOffsetPx.isEmpty()) return 0
    val anchor = anchorRow.coerceIn(0, rowCumOffsetPx.lastIndex)
    val selected = selectedRow.coerceIn(0, rowCumOffsetPx.lastIndex)
    val selectedTop = rowCumOffsetPx[selected]
    val selectedBottom = selectedTop + rowHeightsPx[selected]
    val windowHeightPx = viewportHeightPx - topPaddingPx
    return when {
        selectedTop < rowCumOffsetPx[anchor] -> selected
        selectedBottom > rowCumOffsetPx[anchor] + windowHeightPx -> {
            // Smallest forward shift that fits the selected row's bottom edge. A row taller than
            // the window degenerates to the selected row itself, top-aligned like Static.
            var row = anchor
            while (row < selected && rowCumOffsetPx[row] < selectedBottom - windowHeightPx) row++
            row
        }

        else -> anchor
    }
}

/** Geometry of a row that renders nothing, so the rows below it are placed where they really are. */
private val EmptyRowMetrics = RowMetrics(
    headerHeight = 0.dp,
    contentHeight = 0.dp,
    itemWidth = 0.dp,
    itemSpacing = 0.dp,
    startPadding = 0.dp,
    endPadding = 0.dp
)
