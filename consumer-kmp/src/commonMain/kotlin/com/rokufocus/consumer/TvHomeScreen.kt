package com.rokufocus.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rokufocus.DefaultFocusHighlight
import com.rokufocus.RokuAnimationSpec
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
import com.rokufocus.rememberRokuFocusListState
import androidx.compose.ui.tooling.preview.Preview

private data class Title(val id: Int, val name: String)

private val trending = List(30) { Title(it, "Trending ${it + 1}") }
private val newReleases = List(24) { Title(it, "New ${it + 1}") }
private val hero = List(6) { Title(it, "Hero ${it + 1}") }

/**
 * Full OTT layout built entirely from commonMain. If this compiles for every declared
 * target, the library is genuinely multiplatform-consumable.
 */
@Composable
fun TvHomeScreen(modifier: Modifier = Modifier) {
    RokuLazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFF0E0E0E)),
        config = RokuFocusConfig(
            highlightAnimationSpec = RokuAnimationSpec.Smooth,
            wrapAround = true
        ),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        rowSpacing = 12.dp,
        onItemClicked = { _, _ -> }
    ) {
        row(
            itemWidth = 580.dp,
            itemHeight = 310.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused -> RowHeader("Featured", isRowFocused) }
        ) {
            items(hero, key = { it.id }) { title, isFocused ->
                Card(title.name, 580.dp, 310.dp, isFocused)
            }
        }

        row(
            itemWidth = 220.dp,
            itemHeight = 140.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused -> RowHeader("Trending Now", isRowFocused) }
        ) {
            items(trending, key = { it.id }) { title, isFocused ->
                Card(title.name, 220.dp, 140.dp, isFocused)
            }
        }

        row(
            itemWidth = 150.dp,
            itemHeight = 220.dp,
            headerHeight = 30.dp,
            focusSlot = 2,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            header = { isRowFocused -> RowHeader("New Releases", isRowFocused) }
        ) {
            items(newReleases, key = { it.id }) { title, isFocused ->
                Card(title.name, 150.dp, 220.dp, isFocused)
            }
        }
    }
}

/** Standalone row, DSL variant — item width is auto-measured. */
@Composable
fun TrendingRow(modifier: Modifier = Modifier) {
    RokuLazyRow(
        modifier = modifier,
        itemSpacing = 14.dp,
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
        focusHighlight = { isFocused ->
            DefaultFocusHighlight(
                isFocused = isFocused,
                borderColor = Color.Cyan,
                borderWidth = 4.dp,
                cornerRadius = 16.dp,
                animateScale = true
            )
        },
        onItemSelected = { },
        onItemClicked = { }
    ) {
        items(trending, key = { it.id }) { title, isFocused ->
            Card(title.name, 220.dp, 140.dp, isFocused)
        }
    }
}

/** Standalone row, state variant — explicit width plus programmatic control. */
@Composable
fun ControlledRow(modifier: Modifier = Modifier) {
    val state = rememberRokuFocusListState(
        itemCount = trending.size,
        initialIndex = 5,
        focusSlot = 1
    )

    RokuLazyRow(
        state = state,
        itemWidth = 220.dp,
        modifier = modifier,
        itemSpacing = 14.dp,
        config = RokuFocusConfig(keyRepeatAccelAfter = 0),
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
    ) { index, isFocused ->
        Card(trending[index].name, 220.dp, 140.dp, isFocused)
    }
}

@Composable
private fun RowHeader(text: String, isRowFocused: Boolean) {
    BasicText(
        text = text,
        style = TextStyle(
            color = if (isRowFocused) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 18.sp
        ),
        modifier = Modifier.padding(start = 24.dp, bottom = 8.dp).height(30.dp)
    )
}

@Composable
private fun Card(label: String, width: Dp, height: Dp, isFocused: Boolean) {
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text = label, style = TextStyle(color = Color.White, fontSize = 14.sp))
    }
}

@Preview
@Composable
private fun TvHomeScreenPreview() {
    TvHomeScreen()
}

@Preview
@Composable
private fun TrendingRowPreview() {
    Box(modifier = Modifier.background(Color(0xFF0E0E0E)).height(180.dp)) {
        TrendingRow()
    }
}

@Preview
@Composable
private fun ControlledRowPreview() {
    Box(modifier = Modifier.background(Color(0xFF0E0E0E)).height(180.dp)) {
        ControlledRow()
    }
}
