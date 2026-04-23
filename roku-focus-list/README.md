# roku-focus-list

[![](https://jitpack.io/v/souravnoobcoder/roku-focus-list.svg)](https://jitpack.io/#souravnoobcoder/roku-focus-list)

A Jetpack Compose library that provides Roku-style fixed-focus navigation for Android TV and Fire TV. The focus highlight stays at a fixed screen position while content scrolls behind it — both horizontally (within rows) and vertically (between rows).

## Installation

### JitPack

Add JitPack to your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.souravnoobcoder:roku-focus-list:1.0.0")
}
```

## Quick Start

### Simple Row (DSL — auto-measured width)

```kotlin
@Composable
fun MyTvRow() {
    RokuLazyRow(
        itemSpacing = 14.dp,
        contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
        onItemClicked = { index -> /* handle click */ }
    ) {
        items(movies) { movie, isFocused ->
            MovieCard(movie = movie, isFocused = isFocused)
        }
    }
}
```

### Row with External State

```kotlin
@Composable
fun MyControlledRow() {
    val state = rememberRokuFocusListState(
        itemCount = movies.size,
        initialIndex = 0,
        focusSlot = 0
    )

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

### Full OTT Layout (Column + Rows)

```kotlin
@Composable
fun MyHomeScreen() {
    RokuLazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        rowSpacing = 8.dp,
    ) {
        row(
            itemWidth = 580.dp, itemHeight = 310.dp, itemSpacing = 20.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused -> Text("Hero", color = Color.White) }
        ) {
            items(heroMovies) { movie, isFocused ->
                BannerCard(movie = movie, isFocused = isFocused)
            }
        }
        row(
            itemWidth = 220.dp, itemHeight = 140.dp, itemSpacing = 14.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused -> Text("Trending", color = Color.White) }
        ) {
            items(trendingMovies) { movie, isFocused ->
                MovieCard(movie = movie, isFocused = isFocused)
            }
        }
    }
}
```

## Configuration

```kotlin
val config = RokuFocusConfig(
    highlightAnimationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    keyRepeatDelayMs = 150L,
    keyRepeatAccelAfter = 3,
    keyRepeatFastDelayMs = 50L,
    wrapAround = false,
    hapticFeedback = true,
    allowFocusEscape = true
)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `highlightAnimationSpec` | `AnimationSpec<Float>` | `tween(200ms)` | Animation for highlight and content sliding |
| `keyRepeatDelayMs` | `Long` | `150L` | Throttle delay for held D-pad keys (ms) |
| `keyRepeatAccelAfter` | `Int` | `3` | After N consecutive presses, switch to fast delay. 0 = disabled |
| `keyRepeatFastDelayMs` | `Long` | `50L` | Faster repeat delay after acceleration kicks in |
| `wrapAround` | `Boolean` | `false` | Wrap from last item to first and vice versa |
| `hapticFeedback` | `Boolean` | `true` | Vibrate on boundary hit |
| `allowFocusEscape` | `Boolean` | `true` | Let D-pad at edges pass focus to adjacent composables |

## Custom Focus Highlight

Replace the default white border with your own:

```kotlin
RokuLazyRow(
    itemSpacing = 14.dp,
    focusHighlight = { isFocused ->
        if (isFocused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(4.dp, Color.Blue, RoundedCornerShape(16.dp))
            )
        }
    }
) {
    items(movies) { movie, isFocused ->
        MovieCard(movie, isFocused)
    }
}
```

Or use the default with custom parameters:

```kotlin
focusHighlight = { isFocused ->
    DefaultFocusHighlight(
        isFocused = isFocused,
        borderColor = Color.Cyan,
        borderWidth = 4.dp,
        cornerRadius = 16.dp,
        overflow = 8.dp,
        animateScale = true
    )
}
```

## Focus Slot

The `focusSlot` parameter controls where the fixed focus highlight sits:

- `focusSlot = 0` — leftmost visible item (default)
- `focusSlot = 2` — third slot from left

At list edges, the highlight position adjusts automatically so no empty space is shown.

## API Reference

### `RokuFocusListState`

State holder for focus list navigation.

| Property / Method | Type | Description |
|---|---|---|
| `selectedIndex` | `Int` | Currently selected item index (read-only) |
| `itemCount` | `Int` | Total number of items (read-only) |
| `visibleCount` | `Int` | Items visible on screen (auto-computed) |
| `focusSlot` | `Int` | Fixed focus slot position (0-indexed) |
| `windowStart` | `Int` | Index of first visible item |
| `highlightSlot` | `Int` | Visible position of highlight (= selectedIndex - windowStart) |
| `canScrollForward` | `Boolean` | Whether more items exist ahead |
| `canScrollBackward` | `Boolean` | Whether more items exist behind |
| `moveNext()` | `Boolean` | Advance selection by one. Returns true if moved. |
| `movePrevious()` | `Boolean` | Move selection back by one. Returns true if moved. |
| `scrollTo(index)` | `Unit` | Jump to specific index (clamped to valid range) |
| `updateItemCount(count)` | `Unit` | Update total item count, clamping selection |

### `rememberRokuFocusListState()`

```kotlin
@Composable
fun rememberRokuFocusListState(
    itemCount: Int,
    initialIndex: Int = 0,
    focusSlot: Int = 0
): RokuFocusListState
```

### `RokuLazyRow` (DSL)

```kotlin
@Composable
fun RokuLazyRow(
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 12.dp,
    focusSlot: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    content: RokuItemScope.() -> Unit
)
```

### `RokuLazyRow` (State-based)

```kotlin
@Composable
fun RokuLazyRow(
    state: RokuFocusListState,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 12.dp,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
)
```

### `RokuLazyColumn` (DSL)

```kotlin
@Composable
fun RokuLazyColumn(
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    initialRowIndex: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    content: RokuLazyColumnScope.() -> Unit
)
```

### `RokuLazyColumn` (State-based)

```kotlin
@Composable
fun RokuLazyColumn(
    rows: List<RokuColumnRowConfig>,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = DefaultRokuFocusConfig,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowSpacing: Dp = 24.dp,
    initialRowIndex: Int = 0,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    onItemClicked: ((rowIndex: Int, itemIndex: Int) -> Unit)? = null,
    rowHeader: (@Composable (rowIndex: Int, isRowFocused: Boolean) -> Unit)? = null,
    itemContent: @Composable (rowIndex: Int, itemIndex: Int, isFocused: Boolean) -> Unit
)
```

### `DefaultFocusHighlight`

```kotlin
@Composable
fun BoxScope.DefaultFocusHighlight(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White,
    borderWidth: Dp = 3.dp,
    cornerRadius: Dp = 12.dp,
    overflow: Dp = 6.dp,
    animateScale: Boolean = false
)
```

### `RokuAnimationSpec`

Pre-defined animation specs:

| Spec | Description |
|---|---|
| `RokuAnimationSpec.Default` | `tween(300ms, FastOutSlowInEasing)` |
| `RokuAnimationSpec.Fast` | `tween(150ms, FastOutSlowInEasing)` |
| `RokuAnimationSpec.Smooth` | `spring(damping=0.8, stiffness=300)` |

### `Modifier.rokuKeyHandler()`

Reusable key event handler modifier for standalone rows:

```kotlin
fun Modifier.rokuKeyHandler(
    state: RokuFocusListState,
    config: RokuFocusConfig,
    orientation: Orientation = Orientation.Horizontal,
    onSelected: ((Int) -> Unit)? = null,
    onClicked: ((Int) -> Unit)? = null,
    onBoundaryHit: (() -> Unit)? = null
): Modifier
```

## How It Works

1. The entire `RokuLazyRow` / `RokuLazyColumn` is a single focusable composable — individual items are NOT focusable
2. D-pad LEFT/RIGHT events are intercepted at the container level
3. `selectedIndex` is maintained internally (not via Compose focus system)
4. Internally uses `LazyRow(userScrollEnabled = false)` with `animateScrollToItem()` for programmatic scrolling — Compose handles all item recycling and composition
5. The focus highlight overlay uses `graphicsLayer { translationX/Y }` (draw-phase only) and never moves during normal scrolling — only adjusts at list edges
6. D-pad UP/DOWN events pass through for vertical navigation between rows (standalone) or are handled by the column (in `RokuLazyColumn`)

## Requirements

- minSdk 24
- Jetpack Compose (BOM 2024.09.00+)
- No Material dependency required

## License

```
Copyright 2024 roku-focus-list contributors

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
