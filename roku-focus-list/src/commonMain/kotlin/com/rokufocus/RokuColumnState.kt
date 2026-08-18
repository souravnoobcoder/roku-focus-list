package com.rokufocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Which row of a [RokuLazyColumn] is selected.
 *
 * Hoist it to read or drive the selection from outside the column — restoring a screen, reacting
 * to a deep link, or syncing with your own state holder:
 *
 * ```
 * val columnState = rememberRokuColumnState()
 * LaunchedEffect(deepLink) { columnState.selectedRowIndex = deepLink.rowIndex }
 * RokuLazyColumn(state = columnState) { /* rows */ }
 * ```
 *
 * ### Requested vs selected row
 *
 * [selectedRowIndex] is derived, not stored: it is [requestedRowIndex] resolved against the rows
 * that currently exist. Asking for row 5 while only one row has loaded is therefore remembered
 * rather than clamped away — the selection lands on row 5 as soon as the rows arrive. Every D-pad
 * move replaces the request, so ordinary navigation never snaps back to a stale target.
 *
 * Rows that are not selectable (an item row with no items) are skipped: [selectedRowIndex]
 * resolves to the nearest selectable row instead.
 *
 * @param initialRowIndex Row to select first. May exceed the eventual row count; see above.
 */
@Stable
class RokuColumnState(initialRowIndex: Int = 0) {

    private var _requestedRowIndex by mutableIntStateOf(initialRowIndex.coerceAtLeast(0))
    private var _rowCount by mutableIntStateOf(0)

    /**
     * Plain field, not snapshot state: it is re-assigned from composition on every pass, and the
     * observable values it reads (each row's item count) already invalidate the readers of
     * [selectedRowIndex].
     */
    private var rowSelectable: (Int) -> Boolean = { true }

    internal val keyRepeat = RokuKeyRepeatTracker()
    internal val focusRequester = FocusRequester()

    /** The last row anyone asked for, before resolution. Re-applied whenever rows are added. */
    val requestedRowIndex: Int get() = _requestedRowIndex

    /** How many rows the column currently renders. */
    val rowCount: Int get() = _rowCount

    /**
     * The row the highlight sits on. Always in range once rows exist, and always a selectable row
     * when the column has at least one. Assigning to it is the same as calling [moveToRow].
     */
    var selectedRowIndex: Int
        get() {
            val count = _rowCount
            if (count <= 0) return _requestedRowIndex
            val nearest = nearestSelectableRow(count, _requestedRowIndex, rowSelectable)
            return if (nearest >= 0) nearest else _requestedRowIndex.coerceIn(0, count - 1)
        }
        set(value) = moveToRow(value)

    /** True while any row of the column has selectable content. */
    val hasSelectableRow: Boolean
        get() = _rowCount > 0 && nearestSelectableRow(_rowCount, _requestedRowIndex, rowSelectable) >= 0

    /** True while the column holds platform focus. */
    var hasFocus by mutableStateOf(false)
        internal set

    /**
     * Selects [index], remembering it as the request even when the column is currently shorter or
     * that row has no items yet.
     */
    fun moveToRow(index: Int) {
        _requestedRowIndex = index.coerceAtLeast(0)
    }

    /**
     * Moves platform focus onto the column, so D-pad events start arriving. Call it when an
     * external component — a navigation pane, a button above the list — hands focus over.
     *
     * @return whether focus was taken. False when the column is not composed and laid out yet,
     *   or when the focus target refused it.
     */
    fun requestFocus(): Boolean = focusRequester.requestFocus()

    /** Called from composition with the rows the column is about to render. */
    internal fun syncRows(count: Int, isSelectable: (Int) -> Boolean) {
        rowSelectable = isSelectable
        if (_rowCount != count) _rowCount = count
    }

    companion object {
        /** Saves the requested row index, mirroring how `LazyListState.Saver` stores its indices. */
        val Saver: Saver<RokuColumnState, *> = listSaver(
            save = { listOf(it.requestedRowIndex) },
            restore = { RokuColumnState(initialRowIndex = it[0]) }
        )
    }
}

/**
 * Creates a [RokuColumnState] that survives configuration changes and back-stack restoration.
 *
 * @param initialRowIndex Row to select the first time the state is created.
 */
@Composable
fun rememberRokuColumnState(initialRowIndex: Int = 0): RokuColumnState =
    rememberSaveable(saver = RokuColumnState.Saver) {
        RokuColumnState(initialRowIndex = initialRowIndex)
    }
