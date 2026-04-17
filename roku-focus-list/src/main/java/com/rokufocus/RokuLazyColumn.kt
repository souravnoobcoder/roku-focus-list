package com.rokufocus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * OTT-style vertical + horizontal navigation component with a single animated focus highlight.
 *
 * This is a single focusable composable that manages both axes:
 * - D-pad UP/DOWN navigates between rows (vertical scroll, content slides under fixed focus)
 * - D-pad LEFT/RIGHT navigates within the focused row (horizontal scroll)
 * - Enter/Select triggers [onItemClicked]
 *
 * The focus highlight smoothly animates its position, width, and height when moving
 * between rows that have different item dimensions (e.g., landscape → portrait cards).
 */
@Composable
fun RokuLazyColumn(
    rows: List<RokuColumnRowConfig>,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = RokuFocusConfig(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    initialRowIndex: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    rowHeader: (@Composable (rowIndex: Int, isRowFocused: Boolean) -> Unit)? = null,
    itemContent: @Composable (rowIndex: Int, itemIndex: Int, isFocused: Boolean) -> Unit
) {
    if (rows.isEmpty()) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val navState = remember { RokuColumnNavState(initialRowIndex, rows.size) }
    var hasFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val lazyColumnState = rememberLazyListState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.hasFocus || it.isFocused }
            .focusable()
            .rokuColumnKeyHandler(rows, navState, config, onItemSelected, onItemClicked)
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val selectedRowIndex = navState.selectedRowIndex

        // ── Cached geometry: only recomputed when row config or viewport changes ──
        val (rowCumOffset, maxVerticalScrollPx, topPaddingPx) = remember(
            rows, density, contentPadding, rowSpacing, maxWidth, maxHeight, layoutDirection
        ) {
            rows.forEach { rowConfig ->
                val startPad = rowConfig.contentPadding.calculateLeftPadding(layoutDirection)
                val endPad = rowConfig.contentPadding.calculateRightPadding(layoutDirection)
                val avail = maxWidth - startPad - endPad
                rowConfig.state.visibleCount =
                    ((avail + rowConfig.itemSpacing) / (rowConfig.itemWidth + rowConfig.itemSpacing)).toInt()
                        .coerceAtLeast(1)
            }

            val topPx = with(density) { contentPadding.calculateTopPadding().toPx() }
            val bottomPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
            val spacingPx = with(density) { rowSpacing.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }

            val heights = FloatArray(rows.size) { i ->
                with(density) { (rows[i].headerHeight + rows[i].itemHeight).toPx() }
            }
            val cumOffset = FloatArray(rows.size)
            for (i in 1 until rows.size) {
                cumOffset[i] = cumOffset[i - 1] + heights[i - 1] + spacingPx
            }

            val totalContent = topPx + heights.sum() + max(0, rows.size - 1) * spacingPx + bottomPx
            val maxScroll = (totalContent - viewportHeightPx).coerceAtLeast(0f)

            Triple(cumOffset, maxScroll, topPx)
        }

        // ── Vertical scroll + overflow correction ──
        val desiredVerticalScrollPx = rowCumOffset.getOrElse(selectedRowIndex) { 0f }
        val verticalScrollOverflowPx = (desiredVerticalScrollPx - maxVerticalScrollPx).coerceAtLeast(0f)

        LaunchedEffect(selectedRowIndex) {
            if (navState.consecutivePresses > config.keyRepeatAccelAfter) {
                lazyColumnState.scrollToItem(selectedRowIndex)
            } else {
                lazyColumnState.animateScrollToItem(selectedRowIndex)
            }
        }

        // ── Highlight position ──
        val activeRow = rows[selectedRowIndex]
        val activeHeaderPx = with(density) { activeRow.headerHeight.toPx() }
        val targetHighlightY = topPaddingPx + verticalScrollOverflowPx + activeHeaderPx

        val activeState = activeRow.state
        val activeStartPadPx = with(density) { activeRow.contentPadding.calculateLeftPadding(layoutDirection).toPx() }
        val activeEndPadPx = with(density) { activeRow.contentPadding.calculateRightPadding(layoutDirection).toPx() }
        val activeItemWidthPx = with(density) { activeRow.itemWidth.toPx() }
        val activeItemSpacingPx = with(density) { activeRow.itemSpacing.toPx() }
        val activeItemHeightPx = with(density) { activeRow.itemHeight.toPx() }

        val targetHighlightX = computeHighlightOffsetPx(
            activeState, activeItemWidthPx, activeItemSpacingPx,
            activeStartPadPx, activeEndPadPx, viewportWidthPx
        )

        // ── Animate highlight: full spec for position, fast tween for size ──
        val spec = config.highlightAnimationSpec
        val sizeSpec = tween<Float>(durationMillis = 100, easing = FastOutSlowInEasing)

        val animatedX by animateFloatAsState(targetHighlightX, spec, label = "hl_x")
        val animatedY by animateFloatAsState(targetHighlightY, spec, label = "hl_y")
        val animatedWidth by animateFloatAsState(activeItemWidthPx, sizeSpec, label = "hl_w")
        val animatedHeight by animateFloatAsState(activeItemHeightPx, sizeSpec, label = "hl_h")

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
                    key = { it }
                ) { rowIndex ->
                    val rowConfig = rows[rowIndex]
                    val isRowFocused = hasFocus && rowIndex == selectedRowIndex

                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (rowHeader != null) {
                            rowHeader(rowIndex, isRowFocused)
                        }
                        RokuRowContent(
                            state = rowConfig.state,
                            contentPadding = rowConfig.contentPadding,
                            itemWidth = rowConfig.itemWidth,
                            itemSpacing = rowConfig.itemSpacing,
                            itemContent = { itemIndex, isFocused ->
                                itemContent(rowIndex, itemIndex, isFocused && isRowFocused)
                            }
                        )
                    }
                }
            }

            // Single global highlight overlay
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
                focusHighlight(hasFocus)
            }
        }
    }
}
