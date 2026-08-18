package com.rokufocus

/**
 * First selectable row strictly beyond [from] in direction [step] (+1 down, -1 up), or -1 when
 * there is none. Rows that report themselves unselectable — an item row that currently has no
 * items — are stepped over so the highlight never parks on nothing.
 */
internal fun nextSelectableRow(
    count: Int,
    from: Int,
    step: Int,
    isSelectable: (Int) -> Boolean
): Int {
    if (count <= 0 || step == 0) return -1
    var index = from + step
    while (index in 0 until count) {
        if (isSelectable(index)) return index
        index += step
    }
    return -1
}

/**
 * The selectable row closest to [target], preferring [target] itself, then the row below, then the
 * row above, and so on. Returns -1 when no row is selectable at all.
 *
 * [target] is clamped into range first, so asking for a row past the end resolves to the last
 * selectable row rather than to nothing.
 */
internal fun nearestSelectableRow(
    count: Int,
    target: Int,
    isSelectable: (Int) -> Boolean
): Int {
    if (count <= 0) return -1
    val clamped = target.coerceIn(0, count - 1)
    if (isSelectable(clamped)) return clamped

    var forward = clamped + 1
    var backward = clamped - 1
    while (forward < count || backward >= 0) {
        if (forward < count) {
            if (isSelectable(forward)) return forward
            forward++
        }
        if (backward >= 0) {
            if (isSelectable(backward)) return backward
            backward--
        }
    }
    return -1
}
