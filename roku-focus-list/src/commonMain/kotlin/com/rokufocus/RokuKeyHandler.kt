package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Low-level D-pad handler, for wiring a fixed-focus container of your own.
 *
 * Moves [state] along [orientation] with key-repeat throttling and acceleration, and leaves the
 * perpendicular directions unconsumed so focus can still travel between containers. A press that
 * cannot move the selection is consumed or not according to [RokuFocusConfig.focusEscape], which
 * is what lets focus leave the list at its edges.
 *
 * Key-repeat bookkeeping lives on [state], so applying this modifier does not itself need
 * composition.
 *
 * @param state Selection state to drive.
 * @param config Navigation behaviour, including per-edge focus escape.
 * @param orientation Which pair of direction keys moves the selection.
 * @param onSelected Called with the new index after every accepted move.
 * @param onClicked Called on Enter / D-pad center.
 * @param onBoundaryHit Called when a press could not move the selection.
 */
fun Modifier.rokuKeyHandler(
    state: RokuFocusListState,
    config: RokuFocusConfig,
    orientation: Orientation = Orientation.Horizontal,
    onSelected: ((Int) -> Unit)? = null,
    onClicked: ((Int) -> Unit)? = null,
    onBoundaryHit: (() -> Unit)? = null
): Modifier = onPreviewKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val isHorizontal = orientation == Orientation.Horizontal
    val isForward = if (isHorizontal) {
        keyEvent.key == Key.DirectionRight
    } else {
        keyEvent.key == Key.DirectionDown
    }
    val isBackward = if (isHorizontal) {
        keyEvent.key == Key.DirectionLeft
    } else {
        keyEvent.key == Key.DirectionUp
    }

    when {
        isForward || isBackward -> {
            val now = RokuClock.uptimeMillis()
            val repeat = state.keyRepeat
            repeat.resetIfIdle(now)
            if (repeat.isThrottled(now, config)) return@onPreviewKeyEvent true

            if (moveWithinRow(state, config, forward = isForward)) {
                repeat.accept(now)
                onSelected?.invoke(state.selectedIndex)
                true
            } else {
                onBoundaryHit?.invoke()
                !config.focusEscape.allowsLeaving(orientation, forward = isForward)
            }
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

/**
 * Applies one horizontal step to [state], honouring [RokuFocusConfig.wrapAround].
 *
 * @return whether the selection actually changed.
 */
internal fun moveWithinRow(
    state: RokuFocusListState,
    config: RokuFocusConfig,
    forward: Boolean
): Boolean {
    if (config.wrapAround && state.itemCount > 1) {
        if (forward && !state.canScrollForward) {
            state.scrollTo(0)
            return true
        }
        if (!forward && !state.canScrollBackward) {
            state.scrollTo(state.itemCount - 1)
            return true
        }
    }
    return if (forward) state.moveNext() else state.movePrevious()
}

/** Which edge a press ran into, and whether focus is allowed to leave through it. */
internal fun RokuFocusEscape.allowsLeaving(orientation: Orientation, forward: Boolean): Boolean =
    if (orientation == Orientation.Horizontal) {
        if (forward) end else start
    } else {
        if (forward) down else up
    }
