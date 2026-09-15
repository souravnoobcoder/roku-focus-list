package com.rokufocus.sample

/**
 * One report from the host's pan recogniser, in UIKit points. [Changed] carries the movement since
 * the previous report, not the total, so the receiver can follow the finger as it goes.
 */
sealed interface RemotePanEvent {
    data object Began : RemotePanEvent
    data class Changed(val dx: Float, val dy: Float) : RemotePanEvent
    data class Ended(val velocityX: Float, val velocityY: Float) : RemotePanEvent
    data object Cancelled : RemotePanEvent
}

/**
 * Hands Siri Remote touchpad movement from the host layer to whichever screen is listening.
 *
 * roku-focus-list is deliberately input-agnostic — it takes a step count, never a gesture — so
 * reading the touchpad belongs to the app. On tvOS that is a UIKit `UIPanGestureRecognizer` (see
 * `SiriRemotePan.apple.kt`), which sits outside the Compose tree and cannot reach the list state
 * directly. The screen registers a handler here while it is composed and the recogniser calls it.
 */
object TvRemotePan {

    /** Set by the screen while it is composed; null when nothing is listening. */
    var onEvent: ((RemotePanEvent) -> Unit)? = null

    /**
     * Pixels per UIKit point on this screen: 1 on an Apple TV HD, 2 on an Apple TV 4K. Gesture
     * distances arrive in points; anything derived from Compose pixel sizes divides by this to
     * compare against them.
     */
    var screenScale: Float = 1f

    /** Refresh rate of the panel the app draws to, for judging frame gaps. 50 on a PAL-region TV. */
    var displayRefreshHz: Int = 60
}
