package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlin.math.abs

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
 * Applies one velocity-scaled swipe to a row, as a single coalesced move, and reports whether the
 * gesture was consumed.
 *
 * This is the gesture counterpart to [Modifier.rokuKeyHandler], and the reason it exists rather
 * than leaving consumers to call [RokuFocusListState.moveBy] themselves is the edge policy: a
 * multi-step move that runs out of row has to consume what it can and then apply
 * [RokuFocusConfig.focusEscape] **once**, never once per step. Wire a platform gesture to it by
 * turning the gesture's velocity into a step count with [stepsForVelocity] and negating it for a
 * backward swipe:
 *
 * ```
 * val steps = config.stepsForVelocity(velocity)
 * val consumed = rokuMoveBy(state, config, if (forward) steps else -steps, onSelected = ::onSelect)
 * ```
 *
 * The library never touches the platform's gesture APIs itself — translating a swipe into a
 * velocity is the host's job, and keeping it that way is what lets this work identically on tvOS,
 * Android TV, desktop and the web.
 *
 * @param steps How far to move; negative travels toward the start. 0 does nothing.
 * @param orientation Which pair of [RokuFocusEscape] edges a clipped move is judged against.
 * @param onSelected Called at most once, with the new index, when the selection changed.
 * @param onBoundaryHit Called when the swipe could not move at all.
 * @return whether the gesture was consumed. False means the move ran into an edge that
 *   [RokuFocusConfig.focusEscape] leaves open, so the host should let focus travel onward.
 */
fun rokuMoveBy(
    state: RokuFocusListState,
    config: RokuFocusConfig,
    steps: Int,
    orientation: Orientation = Orientation.Horizontal,
    onSelected: ((index: Int) -> Unit)? = null,
    onBoundaryHit: (() -> Unit)? = null
): Boolean {
    if (steps == 0) return false
    state.keyRepeat.reset()

    val consumed = state.moveSteps(steps, config.wrapAround)
    if (consumed != 0) onSelected?.invoke(state.selectedIndex)
    if (consumed == abs(steps)) return true

    // Clipped — the row ran out. Everything below happens exactly once, however many steps were
    // asked for.
    if (consumed == 0) onBoundaryHit?.invoke()
    return !config.focusEscape.allowsLeaving(orientation, forward = steps > 0)
}

/**
 * Applies one velocity-scaled swipe to a column, as a single coalesced move, and reports whether
 * the gesture was consumed. The vertical sibling of [rokuMoveBy]; rows with nothing to select are
 * stepped over and do not count toward [steps].
 *
 * @return whether the gesture was consumed. False means the move ran into the top or bottom edge
 *   and [RokuFocusConfig.focusEscape] leaves it open.
 */
fun rokuMoveRowsBy(
    state: RokuColumnState,
    config: RokuFocusConfig,
    steps: Int,
    onSelected: ((rowIndex: Int) -> Unit)? = null,
    onBoundaryHit: (() -> Unit)? = null
): Boolean {
    if (steps == 0) return false
    state.keyRepeat.reset()

    val consumed = state.moveRowSteps(steps, config.wrapAround)
    if (consumed != 0) onSelected?.invoke(state.selectedRowIndex)
    if (consumed == abs(steps)) return true

    if (consumed == 0) onBoundaryHit?.invoke()
    return !config.focusEscape.allowsLeaving(Orientation.Vertical, forward = steps > 0)
}

/**
 * Applies one velocity-scaled swipe **within the active row of a column**, as a single coalesced
 * move, for hosts that only hold the column's state — the `row { }` DSL never hands out its rows'
 * states. Resolves [RokuColumnState.activeRowState] and behaves exactly like [rokuMoveBy] on it,
 * escape policy included, so a consumer can wire horizontal swipes the same way for either
 * [RokuLazyColumn] overload.
 *
 * @param onSelected Called at most once, with the row and the new item index, when the selection
 *   changed.
 * @return whether the gesture was consumed. False when the column has no active item row, or when
 *   the move ran into a start/end edge that [RokuFocusConfig.focusEscape] leaves open.
 */
fun rokuMoveItemsBy(
    state: RokuColumnState,
    config: RokuFocusConfig,
    steps: Int,
    onSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onBoundaryHit: (() -> Unit)? = null
): Boolean {
    if (steps == 0) return false
    val row = state.activeRowState ?: return false
    state.keyRepeat.reset()
    val rowIndex = state.selectedRowIndex
    return rokuMoveBy(
        state = row,
        config = config,
        steps = steps,
        orientation = Orientation.Horizontal,
        onSelected = onSelected?.let { report -> { itemIndex -> report(rowIndex, itemIndex) } },
        onBoundaryHit = onBoundaryHit
    )
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
    // Deliberately routed through the shared core rather than moveBy, which resets the key-repeat
    // streak: this IS the key-repeat path, and resetting here would stop acceleration ever
    // engaging.
    return state.moveSteps(if (forward) 1 else -1, config.wrapAround) != 0
}

/** Which edge a press ran into, and whether focus is allowed to leave through it. */
internal fun RokuFocusEscape.allowsLeaving(orientation: Orientation, forward: Boolean): Boolean =
    if (orientation == Orientation.Horizontal) {
        if (forward) end else start
    } else {
        if (forward) down else up
    }
