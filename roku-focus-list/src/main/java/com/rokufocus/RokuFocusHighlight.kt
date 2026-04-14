package com.rokufocus

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun BoxScope.DefaultFocusHighlight(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White,
    unfocusedAlpha: Float = 0.0f,
    focusedAlpha: Float = 1.0f,
    borderWidth: Dp = 3.dp,
    cornerRadius: Dp = 12.dp,
    glowAlpha: Float = 0.3f,
    overflow: Dp = 6.dp
) {
    val alpha by animateFloatAsState(
        targetValue = if (isFocused) focusedAlpha else unfocusedAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "highlight_alpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .matchParentSize()
                .graphicsLayer {
                    this.alpha = alpha
                    clip = false
                }
                .drawBehind {
                    val overflowPx = overflow.toPx()
                    val bw = borderWidth.toPx()
                    val glowBw = bw + 2.dp.toPx()
                    val cr = cornerRadius.toPx() + overflowPx
                    val glowCr = cr + 1.dp.toPx()

                    val left = -overflowPx
                    val top = -overflowPx
                    val w = size.width + overflowPx * 2
                    val h = size.height + overflowPx * 2

                    // Outer glow
                    drawRoundRect(
                        color = borderColor.copy(alpha = glowAlpha),
                        topLeft = Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                        size = Size(w + 2.dp.toPx(), h + 2.dp.toPx()),
                        cornerRadius = CornerRadius(glowCr, glowCr),
                        style = Stroke(width = glowBw)
                    )

                    // Main border
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = bw)
                    )
                }
        )
    }
}
