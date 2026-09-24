package com.rokufocus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A wall of equal-size cells, [RokuGridState.columns] wide, scrolling vertically, with one
 * highlight overlay. Cell width comes from the viewport: what is left after the content padding
 * and the gaps, split evenly. Rows are [itemHeight] tall and always fully on screen horizontally,
 * so only vertical moves ever scroll.
 */
@Composable
internal fun RokuFocusGridImpl(
    state: RokuGridState,
    itemHeight: Dp,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 12.dp,
    rowSpacing: Dp = 12.dp,
    focusHighlight: @Composable RokuHighlightScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    itemKey: ((index: Int) -> Any)? = null,
    itemContentDescription: ((index: Int) -> String?)? = null,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
) {
    // Captured with itemKey and itemContent and laid out from here, never read inside the grid's
    // content — see RokuRowContent's itemCount.
    val itemCount = state.itemCount
    if (itemCount == 0) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val hapticFeedback = LocalHapticFeedback.current
    val touchpad = LocalRokuTouchpad.current

    DisposableEffect(state) {
        onDispose { state.hasFocus = false }
    }

    val onBoundaryHit: (() -> Unit)? = if (config.hapticFeedback) {
        { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
    } else null

    val selectedDescription = itemContentDescription?.invoke(state.selectedIndex)
    val columns = state.columns

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
                collectionInfo = CollectionInfo(rowCount = state.rowCount, columnCount = columns)
                if (selectedDescription != null) {
                    contentDescription = selectedDescription
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .rokuTouchpadKeyGuard(touchpad)
            .rokuGridKeyHandler(state, config, onItemSelected, onItemClicked, onBoundaryHit)
    ) {
        val startPadPx = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
        val endPadPx = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx() }
        val topPadPx = with(density) { contentPadding.calculateTopPadding().toPx() }
        val bottomPadPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
        val itemSpacingPx = with(density) { itemSpacing.toPx() }
        val rowSpacingPx = with(density) { rowSpacing.toPx() }
        val itemHeightPx = with(density) { itemHeight.toPx() }
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        val cellWidthPx =
            ((viewportWidthPx - startPadPx - endPadPx - (columns - 1) * itemSpacingPx) / columns).coerceAtLeast(0f)
        val rowPitchPx = itemHeightPx + rowSpacingPx

        // How many rows fit, from the real viewport.
        val availableHeight =
            maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding()
        val rowPitch = itemHeight + rowSpacing
        val visibleRows = if (rowPitch > 0.dp) {
            ((availableHeight + rowSpacing) / rowPitch).toInt().coerceAtLeast(1)
        } else 1
        // Written on the first pass even when it matches what the state holds: one row is both
        // the constructor's placeholder and a real measurement, and the state cannot contain its
        // window until it knows which it has. See RokuFocusListState.viewportMeasured.
        if (!state.viewportMeasured || state.visibleRows != visibleRows) {
            state.visibleRows = visibleRows
        }

        val gridState = rememberLazyGridState()
        val scrollAnimator = remember(gridState) { RokuScrollAnimator() }
        val rowCount = state.rowCount
        val totalContentPx = topPadPx + bottomPadPx + rowCount * itemHeightPx + max(0, rowCount - 1) * rowSpacingPx
        val maxScrollPx = (totalContentPx - viewportHeightPx).coerceAtLeast(0f)

        // Keyed on rowCount as well: a scroll clamped at the end of a still-loading grid must be
        // re-run once more rows arrive, or the content sits at the clamped offset while the
        // highlight maths assumes the unclamped one.
        LaunchedEffect(state, gridState, columns, rowPitchPx, rowCount) {
            snapshotFlow { state.windowStartRow }.collectLatest { startRow ->
                val firstIndex = startRow * columns
                val currentPx = (gridState.firstVisibleItemIndex / columns) * rowPitchPx +
                    gridState.firstVisibleItemScrollOffset
                val visible = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstIndex }
                val targetPx = (if (visible != null) currentPx + visible.offset.y else startRow * rowPitchPx)
                    .coerceIn(0f, maxScrollPx)
                scrollAnimator.animateTo(
                    gridState, currentPx, targetPx, viewportHeightPx,
                    spec = config.verticalAnimationSpec ?: DefaultScrollSpec
                ) {
                    gridState.animateScrollToItem(firstIndex)
                }
            }
        }

        // Highlight: the column walks X; the row-in-window walks Y, corrected for the clamp at
        // the bottom of the grid exactly like the horizontal overflow correction.
        val overflowPx = (state.windowStartRow * rowPitchPx - maxScrollPx).coerceAtLeast(0f)
        val targetX = startPadPx + state.selectedColumn * (cellWidthPx + itemSpacingPx)
        val targetY = topPadPx + overflowPx + state.highlightRowSlot * rowPitchPx
        val animatedX by animateFloatAsState(targetX, config.highlightAnimationSpec, label = "roku_grid_hl_x")
        val animatedY by animateFloatAsState(
            targetY, config.verticalAnimationSpec ?: config.highlightAnimationSpec, label = "roku_grid_hl_y"
        )

        // Touchpad: along the row for horizontal travel, whole rows for vertical, with the focused
        // cell and the highlight leaning toward pending travel. Nothing here exists without one.
        val touchLean = rememberRokuTouchLean(touchpad, state.hasFocus)
        val leanStyle = rememberRokuTouchLeanStyle(touchpad)
        if (touchpad != null) {
            val cellPitchPx = cellWidthPx + itemSpacingPx
            val target = remember(state, config, cellPitchPx, rowPitchPx, onItemSelected, onBoundaryHit) {
                GridTouchTarget(state, config, cellPitchPx, rowPitchPx, onItemSelected, onBoundaryHit)
            }
            BindRokuTouchpad(touchpad, target, state.hasFocus)
        }
        val focusedItemModifier = remember(touchLean, leanStyle) {
            if (touchLean != null && leanStyle != null) Modifier.rokuTouchLean(touchLean, leanStyle) else Modifier
        }

        // Remembered so the grid receives the same content lambda on every selection
        // recomposition; selection is read back per cell through derivedStateOf.
        val cells: LazyGridScope.() -> Unit = remember(
            state, itemCount, columns, itemHeight, itemKey, itemContentDescription, itemContent, focusedItemModifier
        ) {
            {
                items(count = itemCount, key = itemKey ?: { it }) { index ->
                    // Derived per cell for the same reason as RokuRowContent: a move recomposes
                    // the two cells whose value flipped, not every visible cell.
                    val isSelected by remember(index) { derivedStateOf { index == state.selectedIndex } }
                    val showAsFocused by remember(index) {
                        derivedStateOf { index == state.selectedIndex && state.hasFocus }
                    }
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .zIndex(if (isSelected) 1f else 0f)
                            .then(if (showAsFocused) focusedItemModifier else Modifier)
                            .semantics {
                                collectionItemInfo = CollectionItemInfo(
                                    rowIndex = index / columns,
                                    rowSpan = 1,
                                    columnIndex = index % columns,
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

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
            userScrollEnabled = false,
            content = cells
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = animatedX
                    translationY = animatedY
                    if (touchLean != null && leanStyle != null) {
                        applyTouchLean(touchLean.value, leanStyle, leanStyle.highlightParallax)
                    }
                }
                .layout { measurable, _ ->
                    val w = cellWidthPx.roundToInt().coerceAtLeast(0)
                    val h = itemHeightPx.roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
        ) {
            RokuHighlightScopeImpl(
                boxScope = this,
                rowIndex = state.selectedRow,
                itemIndex = state.selectedIndex
            ).focusHighlight(state.hasFocus)
        }
    }
}

/** A grid: along the row for horizontal travel, whole rows for vertical, both column-aware. */
private class GridTouchTarget(
    private val state: RokuGridState,
    private val config: RokuFocusConfig,
    private val cellPitchPx: Float,
    private val rowPitchPx: Float,
    private val onItemSelected: ((index: Int) -> Unit)?,
    private val onBoundaryHit: (() -> Unit)?
) : RokuTouchTarget {

    override fun stepPx(orientation: Orientation): Float =
        if (orientation == Orientation.Horizontal) cellPitchPx else rowPitchPx

    override fun moveItems(steps: Int): Boolean {
        var moved = false
        rokuMoveColumnsBy(state, config, steps, onSelected = { index ->
            moved = true
            onItemSelected?.invoke(index)
        }, onBoundaryHit = onBoundaryHit)
        return moved
    }

    override fun moveRows(steps: Int): Boolean {
        var moved = false
        rokuMoveRowsBy(state, config, steps, onSelected = { index ->
            moved = true
            onItemSelected?.invoke(index)
        }, onBoundaryHit = onBoundaryHit)
        return moved
    }
}
