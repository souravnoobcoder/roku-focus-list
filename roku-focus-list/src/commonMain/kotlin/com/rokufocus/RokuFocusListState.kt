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
import kotlin.math.max

/**
 * Selection state for a single fixed-focus row.
 *
 * The state is a plain object with a public constructor, so it can be hoisted into your own state
 * holder and driven from outside composition. [rememberRokuFocusListState] is the convenience
 * factory that also survives configuration changes and back-stack restoration.
 *
 * ### Requested vs selected index
 *
 * [selectedIndex] is derived: it is [requestedIndex] coerced into the current item range. Setting
 * an index that is not valid *yet* is therefore remembered rather than lost — when items arrive
 * later and the range grows, the selection lands where it was asked to. Every move (D-pad,
 * [scrollTo], assigning [selectedIndex]) replaces the request, so ordinary navigation never snaps
 * back to a stale target.
 *
 * @param itemCount Number of items currently in the row.
 * @param initialIndex Index to select. May exceed [itemCount]; see above.
 * @param visibleCount How many items fit in the viewport. Overwritten by the composable that
 *   renders the row, which measures it.
 * @property focusSlot Which visible slot the highlight sits at (0 = leading edge). Only meaningful
 *   in [RokuFocusMode.Static]; a floating window has no fixed slot, so it is ignored there.
 * @property focusMode How the highlight relates to scrolling: parked at [focusSlot] while content
 *   scrolls ([RokuFocusMode.Static]), or walking the visible window and scrolling only at its
 *   edges ([RokuFocusMode.Floating]).
 */
@Stable
class RokuFocusListState(
    itemCount: Int,
    initialIndex: Int = 0,
    visibleCount: Int = 1,
    val focusSlot: Int = 0,
    val focusMode: RokuFocusMode = RokuFocusMode.Static
) {
    private var _requestedIndex by mutableIntStateOf(initialIndex.coerceAtLeast(0))
    private var _itemCount by mutableIntStateOf(itemCount.coerceAtLeast(0))
    private var _visibleCount by mutableIntStateOf(visibleCount)

    /**
     * Raw [RokuFocusMode.Floating] window anchor. Stored raw and bounds-clamped on read, like
     * [requestedIndex]: a window pushed out of range by a shrinking list comes back where it was
     * once the items return, because the clamp is never written back.
     */
    internal var windowAnchor by mutableIntStateOf(0)

    internal val keyRepeat = RokuKeyRepeatTracker()
    internal val focusRequester = FocusRequester()

    init {
        containWindow()
    }

    /** The last index anyone asked for, before coercion. Re-applied whenever the row grows. */
    val requestedIndex: Int get() = _requestedIndex

    /** The item the highlight sits on. Always in range, or 0 while the row is empty. */
    var selectedIndex: Int
        get() = _requestedIndex.coerceIn(0, max(0, _itemCount - 1))
        set(value) = scrollTo(value)

    val itemCount: Int get() = _itemCount

    /** How many items fit in the viewport. Auto-computed by [RokuLazyRow] / [RokuLazyColumn]. */
    var visibleCount: Int
        get() = _visibleCount
        internal set(value) {
            if (_visibleCount == value) return
            _visibleCount = value
            containWindow()
        }

    /**
     * True while this row renders as focused: it holds platform focus when used standalone, or it
     * is the active row of a focused [RokuLazyColumn].
     */
    var hasFocus by mutableStateOf(false)
        internal set

    val windowStart: Int
        get() = when (focusMode) {
            RokuFocusMode.Static -> {
                val ideal = selectedIndex - focusSlot
                ideal.coerceIn(0, max(0, _itemCount - _visibleCount))
            }

            RokuFocusMode.Floating ->
                windowAnchor.coerceIn(0, max(0, _itemCount - _visibleCount))
        }

    val highlightSlot: Int
        get() = (selectedIndex - windowStart).coerceIn(0, max(0, visibleCount - 1))

    val canScrollForward: Boolean
        get() = selectedIndex < _itemCount - 1

    val canScrollBackward: Boolean
        get() = selectedIndex > 0

    fun moveNext(): Boolean {
        if (!canScrollForward) return false
        scrollTo(selectedIndex + 1)
        return true
    }

    fun movePrevious(): Boolean {
        if (!canScrollBackward) return false
        scrollTo(selectedIndex - 1)
        return true
    }

    /** Selects [index], remembering it as the request even when the row is currently shorter. */
    fun scrollTo(index: Int) {
        _requestedIndex = index.coerceAtLeast(0)
        containWindow()
    }

    /**
     * Tells the state how many items the row now has. Selection is re-derived from
     * [requestedIndex], so a target that was out of range before becomes reachable once the row
     * grows to include it.
     */
    fun updateItemCount(newCount: Int) {
        val coerced = newCount.coerceAtLeast(0)
        if (_itemCount == coerced) return
        _itemCount = coerced
        containWindow()
    }

    /**
     * Keeps the floating window containing the selection. Runs from every write that can move the
     * selection relative to the window — [scrollTo], [updateItemCount], the [visibleCount] setter —
     * and shifts [windowAnchor] minimally: forward until the selection is last visible, backward
     * until it is first visible. Deliberately not in the [windowStart] getter: a read-side shift
     * that is never stored flip-flops, snapping back to the stale anchor on the next in-window
     * move. Comparing against the clamped [windowStart] rather than the raw anchor keeps a purely
     * data-driven shrink from overwriting the anchor the raw value still remembers.
     *
     * The callers guard on value equality first: [updateItemCount] and the [visibleCount] setter
     * run from composition on every pass, and the selection reads here would otherwise subscribe
     * that composition scope to every future selection change.
     */
    private fun containWindow() {
        if (focusMode != RokuFocusMode.Floating) return
        val selected = selectedIndex
        val start = windowStart
        when {
            selected < start -> windowAnchor = selected
            selected > start + _visibleCount - 1 -> windowAnchor = selected - _visibleCount + 1
        }
    }

    /**
     * Moves platform focus onto a **standalone** [RokuLazyRow] driven by this state.
     *
     * It does nothing for a row inside a [RokuLazyColumn]: the column is deliberately the only
     * focusable node there, so call [RokuColumnState.requestFocus] instead and set the row with
     * [RokuColumnState.moveToRow].
     *
     * @return whether focus was taken. False when the row is not composed and laid out yet, or
     *   when the focus target refused it.
     */
    fun requestFocus(): Boolean = focusRequester.requestFocus()

    companion object {
        /**
         * Saves the requested index, the focus slot, the focus mode and the floating window
         * anchor, mirroring how `LazyListState.Saver` stores its indices. Use it when hoisting a state into your own `rememberSaveable`.
         *
         * The item count is deliberately **not** saved: it describes the data, not the selection,
         * and a count restored from a previous run can easily exceed what the data source has this
         * time — which would hand out-of-range indices straight to your item lambda. A restored
         * state therefore comes back empty and renders nothing until you call [updateItemCount]
         * with the count you actually have. [rememberRokuFocusListState] does that for you.
         */
        val Saver: Saver<RokuFocusListState, *> = listSaver(
            save = { listOf(it.requestedIndex, it.focusSlot, it.focusMode.ordinal, it.windowAnchor) },
            restore = {
                RokuFocusListState(
                    itemCount = 0,
                    initialIndex = it[0],
                    focusSlot = it[1],
                    focusMode = RokuFocusMode.entries[it[2]]
                ).also { restored -> restored.windowAnchor = it[3] }
            }
        )
    }
}

