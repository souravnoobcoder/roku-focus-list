package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Plain holder, not snapshot state: nothing observes which row the column last marked focused. */
private class FocusedRowRef {
    var value: RokuFocusListState? = null
}

/**
 * Everything the per-press maths needs, precomputed in pixels, one slot per row. Built inside a
 * `derivedStateOf`, so it is recomputed when a row's content arrives or leaves (the item-count
 * reads below) — never on a selection move, which only indexes into these arrays.
 */
private class ColumnGeometry(
    val rowCumOffsetPx: FloatArray,
    val rowHeightsPx: FloatArray,
    val headerPx: FloatArray,
    val contentHeightPx: FloatArray,
    val itemWidthPx: FloatArray,
    val itemSpacingPx: FloatArray,
    val startPadPx: FloatArray,
    val endPadPx: FloatArray,
    val maxVerticalScrollPx: Float,
    val topPaddingPx: Float,
    val viewportHeightPx: Float,
    val rowSpacingPx: Float,
    val anchorOffsetPx: Float
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RokuLazyColumnImpl(
    rows: List<RokuResolvedRow>,
    state: RokuColumnState,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    verticalFocusMode: RokuFocusMode = RokuFocusMode.Static,
    verticalAnchorOffset: Dp = 0.dp,
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    rowHeader: (@Composable (rowIndex: Int, isRowFocused: Boolean) -> Unit)? = null
) {
    // Published before anything reads selectedRowIndex, so the row it resolves to is always one
    // that exists and has something to select.
    state.syncRows(rows.size) { rows[it].isSelectable }

    if (rows.isEmpty()) {
        if (state.activeRowState != null) state.activeRowState = null
        DisposableEffect(state) {
            onDispose { state.hasFocus = false }
        }
        return
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val touchpad = LocalRokuTouchpad.current
    // Laid out at the row the state already points to — see RokuRowContent — so a restored column
    // shows its remembered rows from the first frame. Floating anchors the window, Static scrolls
    // the selected row itself; both are the row the scroll effect below resolves. The anchor
    // offset is applied here too: the first frame is measured before the landing scroll runs, so
    // starting at the bare row would show it at the top edge for a frame and then drop it.
    val initialColumnPosition = remember {
        Snapshot.withoutReadObservation {
            val row = when (verticalFocusMode) {
                RokuFocusMode.Floating -> state.windowAnchorRow
                RokuFocusMode.Static -> state.selectedRowIndex
            }.coerceIn(0, rows.lastIndex)
            val spacingPx = with(density) { rowSpacing.toPx() }
            anchoredScrollPosition(
                row = row,
                anchorOffsetPx = with(density) { verticalAnchorOffset.toPx() }
            ) { i -> with(density) { rows[i].laidOutHeight.toPx() } + spacingPx }
        }
    }
    // Remembered: `rememberLazyListState` keys its saveable on the strategy instance, so a fresh
    // one per pass would rebuild the list state — and lose the scroll — on every recomposition.
    val prefetchStrategy = remember(config.rowPrefetchItemCount) {
        LazyListPrefetchStrategy(nestedPrefetchItemCount = config.rowPrefetchItemCount)
    }
    val lazyColumnState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialColumnPosition.index,
        initialFirstVisibleItemScrollOffset = initialColumnPosition.offset,
        prefetchStrategy = prefetchStrategy
    )

    val selectedRowIndex = state.selectedRowIndex
    val activeRow = rows[selectedRowIndex]
    val activeItemIndex = rows.selectedItemIndexIn(selectedRowIndex)

    // Published whether or not the column is focused: a touchpad swipe arrives from the host
    // layer, not through Compose focus. Guarded, so an unchanged row costs no snapshot write.
    val activeRowState = (activeRow as? RokuResolvedRow.Items)?.config?.state
    if (state.activeRowState !== activeRowState) state.activeRowState = activeRowState

    // Rows learn whether they render as focused, so consumers reading RokuFocusListState.hasFocus
    // see the same thing the header lambda does. The previously focused state is tracked so the
    // column can retract what it asserted even when that row has since left the list — a hoisted
    // state must never be left reading "focused" by a column that no longer renders it.
    val focusedRowState = activeRowState?.takeIf { state.hasFocus }
    val focusedRowRef = remember { FocusedRowRef() }
    if (focusedRowRef.value !== focusedRowState) {
        focusedRowRef.value?.hasFocus = false
        focusedRowState?.hasFocus = true
        focusedRowRef.value = focusedRowState
    }
    DisposableEffect(state) {
        onDispose {
            state.hasFocus = false
            state.activeRowState = null
            state.rowEntry = null
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
            .rokuTouchpadKeyGuard(touchpad)
            .rokuColumnKeyHandler(rows, state, config, onItemSelected, onItemClicked)
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }

        // How many cards fit each rail, from the real viewport rather than the screen width.
        // Keyed, not run per pass: this is O(rows) of Dp arithmetic that only depends on the rows
        // and the viewport, and a selection move must not pay for it.
        remember(rows, maxWidth, layoutDirection) {
            rows.forEach { row ->
                if (row is RokuResolvedRow.Items) {
                    val start = row.config.contentPadding.calculateLeftPadding(layoutDirection)
                    val end = row.config.contentPadding.calculateRightPadding(layoutDirection)
                    val available = maxWidth - start - end
                    val denominator = row.config.itemWidth + row.config.itemSpacing
                    val visible = if (denominator > 0.dp) {
                        ((available + row.config.itemSpacing) / denominator).toInt().coerceAtLeast(1)
                    } else 1
                    // First pass always reports — see RokuFocusListState.viewportMeasured.
                    val rowState = row.config.state
                    if (!rowState.viewportMeasured || rowState.visibleCount != visible) {
                        rowState.visibleCount = visible
                    }
                }
            }
        }

        // All pixel maths precomputed per row. derivedStateOf, not a value compared per pass:
        // the only observable inputs are the rows' item counts (via isSelectable — an item row
        // with nothing in it renders nothing, so it must contribute nothing to the geometry
        // either, or every row below it lands at the wrong Y). A selection move reads the cached
        // arrays and allocates nothing.
        val geometryState = remember(
            rows, density, layoutDirection, contentPadding, rowSpacing, maxHeight, verticalAnchorOffset
        ) {
            derivedStateOf {
                val topPx = with(density) { contentPadding.calculateTopPadding().toPx() }
                val bottomPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
                val spacingPx = with(density) { rowSpacing.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }

                val count = rows.size
                val headerPx = FloatArray(count)
                val contentPx = FloatArray(count)
                val itemWPx = FloatArray(count)
                val itemSPx = FloatArray(count)
                val startPx = FloatArray(count)
                val endPx = FloatArray(count)
                val heights = FloatArray(count)

                with(density) {
                    rows.forEachIndexed { i, row ->
                        when (row) {
                            is RokuResolvedRow.Items -> if (row.isSelectable) {
                                headerPx[i] = row.headerHeight.toPx()
                                contentPx[i] = row.contentHeight.toPx()
                                itemWPx[i] = row.config.itemWidth.toPx()
                                itemSPx[i] = row.config.itemSpacing.toPx()
                                startPx[i] = row.config.contentPadding
                                    .calculateLeftPadding(layoutDirection).toPx()
                                endPx[i] = row.config.contentPadding
                                    .calculateRightPadding(layoutDirection).toPx()
                            }

                            is RokuResolvedRow.Custom -> {
                                headerPx[i] = row.headerHeight.toPx()
                                contentPx[i] = row.contentHeight.toPx()
                            }
                        }
                        heights[i] = headerPx[i] + contentPx[i]
                    }
                }

                val cumOffset = FloatArray(count)
                for (i in 1 until count) {
                    cumOffset[i] = cumOffset[i - 1] + heights[i - 1] + spacingPx
                }

                val totalContent = topPx + heights.sum() + max(0, count - 1) * spacingPx + bottomPx
                ColumnGeometry(
                    rowCumOffsetPx = cumOffset,
                    rowHeightsPx = heights,
                    headerPx = headerPx,
                    contentHeightPx = contentPx,
                    itemWidthPx = itemWPx,
                    itemSpacingPx = itemSPx,
                    startPadPx = startPx,
                    endPadPx = endPx,
                    maxVerticalScrollPx = (totalContent - viewportHeightPx).coerceAtLeast(0f),
                    topPaddingPx = topPx,
                    viewportHeightPx = viewportHeightPx,
                    rowSpacingPx = spacingPx,
                    anchorOffsetPx = with(density) { verticalAnchorOffset.toPx() }.coerceAtLeast(0f)
                )
            }
        }
        val geometry by geometryState

        // Spatial row entry: on a vertical move, the entered floating row selects the card under
        // the highlight. Resolved from the derived geometry at the moment of the move, and the
        // card is always inside that row's current window, so the row never scrolls sideways.
        val rowEntry = remember(rows, geometryState, viewportWidthPx, config.rowEntry) {
            if (config.rowEntry == RokuRowEntry.Spatial) {
                SpatialRowEntry(rows, geometryState, viewportWidthPx)::enter
            } else {
                null
            }
        }
        if (state.rowEntry !== rowEntry) state.rowEntry = rowEntry

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
                topPaddingPx = geometry.topPaddingPx,
                anchorOffsetPx = geometry.anchorOffsetPx
            )
            if (contained != anchor) state.windowAnchorRow = contained
            contained
        } else {
            selectedRowIndex
        }

        // The target row parks `anchorOffsetPx` below the top padding, which is the same as
        // scrolling that much less. Clamped at zero, so the rows the offset cannot lift clear of
        // the top — the first of them, a hero — stay exactly where an unscrolled column has them.
        val desiredVerticalScrollPx = anchoredScrollPx(
            geometry.rowCumOffsetPx.getOrElse(scrollTargetRow) { 0f }, geometry.anchorOffsetPx
        )
        val verticalScrollOverflowPx =
            (desiredVerticalScrollPx - geometry.maxVerticalScrollPx).coerceAtLeast(0f)

        // How far below the window's top the selected row sits: the anchor offset in Static (or
        // less, near the top), plus the rows the highlight has walked in Floating.
        val windowOffsetPx =
            geometry.rowCumOffsetPx.getOrElse(selectedRowIndex) { 0f } - desiredVerticalScrollPx

        // Keyed on the geometry as well as the scroll target: scrolling to the last row of a list
        // that is still loading gets clamped, and once later rows arrive the column would
        // otherwise sit at that clamped offset while the highlight maths assumed the unclamped one.
        // A new key cancels the in-flight animation; the animator carries its velocity into the
        // next one so successive row moves read as one scroll, not a restart per row.
        val scrollAnimator = remember(lazyColumnState) { RokuScrollAnimator() }
        // Snapped, not animated, until the column has a measured viewport to resolve its scroll
        // target against. Everything before that is arrival — a restored screen must appear at its
        // rows, not scroll down to them — and the target is provisional while the geometry is
        // still degenerate.
        val landing = remember(lazyColumnState) { ColumnLanding() }
        LaunchedEffect(scrollTargetRow, geometry) {
            val cumOffsets = geometry.rowCumOffsetPx
            if (!landing.done) {
                if (geometry.viewportHeightPx > 0f) landing.done = true
                scrollAnimator.reset()
                lazyColumnState.scrollToOffset(scrollTargetRow, desiredVerticalScrollPx, cumOffsets)
                return@LaunchedEffect
            }
            if (state.keyRepeat.consecutivePresses > config.keyRepeatAccelAfter) {
                scrollAnimator.reset()
                lazyColumnState.scrollToOffset(scrollTargetRow, desiredVerticalScrollPx, cumOffsets)
            } else {
                val currentPx = cumOffsets.getOrElse(lazyColumnState.firstVisibleItemIndex) { 0f } +
                    lazyColumnState.firstVisibleItemScrollOffset
                val targetPx = lazyColumnState.targetOffsetPx(
                    index = scrollTargetRow,
                    currentPx = currentPx,
                    estimatedPx = cumOffsets.getOrElse(scrollTargetRow) { 0f },
                    maxScrollPx = geometry.maxVerticalScrollPx,
                    leadingPx = geometry.anchorOffsetPx
                )
                scrollAnimator.animateTo(
                    lazyColumnState, currentPx, targetPx, geometry.viewportHeightPx,
                    spec = config.verticalAnimationSpec ?: DefaultScrollSpec
                ) {
                    val row = rowAtOrBefore(scrollTargetRow, targetPx, cumOffsets)
                    lazyColumnState.animateScrollToItem(row, offsetInto(row, targetPx, cumOffsets))
                }
            }
        }

        // ── Highlight position — pure array reads from the derived geometry ──
        val targetHighlightY = geometry.topPaddingPx + windowOffsetPx +
            verticalScrollOverflowPx + geometry.headerPx[selectedRowIndex]
        val targetHighlightX = if (activeRow is RokuResolvedRow.Items) {
            computeHighlightOffsetPx(
                activeRow.config.state,
                geometry.itemWidthPx[selectedRowIndex],
                geometry.itemSpacingPx[selectedRowIndex],
                geometry.startPadPx[selectedRowIndex],
                geometry.endPadPx[selectedRowIndex],
                viewportWidthPx
            )
        } else {
            0f
        }
        val targetHighlightWidth = if (activeRow is RokuResolvedRow.Items) {
            geometry.itemWidthPx[selectedRowIndex]
        } else {
            viewportWidthPx
        }

        // ── Animate highlight: position and size on one curve ──
        //
        // The width and height ran on their own 100ms tween while Y travelled on `verticalSpec`
        // (a ~150ms spring for most consumers), so between two rows of different card shapes the
        // box had finished changing shape while it was still a third of the way down. A consumer
        // drawing its ring to the box saw the ring pop to the destination's shape at the top of
        // the travel and then slide — the glitch every feed with mixed rails showed on vertical
        // moves and never on horizontal ones. Size changes only ever come from a row change, so
        // the vertical spec is the right clock for it and the two now finish together.
        //
        // 🚨 Keyed on whether the highlight is drawn at all. A row that hides it — a hero drawing
        // its own treatment, through `showHighlight = false` — still moves these targets, so while
        // nothing is on screen they sit on that row's full-width, full-height geometry. Letting
        // them run when the next row brings the highlight back is a viewport-wide box sweeping
        // down the screen and shrinking onto a card: the glitch every feed with a hero showed on
        // the first press down, and no feed without one did. Re-keying restarts them at the row
        // they appear on, so the highlight is *placed* when it appears and animates only between
        // rows that show it.
        val highlightShown = activeRow.showHighlight && activeRow.isSelectable
        val spec = config.highlightAnimationSpec
        val verticalSpec = config.verticalAnimationSpec ?: spec
        val highlight = key(highlightShown) {
            HighlightFrame(
                x = animateFloatAsState(targetHighlightX, spec, label = "hl_x"),
                y = animateFloatAsState(targetHighlightY, verticalSpec, label = "hl_y"),
                width = animateFloatAsState(targetHighlightWidth, verticalSpec, label = "hl_w"),
                height = animateFloatAsState(
                    geometry.contentHeightPx[selectedRowIndex], verticalSpec, label = "hl_h"
                ),
            )
        }

        // Touchpad: bound while the column holds focus; horizontal swipes go to the active rail (or
        // to a custom row's onKeyEvent), vertical ones step rows, and the focused card and the
        // highlight lean toward pending travel. Nothing here exists without a touchpad.
        val touchLean = rememberRokuTouchLean(touchpad, state.hasFocus)
        val leanStyle = rememberRokuTouchLeanStyle(touchpad)
        if (touchpad != null) {
            val target = remember(rows, state, config, geometryState, viewportWidthPx, onItemSelected) {
                ColumnTouchTarget(rows, state, config, geometryState, viewportWidthPx, onItemSelected)
            }
            BindRokuTouchpad(touchpad, target, state.hasFocus)
        }
        val focusedItemModifier = remember(touchLean, leanStyle) {
            if (touchLean != null && leanStyle != null) Modifier.rokuTouchLean(touchLean, leanStyle) else Modifier
        }

        // The row content is remembered so LazyColumn receives the same lambda instance on every
        // selection recomposition — `rows` is a List (unstable), so without this the compiler
        // recreates the lambda per pass and every visible row (and, through the replaced inner
        // lambdas, every visible card wrapper) recomposes on every key press. Selection is read
        // back inside each item's own scope via derivedStateOf, so a move recomposes exactly the
        // rows and items whose focus actually flipped.
        val rowItemContent: (@Composable LazyItemScope.(Int) -> Unit) =
            remember(rows, state, rowHeader, focusedItemModifier) {
                { rowIndex ->
                    val row = rows[rowIndex]
                    // Keyed on rowIndex: a keyed row that shifts position keeps its composition,
                    // and a keyless remember would keep comparing against the old position.
                    val isRowFocused by remember(rowIndex) {
                        derivedStateOf { state.hasFocus && rowIndex == state.selectedRowIndex }
                    }

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
                                itemCount = row.itemCount,
                                contentPadding = row.config.contentPadding,
                                itemWidth = row.config.itemWidth,
                                itemSpacing = row.config.itemSpacing,
                                rowIndex = rowIndex,
                                itemKey = row.itemKey,
                                itemContentDescription = row.config.itemContentDescription,
                                rowFocused = { state.hasFocus && rowIndex == state.selectedRowIndex },
                                focusedItemModifier = focusedItemModifier,
                                itemContent = row.itemContent
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
                    key = rowKeys,
                    itemContent = rowItemContent
                )
            }

            // Single global highlight overlay
            if (highlightShown) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = highlight.x.value
                            translationY = highlight.y.value
                            if (touchLean != null && leanStyle != null) {
                                applyTouchLean(touchLean.value, leanStyle, leanStyle.highlightParallax)
                            }
                        }
                        .layout { measurable, _ ->
                            val w = highlight.width.value.roundToInt().coerceAtLeast(0)
                            val h = highlight.height.value.roundToInt().coerceAtLeast(0)
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

/** Whether the column has landed on a scroll target resolved against a real viewport. */
private class ColumnLanding {
    var done = false
}

/**
 * The highlight's animated geometry, held as `State` so the overlay reads it at draw and layout
 * time rather than in composition — a move costs a redraw, not a recomposition of the column.
 */
private class HighlightFrame(
    val x: State<Float>,
    val y: State<Float>,
    val width: State<Float>,
    val height: State<Float>
)

/**
 * A column of rails. Horizontal moves go through the active rail with the same edge policy and
 * callbacks as a key press; on a custom row each step is one [RokuNavKey.Left] / [RokuNavKey.Right]
 * to its `onKeyEvent`, since that is the only handle such a row has. Step sizes are read from the
 * derived geometry at the time of the report, never captured.
 */
private class ColumnTouchTarget(
    private val rows: List<RokuResolvedRow>,
    private val state: RokuColumnState,
    private val config: RokuFocusConfig,
    private val geometry: State<ColumnGeometry>,
    private val viewportWidthPx: Float,
    private val onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)?
) : RokuTouchTarget {

    override fun stepPx(orientation: Orientation): Float {
        val g = geometry.value
        val row = state.selectedRowIndex
        return if (orientation == Orientation.Horizontal) {
            val pitch = g.itemWidthPx.getOrElse(row) { 0f } + g.itemSpacingPx.getOrElse(row) { 0f }
            if (pitch > 0f) pitch else viewportWidthPx / CustomRowStepsPerViewport
        } else {
            g.rowHeightsPx.getOrElse(row) { 0f } + g.rowSpacingPx
        }
    }

    override fun moveItems(steps: Int): Boolean {
        val active = rows.getOrNull(state.selectedRowIndex)
        if (active is RokuResolvedRow.Custom) {
            val onKey = active.onKeyEvent ?: return false
            val key = if (steps > 0) RokuNavKey.Right else RokuNavKey.Left
            var moved = false
            repeat(abs(steps)) { if (onKey(key)) moved = true }
            return moved
        }
        var moved = false
        rokuMoveItemsBy(
            state = state,
            config = config,
            steps = steps,
            onSelected = { rowIndex, itemIndex ->
                moved = true
                onItemSelected?.invoke(rowIndex, itemIndex)
            }
        )
        return moved
    }

    override fun moveRows(steps: Int): Boolean {
        var moved = false
        rokuMoveRowsBy(
            state = state,
            config = config,
            steps = steps,
            onSelected = { rowIndex ->
                moved = true
                onItemSelected?.invoke(rowIndex, rows.selectedItemIndexIn(rowIndex))
            }
        )
        return moved
    }
}

