package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import kotlin.math.abs

/** Shared so the deprecated [RokuFocusConfig] factory below can reuse the same default. */
private val DefaultHighlightAnimationSpec: AnimationSpec<Float> = tween(
    durationMillis = 200,
    easing = FastOutSlowInEasing
)

val DefaultRokuFocusConfig = RokuFocusConfig()

/**
 * Navigation behaviour shared by [RokuLazyRow] and [RokuLazyColumn].
 *
 * @property highlightAnimationSpec Drives the highlight's position animation.
 * @property keyRepeatDelayMs Minimum gap between two accepted D-pad moves.
 * @property keyRepeatAccelAfter After this many consecutive presses, switch to
 *   [keyRepeatFastDelayMs]. 0 disables acceleration.
 * @property keyRepeatFastDelayMs Faster repeat delay used once accelerated.
 * @property wrapAround Wrap from the last item of a row back to the first, and vice versa.
 * @property hapticFeedback Pulse when a press cannot move the selection.
 * @property focusEscape Per-edge control over whether unconsumed edge presses let platform focus
 *   leave the list. See [RokuFocusEscape].
 * @property swipeVelocityThreshold Velocity at or below which a swipe moves exactly one step. Same
 *   unit the consumer passes to [stepsForVelocity] — points per second on tvOS.
 * @property swipeMaxSteps Upper bound on a single swipe, so a hard flick cannot fling a rail end
 *   to end.
 * @property swipeSensitivity Scales the curve. Above 1 moves further per swipe, below 1 less. The
 *   one knob most apps ever touch.
 * @property swipeStepsForVelocity Full override of the built-in curve. Null uses it.
 */
data class RokuFocusConfig(
    val highlightAnimationSpec: AnimationSpec<Float> = DefaultHighlightAnimationSpec,
    val keyRepeatDelayMs: Long = 150L,
    val keyRepeatAccelAfter: Int = 3,
    val keyRepeatFastDelayMs: Long = 50L,
    val wrapAround: Boolean = false,
    val hapticFeedback: Boolean = true,
    val focusEscape: RokuFocusEscape = RokuFocusEscape.All,
    // The swipe knobs are appended rather than filed next to the key-repeat ones so that every
    // positional 2.x call keeps its meaning.
    val swipeVelocityThreshold: Float = 500f,
    val swipeMaxSteps: Int = 5,
    val swipeSensitivity: Float = 1f,
    val swipeStepsForVelocity: ((velocity: Float) -> Int)? = null
)

/**
 * How many items one swipe of [velocity] should move, for handing to
 * [RokuFocusListState.moveBy] or [RokuColumnState.moveRowsBy].
 *
 * The sign is ignored — this answers "how far", and the caller decides the direction by the sign
 * of the step count it passes on. The built-in curve returns 1 at or below
 * [RokuFocusConfig.swipeVelocityThreshold] and grows linearly above it, scaled by
 * [RokuFocusConfig.swipeSensitivity] and capped at [RokuFocusConfig.swipeMaxSteps], so it does
 * something sensible with no configuration at all. Setting
 * [RokuFocusConfig.swipeStepsForVelocity] replaces it entirely.
 *
 * The built-in curve always returns at least 1, so a swipe the host decided to report always
 * moves something. A [RokuFocusConfig.swipeStepsForVelocity] override is returned untouched —
 * it replaces the curve entirely, [RokuFocusConfig.swipeMaxSteps] included, and owns its own
 * bounds.
 */
fun RokuFocusConfig.stepsForVelocity(velocity: Float): Int {
    swipeStepsForVelocity?.let { return it(velocity) }

    val maxSteps = swipeMaxSteps.coerceAtLeast(1)

    val speed = abs(velocity)
    // A non-positive threshold would divide by zero and has no sensible reading other than
    // "every swipe is a hard flick".
    if (swipeVelocityThreshold <= 0f) return maxSteps
    if (!speed.isFinite() || speed <= swipeVelocityThreshold) return 1

    val overshoot = (speed - swipeVelocityThreshold) / swipeVelocityThreshold
    val steps = 1f + overshoot * swipeSensitivity
    if (!steps.isFinite()) return maxSteps
    return steps.toInt().coerceIn(1, maxSteps)
}

/**
 * Builds a [RokuFocusConfig] from the 1.x all-or-nothing `allowFocusEscape` flag.
 *
 * `allowFocusEscape` has no default here on purpose: it keeps every existing call that does not
 * mention it resolving to the primary constructor instead of becoming ambiguous.
 */
@Deprecated(
    message = "allowFocusEscape was replaced by per-edge focusEscape.",
    replaceWith = ReplaceWith(
        "RokuFocusConfig(highlightAnimationSpec, keyRepeatDelayMs, keyRepeatAccelAfter, " +
            "keyRepeatFastDelayMs, wrapAround, hapticFeedback, " +
            "if (allowFocusEscape) RokuFocusEscape.All else RokuFocusEscape.None)"
    )
)
fun RokuFocusConfig(
    highlightAnimationSpec: AnimationSpec<Float> = DefaultHighlightAnimationSpec,
    keyRepeatDelayMs: Long = 150L,
    keyRepeatAccelAfter: Int = 3,
    keyRepeatFastDelayMs: Long = 50L,
    wrapAround: Boolean = false,
    hapticFeedback: Boolean = true,
    allowFocusEscape: Boolean
): RokuFocusConfig = RokuFocusConfig(
    highlightAnimationSpec = highlightAnimationSpec,
    keyRepeatDelayMs = keyRepeatDelayMs,
    keyRepeatAccelAfter = keyRepeatAccelAfter,
    keyRepeatFastDelayMs = keyRepeatFastDelayMs,
    wrapAround = wrapAround,
    hapticFeedback = hapticFeedback,
    focusEscape = if (allowFocusEscape) RokuFocusEscape.All else RokuFocusEscape.None
)

/**
 * Reads the 1.x all-or-nothing flag off a config. True only when every edge lets focus leave.
 *
 * `copy(allowFocusEscape = ...)` has no equivalent — build the config with `focusEscape` instead.
 */
@Deprecated(
    message = "allowFocusEscape was replaced by per-edge focusEscape.",
    replaceWith = ReplaceWith("focusEscape == RokuFocusEscape.All")
)
val RokuFocusConfig.allowFocusEscape: Boolean
    get() = focusEscape == RokuFocusEscape.All
