package com.example.publishedconsumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rokufocus.RokuFocusConfig
import com.rokufocus.RokuLazyColumn
import com.rokufocus.RokuLazyRow
import com.rokufocus.rememberRokuFocusListState

/**
 * Shared Compose UI written in commonMain against the published roku-focus-list artifact.
 * If this compiles, a Kotlin Multiplatform consumer can use the library from common code.
 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    RokuLazyColumn(
        modifier = modifier.fillMaxSize(),
        config = RokuFocusConfig(wrapAround = true),
        contentPadding = PaddingValues(vertical = 24.dp),
        rowSpacing = 16.dp
    ) {
        row(itemWidth = 220.dp, itemHeight = 140.dp, headerHeight = 0.dp) {
            items(40) { index, isFocused -> Tile(index, isFocused) }
        }
        row(itemWidth = 150.dp, itemHeight = 220.dp, focusSlot = 1) {
            items(25) { index, isFocused -> Tile(index, isFocused) }
        }
    }
}

@Composable
fun SingleRow(modifier: Modifier = Modifier) {
    val state = rememberRokuFocusListState(itemCount = 40, initialIndex = 3, focusSlot = 1)
    RokuLazyRow(
        state = state,
        itemWidth = 220.dp,
        modifier = modifier,
        itemSpacing = 14.dp,
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) { index, isFocused ->
        Tile(index, isFocused)
    }
}

@Composable
private fun Tile(index: Int, isFocused: Boolean) {
    Box(
        modifier = Modifier
            .size(220.dp, 140.dp)
            .background(if (isFocused) Color.DarkGray else Color.Black)
    )
}
