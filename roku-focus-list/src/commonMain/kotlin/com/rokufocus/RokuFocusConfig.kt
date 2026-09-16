package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

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
 *   leave the list. See [RokuFocusEscape]. Touchpad pacing lives on [RokuTouchpadConfig].
 * @property rowEntry Which card a vertical move in a [RokuLazyColumn] lands on when it enters a
 *   floating row: the card under the highlight ([RokuRowEntry.Spatial], the default) or the one
 *   the row last had ([RokuRowEntry.Remembered]).
 * @property verticalAnimationSpec Speed of a vertical move in [RokuLazyColumn] and
 *   [RokuFocusGrid]: drives both the highlight's Y and the content scroll between rows, so the
 *   two travel together. Null keeps [highlightAnimationSpec] for the highlight and the library's
 *   default spring for the scroll. Horizontal moves are unaffected.
 * @property verticalKeyRepeatDelayMs Minimum gap between two accepted UP/DOWN presses while the
 *   key is held, before acceleration. Null uses [keyRepeatDelayMs]. Raise it to slow a held
 *   D-pad down the column without slowing it along a row.
 *
 * The parameters after `focusEscape` are appended in the order they were added so positional
 * 2.x calls keep their meaning.
 */
data class RokuFocusConfig(
    val highlightAnimationSpec: AnimationSpec<Float> = DefaultHighlightAnimationSpec,
    val keyRepeatDelayMs: Long = 150L,
    val keyRepeatAccelAfter: Int = 3,
    val keyRepeatFastDelayMs: Long = 50L,
    val wrapAround: Boolean = false,
    val hapticFeedback: Boolean = true,
    val focusEscape: RokuFocusEscape = RokuFocusEscape.All,
    val rowEntry: RokuRowEntry = RokuRowEntry.Spatial,
    val verticalAnimationSpec: AnimationSpec<Float>? = null,
    val verticalKeyRepeatDelayMs: Long? = null
)

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
