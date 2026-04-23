package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

val DefaultRokuFocusConfig = RokuFocusConfig()

data class RokuFocusConfig(
    val highlightAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    ),
    val keyRepeatDelayMs: Long = 150L,
    /** After this many consecutive presses, switch to [keyRepeatFastDelayMs]. 0 = disabled. */
    val keyRepeatAccelAfter: Int = 3,
    /** Faster repeat delay used after [keyRepeatAccelAfter] consecutive presses. */
    val keyRepeatFastDelayMs: Long = 50L,
    val wrapAround: Boolean = false,
    val hapticFeedback: Boolean = true,
    /**
     * When true, D-pad presses at a list edge (first/last row or column) are not
     * consumed, so the platform focus system can move focus to an adjacent composable
     * (e.g., a sidebar or top bar). When false, edge presses are swallowed.
     */
    val allowFocusEscape: Boolean = true
)
