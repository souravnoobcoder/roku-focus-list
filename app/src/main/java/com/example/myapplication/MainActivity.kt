package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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

// 10 rows — 6 card types, 308 total items
private val allRows = listOf(
    RowDef("Hero",                 SampleData.heroBanner,       CardType.BANNER,    700.dp, 370.dp, 20.dp),
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

@Composable
fun StreamFocusDemoScreen() {
    val focusRequester = remember { FocusRequester() }

    val rowStates = allRows.map { row ->
        rememberRokuFocusListState(itemCount = row.items.size, focusSlot = 0)
    }

    val rowConfigs = allRows.mapIndexed { i, row ->
        RokuColumnRowConfig(
            state = rowStates[i],
            itemWidth = row.itemWidth,
            itemHeight = row.itemHeight,
            itemSpacing = row.itemSpacing,
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp),
            headerHeight = 30.dp
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E0E))
    ) {
        Text(
            text = "StreamFocus",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, top = 24.dp)
        )
        Text(
            text = "10 rows \u00b7 6 card types \u00b7 308 items",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
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
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
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
