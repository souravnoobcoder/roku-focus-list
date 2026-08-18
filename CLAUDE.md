# roku-focus-list — Project Context

## What This Is
Kotlin Multiplatform / Compose Multiplatform library implementing Roku-style fixed-focus
navigation. The focus highlight stays at a fixed screen position while content scrolls behind it —
both horizontally (within rows) and vertically (between rows). Primary target is Android TV /
Fire TV; the library also builds for desktop JVM and iOS.

## Architecture

### Library module: `roku-focus-list/`

Kotlin Multiplatform. **All code is in `src/commonMain/kotlin/com/rokufocus/`** — there is no
`androidMain`, `iosMain`, or `desktopMain` source set, because nothing in the library needs a
platform API.

| File | Role |
|---|---|
| `RokuApi.kt` | **Public API.** Four entry points: `RokuLazyRow` (DSL + state overloads) and `RokuLazyColumn` (DSL + state overloads). The DSL `RokuLazyRow` invisibly composes item 0 to auto-measure item width. |
| `RokuScope.kt` | DSL scopes: `RokuItemScope.items(...)`, `RokuLazyColumnScope.row(...)` / `.customRow(...)`, `@RokuDsl`. Plain collector classes, no magic. `key` and `initialIndex` sit *after* the 1.x parameters so positional calls keep their meaning. |
| `RokuRowContent.kt` | Internal pure LazyRow renderer. No focus, no highlight. Scrolls via `animateScrollToItem(windowStart)`. |
| `RokuLazyRow.kt` | `RokuLazyRowImpl` — **standalone** horizontal row. `RokuRowContent` + focusable + key handler + highlight overlay. For use outside a column. |
| `RokuLazyColumn.kt` | `RokuLazyColumnImpl` — **OTT layout**. Single focusable composable. LazyColumn of `RokuRowContent` items. Renders ONE global highlight that animates X/Y/width/height between rows. Uses `BoxWithConstraints` for accurate viewport measurement. |
| `RokuFocusListState.kt` | State holder per row. `selectedIndex` is **derived** from `requestedIndex` coerced into the current range; `windowStart` / `highlightSlot` / `visibleCount` as before, plus `Saver`, `hasFocus`, `requestFocus()`. Also holds `computeHighlightOffsetPx()`. |
| `RokuColumnState.kt` | Public column state: derived `selectedRowIndex`, `requestedRowIndex`, `rowCount`, `hasSelectableRow`, `hasFocus`, `requestFocus()`, `Saver`, `rememberRokuColumnState`. |
| `RokuRowSelection.kt` | Pure `nextSelectableRow` / `nearestSelectableRow` — how UP/DOWN steps over rows with nothing to select. |
| `RokuResolvedRow.kt` | Internal sealed view of a column row (`Items` rail vs consumer-drawn `Custom`) + the `RokuNavKey` enum handed to `customRow`. |
| `RokuHighlightScope.kt` | Receiver of `focusHighlight`: `BoxScope` + `rowIndex` / `itemIndex`. `isFocused` stays a lambda parameter so 1.x highlight lambdas still compile. |
| `RokuFocusEscape.kt` | Per-edge focus escape (`start`, `end`, `up`, `down`) with `All` / `None` / `Horizontal` / `Vertical` presets. |
| `RokuKeyRepeat.kt` | `RokuKeyRepeatTracker` — key-repeat throttle + acceleration counters, held by the state objects. Plain fields, never read during composition. |
| `RokuColumnRowConfig.kt` | Per-row config for the state-based `RokuLazyColumn`: state, itemWidth/Height, spacing, contentPadding, headerHeight, key, itemContentDescription. |
| `RokuFocusHighlight.kt` | `DefaultFocusHighlight` — BoxScope extension. Rounded border drawn OUTSIDE card bounds via `drawBehind` + `graphicsLayer { clip = false }` with configurable `overflow` (default 6dp). |
| `RokuKeyHandler.kt` | `Modifier.rokuKeyHandler()` — used by standalone `RokuLazyRow`. Handles LEFT/RIGHT + Enter, passes UP/DOWN through. A plain modifier factory; repeat state lives on `RokuFocusListState`. Also holds `moveWithinRow` and the escape-edge lookup. |
| `RokuColumnKeyHandler.kt` | Internal `Modifier.rokuColumnKeyHandler()`. Handles ALL D-pad events for `RokuLazyColumn`: UP/DOWN between selectable rows, LEFT/RIGHT/ENTER to the active row's state or to a custom row's `onKeyEvent`. |
| `RokuClock.kt` | Internal monotonic ms clock built on `kotlin.time.TimeSource.Monotonic`. Replaces `android.os.SystemClock.uptimeMillis()`. |
| `RokuFocusConfig.kt` | Config data class: animation spec, key repeat delay + acceleration, wrapAround, haptics, `focusEscape`. Keeps a deprecated `allowFocusEscape` factory and read-path extension. |
| `RokuAnimationSpec.kt` | Preset animation specs (Default, Fast, Smooth). |

