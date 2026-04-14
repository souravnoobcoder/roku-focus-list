# roku-focus-list — Project Context

## What This Is
Android TV library implementing Roku-style fixed-focus navigation. The focus highlight stays at a fixed screen position while content scrolls behind it — both horizontally (within rows) and vertically (between rows).

## Architecture

### Library module: `roku-focus-list/`

| File | Role |
|---|---|
| `RokuRowContent.kt` | Internal pure LazyRow renderer. No focus, no highlight. Scrolls via `animateScrollToItem(windowStart)`. |
| `RokuLazyRow.kt` | **Standalone** horizontal row. Wraps `RokuRowContent` + adds focusable + key handler + highlight overlay. For use outside a column. |
| `RokuLazyColumn.kt` | **OTT layout**. Single focusable composable. LazyColumn of RokuRowContent items. Handles ALL D-pad events (UP/DOWN for rows, LEFT/RIGHT delegated to active row state). Renders ONE global highlight overlay that animates X/Y/width/height between rows. Uses `BoxWithConstraints` for accurate viewport measurement. |
| `RokuFocusListState.kt` | State holder per row. `selectedIndex`, `windowStart`, `highlightSlot`, `visibleCount` (auto-computed by composable). Also contains `computeHighlightOffsetPx()` utility. |
| `RokuFocusHighlight.kt` | `DefaultFocusHighlight` — BoxScope extension. White rounded border drawn OUTSIDE card bounds via `drawBehind` + `graphicsLayer { clip = false }` with configurable `overflow` (default 6dp). |
| `RokuKeyHandler.kt` | `Modifier.rokuKeyHandler()` — used by standalone `RokuLazyRow`. Handles LEFT/RIGHT, passes UP/DOWN through. Uses `composed` for state. |
| `RokuFocusConfig.kt` | Config data class: animation spec, key repeat delay, wrapAround, haptic feedback. |
| `RokuAnimationSpec.kt` | Preset animation specs (Default, Fast, Smooth). |

### Key design decisions

- **`visibleCount` is auto-computed** from viewport width, padding, item width, and spacing. Consumer never specifies it.
- **Scroll overflow correction**: When `animateScrollToItem(windowStart)` clamps at list end, `computeHighlightOffsetPx()` computes the overflow (`desiredScroll - maxScroll`) and shifts the highlight to match actual item position.
- **LaunchedEffect keyed on `windowStart`** (not `selectedIndex`) — prevents redundant scroll animations at list edges where windowStart is clamped.
- **RokuLazyColumn uses a single global highlight** that animates all 4 dimensions (X, Y, width, height) when navigating between rows. Per-row highlights were removed — `RokuRowContent` is highlight-free.
- **`RokuColumnRowConfig.headerHeight`** must match actual rendered header height for correct vertical highlight Y positioning. The Y calculation: `topPadding + verticalScrollOverflow + headerHeight`.

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
desiredVerticalScroll = rowCumOffset[selectedRowIndex]
verticalOverflow = max(0, desired - max)
highlightY = topPad + verticalOverflow + headerHeight[selectedRow]
```

## Demo app: `app/`

10 rows, 6 card types, 308 total items using `picsum.photos` images.

| Card | File | Size | Used in |
|---|---|---|---|
| BannerCard | `BannerCard.kt` | 700×370dp | Hero |
| WideCard | `WideCard.kt` | 300×170dp | Featured, Critically Acclaimed |
| MovieCard | `MovieCard.kt` | 220×140dp | Trending, Continue Watching, Action, Sci-Fi |
| ContinueWatchingCard | `ContinueWatchingCard.kt` | 220×140dp + progress bar | (available but not used currently) |
| PortraitCard | `PortraitCard.kt` | 150×220dp | New Releases, Drama |
| MiniCard | `MiniCard.kt` | 100×120dp | Quick Picks |

`SampleData.kt` generates items programmatically by cycling 45 base entries.
`App.kt` configures Coil 3 singleton ImageLoader with crossfade.

## Build

- AGP 9.0.1, Gradle 9.1.0, Kotlin 2.0.21
- Compose BOM 2024.09.00
- minSdk 24, compileSdk release(36)
- Library depends only on compose foundation/ui/animation/runtime (no Material)
- Demo app adds Coil 3 (`coil-compose` + `coil-network-okhttp`), Material3

## Known issues / future work

- `headerHeight` in `RokuColumnRowConfig` must be specified manually — could be measured at runtime
- Vertical `focusSlot` is hardcoded to 0 (top-aligned) — could be made configurable like horizontal
- `RokuLazyRow` standalone doesn't know `itemHeight`, so highlight overflow works on width only (height uses `fillMaxHeight`)
- RoundCard was removed from demo but `RoundCard.kt` file still exists
