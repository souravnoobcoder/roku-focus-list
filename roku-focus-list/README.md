# roku-focus-list

A Jetpack Compose library that provides Roku-style fixed-focus horizontal list navigation for Android TV and Fire TV. The focus highlight stays at a fixed screen position while the entire list content slides behind it — the Roku TV navigation model.

![Demo](demo.gif)

## Installation

Add the library to your project:

```kotlin
// settings.gradle.kts
include(":roku-focus-list")

// app/build.gradle.kts
dependencies {
    implementation(project(":roku-focus-list"))
}
```

For published artifacts (coming soon):

```kotlin
dependencies {
    implementation("com.rokufocus:roku-focus-list:1.0.0")
}
```

## Basic Usage

```kotlin
@Composable
fun MyTvRow() {
    val state = rememberRokuFocusListState(
        itemCount = 20,
        visibleCount = 5,
        focusSlot = 0  // focus anchored to leftmost slot
    )

    RokuLazyRow(
        state = state,
        itemWidth = 220.dp,
        itemSpacing = 16.dp,
        contentPadding = PaddingValues(horizontal = 48.dp),
        onItemClicked = { index -> /* handle click */ }
    ) { index, isFocused ->
        // Your card composable
        MyCard(title = "Item $index", isFocused = isFocused)
    }
}
```

## Configuration

| Parameter | Type | Default | Description |
|---|---|---|---|
| `animationSpec` | `AnimationSpec<Float>` | `tween(300ms)` | Animation for content sliding |
| `highlightAnimationSpec` | `AnimationSpec<Float>` | `tween(300ms)` | Animation for highlight movement at edges |
| `keyRepeatDelayMs` | `Long` | `150L` | Throttle delay for held D-pad keys (ms) |
| `wrapAround` | `Boolean` | `false` | Wrap from last item to first and vice versa |
| `hapticFeedback` | `Boolean` | `true` | Vibrate on boundary hit |

```kotlin
val config = RokuFocusConfig(
    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
    keyRepeatDelayMs = 100L,
    wrapAround = true,
    hapticFeedback = false
)

RokuLazyRow(
    state = state,
    config = config,
    itemWidth = 200.dp,
    ...
)
```

## Custom Focus Highlight

Replace the default white border with your own. The `focusHighlight` lambda has `BoxScope` receiver so `matchParentSize()` is available:

```kotlin
RokuLazyRow(
    state = state,
    itemWidth = 220.dp,
    focusHighlight = { isFocused ->
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 4.dp,
                    color = if (isFocused) Color.Blue else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        )
    }
) { index, isFocused ->
    MyCard(index, isFocused)
}
```

Or use the default with custom colors:

```kotlin
focusHighlight = { isFocused ->
    DefaultFocusHighlight(
        isFocused = isFocused,
        borderColor = Color.Cyan,
        borderWidth = 4.dp,
        cornerRadius = 16.dp,
        glowAlpha = 0.4f
    )
}
```

## Focus Slot

The `focusSlot` parameter controls where the fixed focus highlight sits:

- `focusSlot = 0` — leftmost visible item (default)
- `focusSlot = 2` — third slot from left (Roku-style center-ish)
- `focusSlot = visibleCount / 2` — true center

At list edges, the highlight position adjusts automatically so no empty space is shown.

## Multiple Rows (TV Home Screen)

Stack rows in a `Column`. D-pad UP/DOWN naturally moves focus between rows:

```kotlin
Column {
    ContentRow(title = "Trending", itemCount = 15, focusSlot = 0)
    ContentRow(title = "Continue Watching", itemCount = 10, focusSlot = 2)
    ContentRow(title = "Recommended", itemCount = 20, focusSlot = 0)
}
```

Each row remembers its own selection position independently.

## API Reference

### `RokuFocusListState`

State holder for the focus list.

| Property / Method | Type | Description |
|---|---|---|
| `selectedIndex` | `Int` | Currently selected item index (read-only) |
| `itemCount` | `Int` | Total number of items (read-only) |
| `visibleCount` | `Int` | Items visible on screen |
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
    visibleCount: Int = 5,
    focusSlot: Int = 0
): RokuFocusListState
```

### `RokuLazyRow`

```kotlin
@Composable
fun RokuLazyRow(
    state: RokuFocusListState,
    modifier: Modifier = Modifier,
    config: RokuFocusConfig = RokuFocusConfig(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemWidth: Dp,
    itemSpacing: Dp = 12.dp,
    focusHighlight: @Composable BoxScope.(isFocused: Boolean) -> Unit = { DefaultFocusHighlight(it) },
    onItemSelected: ((index: Int) -> Unit)? = null,
    onItemClicked: ((index: Int) -> Unit)? = null,
    onFocusEnter: (() -> Unit)? = null,
    onFocusExit: (() -> Unit)? = null,
    itemContent: @Composable (index: Int, isFocused: Boolean) -> Unit
)
```

### `RokuFocusConfig`

```kotlin
data class RokuFocusConfig(
    val animationSpec: AnimationSpec<Float>,
    val highlightAnimationSpec: AnimationSpec<Float>,
    val keyRepeatDelayMs: Long,
    val wrapAround: Boolean,
    val hapticFeedback: Boolean
)
```

### `DefaultFocusHighlight`

A `BoxScope` extension composable rendering a white rounded border with subtle glow:

```kotlin
@Composable
fun BoxScope.DefaultFocusHighlight(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White,
    unfocusedAlpha: Float = 0.0f,
    focusedAlpha: Float = 1.0f,
    borderWidth: Dp = 3.dp,
    cornerRadius: Dp = 12.dp,
    glowAlpha: Float = 0.3f
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

Reusable key event handler modifier:

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

1. The entire `RokuLazyRow` is a single focusable composable — individual items are NOT focusable
2. D-pad LEFT/RIGHT events are intercepted at the container level
3. `selectedIndex` is maintained internally (not via Compose focus system)
4. Internally uses `LazyRow(userScrollEnabled = false)` with `LazyListState.animateScrollToItem()` for programmatic scrolling — Compose handles all item recycling and composition
5. The focus highlight overlay uses `graphicsLayer { translationX }` (draw-phase only) and never moves during normal scrolling — only adjusts at list edges
6. D-pad UP/DOWN events pass through for vertical navigation between rows

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
