package com.rokufocus

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
 * Feeds a [RokuTouchpad] from a `UIPanGestureRecognizer`, for the whole life of a contact: a
 * report per movement with the thumb's speed at that moment, and the lift-off. Reporting once at
 * the end, as the fork's own swipe-to-focus does, can only ever produce a jump.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class RokuPanTarget(private val touchpad: RokuTouchpad) : NSObject() {

    @ObjCAction
    fun handlePan(recognizer: UIPanGestureRecognizer) {
        val view = recognizer.view ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan -> {
                touchpad.panBegan()
                forwardTranslation(recognizer, view)
            }

            UIGestureRecognizerStateChanged -> forwardTranslation(recognizer, view)
            UIGestureRecognizerStateEnded -> touchpad.panEnded()
            UIGestureRecognizerStateCancelled, UIGestureRecognizerStateFailed -> touchpad.panCancelled()
            else -> Unit
        }
    }

    private fun forwardTranslation(recognizer: UIPanGestureRecognizer, view: UIView) {
        val (dx, dy) = recognizer.translationInView(view).useContents { x to y }
        val (vx, vy) = recognizer.velocityInView(view).useContents { x to y }
        recognizer.setTranslation(CGPointMake(0.0, 0.0), inView = view)
        touchpad.panChanged(dx.toFloat(), dy.toFloat(), vx.toFloat(), vy.toFloat())
    }
}

/** A Siri Remote attachment; [detach] removes the recogniser from its view. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class RokuSiriRemoteAttachment internal constructor(
    private val view: UIView,
    private val recognizer: UIPanGestureRecognizer,
    private val target: RokuPanTarget
) {
    fun detach() {
        view.removeGestureRecognizer(recognizer)
        retainedTargets.remove(target)
    }
}

/**
 * A gesture recogniser holds its target weakly; letting a target go would silently stop every
 * swipe working, so they are retained here until detached.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private val retainedTargets = mutableSetOf<RokuPanTarget>()

/**
 * Reads the Siri Remote touchpad for this touchpad by attaching a `UIPanGestureRecognizer` to
 * [view] — the Compose host view, `ComposeUIViewController { … }.view`. Reports arrive in UIKit
 * points, and [RokuTouchpad.pxPerUnit] is set from the screen scale so travel can be compared
 * against item sizes.
 *
 * The recogniser cancels the underlying touch in the view once it recognises. That is what stops
 * the Compose tvOS fork's own swipe-to-focus — one D-pad key per swipe at lift-off — from moving
 * the selection a second time; clicks and D-pad presses are `UIPress` events and are untouched.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
fun RokuTouchpad.attachSiriRemote(view: UIView): RokuSiriRemoteAttachment {
    pxPerUnit = UIScreen.mainScreen.scale.toFloat()
    val target = RokuPanTarget(this)
    retainedTargets += target
    val recognizer = UIPanGestureRecognizer(target = target, action = NSSelectorFromString("handlePan:"))
    recognizer.cancelsTouchesInView = true
    view.addGestureRecognizer(recognizer)
    return RokuSiriRemoteAttachment(view, recognizer, target)
}
