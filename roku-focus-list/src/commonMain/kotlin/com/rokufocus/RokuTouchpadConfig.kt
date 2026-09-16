package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a [RokuTouchpad] turns thumb travel into moves, and how the focused card answers travel
 * that is not yet a move.
 *
 * Distances and speeds are in the units the host reports to the touchpad — UIKit points on tvOS,
 * where a full swipe across the Siri Remote pad is roughly 1,000–1,800 pt and the thumb lifts off
 * at 4,000–8,000 pt/s after a relaxed flick and 15,000–20,000 after a hard one. The defaults were
 * measured against Apple TV+ on an Apple TV HD.
 *
 * @property itemStepFraction Travel per item while dragging along a row, in item pitches. At 0.65
 *   a full pad swipe walks four to six cards before acceleration and a half-pad swipe still moves
 *   one.
 * @property rowStepFraction Travel per row while dragging up or down, in row pitches.
 * @property axisLock Travel before a contact commits to an axis and can move the selection. Below
 *   it the thumb only leans the card, so a click that rolls a little never moves focus.
 * @property gainStartVelocity Thumb speed up to which travel counts once.
 * @property gainMaxVelocity Thumb speed from which travel counts [maxGain] times. Smooth between
 *   the two — the focus engine's equivalent of pointer acceleration, so a hard swipe crosses about
 *   twice the cards of a careful one without ever coasting after lift-off.
 * @property maxGain Multiplier applied to travel at [gainMaxVelocity] and above.
 * @property hintTravel How far the focused card leans toward the thumb at a full step of pending
 *   travel. The lean follows a square-root curve, so a brush of the pad already reads. Kept
 *   small on purpose: the light does most of the talking.
 * @property hintTiltDegrees How far the card tilts toward the thumb at a full step, around the
 *   axis perpendicular to the movement.
 * @property hintScale The card's scale at a full step of pull; 1 disables the lift.
 * @property hintHighlightParallax How much further than the card the highlight leans, so the two
 *   read as layers with depth between them. 1 moves them as one.
 * @property hintLight Peak opacity of the light that plays across the focused card while the
 *   thumb moves: a soft spot that slides toward the thumb and brightens with pull, drawn only
 *   over the card's own pixels so any shape keeps its corners. 0 disables it.
 * @property hintLightColor Colour of that light.
 * @property hintReleaseSpec How the lean returns to rest when the thumb lifts. The default
 *   overshoots once, so the card visibly springs back rather than fading.
 */
data class RokuTouchpadConfig(
    val itemStepFraction: Float = 0.65f,
    val rowStepFraction: Float = 1f,
    val axisLock: Float = 16f,
    val gainStartVelocity: Float = 2500f,
    val gainMaxVelocity: Float = 12000f,
    val maxGain: Float = 2f,
    val hintTravel: Dp = 4.dp,
    val hintTiltDegrees: Float = 2f,
    val hintScale: Float = 1.012f,
    val hintHighlightParallax: Float = 1.2f,
    val hintLight: Float = 0.10f,
    val hintLightColor: Color = Color.White,
    val hintReleaseSpec: AnimationSpec<Offset> = spring(dampingRatio = 0.55f, stiffness = 450f)
)

/** Travel multiplier for a thumb moving at [speed]: 1 up to the start speed, [RokuTouchpadConfig.maxGain] from the max speed, smoothstep between. */
internal fun RokuTouchpadConfig.gain(speed: Float): Float {
    val range = gainMaxVelocity - gainStartVelocity
    if (range <= 0f) return if (speed >= gainMaxVelocity) maxGain else 1f
    val t = ((speed - gainStartVelocity) / range).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return 1f + (maxGain - 1f) * eased
}
