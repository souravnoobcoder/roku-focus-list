package com.rokufocus.sample

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * Streams Siri Remote touchpad movement to [TvRemotePan] for the whole life of a contact: a
 * [RemotePanEvent.Began], a [RemotePanEvent.Changed] per movement report, and a
 * [RemotePanEvent.Ended] carrying the lift-off velocity in points per second.
 *
 * This is how the native focus engine treats the touchpad — focus follows the finger while it is
 * down and coasts on after a flick — and it is the model [RemoteNavigator] reproduces. Reporting
 * once at the end, as an earlier version of this file did, can only ever produce a jump.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class SiriRemotePanTarget : NSObject() {

    @ObjCAction
    fun handlePan(recognizer: UIPanGestureRecognizer) {
        val view = recognizer.view ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan -> {
                TvRemotePan.onEvent?.invoke(RemotePanEvent.Began)
                // The movement that got the gesture recognised is real travel too.
                forwardTranslation(recognizer, view)
            }

            UIGestureRecognizerStateChanged -> forwardTranslation(recognizer, view)

            UIGestureRecognizerStateEnded -> {
                val (vx, vy) = recognizer.velocityInView(view).useContents { x to y }
                TvRemotePan.onEvent?.invoke(RemotePanEvent.Ended(vx.toFloat(), vy.toFloat()))
            }

            UIGestureRecognizerStateCancelled, UIGestureRecognizerStateFailed ->
                TvRemotePan.onEvent?.invoke(RemotePanEvent.Cancelled)

            else -> Unit
        }
    }

    private fun forwardTranslation(recognizer: UIPanGestureRecognizer, view: UIView) {
        val (dx, dy) = recognizer.translationInView(view).useContents { x to y }
        recognizer.setTranslation(CGPointMake(0.0, 0.0), inView = view)
        TvRemotePan.onEvent?.invoke(RemotePanEvent.Changed(dx.toFloat(), dy.toFloat()))
    }
}

/**
 * Kept for the life of the process: a gesture recogniser holds its target weakly, so letting this
 * go would silently stop every swipe working.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private val panTarget = SiriRemotePanTarget()

/** Attaches the pan recogniser to the Compose host view. */
@OptIn(ExperimentalForeignApi::class)
fun installSiriRemotePan(view: UIView) {
    val screen = UIScreen.mainScreen
    TvRemotePan.screenScale = screen.scale.toFloat()
    TvRemotePan.displayRefreshHz = screen.maximumFramesPerSecond.toInt()
    println(
        "[roku] display scale=${TvRemotePan.screenScale} maxFps=${TvRemotePan.displayRefreshHz} " +
            "points=${screen.bounds.useContents { "${size.width.toInt()}x${size.height.toInt()}" }}"
    )
    val recognizer = UIPanGestureRecognizer(
        target = panTarget,
        action = NSSelectorFromString("handlePan:"),
    )
    // Once the pan is recognised UIKit cancels the underlying touch in the Compose view. That is
    // what stops the Compose tvOS fork's own swipe-to-focus, which turns every touchpad swipe
    // into one D-pad key at lift-off, from moving the selection a second time on top of this
    // recogniser. Clicks and D-pad presses are UIPress events and are not touched.
    recognizer.cancelsTouchesInView = true
    view.addGestureRecognizer(recognizer)
}
