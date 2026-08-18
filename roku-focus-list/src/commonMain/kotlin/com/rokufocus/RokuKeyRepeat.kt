package com.rokufocus

/**
 * Key-repeat bookkeeping for one navigable surface (a row or a column).
 *
 * Deliberately backed by plain fields rather than snapshot state: the counters are only ever
 * read from key callbacks and effect bodies, never during composition or draw, so making them
 * observable would cost a recomposition per D-pad press and buy nothing.
 */
internal class RokuKeyRepeatTracker {

    var lastKeyTime: Long = 0L
        private set

    var consecutivePresses: Int = 0
        private set

    /** Forgets the acceleration streak once the user has stopped pressing for [IdleResetMs]. */
    fun resetIfIdle(now: Long) {
        if (now - lastKeyTime > IdleResetMs) consecutivePresses = 0
    }

    /** True when [now] is too soon after the previous accepted press for another move. */
    fun isThrottled(now: Long, config: RokuFocusConfig): Boolean {
        val accelerated = config.keyRepeatAccelAfter > 0 &&
            consecutivePresses >= config.keyRepeatAccelAfter
        val delay = if (accelerated) config.keyRepeatFastDelayMs else config.keyRepeatDelayMs
        return now - lastKeyTime < delay
    }

    /** Records an accepted press, extending the acceleration streak. */
    fun accept(now: Long) {
        lastKeyTime = now
        consecutivePresses++
    }

    private companion object {
        const val IdleResetMs = 300L
    }
}
