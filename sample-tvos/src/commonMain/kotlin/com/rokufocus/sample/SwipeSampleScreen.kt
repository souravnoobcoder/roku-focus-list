package com.rokufocus.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rokufocus.DefaultFocusHighlight
import com.rokufocus.RokuAnimationSpec
import com.rokufocus.RokuColumnRowConfig
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuFocusEscape
import com.rokufocus.RokuLazyColumn
import com.rokufocus.rememberRokuColumnState
import com.rokufocus.rememberRokuFocusListState

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
private val Accent = Color(0xFF7DE2D1)

/**
 * Navigation behaviour shared by the column and the swipe handler, so a flick and a D-pad press
 * obey the same wrap and escape rules.
 */
private val SampleConfig = RokuFocusConfig(
    highlightAnimationSpec = RokuAnimationSpec.Smooth,
    hapticFeedback = false,
    // The grid is the whole screen; there is nowhere for focus to escape to.
    focusEscape = RokuFocusEscape.None,
)

/**
 * Fling curves in the points per second a Siri Remote actually reports, measured on an Apple TV HD:
 * a relaxed flick lifts off at 4,000–8,000 pt/s and a hard one at 15,000–20,000. The library's
 * defaults are in no particular unit and would max every flick out, so the sample re-bases them:
 * a gentle rail flick coasts nothing, a relaxed one a card, a hard one three or four. Rows are
 * bigger moves, so a vertical flick coasts at most two of them.
 */
private val RowSwipeConfig =
    SampleConfig.copy(swipeVelocityThreshold = 3000f, swipeSensitivity = 0.5f, swipeMaxSteps = 4)
private val ColumnSwipeConfig =
    SampleConfig.copy(swipeVelocityThreshold = 5000f, swipeSensitivity = 0.3f, swipeMaxSteps = 2)

/**
 * Touchpad travel per item while dragging, in item pitches. Above 1 so a full swipe across the pad
 * (roughly 1,000–1,800 pt) walks two or three cards rather than four, and rows slower still.
 */
private const val HorizontalStepScale = 1.15f
private const val VerticalStepScale = 1.5f

/**
 * Apple TV sample: the same fixed-focus grid the other samples show, driven by the **Siri Remote
 * touchpad** as well as the D-pad.
 *
 * Touchpad movement reaches the screen as a stream of [RemotePanEvent]s, and [RemoteNavigator]
 * turns it into selection moves the way the native focus engine does: the highlight follows the
 * thumb while it is down, one item per item-width of travel, and a flick coasts on for
 * [com.rokufocus.stepsForVelocity] more items on a decelerating schedule. Under the title, one
 * line reports the last gesture and another the frame timing it produced.
 *
 * Note the column is the **state-based** overload rather than the `row { }` DSL: a horizontal
 * swipe has to reach the focused row's state, and the DSL keeps each row's state to itself.
 */
