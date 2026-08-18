package com.rokufocus

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Immutable

/**
 * Receiver of the `focusHighlight` lambda.
 *
 * It extends [BoxScope] — the highlight is drawn in a box sized to the selected card, so
 * `Modifier.matchParentSize()` and `Modifier.align()` work as before — and adds where the
 * highlight currently is, so one lambda can render different treatments for different rows:
 *
 * ```
 * focusHighlight = { isFocused ->
 *     DefaultFocusHighlight(
 *         isFocused = isFocused,
 *         cornerRadius = if (rowIndex == AVATARS_ROW) 60.dp else 12.dp
 *     )
 * }
 * ```
 *
 * `isFocused` stays a parameter of the lambda rather than a member here, so highlight lambdas
 * written against 1.x keep working untouched.
 *
 * @property rowIndex Row the highlight sits on. Always 0 for a standalone [RokuLazyRow].
 * @property itemIndex Item within that row.
 */
@Immutable
interface RokuHighlightScope : BoxScope {
    val rowIndex: Int
    val itemIndex: Int
}

internal class RokuHighlightScopeImpl(
    boxScope: BoxScope,
    override val rowIndex: Int,
    override val itemIndex: Int
) : RokuHighlightScope, BoxScope by boxScope
