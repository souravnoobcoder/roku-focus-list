package com.rokufocus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where rows really land with a `verticalAnchorOffset`, read from the laid-out nodes rather than
 * from the maths, so a scroll path that ignored the offset (the landing snap, the held-key snap,
 * the animated step, the far jump) or a highlight that disagreed with the rows would show here.
 *
 * Every fixture is a 400dp-tall column of 100dp rails 10dp apart, so rail `r` starts at `110 * r`
 * in the content, under a 40dp offset.
 */
@OptIn(ExperimentalTestApi::class)
class RokuVerticalAnchorOffsetTest {

    private val offset = 40.dp

    /** Every press is taken and scrolls animated: no throttle, no acceleration streak. */
    private val animated = RokuFocusConfig(keyRepeatDelayMs = 0L, keyRepeatAccelAfter = Int.MAX_VALUE)

    /** Every press is taken and takes the held-key path, which snaps instead of animating. */
    private val snapped = RokuFocusConfig(keyRepeatDelayMs = 0L, keyRepeatAccelAfter = 0)

    private fun ComposeUiTest.feed(
        state: RokuColumnState,
        mode: RokuFocusMode,
        anchorOffset: Dp = offset,
        config: RokuFocusConfig = animated,
        hero: Boolean = false,
        onRailPlaced: ((rail: Int, topDp: Float) -> Unit)? = null,
    ) {
        setContent {
            Box(Modifier.size(400.dp, 400.dp)) {
                RokuLazyColumn(
                    state = state,
                    config = config,
                    rowSpacing = 10.dp,
                    verticalFocusMode = mode,
                    verticalAnchorOffset = anchorOffset,
                    focusHighlight = { Box(Modifier.fillMaxSize().testTag("highlight")) },
                ) {
                    if (hero) {
                        customRow(height = 300.dp, key = "hero") {
                            BasicText("hero", Modifier.fillMaxSize())
                        }
                    }
                    repeat(RailCount) { rail ->
                        row(
                            key = "rail$rail",
                            itemWidth = 80.dp,
                            itemHeight = 100.dp,
                            headerHeight = 0.dp,
                            focusMode = mode,
                        ) {
                            items(3) { item, _ ->
                                val density = LocalDensity.current.density
                                val placed = Modifier.onGloballyPositioned { coordinates ->
                                    if (item == 0) onRailPlaced?.invoke(rail, coordinates.positionInRoot().y / density)
                                }
                                BasicText("r$rail-$item", Modifier.size(80.dp, 100.dp).then(placed))
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.railTop(rail: Int): Dp = onNodeWithText("r$rail-0").getUnclippedBoundsInRoot().top

    private fun ComposeUiTest.highlightTop(): Dp = onNodeWithTag("highlight").getUnclippedBoundsInRoot().top

    private fun ComposeUiTest.press(key: Key, times: Int = 1) = repeat(times) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.assertHighlightOn(rail: Int) {
        assertEquals(railTop(rail), highlightTop(), "the highlight sits on rail $rail's cards")
    }

    // ── Static: the selected row parks on the offset ──

    @Test
    fun staticParksTheSelectedRowOnTheOffset() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Static)

        for (rail in 1..4) {
            state.moveToRow(rail)
            waitForIdle()
            assertEquals(offset, railTop(rail), "rail $rail")
            assertHighlightOn(rail)
        }
        state.moveToRow(0)
        waitForIdle()
        assertEquals(0.dp, railTop(0), "the first rail is never pushed down by the offset")
        assertHighlightOn(0)
    }

    @Test
    fun staticKeyPressesParkOnTheOffsetAnimatedAndSnapped() {
        for (config in listOf(animated, snapped)) runComposeUiTest {
            val state = RokuColumnState()
            feed(state, RokuFocusMode.Static, config = config)
            assertTrue(state.requestFocus())
            waitForIdle()

            press(Key.DirectionDown, times = 3)
            assertEquals(offset, railTop(3), "down, ${config.keyRepeatAccelAfter}")
            assertHighlightOn(3)

            press(Key.DirectionUp)
            assertEquals(offset, railTop(2), "up, ${config.keyRepeatAccelAfter}")
            assertHighlightOn(2)
        }
    }

    @Test
    fun aFarJumpParksOnTheOffset() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Static)
        // 550dp of travel: more than the 400dp viewport, so the animator teleports.
        state.moveToRow(5)
        waitForIdle()
        assertEquals(offset, railTop(5))
        assertHighlightOn(5)
    }

    @Test
    fun theEndOfTheColumnKeepsTheHighlightOnItsCards() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Static)
        // Content is 12 × 100 + 11 × 10 = 1310dp, so the column scrolls at most 910dp and the
        // last rail (at 1210) can only come up to 300, short of the offset.
        state.moveToRow(RailCount - 1)
        waitForIdle()
        assertEquals(300.dp, railTop(RailCount - 1))
        assertHighlightOn(RailCount - 1)
    }

    // ── Floating: the window's top row parks on the offset ──

    @Test
    fun floatingWalksTheWindowAndParksItsTopRowOnTheOffset() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Floating)
        assertTrue(state.requestFocus())
        waitForIdle()

