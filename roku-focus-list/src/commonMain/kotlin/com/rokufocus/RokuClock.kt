package com.rokufocus

import kotlin.time.TimeSource

/**
 * Monotonic millisecond clock used for D-pad key-repeat throttling.
 *
 * Replaces `android.os.SystemClock.uptimeMillis()`, which is not available outside Android.
 * Only differences between two readings are meaningful.
 */
internal object RokuClock {

    /**
     * Readings are offset by this baseline so the `0L` "no key pressed yet" sentinel held by
     * the key handlers always compares as far in the past — `uptimeMillis()` returned time
     * since boot, so it was never small enough to look like a recent press.
     */
    private const val BASELINE_MS = 1_000_000L

    private val origin = TimeSource.Monotonic.markNow()

    fun uptimeMillis(): Long = BASELINE_MS + origin.elapsedNow().inWholeMilliseconds
}
