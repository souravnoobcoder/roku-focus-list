package com.rokufocus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import kotlin.math.abs

/**
 * Scrolls a lazy list to an absolute pixel offset with one spring whose **velocity survives
 * retargeting**.
 *
 * `LazyListState.animateScrollToItem` builds a fresh `AnimationState(0f)` on every call, so when a
 * second move interrupts the first — a key repeat, or the run of single steps a touchpad swipe
 * produces — the content stops dead and eases in again from rest. At the 60–200 ms cadence of a
 * swipe that reads as a pulse per item rather than one continuous scroll. This keeps a single
 * [AnimationState] across calls and hands its last velocity to the next spring, so a retarget bends
 * the motion instead of restarting it — the same additive behaviour UIKit scroll views have had
 * since iOS 8, and the reason native tvOS rails stay smooth under a flick.
 *
 * The distance driven per frame is what `scrollBy` actually consumed, so the model never drifts
 * from the list, and hitting the end of the list stops the spring instead of pushing on it.
 *
 * Far jumps (more than a viewport away) and lists without a layout yet still go through
 * `animateScrollToItem`: its teleporting keeps a jump across a long rail from composing every item
 * in between, and only a fresh spring can start from an unmeasured list.
 */
internal class RokuScrollAnimator {
    private var animation = AnimationState(0f)

    /** Forgets the carried velocity, for the code paths that snap or teleport instead. */
    fun reset() {
        animation = AnimationState(0f)
    }

    /**
     * @param index The item [targetPx] aligns to; used when falling back to `animateScrollToItem`.
     * @param currentPx Absolute scroll offset of the list right now.
     * @param targetPx Absolute scroll offset to reach, already clamped to what the list can scroll.
     * @param viewportPx Main-axis size of the viewport; 0 when the list has not been laid out yet.
     */
    suspend fun scrollToIndex(
        listState: LazyListState,
        index: Int,
        currentPx: Float,
        targetPx: Float,
        viewportPx: Float
    ) {
        if (viewportPx <= 0f || abs(targetPx - currentPx) > viewportPx) {
            reset()
            listState.animateScrollToItem(index)
            return
        }

        animation = animation.copy(value = currentPx)
        val forward = targetPx >= currentPx
        var scrolled = currentPx
        listState.scroll {
            animation.animateTo(
                targetValue = targetPx,
                animationSpec = ScrollSpec,
                sequentialAnimation = animation.velocity != 0f
            ) {
                // A spring carrying velocity can overshoot; the list is clamped to the segment.
                val clamped = if (forward) value.coerceAtMost(targetPx) else value.coerceAtLeast(targetPx)
                val consumed = scrollBy(clamped - scrolled)
                scrolled += consumed
                if (consumed == 0f && abs(clamped - scrolled) >= EndOfListTolerancePx) cancelAnimation()
            }
        }
    }
}

/** Same spec `animateScrollToItem` uses, so a lone D-pad step is timed exactly as before. */
private val ScrollSpec: AnimationSpec<Float> = spring()

private const val EndOfListTolerancePx = 0.5f

/**
 * Where a lazy list of equal-size items sits, in absolute pixels from the very start of its
 * content, derived from the first visible item. Items are [stepPx] apart, spacing included.
 */
internal fun LazyListState.absoluteOffsetPx(stepPx: Float): Float =
    firstVisibleItemIndex * stepPx + firstVisibleItemScrollOffset

/**
 * The absolute offset that puts [index] at the leading edge, clamped to what the list can scroll.
 * Prefers the item's measured position when it is on screen — exact by construction — over the
 * [estimatedPx] the caller derived from its own geometry.
 */
internal fun LazyListState.targetOffsetPx(
    index: Int,
    currentPx: Float,
    estimatedPx: Float,
    maxScrollPx: Float
): Float {
    val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    val target = if (visible != null) currentPx + visible.offset else estimatedPx
    return target.coerceIn(0f, maxScrollPx.coerceAtLeast(0f))
}
