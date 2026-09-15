package com.rokufocus.sample

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import com.rokufocus.DefaultFocusHighlight
import com.rokufocus.RokuAnimationSpec
import com.rokufocus.RokuColumnRowConfig
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuFocusEscape
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
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
private val RailPadding = PaddingValues(start = 48.dp, end = 48.dp)
private val Accent = Color(0xFF7DE2D1)

/**
 * Navigation behaviour shared by every layout and the touchpad handler, so a swipe and a D-pad
 * press obey the same wrap and escape rules.
 */
private val SampleConfig = RokuFocusConfig(
    highlightAnimationSpec = RokuAnimationSpec.Smooth,
    hapticFeedback = false,
    // Each layout fills the screen; there is nowhere for focus to escape to.
    focusEscape = RokuFocusEscape.None,
)

/**
 * Touchpad travel per item while dragging, in item pitches. A full swipe across the Siri Remote
 * pad is roughly 1,000–1,800 pt on an Apple TV HD; at 0.65 of a card that walks four to six cards
 * before acceleration, and a modest half-pad swipe still moves one. Rows are bigger moves.
 */
private const val HorizontalStepScale = 0.65f
private const val VerticalStepScale = 1f

/**
 * Drag acceleration, the focus engine's equivalent of pointer acceleration: travel counts for more
 * the faster the thumb moves. Unity below [GainStartsAtPtPerSec], [MaxDragGain] from
 * [GainMaxAtPtPerSec] up, smooth in between. A relaxed swipe lifts off at 4,000–8,000 pt/s and a
 * hard one at 15,000–20,000, so a hard swipe crosses about twice the cards of a careful one.
 */
private const val GainStartsAtPtPerSec = 2500f
private const val GainMaxAtPtPerSec = 12000f
private const val MaxDragGain = 2f

/**
 * The focus-movement hint: how far the focused card leans toward the thumb at a full step of
 * pending travel, and how much it tilts. The response is a square root of the pending fraction,
 * so a brush of the pad already reads as a lean (a fifth of a step gives almost half the travel)
 * while the last stretch before a move adds little. Small on purpose — it says "I felt that", not
 * "I moved".
 */
private val HintTravel = 12.dp
private const val HintTiltDegrees = 5f

private fun shapeLean(fraction: Float): Float = sign(fraction) * sqrt(abs(fraction))

private fun dragGain(speed: Float): Float {
    val t = ((speed - GainStartsAtPtPerSec) / (GainMaxAtPtPerSec - GainStartsAtPtPerSec)).coerceIn(0f, 1f)
    val eased = t * t * (3f - 2f * t)
    return 1f + (MaxDragGain - 1f) * eased
}

/** The three ways to put the library on screen; Play/Pause on the remote cycles through them. */
private enum class Layout(val title: String) {
    ColumnDsl("RokuLazyColumn · row { } DSL"),
    ColumnState("RokuLazyColumn · state-based rows"),
    StandaloneRow("RokuLazyRow · standalone, hoisted state");

    fun next(): Layout = entries[(ordinal + 1) % entries.size]
}

/** What every layout needs from the host: the hint to lean with, and the navigator's plumbing. */
private class SwipeHost(
    val hint: State<Offset>,
    val hintTravelPx: Float,
    val stepPoints: (PanAxis) -> Float,
    val onHint: (Offset) -> Unit,
    val onReport: (String) -> Unit,
)

/**
 * Apple TV sample: the same fixed-focus rails in each of the library's layouts, driven by the
 * **Siri Remote touchpad** as well as the D-pad. Play/Pause switches layout.
 *
 * Touchpad movement reaches the screen as a stream of [RemotePanEvent]s, and [RemoteNavigator]
 * turns it into selection moves the way the native focus engine does: the highlight follows the
 * thumb while it is down, a fast thumb covers more ground, nothing moves once it lifts, and travel
 * too small to change focus leans the focused card toward the thumb so it is never mistaken for a
 * lost gesture. The same navigator drives all three layouts through a [SwipeTarget]: a column's
 * horizontal swipes go through `rokuMoveItemsBy` on the column state, so the `row { }` DSL — which
 * keeps its rows' states private — works exactly like the state-based overload. Under the title,
 * one line reports the last gesture and another the frame timing it produced.
 */
