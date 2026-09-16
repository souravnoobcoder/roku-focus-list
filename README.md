# RokuFocus

[![Maven Central](https://img.shields.io/maven-central/v/io.github.souravnoobcoder/roku-focus-list.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.souravnoobcoder/roku-focus-list)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.10.3-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Roku-style fixed-focus D-pad navigation for **Android TV**, **Fire TV**, and any other Compose
target — built as a **Kotlin Multiplatform / Compose Multiplatform** library.

The focus highlight stays locked at a fixed screen position while content smoothly scrolls behind
it — exactly how Roku TV navigation works. Supports horizontal rows, full OTT grid layouts
(vertical + horizontal), heterogeneous rows, wrap-around, key-repeat acceleration, and custom
highlight rendering.

## Why RokuFocus?

Android TV's default focus system moves focus *to* each item, causing the entire row to jump
around. RokuFocus flips this: the highlight stays put, and the *content* slides. This gives users a
predictable, cinematic browsing experience — the same pattern used by Roku, Apple TV, and most
major streaming apps.

| Feature | RokuFocus | Default Compose TV |
|---|---|---|
| Focus model | Fixed highlight, content scrolls | Focus moves to each item |
| D-pad handling | Container-level, throttled | Per-item focusable |
| Key-repeat acceleration | Built-in | Manual |
| Wrap-around | One flag | Manual |
| Focus escape | Per edge | Manual |
| Highlight customization | One lambda, with row/item context | Per-item focus indication |
| OTT grid layout | `RokuLazyColumn` with mixed row sizes | Manual `LazyColumn` + focus wiring |
| Non-uniform rows | `customRow` escape hatch | Manual |
| State restoration | `rememberSaveable`-backed | Manual |

---

## Kotlin Multiplatform

The entire library lives in `commonMain`. There is no `androidMain` source set — nothing in the
library needs a platform API — so **a Kotlin Multiplatform project can depend on it directly from
`commonMain`** and write shared Compose UI against it.

Android-only projects are unaffected: Gradle module metadata resolves the Android variant to an AAR
that depends on Google's `androidx.compose.*` artifacts, exactly as before.

### Supported targets

| Target | Status | Notes |
|---|---|---|
| `androidTarget` (Android, Android TV, Fire TV) | Supported, verified | The primary use case. minSdk 23. |
| `jvm("desktop")` (Windows / macOS / Linux) | Supported, verified | Arrow keys and Enter work. Needs JDK 11+. |
| `tvosArm64`, `tvosSimulatorArm64` (Apple TV) | Supported, verified on device | Needs one extra plugin line — see [Apple TV](#apple-tv-tvos). |
| `iosArm64`, `iosSimulatorArm64` | Compiles, runtime untested | See [Platform limitations](#platform-limitations). |
| `wasmJs` (web, Samsung Tizen TV) | Supported, compiles | See [Samsung TV](#samsung-tv-tizen). |

Apple x86_64 (`iosX64`, `tvosX64`) is not available: Compose Multiplatform 1.11+ ships no Apple
x86_64 artifacts at all, so there is nothing to link an Intel-Mac simulator build against.

### Apple TV (tvOS)

The whole library compiles for tvOS unmodified, and the full shared test suite runs on a tvOS
simulator in CI. Fixed focus, per-row focus memory, key-repeat throttling and the end-of-row
highlight walk were verified on an Apple TV 4K simulator and on real Apple TV HD hardware.

JetBrains does not publish tvOS artifacts for Compose Multiplatform itself, so **your build needs a
settings plugin** that supplies them. Add it to `settings.gradle.kts`, directly after
`pluginManagement { }`:

```kotlin
plugins {
    id("dev.sajidali.compose-tvos") version "1.4.2"
}
```

It maps the official `org.jetbrains.compose.*` coordinates onto tvOS builds published by the
[`sajidalidev/compose-multiplatform-core`](https://github.com/sajidalidev/compose-multiplatform-core)
fork, at resolution time and for tvOS configurations only — Android, desktop, iOS and wasmJs keep
resolving JetBrains' own artifacts.

This library's own published metadata stays on the official `org.jetbrains.compose.*` coordinates,
so depending on it never forces the fork on you: the substitution happens in your build, under your
control, and disappears the day JetBrains ships tvOS themselves.

Leave `composeTvos { strictMode }` off. It reports false positives on iOS-only platform leaves
(`*-uikitarm64`, `*-uikitsimarm64`) and on conflict-resolution losers, failing builds whose linked
graph is fine.

The Siri Remote touchpad is read by the library: provide a `RokuTouchpad` and call
`attachSiriRemote(view)` on the Compose host view (see [Touchpad remotes](#touchpad-remotes)), and
every component follows the thumb like the native focus engine. Two more things the fork decides
for you, both covered further down: it turns every touchpad swipe into a single D-pad key at
lift-off, which `attachSiriRemote` suppresses, and on a real Apple TV HD it lays the scene out at
density 1.0 (see [Check `LocalDensity`](#check-localdensity-on-real-tv-hardware)). `sample-tvos/`
is a runnable Apple TV app that shows both.

### Samsung TV (Tizen)

A Tizen TV app is a web app — HTML, JS and WebAssembly wrapped in a `config.xml` widget manifest —
so Tizen support is simply the `wasmJs` target, with nothing Tizen-specific in this library:

```kotlin
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
}
```

Compose Multiplatform's web target compiles to **WebAssembly GC**, which shipped in Chromium 119,
so the TV's web engine decides whether it runs at all. Per Samsung's Web Engine Specifications:

| Tizen | TV model year | Chromium | Runs Compose web |
|---|---|---|---|
| 10.0 | 2026 | M130 | Yes |
| 9.0 | 2025 | M120 | Yes |
| 8.0 | 2024 | M108 | **No** — predates WasmGC |

Set `required_version="9.0"` in your Tizen `config.xml` so older TVs never install a build they
cannot run.

---

## Installation

Published to Maven Central, so `mavenCentral()` in your repositories is all the setup there is.

### From a Kotlin Multiplatform project (`commonMain`)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.souravnoobcoder:roku-focus-list:2.3.0")
        }
    }
}
```

That single declaration is enough. Gradle selects the right artifact per target — the metadata klib
for `commonMain`, the AAR for Android, a jar for desktop, klibs for iOS.

### From an Android-only Jetpack Compose project

```kotlin
dependencies {
    implementation("io.github.souravnoobcoder:roku-focus-list:2.3.0")
}
```

> The library has **no Material dependency** — it only pulls in Compose Foundation, UI, Animation,
> Runtime, and Runtime-Saveable.

Upgrading from 1.x? See the [migration table](#migrating-from-1x-to-20).

---

## Quick Start

The API is identical on every platform.

### 1. Single Row

Item width is auto-measured from your composable — no `itemWidth` needed:

```kotlin
@Composable
fun HomeScreen() {
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

D-pad LEFT/RIGHT scrolls the row. D-pad UP/DOWN passes through to adjacent composables.

### 2. Full OTT home screen from shared code

This is a `commonMain` composable — one source, compiled for Android, desktop, and iOS:

```kotlin
@Composable
fun TvHomeScreen() {
    RokuLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        rowSpacing = 8.dp,
    ) {
        row(
            itemWidth = 580.dp,
            itemHeight = 310.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            key = "hero",
            header = { isRowFocused -> RowHeader("Hero", isRowFocused) }
        ) {
            items(heroMovies, key = { it.id }) { movie, isFocused ->
                BannerCard(movie = movie, isFocused = isFocused)
            }
        }

        row(
            itemWidth = 220.dp,
            itemHeight = 140.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            key = "trending",
            header = { isRowFocused -> RowHeader("Trending Now", isRowFocused) }
        ) {
            items(trendingMovies, key = { it.id }) { movie, isFocused ->
                MovieCard(movie = movie, isFocused = isFocused)
            }
        }
    }
}
```

`RokuLazyColumn` handles everything: D-pad UP/DOWN moves between rows, LEFT/RIGHT scrolls within
the active row, and a single highlight overlay animates smoothly across rows of different sizes.

### 3. Row with External State

When you need programmatic control (jump to an index, read the current selection, move focus in):

```kotlin
@Composable
fun ControlledRow() {
    val state = rememberRokuFocusListState(
        itemCount = movies.size,
        initialIndex = 5,   // start at the 6th item
        focusSlot = 0       // highlight on leftmost slot
    )

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

## State, hoisting and restoration

Both state objects follow the `LazyColumn` / `rememberLazyListState` pattern: a plain class with a
public constructor, a `remember*` factory that defaults into the composable, and a `Saver`.

```kotlin
val columnState = rememberRokuColumnState()
val rowState = rememberRokuFocusListState(itemCount = movies.size)

RokuLazyColumn(state = columnState) { /* rows */ }
```

| API | What it does |
|---|---|
| `rememberRokuColumnState(initialRowIndex)` | Column selection, restored across config changes and back-stack restoration. |
| `columnState.selectedRowIndex` | Read or assign the selected row. `moveToRow(index)` does the same thing. |
| `columnState.requestedRowIndex` | The last row anyone asked for, before resolution. |
| `columnState.rowCount` / `hasSelectableRow` | What the column currently renders. |
| `columnState.hasFocus` | Observable — true while the column holds platform focus. |
| `columnState.requestFocus()` | Move platform focus onto the column. Returns `false` if it is not laid out yet. |
| `rowState.requestFocus()` | Same, for a **standalone** `RokuLazyRow`. Inside a column the column is the focus target — use `columnState.requestFocus()` and `moveToRow`. |
| `rememberRokuFocusListState(itemCount, initialIndex, focusSlot, focusMode)` | Per-row selection, also saveable. |
| `rowState.selectedIndex` / `scrollTo(index)` | Read or set the selected item. |
| `rowState.moveNext()` / `movePrevious()` | Step the selection. Returns `false` at an edge. |
| `rowState.hasFocus` | True while the row renders as focused (standalone, or the active row of a focused column). |
| `RokuColumnState.Saver`, `RokuFocusListState.Saver` | For hoisting into your own `rememberSaveable` or state holder. `RokuFocusListState.Saver` does not save the item count — call `updateItemCount` after restoring a hoisted row state. |

### Selection survives navigation for free

`rememberRokuColumnState()` is backed by `rememberSaveable`, so a destination that is torn down and
re-created comes back on the same row — configuration changes, process death, and back-stack
restoration all work with no extra wiring, as long as your navigation library provides a
`SaveableStateHolder` (all of them do; `androidx.navigation` does it per destination).

### Selecting a row that does not exist yet

Rows usually stream in from the network. Assigning a selection *before* those rows arrive is the
normal case, not an error, so both state objects remember the index you asked for and apply it once
the range grows to include it:

```kotlin
val columnState = rememberRokuColumnState()

LaunchedEffect(Unit) {
    columnState.selectedRowIndex = 5   // only one placeholder row exists right now
}
// ... rows load ...
// columnState.selectedRowIndex is 5 the moment row 5 exists.
```

Any D-pad move or explicit `moveToRow` replaces the pending request, so ordinary navigation never
snaps back to a stale target. `RokuFocusListState` behaves the same way when `updateItemCount`
grows a row.

---

## Row identity (`key`)

Selection is **positional**, following `LazyListState`'s semantics: `selectedRowIndex` and
`selectedIndex` are indices, so a row inserted above the selection moves the highlight to whatever
now sits at that index. Keys make the per-row *state* (each row's own horizontal selection, its
measured size) follow the row's identity across inserts, removals and reorders — they do not make
the vertical selection chase a row that moved.

`row(key = ...)` follows `LazyColumn`'s `key` contract. Supply one whenever rows can be inserted,
removed, filtered or reordered:

```kotlin
row(itemWidth = 220.dp, itemHeight = 140.dp, key = "continue-watching") { /* items */ }
```

Without a key, each row's selection state is remembered by *position*, so inserting a row at the
top silently shifts every row's selection down one. Keys must be unique within the column and
savable (a `String`, `Int`, or another Bundle-friendly type).

Keys are all-or-nothing: if any row omits one, the column falls back to positional identity for
every row rather than mixing consumer keys with positional fallbacks.

---

## Focus escape, per edge

`RokuFocusEscape` decides, for each edge, whether a press that cannot move the selection is left
unconsumed — which is what lets platform focus travel to whatever is next to the list.

```kotlin
RokuFocusConfig(
    // Left goes back to the navigation pane; the other three edges stay inside the list.
    focusEscape = RokuFocusEscape(start = true, end = false, up = false, down = false)
)
```

Presets: `RokuFocusEscape.All` (the default), `.None`, `.Horizontal`, `.Vertical`.

`start` / `end` mean the beginning and end of a row's item order — LEFT and RIGHT in a
left-to-right layout. The library does not currently mirror for RTL.

---

## Heterogeneous rows (`customRow`)

Real OTT screens are not 100% uniform card rails. `customRow` drops anything into the column — a
hero pager, a chip strip, a multi-line grid — while the column keeps owning vertical navigation:

```kotlin
RokuLazyColumn {
    customRow(
        height = 310.dp,
        key = "hero",
        onKeyEvent = { navKey ->
            when (navKey) {
                RokuNavKey.Left  -> if (page > 0) { page--; true } else false
                RokuNavKey.Right -> if (page < last) { page++; true } else false
                RokuNavKey.Enter -> { open(page); true }
            }
        }
    ) { isRowFocused ->
        HeroPager(page = page, isRowFocused = isRowFocused)
    }

    row(itemWidth = 220.dp, itemHeight = 140.dp, key = "trending") { /* a normal rail */ }
}
```

**The contract**

| The column owns | The custom row owns |
|---|---|
| UP / DOWN between rows | LEFT / RIGHT / ENTER while it is selected |
| Vertical scrolling to bring the row into view | Whatever it draws inside `height` |
| The global highlight's Y position | Its own focus treatment, if `showHighlight` is left `false` |

`onKeyEvent` returns `true` to consume the key and `false` to say "I am at my own edge" — the
column then applies its [focus-escape policy](#focus-escape-per-edge), so focus can leave the list.

`height` is what the column uses to place rows and the highlight, so the content must render at
exactly that height. `showHighlight = true` draws the global highlight across the full width of the
row instead of over a card.

### Empty rows

A `row` with zero items is never selectable: UP/DOWN steps straight over it, the highlight never
parks on it, it renders nothing (not even its header), and it contributes no height. Row indices
and keys are unaffected, so `onItemSelected(rowIndex, …)` keeps meaning what it meant. The one
visible trace is the row spacing on either side of it, because `LazyColumn` still allocates spacing
around a zero-height item — declare rows only when they have content if that matters to you.

---

## Custom Focus Highlight

The `focusHighlight` lambda takes `isFocused` and runs in a `RokuHighlightScope`: a `BoxScope`
sized to the selected card, plus `rowIndex` and `itemIndex`. One lambda can therefore render a
different treatment per row:

```kotlin
RokuLazyColumn(
    focusHighlight = { isFocused ->
        DefaultFocusHighlight(
            isFocused = isFocused,
            cornerRadius = if (rowIndex == AVATARS_ROW) 80.dp else 12.dp
        )
    }
) { /* rows */ }
```

Or replace it entirely:

```kotlin
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
) { /* items */ }
```

`DefaultFocusHighlight` takes `borderColor`, `borderWidth`, `cornerRadius`, `overflow` (how far the
border extends outside the card) and `animateScale`.

---

## Row sizing: explicit or auto-measured

Rows in the `RokuLazyColumn` DSL don't need dimensions — omit them and the column measures the
first item (and the header) by composing it invisibly once, the same way the DSL `RokuLazyRow`
auto-measures its item width:

```kotlin
RokuLazyColumn {
    row(key = "trending", header = { Text("Trending") }) {
        items(movies) { movie, isFocused -> MovieCard(movie, isFocused) }   // sized from the card
    }
    row(itemWidth = 150.dp, itemHeight = 150.dp, key = "avatars") {         // explicit override
        items(profiles) { p, isFocused -> Avatar(p, isFocused) }
    }
}
```

Any composable fits without size bookkeeping; all items in a row share the first item's size.
The first **non-zero** measured size wins and is kept: a first item with no intrinsic size on its
first layout (an async image with no placeholder dimensions) just keeps the row waiting — it stays
unselectable and occupies no height, like an empty row, until a real size lands. If your first
item never has intrinsic size, pass explicit dimensions. Headers are the one exception: a header
may legitimately measure zero, so its first reading — zero included — is final.

Pass explicit sizes when you want the highlight bounds to differ from the card's measured bounds,
or to skip the measuring pass on screens with very many rows. The state-based `RokuLazyColumn`
overload stays fully explicit, and `customRow` always takes its `height` up front.

---

## Focus modes: Static vs Floating

Each axis chooses how the highlight relates to scrolling, independently:

- **`RokuFocusMode.Static`** (default, and the whole point of the library's name): the highlight
  stays parked at a fixed slot and the content scrolls behind it on every move — how Roku's home
  screen behaves.
- **`RokuFocusMode.Floating`**: the list holds still and the highlight walks across the visible
  items or rows. It only scrolls when the selection would leave the visible window, and then by
  the minimum needed to keep it visible — how Android TV's leanback rows behave.

```kotlin
// Vertical floating (rows hold still, highlight walks down), horizontal static (default):
RokuLazyColumn(verticalFocusMode = RokuFocusMode.Floating) {
    row(itemWidth = 220.dp, itemHeight = 140.dp) { /* items */ }              // static rail
    row(itemWidth = 220.dp, itemHeight = 140.dp,
        focusMode = RokuFocusMode.Floating) { /* items */ }                   // floating rail
}

// Standalone row:
RokuLazyRow(focusMode = RokuFocusMode.Floating) { /* items */ }

// Hoisted state:
val state = rememberRokuFocusListState(
    itemCount = movies.size,
    focusMode = RokuFocusMode.Floating
)
```

Pick `Static` when you want the eye to never travel (content does the moving); pick `Floating`
when you want the scroll position to stay put while the user browses what is already on screen.
Both modes remember their window across configuration changes and process death, and both apply
the same edge-overflow correction. `focusSlot` only means something in `Static` — a floating
window has no fixed slot — so it is ignored in `Floating`.

---

## Grid (`RokuFocusGrid`)

A wall of equal-size cells — an "all titles" screen, a channel guide, a settings grid — N columns
wide and scrolling vertically, with one highlight overlay:

```kotlin
val grid = rememberRokuGridState(itemCount = movies.size, columns = 5)

RokuFocusGrid(
    state = grid,
    itemHeight = 160.dp,
    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
    itemSpacing = 14.dp,
    rowSpacing = 14.dp,
    onItemClicked = { index -> open(movies[index]) },
) { index, isFocused ->
    PosterCard(movies[index], isFocused)
}
```

Cell width is whatever is left after the padding and the gaps, split evenly across the columns, so
the grid fills the viewport at any width. LEFT/RIGHT move along the row and stop at its ends (with
`wrapAround` they flow into the neighbouring row like reading); UP/DOWN move by whole rows keeping
the column, and a shorter last row hands out its last cell.

Unlike the rails, **a grid floats by default**: the highlight walks the visible cells and the grid
scrolls only when the selection would leave them, which is how a wall of posters is browsed
everywhere. `rememberRokuGridState(focusMode = RokuFocusMode.Static)` parks the selected row at the
top instead and scrolls on every row move. The state follows the same rules as the rails: the
selection is a raw requested index coerced on read, and the floating window is a raw anchor row
contained at write time — so a grid that is still loading, or one that shrank, comes back where it
was. `Saver` and `rememberRokuGridState` handle restoration.

Touchpad input goes through `rokuMoveColumnsBy(gridState, …)` and `rokuMoveRowsBy(gridState, …)`,
or `gridState.moveColumnsBy` / `moveRowsBy` / `moveBy` (reading order) directly — see
[Touchpad remotes](#touchpad-remotes).

---

## Focus Slot

In `Static` mode, control where the highlight sits within the visible window:

```kotlin
RokuLazyRow(focusSlot = 0) { /* items */ }   // leftmost visible item (default)
RokuLazyRow(focusSlot = 2) { /* items */ }   // 3rd visible slot
```

At list edges, the highlight automatically shifts to track the actual item position — no empty
space is ever shown.

---

## Accessibility

The fixed-focus model is a single focusable container by design, which is both what makes it work
and what limits it. What the library does:

- The container reports itself as a collection (`CollectionInfo`), so a screen reader announces
  a list rather than an anonymous box.
- Every item carries `CollectionItemInfo` (its row and column) and `selected`, so the selected
  card is identifiable in the node tree.
- If you supply descriptions, the **selected** item's description is surfaced on the container as
  its `contentDescription`, in a polite live region — so moving the selection re-announces:

```kotlin
items(movies, key = { it.id }, contentDescription = { it.title }) { movie, isFocused -> /* card */ }
```

The state-based overloads take `itemContentDescription` / `RokuColumnRowConfig.itemContentDescription`
instead.

**Honest limits.** There are two cursors, and they are not the same one. D-pad selection moves the
library's own highlight; a screen reader moves its accessibility cursor. Item nodes are in the tree
and a screen reader can reach them (explore-by-touch, or swiping through nodes), but they are not
input-focusable, so reaching one that way does not move the highlight, and moving the highlight does
not move the screen-reader cursor. What ties the two together is the container's description, which
is why supplying `contentDescription` matters: without it nothing is announced on selection change,
and the library will not invent text from your composables.

**Platform support.** These are Compose semantics, and how far they travel differs per platform.
On **Android** they map onto `AccessibilityNodeInfo` in full — that is where this was verified.
On **iOS**, Compose Multiplatform 1.10.3 maps a live region to "updates frequently" without the
politeness mode, and does not map collection info at all. On **desktop** there is no mapping for
any of them. Treat the accessibility story as an Android feature today.

**What was verified:** the emitted accessibility node tree on an Android TV emulator (API 31),
inspected with `uiautomator dump` — the container is the single focusable node, its content
description tracks the selected item across D-pad moves, and each item appears as its own node
carrying its description and selected state. TalkBack itself was not exercised end to end; if you
ship this to users, test with TalkBack on a real device.

---

## Android TV / Fire TV usage

The library needs nothing platform-specific, but your **app** module still needs the usual leanback
wiring in `AndroidManifest.xml`:

```xml
<uses-feature
    android:name="android.software.leanback"
    android:required="false" />
<uses-feature
    android:name="android.hardware.touchscreen"
    android:required="false" />

<activity
    android:name=".MainActivity"
    android:exported="true"
    android:screenOrientation="landscape">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

`RokuLazyRow` / `RokuLazyColumn` is a single focusable node, so give it focus on entry:

```kotlin
val columnState = rememberRokuColumnState()

LaunchedEffect(Unit) {
    delay(100)                  // wait for measurement + layout
    columnState.requestFocus()
}

RokuLazyColumn(state = columnState, modifier = Modifier.fillMaxSize()) { /* rows */ }
```

`requestFocus()` returns `false` rather than throwing when the list is not laid out yet, so it is
safe to call from arbitrary callbacks. Applying your own `Modifier.focusRequester(...)` still works
too. The DSL `RokuLazyRow` has no state handle by design — use the state-based overload if you need
one.

---

## Configuration

```kotlin
val config = RokuFocusConfig(
    highlightAnimationSpec = tween(200, easing = FastOutSlowInEasing),
    keyRepeatDelayMs = 150L,
    keyRepeatAccelAfter = 3,        // accelerate after 3 consecutive presses
    keyRepeatFastDelayMs = 50L,     // fast speed once accelerated
    wrapAround = true,              // wrap from last to first
    hapticFeedback = true,          // vibrate at boundaries
    focusEscape = RokuFocusEscape.All
)

RokuLazyRow(config = config) { /* items */ }
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `highlightAnimationSpec` | `AnimationSpec<Float>` | `tween(200ms)` | Highlight position animation. Content scrolling uses its own velocity-carrying spring. |
| `keyRepeatDelayMs` | `Long` | `150` | Throttle delay for held D-pad keys (ms) |
| `keyRepeatAccelAfter` | `Int` | `3` | After N presses, switch to fast delay. 0 = disabled |
| `keyRepeatFastDelayMs` | `Long` | `50` | Fast repeat delay after acceleration |
| `wrapAround` | `Boolean` | `false` | Wrap from last item to first and vice versa |
| `hapticFeedback` | `Boolean` | `true` | Vibrate on boundary hit. No-op on desktop and web. |
| `focusEscape` | `RokuFocusEscape` | `All` | Per-edge control over letting focus leave the list |

Touchpad pacing and the focus-movement hint are configured on `RokuTouchpadConfig`, not here — see
[Touchpad remotes](#touchpad-remotes).

Built-in animation presets:

```kotlin
RokuAnimationSpec.Default  // tween(300ms) — balanced
RokuAnimationSpec.Fast     // tween(150ms) — snappy
RokuAnimationSpec.Smooth   // spring(0.8, 300) — organic
```

---

## Touchpad remotes

A Siri Remote produces a thumb that moves, not discrete presses. The library reads it for you:
provide one `RokuTouchpad` at the root and every `RokuLazyRow`, `RokuLazyColumn` and
`RokuFocusGrid` below follows the thumb the way the native tvOS focus engine does. On tvOS the whole
wiring is:

```kotlin
val touchpad = RokuTouchpad()
ComposeUIViewController {
    CompositionLocalProvider(LocalRokuTouchpad provides touchpad) { App() }
}.also { touchpad.attachSiriRemote(it.view) }
```

What every component then does, with no per-screen code:

- **Focus follows the thumb, and only the thumb.** Each 0.65 of an item pitch of travel moves one
  item (one row pitch up or down), coalesced into a single move when travel arrives faster than one
  item per report. Nothing moves once the thumb lifts — there is no coast.
- **A fast thumb covers more ground** through a smooth velocity gain (×1 up to 2,500 pt/s, ×2 from
  12,000), so a hard swipe crosses about twice the items of a careful one.
- **Small movement is never lost.** Travel short of a full step plays a soft light across the
  focused card toward the thumb, brightening with pull, while the card leans a little (6 dp, a 3°
  tilt, a 2 % lift; the highlight travels slightly further for depth) and springs back with a
  bounce when the thumb lifts. This is the focus-movement hint that tells the user a small swipe
  was felt. The light is blended onto the card's own pixels, so rounded or odd-shaped cards keep
  their corners. At the end of a row the lean pins at full pull, pushing further is dropped, and
  one step of travel back moves back.
- **Swipes and keys are the same move.** `onItemSelected`, `wrapAround` and `focusEscape` behave
  identically for both, and a `customRow` receives each step as a `RokuNavKey.Left` / `Right`.

Tune it with `RokuTouchpadConfig`. Distances and speeds are in the units your host reports — UIKit
points on tvOS, where a full swipe across the pad is 1,000–1,800 pt and a relaxed flick lifts off at
4,000–8,000 pt/s:

| Parameter | Default | Description |
|---|---|---|
| `itemStepFraction` | `0.65` | Travel per item along a row, in item pitches |
| `rowStepFraction` | `1` | Travel per row up or down, in row pitches |
| `axisLock` | `16` | Travel before a contact commits to an axis and can move; below it the card only leans |
| `gainStartVelocity` / `gainMaxVelocity` | `2500` / `12000` | Thumb speeds between which travel counts ×1 → `maxGain`, smoothly |
| `maxGain` | `2` | Travel multiplier for a fast thumb |
| `hintTravel` | `6.dp` | Lean of the focused card at a full step of pending travel (square-root curve) |
| `hintTiltDegrees` | `3` | Tilt toward the thumb at a full step |
| `hintScale` | `1.02` | Lift of the card at a full step; `1` disables it |
| `hintHighlightParallax` | `1.25` | How much further than the card the highlight leans; `1` moves them as one |
| `hintLight` | `0.22` | Peak opacity of the light that slides toward the thumb across the card; `0` disables it |
| `hintLightColor` | `Color.White` | Colour of that light |
| `hintReleaseSpec` | `spring(0.55, 450)` | How the lean springs back when the thumb lifts |

Other platforms feed the same object: call `panBegan()`, `panChanged(dx, dy, velocityX, velocityY)`
and `panEnded()` from whatever reads the device, and set `pxPerUnit` if you report anything other
than pixels. Without a `RokuTouchpad` in the composition none of this exists at runtime — no layer,
no coroutine, no key interception — so a D-pad TV runs exactly the code it ran before.

### The moves underneath

The touchpad drives the components through coalesced multi-step moves you can also call yourself,
for input that arrives some other way (a trackpad, a wheel, a gamepad stick). Calling `moveNext()`
N times for one gesture gives you N selection changes, N highlight animations, N scroll animations
and N `onItemSelected` callbacks — and every prefetch or saved-position write hanging off that
callback fires N times too. These do it once:

```kotlin
rowState.moveBy(3)           // one selection change, one animation, one callback
rowState.moveBy(-2)          // negative steps travel toward the start
columnState.moveRowsBy(2)    // vertical equivalent; skips rows with nothing to select
columnState.moveItemsBy(3)   // within the column's active row — no row state needed
gridState.moveColumnsBy(2)   // along a grid row; moveRowsBy keeps the column

// Edge-aware variants that also apply focusEscape / onBoundaryHit exactly once per move:
rokuMoveBy(rowState, config, steps = 3, onSelected = { index -> /* ... */ })
rokuMoveRowsBy(columnState, config, steps = -1)
rokuMoveItemsBy(columnState, config, steps = 3, onSelected = { rowIndex, itemIndex -> /* ... */ })
rokuMoveColumnsBy(gridState, config, steps = 2)
```

Every entry point has a handle for this. A `RokuLazyColumn` exposes the selected rail's state as
`columnState.activeRowState`, so horizontal moves go through the column state and the `row { }`
DSL — which never hands out its rows' states — works exactly like the state-based overload. A
standalone DSL `RokuLazyRow` takes an optional hoisted `state` for the same reason, while keeping
its auto-measured item width.

Moves clamp at the ends of a row — asking for more steps than remain lands on the last item. With
`wrapAround` the move wraps only when the selection is *already* parked on the edge being pushed,
mirroring single steps. `moveBy(1)` and `moveNext()` behave identically; `moveNext` is implemented
on top of the same core, not duplicated. A multi-step move also resets the key-repeat acceleration
streak, so a swipe landing mid-repeat cannot compound into a runaway scroll. Chained moves scroll
as one continuous motion: the scroll animation carries its velocity across retargets instead of
restarting from rest on each item.

### Apple TV and the Compose tvOS fork

The fork already turns every touchpad swipe into one D-pad key at lift-off — a slow drag is one
step, a long flick is one step, and there is no velocity to read. `attachSiriRemote` installs a
`UIPanGestureRecognizer` that cancels the underlying touch once it recognises, so the fork's key is
never dispatched and the swipe is not applied twice; clicks and D-pad ring presses are `UIPress`
events and are unaffected. A flick so short that the pan only recognises as the touch ends can
still leak that one key (seen twice in ~130 gestures on real hardware), so the components drop any
direction key arriving within 120 ms of touch-driven movement. If you install a pan recogniser of
your own instead, leave `cancelsTouchesInView = true`.

---

## Two things that will bite you

### Don't put `Modifier.clickable` on item content

`clickable` makes the node focusable, so it competes with the list's own single focus target: the
highlight stops tracking the selection. This library deliberately keeps one focusable per list —
that is what makes fixed focus work — so handle taps without adding a focus target:

```kotlin
// Breaks focus tracking:
Modifier.clickable { open(movie) }

// Works — taps without a focus target:
Modifier.pointerInput(movie.id) {
    detectTapGestures { open(movie) }
}
```

D-pad activation is already delivered through `onItemClicked`; the gesture above is only for
touch and mouse.

### Check `LocalDensity` on real TV hardware

TV platforms disagree about density, and the wrong one silently halves your layout:

| Device | Reported density | 1920×1080 screen becomes |
|---|---|---|
| Apple TV HD (real hardware) | 1.0 | 1920×1080 **dp** |
| Apple TV 4K simulator | 2.0 | 960×540 dp |
| Android TV @ 320 dpi | 2.0 | 960×540 dp |
| Desktop browser / Tizen | 1.0 | 1920×1080 dp |

At density 1.0 every card is half its intended physical size and roughly twice as many rows fit on
screen — which also doubles the per-frame work. On a real Apple TV HD that measured **~28 fps**
during vertical scrolls (about 10 frames per 345 ms scroll), which reads as a jump rather than a
scroll.

Pin a design density instead of trusting the platform's:

```kotlin
BoxWithConstraints {
    val designDensity = constraints.maxWidth / 960f
    CompositionLocalProvider(
        LocalDensity provides Density(designDensity, fontScale = 1f)
    ) {
        HomeScreen()
    }
}
```

This is a consumer concern rather than a library bug, but it lands hardest on exactly this
library's users.

---

## API Reference

### Components

| Component | Description |
|---|---|
| `RokuLazyRow` | Horizontal fixed-focus row. DSL variant auto-measures width; state variant takes explicit `itemWidth`. |
| `RokuLazyColumn` | Vertical + horizontal OTT grid. DSL variant manages per-row state internally; state variant takes `List<RokuColumnRowConfig>`. |
| `RokuFocusGrid` | N-column wall of equal cells, scrolling vertically. Floats by default. Takes a `RokuGridState`. |
| `RokuLazyColumnScope.row` | A rail of equal-size cards. Sizes explicit, or measured from the first item when omitted. |
| `RokuLazyColumnScope.customRow` | Anything else, with LEFT/RIGHT/ENTER delegated to it. |
| `DefaultFocusHighlight` | Default white rounded-border highlight. `BoxScope` extension, fully replaceable. |
| `Modifier.rokuKeyHandler` | Low-level D-pad handler, for wiring your own container. |
| `rokuMoveBy` / `rokuMoveRowsBy` / `rokuMoveItemsBy` / `rokuMoveColumnsBy` | Edge-aware multi-step moves: a row, a column's rows, a column's active row, a grid's row and rows. Escape policy applied once per move. |
| `RokuTouchpad.attachSiriRemote` | tvOS only. Installs the pan recogniser that feeds a `RokuTouchpad` from the Siri Remote; returns an attachment with `detach()`. |

### Types

| Type | Description |
|---|---|
| `RokuColumnState` | Which row is selected; focus control; observable `hasFocus`; `activeRowState` and `moveItemsBy` / `moveRowsBy` for driving it from outside. |
| `RokuFocusListState` | Which item of a row is selected; `moveBy` for coalesced multi-step moves. |
| `RokuGridState` | Which cell of a grid is selected (linear index; `selectedRow` / `selectedColumn` derived); `moveColumnsBy` / `moveRowsBy` / `moveBy`; owns `columns` and the focus mode. |
| `RokuFocusConfig` | Navigation behaviour. |
| `RokuTouchpad` | A touchpad remote: `panBegan` / `panChanged` / `panEnded` in, thumb-following moves and the focus-movement hint out. Provided through `LocalRokuTouchpad`. |
| `RokuTouchpadConfig` | Pacing (step fractions, axis lock, velocity gain) and hint tuning (light, travel, tilt, lift, parallax, release spring). |
| `RokuFocusMode` | Per-axis `Static` (fixed slot, content scrolls) vs `Floating` (highlight walks, scrolls at window edges). |
| `RokuFocusEscape` | Per-edge focus escape. |
| `RokuHighlightScope` | Receiver of `focusHighlight`: `BoxScope` + `rowIndex`, `itemIndex`. |
| `RokuNavKey` | `Left` / `Right` / `Enter`, handed to `customRow`'s `onKeyEvent`. |
| `RokuColumnRowConfig` | One row of the state-based `RokuLazyColumn`. |

### Callbacks

| Callback | Available on | Description |
|---|---|---|
| `onItemSelected` | Row, Column, Grid | Fires when the selected index changes — once per move, whether a key or a swipe caused it. |
| `onItemClicked` | Row, Column, Grid | Fires on Enter / DpadCenter press. |
| `onFocusEnter` | Row, Column, Grid | Fires when the list gains focus. |
| `onFocusExit` | Row, Column, Grid | Fires when the list loses focus. |

---

## Migrating from 1.x to 2.0

| 1.x | 2.0 | Why |
|---|---|---|
| `focusHighlight = { isFocused -> … }` | Unchanged | The lambda gained a `RokuHighlightScope` receiver carrying `rowIndex` and `itemIndex`; `isFocused` stays its parameter, so 1.x highlight lambdas compile as they are. |
| `RokuFocusConfig(allowFocusEscape = true)` | `RokuFocusConfig(focusEscape = RokuFocusEscape.All)` | Per-edge control. The old spelling still compiles as a deprecated factory that maps to all edges. |
| `config.allowFocusEscape` | `config.focusEscape` | A deprecated extension property still reads the old flag; `copy(allowFocusEscape = …)` has no equivalent. |
| `RokuLazyColumn(initialRowIndex = 3)` | `RokuLazyColumn(state = rememberRokuColumnState(initialRowIndex = 3))` | One source of truth for the selected row, matching `LazyColumn` / `rememberLazyListState`. |
| `row(itemWidth = …, itemHeight = …)` | `row(itemWidth = …, itemHeight = …, key = "trending")` | `key` and `initialIndex` are appended after the 1.x parameters, so positional calls keep their meaning. The key is optional but strongly recommended. |
| `com.github.reshusingh07:roku-focus-list:1.0.0` | `io.github.souravnoobcoder:roku-focus-list:2.0.0` | Moved from JitPack to Maven Central. JitPack cannot serve a Kotlin Multiplatform publication: six publications put it into multi-module mode, which re-groups the artifacts and rewrites the Gradle metadata until `commonMain` resolution breaks. Drop the `jitpack.io` repository line. |
| Selection lost on rotation | Nothing to do | `rememberRokuFocusListState` and `rememberRokuColumnState` are `rememberSaveable`-backed. |
| Out-of-range selection clamped forever | Nothing to do | The requested index is remembered and applied when the list grows. |
| Rows with no items still selectable | Nothing to do | Empty rows are skipped by UP/DOWN and render nothing. |

`Modifier.rokuKeyHandler`, `rememberRokuFocusListState`, `RokuFocusListState.scrollTo` /
`moveNext` / `movePrevious`, `DefaultFocusHighlight`, `RokuAnimationSpec` and the `items { }` DSL
keep their 1.x signatures.

---

## How It Works

1. `RokuLazyRow` / `RokuLazyColumn` is a **single focusable composable** — individual items are never focused
2. D-pad events are intercepted at the container level with key-repeat throttling
3. Selection is tracked via `selectedIndex` in `RokuFocusListState`, not the Compose focus system
4. Content scrolls via `LazyRow(userScrollEnabled = false)` driven by one spring per list whose velocity is carried across retargets, so a run of quick moves (key repeat, a touchpad drag or fling) reads as one continuous scroll rather than a restart per item; far jumps use `animateScrollToItem()` for its teleporting. Compose handles recycling
5. The highlight overlay is positioned with `graphicsLayer { translationX/Y }` (GPU-only, no re-layout)
6. At list edges, overflow correction shifts the highlight to match the actual item position
7. In `RokuLazyColumn`, one global highlight animates X, Y, width, and height between rows of different card sizes
8. `RokuFocusMode.Floating` keeps a raw window anchor per axis and only moves it when a selection
   change would leave the window — same maths, different scroll target

Key-repeat throttling uses `kotlin.time.TimeSource.Monotonic` rather than Android's `SystemClock`,
which is why no platform-specific source set is needed.

---

## Platform limitations

- **`Key.DirectionCenter` never fires on desktop.** Compose Multiplatform maps it to a sentinel
  keycode outside Android. `Key.Enter` and `Key.NumPadEnter` are also handled, so `onItemClicked`
  still works there.
- **Haptic feedback is a no-op on desktop and web.** `hapticFeedback = true` is harmless; there is
  simply no haptic hardware.
- **iOS compiles but has not been exercised at runtime.** The klibs build for both iOS targets.
- **tvOS needs the `compose-tvos` settings plugin in your build**, because JetBrains publishes no
  tvOS artifacts for Compose Multiplatform. See [Apple TV](#apple-tv-tvos).
- **No Apple x86_64.** Compose Multiplatform 1.11+ dropped `iosX64` / `tvosX64` entirely, so Intel-Mac
  simulator builds are not possible.
- **Linking an Apple framework requires macOS.** Compiling the klibs works from any host, including
  Windows, but producing an `.xcframework` needs Xcode.
- **Tizen 8.0 (2024 TVs) cannot run the wasm build at all** — its Chromium M108 predates WebAssembly
  GC. See [Samsung TV](#samsung-tv-tizen).
- **`headerHeight` in `RokuLazyColumn`'s `row { }` must match the header's real rendered height**,
  and `customRow`'s `height` must match its content, or the vertical highlight lands at the wrong Y.
- **Layout is left-to-right only.** `RokuFocusEscape.start` / `.end` map to LEFT / RIGHT; nothing
  mirrors for RTL yet.
- **Individual items are not accessibility-focusable.** See [Accessibility](#accessibility).

---

## Requirements

- **Kotlin** 2.4.x (built with 2.4.10)
- **Compose Multiplatform** 1.12.0, or **Jetpack Compose** 1.12.0 / BOM 2026.08.00 for Android-only projects
- **AGP** 9.1+ and **compileSdk** 37 for Android consumers — required by Compose Multiplatform 1.12.0's
  Android artifacts, which declare it in their aar-metadata
- **minSdk** 23 (Android 6.0+)
- **JDK** 11+ for desktop consumers
- **No Material dependency** — works with any design system

---

## Repository layout

| Module | What it is |
|---|---|
| `roku-focus-list/` | The library. All code in `src/commonMain/kotlin` except the Siri Remote recogniser in `src/tvosMain/kotlin`; tests in `src/commonTest/kotlin`. |
| `app/` | Android TV demo app: 100 rows, 6 card types, 7 demo screens. Run on a TV emulator or device. |
| `sample-tvos/` | Runnable Apple TV sample: the four layouts in both focus modes, driven by `RokuTouchpad` with the one-line tvOS wiring, plus an on-screen selection and frame-timing readout. Xcode project in `sample-tvos/tvosApp/`; build in Release for a fair read on smoothness. |
| `consumer-kmp/` | Verification module — a KMP library whose `commonMain` uses `RokuLazyRow` / `RokuLazyColumn`. |
| `verification/published-consumer/` | Standalone Gradle build that resolves the **published** artifact from `mavenLocal` in `commonMain`. |

### Verifying a change

```bash
./gradlew :roku-focus-list:compileCommonMainKotlinMetadata :roku-focus-list:desktopTest
```

```bash
./gradlew :roku-focus-list:compileAndroidMain :roku-focus-list:compileKotlinDesktop :roku-focus-list:compileKotlinIosArm64 :roku-focus-list:compileKotlinIosSimulatorArm64 :roku-focus-list:compileKotlinTvosArm64 :roku-focus-list:compileKotlinTvosSimulatorArm64 :roku-focus-list:compileKotlinWasmJs
```

```bash
./gradlew :consumer-kmp:compileCommonMainKotlinMetadata :consumer-kmp:compileAndroidMain
```

```bash
./gradlew :app:assembleDebug :app:assembleRelease
```

```bash
./gradlew :roku-focus-list:publishToMavenLocal && ./gradlew -p verification/published-consumer verifyCommonMainConsumption printRokuFocusResolution
```

The standalone consumer needs an Android SDK: set `ANDROID_HOME`, or create
`verification/published-consumer/local.properties` with `sdk.dir=/path/to/Android/Sdk`.

---

## Releasing

Published to Maven Central through the Sonatype Central Portal. Releasing is: bump the version,
run the workflow, approve the staged deployment.

**1. Bump the version in a PR.** `libraryVersion` in `gradle.properties` is the single source of
truth. Update it, add the matching `## [x.y.z]` section to [CHANGELOG.md](CHANGELOG.md), and merge.
The workflow never pushes to `master`; it only creates a tag, a release, and a staged deployment.

**2. Run the workflow** from the Actions tab, or:

```bash
gh workflow run release.yml -f version=2.0.1
```

It refuses to run unless the version is a bare semver string, matches `libraryVersion`, and is not
already tagged. Then it builds every target on JDK 17, runs the shared tests, publishes to the
local Maven repo, resolves that coordinate from the standalone `verification/published-consumer`
build, and checks the POM carries everything Central validates — because Central validates *after*
upload, and a rejection there is a slower way to learn the same thing.

Only then does it upload. Central comes before tagging on purpose: a rejected deployment should not
leave a tag behind.

There is no manual step. The workflow publishes to Central, and the plugin polls the deployment
and fails the build if Central rejects it.

**3. It verifies itself.** After publishing it waits for the artifact to appear on
`repo1.maven.org`, then resolves `io.github.souravnoobcoder:roku-focus-list:<version>` back out of
Central through the standalone consumer build — `commonMain` and every target, with `mavenLocal()`
stripped and `--refresh-dependencies` so nothing can resolve from the copy CI just published
locally. A successful publish is not the same as a usable artifact, and this project has already
shipped one that wasn't; see the [1.x → 2.0 table](#migrating-from-1x-to-20).

Pass `-f dry_run=true` to run every check without tagging or uploading.

The build itself stays staging-only (`automaticRelease = false`). Releasing is a decision the
workflow makes by calling `publishAndReleaseToMavenCentral`, so running `publishToMavenCentral`
by hand can never publish irrevocably.

### One-time setup

Four repository secrets under Settings → Secrets and variables → Actions:

| Secret | What it is |
|---|---|
| `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` | A **user token** from central.sonatype.com, not your login |
| `SIGNING_KEY` | ASCII-armoured GPG private key (`gpg --export-secret-keys --armor <id>`) |
| `SIGNING_KEY_PASSWORD` | Its passphrase |

The `io.github.souravnoobcoder` namespace is granted automatically when the Central account is
created via GitHub.

Signing is conditional in the build: without a key, `publishToMavenLocal` still works, so
contributors are not blocked. The workflow refuses to upload if the key is missing rather than
publishing unsigned.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).