/**
 * Creates a [RokuFocusListState] that survives configuration changes and back-stack restoration.
 *
 * @param itemCount Current number of items. Changes are pushed into the state, which re-derives
 *   the selection from the last requested index.
 * @param initialIndex Index to select the first time the state is created.
 * @param focusSlot Which visible slot the highlight sits at. Changing it recreates the state.
 *   Ignored in [RokuFocusMode.Floating].
 * @param focusMode How the highlight relates to scrolling; see [RokuFocusMode]. Changing it
 *   recreates the state.
 */
@Composable
fun rememberRokuFocusListState(
    itemCount: Int,
    initialIndex: Int = 0,
    focusSlot: Int = 0,
    focusMode: RokuFocusMode = RokuFocusMode.Static
): RokuFocusListState {
    // Pinning focusSlot and focusMode into the saver keeps a restored state on what the caller
    // asks for today: rememberSaveable compares `inputs` within a composition, but a value coming
    // back across process death was saved before those inputs existed.
    val saver = remember(focusSlot, focusMode) {
        listSaver<RokuFocusListState, Int>(
            save = { listOf(it.requestedIndex, it.windowAnchor) },
            restore = {
                RokuFocusListState(
                    itemCount = 0,
                    initialIndex = it[0],
                    focusSlot = focusSlot,
                    focusMode = focusMode
                ).also { restored -> restored.windowAnchor = it[1] }
            }
        )
    }

    val state = rememberSaveable(focusSlot, focusMode, saver = saver) {
        RokuFocusListState(
            itemCount = itemCount,
            initialIndex = initialIndex,
            focusSlot = focusSlot,
            focusMode = focusMode
        )
    }

    // Applied during composition rather than from an effect, so a restored or newly grown row is
    // never rendered against a stale count for one frame.
    state.updateItemCount(itemCount)

    return state
}

/**
 * Computes the pixel X offset for the focus highlight within a row,
 * accounting for scroll clamping at the end of the list.
 *
 * When the desired scroll (to place windowStart at the leading edge) exceeds
 * the LazyRow's max scroll, items shift right. The overflow corrects the highlight
 * to track the actual item position.
 */
internal fun computeHighlightOffsetPx(
    state: RokuFocusListState,
    itemWidthPx: Float,
    itemSpacingPx: Float,
    startPaddingPx: Float,
    endPaddingPx: Float,
    viewportWidthPx: Float
): Float {
    if (state.itemCount == 0) return startPaddingPx
    val stepPx = itemWidthPx + itemSpacingPx
    val totalContentPx = startPaddingPx +
        state.itemCount * itemWidthPx +
        max(0, state.itemCount - 1) * itemSpacingPx +
        endPaddingPx
    val maxScrollPx = (totalContentPx - viewportWidthPx).coerceAtLeast(0f)
    val desiredScrollPx = state.windowStart * stepPx
    val scrollOverflowPx = (desiredScrollPx - maxScrollPx).coerceAtLeast(0f)
    return startPaddingPx + scrollOverflowPx + state.highlightSlot * stepPx
}

