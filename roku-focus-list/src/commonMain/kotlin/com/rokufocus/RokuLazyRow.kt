package com.rokufocus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * @param itemCount The count declared alongside [itemKey] and [itemContent]; see [RokuRowContent].
 */
@Composable
internal fun RokuLazyRowImpl(
    state: RokuFocusListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp,
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
    if (itemCount == 0) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val hapticFeedback = LocalHapticFeedback.current
    val touchpad = LocalRokuTouchpad.current

    // A row that leaves the composition is no longer focused, whatever the last event said.
    DisposableEffect(state) {
        onDispose { state.hasFocus = false }
    }

    val onBoundaryHit: (() -> Unit)? = if (config.hapticFeedback) {
        { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
    } else null

    val selectedDescription = itemContentDescription?.invoke(state.selectedIndex)

    // BoxWithConstraints gives us the actual viewport width (not full screen),
    // critical when sidebars, insets, or split-screen reduce available space.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
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
                collectionInfo = CollectionInfo(rowCount = 1, columnCount = state.itemCount)
                if (selectedDescription != null) {
                    contentDescription = selectedDescription
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .rokuTouchpadKeyGuard(touchpad)
            .rokuKeyHandler(
                state = state,
                config = config,
                orientation = Orientation.Horizontal,
                onSelected = onItemSelected,
                onClicked = onItemClicked,
                onBoundaryHit = onBoundaryHit
            )
    ) {
        // Pixel values for highlight calculation — using actual viewport width
        val startPaddingPx = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
        val endPaddingPx = with(density) { contentPadding.calculateRightPadding(layoutDirection).toPx() }
        val itemWidthPx = with(density) { itemWidth.toPx() }
        val itemSpacingPx = with(density) { itemSpacing.toPx() }
        val viewportWidthPx = with(density) { maxWidth.toPx() }

        // Auto-compute visible count from actual viewport dimensions
        val startPaddingDp = contentPadding.calculateLeftPadding(layoutDirection)
        val endPaddingDp = contentPadding.calculateRightPadding(layoutDirection)
        val availableWidth = maxWidth - startPaddingDp - endPaddingDp
        val denominator = itemWidth + itemSpacing
        val computedVisibleCount = if (denominator > 0.dp) {
            ((availableWidth + itemSpacing) / denominator).toInt().coerceAtLeast(1)
        } else 1
        // First pass always reports — see RokuFocusListState.viewportMeasured.
        if (!state.viewportMeasured || state.visibleCount != computedVisibleCount) {
            state.visibleCount = computedVisibleCount
        }

        // Highlight X position using shared utility (handles scroll clamping at edges)
        val targetHighlightX = computeHighlightOffsetPx(
            state, itemWidthPx, itemSpacingPx, startPaddingPx, endPaddingPx, viewportWidthPx
        )
        val animatedHighlightX by animateFloatAsState(
            targetValue = targetHighlightX,
            animationSpec = config.highlightAnimationSpec,
            label = "roku_row_highlight_x"
        )

        // Touchpad: bound while focused, leaning the focused card and the highlight. All of it
        // resolves to nothing when no touchpad is provided.
        val touchLean = rememberRokuTouchLean(touchpad, state.hasFocus)
        val leanStyle = rememberRokuTouchLeanStyle(touchpad)
        if (touchpad != null) {
            val target = remember(state, config, itemWidthPx, itemSpacingPx, onItemSelected, onBoundaryHit) {
                RowTouchTarget(state, config, itemWidthPx + itemSpacingPx, onItemSelected, onBoundaryHit)
            }
            BindRokuTouchpad(touchpad, target, state.hasFocus)
        }
        val focusedItemModifier = remember(touchLean, leanStyle) {
            if (touchLean != null && leanStyle != null) Modifier.rokuTouchLean(touchLean, leanStyle) else Modifier
        }

        RokuRowContent(
            state = state,
            itemCount = itemCount,
            contentPadding = contentPadding,
            itemWidth = itemWidth,
            itemSpacing = itemSpacing,
            itemKey = itemKey,
            itemContentDescription = itemContentDescription,
            focusedItemModifier = focusedItemModifier,
            itemContent = itemContent
        )

        // Highlight overlay — positioned at the computed X offset
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = animatedHighlightX
                        if (touchLean != null && leanStyle != null) {
                            applyTouchLean(touchLean.value, leanStyle, leanStyle.highlightParallax)
                        }
                    }
                    .width(itemWidth)
                    .fillMaxHeight()
            ) {
                RokuHighlightScopeImpl(
                    boxScope = this,
                    rowIndex = 0,
                    itemIndex = state.selectedIndex
                ).focusHighlight(state.hasFocus)
            }
        }
    }
}

/** A lone rail: items along it, nowhere to go vertically. */
private class RowTouchTarget(
    private val state: RokuFocusListState,
    private val config: RokuFocusConfig,
    private val itemPitchPx: Float,
    private val onItemSelected: ((index: Int) -> Unit)?,
    private val onBoundaryHit: (() -> Unit)?
) : RokuTouchTarget {

    override fun stepPx(orientation: Orientation): Float =
        if (orientation == Orientation.Horizontal) itemPitchPx else 0f

    override fun moveItems(steps: Int): Boolean {
        var moved = false
        rokuMoveBy(
            state = state,
            config = config,
            steps = steps,
            onSelected = { index ->
                moved = true
                onItemSelected?.invoke(index)
            },
            onBoundaryHit = onBoundaryHit
        )
        return moved
    }

    override fun moveRows(steps: Int): Boolean = false
}
