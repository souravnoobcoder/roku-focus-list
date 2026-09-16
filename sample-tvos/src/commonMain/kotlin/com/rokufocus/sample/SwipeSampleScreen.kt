package com.rokufocus.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rokufocus.DefaultFocusHighlight
import com.rokufocus.RokuAnimationSpec
import com.rokufocus.RokuColumnRowConfig
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuFocusEscape
import com.rokufocus.RokuFocusGrid
import com.rokufocus.RokuFocusMode
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
import com.rokufocus.rememberRokuColumnState
import com.rokufocus.rememberRokuFocusListState
import com.rokufocus.rememberRokuGridState

private data class Title(val id: Int, val name: String)

private val sections = listOf(
    "Trending Now" to List(40) { Title(it, "Trending ${it + 1}") },
    "New Releases" to List(30) { Title(it, "New ${it + 1}") },
    "Action" to List(35) { Title(it, "Action ${it + 1}") },
    "Documentaries" to List(25) { Title(it, "Docs ${it + 1}") },
    "Comedy" to List(28) { Title(it, "Comedy ${it + 1}") },
    "Sci-Fi" to List(32) { Title(it, "Sci-Fi ${it + 1}") },
)

private val CardWidth = 220.dp
private val CardHeight = 140.dp
private val CardSpacing = 14.dp
private val RowHeaderHeight = 30.dp
private val RowSpacing = 20.dp
private val RailPadding = PaddingValues(start = 48.dp, end = 48.dp)
private const val GridColumns = 5
private const val GridCellCount = 60
private val GridCellHeight = 110.dp
private val GridSpacing = 14.dp
private val Accent = Color(0xFF7DE2D1)

/** Shared by every layout, so a swipe and a D-pad press obey the same wrap and escape rules. */
private val SampleConfig = RokuFocusConfig(
    highlightAnimationSpec = RokuAnimationSpec.Smooth,
    hapticFeedback = false,
    // Each layout fills the screen; there is nowhere for focus to escape to.
    focusEscape = RokuFocusEscape.None,
)

/** The four ways to put the library on screen. */
private enum class Layout(val title: String) {
    ColumnDsl("RokuLazyColumn · row { } DSL"),
    ColumnState("RokuLazyColumn · state-based rows"),
    StandaloneRow("RokuLazyRow · standalone, hoisted state"),
    Grid("RokuFocusGrid · 5 columns"),
}

/**
 * Every layout in both focus modes; Play/Pause on the remote steps through them. All four
 * Floating scenes come first — the walking highlight is what is under test — then the Static
 * ones.
 */
private data class Scene(val layout: Layout, val mode: RokuFocusMode) {
    val title: String get() = "${layout.title} · $mode"
}

private val Scenes: List<Scene> =
    listOf(RokuFocusMode.Floating, RokuFocusMode.Static).flatMap { mode ->
        Layout.entries.map { Scene(it, mode) }
    }

/**
 * Apple TV sample: the library's four layouts, each in both focus modes, driven by the Siri Remote
 * touchpad as well as the D-pad. Play/Pause switches scene.
 *
 * Nothing in this file reads the touchpad. `MainViewController` provides one `RokuTouchpad` to
 * the tree and attaches it to the host view; from there every `RokuLazyColumn`, `RokuLazyRow` and
 * `RokuFocusGrid` follows the thumb, leans toward travel too small to move, and stops when the
 * thumb lifts, exactly as a consumer's own screens would. Under the title, one line names the
 * last selection and another the frame timing the move produced.
 */
@Composable
fun SwipeSampleScreen(modifier: Modifier = Modifier) {
    var sceneIndex by remember { mutableIntStateOf(0) }
    val scene = Scenes[sceneIndex]
    var selectionReport by remember { mutableStateOf(IdleReport) }
    var moveCount by remember { mutableIntStateOf(0) }
    val frameReport = rememberFrameReport(moveCount)

    val onSelected: (rowIndex: Int, itemIndex: Int) -> Unit = remember {
        { rowIndex, itemIndex ->
            selectionReport = "Selected row ${rowIndex + 1} · item ${itemIndex + 1}"
            moveCount++
            println("[roku] select row=$rowIndex item=$itemIndex")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.MediaPlayPause) {
                    sceneIndex = (sceneIndex + 1) % Scenes.size
                    selectionReport = IdleReport
                    true
                } else {
                    false
                }
            },
    ) {
        Column {
            BasicText(
                text = scene.title,
                style = TextStyle(color = Color.White, fontSize = 30.sp),
                modifier = Modifier.padding(start = 48.dp, top = 40.dp),
            )
            SelectionReadout(selectionReport, frameReport)

            // Keyed so a scene change starts each layout fresh instead of reusing a neighbour's
            // remembered states under a different focus mode.
            key(scene) {
                when (scene.layout) {
                    Layout.ColumnDsl -> ColumnDslLayout(scene.mode, onSelected)
                    Layout.ColumnState -> ColumnStateLayout(scene.mode, onSelected)
                    Layout.StandaloneRow -> StandaloneRowLayout(scene.mode, onSelected)
                    Layout.Grid -> GridLayout(scene.mode, onSelected)
                }
            }
        }
    }
}

