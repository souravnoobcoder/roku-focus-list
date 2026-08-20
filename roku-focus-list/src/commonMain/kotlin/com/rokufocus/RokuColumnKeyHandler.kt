package com.rokufocus

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Key handler for [RokuLazyColumn]. Handles D-pad navigation across both axes: UP/DOWN moves
 * between rows, skipping rows with nothing to select; LEFT/RIGHT and ENTER go to the active row —
 * to its item state for a card rail, or to its `onKeyEvent` for a custom row.
 *
 * Presses that cannot move anywhere are consumed or not according to
 * [RokuFocusConfig.focusEscape], per edge.
 */
internal fun Modifier.rokuColumnKeyHandler(
    rows: List<RokuResolvedRow>,
    state: RokuColumnState,
    config: RokuFocusConfig,
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)?,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)?
): Modifier = onPreviewKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    val now = RokuClock.uptimeMillis()
    val repeat = state.keyRepeat
    repeat.resetIfIdle(now)

    val rowIndex = state.selectedRowIndex
    val activeRow = rows.getOrNull(rowIndex) ?: return@onPreviewKeyEvent false
    val escape = config.focusEscape

    when (keyEvent.key) {
        // ── Vertical: move between rows, stepping over rows with nothing to select ──
        Key.DirectionUp, Key.DirectionDown -> {
            if (repeat.isThrottled(now, config)) return@onPreviewKeyEvent true
            val step = if (keyEvent.key == Key.DirectionUp) -1 else 1
            val target = nextSelectableRow(rows.size, rowIndex, step) { rows[it].isSelectable }
            if (target >= 0) {
                state.moveToRow(target)
                repeat.accept(now)
                onItemSelected?.invoke(target, rows.selectedItemIndexIn(target))
                true
            } else {
                !(if (step < 0) escape.up else escape.down)
            }
        }

        // ── Horizontal: delegate to the active row ──
        Key.DirectionLeft, Key.DirectionRight -> {
            if (repeat.isThrottled(now, config)) return@onPreviewKeyEvent true
            val forward = keyEvent.key == Key.DirectionRight
            val handled = when (activeRow) {
                is RokuResolvedRow.Items ->
                    moveWithinRow(activeRow.config.state, config, forward)

                is RokuResolvedRow.Custom ->
                    activeRow.onKeyEvent
                        ?.invoke(if (forward) RokuNavKey.Right else RokuNavKey.Left)
                        ?: false
            }
            if (handled) {
                repeat.accept(now)
                if (activeRow is RokuResolvedRow.Items) {
                    onItemSelected?.invoke(rowIndex, activeRow.config.state.selectedIndex)
                }
                true
            } else {
                !(if (forward) escape.end else escape.start)
            }
        }

        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> when (activeRow) {
            is RokuResolvedRow.Items -> if (activeRow.isSelectable) {
                onItemClicked?.invoke(rowIndex, activeRow.config.state.selectedIndex)
                true
            } else {
                // Nothing to activate: with no selectable row anywhere, selectedRowIndex is only a
                // coerced fallback. Left unconsumed to mirror the directional keys, which fall
                // through to focus escape at the same dead end — an enclosing screen keeps its
                // chance to act on Enter.
                false
            }

            is RokuResolvedRow.Custom ->
                activeRow.onKeyEvent?.invoke(RokuNavKey.Enter) ?: false
        }

        else -> false
    }
}

/** Item index selected in row [index], or 0 for a custom row that has no item concept. */
internal fun List<RokuResolvedRow>.selectedItemIndexIn(index: Int): Int =
    (getOrNull(index) as? RokuResolvedRow.Items)?.config?.state?.selectedIndex ?: 0
