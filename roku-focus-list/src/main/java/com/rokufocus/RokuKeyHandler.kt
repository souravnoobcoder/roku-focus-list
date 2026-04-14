package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.getValue
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

    onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

        val isHorizontal = orientation == Orientation.Horizontal
        val forwardKeys = if (isHorizontal) {
            setOf(Key.DirectionRight)
        } else {
            setOf(Key.DirectionDown)
        }
        val backwardKeys = if (isHorizontal) {
            setOf(Key.DirectionLeft)
        } else {
            setOf(Key.DirectionUp)
        }

        when {
            keyEvent.key in forwardKeys -> {
                val now = System.currentTimeMillis()
                if (now - lastKeyTime < config.keyRepeatDelayMs) return@onPreviewKeyEvent true

                val moved = if (config.wrapAround && !state.canScrollForward) {
                    state.scrollTo(0)
                    true
                } else {
                    state.moveNext()
                }

                if (moved) {
                    lastKeyTime = now
                    onSelected?.invoke(state.selectedIndex)
                } else {
                    onBoundaryHit?.invoke()
                }
                true // always consume navigation-axis events
            }

            keyEvent.key in backwardKeys -> {
                val now = System.currentTimeMillis()
                if (now - lastKeyTime < config.keyRepeatDelayMs) return@onPreviewKeyEvent true

                val moved = if (config.wrapAround && !state.canScrollBackward) {
                    state.scrollTo(state.itemCount - 1)
                    true
                } else {
                    state.movePrevious()
                }

                if (moved) {
                    lastKeyTime = now
                    onSelected?.invoke(state.selectedIndex)
                } else {
                    onBoundaryHit?.invoke()
                }
                true // always consume navigation-axis events
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