Tests live in `src/commonTest/kotlin/com/rokufocus/` and run on the desktop JVM target
(`:roku-focus-list:desktopTest`): `RokuFocusListStateTest`, `RokuColumnStateTest`,
`RokuRowSelectionTest`, `RokuRowMovementTest`, `RokuFocusEscapeTest`,
`RokuKeyRepeatTrackerTest`, `RokuHighlightOffsetTest`, `RokuClockTest`.

### Key design decisions

- **`visibleCount` is auto-computed** from viewport width, padding, item width, and spacing. Consumer never specifies it.
- **Scroll overflow correction**: when `animateScrollToItem(windowStart)` clamps at list end, `computeHighlightOffsetPx()` computes the overflow (`desiredScroll - maxScroll`) and shifts the highlight to match actual item position.
- **LaunchedEffect keyed on `windowStart`** (not `selectedIndex`) — prevents redundant scroll animations at list edges where windowStart is clamped.
- **RokuLazyColumn uses a single global highlight** that animates all 4 dimensions (X, Y, width, height) when navigating between rows. Per-row highlights were removed — `RokuRowContent` is highlight-free.
- **`RokuColumnRowConfig.headerHeight`** must match actual rendered header height for correct vertical highlight Y positioning. The Y calculation: `topPadding + verticalScrollOverflow + headerHeight`.
- **Selection is derived, never stored coerced.** Both state objects keep the raw *requested* index and coerce on read. That is what makes "restore to row 5 while one row exists" land on row 5 once the rows arrive, and it means there is exactly one coercion site.
- **A row with zero items is not selectable.** UP/DOWN steps over it, the highlight never parks on it, it renders nothing (not even its header) and contributes zero height to the column geometry. Its row index and key are unchanged.
- **The column re-scrolls when the geometry changes, not only when the selection does.** `animateScrollToItem` clamps at the end of a still-loading list; without re-running when rows arrive, the real scroll offset diverges from the offset the highlight maths assumes.
- **`RokuLazyColumn` retracts `hasFocus` from the row state it last marked**, so a hoisted row state is never left reading "focused" by a column that no longer renders it.
- **`RokuClock` offsets readings by a 1,000,000ms baseline.** The key handlers seed `lastKeyTime = 0L` to mean "no key pressed yet"; `SystemClock.uptimeMillis()` returned time since boot so 0 always looked far in the past. A clock starting near zero would have made the first D-pad press get throttled. Do not remove the baseline.
- **`@SuppressLint` is unavailable in commonMain.** `UnusedBoxWithConstraintsScope` is disabled via `lint { disable += ... }` in the library's `kotlin { android { } }` block instead.

### Highlight positioning math (horizontal)
```
stepPx = itemWidthPx + itemSpacingPx
totalContentPx = startPad + itemCount * itemWidthPx + (itemCount-1) * spacingPx + endPad
maxScrollPx = max(0, totalContent - viewport)
desiredScrollPx = windowStart * stepPx
scrollOverflowPx = max(0, desiredScroll - maxScroll)
highlightX = startPadPx + scrollOverflowPx + highlightSlot * stepPx
```

### Highlight positioning math (vertical, in RokuLazyColumn)
```
rowCumOffset[i] = sum of (rowHeight[j] + spacing) for j in 0..<i
totalColumnContent = topPad + sum(rowHeights) + (rows-1)*spacing + bottomPad
maxVerticalScroll = max(0, totalColumnContent - viewportHeight)
desiredVerticalScroll = rowCumOffset[selectedRowIndex]    // empty rows contribute 0 height
verticalOverflow = max(0, desired - max)
highlightY = topPad + verticalOverflow + headerHeight[selectedRow]
```

## Demo app: `app/`

Android-only (`com.android.application`). `ROW_COUNT = 100` rows generated by cycling 9 `baseRows`,
6 card types, 7 demo screens (Column DSL, Mixed rows, Late-arriving rows, Row DSL, Row + State,
Wrap-Around, Plain Compose comparison). Images from `picsum.photos`. Screens are wrapped in a
`rememberSaveableStateHolder` so selection survives switching destinations.

