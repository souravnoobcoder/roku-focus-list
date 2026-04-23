package com.rokufocus

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun RokuLazyRowImpl(
    state: RokuFocusListState,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp,
    itemSpacing: Dp = 12.dp,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
) {
    if (state.itemCount == 0) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val hapticFeedback = LocalHapticFeedback.current

    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }

    val onBoundaryHit: (() -> Unit)? = if (config.hapticFeedback) {
        { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
    } else null

    // BoxWithConstraints gives us the actual viewport width (not full screen),
    // critical when sidebars, insets, or split-screen reduce available space.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                val newFocus = focusState.hasFocus || focusState.isFocused
                if (newFocus != hasFocus) {
                    if (newFocus) onFocusEnter?.invoke() else onFocusExit?.invoke()
                    hasFocus = newFocus
                }
            }
            .focusable()
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
        state.visibleCount = if (denominator > 0.dp) {
            ((availableWidth + itemSpacing) / denominator).toInt().coerceAtLeast(1)
        } else 1

        // Highlight X position using shared utility (handles scroll clamping at edges)
        val targetHighlightX = computeHighlightOffsetPx(
            state, itemWidthPx, itemSpacingPx, startPaddingPx, endPaddingPx, viewportWidthPx
        )
        val animatedHighlightX by animateFloatAsState(
            targetValue = targetHighlightX,
            animationSpec = config.highlightAnimationSpec,
            label = "roku_row_highlight_x"
        )

        RokuRowContent(
            state = state,
            contentPadding = contentPadding,
            itemWidth = itemWidth,
            itemSpacing = itemSpacing,
            itemContent = itemContent
        )

        // Highlight overlay — positioned at the computed X offset
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .graphicsLayer { translationX = animatedHighlightX }
                    .width(itemWidth)
                    .fillMaxHeight()
            ) {
                focusHighlight(hasFocus)
            }
        }
    }
}