/** A custom row has no item pitch; a step is taken to be this fraction of the viewport. */
private const val CustomRowStepsPerViewport = 5f

/**
 * [RokuRowEntry.Spatial] for a column: the entered row's card under the centre of the highlight
 * being left. Leaving or entering a custom row, or entering a Static row, leaves the row's own
 * selection alone — a Static row already has its remembered card under the slot.
 */
private class SpatialRowEntry(
    private val rows: List<RokuResolvedRow>,
    private val geometry: State<ColumnGeometry>,
    private val viewportWidthPx: Float
) {
    fun enter(fromRow: Int, toRow: Int) {
        val from = rows.getOrNull(fromRow) as? RokuResolvedRow.Items ?: return
        val to = rows.getOrNull(toRow) as? RokuResolvedRow.Items ?: return
        val toState = to.config.state
        if (toState.focusMode != RokuFocusMode.Floating || toState.itemCount == 0) return
        val g = geometry.value
        if (fromRow > g.itemWidthPx.lastIndex || toRow > g.itemWidthPx.lastIndex) return

        val centreX = computeHighlightOffsetPx(
            from.config.state,
            g.itemWidthPx[fromRow], g.itemSpacingPx[fromRow],
            g.startPadPx[fromRow], g.endPadPx[fromRow],
            viewportWidthPx
        ) + g.itemWidthPx[fromRow] / 2f

        val windowLeft = windowLeftEdgePx(
            toState,
            g.itemWidthPx[toRow], g.itemSpacingPx[toRow],
            g.startPadPx[toRow], g.endPadPx[toRow],
            viewportWidthPx
        )
        val slot = slotUnder(
            centreX = centreX,
            windowLeftPx = windowLeft,
            itemWidthPx = g.itemWidthPx[toRow],
            stepPx = g.itemWidthPx[toRow] + g.itemSpacingPx[toRow],
            visibleCount = toState.visibleCount
        )
        val index = (toState.windowStart + slot).coerceIn(0, toState.itemCount - 1)
        if (index != toState.selectedIndex) toState.scrollTo(index)
    }
}

