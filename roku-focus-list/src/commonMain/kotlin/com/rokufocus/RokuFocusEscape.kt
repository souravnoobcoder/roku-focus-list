package com.rokufocus

import androidx.compose.runtime.Immutable

/**
 * Per-edge control over what happens when a D-pad press cannot move the selection any further.
 *
 * When an edge is `true` the key event is left unconsumed, so the platform focus system moves
 * focus to whatever composable sits beyond that edge. When it is `false` the event is swallowed
 * and focus stays inside the list.
 *
 * Real layouts need this asymmetry — for example escaping toward a navigation pane on the start
 * edge while consuming Up on the first row so focus never falls off the top of the screen:
 *
 * ```
 * RokuFocusConfig(
 *     focusEscape = RokuFocusEscape(start = true, end = false, up = false, down = false)
 * )
 * ```
 *
 * @property start Escape past the first item of a row. LEFT in a left-to-right layout.
 * @property end Escape past the last item of a row. RIGHT in a left-to-right layout.
 * @property up Escape above the first row.
 * @property down Escape below the last row.
 */
@Immutable
data class RokuFocusEscape(
    val start: Boolean = true,
    val end: Boolean = true,
    val up: Boolean = true,
    val down: Boolean = true
) {
    companion object {
        /** Focus may leave the list at every edge. This is the default. */
        val All: RokuFocusEscape = RokuFocusEscape()

        /** Focus is trapped inside the list; every edge press is swallowed. */
        val None: RokuFocusEscape = RokuFocusEscape(
            start = false,
            end = false,
            up = false,
            down = false
        )

        /** Focus may leave sideways only — useful next to a start-aligned navigation pane. */
        val Horizontal: RokuFocusEscape = RokuFocusEscape(
            start = true,
            end = true,
            up = false,
            down = false
        )

        /** Focus may leave vertically only — useful between a top bar and a bottom bar. */
        val Vertical: RokuFocusEscape = RokuFocusEscape(
            start = false,
            end = false,
            up = true,
            down = true
        )
    }
}
