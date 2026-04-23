package com.rokufocus.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rokufocus.demo.ui.theme.RokuFocusTheme
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
import com.rokufocus.rememberRokuFocusListState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RokuFocusTheme(darkTheme = true, dynamicColor = false) {
                StreamFocusDemoScreen()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Screens & Data
// ─────────────────────────────────────────────────────────────────────────────

private enum class Screen(val label: String, val icon: String) {
    COLUMN("Column", "C"),
    ROW("Row", "R"),
    STATE("State", "S"),
    WRAP("Wrap", "W"),
    PLAIN("Plain", "P"),
}

private enum class CardType { BANNER, WIDE, LANDSCAPE, CONTINUE, PORTRAIT, MINI }

private data class RowDef(
    val title: String,
    val items: List<MovieItem>,
    val cardType: CardType,
    val itemWidth: Dp,
    val itemHeight: Dp,
    val itemSpacing: Dp = 14.dp,
)

private val baseRows = listOf(
    RowDef("Hero",                 SampleData.heroBanner,       CardType.BANNER,    580.dp, 310.dp, 20.dp),
    RowDef("Featured",             SampleData.featured,         CardType.WIDE,      300.dp, 170.dp, 16.dp),
    RowDef("Trending Now",         SampleData.trending,         CardType.LANDSCAPE, 220.dp, 140.dp, 14.dp),
    RowDef("Continue Watching",    SampleData.continueWatching, CardType.LANDSCAPE, 220.dp, 140.dp, 14.dp),
    RowDef("New Releases",         SampleData.newReleases,      CardType.PORTRAIT,  150.dp, 220.dp, 14.dp),

    RowDef("Action & Adventure",   SampleData.action,           CardType.LANDSCAPE, 220.dp, 140.dp, 14.dp),
    RowDef("Critically Acclaimed", SampleData.acclaimed,        CardType.WIDE,      300.dp, 170.dp, 16.dp),
    RowDef("Drama",                SampleData.drama,            CardType.PORTRAIT,  150.dp, 220.dp, 14.dp),
    RowDef("Sci-Fi & Fantasy",     SampleData.sciFi,            CardType.LANDSCAPE, 220.dp, 140.dp, 14.dp),
)

private const val ROW_COUNT = 100
private val allRows: List<RowDef> = List(ROW_COUNT) { i ->
    val base = baseRows[i % baseRows.size]
    base.copy(title = "${i + 1}. ${base.title}")
}

@Composable
private fun CardForType(cardType: CardType, movie: MovieItem, isFocused: Boolean) {
    when (cardType) {
        CardType.BANNER    -> BannerCard(movie = movie, isFocused = isFocused)
        CardType.WIDE      -> WideCard(movie = movie, isFocused = isFocused)
        CardType.LANDSCAPE -> MovieCard(movie = movie, isFocused = isFocused)
        CardType.CONTINUE  -> ContinueWatchingCard(movie = movie, isFocused = isFocused)
        CardType.PORTRAIT  -> PortraitCard(movie = movie, isFocused = isFocused)
        CardType.MINI      -> MiniCard(movie = movie, isFocused = isFocused)
    }
}

/** Request focus after a short delay to ensure the target is in the tree. */
@Composable
private fun RequestFocusOnAppear(focusRequester: FocusRequester) {
    LaunchedEffect(Unit) {
        delay(100) // wait for measurement + layout frames
        try { focusRequester.requestFocus() } catch (_: Exception) { }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StreamFocusDemoScreen() {
    var activeScreen by remember { mutableStateOf(Screen.COLUMN) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
    ) {
        Sidebar(activeScreen = activeScreen, onScreenSelect = { activeScreen = it })

        // Each screen manages its own focus internally
        when (activeScreen) {
            Screen.COLUMN -> ColumnDslContent()
            Screen.ROW    -> RowDslContent()
            Screen.STATE  -> RowStateContent()
            Screen.WRAP   -> WrapAroundContent()
            Screen.PLAIN  -> PlainContent()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. COLUMN DSL — full OTT layout, 100 rows, 6 card types
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnDslContent() {
    val focusRequester = remember { FocusRequester() }
    RequestFocusOnAppear(focusRequester)

    ScreenShell(title = "RokuLazyColumn DSL", subtitle = "$ROW_COUNT rows \u00b7 6 card types \u00b7 fixed-focus") {
        RokuLazyColumn(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            rowSpacing = 8.dp,
        ) {
            allRows.forEach { rowDef ->
                row(
                    itemWidth = rowDef.itemWidth,
                    itemHeight = rowDef.itemHeight,
                    itemSpacing = rowDef.itemSpacing,
                    contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
                    headerHeight = 30.dp,
                    header = { isRowFocused -> RowHeaderText(rowDef.title, isRowFocused) }
                ) {
                    items(rowDef.items) { movie, isFocused ->
                        CardForType(rowDef.cardType, movie, isFocused)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. ROW DSL — standalone RokuLazyRows, auto-measured width
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RowDslContent() {
    val focusRequester = remember { FocusRequester() }
    RequestFocusOnAppear(focusRequester)

    ScreenShell(title = "RokuLazyRow DSL", subtitle = "Auto-measured width \u00b7 no itemWidth needed") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Landscape cards — first row gets focus
            Text("Trending (220\u00d7140)", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp))
            RokuLazyRow(
                modifier = Modifier.focusRequester(focusRequester),
                itemSpacing = 14.dp,
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
            ) {
                items(SampleData.trending) { movie, isFocused ->
                    MovieCard(movie = movie, isFocused = isFocused)
                }
            }

            // Wide cards
            Text("Featured (300\u00d7170)", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp))
            RokuLazyRow(
                itemSpacing = 16.dp,
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
            ) {
                items(SampleData.featured) { movie, isFocused ->
                    WideCard(movie = movie, isFocused = isFocused)
                }
            }

            // Portrait cards
            Text("New Releases (150\u00d7220)", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp))
            RokuLazyRow(
                itemSpacing = 14.dp,
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
            ) {
                items(SampleData.newReleases) { movie, isFocused ->
                    PortraitCard(movie = movie, isFocused = isFocused)
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. STATE — RokuLazyRow with external state (programmatic control demo)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RowStateContent() {
    val focusRequester = remember { FocusRequester() }
    RequestFocusOnAppear(focusRequester)

    ScreenShell(title = "RokuLazyRow + State", subtitle = "External state \u00b7 explicit itemWidth \u00b7 programmatic control") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Row starting at index 5
            val state1 = rememberRokuFocusListState(
                itemCount = SampleData.trending.size, initialIndex = 5
            )
            Text("Trending (starts at item 6)", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp))
            RokuLazyRow(
                state = state1,
                itemWidth = 220.dp,
                modifier = Modifier.focusRequester(focusRequester),
                itemSpacing = 14.dp,
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
            ) { index, isFocused ->
                MovieCard(movie = SampleData.trending[index], isFocused = isFocused)
            }

            // Row with focusSlot = 2
            val state2 = rememberRokuFocusListState(
                itemCount = SampleData.featured.size, focusSlot = 2
            )
            Text("Featured (focusSlot = 2)", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp))
            RokuLazyRow(
                state = state2,
                itemWidth = 300.dp,
                itemSpacing = 16.dp,
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
            ) { index, isFocused ->
                WideCard(movie = SampleData.featured[index], isFocused = isFocused)
            }

            // Row with acceleration disabled
            val state3 = rememberRokuFocusListState(itemCount = SampleData.action.size)
            Text("Action (no acceleration)", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 24.dp))
            RokuLazyRow(
                state = state3,
                itemWidth = 220.dp,
                itemSpacing = 14.dp,
                config = RokuFocusConfig(keyRepeatAccelAfter = 0),
                contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
            ) { index, isFocused ->
                MovieCard(movie = SampleData.action[index], isFocused = isFocused)
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. WRAP — RokuLazyColumn with wrapAround enabled
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WrapAroundContent() {
    val focusRequester = remember { FocusRequester() }
    RequestFocusOnAppear(focusRequester)

    val wrapRows = baseRows.take(5)
    ScreenShell(title = "Wrap-Around Mode", subtitle = "${wrapRows.size} rows \u00b7 horizontal + vertical wrap") {
        RokuLazyColumn(
            modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
            config = RokuFocusConfig(wrapAround = true),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            rowSpacing = 16.dp,
        ) {
            wrapRows.forEachIndexed { i, rowDef ->
                row(
                    itemWidth = rowDef.itemWidth,
                    itemHeight = rowDef.itemHeight,
                    itemSpacing = rowDef.itemSpacing,
                    contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
                    headerHeight = 30.dp,
                    header = { isRowFocused ->
                        RowHeaderText("${i + 1}. ${rowDef.title} (wrap)", isRowFocused)
                    }
                ) {
                    items(rowDef.items) { movie, isFocused ->
                        CardForType(rowDef.cardType, movie, isFocused)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. PLAIN — Standard LazyColumn + LazyRow for comparison
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlainContent() {
    ScreenShell(title = "Plain Compose", subtitle = "$ROW_COUNT rows \u00b7 standard LazyColumn + LazyRow") {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(allRows.size) { rowIndex ->
                val row = allRows[rowIndex]
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = row.title,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(row.itemSpacing)
                    ) {
                        items(row.items.size) { itemIndex ->
                            val movie = row.items[itemIndex]
                            var focused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .width(row.itemWidth)
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                            ) {
                                CardForType(row.cardType, movie, focused)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScreenShell(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp))
        Text(text = subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp))
        content()
    }
}

@Composable
private fun RowHeaderText(title: String, isRowFocused: Boolean) {
    Text(text = title,
        color = if (isRowFocused) Color.White else Color.White.copy(alpha = 0.6f),
        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// Sidebar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Sidebar(activeScreen: Screen, onScreenSelect: (Screen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(Color(0xFF151515))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Screen.entries.forEach { screen ->
            SidebarItem(
                label = screen.label,
                icon = screen.icon,
                isActive = activeScreen == screen,
                onClick = { onScreenSelect(screen) }
            )
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: String,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bgTarget = when {
        focused -> Color.White; isActive -> Color.White.copy(alpha = 0.25f); else -> Color.Transparent
    }
    val bg by animateColorAsState(bgTarget, tween(180), label = "sidebar_bg")
    val fgTarget = when {
        focused -> Color.Black; isActive -> Color.White.copy(alpha = 0.9f); else -> Color.White.copy(alpha = 0.55f)
    }
    val fg by animateColorAsState(fgTarget, tween(180), label = "sidebar_fg")
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(180), label = "sidebar_scale")

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onClick != null) Modifier.clickable(interactionSource, null) { onClick() }
                else Modifier.focusable()
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()) {
            Text(text = icon, color = fg, fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(text = label, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}
