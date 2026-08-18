# roku-focus-list

[![](https://jitpack.io/v/reshusingh07/roku-focus-list.svg)](https://jitpack.io/#reshusingh07/roku-focus-list)

Kotlin Multiplatform / Compose Multiplatform library providing Roku-style fixed-focus navigation.
The focus highlight stays at a fixed screen position while content scrolls behind it — both
horizontally (within rows) and vertically (between rows).

Full documentation, API reference, and examples live in the [root README](../README.md).

## Module layout

```
roku-focus-list/
  src/commonMain/kotlin/com/rokufocus/   all library code — no platform source sets
  src/commonTest/kotlin/com/rokufocus/   shared unit tests for the focus math
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
            implementation("com.github.reshusingh07:roku-focus-list:1.0.0")
        }
    }
}
```

From an Android-only Jetpack Compose project:

```kotlin
dependencies {
    implementation("com.github.reshusingh07:roku-focus-list:1.0.0")
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

## Build and test

```bash
./gradlew :roku-focus-list:compileCommonMainKotlinMetadata
./gradlew :roku-focus-list:desktopTest
./gradlew :roku-focus-list:compileAndroidMain :roku-focus-list:compileKotlinDesktop
./gradlew :roku-focus-list:publishToMavenLocal
```
