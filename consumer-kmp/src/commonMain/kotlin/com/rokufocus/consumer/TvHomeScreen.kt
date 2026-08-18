package com.rokufocus.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.rokufocus.RokuFocusEscape
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
import com.rokufocus.RokuNavKey
import com.rokufocus.rememberRokuColumnState
import com.rokufocus.rememberRokuFocusListState
import androidx.compose.ui.tooling.preview.Preview

private data class Title(val id: Int, val name: String)

private val trending = List(30) { Title(it, "Trending ${it + 1}") }
private val newReleases = List(24) { Title(it, "New ${it + 1}") }
private val hero = List(6) { Title(it, "Hero ${it + 1}") }
private val genres = listOf("Action", "Drama", "Comedy", "Sci-Fi", "Docs")

/**
 * Full OTT layout built entirely from commonMain. If this compiles for every declared
 * target, the library is genuinely multiplatform-consumable.
 *
 * Exercises the 2.0 surface on purpose: a hoisted column state, keyed rows, a custom row that
 * owns its own LEFT/RIGHT, per-edge focus escape, per-row highlight shapes and accessibility
 * descriptions.
 */
@Composable
fun TvHomeScreen(modifier: Modifier = Modifier) {
    val columnState = rememberRokuColumnState()
    var genreIndex by remember { mutableIntStateOf(0) }

    RokuLazyColumn(
        modifier = modifier.fillMaxSize().background(Color(0xFF0E0E0E)),
        state = columnState,
        config = RokuFocusConfig(
            highlightAnimationSpec = RokuAnimationSpec.Smooth,
            wrapAround = true,
            // Escape sideways toward a navigation pane, never off the top or bottom.
            focusEscape = RokuFocusEscape.Horizontal
        ),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        rowSpacing = 12.dp,
        focusHighlight = { isFocused ->
            DefaultFocusHighlight(
                isFocused = isFocused,
                cornerRadius = if (rowIndex == 0) 20.dp else 12.dp
            )
        },
        onItemClicked = { _, _ -> }
    ) {
        row(
            itemWidth = 580.dp,
            itemHeight = 310.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            key = "featured",
            header = { isRowFocused -> RowHeader("Featured", isRowFocused) }
        ) {
            items(hero, key = { it.id }, contentDescription = { it.name }) { title, isFocused ->
                Card(title.name, 580.dp, 310.dp, isFocused)
            }
        }

        customRow(
            height = 44.dp,
            headerHeight = 30.dp,
            key = "genres",
            header = { isRowFocused -> RowHeader("Browse", isRowFocused) },
            onKeyEvent = { navKey ->
                when (navKey) {
                    RokuNavKey.Left -> if (genreIndex > 0) { genreIndex--; true } else false
                    RokuNavKey.Right ->
                        if (genreIndex < genres.lastIndex) { genreIndex++; true } else false

                    RokuNavKey.Enter -> true
                }
            }
        ) { isRowFocused ->
            GenreChips(selected = genreIndex, isRowFocused = isRowFocused)
        }

        row(
            itemWidth = 220.dp,
            itemHeight = 140.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            key = "trending",
            header = { isRowFocused -> RowHeader("Trending Now", isRowFocused) }
        ) {
            items(trending, key = { it.id }, contentDescription = { it.name }) { title, isFocused ->
                Card(title.name, 220.dp, 140.dp, isFocused)
            }
        }

        row(
            itemWidth = 150.dp,
            itemHeight = 220.dp,
            headerHeight = 30.dp,
            focusSlot = 2,
            initialIndex = 3,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            key = "new-releases",
            header = { isRowFocused -> RowHeader("New Releases", isRowFocused) }
        ) {
            items(newReleases, key = { it.id }, contentDescription = { it.name }) { title, isFocused ->
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
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
        itemKey = { trending[it].id },
        itemContentDescription = { trending[it].name }
    ) { index, isFocused ->
        Card(trending[index].name, 220.dp, 140.dp, isFocused)
    }
}

@Composable
private fun GenreChips(selected: Int, isRowFocused: Boolean) {
    Row(
        modifier = Modifier.padding(start = 24.dp).height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        genres.forEachIndexed { index, genre ->
            val active = isRowFocused && index == selected
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (active) Color.White else Color(0xFF1F1F1F))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = genre,
                    style = TextStyle(
                        color = if (active) Color.Black else Color.White,
                        fontSize = 14.sp
                    )
                )
            }
        }
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

@Preview
@Composable
private fun GenreChipsPreview() {
    Box(modifier = Modifier.background(Color(0xFF0E0E0E))) {
        GenreChips(selected = 1, isRowFocused = true)
    }
}
