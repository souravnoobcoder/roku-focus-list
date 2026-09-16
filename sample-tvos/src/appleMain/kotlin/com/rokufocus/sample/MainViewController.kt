package com.rokufocus.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.rokufocus.LocalRokuTouchpad
import com.rokufocus.RokuTouchpad
import com.rokufocus.attachSiriRemote
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController

/**
 * Entry point the Swift side calls as `MainViewControllerKt.MainViewController()`. The name comes
 * from this file's name, the convention JetBrains' Kotlin Multiplatform wizard uses.
 *
 * This is all a tvOS consumer has to do for the Siri Remote touchpad: one [RokuTouchpad] provided
 * to the tree, attached to the Compose host view. Every row, column and grid below picks it up.
 */
@OptIn(ExperimentalForeignApi::class)
fun MainViewController(): UIViewController {
    val screen = UIScreen.mainScreen
    TvDisplay.refreshHz = screen.maximumFramesPerSecond.toInt()
    println(
        "[roku] display scale=${screen.scale} maxFps=${TvDisplay.refreshHz} " +
            "points=${screen.bounds.useContents { "${size.width.toInt()}x${size.height.toInt()}" }}"
    )

    val touchpad = RokuTouchpad()
    return ComposeUIViewController {
        CompositionLocalProvider(LocalRokuTouchpad provides touchpad) {
            WithTvDensity { SwipeSampleScreen() }
        }
    }.also { touchpad.attachSiriRemote(it.view) }
}
