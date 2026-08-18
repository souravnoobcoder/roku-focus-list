package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object RokuAnimationSpec {

    val Default: AnimationSpec<Float> = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    val Fast: AnimationSpec<Float> = tween(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    val Smooth: AnimationSpec<Float> = spring(
        dampingRatio = 0.8f,
        stiffness = 300f
    )
}
