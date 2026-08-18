# RokuFocus

[![JitPack](https://jitpack.io/v/reshusingh07/roku-focus-list.svg)](https://jitpack.io/#reshusingh07/roku-focus-list)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blue.svg)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.10.3-blue.svg)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Roku-style fixed-focus D-pad navigation for **Android TV**, **Fire TV**, and any other Compose
target — built as a **Kotlin Multiplatform / Compose Multiplatform** library.

The focus highlight stays locked at a fixed screen position while content smoothly scrolls behind
it — exactly how Roku TV navigation works. Supports horizontal rows, full OTT grid layouts
(vertical + horizontal), wrap-around, key-repeat acceleration, and custom highlight rendering.

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
| Highlight customization | Lambda with `BoxScope` | Per-item focus indication |
| OTT grid layout | `RokuLazyColumn` with mixed row sizes | Manual `LazyColumn` + focus wiring |

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

**Step 2.** Add the dependency.

### From a Kotlin Multiplatform project (`commonMain`)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.reshusingh07:roku-focus-list:1.0.0")
        }
    }
}
```

That single declaration is enough. Gradle selects the right artifact per target — the metadata klib
for `commonMain`, the AAR for Android, a jar for desktop, klibs for iOS.

### From an Android-only Jetpack Compose project

```kotlin
dependencies {
    implementation("com.github.reshusingh07:roku-focus-list:1.0.0")
}
```

> The library has **no Material dependency** — it only pulls in Compose Foundation, UI, Animation,
> and Runtime.

---

## Quick Start

The API is identical on every platform, and unchanged from the Android-only releases.

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
        // Hero banner row
        row(
            itemWidth = 580.dp,
            itemHeight = 310.dp,
            itemSpacing = 20.dp,
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            headerHeight = 30.dp,
            header = { isRowFocused -> RowHeader("Hero", isRowFocused) }
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
            header = { isRowFocused -> RowHeader("Trending Now", isRowFocused) }
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
            header = { isRowFocused -> RowHeader("New Releases", isRowFocused) }
        ) {
            items(newReleases) { movie, isFocused ->
                PortraitCard(movie = movie, isFocused = isFocused)
            }
        }
    }
}
```

`RokuLazyColumn` handles everything: D-pad UP/DOWN moves between rows, LEFT/RIGHT scrolls within
the active row, and a single highlight overlay animates smoothly across rows of different sizes.

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
val focusRequester = remember { FocusRequester() }

LaunchedEffect(Unit) {
    delay(100) // wait for measurement + layout
    focusRequester.requestFocus()
}

RokuLazyColumn(modifier = Modifier.fillMaxSize().focusRequester(focusRequester)) { /* ... */ }
```

Leave `allowFocusEscape = true` (the default) so D-pad presses at an edge fall through to a sidebar
or top bar instead of being swallowed.

---

## Configuration

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
| `hapticFeedback` | `Boolean` | `true` | Vibrate on boundary hit. No-op on desktop and web. |
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
RokuLazyRow(focusSlot = 0, ...) { ... }   // leftmost visible item (default)
RokuLazyRow(focusSlot = 2, ...) { ... }   // 3rd visible slot
```

At list edges, the highlight automatically shifts to track the actual item position — no empty
space is ever shown.

---

## API Reference

### Components

| Component | Description |
|---|---|
| `RokuLazyRow` | Horizontal fixed-focus row. DSL variant auto-measures width; state variant takes explicit `itemWidth`. |
| `RokuLazyColumn` | Vertical + horizontal OTT grid. DSL variant manages state internally; state variant takes `List<RokuColumnRowConfig>`. |
| `DefaultFocusHighlight` | Default white rounded-border highlight. `BoxScope` extension, fully replaceable. |
| `Modifier.rokuKeyHandler` | Low-level D-pad handler, for wiring your own container. |

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
  or the vertical highlight lands at the wrong Y.
- **Selection is not saved across configuration changes.** `RokuFocusListState` uses `remember`, not
  `rememberSaveable`.

---

## Requirements

- **Kotlin** 2.1.0+ (built with 2.2.21)
- **Compose Multiplatform** 1.10.3, or **Jetpack Compose** 1.10.5 / BOM 2026.03.00 for Android-only projects
- **minSdk** 24 (Android 7.0+)
- **JDK** 11+ for desktop consumers
- **No Material dependency** — works with any design system

---

## Repository layout

| Module | What it is |
|---|---|
| `roku-focus-list/` | The library. All code in `src/commonMain/kotlin`, tests in `src/commonTest/kotlin`. |
| `app/` | Android TV demo app: 100 rows, 6 card types, 5 demo screens. Run on a TV emulator or device. |
| `consumer-kmp/` | Verification module — a KMP library whose `commonMain` uses `RokuLazyRow` / `RokuLazyColumn`. |
| `verification/published-consumer/` | Standalone Gradle build that resolves the **published** artifact from `mavenLocal` in `commonMain`. |

### Verifying a change

```bash
# Library: commonMain, every target, and the shared unit tests
./gradlew :roku-focus-list:compileCommonMainKotlinMetadata :roku-focus-list:desktopTest
./gradlew :roku-focus-list:compileAndroidMain :roku-focus-list:compileKotlinDesktop
./gradlew :roku-focus-list:compileKotlinIosArm64 :roku-focus-list:compileKotlinIosX64 :roku-focus-list:compileKotlinIosSimulatorArm64

# KMP consumption through a project dependency
./gradlew :consumer-kmp:compileCommonMainKotlinMetadata :consumer-kmp:compileAndroidMain

# Android demo, debug and minified release
./gradlew :app:assembleDebug :app:assembleRelease

# KMP consumption through the published Maven coordinate
./gradlew :roku-focus-list:publishToMavenLocal
./gradlew -p verification/published-consumer verifyCommonMainConsumption
./gradlew -p verification/published-consumer printRokuFocusResolution
```

The standalone consumer needs an Android SDK: set `ANDROID_HOME`, or create
`verification/published-consumer/local.properties` with `sdk.dir=/path/to/Android/Sdk`.

---

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
