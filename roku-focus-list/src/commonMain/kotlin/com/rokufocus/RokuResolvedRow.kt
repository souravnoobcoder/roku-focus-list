package com.rokufocus

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * A D-pad key the column hands to a custom row while that row is selected.
 *
 * Deliberately a small enum rather than Compose's `Key`: it is the whole set a custom row can be
 * asked to handle, it reads the same on every platform, and it keeps
 * [RokuLazyColumnScope.customRow]'s contract testable without a key-event fixture.
 */
enum class RokuNavKey {
    /** LEFT in a left-to-right layout: move toward the start of the row. */
    Left,

    /** RIGHT in a left-to-right layout: move toward the end of the row. */
    Right,

    /** Enter / D-pad center: activate whatever the row currently has selected. */
    Enter
}

/**
 * Uniform view of the rows a [RokuLazyColumn] renders, so vertical navigation, scrolling and
 * highlight geometry do not care whether a row is a card rail or a consumer-drawn block.
 */
internal sealed class RokuResolvedRow {

    abstract val key: Any?

    /** Header height, which the highlight Y math adds on top of the row's own offset. */
    abstract val headerHeight: Dp

    /** Height of the row body, excluding [headerHeight]. */
    abstract val contentHeight: Dp

    /** Header rendered above the row. Null falls back to the column-level `rowHeader`. */
    abstract val header: (@Composable (isRowFocused: Boolean) -> Unit)?

    /** False for rows UP/DOWN must step over — an item row that currently has no items. */
    abstract val isSelectable: Boolean

    /** Whether the column's global highlight is drawn over this row. */
    abstract val showHighlight: Boolean

    class Items(
        override val key: Any?,
        override val header: (@Composable (isRowFocused: Boolean) -> Unit)?,
        val config: RokuColumnRowConfig,
        val itemKey: ((index: Int) -> Any)? = null,
        /**
         * True while an auto-sized row's first item or header has not reported its size yet.
         * Such a row behaves exactly like an empty one — skipped by UP/DOWN, zero geometry,
         * renders nothing — so the column never scrolls or highlights against made-up sizes.
         * Measurement lands within a frame and the row appears through the same machinery that
         * handles late-arriving rows.
         */
        val awaitingMeasure: Boolean = false
    ) : RokuResolvedRow() {
        override val headerHeight: Dp get() = config.headerHeight
        override val contentHeight: Dp get() = config.itemHeight
        override val isSelectable: Boolean get() = config.state.itemCount > 0 && !awaitingMeasure
        override val showHighlight: Boolean get() = true
    }

    class Custom(
        override val key: Any?,
        override val headerHeight: Dp,
        override val contentHeight: Dp,
        override val showHighlight: Boolean,
        override val header: (@Composable (isRowFocused: Boolean) -> Unit)?,
        val onKeyEvent: ((RokuNavKey) -> Boolean)?,
        val content: @Composable (isRowFocused: Boolean) -> Unit
    ) : RokuResolvedRow() {
        override val isSelectable: Boolean get() = true
    }
}
