package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RokuKeyRepeatTrackerTest {

    private val config = RokuFocusConfig(
        keyRepeatDelayMs = 150L,
        keyRepeatAccelAfter = 3,
        keyRepeatFastDelayMs = 50L
    )

    @Test
    fun theFirstPressIsNeverThrottled() {
        val tracker = RokuKeyRepeatTracker()
        tracker.resetIfIdle(1_000_000L)
        assertFalse(tracker.isThrottled(1_000_000L, config))
    }

    @Test
    fun pressesInsideTheRepeatDelayAreThrottled() {
        val tracker = RokuKeyRepeatTracker()
        tracker.accept(1_000_000L)

        assertTrue(tracker.isThrottled(1_000_100L, config))
        assertFalse(tracker.isThrottled(1_000_150L, config))
    }

    @Test
    fun holdingTheKeyAcceleratesAfterTheConfiguredRunOfPresses() {
        val tracker = RokuKeyRepeatTracker()
        var now = 1_000_000L
        repeat(3) {
            tracker.accept(now)
            now += 150L
        }
        assertEquals(3, tracker.consecutivePresses)

        // Accelerated: 50ms is now enough, where 150ms was needed before.
        assertFalse(tracker.isThrottled(tracker.lastKeyTime + 50L, config))
    }

    @Test
    fun accelerationIsDisabledWhenTheThresholdIsZero() {
        val tracker = RokuKeyRepeatTracker()
        val noAccel = config.copy(keyRepeatAccelAfter = 0)
        var now = 1_000_000L
        repeat(5) {
            tracker.accept(now)
            now += 150L
        }
        assertTrue(tracker.isThrottled(tracker.lastKeyTime + 50L, noAccel))
        assertFalse(tracker.isThrottled(tracker.lastKeyTime + 150L, noAccel))
    }

    @Test
    fun lettingGoOfTheKeyForgetsTheStreak() {
        val tracker = RokuKeyRepeatTracker()
        repeat(5) { tracker.accept(1_000_000L + it * 150L) }
        assertEquals(5, tracker.consecutivePresses)

        tracker.resetIfIdle(tracker.lastKeyTime + 301L)
        assertEquals(0, tracker.consecutivePresses)
        assertTrue(tracker.isThrottled(tracker.lastKeyTime + 100L, config))
    }
}
