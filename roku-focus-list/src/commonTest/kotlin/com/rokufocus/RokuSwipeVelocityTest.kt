package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RokuSwipeVelocityTest {

    private val config = DefaultRokuFocusConfig

    @Test
    fun aGentleSwipeMovesExactlyOneStep() {
        assertEquals(1, config.stepsForVelocity(0f))
        assertEquals(1, config.stepsForVelocity(120f))
        assertEquals(
            1, config.stepsForVelocity(config.swipeVelocityThreshold),
            "the threshold itself is still a one-step swipe"
        )
    }

    @Test
    fun stepsGrowWithVelocityAboveTheThreshold() {
        // threshold 500, sensitivity 1: each extra 500 pt/s buys one more step
        assertEquals(2, config.stepsForVelocity(1000f))
        assertEquals(3, config.stepsForVelocity(1500f))
        assertEquals(4, config.stepsForVelocity(2000f))
    }

    @Test
    fun aHardFlickIsCappedBySwipeMaxSteps() {
        assertEquals(config.swipeMaxSteps, config.stepsForVelocity(100_000f))
        assertEquals(2, config.copy(swipeMaxSteps = 2).stepsForVelocity(100_000f))
    }

    @Test
    fun directionIsTheCallersJobSoTheCurveIgnoresSign() {
        assertEquals(config.stepsForVelocity(1500f), config.stepsForVelocity(-1500f))
    }

    @Test
    fun sensitivityScalesTheCurveMonotonically() {
        val velocity = 2000f
        val slow = config.copy(swipeSensitivity = 0.5f).stepsForVelocity(velocity)
        val normal = config.copy(swipeSensitivity = 1f).stepsForVelocity(velocity)
        val fast = config.copy(swipeSensitivity = 2f).stepsForVelocity(velocity)

        assertTrue(slow < normal, "0.5 must travel less than 1 ($slow vs $normal)")
        assertTrue(normal < fast, "2 must travel further than 1 ($normal vs $fast)")
    }

    @Test
    fun sensitivityNeverGoesBackwardsAcrossTheWholeCurve() {
        val sensitivities = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)
        for (velocity in listOf(600f, 900f, 1500f, 3000f, 8000f)) {
            val steps = sensitivities.map { config.copy(swipeSensitivity = it).stepsForVelocity(velocity) }
            assertEquals(
                steps.sorted(), steps,
                "raising sensitivity must never move fewer steps (velocity $velocity gave $steps)"
            )
        }
    }

    @Test
    fun raisingVelocityNeverMovesFewerSteps() {
        val steps = (0..20).map { config.stepsForVelocity(it * 400f) }
        assertEquals(steps.sorted(), steps, "the curve must be monotonic in velocity: $steps")
        assertTrue(steps.all { it in 1..config.swipeMaxSteps })
    }

    @Test
    fun aCustomCurveReplacesTheBuiltInOneEntirely() {
        val custom = config.copy(swipeStepsForVelocity = { v -> if (v > 100f) 42 else 0 })

        assertEquals(
            42, custom.stepsForVelocity(5000f),
            "a full override owns its own bounds — swipeMaxSteps must not clip it"
        )
        assertEquals(0, custom.stepsForVelocity(10f))
    }

    @Test
    fun degenerateConfigurationsStayInRangeInsteadOfThrowing() {
        assertEquals(1, config.copy(swipeMaxSteps = 0).stepsForVelocity(9000f))
        assertEquals(1, config.copy(swipeSensitivity = 0f).stepsForVelocity(9000f))
        assertEquals(1, config.copy(swipeSensitivity = -5f).stepsForVelocity(9000f))

        // a zero threshold would divide by zero; every swipe reads as a hard flick instead
        assertEquals(config.swipeMaxSteps, config.copy(swipeVelocityThreshold = 0f).stepsForVelocity(1f))

        assertTrue(config.stepsForVelocity(Float.NaN) in 1..config.swipeMaxSteps)
        assertTrue(config.stepsForVelocity(Float.POSITIVE_INFINITY) in 1..config.swipeMaxSteps)
    }

    @Test
    fun theSwipeDefaultsLeaveExistingConfigsUntouched() {
        val default = RokuFocusConfig()
        assertEquals(500f, default.swipeVelocityThreshold)
        assertEquals(5, default.swipeMaxSteps)
        assertEquals(1f, default.swipeSensitivity)
        assertEquals(null, default.swipeStepsForVelocity)

        // a 2.x positional call still means what it meant
        val positional = RokuFocusConfig(
            DefaultRokuFocusConfig.highlightAnimationSpec, 150L, 3, 50L, true, false,
            RokuFocusEscape.None
        )
        assertTrue(positional.wrapAround)
        assertEquals(RokuFocusEscape.None, positional.focusEscape)
        assertEquals(5, positional.swipeMaxSteps)
    }
}
