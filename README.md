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
| `androidTarget` (Android, Android TV, Fire TV) | Supported, verified | The primary use case. minSdk 24. |
| `jvm("desktop")` (Windows / macOS / Linux) | Supported, verified | Arrow keys and Enter work. Needs JDK 11+. |
| `iosX64`, `iosArm64`, `iosSimulatorArm64` | Compiles, runtime untested | See [Platform limitations](#platform-limitations). |
| `wasmJs` / `js` (web) | Not declared | Easy to add — see below. |

Web targets are not declared by default because Compose Multiplatform for web is still Beta. To add
one, declare the target in the library's `build.gradle.kts`; no source changes are needed:

```kotlin
kotlin {
    wasmJs { browser() }
}
```

---

## Installation

Published to Maven Central, so `mavenCentral()` in your repositories is all the setup there is.

### From a Kotlin Multiplatform project (`commonMain`)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.souravnoobcoder:roku-focus-list:2.0.0")
        }
    }
}
```

That single declaration is enough. Gradle selects the right artifact per target — the metadata klib
for `commonMain`, the AAR for Android, a jar for desktop, klibs for iOS.

### From an Android-only Jetpack Compose project

```kotlin
dependencies {
    implementation("io.github.souravnoobcoder:roku-focus-list:2.0.0")
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
| `rememberRokuFocusListState(itemCount, initialIndex, focusSlot)` | Per-row selection, also saveable. |
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

## Focus Slot

Control where the highlight sits within the visible window:

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
| `highlightAnimationSpec` | `AnimationSpec<Float>` | `tween(200ms)` | Highlight and scroll animation |
| `keyRepeatDelayMs` | `Long` | `150` | Throttle delay for held D-pad keys (ms) |
| `keyRepeatAccelAfter` | `Int` | `3` | After N presses, switch to fast delay. 0 = disabled |
| `keyRepeatFastDelayMs` | `Long` | `50` | Fast repeat delay after acceleration |
| `wrapAround` | `Boolean` | `false` | Wrap from last item to first and vice versa |
| `hapticFeedback` | `Boolean` | `true` | Vibrate on boundary hit. No-op on desktop and web. |
| `focusEscape` | `RokuFocusEscape` | `All` | Per-edge control over letting focus leave the list |

Built-in animation presets:

```kotlin
RokuAnimationSpec.Default  // tween(300ms) — balanced
RokuAnimationSpec.Fast     // tween(150ms) — snappy
RokuAnimationSpec.Smooth   // spring(0.8, 300) — organic
```

---

## API Reference

### Components

| Component | Description |
|---|---|
| `RokuLazyRow` | Horizontal fixed-focus row. DSL variant auto-measures width; state variant takes explicit `itemWidth`. |
| `RokuLazyColumn` | Vertical + horizontal OTT grid. DSL variant manages per-row state internally; state variant takes `List<RokuColumnRowConfig>`. |
| `RokuLazyColumnScope.row` | A rail of equal-size cards. |
| `RokuLazyColumnScope.customRow` | Anything else, with LEFT/RIGHT/ENTER delegated to it. |
| `DefaultFocusHighlight` | Default white rounded-border highlight. `BoxScope` extension, fully replaceable. |
| `Modifier.rokuKeyHandler` | Low-level D-pad handler, for wiring your own container. |

### Types

| Type | Description |
|---|---|
| `RokuColumnState` | Which row is selected; focus control; observable `hasFocus`. |
| `RokuFocusListState` | Which item of a row is selected. |
| `RokuFocusConfig` | Navigation behaviour. |
| `RokuFocusEscape` | Per-edge focus escape. |
| `RokuHighlightScope` | Receiver of `focusHighlight`: `BoxScope` + `rowIndex`, `itemIndex`. |
| `RokuNavKey` | `Left` / `Right` / `Enter`, handed to `customRow`'s `onKeyEvent`. |
| `RokuColumnRowConfig` | One row of the state-based `RokuLazyColumn`. |

### Callbacks

| Callback | Available on | Description |
|---|---|---|
| `onItemSelected` | Row, Column | Fires when the selected index changes. |
| `onItemClicked` | Row, Column | Fires on Enter / DpadCenter press. |
| `onFocusEnter` | Row, Column | Fires when the list gains focus. |
| `onFocusExit` | Row, Column | Fires when the list loses focus. |

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
4. Content scrolls via `LazyRow(userScrollEnabled = false)` + `animateScrollToItem()` — Compose handles recycling
5. The highlight overlay is positioned with `graphicsLayer { translationX/Y }` (GPU-only, no re-layout)
6. At list edges, overflow correction shifts the highlight to match the actual item position
7. In `RokuLazyColumn`, one global highlight animates X, Y, width, and height between rows of different card sizes

Key-repeat throttling uses `kotlin.time.TimeSource.Monotonic` rather than Android's `SystemClock`,
which is why no platform-specific source set is needed.

---

## Platform limitations

- **`Key.DirectionCenter` never fires on desktop.** Compose Multiplatform maps it to a sentinel
  keycode outside Android. `Key.Enter` and `Key.NumPadEnter` are also handled, so `onItemClicked`
  still works there.
- **Haptic feedback is a no-op on desktop and web.** `hapticFeedback = true` is harmless; there is
  simply no haptic hardware.
- **iOS compiles but has not been exercised at runtime.** The klibs build for all three iOS targets.
  Compose Multiplatform does not support tvOS, so there is no Apple TV target.
- **Linking an iOS framework requires macOS.** Compiling the klibs works from any host, including
  Windows, but producing an `.xcframework` needs Xcode.
- **`headerHeight` in `RokuLazyColumn`'s `row { }` must match the header's real rendered height**,
  and `customRow`'s `height` must match its content, or the vertical highlight lands at the wrong Y.
- **Layout is left-to-right only.** `RokuFocusEscape.start` / `.end` map to LEFT / RIGHT; nothing
  mirrors for RTL yet.
- **Individual items are not accessibility-focusable.** See [Accessibility](#accessibility).

---

## Requirements

- **Kotlin** 2.2.x (built with 2.2.21)
- **Compose Multiplatform** 1.10.3, or **Jetpack Compose** 1.10.5 / BOM 2026.03.00 for Android-only projects
- **minSdk** 24 (Android 7.0+)
- **JDK** 11+ for desktop consumers
- **No Material dependency** — works with any design system

---

## Repository layout

| Module | What it is |
|---|---|
| `roku-focus-list/` | The library. All code in `src/commonMain/kotlin`, tests in `src/commonTest/kotlin`. |
| `app/` | Android TV demo app: 100 rows, 6 card types, 7 demo screens. Run on a TV emulator or device. |
| `consumer-kmp/` | Verification module — a KMP library whose `commonMain` uses `RokuLazyRow` / `RokuLazyColumn`. |
| `verification/published-consumer/` | Standalone Gradle build that resolves the **published** artifact from `mavenLocal` in `commonMain`. |

### Verifying a change

```bash
./gradlew :roku-focus-list:compileCommonMainKotlinMetadata :roku-focus-list:desktopTest
```

```bash
./gradlew :roku-focus-list:compileAndroidMain :roku-focus-list:compileKotlinDesktop :roku-focus-list:compileKotlinIosArm64 :roku-focus-list:compileKotlinIosX64 :roku-focus-list:compileKotlinIosSimulatorArm64
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
