package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

data class RokuFocusConfig(
    val animationSpec: AnimationSpec<Float> = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    ),
    val highlightAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    ),
    val keyRepeatDelayMs: Long = 150L,
    val wrapAround: Boolean = false,
    val hapticFeedback: Boolean = true
)