        // Rails 0–2 fit the unscrolled window: the highlight walks, nothing moves.
        press(Key.DirectionDown, times = 2)
        assertEquals(0.dp, railTop(0))
        assertHighlightOn(2)

        // Rail 3's bottom (430) is below the viewport, so the window scrolls to rail 1 (the first
        // row that, parked 40dp down, leaves room for it) and rail 3 lands at 40 + 220.
        press(Key.DirectionDown)
        assertEquals(offset, railTop(1))
        assertEquals(260.dp, railTop(3))
        assertHighlightOn(3)

        press(Key.DirectionDown)
        assertEquals(offset, railTop(2))
        assertHighlightOn(4)
    }

    @Test
    fun floatingNeverParksTheSelectedRowBelowTheViewport() = runComposeUiTest {
        // At 90dp a window row two rails up would leave the selected rail's bottom 10dp off the
        // screen (90 + 220 + 100 = 410), so every scroll has to start the window one row lower.
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Floating, anchorOffset = 90.dp)
        assertTrue(state.requestFocus())
        waitForIdle()

        for (rail in 1 until RailCount) {
            press(Key.DirectionDown)
            val bounds = onNodeWithText("r$rail-0").getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= 0.dp && bounds.bottom <= 400.dp, "rail $rail at $bounds")
            assertHighlightOn(rail)
        }
    }

    @Test
    fun floatingUpIntoTheOffsetBandScrollsThatRowOntoTheAnchor() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Floating)
        assertTrue(state.requestFocus())
        waitForIdle()
        press(Key.DirectionDown, times = 4)
        assertEquals(offset, railTop(2), "window row 2, with rail 1's bottom showing above it")

        // Up within the window holds it; up into the band makes that row the window row.
        press(Key.DirectionUp, times = 2)
        assertEquals(offset, railTop(2))
        assertHighlightOn(2)
        press(Key.DirectionUp)
        assertEquals(offset, railTop(1))
        assertHighlightOn(1)
    }

    // ── The first row ──

    @Test
    fun aHeroStaysFlushWithTheTopAndTheRailBelowItParksOnTheOffset() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Static, hero = true)
        assertEquals(0.dp, onNodeWithText("hero").getUnclippedBoundsInRoot().top)

        state.moveToRow(1)
        waitForIdle()
        assertEquals(offset, railTop(0))
        assertHighlightOn(0)

        state.moveToRow(0)
        waitForIdle()
        assertEquals(0.dp, onNodeWithText("hero").getUnclippedBoundsInRoot().top)
    }

    // ── Arrival ──

    @Test
    fun aRestoredColumnIsLaidOutOnTheOffsetFromItsFirstFrame() {
        for (mode in RokuFocusMode.entries) runComposeUiTest {
            val placements = mutableListOf<Float>()
            val state = RokuColumnState(initialRowIndex = 4)
            if (mode == RokuFocusMode.Floating) state.windowAnchorRow = 4
            feed(state, mode, onRailPlaced = { rail, top -> if (rail == 4) placements += top })

            assertEquals(offset.value, placements.first(), "$mode: first placement of rail 4")
            assertEquals(offset, railTop(4), "$mode: and it stays there")
        }
    }

    // ── No offset ──

    @Test
    fun noOffsetParksTheSelectedRowAtTheTop() = runComposeUiTest {
        val state = RokuColumnState()
        feed(state, RokuFocusMode.Static, anchorOffset = 0.dp)
        state.moveToRow(3)
        waitForIdle()
        assertEquals(0.dp, railTop(3))
        assertHighlightOn(3)
    }

    private companion object {
        const val RailCount = 12
    }
}