| Card | File | Size | Used in |
|---|---|---|---|
| BannerCard | `BannerCard.kt` | 580×310dp | Hero |
| WideCard | `WideCard.kt` | 300×170dp | Featured, Critically Acclaimed |
| MovieCard | `MovieCard.kt` | 220×140dp | Trending, Continue Watching, Action, Sci-Fi |
| ContinueWatchingCard | `ContinueWatchingCard.kt` | 220×140dp + progress bar | CardType.CONTINUE (not in `baseRows`) |
| PortraitCard | `PortraitCard.kt` | 150×220dp | New Releases, Drama |
| MiniCard | `MiniCard.kt` | 100dp square image + label | CardType.MINI (not in `baseRows`) |

`SampleData.kt` generates items programmatically by cycling 45 base entries.
`App.kt` configures Coil 3 singleton ImageLoader with crossfade.

## Verification modules

| Module | Purpose |
|---|---|
| `consumer-kmp/` | KMP library whose `commonMain` uses `RokuLazyRow` / `RokuLazyColumn` via `project(":roku-focus-list")`. Proves commonMain consumption compiles for android + desktop + iOS. |
| `verification/published-consumer/` | **Standalone** Gradle build (own `settings.gradle.kts`, not in root settings). Resolves `com.github.souravnoobcoder:roku-focus-list:2.0.0` from `mavenLocal()` in `commonMain`. Run with `./gradlew -p verification/published-consumer verifyCommonMainConsumption`. Proves the published Gradle module metadata works. |

## Build

- AGP 9.0.1, Gradle 9.1.0, Kotlin 2.2.21, Compose Multiplatform 1.10.3
- Compose BOM 2026.03.00 in the demo app — pins androidx Compose to 1.10.5, which is exactly what CMP 1.10.3 resolves to on Android. Do not desync these.
- minSdk 24, compileSdk 36, jvmTarget 11
- Library depends only on CMP runtime/runtime-saveable/foundation/ui/animation (no Material)
- Demo app adds Coil 3 (`coil-compose` + `coil-network-okhttp`), Material3
- **The demo app must NOT apply `org.jetbrains.kotlin.android`** — AGP 9 has built-in Kotlin support and hard-errors if KGP's android plugin is applied. It picks up KGP 2.2.21 from the root buildscript classpath.
- Library module uses `com.android.kotlin.multiplatform.library` with the `kotlin { android { } }` block. `androidLibrary { }` is the deprecated alias.
- CMP 1.10 deprecates the `compose.foundation` / `compose.ui` shorthand accessors. Dependencies are declared as explicit `org.jetbrains.compose.*` coordinates in `gradle/libs.versions.toml` (`compose-mp-*` aliases).
- CMP 1.10 also deprecates `org.jetbrains.compose.ui.tooling.preview.Preview` in favour of `androidx.compose.ui.tooling.preview.Preview` from `org.jetbrains.compose.ui:ui-tooling-preview`.

## Publishing

- Group `com.github.souravnoobcoder`, artifact `roku-focus-list`, version `2.0.0`. Group must match the GitHub owner for JitPack to resolve.
- KMP `maven-publish` creates 6 publications: `kotlinMultiplatform` (root, carries the commonMain metadata variant and redirects), `android`, `desktop`, `iosArm64`, `iosSimulatorArm64`, `iosX64`.
- Consumers only ever reference the root coordinate.
- `consumer-rules.pro` is published inside the AAR as `proguard.txt` via `optimization { consumerKeepRules.apply { publish = true; file(...) } }`.

## Known issues / future work

- `headerHeight` in `RokuColumnRowConfig` must be specified manually — could be measured at runtime
- Vertical `focusSlot` is hardcoded to 0 (top-aligned) — could be made configurable like horizontal
- `RokuLazyRow` standalone doesn't know `itemHeight`, so highlight overflow works on width only (height uses `fillMaxHeight`)
- An empty row still leaves the row spacing on either side of it, because `LazyColumn` allocates spacing around a zero-height item
- `RokuFocusEscape.start` / `.end` map to LEFT / RIGHT; nothing mirrors for RTL yet
- Public `data class`es (`RokuFocusConfig`, `RokuFocusEscape`, `RokuColumnRowConfig`) make the ABI hard to evolve; there is no binary-compatibility validator yet
- iOS klibs compile but the library has not been exercised on an iOS runtime
- Accessibility was verified from the emitted node tree (`uiautomator dump` on an API 31 TV emulator), not end to end with TalkBack
- Web (`wasmJs`/`js`) targets are not declared; CMP web is still Beta
