package com.rokufocus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight focus highlight: a single rounded stroke that sits slightly outside
 * the card bounds. Tuned for low-end TV GPUs:
 *  - one drawRoundRect per frame (no glow layer)
 *  - pixel conversions hoisted out of the per-frame DrawScope
 *  - internal alpha animation + early return skips draw work entirely once hidden
 */
@Composable
fun BoxScope.DefaultFocusHighlight(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White,
    unfocusedAlpha: Float = 0.0f,
    focusedAlpha: Float = 1.0f,
    borderWidth: Dp = 3.dp,
    cornerRadius: Dp = 12.dp,
    overflow: Dp = 6.dp,
    animateScale: Boolean = false
) {
    val alpha by animateFloatAsState(
        targetValue = if (isFocused) focusedAlpha else unfocusedAlpha,
        animationSpec = tween(durationMillis = 150),
        label = "highlight_alpha"
    )

    val targetScale = if (animateScale && isFocused) 1.02f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "highlight_scale"
    )

    // Once fully faded out, drop out of composition entirely so we stop drawing.
    if (alpha <= 0f) return

    // Hoist pixel conversions out of the hot draw path.
    val density = LocalDensity.current
    val overflowPx = with(density) { overflow.toPx() }
    val borderWidthPx = with(density) { borderWidth.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() } + overflowPx
    val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
    val stroke = Stroke(width = borderWidthPx)
    val topLeft = Offset(-overflowPx, -overflowPx)
    val overflowTwice = overflowPx * 2f

    Box(
        modifier = modifier
            .matchParentSize()
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    topLeft = topLeft,
                    size = Size(size.width + overflowTwice, size.height + overflowTwice),
                    cornerRadius = cornerRadius,
                    style = stroke
                )
            }
    )
}
