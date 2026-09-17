package com.rokufocus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import kotlin.math.abs
import kotlin.math.max

/**
 * Selection state for a [RokuFocusGrid]: a wall of equal-size cells, [columns] wide, scrolling
 * vertically.
 *
 * The selection is one linear index in reading order; [selectedRow] and [selectedColumn] are
 * derived from it. LEFT/RIGHT move along the row and stop at its ends (or, with `wrapAround`, flow
 * into the neighbouring row like reading); UP/DOWN move by whole rows keeping the column, and a
 * shorter last row hands out its last cell.
 *
 * It follows the same two rules as [RokuFocusListState]: [selectedIndex] is **derived** from the
 * raw [requestedIndex] coerced into range, so a target the grid cannot reach yet is honoured once
 * the items arrive; and the vertical window is a raw anchor row, contained at write time and
 * clamped on read, so a shrunken grid gives the window back when its rows return.
 *
 * @param itemCount Number of cells currently in the grid.
 * @param columns Cells per row. At least 1.
 * @param initialIndex Cell to select. May exceed [itemCount]; see above.
 * @param visibleRows How many rows fit in the viewport. Overwritten by the composable, which
 *   measures it.
 * @property focusMode How the highlight relates to vertical scrolling. **Defaults to
 *   [RokuFocusMode.Floating]**: the highlight walks the visible rows and the grid scrolls only when
 *   the selection would leave them — the way a wall of posters is browsed. [RokuFocusMode.Static]
 *   parks the selected row at the top and scrolls on every row move instead. Horizontal moves
 *   never scroll: a row is always fully on screen.
 */
