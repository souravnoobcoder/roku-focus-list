package com.rokufocus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Configuration for a single row inside [RokuLazyColumn].
 *
 * @param headerHeight The explicit height of the row header rendered by [RokuLazyColumn.rowHeader].
 *   Must match the actual rendered header height for correct highlight Y positioning.
 *   Set to 0.dp if no header is used.
 */
data class RokuColumnRowConfig(
    val state: RokuFocusListState,
    val itemWidth: Dp,
    val itemHeight: Dp,
    val itemSpacing: Dp = 14.dp,
    val contentPadding: PaddingValues = PaddingValues(0.dp),
    val headerHeight: Dp = 0.dp
)

/**
 * OTT-style vertical + horizontal navigation component with a single animated focus highlight.
 *
 * This is a single focusable composable that manages both axes:
 * - D-pad UP/DOWN navigates between rows (vertical scroll, content slides under fixed focus)
 * - D-pad LEFT/RIGHT navigates within the focused row (horizontal scroll)
 * - Enter/Select triggers [onItemClicked]
 *
 * The focus highlight smoothly animates its position, width, and height when moving
 * between rows that have different item dimensions (e.g., landscape → round → portrait cards).
 *
 * Vertical model (focusSlot = 0): the selected row is always scrolled to the top of the viewport.
 * At the bottom edge, when scroll clamps, the highlight walks down to follow the actual row position.
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

    var selectedRowIndex by remember { mutableIntStateOf(initialRowIndex.coerceIn(0, rows.size - 1)) }
    var hasFocus by remember { mutableStateOf(false) }
    var lastKeyTime by remember { mutableLongStateOf(0L) }
    val focusRequester = remember { FocusRequester() }
    val lazyColumnState = rememberLazyListState()

    // BoxWithConstraints gives us the actual viewport dimensions (not full screen)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                hasFocus = focusState.hasFocus || focusState.isFocused
            }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val now = System.currentTimeMillis()
                val activeRow = rows.getOrNull(selectedRowIndex) ?: return@onPreviewKeyEvent false
                val activeState = activeRow.state

                when (keyEvent.key) {
                    // ── Vertical: move between rows ──
                    Key.DirectionUp -> {
                        if (now - lastKeyTime < config.keyRepeatDelayMs) return@onPreviewKeyEvent true
                        if (selectedRowIndex > 0) {
                            selectedRowIndex--
                            lastKeyTime = now
                            onItemSelected?.invoke(selectedRowIndex, rows[selectedRowIndex].state.selectedIndex)
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        if (now - lastKeyTime < config.keyRepeatDelayMs) return@onPreviewKeyEvent true
                        if (selectedRowIndex < rows.size - 1) {
                            selectedRowIndex++
                            lastKeyTime = now
                            onItemSelected?.invoke(selectedRowIndex, rows[selectedRowIndex].state.selectedIndex)
                        }
                        true
                    }

                    // ── Horizontal: delegate to active row's state ──
                    Key.DirectionRight -> {
                        if (now - lastKeyTime < config.keyRepeatDelayMs) return@onPreviewKeyEvent true
                        val moved = if (config.wrapAround && !activeState.canScrollForward) {
                            activeState.scrollTo(0); true
                        } else {
                            activeState.moveNext()
                        }
                        if (moved) {
                            lastKeyTime = now
                            onItemSelected?.invoke(selectedRowIndex, activeState.selectedIndex)
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (now - lastKeyTime < config.keyRepeatDelayMs) return@onPreviewKeyEvent true
                        val moved = if (config.wrapAround && !activeState.canScrollBackward) {
                            activeState.scrollTo(activeState.itemCount - 1); true
                        } else {
                            activeState.movePrevious()
                        }
                        if (moved) {
                            lastKeyTime = now
                            onItemSelected?.invoke(selectedRowIndex, activeState.selectedIndex)
                        }
                        true
                    }

                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                        onItemClicked?.invoke(selectedRowIndex, activeState.selectedIndex)
                        true
                    }

                    else -> false
                }
            }
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        // ────────────────────────────────────────────
        // Auto-compute visibleCount for each row
        // ────────────────────────────────────────────
        rows.forEach { rowConfig ->
            val startPad = rowConfig.contentPadding.calculateLeftPadding(layoutDirection)
            val endPad = rowConfig.contentPadding.calculateRightPadding(layoutDirection)
            val avail = maxWidth - startPad - endPad
            rowConfig.state.visibleCount =
                ((avail + rowConfig.itemSpacing) / (rowConfig.itemWidth + rowConfig.itemSpacing)).toInt()
                    .coerceAtLeast(1)
        }

        // ────────────────────────────────────────────
        // Vertical geometry: row heights and offsets
        // ────────────────────────────────────────────
        val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
        val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
        val rowSpacingPx = with(density) { rowSpacing.toPx() }

        // Total height of each row = header + item content
        val rowTotalHeightPx = rows.map { rc ->
            with(density) { (rc.headerHeight + rc.itemHeight).toPx() }
        }

        // Cumulative offset from content start to each row's top edge
        // rowCumOffset[i] = sum of (rowHeight[j] + spacing) for j in 0..<i
        val rowCumOffset = FloatArray(rows.size)
        for (i in 1 until rows.size) {
            rowCumOffset[i] = rowCumOffset[i - 1] + rowTotalHeightPx[i - 1] + rowSpacingPx
        }

        // Total column content height
        val totalColumnContentPx = topPaddingPx +
            rowTotalHeightPx.sum().toFloat() +
            max(0, rows.size - 1) * rowSpacingPx +
            bottomPaddingPx
        val maxVerticalScrollPx = (totalColumnContentPx - viewportHeightPx).coerceAtLeast(0f)

        // ────────────────────────────────────────────
        // Vertical scroll + overflow correction
        // ────────────────────────────────────────────
        // With focusSlot=0, we always scroll the selected row to the top.
        // At the bottom of the column, scroll clamps → overflow shifts highlight down.
        val desiredVerticalScrollPx = rowCumOffset.getOrElse(selectedRowIndex) { 0f }
        val verticalScrollOverflowPx = (desiredVerticalScrollPx - maxVerticalScrollPx).coerceAtLeast(0f)

        LaunchedEffect(selectedRowIndex) {
            lazyColumnState.animateScrollToItem(selectedRowIndex)
        }

        // ────────────────────────────────────────────
        // Highlight Y: topPadding + overflow + header (to skip past header)
        // ────────────────────────────────────────────
        val activeRow = rows[selectedRowIndex]
        val activeHeaderPx = with(density) { activeRow.headerHeight.toPx() }
        val targetHighlightY = topPaddingPx + verticalScrollOverflowPx + activeHeaderPx

        // ────────────────────────────────────────────
        // Highlight X: from active row's horizontal state
        // ────────────────────────────────────────────
        val activeState = activeRow.state
        val activeStartPadPx = with(density) { activeRow.contentPadding.calculateLeftPadding(layoutDirection).toPx() }
        val activeEndPadPx = with(density) { activeRow.contentPadding.calculateRightPadding(layoutDirection).toPx() }
        val activeItemWidthPx = with(density) { activeRow.itemWidth.toPx() }
        val activeItemSpacingPx = with(density) { activeRow.itemSpacing.toPx() }

        val targetHighlightX = computeHighlightOffsetPx(
            activeState, activeItemWidthPx, activeItemSpacingPx,
            activeStartPadPx, activeEndPadPx, viewportWidthPx
        )

        // ────────────────────────────────────────────
        // Animate all 4 highlight dimensions
        // ────────────────────────────────────────────
        val animSpec = tween<Float>(300, easing = FastOutSlowInEasing)
        val dpAnimSpec = tween<Dp>(300, easing = FastOutSlowInEasing)

        val animatedX by animateFloatAsState(targetHighlightX, animSpec, label = "hl_x")
        val animatedY by animateFloatAsState(targetHighlightY, animSpec, label = "hl_y")
        val animatedWidth by animateDpAsState(activeRow.itemWidth, dpAnimSpec, label = "hl_w")
        val animatedHeight by animateDpAsState(activeRow.itemHeight, dpAnimSpec, label = "hl_h")

        // ────────────────────────────────────────────
        // Render
        // ────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: Scrolling content (LazyColumn of row sections)
            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                userScrollEnabled = false
            ) {
                items(rows.size) { rowIndex ->
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

            // Layer 2: Single global highlight overlay
            // Animates position + size smoothly across rows of different dimensions
            if (hasFocus) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = animatedX
                            translationY = animatedY
                        }
                        .width(animatedWidth)
                        .height(animatedHeight)
                ) {
                    focusHighlight(true)
                }
            }
        }
    }
}