@Composable
fun SwipeSampleScreen(modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf(Layout.ColumnDsl) }
    var gestureReport by remember { mutableStateOf(IdleReport) }
    var gestureCount by remember { mutableIntStateOf(0) }
    val frameReport = rememberFrameReport(gestureCount)

    // Pending sub-step travel as a fraction of a step per axis. Followed with a soft spring so the
    // card leans rather than twitches, and springs back to rest when the thumb lifts.
    var hintTarget by remember { mutableStateOf(Offset.Zero) }
    val hint = animateOffsetAsState(
        targetValue = hintTarget,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
        label = "focus_hint",
    )

    val density = LocalDensity.current
    val host = remember(density) {
        val pointsPerPx = 1f / TvRemotePan.screenScale
        val horizontalStep =
            with(density) { (CardWidth + CardSpacing).toPx() } * pointsPerPx * HorizontalStepScale
        val verticalStep =
            with(density) { (RowHeaderHeight + CardHeight + RowSpacing).toPx() } * pointsPerPx * VerticalStepScale
        SwipeHost(
            hint = hint,
            hintTravelPx = with(density) { HintTravel.toPx() },
            stepPoints = { axis -> if (axis == PanAxis.Horizontal) horizontalStep else verticalStep },
            onHint = { hintTarget = it },
            onReport = { report ->
                gestureReport = report
                gestureCount++
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.MediaPlayPause) {
                    layout = layout.next()
                    gestureReport = IdleReport
                    true
                } else {
                    false
                }
            },
    ) {
        Column {
            BasicText(
                text = layout.title,
                style = TextStyle(color = Color.White, fontSize = 30.sp),
                modifier = Modifier.padding(start = 48.dp, top = 40.dp),
            )
            GestureReadout(gestureReport, frameReport)

            when (layout) {
                Layout.ColumnDsl -> ColumnDslLayout(host)
                Layout.ColumnState -> ColumnStateLayout(host)
                Layout.StandaloneRow -> StandaloneRowLayout(host)
            }
        }
    }
}

private const val IdleReport = "Drag the remote, or use the D-pad · Play/Pause switches layout"

/** The column DSL: sizes are measured from the first card and the header, nothing declared. */
@Composable
private fun ColumnDslLayout(host: SwipeHost) {
    val columnState = rememberRokuColumnState()
    BindRemote(remember(columnState) { ColumnSwipeTarget(columnState, SampleConfig) }, host)
    RequestFocusWhenReady(columnState) { columnState.requestFocus() }

    RokuLazyColumn(
        state = columnState,
        config = SampleConfig,
        contentPadding = PaddingValues(bottom = 48.dp),
        rowSpacing = RowSpacing,
        focusHighlight = { isFocused -> LeaningHighlight(isFocused, host) },
        onItemSelected = ::logKeyMove,
    ) {
        sections.forEach { (title, items) ->
            row(
                itemSpacing = CardSpacing,
                contentPadding = RailPadding,
                key = title,
                header = { isRowFocused -> RowHeader(title, isRowFocused) },
            ) {
                items(items, key = { it.id }, contentDescription = { it.name }) { item, isFocused ->
                    Card(item.name, isFocused, host)
                }
            }
        }
    }
}

/** The state-based column: every size declared, one hoisted state per rail. */
@Composable
private fun ColumnStateLayout(host: SwipeHost) {
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
            contentPadding = RailPadding,
            headerHeight = RowHeaderHeight,
            key = title,
            itemContentDescription = { index -> items[index].name },
        )
    }
    BindRemote(remember(columnState) { ColumnSwipeTarget(columnState, SampleConfig) }, host)
    RequestFocusWhenReady(columnState) { columnState.requestFocus() }

    RokuLazyColumn(
        rows = rows,
        state = columnState,
        config = SampleConfig,
        contentPadding = PaddingValues(bottom = 48.dp),
        rowSpacing = RowSpacing,
        focusHighlight = { isFocused -> LeaningHighlight(isFocused, host) },
        onItemSelected = ::logKeyMove,
        rowHeader = { rowIndex, isRowFocused -> RowHeader(sections[rowIndex].first, isRowFocused) },
    ) { rowIndex, itemIndex, isFocused ->
        Card(sections[rowIndex].second[itemIndex].name, isFocused, host)
    }
}