@Stable
class RokuGridState(
    itemCount: Int,
    columns: Int,
    initialIndex: Int = 0,
    visibleRows: Int = 1,
    val focusMode: RokuFocusMode = RokuFocusMode.Floating
) {
    private var _requestedIndex by mutableIntStateOf(initialIndex.coerceAtLeast(0))
    private var _itemCount by mutableIntStateOf(itemCount.coerceAtLeast(0))
    private var _columns by mutableIntStateOf(columns.coerceAtLeast(1))
    private var _visibleRows by mutableIntStateOf(visibleRows.coerceAtLeast(1))

    /**
     * Whether [visibleRows] is a measured viewport rather than the constructor's placeholder.
     * Same rule, and the same defect, as `RokuFocusListState.visibleCountMeasured`: containing a
     * floating window against an unmeasured viewport of one row collapses the anchor onto the
     * selected row, and a grid restored on row 4 then scrolls row 4 to the top.
     */
    private var visibleRowsMeasured = visibleRows > 1

    /** Whether the composable has reported a viewport yet — see `RokuFocusListState`. */
    internal val viewportMeasured: Boolean get() = visibleRowsMeasured

    /** Raw floating window anchor, in rows. Stored raw, clamped on read — see [windowStartRow]. */
    internal var windowAnchorRow by mutableIntStateOf(0)

    internal val keyRepeat = RokuKeyRepeatTracker()
    internal val focusRequester = FocusRequester()

    init {
        containWindow()
    }

    /** The last index anyone asked for, before coercion. Re-applied whenever the grid grows. */
    val requestedIndex: Int get() = _requestedIndex

    /** The cell the highlight sits on. Always in range, or 0 while the grid is empty. */
    var selectedIndex: Int
        get() = _requestedIndex.coerceIn(0, max(0, _itemCount - 1))
        set(value) = scrollTo(value)

    val itemCount: Int get() = _itemCount

    val columns: Int get() = _columns

    /** Rows needed to hold every cell; the last one may be partial. */
    val rowCount: Int get() = (_itemCount + _columns - 1) / _columns

    val selectedRow: Int get() = selectedIndex / _columns

    val selectedColumn: Int get() = selectedIndex % _columns

    /** How many rows fit in the viewport. Auto-computed by [RokuFocusGrid]. */
    var visibleRows: Int
        get() = _visibleRows
        internal set(value) {
            val rows = value.coerceAtLeast(1)
            // The first report always contains, even when it equals the placeholder.
            val firstReport = !visibleRowsMeasured
            visibleRowsMeasured = true
            if (_visibleRows == rows && !firstReport) return
            _visibleRows = rows
            containWindow()
        }

    /** True while the grid holds platform focus. */
    var hasFocus by mutableStateOf(false)
        internal set

    /** First row the viewport shows. Static: the selected row, clamped at the end. Floating: the anchor. */
    val windowStartRow: Int
        get() {
            val maxStart = max(0, rowCount - _visibleRows)
            return when (focusMode) {
                RokuFocusMode.Static -> selectedRow.coerceIn(0, maxStart)
                RokuFocusMode.Floating -> windowAnchorRow.coerceIn(0, maxStart)
            }
        }

    /** Which visible row the highlight sits on. */
    val highlightRowSlot: Int
        get() = (selectedRow - windowStartRow).coerceIn(0, max(0, _visibleRows - 1))

    /** Steps one cell to the right within the row. Same as `moveColumnsBy(1)`. */
    fun moveNext(): Boolean = moveColumnSteps(1, wrapAround = false) != 0

    /** Steps one cell to the left within the row. Same as `moveColumnsBy(-1)`. */
    fun movePrevious(): Boolean = moveColumnSteps(-1, wrapAround = false) != 0

    /**
     * Moves [steps] cells along the current row as **one logical move**, clamping at the row's
     * ends. With [wrapAround] the move flows into the neighbouring rows in reading order instead,
     * and wraps from the grid's last cell to its first (and back) only when already parked there.
     * Resets the key-repeat streak; see [RokuFocusListState.moveBy] for why.
     *
     * @return whether the selection changed. `moveColumnsBy(0)` is a no-op returning false.
     */
    fun moveColumnsBy(steps: Int, wrapAround: Boolean = false): Boolean {
        if (steps == 0) return false
        keyRepeat.reset()
        return moveColumnSteps(steps, wrapAround) != 0
    }

    /**
     * Moves [steps] rows up or down as **one logical move**, keeping the column; a shorter last
     * row hands out its last cell. Clamps at the first and last row, or with [wrapAround] wraps
     * from one to the other when already parked there. Resets the key-repeat streak.
     *
     * @return whether the selection changed. `moveRowsBy(0)` is a no-op returning false.
     */
    fun moveRowsBy(steps: Int, wrapAround: Boolean = false): Boolean {
        if (steps == 0) return false
        keyRepeat.reset()
        return moveRowSteps(steps, wrapAround) != 0
    }

    /**
     * Moves [steps] cells in reading order — across row ends — as **one logical move**, clamping
     * at the grid's first and last cell, or with [wrapAround] wrapping when already parked there.
     * Resets the key-repeat streak.
     */
    fun moveBy(steps: Int, wrapAround: Boolean = false): Boolean {
        if (steps == 0) return false
        keyRepeat.reset()
        return moveLinearSteps(steps, wrapAround) != 0
    }

    /** Shared core of the horizontal moves, returning how many cells were actually covered. */
    internal fun moveColumnSteps(steps: Int, wrapAround: Boolean): Int {
        if (steps == 0 || _itemCount == 0) return 0
        if (wrapAround) return moveLinearSteps(steps, wrapAround = true)
        val current = selectedIndex
        val first = selectedRow * _columns
        val last = minOf(first + _columns - 1, _itemCount - 1)
        val target = (current + steps).coerceIn(first, last)
        if (target == current) return 0
        scrollTo(target)
        return abs(target - current)
    }

    /** Shared core of the vertical moves, returning how many rows were actually covered. */
    internal fun moveRowSteps(steps: Int, wrapAround: Boolean): Int {
        if (steps == 0 || _itemCount == 0) return 0
        val lastRow = rowCount - 1
        val row = selectedRow
        val column = selectedColumn

        if (wrapAround && lastRow > 0) {
            if (steps > 0 && row == lastRow) {
                scrollTo(cellAt(0, column))
                return abs(steps)
            }
            if (steps < 0 && row == 0) {
                scrollTo(cellAt(lastRow, column))
                return abs(steps)
            }
        }

        val targetRow = (row + steps).coerceIn(0, lastRow)
        if (targetRow == row) return 0
        scrollTo(cellAt(targetRow, column))
        return abs(targetRow - row)
    }

    internal fun moveLinearSteps(steps: Int, wrapAround: Boolean): Int {
        if (steps == 0 || _itemCount == 0) return 0
        val current = selectedIndex
        val last = _itemCount - 1

        if (wrapAround && _itemCount > 1) {
            if (steps > 0 && current == last) {
                scrollTo(0)
                return abs(steps)
            }
            if (steps < 0 && current == 0) {
                scrollTo(last)
                return abs(steps)
            }
        }

        val target = (current + steps).coerceIn(0, last)
        if (target == current) return 0
        scrollTo(target)
        return abs(target - current)
    }

    /** The cell at [row] / [column], or the row's last cell when the row is shorter than that. */
    private fun cellAt(row: Int, column: Int): Int = minOf(row * _columns + column, _itemCount - 1)

    /** Selects [index], remembering it as the request even when the grid is currently smaller. */
    fun scrollTo(index: Int) {
        _requestedIndex = index.coerceAtLeast(0)
        containWindow()
    }

    /** Tells the state how many cells the grid now has; the selection is re-derived. */
    fun updateItemCount(newCount: Int) {
        val coerced = newCount.coerceAtLeast(0)
        if (_itemCount == coerced) return
        _itemCount = coerced
        containWindow()
    }

    /** Tells the state how many cells make a row. The selected cell keeps its index, not its column. */
    fun updateColumns(columns: Int) {
        val coerced = columns.coerceAtLeast(1)
        if (_columns == coerced) return
        _columns = coerced
        containWindow()
    }

    /**
     * Keeps the floating window containing the selected row, shifting the anchor minimally. Run
     * from every write that can move the selection relative to the window, never from the
     * [windowStartRow] getter — see [RokuFocusListState] for the reasoning.
     */
    private fun containWindow() {
        if (focusMode != RokuFocusMode.Floating) return
        if (!visibleRowsMeasured) return
        val row = selectedRow
        val start = windowStartRow
        when {
            row < start -> windowAnchorRow = row
            row > start + _visibleRows - 1 -> windowAnchorRow = row - _visibleRows + 1
        }
    }

    /**
     * Moves platform focus onto the [RokuFocusGrid] driven by this state.
     *
     * @return whether focus was taken. False when the grid is not composed and laid out yet.
     */
    fun requestFocus(): Boolean = focusRequester.requestFocus()

    companion object {
        /**
         * Saves the requested index, the column count, the focus mode and the window anchor row.
         * The item count is deliberately not saved, for the reason [RokuFocusListState.Saver]
         * gives: it describes the data, not the selection.
         */
        val Saver: Saver<RokuGridState, *> = listSaver(
            save = { listOf(it.requestedIndex, it.columns, it.focusMode.ordinal, it.windowAnchorRow) },
            restore = {
                RokuGridState(
                    itemCount = 0,
                    columns = it[1],
                    initialIndex = it[0],
                    focusMode = RokuFocusMode.entries[it[2]]
                ).also { restored -> restored.windowAnchorRow = it[3] }
            }
        )
    }
}

