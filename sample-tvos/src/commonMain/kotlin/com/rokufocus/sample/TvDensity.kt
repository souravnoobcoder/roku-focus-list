package com.rokufocus.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density

/**
 * Lays the sample out on a 960 dp wide canvas whatever density the platform reports.
 *
 * The Compose tvOS fork this build runs on squares the UIKit screen scale, which on a 1x Apple TV
 * HD is a density of 1.0: a 1920×1080 **dp** canvas, every card half the size it has on an Android
 * TV at 1080p (density 2.0, 960×540 dp), and about twice as many cards and rows on screen to lay
 * out and draw per frame. The README documents the ~28 fps that cost on real Apple TV HD hardware.
 * Pinning the design width puts every TV at the Android TV canvas and halves the per-frame work.
 *
 * Font scale is carried through untouched: it is the viewer's accessibility setting.
 */
@Composable
internal fun WithTvDensity(content: @Composable () -> Unit) {
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val platformDensity = LocalDensity.current

    // Zero for the first frame or two, before the window is measured.
    if (containerWidthPx < TvSurfaceMinWidthPx) {
        content()
        return
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = containerWidthPx / TvDesignWidthDp,
            fontScale = platformDensity.fontScale,
        ),
        content = content,
    )
}

private const val TvDesignWidthDp = 960f

/** Narrowest container treated as a TV; below this the platform density is left alone. */
private const val TvSurfaceMinWidthPx = 1280