/** One rail on its own: the DSL overload with a hoisted state, so a swipe has a handle on it. */
@Composable
private fun StandaloneRowLayout(host: SwipeHost) {
    val (title, items) = sections.first()
    val rowState = rememberRokuFocusListState(itemCount = items.size)
    BindRemote(remember(rowState) { RowSwipeTarget(rowState, SampleConfig) }, host)
    RequestFocusWhenReady(rowState) { rowState.requestFocus() }

    Column {
        RowHeader(title, rowState.hasFocus)
        RokuLazyRow(
            config = SampleConfig,
            contentPadding = RailPadding,
            itemSpacing = CardSpacing,
            focusHighlight = { isFocused -> LeaningHighlight(isFocused, host) },
            onItemSelected = { index -> logKeyMove(0, index) },
            state = rowState,
        ) {
            items(items, key = { it.id }, contentDescription = { it.name }) { item, isFocused ->
                Card(item.name, isFocused, host)
            }
        }
    }
}

/** Routes the remote to [target] while the calling layout is on screen. */
@Composable
private fun BindRemote(target: SwipeTarget, host: SwipeHost) {
    val navigator = remember(target, host) {
        RemoteNavigator(target, host.stepPoints, ::dragGain, host.onHint, host.onReport)
    }
    DisposableEffect(navigator) {
        val handler: (RemotePanEvent) -> Unit = navigator::onEvent
        TvRemotePan.onEvent = handler
        onDispose { if (TvRemotePan.onEvent === handler) TvRemotePan.onEvent = null }
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

/**
 * Fires for D-pad presses and for any swipe the Compose tvOS fork still turned into a key; the
 * navigator logs its own moves, so a "key" line with no "move" line before it means the fork's
 * swipe-to-focus got through.
 */
private fun logKeyMove(rowIndex: Int, itemIndex: Int) {
    println("[roku] key row=$rowIndex item=$itemIndex")
}

/** The default border, leaning with the card so the two stay one object. */
@Composable
private fun BoxScope.LeaningHighlight(isFocused: Boolean, host: SwipeHost) {
    Box(modifier = Modifier.matchParentSize().focusHint(host.hint, host.hintTravelPx)) {
        DefaultFocusHighlight(
            isFocused = isFocused,
            borderColor = Accent,
            borderWidth = 3.dp,
            cornerRadius = 10.dp,
            overflow = 5.dp,
            animateScale = true,
        )
    }
}

/**
 * Leans and tilts toward the pending travel in [hint]. Read inside the layer block, so following
 * the thumb re-draws the layer and never recomposes; a null [hint] contributes nothing, which is
 * what every card but the focused one passes.
 */
private fun Modifier.focusHint(hint: State<Offset>?, travelPx: Float): Modifier {
    if (hint == null) return this
    return graphicsLayer {
        val lean = hint.value
        val x = shapeLean(lean.x)
        val y = shapeLean(lean.y)
        translationX = x * travelPx
        translationY = y * travelPx
        rotationY = x * HintTiltDegrees
        rotationX = -y * HintTiltDegrees
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

@Composable
private fun Card(label: String, isFocused: Boolean, host: SwipeHost?) {
    Box(
        modifier = Modifier
            .size(CardWidth, CardHeight)
            .focusHint(if (isFocused) host?.hint else null, host?.hintTravelPx ?: 0f)
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
            gesture = "◀▶ drag 612 pt → 2 cards · peak 6,310 pt/s, gain ×1.4",
            frames = "50 fps on a 50 Hz panel · worst frame 21 ms · 0 missed vsync",
        )
    }
}

@Preview
@Composable
private fun CardPreview() {
    Row(modifier = Modifier.background(Color(0xFF0B0B0B)).padding(16.dp)) {
        Card("Trending 1", isFocused = true, host = null)
        Card("Trending 2", isFocused = false, host = null)
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
