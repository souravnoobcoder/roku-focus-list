# roku-focus-list

[![](https://jitpack.io/v/souravnoobcoder/roku-focus-list.svg)](https://jitpack.io/#souravnoobcoder/roku-focus-list)

Kotlin Multiplatform / Compose Multiplatform library providing Roku-style fixed-focus navigation.
The focus highlight stays at a fixed screen position while content scrolls behind it — both
horizontally (within rows) and vertically (between rows).

Full documentation, API reference, migration notes, and examples live in the
[root README](../README.md).

## Module layout

```
roku-focus-list/
  src/commonMain/kotlin/com/rokufocus/   all library code — no platform source sets
  src/commonTest/kotlin/com/rokufocus/   shared unit tests for the focus and selection logic
  consumer-rules.pro                     R8 keep rules, published inside the Android AAR
```

There is deliberately no `androidMain` / `iosMain` / `desktopMain`: nothing in the library needs a
platform API. Key-repeat throttling uses `kotlin.time.TimeSource.Monotonic` instead of Android's
`SystemClock`.

## Targets

`androidTarget` (via `com.android.kotlin.multiplatform.library`), `jvm("desktop")`, `iosX64`,
`iosArm64`, `iosSimulatorArm64`.

## Add the dependency

From a Kotlin Multiplatform project:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.souravnoobcoder:roku-focus-list:2.0.0")
        }
    }
}
```

From an Android-only Jetpack Compose project:

```kotlin
dependencies {
    implementation("com.github.souravnoobcoder:roku-focus-list:2.0.0")
}
```

## Quick example

```kotlin
@Composable
fun HomeScreen() {
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

## Source map

| File | Role |
|---|---|
| `RokuApi.kt` | The four public entry points: `RokuLazyRow` and `RokuLazyColumn`, DSL and state variants. |
| `RokuScope.kt` | DSL scopes — `items { }`, `row { }`, `customRow { }`. |
| `RokuLazyRow.kt` / `RokuLazyColumn.kt` | Layout, geometry, highlight animation, semantics. |
| `RokuRowContent.kt` | Internal LazyRow renderer with programmatic scrolling and per-item semantics. |
| `RokuFocusListState.kt` / `RokuColumnState.kt` | Selection state, savers, focus handles. |
| `RokuKeyHandler.kt` / `RokuColumnKeyHandler.kt` | D-pad handling and edge policy. |
| `RokuRowSelection.kt` | Pure helpers for stepping over rows with nothing to select. |
| `RokuFocusConfig.kt` / `RokuFocusEscape.kt` / `RokuAnimationSpec.kt` | Configuration. |
| `RokuHighlightScope.kt` / `RokuFocusHighlight.kt` | The highlight lambda's receiver and the default highlight. |
| `RokuResolvedRow.kt` | Internal uniform view of card rails and custom rows. |
| `RokuKeyRepeat.kt` / `RokuClock.kt` | Key-repeat throttling and its multiplatform clock. |

## Build and test

```bash
./gradlew :roku-focus-list:compileCommonMainKotlinMetadata
```

```bash
./gradlew :roku-focus-list:desktopTest
```

```bash
./gradlew :roku-focus-list:compileAndroidMain :roku-focus-list:compileKotlinDesktop :roku-focus-list:compileKotlinIosSimulatorArm64
```

```bash
./gradlew :roku-focus-list:publishToMavenLocal
```
