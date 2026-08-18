package com.rokufocus

import kotlin.test.Test
import kotlin.test.assertTrue

class RokuClockTest {

    /**
     * The key handlers seed `lastKeyTime` with 0L to mean "no key pressed yet". If readings
     * started near zero the very first D-pad press would be swallowed by the repeat throttle,
     * so the clock must always report well past the longest throttle window (300ms).
     */
    @Test
    fun readingsStartFarPastTheLongestThrottleWindow() {
        assertTrue(RokuClock.uptimeMillis() > 300L)
    }

    @Test
    fun readingsNeverGoBackwards() {
        var previous = RokuClock.uptimeMillis()
        repeat(1000) {
            val now = RokuClock.uptimeMillis()
            assertTrue(now >= previous, "clock went backwards: $now < $previous")
            previous = now
        }
    }
}
