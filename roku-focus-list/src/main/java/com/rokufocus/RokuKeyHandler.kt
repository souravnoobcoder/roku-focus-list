package com.rokufocus

import android.os.SystemClock
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.rokuKeyHandler(
    state: RokuFocusListState,
    config: RokuFocusConfig,
    orientation: Orientation = Orientation.Horizontal,
    onSelected: ((Int) -> Unit)? = null,
    onClicked: ((Int) -> Unit)? = null,
    onBoundaryHit: (() -> Unit)? = null
): Modifier = composed {
    var lastKeyTime by remember { mutableLongStateOf(0L) }
    var consecutivePresses by remember { mutableIntStateOf(0) }

    onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

        val isHorizontal = orientation == Orientation.Horizontal
        val isForward = if (isHorizontal) keyEvent.key == Key.DirectionRight
            else keyEvent.key == Key.DirectionDown
        val isBackward = if (isHorizontal) keyEvent.key == Key.DirectionLeft
            else keyEvent.key == Key.DirectionUp

        when {
            isForward || isBackward -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastKeyTime > 300) consecutivePresses = 0

                val effectiveDelay = if (config.keyRepeatAccelAfter > 0 &&
                    consecutivePresses >= config.keyRepeatAccelAfter
                ) config.keyRepeatFastDelayMs else config.keyRepeatDelayMs

                if (now - lastKeyTime < effectiveDelay) return@onPreviewKeyEvent true

                val moved = if (isForward) {
                    if (config.wrapAround && !state.canScrollForward && state.itemCount > 1) {
                        state.scrollTo(0)
                        true
                    } else {
                        state.moveNext()
                    }
                } else {
                    if (config.wrapAround && !state.canScrollBackward && state.itemCount > 1) {
                        state.scrollTo(state.itemCount - 1)
                        true
                    } else {
                        state.movePrevious()
                    }
                }

                if (moved) {
                    lastKeyTime = now
                    consecutivePresses++
                    onSelected?.invoke(state.selectedIndex)
                } else {
                    onBoundaryHit?.invoke()
                }
                true
            }

            keyEvent.key == Key.Enter ||
                keyEvent.key == Key.DirectionCenter ||
                keyEvent.key == Key.NumPadEnter -> {
                if (state.itemCount > 0) {
                    onClicked?.invoke(state.selectedIndex)
                }
                true
            }

            // Do NOT consume perpendicular direction events (Up/Down for horizontal,
            // Left/Right for vertical) so focus can move between rows/columns.
            else -> false
        }
    }
}