@Composable
fun SwipeSampleScreen(modifier: Modifier = Modifier) {
    val columnState = rememberRokuColumnState()
    val rowStates = sections.map { (title, items) ->
        key(title) { rememberRokuFocusListState(itemCount = items.size) }
    }

    val rows = sections.mapIndexed { rowIndex, (title, items) ->
        RokuColumnRowConfig(
            state = rowStates[rowIndex],
            itemWidth = CardWidth,
            itemHeight = CardHeight,
            itemSpacing = CardSpacing,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
            headerHeight = RowHeaderHeight,
            key = title,
            itemContentDescription = { index -> items[index].name },
        )
    }

    var gestureReport by remember { mutableStateOf("Drag or flick the remote, or use the D-pad") }
    var gestureCount by remember { mutableIntStateOf(0) }
    val frameReport = rememberFrameReport(gestureCount)

    // rowStates is a fresh list every pass; the navigator is long-lived (it owns the fling job),
    // so it reads the current list through a holder instead of being rebuilt around it.
    val currentRowStates = rememberUpdatedState(rowStates)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navigator = remember(columnState, density) {
        // One item of on-screen travel per item moved, in the points the touchpad reports.
        val pointsPerPx = 1f / TvRemotePan.screenScale
        val horizontalStep =
            with(density) { (CardWidth + CardSpacing).toPx() } * pointsPerPx * HorizontalStepScale
        val verticalStep =
            with(density) { (RowHeaderHeight + CardHeight + RowSpacing).toPx() } * pointsPerPx * VerticalStepScale
        RemoteNavigator(
            scope = scope,
            config = { axis -> if (axis == PanAxis.Horizontal) RowSwipeConfig else ColumnSwipeConfig },
            columnState = columnState,
            rowStates = { currentRowStates.value },
            stepPoints = { axis -> if (axis == PanAxis.Horizontal) horizontalStep else verticalStep },
            onReport = { report ->
                gestureReport = report
                gestureCount++
            },
        )
    }
    DisposableEffect(navigator) {
        TvRemotePan.onEvent = navigator::onEvent
        onDispose { TvRemotePan.onEvent = null }
    }

    // The column is the only focusable node, so it needs platform focus before any key arrives.
    LaunchedEffect(columnState) { runCatching { columnState.requestFocus() } }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        Column {
            BasicText(
                text = "RokuFocus — swipe sample",
                style = TextStyle(color = Color.White, fontSize = 30.sp),
                modifier = Modifier.padding(start = 48.dp, top = 40.dp),
            )
            GestureReadout(gestureReport, frameReport)

            RokuLazyColumn(
                rows = rows,
                state = columnState,
                config = SampleConfig,
                contentPadding = PaddingValues(bottom = 48.dp),
                rowSpacing = RowSpacing,
                focusHighlight = { isFocused ->
                    DefaultFocusHighlight(
                        isFocused = isFocused,
                        borderColor = Accent,
                        borderWidth = 3.dp,
                        cornerRadius = 10.dp,
                        overflow = 5.dp,
                        animateScale = true,
                    )
                },
                // Fires for D-pad and for any swipe the Compose tvOS fork still turned into a key;
                // the navigator logs its own moves, so a "key" line with no "move" line before it
                // means the fork's swipe-to-focus got through.
                onItemSelected = { rowIndex, itemIndex ->
                    println("[roku] key row=$rowIndex item=$itemIndex")
                },
                rowHeader = { rowIndex, isRowFocused ->
                    RowHeader(sections[rowIndex].first, isRowFocused)
                },
            ) { rowIndex, itemIndex, isFocused ->
                Card(sections[rowIndex].second[itemIndex].name, CardWidth, CardHeight, isFocused)
            }
        }
    }
}

@Composable
private fun GestureReadout(gesture: String, frames: String) {
    Column(modifier = Modifier.padding(start = 48.dp, top = 6.dp, bottom = 16.dp)) {
        BasicText(text = gesture, style = TextStyle(color = Accent, fontSize = 18.sp))
        BasicText(
            text = frames.ifEmpty { " " },
            style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RowHeader(text: String, isRowFocused: Boolean) {
    BasicText(
        text = text,
        style = TextStyle(
            color = if (isRowFocused) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 18.sp,
        ),
        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp).height(RowHeaderHeight),
    )
}

@Composable
private fun Card(label: String, width: Dp, height: Dp, isFocused: Boolean) {
    Box(
        modifier = Modifier
            .size(width, height)
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
private fun GestureReadoutPreview() {
    Box(modifier = Modifier.background(Color(0xFF0B0B0B))) {
        GestureReadout(
            gesture = "◀▶ drag 612 pt → 1 step · flick 2310 pt/s → 3 steps",
            frames = "50 fps on a 50 Hz panel · worst frame 21 ms · 0 missed vsync",
        )
    }
}

@Preview
@Composable
private fun CardPreview() {
    Row(modifier = Modifier.background(Color(0xFF0B0B0B)).padding(16.dp)) {
        Card("Trending 1", CardWidth, CardHeight, isFocused = true)
        Card("Trending 2", CardWidth, CardHeight, isFocused = false)
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
