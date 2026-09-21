package com.rokufocus

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max

/**
 * A touchpad remote, as the library sees it: a stream of pan reports that the focused
 * [RokuLazyRow], [RokuLazyColumn] or [RokuFocusGrid] turns into moves the way the tvOS focus
 * engine does.
 *
 * Provide one through [LocalRokuTouchpad] and feed it from the platform — on tvOS,
 * `attachSiriRemote(view)` installs the recogniser and does the feeding; anywhere else call
 * [panBegan], [panChanged] and [panEnded] from whatever reads the device. Every component then
 * behaves the same without further wiring:
 *
 * - **Focus follows the thumb, and only the thumb.** Each step of travel along the locked axis
 *   ([RokuTouchpadConfig.itemStepFraction] of an item pitch sideways, one row pitch up or down)
 *   moves the selection one item, coalesced into a single move when travel arrives faster than
 *   one item per report. Nothing moves once the thumb lifts; there is no coast.
 * - **A fast thumb covers more ground** through a smooth velocity gain, so a hard swipe crosses
 *   several items while a careful one walks them.
 * - **Small movement is never lost.** Travel short of a full step leans the focused card and its
 *   highlight toward the thumb — the focus-movement hint — from the very first report, and at the
 *   end of a row the lean pins at full pull so pushing further is dropped and one step of travel
 *   back moves back.
 * - Moves go through the same code as D-pad presses, so `onItemSelected`, `wrapAround` and
 *   `focusEscape` behave identically for a swipe and a key.
 *
 * Direction is the screen's: swiping right moves the selection right, swiping down moves it down.
 * Without a touchpad in the composition none of this exists at runtime — a D-pad TV runs exactly
 * the code it ran before.
 *
 * @param config Pacing and hint tuning. See [RokuTouchpadConfig] for the units.
 */
@Stable
class RokuTouchpad(val config: RokuTouchpadConfig = RokuTouchpadConfig()) {

    /**
     * Pixels per unit of the host's reports, so travel can be compared against item sizes: the
     * screen scale on tvOS (1 on an Apple TV HD, 2 on a 4K), 1 when the host already reports
     * pixels.
     */
    var pxPerUnit: Float = 1f

    /** Pending travel as a fraction of a step per axis, in [-1, 1]. Rendered by the focused component. */
    internal var lean: Offset by mutableStateOf(Offset.Zero)
        private set

    /** True between [panBegan] and the lift-off, so the lean follows the thumb directly instead of springing. */
    internal var isDragging: Boolean by mutableStateOf(false)
        private set

    private var target: RokuTouchTarget? = null
    private var axis: Orientation? = null
    private var totalX = 0f
    private var totalY = 0f
    private var travel = 0f
    private var edgePull = 0
    /** When the selection last moved under the thumb; the platform's key for that swipe follows within [KeyLeakWindowMs]. */
    private var lastMoveUptime = 0L
    /** When the last contact lifted, so a key arriving just after it can still be judged against that contact. */
    private var lastContactEndUptime = 0L

    /** A new contact. Clears anything left from the previous one. */
    fun panBegan() {
        axis = null
        totalX = 0f
        totalY = 0f
        travel = 0f
        edgePull = 0
        isDragging = true
    }

    /**
     * Movement since the previous report, with the thumb's speed at this moment, in the host's
     * units. A report with no preceding [panBegan] starts a contact.
     */
    fun panChanged(dx: Float, dy: Float, velocityX: Float, velocityY: Float) {
        if (!isDragging) panBegan()
        totalX += dx
        totalY += dy
        val locked = axis
        val current: Orientation
        val delta: Float
        if (locked == null) {
            if (totalX * totalX + totalY * totalY < config.axisLock * config.axisLock) {
                val guess = dominant(totalX, totalY)
                updateLean(leanFor(guess, along(guess, totalX, totalY)))
                return
            }
            current = dominant(totalX, totalY)
            axis = current
            delta = along(current, totalX, totalY)
        } else {
            current = locked
            delta = along(current, dx, dy)
        }
        if (edgePull != 0 && delta * edgePull > 0f) {
            updateLean(leanFor(current, travel))
            return
        }
        edgePull = 0
        travel += delta * config.gain(abs(along(current, velocityX, velocityY)))
        stepFromTravel(current)
        updateLean(leanFor(current, travel))
    }

    /** The thumb lifted. The lean springs back; nothing else happens. */
    fun panEnded() = release()

