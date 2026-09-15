package com.rokufocus.sample

import com.rokufocus.RokuColumnState
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuFocusListState
import com.rokufocus.rokuMoveBy
import com.rokufocus.rokuMoveItemsBy
import com.rokufocus.rokuMoveRowsBy

/**
 * What a touchpad gesture can do to a layout, so [RemoteNavigator] is the same object whether the
 * screen is a [com.rokufocus.RokuLazyColumn] or a lone [com.rokufocus.RokuLazyRow]. Both methods
 * return whether the selection actually changed, which is how the navigator knows it hit an edge.
 */
internal interface SwipeTarget {
    fun moveItems(steps: Int): Boolean
    fun moveRows(steps: Int): Boolean
}

/**
 * A column of rails, either overload. Horizontal moves go through the column's active row, so the
 * host never needs the rows' own states — the `row { }` DSL does not hand them out anyway.
 */
internal class ColumnSwipeTarget(
    private val state: RokuColumnState,
    private val config: RokuFocusConfig,
) : SwipeTarget {

    override fun moveItems(steps: Int): Boolean {
        var moved = false
        rokuMoveItemsBy(state, config, steps, onSelected = { _, _ -> moved = true })
        return moved
    }

    override fun moveRows(steps: Int): Boolean {
        var moved = false
        rokuMoveRowsBy(state, config, steps, onSelected = { moved = true })
        return moved
    }
}

/** A single standalone rail. Vertical swipes have nowhere to go. */
internal class RowSwipeTarget(
    private val state: RokuFocusListState,
    private val config: RokuFocusConfig,
) : SwipeTarget {

    override fun moveItems(steps: Int): Boolean {
        var moved = false
        rokuMoveBy(state, config, steps, onSelected = { moved = true })
        return moved
    }

    override fun moveRows(steps: Int): Boolean = false
}