private const val IdleReport = "Drag the remote, or use the D-pad · Play/Pause switches layout and focus mode"

/** The column DSL: sizes are measured from the first card and the header, nothing declared. */
@Composable
private fun ColumnDslLayout(mode: RokuFocusMode, onSelected: (Int, Int) -> Unit) {
    val columnState = rememberRokuColumnState()
    RequestFocusWhenReady(columnState) { columnState.requestFocus() }

    RokuLazyColumn(
        state = columnState,
        config = SampleConfig,
        contentPadding = PaddingValues(bottom = 48.dp),
        rowSpacing = RowSpacing,
        focusHighlight = { isFocused -> SampleHighlight(isFocused) },
        onItemSelected = onSelected,
        verticalFocusMode = mode,
    ) {
        sections.forEach { (title, items) ->
            row(
                itemSpacing = CardSpacing,
                contentPadding = RailPadding,
                key = title,
                focusMode = mode,
                header = { isRowFocused -> RowHeader(title, isRowFocused) },
            ) {
                items(items, key = { it.id }, contentDescription = { it.name }) { item, isFocused ->
                    Card(item.name, isFocused)
                }
            }
        }
    }
}

/** The state-based column: every size declared, one hoisted state per rail. */
@Composable
private fun ColumnStateLayout(mode: RokuFocusMode, onSelected: (Int, Int) -> Unit) {
    val columnState = rememberRokuColumnState()
    val rowStates = sections.map { (title, items) ->
        key(title) { rememberRokuFocusListState(itemCount = items.size, focusMode = mode) }
    }
    val rows = sections.mapIndexed { rowIndex, (title, items) ->
        RokuColumnRowConfig(
            state = rowStates[rowIndex],
            itemWidth = CardWidth,
            itemHeight = CardHeight,
            itemSpacing = CardSpacing,
            contentPadding = RailPadding,
            headerHeight = RowHeaderHeight,
            key = title,
            itemContentDescription = { index -> items[index].name },
        )
    }
    RequestFocusWhenReady(columnState) { columnState.requestFocus() }

    RokuLazyColumn(
        rows = rows,
        state = columnState,
        config = SampleConfig,
        contentPadding = PaddingValues(bottom = 48.dp),
        rowSpacing = RowSpacing,
        focusHighlight = { isFocused -> SampleHighlight(isFocused) },
        onItemSelected = onSelected,
        rowHeader = { rowIndex, isRowFocused -> RowHeader(sections[rowIndex].first, isRowFocused) },
        verticalFocusMode = mode,
    ) { rowIndex, itemIndex, isFocused ->
        Card(sections[rowIndex].second[itemIndex].name, isFocused)
    }
}

/** One rail on its own: the DSL overload with a hoisted state. */
@Composable
private fun StandaloneRowLayout(mode: RokuFocusMode, onSelected: (Int, Int) -> Unit) {
    val (title, items) = sections.first()
    val rowState = rememberRokuFocusListState(itemCount = items.size, focusMode = mode)
    RequestFocusWhenReady(rowState) { rowState.requestFocus() }

    Column {
        RowHeader(title, rowState.hasFocus)
        RokuLazyRow(
            config = SampleConfig,
            contentPadding = RailPadding,
            itemSpacing = CardSpacing,
            focusHighlight = { isFocused -> SampleHighlight(isFocused) },
            onItemSelected = { index -> onSelected(0, index) },
            state = rowState,
        ) {
            items(items, key = { it.id }, contentDescription = { it.name }) { item, isFocused ->
                Card(item.name, isFocused)
            }
        }
    }
}

