# RokuFocus

[![JitPack](https://jitpack.io/v/souravnoobcoder/roku-focus-list.svg)](https://jitpack.io/#souravnoobcoder/roku-focus-list)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Roku-style fixed-focus D-pad navigation for **Android TV** and **Fire TV**, built with Jetpack Compose.

The focus highlight stays locked at a fixed screen position while content smoothly scrolls behind it — exactly how Roku TV navigation works. Supports horizontal rows, full OTT grid layouts (vertical + horizontal), wrap-around, key-repeat acceleration, and custom highlight rendering.

## Why RokuFocus?

Android TV's default focus system moves focus *to* each item, causing the entire row to jump around. RokuFocus flips this: the highlight stays put, and the *content* slides. This gives users a predictable, cinematic browsing experience — the same pattern used by Roku, Apple TV, and most major streaming apps.

| Feature | RokuFocus | Default Compose TV |
|---|---|---|
| Focus model | Fixed highlight, content scrolls | Focus moves to each item |
| D-pad handling | Container-level, throttled | Per-item focusable |
| Key-repeat acceleration | Built-in | Manual |
| Wrap-around | One flag | Manual |
| Highlight customization | Lambda with `BoxScope` | Per-item focus indication |
| OTT grid layout | `RokuLazyColumn` with mixed row sizes | Manual `LazyColumn` + focus wiring |

## Installation

**Step 1.** Add JitPack to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**Step 2.** Add the dependency to your app `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.souravnoobcoder:roku-focus-list:1.0.0")
}
```

> The library has **no Material dependency** — it only pulls in Compose Foundation, UI, Animation, and Runtime.

---

## Quick Start

### 1. Single Row

The simplest usage. Item width is auto-measured from your composable — no `itemWidth` needed:

```kotlin
@Composable
fun TrendingRow() {
    RokuLazyRow(
        itemSpacing = 14.dp,
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
        onItemClicked = { index -> /* handle select */ }
    ) {
        items(movies) { movie, isFocused ->
            MovieCard(movie = movie, isFocused = isFocused)
        }
    }
}
```

That's it. D-pad LEFT/RIGHT scrolls the row. D-pad UP/DOWN passes through to adjacent composables.

### 2. Full Home Screen (Column + Rows)

Build a complete OTT layout with mixed card sizes, row headers, and a single animated highlight:

```kotlin
@Composable
fun HomeScreen() {
    RokuLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        rowSpacing = 8.dp,
    ) {
        // Hero banner row
        row(
            itemWidth = 580.dp,
            itemHeight = 310.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused ->
                Text(
                    "Hero",
                    color = if (isRowFocused) Color.White else Color.Gray,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
            }
        ) {
            items(heroMovies) { movie, isFocused ->
                BannerCard(movie = movie, isFocused = isFocused)
            }
        }

        // Standard movie row
        row(
            itemWidth = 220.dp,
            itemHeight = 140.dp,
            itemSpacing = 14.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused ->
                Text(
                    "Trending Now",
                    color = if (isRowFocused) Color.White else Color.Gray,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
            }
        ) {
            items(trendingMovies) { movie, isFocused ->
                MovieCard(movie = movie, isFocused = isFocused)
            }
        }

        // Portrait cards row
        row(
            itemWidth = 150.dp,
            itemHeight = 220.dp,
            itemSpacing = 14.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused ->
                Text(
                    "New Releases",
                    color = if (isRowFocused) Color.White else Color.Gray,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
                )
            }
        ) {
            items(newReleases) { movie, isFocused ->
                PortraitCard(movie = movie, isFocused = isFocused)
            }
        }
    }
}
```

`RokuLazyColumn` handles everything: D-pad UP/DOWN moves between rows, LEFT/RIGHT scrolls within the active row, and a single highlight overlay animates smoothly across rows of different sizes.

### 3. Row with External State

When you need programmatic control (jump to an index, read current selection):

```kotlin
@Composable
fun ControlledRow() {
    val state = rememberRokuFocusListState(
        itemCount = movies.size,
        initialIndex = 5,   // start at the 6th item
        focusSlot = 0       // highlight on leftmost slot
    )

    // Jump to item 10 on some event
    LaunchedEffect(someEvent) {
        state.scrollTo(10)
    }

    RokuLazyRow(
        state = state,
        itemWidth = 220.dp,
        itemSpacing = 14.dp,
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp)
    ) { index, isFocused ->
        MovieCard(movie = movies[index], isFocused = isFocused)
    }
}
```

---

## Configuration

Customize navigation behavior globally or per-component:

```kotlin
val config = RokuFocusConfig(
    highlightAnimationSpec = tween(200, easing = FastOutSlowInEasing),
    keyRepeatDelayMs = 150L,
    keyRepeatAccelAfter = 3,       // accelerate after 3 consecutive presses
    keyRepeatFastDelayMs = 50L,    // fast speed once accelerated
    wrapAround = true,             // wrap from last to first
    hapticFeedback = true,         // vibrate at boundaries
    allowFocusEscape = true        // let focus leave the list at edges
)

RokuLazyRow(config = config, ...) { ... }
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `highlightAnimationSpec` | `AnimationSpec<Float>` | `tween(200ms)` | Highlight and scroll animation |
| `keyRepeatDelayMs` | `Long` | `150` | Throttle delay for held D-pad keys (ms) |
| `keyRepeatAccelAfter` | `Int` | `3` | After N presses, switch to fast delay. 0 = disabled |
| `keyRepeatFastDelayMs` | `Long` | `50` | Fast repeat delay after acceleration |
| `wrapAround` | `Boolean` | `false` | Wrap from last item to first and vice versa |
| `hapticFeedback` | `Boolean` | `true` | Vibrate on boundary hit |
| `allowFocusEscape` | `Boolean` | `true` | Let D-pad at edges pass focus to adjacent composables |

Built-in animation presets:

```kotlin
RokuAnimationSpec.Default  // tween(300ms) — balanced
RokuAnimationSpec.Fast     // tween(150ms) — snappy
RokuAnimationSpec.Smooth   // spring(0.8, 300) — organic
```

---

## Custom Focus Highlight

The default is a white rounded border. Replace it with anything:

```kotlin
// Fully custom highlight
RokuLazyRow(
    focusHighlight = { isFocused ->
        if (isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(4.dp, Color.Blue, RoundedCornerShape(16.dp))
            )
        }
    },
    ...
) { ... }

// Or tweak the default
RokuLazyRow(
    focusHighlight = { isFocused ->
        DefaultFocusHighlight(
            isFocused = isFocused,
            borderColor = Color.Cyan,
            borderWidth = 4.dp,
            cornerRadius = 16.dp,
            overflow = 8.dp,        // how far the border extends outside the card
            animateScale = true     // subtle scale pulse on focus
        )
    },
    ...
) { ... }
```

---

## Focus Slot

Control where the highlight sits within the visible window:

```kotlin
// Highlight on the leftmost visible item (default)
RokuLazyRow(focusSlot = 0, ...) { ... }

// Highlight on the 3rd visible slot
RokuLazyRow(focusSlot = 2, ...) { ... }
```

At list edges, the highlight automatically shifts to track the actual item position — no empty space is ever shown.

---

## API Reference

### Components

| Component | Description |
|---|---|
| `RokuLazyRow` | Horizontal fixed-focus row. DSL variant auto-measures width; state variant takes explicit `itemWidth`. |
| `RokuLazyColumn` | Vertical + horizontal OTT grid. DSL variant manages state internally; state variant takes `List<RokuColumnRowConfig>`. |
| `DefaultFocusHighlight` | Default white rounded-border highlight. `BoxScope` extension, fully replaceable. |

### State

| API | Description |
|---|---|
| `rememberRokuFocusListState(itemCount, initialIndex, focusSlot)` | Create remembered state for a row. |
| `state.selectedIndex` | Current selected item index. |
| `state.moveNext()` / `state.movePrevious()` | Programmatically navigate. Returns `true` if moved. |
| `state.scrollTo(index)` | Jump to a specific index. |
| `state.canScrollForward` / `state.canScrollBackward` | Check if navigation is possible. |
| `state.updateItemCount(count)` | Update item count (e.g., when data changes). |

### Callbacks

| Callback | Available on | Description |
|---|---|---|
| `onItemSelected` | Row, Column | Fires when the selected index changes. |
| `onItemClicked` | Row, Column | Fires on Enter / DpadCenter press. |
| `onFocusEnter` | Row only | Fires when the row gains focus. |
| `onFocusExit` | Row only | Fires when the row loses focus. |

---

## How It Works

1. `RokuLazyRow` / `RokuLazyColumn` is a **single focusable composable** — individual items are never focused
2. D-pad events are intercepted at the container level with key-repeat throttling
3. Selection is tracked via `selectedIndex` in `RokuFocusListState`, not the Compose focus system
4. Content scrolls via `LazyRow(userScrollEnabled = false)` + `animateScrollToItem()` — Compose handles recycling
5. The highlight overlay is positioned with `graphicsLayer { translationX/Y }` (GPU-only, no re-layout)
6. At list edges, overflow correction shifts the highlight to match the actual item position
7. In `RokuLazyColumn`, one global highlight animates X, Y, width, and height between rows of different card sizes

---

## Requirements

- **minSdk** 24 (Android 7.0+)
- **Jetpack Compose** BOM 2024.09.00 or newer
- **No Material dependency** — works with any design system

## Demo App

The `app/` module contains a full demo with 100 rows, 6 card types, and multiple screen modes (Column, Row, State, Wrap-Around, Plain comparison). Clone the repo and run it on an Android TV emulator or device.

## License

```
Copyright 2024 RokuFocus contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
