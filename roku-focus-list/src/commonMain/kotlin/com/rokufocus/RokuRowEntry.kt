package com.rokufocus

import kotlin.math.floor

/**
 * Which card a vertical move lands on when it enters a row of [RokuLazyColumn].
 *
 * Only rows whose [RokuFocusListState.focusMode] is [RokuFocusMode.Floating] can tell the two
 * apart: a Static row scrolls its remembered card under the fixed slot, so the card above the
 * highlight *is* the remembered one either way.
 */
enum class RokuRowEntry {
    /**
     * The card physically under the highlight — the one whose frame contains the highlight's
     * centre, or the nearest one when none does — as the tvOS focus engine does. Going down and
     * back up lands on the card you were above, not the card that row last had. The row is never
     * scrolled sideways to make this happen; the highlight lands on whatever is already there.
     */
    Spatial,

    /** The card that row selected last, wherever it is on screen — the 2.x behaviour. */
    Remembered
}

/**
 * Which visible slot of a row lies under screen X [centreX], for a row whose first visible slot
 * starts at [windowLeftPx]. A card contains the point; a gap goes to whichever neighbouring card's
 * centre is nearer; beyond the last visible card, the last one.
 */
internal fun slotUnder(
    centreX: Float,
    windowLeftPx: Float,
    itemWidthPx: Float,
    stepPx: Float,
    visibleCount: Int
): Int {
    if (stepPx <= 0f || visibleCount <= 0) return 0
    val rel = centreX - windowLeftPx
    if (rel <= 0f) return 0
    var slot = floor(rel / stepPx).toInt()
    val within = rel - slot * stepPx
    if (within > itemWidthPx) {
        val toLeftCentre = within - itemWidthPx / 2f
        val toRightCentre = stepPx + itemWidthPx / 2f - within
        if (toRightCentre < toLeftCentre) slot++
    }
    return slot.coerceIn(0, visibleCount - 1)
}