/**
 * A wall of cells, floating by default: the highlight walks the visible rows and the grid scrolls
 * only when the selection would leave them.
 */
@Composable
private fun GridLayout(mode: RokuFocusMode, onSelected: (Int, Int) -> Unit) {
    val gridState = rememberRokuGridState(itemCount = GridCellCount, columns = GridColumns, focusMode = mode)
    RequestFocusWhenReady(gridState) { gridState.requestFocus() }

    RokuFocusGrid(
        state = gridState,
        itemHeight = GridCellHeight,
        config = SampleConfig,
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
        itemSpacing = GridSpacing,
        rowSpacing = GridSpacing,
        focusHighlight = { isFocused -> SampleHighlight(isFocused) },
        onItemSelected = { index -> onSelected(index / GridColumns, index % GridColumns) },
        itemContentDescription = { index -> "Title ${index + 1}" },
    ) { index, isFocused ->
        GridCell("Title ${index + 1}", isFocused)
    }
}

/**
 * The lists are the only focusable nodes and need platform focus before any key arrives, but a
 * DSL row only composes its focusable once it has measured a card, so the request is retried for a
 * few frames.
 */
@Composable
private fun RequestFocusWhenReady(key: Any, request: () -> Boolean) {
    LaunchedEffect(key) {
        repeat(FocusRequestFrames) {
            if (runCatching(request).getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
}

private const val FocusRequestFrames = 10

@Composable
private fun BoxScope.SampleHighlight(isFocused: Boolean) {
    DefaultFocusHighlight(
        isFocused = isFocused,
        borderColor = Accent,
        borderWidth = 3.dp,
        cornerRadius = 10.dp,
        overflow = 5.dp,
        animateScale = true,
    )
}

@Composable
private fun SelectionReadout(selection: String, frames: String) {
    Column(modifier = Modifier.padding(start = 48.dp, top = 6.dp, bottom = 16.dp)) {
        BasicText(text = selection, style = TextStyle(color = Accent, fontSize = 18.sp))
        BasicText(
            text = frames.ifEmpty { " " },
            style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Exactly [RowHeaderHeight] tall: the state-based column is told that height and trusts it. */
@Composable
private fun RowHeader(text: String, isRowFocused: Boolean) {
    BasicText(
        text = text,
        style = TextStyle(
            color = if (isRowFocused) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 18.sp,
        ),
        modifier = Modifier.height(RowHeaderHeight).padding(start = 48.dp, bottom = 8.dp),
    )
}

/** A grid cell fills the width the grid hands it; only the height is its own. */
@Composable
private fun GridCell(label: String, isFocused: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF2E2E2E) else Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = label, style = TextStyle(color = Color.White, fontSize = 15.sp))
    }
}

@Composable
private fun Card(label: String, isFocused: Boolean) {
    Box(
        modifier = Modifier
            .size(CardWidth, CardHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF2E2E2E) else Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = label, style = TextStyle(color = Color.White, fontSize = 15.sp))
    }
}

@Preview
@Composable
private fun SwipeSampleScreenPreview() {
    SwipeSampleScreen()
}

@Preview
@Composable
private fun SelectionReadoutPreview() {
    Box(modifier = Modifier.background(Color(0xFF0B0B0B))) {
        SelectionReadout(
            selection = "Selected row 2 · item 7",
            frames = "50 fps on a 50 Hz panel · worst frame 21 ms · 0 missed vsync",
        )
    }
}

@Preview
@Composable
private fun GridCellPreview() {
    Row(modifier = Modifier.background(Color(0xFF0B0B0B)).padding(16.dp)) {
        Box(Modifier.size(160.dp, GridCellHeight)) { GridCell("Title 1", isFocused = true) }
        Box(Modifier.size(160.dp, GridCellHeight).padding(start = 14.dp)) { GridCell("Title 2", isFocused = false) }
    }
}

@Preview
@Composable
private fun CardPreview() {
    Row(modifier = Modifier.background(Color(0xFF0B0B0B)).padding(16.dp)) {
        Card("Trending 1", isFocused = true)
        Card("Trending 2", isFocused = false)
    }
}

@Preview
@Composable
private fun RowHeaderPreview() {
    Column(modifier = Modifier.background(Color(0xFF0B0B0B))) {
        RowHeader("Trending Now", isRowFocused = true)
        RowHeader("New Releases", isRowFocused = false)
    }
}
