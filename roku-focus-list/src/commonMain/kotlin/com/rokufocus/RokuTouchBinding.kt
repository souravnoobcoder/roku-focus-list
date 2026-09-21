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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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

    /**
     * Whether one step along [orientation] toward [forward] would change the selection — without
     * taking it. This is how the touchpad decides whether a direction key from the platform is
     * its own gesture echoed back (the selection can, or did, move that way: the touchpad owns
     * it) or a move it cannot make (an open edge, the wrong axis): then the key has to reach the
     * key handler, whose `focusEscape` policy lets focus leave the list.
     */
    fun canMove(orientation: Orientation, forward: Boolean): Boolean
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
    val highlightParallax: Float,
    val lightAlpha: Float,
    val lightColor: Color
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
            highlightParallax = config.hintHighlightParallax,
            lightAlpha = config.hintLight.coerceIn(0f, 1f),
            lightColor = config.hintLightColor
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

/**
 * The focused item's share of the hint: the card leans with parallax 1 (the highlight goes
 * further), and a light plays across it. The light is blended `SrcAtop` inside the item's own
 * layer, so it lands only on pixels the card painted — a rounded or odd-shaped card keeps its
 * corners — which is why the layer composites offscreen while the light is enabled.
 */
internal fun Modifier.rokuTouchLean(lean: State<Offset>, style: RokuTouchLeanStyle): Modifier {
    val lit = style.lightAlpha > 0f
    val leaning = graphicsLayer {
        if (lit) compositingStrategy = CompositingStrategy.Offscreen
        applyTouchLean(lean.value, style, parallax = 1f)
    }
    return if (lit) {
        leaning.drawWithContent {
            drawContent()
            drawTouchLight(lean.value, style)
        }
    } else {
        leaning
    }
}

/**
 * A soft spot of light that slides toward the thumb and brightens with pull, gone at rest. The
 * spot's centre travels [LightTravel] of the card's size from the middle at a full step, and its
 * radius covers the card so the falloff reads as a sheen rather than a torch.
 */
private fun ContentDrawScope.drawTouchLight(lean: Offset, style: RokuTouchLeanStyle) {
    val x = shapeLean(lean.x)
    val y = shapeLean(lean.y)
    val pull = max(abs(x), abs(y))
    if (pull <= 0f) return
    val center = Offset(
        size.width * (0.5f + LightTravel * x),
        size.height * (0.5f + LightTravel * y)
    )
    drawRect(
        brush = Brush.radialGradient(
            0f to style.lightColor.copy(alpha = style.lightAlpha * pull),
            1f to style.lightColor.copy(alpha = 0f),
            center = center,
            radius = max(size.width, size.height) * LightRadius
        ),
        blendMode = BlendMode.SrcAtop
    )
}

private const val LightTravel = 0.35f
private const val LightRadius = 0.9f

/**
 * A square root of the pending fraction: a fifth of a step already gives almost half the travel,
 * so a brush of the pad is answered, while the last stretch before a move adds little.
 */
private fun shapeLean(fraction: Float): Float = sign(fraction) * sqrt(abs(fraction))

/**
 * Drops the direction key the Compose tvOS fork dispatches for a swipe this touchpad owns —
 * one it already applied, or one it can still apply along that axis. A key the touchpad cannot
 * act on (the list is at an open edge, or the key runs across a row's axis) is left alone so it
 * reaches the key handler and `focusEscape` can let focus leave. Sits before the key handler in
 * the chain. Without a touchpad the modifier is a no-op.
 */
internal fun Modifier.rokuTouchpadKeyGuard(touchpad: RokuTouchpad?): Modifier =
    if (touchpad == null) {
        this
    } else {
        onPreviewKeyEvent { event ->
            val orientation = when (event.key) {
                Key.DirectionLeft, Key.DirectionRight -> Orientation.Horizontal
                Key.DirectionUp, Key.DirectionDown -> Orientation.Vertical
                else -> return@onPreviewKeyEvent false
            }
            val forward = event.key == Key.DirectionRight || event.key == Key.DirectionDown
            touchpad.swallowsKey(orientation, forward, RokuClock.uptimeMillis())
        }
    }
