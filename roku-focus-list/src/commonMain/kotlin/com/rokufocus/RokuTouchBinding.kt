package com.rokufocus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * What a component offers a [RokuTouchpad] while it holds focus: how far one step is in pixels
 * along each axis, and the moves themselves, each answering whether the selection changed — that
 * is how the touchpad knows it hit an edge.
 */
internal interface RokuTouchTarget {
    fun stepPx(orientation: Orientation): Float
    fun moveItems(steps: Int): Boolean
    fun moveRows(steps: Int): Boolean
}

/** Routes [touchpad] to [target] for as long as the component holds focus. */
@Composable
internal fun BindRokuTouchpad(touchpad: RokuTouchpad, target: RokuTouchTarget, hasFocus: Boolean) {
    DisposableEffect(touchpad, target, hasFocus) {
        if (hasFocus) touchpad.bind(target)
        onDispose { touchpad.unbind(target) }
    }
}

/**
 * The lean the focused component draws, or null when there is no touchpad. Follows the thumb
 * directly while it is down and springs back through [RokuTouchpadConfig.hintReleaseSpec] once it
 * lifts; the collector only wakes when the touchpad writes, so a resting pad costs nothing.
 */
@Composable
internal fun rememberRokuTouchLean(touchpad: RokuTouchpad?, hasFocus: Boolean): State<Offset>? {
    if (touchpad == null) return null
    val lean = remember(touchpad) { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(touchpad, hasFocus) {
        if (!hasFocus) {
            lean.snapTo(Offset.Zero)
            return@LaunchedEffect
        }
        snapshotFlow { TouchLeanFrame(touchpad.lean, touchpad.isDragging) }.collectLatest { frame ->
            if (frame.dragging) {
                lean.snapTo(frame.lean)
            } else {
                lean.animateTo(Offset.Zero, touchpad.config.hintReleaseSpec)
            }
        }
    }
    return remember(lean) { lean.asState() }
}

private data class TouchLeanFrame(val lean: Offset, val dragging: Boolean)

/** Pixel-resolved hint tuning, built once per density. */
internal class RokuTouchLeanStyle(
    val travelPx: Float,
    val tiltDegrees: Float,
    val scale: Float,
    val highlightParallax: Float
)

@Composable
internal fun rememberRokuTouchLeanStyle(touchpad: RokuTouchpad?): RokuTouchLeanStyle? {
    if (touchpad == null) return null
    val density = LocalDensity.current
    return remember(touchpad, density) {
        val config = touchpad.config
        RokuTouchLeanStyle(
            travelPx = with(density) { config.hintTravel.toPx() },
            tiltDegrees = config.hintTiltDegrees,
            scale = config.hintScale,
            highlightParallax = config.hintHighlightParallax
        )
    }
}

/**
 * Leans, tilts and lifts the layer toward the pending travel. Adds to whatever translation the
 * caller already set, so the highlight overlays can apply it after positioning themselves. Reads
 * of [lean] belong in a layer block, where following the thumb re-draws the layer and never
 * recomposes.
 */
internal fun GraphicsLayerScope.applyTouchLean(lean: Offset, style: RokuTouchLeanStyle, parallax: Float) {
    if (lean == Offset.Zero) return
    val x = shapeLean(lean.x)
    val y = shapeLean(lean.y)
    translationX += x * style.travelPx * parallax
    translationY += y * style.travelPx * parallax
    rotationY = x * style.tiltDegrees
    rotationX = -y * style.tiltDegrees
    val lift = 1f + (style.scale - 1f) * max(abs(x), abs(y))
    scaleX = lift
    scaleY = lift
}

/** The focused item's share of the hint: the card leans with parallax 1, the highlight further. */
internal fun Modifier.rokuTouchLean(lean: State<Offset>, style: RokuTouchLeanStyle): Modifier =
    graphicsLayer { applyTouchLean(lean.value, style, parallax = 1f) }

/**
 * A square root of the pending fraction: a fifth of a step already gives almost half the travel,
 * so a brush of the pad is answered, while the last stretch before a move adds little.
 */
private fun shapeLean(fraction: Float): Float = sign(fraction) * sqrt(abs(fraction))

/**
 * Drops the direction key the Compose tvOS fork dispatches at lift-off for a swipe this touchpad
 * has already applied. Sits before the key handler in the chain so the handler never sees it.
 * Without a touchpad the modifier is a no-op.
 */
internal fun Modifier.rokuTouchpadKeyGuard(touchpad: RokuTouchpad?): Modifier =
    if (touchpad == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            isDirectionKey(event.key) && touchpad.swallowsKey(RokuClock.uptimeMillis())
        }
    }

private fun isDirectionKey(key: Key): Boolean =
    key == Key.DirectionLeft || key == Key.DirectionRight || key == Key.DirectionUp || key == Key.DirectionDown
