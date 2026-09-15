package com.rokufocus.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Entry point the Swift side calls as `MainViewControllerKt.MainViewController()`. The name comes
 * from this file's name, the convention JetBrains' Kotlin Multiplatform wizard uses.
 */
fun MainViewController(): UIViewController =
    ComposeUIViewController { WithTvDensity { SwipeSampleScreen() } }
        .also { installSiriRemotePan(it.view) }