/**
 * Where the vertical floating window must start so the selected row is fully visible, moved
 * minimally from [anchorRow]. The window is pixel-based, not row-based: rows have heterogeneous
 * heights and an empty row contributes zero, so "visible" means the selected row's
 * `[top, top + height]` span fits between the window row's top and the viewport bottom.
 *
 * With an [anchorOffsetPx] the window row sits that far below the top padding (or less, where the
 * column cannot scroll back far enough), so the span it can hold below it is shorter by the same
 * amount. A row above the window row is outside the window even while part of it shows in the
 * offset band: selecting it scrolls it down onto the anchor.
 */
internal fun containVerticalWindow(
    anchorRow: Int,
    selectedRow: Int,
    rowCumOffsetPx: FloatArray,
    rowHeightsPx: FloatArray,
    viewportHeightPx: Float,
    topPaddingPx: Float,
    anchorOffsetPx: Float = 0f
): Int {
    if (rowCumOffsetPx.isEmpty()) return 0
    val anchor = anchorRow.coerceIn(0, rowCumOffsetPx.lastIndex)
    val selected = selectedRow.coerceIn(0, rowCumOffsetPx.lastIndex)
    val selectedTop = rowCumOffsetPx[selected]
    val selectedBottom = selectedTop + rowHeightsPx[selected]
    val windowHeightPx = viewportHeightPx - topPaddingPx
    val requiredScrollPx = selectedBottom - windowHeightPx
    return when {
        selectedTop < rowCumOffsetPx[anchor] -> selected
        anchoredScrollPx(rowCumOffsetPx[anchor], anchorOffsetPx) < requiredScrollPx -> {
            // Smallest forward shift that fits the selected row's bottom edge. A row taller than
            // the window degenerates to the selected row itself, parked on the anchor like Static.
            var row = anchor
            while (row < selected &&
                anchoredScrollPx(rowCumOffsetPx[row], anchorOffsetPx) < requiredScrollPx
            ) row++
            row
        }

        else -> anchor
    }
}

