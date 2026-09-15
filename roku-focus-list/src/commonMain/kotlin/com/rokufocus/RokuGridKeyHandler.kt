package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Key handler for [RokuFocusGrid]: LEFT/RIGHT along the row, UP/DOWN by whole rows, Enter to
 * click. Same throttle and acceleration as the other handlers, and the same per-edge escape.
 */
internal fun Modifier.rokuGridKeyHandler(
    state: RokuGridState,
    config: RokuFocusConfig,
    onSelected: ((index: Int) -> Unit)?,
    onClicked: ((index: Int) -> Unit)?,
    onBoundaryHit: (() -> Unit)?
): Modifier = onPreviewKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val now = RokuClock.uptimeMillis()
    val repeat = state.keyRepeat
    repeat.resetIfIdle(now)

    fun step(orientation: Orientation, forward: Boolean): Boolean {
        if (repeat.isThrottled(now, config)) return true
        val direction = if (forward) 1 else -1
        val moved = if (orientation == Orientation.Horizontal) {
            state.moveColumnSteps(direction, config.wrapAround) != 0
        } else {
            state.moveRowSteps(direction, config.wrapAround) != 0
        }
        return if (moved) {
            repeat.accept(now)
            onSelected?.invoke(state.selectedIndex)
            true
        } else {
            onBoundaryHit?.invoke()
            !config.focusEscape.allowsLeaving(orientation, forward)
        }
    }

    when (keyEvent.key) {
        Key.DirectionRight -> step(Orientation.Horizontal, forward = true)
        Key.DirectionLeft -> step(Orientation.Horizontal, forward = false)
        Key.DirectionDown -> step(Orientation.Vertical, forward = true)
        Key.DirectionUp -> step(Orientation.Vertical, forward = false)

        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
            if (state.itemCount > 0) onClicked?.invoke(state.selectedIndex)
            true
        }

        else -> false
    }
}
