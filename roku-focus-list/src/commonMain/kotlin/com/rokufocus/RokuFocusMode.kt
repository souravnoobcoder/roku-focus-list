package com.rokufocus

/**
 * How the focus highlight relates to scrolling along one axis.
 *
 * The two axes are independent: a [RokuLazyColumn] can move vertically in one mode while its rows
 * move horizontally in the other. Horizontal mode lives on [RokuFocusListState]; vertical mode is
 * the `verticalFocusMode` parameter of [RokuLazyColumn].
 */
enum class RokuFocusMode {
    /**
     * Roku-style fixed focus: the highlight stays parked at a fixed slot
     * ([RokuFocusListState.focusSlot], or the top row of the column) and the content scrolls
     * behind it on every selection change.
     */
    Static,

    /**
     * Leanback-style floating focus: the scroll window stays put and the highlight walks across
     * the visible items or rows. The list only scrolls when the selection would leave the visible
     * window, and then only by the minimum needed to keep it visible.
     */
    Floating
}
