package com.rokufocus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * Item lambdas must only ever be asked for an index their own list has. Every fixture builds the
 * count, the key and the content from one immutable list captured in composition, the way a feed
 * built from a view-model snapshot does, and indexes it with `list[i]` so a mismatch throws.
 * Reading a `mutableStateOf` inside the lambdas instead would always see the newest list and hide
 * exactly the pairing this checks.
 *
 * Before the rails took their count from the composition that declared their lambdas, every keyed
 * rail that grew threw `IndexOutOfBoundsException: Index 1 out of bounds for length 1` from its
 * key lambda: the state's count landed and the LazyRow re-derived its items against the previous
 * composition's key.
 */
@OptIn(ExperimentalTestApi::class)
class RokuItemCountConsistencyTest {

    private fun ComposeUiTest.columnRailResizes(
        from: Int,
        to: Int,
        keyed: Boolean = true,
        mode: RokuFocusMode = RokuFocusMode.Floating,
        focused: Boolean = false,
        rowCount: Int = 1,
        resizedRow: Int = 0,
    ) {
        val columnState = RokuColumnState()
        var resized by mutableStateOf(List(from) { "g$it" })
        val others = List(4) { "o$it" }

        setContent {
            val current = resized
            RokuLazyColumn(state = columnState, verticalFocusMode = mode) {
                repeat(rowCount) { rowIndex ->
                    val list = if (rowIndex == resizedRow) current else others
                    row(
                        key = "row_$rowIndex",
                        itemWidth = 80.dp,
                        itemHeight = 40.dp,
                        headerHeight = 0.dp,
                        focusMode = mode,
                    ) {
                        items(
                            count = list.size,
                            key = if (keyed) { i -> "${list[i]}_$i" } else null,
                        ) { index, _ ->
                            BasicText("r$rowIndex-${list[index]}", Modifier.size(80.dp, 40.dp))
                        }
                    }
                }
            }
        }
        waitForIdle()
        if (focused) {
            columnState.requestFocus()
            columnState.moveToRow(resizedRow)
            waitForIdle()
        }

        resized = List(to) { "g$it" }
        waitForIdle()

        if (to > 0) onNodeWithText("r$resizedRow-g${to - 1}").assertExists()
    }

    @Test fun columnRailGrowsFromOneToTwo() = runComposeUiTest { columnRailResizes(1, 2) }

    @Test fun columnRailGrowsFromOneToThree() = runComposeUiTest { columnRailResizes(1, 3) }

    @Test fun columnRailGrowsWhileFocused() = runComposeUiTest { columnRailResizes(1, 2, focused = true) }

    @Test fun staticColumnRailGrows() = runComposeUiTest { columnRailResizes(1, 2, mode = RokuFocusMode.Static) }

    @Test fun focusedMiddleRailOfSeveralGrows() = runComposeUiTest {
        columnRailResizes(1, 2, focused = true, rowCount = 4, resizedRow = 1)
    }

    @Test fun unkeyedColumnRailGrows() = runComposeUiTest { columnRailResizes(1, 2, keyed = false) }

    @Test fun emptyColumnRailFills() = runComposeUiTest { columnRailResizes(0, 2) }

    @Test fun columnRailShrinks() = runComposeUiTest { columnRailResizes(3, 1) }

    @Test fun focusedColumnRailShrinks() = runComposeUiTest { columnRailResizes(2, 1, focused = true) }

    // A keyed rail keeps its composition when a row is inserted above it; its cards must go on
    // resolving its own items, not those of whichever row now sits at its old position.
    @Test fun keyedRailShiftedDownByAShorterRow() = runComposeUiTest {
        var rows by mutableStateOf(listOf("long" to List(3) { "l$it" }))
        setContent {
            val current = rows
            RokuLazyColumn {
                current.forEach { (rowKey, list) ->
                    row(key = rowKey, itemWidth = 80.dp, itemHeight = 40.dp, headerHeight = 0.dp) {
                        items(count = list.size, key = { i -> "${list[i]}_$i" }) { index, _ ->
                            BasicText("$rowKey-${list[index]}", Modifier.size(80.dp, 40.dp))
                        }
                    }
                }
            }
        }
        waitForIdle()
        rows = listOf("short" to listOf("s0"), "long" to List(3) { "l$it" })
        waitForIdle()
        onNodeWithText("short-s0").assertExists()
        onNodeWithText("long-l2").assertExists()
    }