/**
 * Creates a [RokuGridState] that survives configuration changes and back-stack restoration.
 *
 * @param itemCount Current number of cells. Changes are pushed into the state.
 * @param columns Cells per row. Changes are pushed into the state; the selected cell keeps its
 *   index.
 * @param initialIndex Cell to select the first time the state is created.
 * @param focusMode Vertical focus behaviour. Defaults to [RokuFocusMode.Floating]. Changing it
 *   recreates the state.
 */
@Composable
fun rememberRokuGridState(
    itemCount: Int,
    columns: Int,
    initialIndex: Int = 0,
    focusMode: RokuFocusMode = RokuFocusMode.Floating
): RokuGridState {
    val saver = remember(focusMode) {
        listSaver<RokuGridState, Int>(
            save = { listOf(it.requestedIndex, it.windowAnchorRow) },
            restore = {
                RokuGridState(itemCount = 0, columns = 1, initialIndex = it[0], focusMode = focusMode)
                    .also { restored -> restored.windowAnchorRow = it[1] }
            }
        )
    }

    val state = rememberSaveable(focusMode, saver = saver) {
        RokuGridState(itemCount = itemCount, columns = columns, initialIndex = initialIndex, focusMode = focusMode)
    }

    // Columns first, so the count is contained against the right row shape.
    state.updateColumns(columns)
    state.updateItemCount(itemCount)

    return state
}
