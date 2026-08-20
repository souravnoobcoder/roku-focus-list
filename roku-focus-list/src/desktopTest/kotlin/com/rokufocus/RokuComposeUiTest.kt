package com.rokufocus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Headless Compose UI tests for the behaviours that only exist in composition: keyed rows and
 * items keeping their composition when they move, auto-measure, and the key handler's wiring.
 */
@OptIn(ExperimentalTestApi::class)
class RokuComposeUiTest {

    // Keyed lazy items retain their composition when they shift position. The focus flags are
    // per-position deriveds inside that retained composition, so they must be re-keyed on the
    // position — a stale capture keeps lighting the row that MOVED instead of the row that IS
    // at the selected position.
    @Test
    fun focusFollowsPositionWhenARowIsInsertedAbove() = runComposeUiTest {
        val columnState = RokuColumnState()
        var rowKeys by mutableStateOf(listOf("a", "b", "c"))

        setContent {
            RokuLazyColumn(state = columnState) {
                rowKeys.forEach { k ->
                    row(
                        itemWidth = 80.dp,
                        itemHeight = 40.dp,
                        key = k,
                        header = { focused -> BasicText("h-$k:$focused") }
                    ) {
                        items(2) { i, focused -> BasicText("c-$k-$i:$focused") }
                    }
                }
            }
        }
        waitForIdle()
        columnState.hasFocus = true
        columnState.moveToRow(1)
        waitForIdle()
        onNodeWithText("h-b:true").assertExists()
        onNodeWithText("c-b-0:true").assertExists()

        rowKeys = listOf("x", "a", "b", "c")
        waitForIdle()

        // Vertical selection is positional (LazyListState semantics): row index 1 is now "a".
        onNodeWithText("h-a:true").assertExists()
        onNodeWithText("c-a-0:true").assertExists()
        onNodeWithText("h-b:false").assertExists()
        onNodeWithText("c-b-0:false").assertExists()
    }

    @Test
    fun itemFocusFollowsPositionWhenKeyedItemsShift() = runComposeUiTest {
        val state = RokuFocusListState(itemCount = 3)
        var data by mutableStateOf(listOf("a", "b", "c"))

        setContent {
            RokuLazyRow(
                state = state,
                itemWidth = 60.dp,
                itemKey = { index -> data[index] }
            ) { index, isFocused ->
                BasicText("i-${data[index]}:$isFocused")
            }
        }
        waitForIdle()
        state.scrollTo(1)
        waitForIdle()
        onNodeWithText("i-b:true").assertExists()

        data = listOf("x", "a", "b", "c")
        state.updateItemCount(4)
        waitForIdle()

        // Selection is positional: index 1 is now "a"; "b" moved to 2 and must not stay lit.
        onNodeWithText("i-a:true").assertExists()
        onNodeWithText("i-b:false").assertExists()

        // The accessibility `selected` flag (and the zIndex lift) rides its own derived — it must
        // follow position the same way.
        onNode(isSelectable() and hasAnyDescendant(hasText("i-a:true"))).assertIsSelected()
        onNode(isSelectable() and hasAnyDescendant(hasText("i-b:false"))).assertIsNotSelected()
    }

    @Test
    fun zeroSizedFirstItemDoesNotFinalizeAutoMeasure() = runComposeUiTest {
        val columnState = RokuColumnState()
        var cardSize by mutableStateOf(0.dp)

        setContent {
            RokuLazyColumn(state = columnState) {
                row(key = "r") {
                    items(3) { _, _ -> Box(Modifier.size(cardSize)) }
                }
            }
        }
        waitForIdle()
        assertFalse(
            columnState.hasSelectableRow,
            "a zero first reading must keep the row awaiting measure, not record 0x0 as final"
        )

        cardSize = 120.dp
        waitForIdle()
        assertTrue(columnState.hasSelectableRow, "the first non-zero size finalizes measurement")
    }

    @Test
    fun enterWithNothingSelectableIsNotAClick() = runComposeUiTest {
        val columnState = RokuColumnState()
        val clicks = mutableListOf<Pair<Int, Int>>()
        var railSize by mutableStateOf(0)

        setContent {
            RokuLazyColumn(
                state = columnState,
                onItemClicked = { row, item -> clicks += row to item }
            ) {
                row(itemWidth = 60.dp, itemHeight = 40.dp, key = "r") {
                    items(railSize) { _, _ -> Box(Modifier.size(60.dp, 40.dp)) }
                }
            }
        }
        waitForIdle()
        assertTrue(columnState.requestFocus(), "the column must take focus for key input")
        waitForIdle()

        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(clicks.isEmpty(), "enter with nothing selectable must not report a click")

        // Positive control, so a broken focus setup cannot green-light the guard vacuously.
        railSize = 3
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf(0 to 0), clicks)
    }
}