    @Test fun stateColumnRailShiftedDownByAShorterRow() = runComposeUiTest {
        var rows by mutableStateOf(listOf("long" to List(3) { "l$it" }))
        val states = mutableMapOf<String, RokuFocusListState>()
        setContent {
            val current = rows
            val configs = current.map { (rowKey, list) ->
                val state = states.getOrPut(rowKey) { RokuFocusListState(itemCount = list.size) }
                state.updateItemCount(list.size)
                RokuColumnRowConfig(state = state, itemWidth = 80.dp, itemHeight = 40.dp, key = rowKey)
            }
            RokuLazyColumn(rows = configs) { rowIndex, itemIndex, _ ->
                val (rowKey, list) = current[rowIndex]
                BasicText("$rowKey-${list[itemIndex]}", Modifier.size(80.dp, 40.dp))
            }
        }
        waitForIdle()
        rows = listOf("short" to listOf("s0"), "long" to List(3) { "l$it" })
        waitForIdle()
        onNodeWithText("short-s0").assertExists()
        onNodeWithText("long-l2").assertExists()
    }

    private fun ComposeUiTest.dslRowResizes(from: Int, to: Int, keyed: Boolean = true, hoisted: Boolean = false) {
        var data by mutableStateOf(List(from) { "g$it" })
        setContent {
            val list = data
            val content: RokuItemScope.() -> Unit = {
                items(count = list.size, key = if (keyed) { i -> "${list[i]}_$i" } else null) { index, _ ->
                    Box(Modifier.size(80.dp, 40.dp)) { BasicText("item-${list[index]}") }
                }
            }
            if (hoisted) {
                RokuLazyRow(state = rememberRokuFocusListState(itemCount = list.size), content = content)
            } else {
                RokuLazyRow(content = content)
            }
        }
        waitForIdle()
        data = List(to) { "g$it" }
        waitForIdle()
        if (to > 0) onNodeWithText("item-g${to - 1}").assertExists()
    }

    @Test fun dslRowGrows() = runComposeUiTest { dslRowResizes(1, 2) }

    @Test fun dslRowWithHoistedStateGrows() = runComposeUiTest { dslRowResizes(1, 2, hoisted = true) }

    @Test fun unkeyedDslRowGrows() = runComposeUiTest { dslRowResizes(1, 2, keyed = false) }

    @Test fun dslRowShrinks() = runComposeUiTest { dslRowResizes(2, 1) }

    private fun ComposeUiTest.stateRowResizes(from: Int, to: Int) {
        var data by mutableStateOf(List(from) { "g$it" })
        setContent {
            val list = data
            RokuLazyRow(
                state = rememberRokuFocusListState(itemCount = list.size),
                itemWidth = 80.dp,
                itemKey = { i -> "${list[i]}_$i" },
            ) { index, _ ->
                Box(Modifier.size(80.dp, 40.dp)) { BasicText("item-${list[index]}") }
            }
        }
        waitForIdle()
        data = List(to) { "g$it" }
        waitForIdle()
        if (to > 0) onNodeWithText("item-g${to - 1}").assertExists()
    }

    @Test fun stateRowGrows() = runComposeUiTest { stateRowResizes(1, 2) }

    @Test fun stateRowShrinks() = runComposeUiTest { stateRowResizes(2, 1) }

    private fun ComposeUiTest.gridResizes(from: Int, to: Int, keyed: Boolean = true) {
        var data by mutableStateOf(List(from) { "g$it" })
        setContent {
            val list = data
            RokuFocusGrid(
                state = rememberRokuGridState(itemCount = list.size, columns = 4),
                itemHeight = 40.dp,
                itemKey = if (keyed) { i -> "${list[i]}_$i" } else null,
            ) { index, _ ->
                BasicText("cell-${list[index]}", Modifier.size(80.dp, 40.dp))
            }
        }
        waitForIdle()
        data = List(to) { "g$it" }
        waitForIdle()
        if (to > 0) onNodeWithText("cell-g${to - 1}").assertExists()
    }

    @Test fun gridGrows() = runComposeUiTest { gridResizes(1, 2) }

    @Test fun unkeyedGridGrows() = runComposeUiTest { gridResizes(1, 2, keyed = false) }

    @Test fun gridShrinks() = runComposeUiTest { gridResizes(2, 1) }
}