/** The scroll that parks a row whose top is [rowTopPx] [anchorOffsetPx] below the top padding. */
internal fun anchoredScrollPx(rowTopPx: Float, anchorOffsetPx: Float): Float =
    (rowTopPx - anchorOffsetPx).coerceAtLeast(0f)

/** The height the column's geometry gives a row: nothing for one it currently skips. */
internal val RokuResolvedRow.laidOutHeight: Dp
    get() = when (this) {
        is RokuResolvedRow.Items -> if (isSelectable) headerHeight + contentHeight else 0.dp
        is RokuResolvedRow.Custom -> headerHeight + contentHeight
    }

/** A lazy list position: an item and how far into it the viewport starts. */
internal class ColumnScrollPosition(val index: Int, val offset: Int)

/**
 * The lazy list position that parks [row] [anchorOffsetPx] below the top padding, walking back
 * through the rows above it. [pitchPx] is a row's height plus the row spacing. A column too short
 * above [row] to give the whole offset starts at its top instead, as the scroll target does.
 */
internal fun anchoredScrollPosition(
    row: Int,
    anchorOffsetPx: Float,
    pitchPx: (row: Int) -> Float
): ColumnScrollPosition {
    var remaining = anchorOffsetPx
    var current = row
    while (remaining > 0f && current > 0) {
        val pitch = pitchPx(current - 1)
        if (pitch > remaining) return ColumnScrollPosition(current - 1, (pitch - remaining).roundToInt())
        remaining -= pitch
        current--
    }
    return ColumnScrollPosition(current, 0)
}