    /** The contact was cancelled by the platform. Treated like a lift-off. */
    fun panCancelled() = release()

    internal fun bind(target: RokuTouchTarget) {
        this.target = target
    }

    internal fun unbind(target: RokuTouchTarget) {
        if (this.target === target) this.target = null
    }

    /**
     * Whether a direction key along [orientation] toward [forward], arriving at [now], belongs to
     * this touchpad and must be dropped before it reaches a key handler.
     *
     * The Compose tvOS fork turns a flick on the pad into one such key — during the contact when
     * it can read the pad's absolute position, at lift-off otherwise. Inside a focused list that
     * key is the touchpad's own gesture echoed back, and letting it through would move the
     * selection a second time. But it is also the only directional input a Siri Remote without
     * ring buttons has, so it must be dropped **only where the touchpad speaks for it**: while a
     * contact is live or has just lifted, and the selection either already moved under the thumb
     * or still can move that way. At an open edge, across a row's axis, or with no list focused,
     * the key is left alone — the key handler then applies `focusEscape`, and Compose moves
     * focus onward exactly as it would for a D-pad press.
     */
    internal fun swallowsKey(orientation: Orientation, forward: Boolean, now: Long): Boolean {
        val bound = target ?: return false
        val contactIsLive = isDragging || now - lastContactEndUptime <= KeyLeakWindowMs
        if (!contactIsLive) return false
        if (now - lastMoveUptime <= KeyLeakWindowMs) return true
        return bound.canMove(orientation, forward)
    }

    private fun stepFromTravel(current: Orientation) {
        val step = stepUnits(current)
        if (step <= 0f) return
        val count = (abs(travel) / step).toInt()
        if (count == 0) return
        val direction = if (travel > 0f) 1 else -1
        if (move(current, direction * count)) {
            lastMoveUptime = RokuClock.uptimeMillis()
            travel -= direction * count * step
        } else {
            edgePull = direction
            travel = 0f
        }
    }

    private fun move(current: Orientation, steps: Int): Boolean {
        val bound = target ?: return false
        return if (current == Orientation.Horizontal) bound.moveItems(steps) else bound.moveRows(steps)
    }

    private fun stepUnits(current: Orientation): Float {
        val bound = target ?: return 0f
        val scale = if (pxPerUnit > 0f) pxPerUnit else 1f
        val horizontal = current == Orientation.Horizontal
        val fraction = if (horizontal) config.itemStepFraction else config.rowStepFraction
        val floor = if (horizontal) config.minItemStepUnits else config.minRowStepUnits
        val fromPitch = bound.stepPx(current) / scale * fraction
        // A component that cannot move along this axis reports no pitch; the floor must not
        // turn that into a step, or a rail would start answering vertical travel.
        return if (fromPitch <= 0f) 0f else max(fromPitch, floor)
    }

    private fun leanFor(current: Orientation, pendingTravel: Float): Offset {
        val step = stepUnits(current)
        val fraction = when {
            edgePull != 0 -> edgePull.toFloat()
            step <= 0f -> 0f
            else -> (pendingTravel / step).coerceIn(-1f, 1f)
        }
        return if (current == Orientation.Horizontal) Offset(fraction, 0f) else Offset(0f, fraction)
    }

    private fun updateLean(value: Offset) {
        if (lean != value) lean = value
    }

    private fun release() {
        if (isDragging) lastContactEndUptime = RokuClock.uptimeMillis()
        axis = null
        isDragging = false
        updateLean(Offset.Zero)
    }

    private fun dominant(x: Float, y: Float): Orientation =
        if (abs(x) >= abs(y)) Orientation.Horizontal else Orientation.Vertical

    private fun along(current: Orientation, x: Float, y: Float): Float =
        if (current == Orientation.Horizontal) x else y
}

/**
 * The touchpad the focused component listens to, or null on a platform without one. Provide it
 * once at the root; every [RokuLazyRow], [RokuLazyColumn] and [RokuFocusGrid] below picks it up.
 */
val LocalRokuTouchpad: ProvidableCompositionLocal<RokuTouchpad?> = staticCompositionLocalOf { null }

/**
 * How long after the selection moved under the thumb, or after the thumb lifted, a direction
 * key is still judged against that contact. The fork dispatches its key mid-contact or within a
 * frame or two of the lift-off.
 */
internal const val KeyLeakWindowMs = 120L
