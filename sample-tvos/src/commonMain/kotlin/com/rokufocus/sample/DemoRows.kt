package com.rokufocus.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Android demo's home screen, card sizes and all — a hero row of 580×310 banners, 300×170
 * wide cards, 220×140 landscape cards and 150×220 posters — so the two TVs show the same layout
 * and a vertical move can be judged against rows of different widths.
 */
internal enum class DemoCardKind { Banner, Wide, Landscape, Portrait }

internal data class DemoTitle(val id: Int, val name: String, val year: String)

internal data class DemoRow(
    val title: String,
    val items: List<DemoTitle>,
    val kind: DemoCardKind,
    val itemWidth: Dp,
    val itemHeight: Dp,
    val itemSpacing: Dp,
)

private val demoTitles = listOf(
    "The Dark Knight", "Inception", "Interstellar", "The Matrix",
    "Infinity War", "Endgame", "Mad Max: Fury Road",
    "Dune", "Dune: Part Two", "The Batman", "Top Gun: Maverick",
    "Spider-Man: NWH", "Oppenheimer", "Star Wars: TFA",
    "The Avengers", "Fight Club", "Forrest Gump", "Barbie",
    "Whiplash", "Free Guy", "Shawshank Redemption", "The Godfather",
    "LOTR: Fellowship", "Spirited Away", "Spider-Verse 2",
    "Parasite", "LOTR: Return of King", "Titanic", "Gladiator",
    "Avatar", "Joker", "Harry Potter", "Spider-Verse",
    "Avatar: Way of Water", "Guardians Vol. 3", "Super Mario Bros.",
    "Inside Out 2", "Ant-Man 3", "Jurassic World",
    "Turning Red", "Killers of Flower Moon", "Soul", "Fast X",
    "Pulp Fiction", "Star Wars: A New Hope",
)

private val demoYears = listOf(
    "2008", "2010", "2014", "1999", "2018", "2019", "2015",
    "2021", "2024", "2022", "2022", "2021", "2023", "2015",
    "2012", "1999", "1994", "2023", "2014", "2021", "1994",
    "1972", "2001", "2001", "2023", "2019", "2003", "1997",
    "2000", "2009", "2019", "2001", "2018", "2022", "2023",
    "2023", "2024", "2023", "2022", "2022", "2023", "2020",
    "2023", "1994", "1977",
)

private fun titles(count: Int, startId: Int): List<DemoTitle> = List(count) { i ->
    DemoTitle(startId + i, demoTitles[i % demoTitles.size], demoYears[i % demoYears.size])
}

private val demoBaseRows = listOf(
    DemoRow("Hero", titles(8, 1), DemoCardKind.Banner, 580.dp, 310.dp, 20.dp),
    DemoRow("Featured", titles(20, 50), DemoCardKind.Wide, 300.dp, 170.dp, 16.dp),
    DemoRow("Trending Now", titles(40, 100), DemoCardKind.Landscape, 220.dp, 140.dp, 14.dp),
    DemoRow("Continue Watching", titles(30, 200), DemoCardKind.Landscape, 220.dp, 140.dp, 14.dp),
    DemoRow("New Releases", titles(35, 400), DemoCardKind.Portrait, 150.dp, 220.dp, 14.dp),
    DemoRow("Action & Adventure", titles(40, 600), DemoCardKind.Landscape, 220.dp, 140.dp, 14.dp),
    DemoRow("Critically Acclaimed", titles(20, 700), DemoCardKind.Wide, 300.dp, 170.dp, 16.dp),
    DemoRow("Drama", titles(30, 800), DemoCardKind.Portrait, 150.dp, 220.dp, 14.dp),
    DemoRow("Sci-Fi & Fantasy", titles(35, 900), DemoCardKind.Landscape, 220.dp, 140.dp, 14.dp),
)

private const val DemoRowCount = 36

internal val demoRows: List<DemoRow> = List(DemoRowCount) { i ->
    val base = demoBaseRows[i % demoBaseRows.size]
    base.copy(title = "${i + 1}. ${base.title}")
}

private val cardTints = listOf(
    Color(0xFF2B3A67), Color(0xFF3D2B56), Color(0xFF1F4E4A), Color(0xFF5A2E2E),
    Color(0xFF2E4A2E), Color(0xFF4A3A1F), Color(0xFF1F3A4A), Color(0xFF4A1F3A),
)

/** A poster without an image: a tinted gradient, the title, the year. Focus dims nothing — the highlight and the lean carry it. */
@Composable
internal fun DemoCard(row: DemoRow, item: DemoTitle, isFocused: Boolean) {
    val tint = cardTints[item.id % cardTints.size]
    val titleSize = if (row.kind == DemoCardKind.Banner) 28.sp else 15.sp
    Box(
        modifier = Modifier
            .size(row.itemWidth, row.itemHeight)
            .clip(RoundedCornerShape(if (row.kind == DemoCardKind.Banner) 14.dp else 10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = if (isFocused) 1f else 0.85f), tint.copy(alpha = 0.55f))
                )
            ),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        ) {
            BasicText(
                text = item.name,
                style = TextStyle(color = Color.White, fontSize = titleSize, fontWeight = FontWeight.SemiBold),
            )
            BasicText(
                text = item.year,
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Preview
@Composable
private fun DemoCardPreview() {
    Row(modifier = Modifier.background(Color(0xFF0B0B0B)).padding(16.dp)) {
        demoBaseRows.take(5).distinctBy { it.kind }.forEach { row ->
            Box(modifier = Modifier.padding(end = 14.dp)) {
                DemoCard(row, row.items.first(), isFocused = row.kind == DemoCardKind.Landscape)
            }
        }
    }
}