/**
 * The row whose top is at or above [absolutePx], searched back from [fromRow] — the scroll target,
 * which [absolutePx] is at most an anchor offset above. The half-pixel tolerance keeps a measured
 * offset that lands a hair above the target's own top from resolving to the row before it.
 */
internal fun rowAtOrBefore(fromRow: Int, absolutePx: Float, rowCumOffsetPx: FloatArray): Int {
    if (rowCumOffsetPx.isEmpty()) return 0
    var row = fromRow.coerceIn(0, rowCumOffsetPx.lastIndex)
    while (row > 0 && rowCumOffsetPx[row] > absolutePx + HalfPixel) row--
    return row
}

/** How far into [row] the viewport starts when the column is scrolled to [absolutePx]. */
internal fun offsetInto(row: Int, absolutePx: Float, rowCumOffsetPx: FloatArray): Int =
    (absolutePx - rowCumOffsetPx.getOrElse(row) { 0f }).roundToInt().coerceAtLeast(0)

private const val HalfPixel = 0.5f

/** Snaps the column to an absolute offset, expressed as the row it falls in and the rest. */
private suspend fun LazyListState.scrollToOffset(
    targetRow: Int,
    absolutePx: Float,
    rowCumOffsetPx: FloatArray
) {
    val row = rowAtOrBefore(targetRow, absolutePx, rowCumOffsetPx)
    scrollToItem(row, offsetInto(row, absolutePx, rowCumOffsetPx))
}

