package com.example.myapplication

object SampleData {

    private val movieTitles = listOf(
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

    private val years = listOf(
        "2008", "2010", "2014", "1999", "2018", "2019", "2015",
        "2021", "2024", "2022", "2022", "2021", "2023", "2015",
        "2012", "1999", "1994", "2023", "2014", "2021", "1994",
        "1972", "2001", "2001", "2023", "2019", "2003", "1997",
        "2000", "2009", "2019", "2001", "2018", "2022", "2023",
        "2023", "2024", "2023", "2022", "2022", "2023", "2020",
        "2023", "1994", "1977",
    )

    private val ratings = listOf(
        "9.0", "8.8", "8.7", "8.7", "8.5", "8.4", "8.1", "8.0",
        "8.3", "7.8", "8.3", "8.2", "8.5", "7.9", "8.0", "8.8",
        "8.8", "7.0", "8.5", "7.7", "9.3", "9.2", "8.8", "8.6",
        "8.7", "8.5", "8.9", "7.9", "8.5", "7.6", "8.2", "7.6",
        "8.4", "7.6", "8.0", "7.1", "7.6", "6.4", "6.3", "7.4",
        "7.3", "8.1", "7.0", "8.9", "8.6",
    )

    // Picsum photo IDs that produce nice landscape/cinematic images
    private val picsumIds = listOf(
        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 24, 25, 27, 28, 29, 36, 37, 38, 39,
        40, 41, 42, 43, 44, 47, 48, 49, 50, 51,
        52, 53, 54, 56, 57, 58, 59, 60, 61, 62,
        63, 64, 65, 66, 67,
    )

    /** Generate [count] items, cycling through base data. Uses picsum.photos for reliable images. */
    private fun generate(count: Int, startId: Int, ratingOverride: ((Int) -> String)? = null): List<MovieItem> {
        val n = movieTitles.size
        return List(count) { i ->
            val picsumId = picsumIds[i % picsumIds.size]
            MovieItem(
                id = startId + i,
                title = movieTitles[i % n],
                year = years[i % n],
                rating = ratingOverride?.invoke(i) ?: ratings[i % n],
                imageUrl = "https://picsum.photos/id/$picsumId/780/440"
            )
        }
    }

    // ── Row data for stress testing ──

    val heroBanner = generate(8, startId = 1)
    val featured = generate(20, startId = 50)
    val trending = generate(40, startId = 100)
    val continueWatching = generate(30, startId = 200) { i ->
        "${((i * 17 + 23) % 91 + 10)}%"
    }
    val newReleases = generate(35, startId = 400)
    val quickPicks = generate(50, startId = 500)
    val action = generate(40, startId = 600)
    val acclaimed = generate(20, startId = 700)
    val drama = generate(30, startId = 800)
    val sciFi = generate(35, startId = 900)
}
