package com.example.myapplication

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.rokufocus.RokuColumnRowConfig
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
import com.rokufocus.rememberRokuFocusListState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                StreamFocusDemoScreen()
            }
        }
    }
}

private enum class Screen { ROKU, PLAIN }

private enum class CardType {
    BANNER,      // 700×370dp hero banner
    WIDE,        // 300×170dp cinematic
    LANDSCAPE,   // 220×140dp standard
    CONTINUE,    // 220×140dp with progress bar
    PORTRAIT,    // 150×220dp tall poster
    MINI,        // 100×120dp compact
}

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
    RowDef("Quick Picks",          SampleData.quickPicks,       CardType.MINI,      100.dp, 120.dp, 12.dp),
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

// ─────────────────────────────────────────────────────────────────────────────
// Main screen — switches between Roku library and plain Compose lists
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StreamFocusDemoScreen() {
    var activeScreen by remember { mutableStateOf(Screen.ROKU) }
    val rokuFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { rokuFocusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
    ) {
        Sidebar(
            activeScreen = activeScreen,
            onScreenSelect = { activeScreen = it }
        )

        when (activeScreen) {
            Screen.ROKU -> RokuContent(rokuFocusRequester)
            Screen.PLAIN -> PlainContent()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Roku library content — fixed-focus navigation
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RokuContent(focusRequester: FocusRequester) {
    val rowStates = allRows.map { row ->
        rememberRokuFocusListState(itemCount = row.items.size, focusSlot = 0)
    }
    val rowConfigs = allRows.mapIndexed { i, row ->
        RokuColumnRowConfig(
            state = rowStates[i],
            itemWidth = row.itemWidth,
            itemHeight = row.itemHeight,
            itemSpacing = row.itemSpacing,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "RokuFocus Library",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
        )
        Text(
            text = "$ROW_COUNT rows \u00b7 6 card types \u00b7 fixed-focus navigation",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
        )

        RokuLazyColumn(
            rows = rowConfigs,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester),
            config = RokuFocusConfig(wrapAround = false),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            rowSpacing = 8.dp,
            rowHeader = { rowIndex, isRowFocused ->
                Text(
                    text = allRows[rowIndex].title,
                    color = if (isRowFocused) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
            }
        ) { rowIndex, itemIndex, isFocused ->
            val movie = allRows[rowIndex].items[itemIndex]
            when (allRows[rowIndex].cardType) {
                CardType.BANNER    -> BannerCard(movie = movie, isFocused = isFocused)
                CardType.WIDE      -> WideCard(movie = movie, isFocused = isFocused)
                CardType.LANDSCAPE -> MovieCard(movie = movie, isFocused = isFocused)
                CardType.CONTINUE  -> ContinueWatchingCard(movie = movie, isFocused = isFocused)
                CardType.PORTRAIT  -> PortraitCard(movie = movie, isFocused = isFocused)
                CardType.MINI      -> MiniCard(movie = movie, isFocused = isFocused)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plain Compose content — standard LazyColumn + LazyRow for comparison
// Each card is individually focusable (default Android TV pattern).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlainContent() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Plain Compose",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
        )
        Text(
            text = "$ROW_COUNT rows \u00b7 standard LazyColumn + LazyRow",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
        )

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
                                when (row.cardType) {
                                    CardType.BANNER    -> BannerCard(movie = movie, isFocused = focused)
                                    CardType.WIDE      -> WideCard(movie = movie, isFocused = focused)
                                    CardType.LANDSCAPE -> MovieCard(movie = movie, isFocused = focused)
                                    CardType.CONTINUE  -> ContinueWatchingCard(movie = movie, isFocused = focused)
                                    CardType.PORTRAIT  -> PortraitCard(movie = movie, isFocused = focused)
                                    CardType.MINI      -> MiniCard(movie = movie, isFocused = focused)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sidebar — screen switcher (Roku / Plain) + decorative nav items
// ─────────────────────────────────────────────────────────────────────────────

private data class NavItem(val label: String, val icon: String)

private val decorativeNavItems = listOf(
    NavItem("Search",   "\u2315"),   // ⌕
    NavItem("Library",  "\u2630"),   // ☰
    NavItem("Settings", "\u2699"),   // ⚙
)

@Composable
private fun Sidebar(
    activeScreen: Screen,
    onScreenSelect: (Screen) -> Unit
) {
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

        // Screen switcher
        SidebarItem(
            item = NavItem("Roku", "R"),
            isActive = activeScreen == Screen.ROKU,
            onClick = { onScreenSelect(Screen.ROKU) }
        )
        SidebarItem(
            item = NavItem("Plain", "P"),
            isActive = activeScreen == Screen.PLAIN,
            onClick = { onScreenSelect(Screen.PLAIN) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Decorative nav items
        decorativeNavItems.forEach { item ->
            SidebarItem(item = item)
        }
    }
}

@Composable
private fun SidebarItem(
    item: NavItem,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val bgTarget = when {
        focused -> Color.White
        isActive -> Color.White.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    val bg by animateColorAsState(bgTarget, tween(180), label = "sidebar_bg")

    val fgTarget = when {
        focused -> Color.Black
        isActive -> Color.White.copy(alpha = 0.9f)
        else -> Color.White.copy(alpha = 0.55f)
    }
    val fg by animateColorAsState(fgTarget, tween(180), label = "sidebar_fg")

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(180),
        label = "sidebar_scale"
    )

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) { onClick() }
                } else {
                    Modifier.focusable()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = item.icon,
                color = fg,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.label,
                color = fg,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StandaloneRowExample() {
    val state = rememberRokuFocusListState(
        itemCount = SampleData.trending.size,
        focusSlot = 0
    )
    RokuLazyRow(
        state = state,
        itemWidth = 220.dp,
        itemSpacing = 14.dp,
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp)
    ) { index, isFocused ->
        MovieCard(movie = SampleData.trending[index], isFocused = isFocused)
    }
}
