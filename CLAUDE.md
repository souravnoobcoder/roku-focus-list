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
| `RokuApi.kt` | **Public API.** Five entry points: `RokuLazyRow` (DSL + state overloads), `RokuLazyColumn` (DSL + state overloads) and `RokuFocusGrid` (state-based, delegates to `RokuFocusGridImpl`). The DSL `RokuLazyRow` invisibly composes item 0 to auto-measure item width, and takes an optional hoisted `state` (appended before `content`) so a host can drive it from outside without giving up the auto-measure; when given, `focusSlot` / `initialIndex` / `focusMode` are ignored in favour of the state's own. The column DSL does the same per row for omitted `itemWidth` / `itemHeight` / `headerHeight` (`RokuColumnAutoMeasure`); a row still waiting on measurement is treated exactly like an empty row. |
| `RokuScope.kt` | DSL scopes: `RokuItemScope.items(...)`, `RokuLazyColumnScope.row(...)` / `.customRow(...)`, `@RokuDsl`. Plain collector classes, no magic. `key` and `initialIndex` sit *after* the 1.x parameters so positional calls keep their meaning. |
| `RokuRowContent.kt` | Internal pure LazyRow renderer. No focus, no highlight. Scrolls via `snapshotFlow { windowStart }` → `RokuScrollAnimator` (collectLatest), so scrolling never recomposes it. Per-item `derivedStateOf` for selection; `rowFocused` lambda merged per item. |
| `RokuScrollAnimator.kt` | Internal. Drives any `ScrollableState` (`animateTo(scrollable, currentPx, targetPx, viewportPx, farJump)`) to an absolute pixel offset with one spring whose velocity is carried across retargets (`AnimationState` + `sequentialAnimation`), so a run of quick moves scrolls as one motion. The caller supplies the far-jump fallback (its `animateScrollToItem`); `scrollToIndex` is the `LazyListState` convenience. Also the `absoluteOffsetPx` / `targetOffsetPx` helpers the list renderers use. |
| `RokuGridState.kt` | Public grid state: linear `selectedIndex` **derived** from `requestedIndex`; `columns`, `rowCount` (ceil), `selectedRow` / `selectedColumn`; raw floating `windowAnchorRow` contained at write time, `windowStartRow` / `highlightRowSlot` on read; `visibleRows` set by the composable. `moveColumnsBy` (row-clamped, or reading-order flow with wrapAround), `moveRowsBy` (column-keeping, shorter last row → its last cell), `moveBy` (reading order); internal `moveColumnSteps` / `moveRowSteps` / `moveLinearSteps` cores; `updateColumns`; `Saver`; `rememberRokuGridState` (**defaults to Floating**). |
| `RokuGridKeyHandler.kt` | Internal `Modifier.rokuGridKeyHandler()`: LEFT/RIGHT along the row, UP/DOWN by rows, Enter clicks; same throttle/acceleration and per-edge escape as the others. |
| `RokuFocusGrid.kt` | `RokuFocusGridImpl` — `LazyVerticalGrid(GridCells.Fixed(columns), userScrollEnabled = false)` inside `BoxWithConstraints`. Cell width = (viewport − padding − gaps) / columns; `visibleRows` from the viewport height; scroll via `snapshotFlow { windowStartRow }` → `RokuScrollAnimator.animateTo(gridState, …)` with the first cell of the window row as the far-jump target, keyed on `rowCount` so late rows re-run a clamped scroll; one highlight overlay at `(startPad + column × cellPitch, topPad + overflow + highlightRowSlot × rowPitch)` with the same tail overflow correction as the rails. |
| `RokuLazyRow.kt` | `RokuLazyRowImpl` — **standalone** horizontal row. `RokuRowContent` + focusable + key handler + highlight overlay. For use outside a column. |
| `RokuLazyColumn.kt` | `RokuLazyColumnImpl` — **OTT layout**. Single focusable composable. LazyColumn of `RokuRowContent` items. Renders ONE global highlight that animates X/Y/width/height between rows. Uses `BoxWithConstraints` for accurate viewport measurement. |
| `RokuFocusListState.kt` | State holder per row. `selectedIndex` is **derived** from `requestedIndex` coerced into the current range; `windowStart` / `highlightSlot` / `visibleCount` as before, plus `Saver`, `hasFocus`, `requestFocus()`, `focusMode` and the raw floating `windowAnchor`. `moveBy(steps)` is the coalesced multi-step move; `moveNext` / `movePrevious` are `moveSteps(±1)` over the same internal core. Also holds `computeHighlightOffsetPx()`. |
| `RokuColumnState.kt` | Public column state: derived `selectedRowIndex`, `requestedRowIndex`, `rowCount`, `hasSelectableRow`, `hasFocus`, `requestFocus()`, `Saver`, `rememberRokuColumnState`. `moveRowsBy(steps)` / internal `moveRowSteps` step over unselectable rows the way UP/DOWN do. `activeRowState` is the selected rail's `RokuFocusListState` (null for a custom row or an empty column), published by `RokuLazyColumnImpl` on every pass whether or not the column is focused; `moveItemsBy(steps)` moves within it. This is what lets a host drive horizontal swipes through the `row { }` DSL, whose row states are private. |
| `RokuRowSelection.kt` | Pure `nextSelectableRow` / `nearestSelectableRow` — how UP/DOWN steps over rows with nothing to select. |
| `RokuResolvedRow.kt` | Internal sealed view of a column row (`Items` rail vs consumer-drawn `Custom`) + the `RokuNavKey` enum handed to `customRow`. |
| `RokuHighlightScope.kt` | Receiver of `focusHighlight`: `BoxScope` + `rowIndex` / `itemIndex`. `isFocused` stays a lambda parameter so 1.x highlight lambdas still compile. |
| `RokuFocusEscape.kt` | Per-edge focus escape (`start`, `end`, `up`, `down`) with `All` / `None` / `Horizontal` / `Vertical` presets. |
| `RokuFocusMode.kt` | `Static` (fixed slot, content scrolls — default) vs `Floating` (highlight walks the window, scrolls only at its edges). Per axis: horizontal on `RokuFocusListState.focusMode`, vertical via `RokuLazyColumn(verticalFocusMode = ...)`. |
| `RokuKeyRepeat.kt` | `RokuKeyRepeatTracker` — key-repeat throttle + acceleration counters, held by the state objects. Plain fields, never read during composition. `reset()` clears the acceleration streak (not the throttle); the multi-step moves call it. |
| `RokuColumnRowConfig.kt` | Per-row config for the state-based `RokuLazyColumn`: state, itemWidth/Height, spacing, contentPadding, headerHeight, key, itemContentDescription. |
| `RokuFocusHighlight.kt` | `DefaultFocusHighlight` — BoxScope extension. Rounded border drawn OUTSIDE card bounds via `drawBehind` + `graphicsLayer { clip = false }` with configurable `overflow` (default 6dp). |
| `RokuKeyHandler.kt` | `Modifier.rokuKeyHandler()` — used by standalone `RokuLazyRow`. Handles LEFT/RIGHT + Enter, passes UP/DOWN through. A plain modifier factory; repeat state lives on `RokuFocusListState`. Also holds `moveWithinRow`, the escape-edge lookup, and the public `rokuMoveBy` / `rokuMoveRowsBy` / `rokuMoveItemsBy` — edge-aware multi-step moves for touchpad input (a row, a column's rows, a column's active row) that apply the escape policy and `onBoundaryHit` once per move, never per step. |
| `RokuColumnKeyHandler.kt` | Internal `Modifier.rokuColumnKeyHandler()`. Handles ALL D-pad events for `RokuLazyColumn`: UP/DOWN between selectable rows, LEFT/RIGHT/ENTER to the active row's state or to a custom row's `onKeyEvent`. |
| `RokuClock.kt` | Internal monotonic ms clock built on `kotlin.time.TimeSource.Monotonic`. Replaces `android.os.SystemClock.uptimeMillis()`. |
| `RokuFocusConfig.kt` | Config data class: animation spec, key repeat delay + acceleration, wrapAround, haptics, `focusEscape`, and the swipe knobs (`swipeVelocityThreshold`, `swipeMaxSteps`, `swipeSensitivity`, `swipeStepsForVelocity`) appended last so positional 2.x calls keep their meaning. `RokuFocusConfig.stepsForVelocity(velocity)` is the velocity → step-count curve. Keeps a deprecated `allowFocusEscape` factory and read-path extension. |
| `RokuAnimationSpec.kt` | Preset animation specs (Default, Fast, Smooth). |

Tests live in `src/commonTest/kotlin/com/rokufocus/` and run on the desktop JVM target
(`:roku-focus-list:desktopTest`): `RokuFocusListStateTest`, `RokuColumnStateTest`,
`RokuRowSelectionTest`, `RokuRowMovementTest`, `RokuFocusEscapeTest`,
`RokuKeyRepeatTrackerTest`, `RokuHighlightOffsetTest`, `RokuFloatingWindowTest`, `RokuClockTest`,
`RokuMoveByTest`, `RokuSwipeVelocityTest`, `RokuGridStateTest`.

### Key design decisions

- **`visibleCount` is auto-computed** from viewport width, padding, item width, and spacing. Consumer never specifies it.
- **Scroll overflow correction**: when `animateScrollToItem(windowStart)` clamps at list end, `computeHighlightOffsetPx()` computes the overflow (`desiredScroll - maxScroll`) and shifts the highlight to match actual item position.
- **LaunchedEffect keyed on `windowStart`** (not `selectedIndex`) — prevents redundant scroll animations at list edges where windowStart is clamped.
- **RokuLazyColumn uses a single global highlight** that animates all 4 dimensions (X, Y, width, height) when navigating between rows. Per-row highlights were removed — `RokuRowContent` is highlight-free.
- **`RokuColumnRowConfig.headerHeight`** must match actual rendered header height for correct vertical highlight Y positioning. The Y calculation: `topPadding + verticalScrollOverflow + headerHeight`.
- **Selection is derived, never stored coerced.** Both state objects keep the raw *requested* index and coerce on read. That is what makes "restore to row 5 while one row exists" land on row 5 once the rows arrive, and it means there is exactly one coercion site.
- **Floating mode stores a raw window anchor, contained at write time.** `RokuFocusListState.windowAnchor` / `RokuColumnState.windowAnchorRow` follow the requested-index philosophy: stored raw, bounds-clamped on read, so a shrunken list gives the window back when items return. Containment (shifting the anchor minimally when the selection exits the window) runs from the writes — `scrollTo`, `updateItemCount`, the `visibleCount` setter, and for the column from composition in `RokuLazyColumnImpl`, the only place the pixel geometry exists. It is deliberately NOT in the `windowStart` getter: a read-side shift that is never stored flip-flops back to the stale anchor on the next in-window move. Containment compares against the *clamped* window so a purely data-driven shrink never overwrites the raw anchor. Both anchors are in the `Saver`s. `focusSlot` is ignored while floating. In `Static` mode every code path is unchanged.
- **A key press does no O(rows) work and allocates nothing.** `ColumnGeometry` precomputes every per-row px value (header/content/item/spacing/padding arrays, cumulative offsets, max scroll) inside a `derivedStateOf` keyed on the resolved rows — its only observable inputs are the rows' item counts, so it recomputes when content arrives or leaves, never on a selection move, which just indexes the arrays. The per-rail `visibleCount` sync is `remember`-keyed on (rows, maxWidth, layoutDirection) for the same reason. `RowMetrics`/`ActiveRowPx` (per-press list allocation + O(rows) value compares) were removed.
- **Recomposition is confined to what actually changed.** The column's LazyColumn row content is ONE remembered lambda (`rows` is an unstable List — an inline lambda would be recreated every pass and invalidate every visible row and, through the replaced inner composable lambdas, every card wrapper). Selection is read per item/per row via `derivedStateOf`, and `RokuRowContent` receives row focus as a lambda, not a Boolean. `updateItemCount` / `visibleCount` guard on value equality BEFORE calling `containWindow()` — they run from composition every pass, and containment's selection reads would otherwise subscribe that scope to every future key press (measured: this was recomposing the whole DSL wrapper per press).
- **DSL rows auto-measure omitted sizes.** `row()` defaults `itemWidth`/`itemHeight`/`headerHeight` to `Dp.Unspecified`; `RokuColumnAutoMeasure` composes the first item / header invisibly once per row (keyed like row state) and stores dp sizes in state maps. While unmeasured, `RokuResolvedRow.Items.awaitingMeasure` makes the row behave exactly like an empty one (unselectable, zero geometry, renders nothing), so nothing scrolls or highlights against made-up sizes; measurement lands within a frame through the late-arriving-rows machinery.
- **A row with zero items is not selectable.** UP/DOWN steps over it, the highlight never parks on it, it renders nothing (not even its header) and contributes zero height to the column geometry. Its row index and key are unchanged.
- **The column re-scrolls when the geometry changes, not only when the selection does.** `animateScrollToItem` clamps at the end of a still-loading list; without re-running when rows arrive, the real scroll offset diverges from the offset the highlight maths assumes.
- **`RokuLazyColumn` retracts `hasFocus` from the row state it last marked**, so a hoisted row state is never left reading "focused" by a column that no longer renders it.
- **The vertical accelerated-repeat snap is intentional, and the horizontal path deliberately has no counterpart.** `RokuLazyColumn` swaps `animateScrollToItem` for an instant `scrollToItem` once `keyRepeat.consecutivePresses > config.keyRepeatAccelAfter`; `RokuRowContent` always animates, relying on `collectLatest` to cancel and re-target an in-flight animation instead. The asymmetry is the settled choice, in that direction: animation is the default everywhere and the snap is only an escape hatch for a user holding the key down. Instrumented on real Apple TV hardware across 35 vertical moves the snap branch fired **once**, and it was not the cause of the jerky scrolling in that investigation (density/frame-rate was — see the README). Do not resolve the asymmetry by adding a snap branch to the horizontal path; if it is ever resolved, resolve it toward always animating.
- **`RokuClock` offsets readings by a 1,000,000ms baseline.** The key handlers seed `lastKeyTime = 0L` to mean "no key pressed yet"; `SystemClock.uptimeMillis()` returned time since boot so 0 always looked far in the past. A clock starting near zero would have made the first D-pad press get throttled. Do not remove the baseline.
- **`@SuppressLint` is unavailable in commonMain.** `UnusedBoxWithConstraintsScope` is disabled via `lint { disable += ... }` in the library's `kotlin { android { } }` block instead.
- **A multi-step move writes the selection once.** `moveBy(n)` / `moveRowsBy(n)` clamp the target and call `scrollTo` a single time, so a five-item swipe is one selection change, one `onItemSelected`, one highlight animation and one scroll — the coalescing a consumer cannot get by calling `moveNext()` in a loop. `moveNext` / `movePrevious` are `moveSteps(±1)` over the same internal core, never a duplicate. Wrap-around applies only when already parked on the edge being pushed against, mirroring single steps. The key-repeat arbiter: `moveBy` **resets** `consecutivePresses` so a swipe landing mid-repeat cannot compound with acceleration, while the D-pad path (`moveWithinRow`) deliberately does not — resetting inside the shared core would run before `accept()` and pin the streak at 1, so acceleration (and the column's snap branch) could never engage. Both facts are mutation-tested in `RokuMoveByTest`.
- **Scroll animations carry their velocity across retargets.** `animateScrollToItem` builds a fresh `AnimationState(0f)` per call, so every interrupting move (key repeat, or the run of single steps a touchpad drag/fling produces) stopped the content dead and eased in again from rest — a visible pulse per item at 60–200 ms cadence. `RokuScrollAnimator` keeps one `AnimationState` per list and passes `sequentialAnimation = velocity != 0f`, so a retarget bends the motion. It drives the list with `scrollBy` per frame using the *consumed* delta (no drift; hitting the end cancels), takes the target from the visible item's measured offset when it is on screen and from the row/item geometry otherwise, and clamps a carried-velocity overshoot to the start→target segment. Far jumps (more than a viewport) and unmeasured lists still use `animateScrollToItem` for its teleporting, and the spring is `spring()` — the same spec `animateScrollToItem` uses — so a lone D-pad step is timed exactly as before. The vertical accelerated-repeat snap branch is untouched. Everything is animated; nothing here snaps.
- **The grid floats by default; the rails are static by default.** A wall of posters is browsed with a walking highlight everywhere (Apple TV, Android TV Leanback, Roku's own grids), so `rememberRokuGridState` defaults `focusMode` to `Floating`, while a rail keeps the Roku-style parked highlight. The grid's floating window is row-based like the horizontal one (uniform row height), not pixel-based like the column's: `windowStartRow = clamp(anchorRow, 0, rowCount − visibleRows)`, contained from the writes, and `highlightRowSlot` walks 0..visibleRows−1. Static mode reuses the same maths with `selectedRow` as the window start, so the tail overflow correction is identical. Horizontal moves never scroll a grid — a row is always fully on screen — which is why `moveColumnsBy` clamps at the row's ends by default and only flows into the next row with `wrapAround`.
- **The Compose tvOS fork already turns touchpad swipes into D-pad keys.** In the published 1.12.0 build (`ComposeSceneMediator.tvos.kt`, tag `tvos-1.12.0`) an indirect `UITouch` that travelled ≥ 40 dp between BEGAN and ENDED dispatches one `Key.Direction*` KeyDown+KeyUp at lift-off; a slow drag gets exactly one step, a long flick gets exactly one step, and `touchesCancelled` clears the pending touch so nothing is dispatched. Newer `tvos-main` has a GameController-based recogniser (`SiriRemoteTouchOracle`) that rejects contacts longer than 250 ms and still emits at most one key per contact. Consequences: (1) a host that adds its own `UIPanGestureRecognizer` must leave `cancelsTouchesInView = true` or every swipe moves twice — the sample's first build did exactly that, with the pan handler's sign flipped, which is why swipes went the wrong way and a one-step swipe netted to nothing; (2) the fork's key carries no velocity, so continuous drag tracking and fling momentum have to come from a host recogniser, which is what `sample-tvos` does. Also: the published fork squares the UIKit screen scale for the scene density, so a real Apple TV HD (scale 1) lays out at density **1.0**, a 1920×1080 dp canvas; `tvos-main` has since changed this to 2 × scale. The sample pins 960 dp (`WithTvDensity`) rather than trusting it.

### Highlight positioning math (horizontal)
```
stepPx = itemWidthPx + itemSpacingPx
totalContentPx = startPad + itemCount * itemWidthPx + (itemCount-1) * spacingPx + endPad
maxScrollPx = max(0, totalContent - viewport)
desiredScrollPx = windowStart * stepPx
scrollOverflowPx = max(0, desiredScroll - maxScroll)
highlightX = startPadPx + scrollOverflowPx + highlightSlot * stepPx

windowStart:  Static   = clamp(selectedIndex - focusSlot, 0, itemCount - visibleCount)
              Floating = clamp(windowAnchor, 0, itemCount - visibleCount)
```
In Floating the anchor only moves on containment (selection exits the window: forward to
`selected - visibleCount + 1`, backward to `selected`), so `highlightSlot` walks 0..visibleCount-1
and the same X formula follows it — no separate floating math.

### Highlight positioning math (vertical, in RokuLazyColumn)
```
rowCumOffset[i] = sum of (rowHeight[j] + spacing) for j in 0..<i
totalColumnContent = topPad + sum(rowHeights) + (rows-1)*spacing + bottomPad
maxVerticalScroll = max(0, totalColumnContent - viewportHeight)
scrollTargetRow = Static   -> selectedRowIndex
                  Floating -> containVerticalWindow(windowAnchorRow, ...)  // pixel-based, minimal shift
desiredVerticalScroll = rowCumOffset[scrollTargetRow]     // empty rows contribute 0 height
verticalOverflow = max(0, desired - max)
windowOffset = rowCumOffset[selectedRow] - desired        // 0 in Static
highlightY = topPad + windowOffset + verticalOverflow + headerHeight[selectedRow]
```
Vertical floating containment is pixel-based (rows have heterogeneous heights): the selected row's
`[top, top+height]` span must fit in `[rowCumOffset[anchor], rowCumOffset[anchor] + viewportHeight - topPad]`;
the anchor advances to the first row that fits it, or retreats to the selected row itself.

## Demo app: `app/`

Android-only (`com.android.application`). `ROW_COUNT = 100` rows generated by cycling 9 `baseRows`,
6 card types, 8 demo screens (Column DSL, Mixed rows, Late-arriving rows, Row DSL, Row + State,
Wrap-Around, Floating Focus, Plain Compose comparison). Images from `picsum.photos`. Screens are wrapped in a
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

## Apple TV sample: `sample-tvos/`

A runnable tvOS app (`tvosArm64` + `tvosSimulatorArm64`, static framework `RokuSample`) plus the
Xcode project that hosts it in `sample-tvos/tvosApp/`. It exists to put the library on a real Apple
TV and to show what a touchpad consumer has to do, since the library itself is input-agnostic.

| File | Role |
|---|---|
| `SwipeSampleScreen.kt` | Eight scenes (`Scene(layout, mode)`), stepped with the remote's Play/Pause (`Key.MediaPlayPause` via `onPreviewKeyEvent` on the root): four layouts — `RokuLazyColumn` with the `row { }` DSL (all sizes auto-measured), `RokuLazyColumn` with state-based rows, a standalone DSL `RokuLazyRow` with a hoisted `state`, and a 5-column `RokuFocusGrid` — each in Static and Floating (the grid Floating first, its default). Scenes are wrapped in `key(scene)` so a mode switch starts the layout fresh. All four are driven by one `RemoteNavigator` through a `SwipeTarget`; each layout supplies its own step distances (`railStepPoints`, or the grid's cell pitch from `BoxWithConstraints`). Under the title: one line with the last gesture, one with the frame timing it produced. `onItemSelected` logs `[roku] key …` — a key line with no `[roku] move` before it means the fork's swipe-to-focus got through. `RowHeader` is exactly `RowHeaderHeight` tall (height before padding) because the state-based column is told that height. Focus is requested with a few-frame retry: a DSL row only composes its focusable after measuring a card. |
| `SwipeTarget.kt` | `SwipeTarget { moveItems(steps); moveRows(steps) }` returning whether the selection changed. `ColumnSwipeTarget` uses `rokuMoveItemsBy` / `rokuMoveRowsBy` on the column state (same object for both column overloads); `RowSwipeTarget` uses `rokuMoveBy` and has nowhere to go vertically; `GridSwipeTarget` uses `rokuMoveColumnsBy` / `rokuMoveRowsBy(gridState, …)`. |
| `src/commonTest/.../RemoteNavigatorTest.kt` | Nine tests against a recording `SwipeTarget`, run with `:sample-tvos:tvosSimulatorArm64Test` (boots a tvOS simulator; slow the first time): a brush leans without moving, one step moves one item and leaves the remainder as lean, fast travel coalesces into one call, lift-off adds nothing and springs back, screen-direction on both axes, axis lock, velocity gain, edge pin (pushing further is dropped, one step back moves back — the earlier "pin travel at step−1" design needed two steps to reverse and this test caught it), and a new touch starting clean. |
| `RemoteNavigator.kt` | The tvOS focus-engine model on top of `rokuMoveBy` / `rokuMoveRowsBy`: **drag** moves one item per step of travel along a locked axis (0.65 card pitches horizontally, one row pitch vertically; coalesced when travel arrives faster than one item per report), travel is scaled by a smooth **velocity gain** (×1 below 2,500 pt/s, ×2 from 12,000), **nothing moves after lift-off**, and the sub-step remainder drives the **focus-movement hint** (card + highlight lean up to 12 dp and tilt 5° on a square-root curve so a brush already shows, from the very first report before the 16 pt axis lock; at a row end an `edgePull` flag pins the lean at full pull and drops further pushing, so one step of travel back moves back). Measured on the user's Apple TV HD: a relaxed flick lifts off at 4,000–8,000 pt/s, a hard one at 15,000–20,000, a full pad swipe travels 1,000–1,800 pt. The user rejected coasting after lift-off — Apple TV+ stops when the thumb does. Direction is the screen's (swipe right → right). |
| `SiriRemotePan.apple.kt` | `UIPanGestureRecognizer` on the Compose host view streaming Began / Changed(dx,dy,vx,vy) / Ended in UIKit points. `cancelsTouchesInView = true` is what suppresses the fork's own one-key swipe — except for a flick so short the pan only recognises as the touch ends, which still leaks one key (seen twice in ~130 gestures). Also prints the screen scale and `maximumFramesPerSecond` at launch (the test TV is 50 Hz). |
| `TvRemotePan.kt` | Hand-off object (`onEvent`, `screenScale`) between UIKit and the Compose tree. |
| `TvDensity.kt` | `WithTvDensity`: pins a 960 dp design width (see the density note above). |
| `FrameReport.kt` | Samples `withFrameNanos` for 1.5 s after each gesture: fps, worst frame, missed vsyncs against the panel's real refresh (`TvRemotePan.displayRefreshHz`). On demand only — awaiting frames forces them. Release build + pinned density measured 50 fps on the 50 Hz panel with every frame hit. |
| `tvosApp/` | Xcode project. The "Compile Kotlin" phase runs `./gradlew :sample-tvos:embedAndSignAppleFrameworkForXcode`; `Config.xcconfig` carries `TEAM_ID` / `BUNDLE_ID` (`com.rokufocus.sample`). |

Build for the connected Apple TV in **Release** — a Debug Kotlin/Native framework is unoptimised and
is not a fair read on smoothness:

```bash
xcodebuild -project sample-tvos/tvosApp/tvosApp.xcodeproj -scheme tvosApp -configuration Release \
  -destination 'id=<device udid>' -derivedDataPath sample-tvos/tvosApp/build/DerivedData -allowProvisioningUpdates build
xcrun devicectl device install app --device <udid> "sample-tvos/tvosApp/build/DerivedData/Build/Products/Release-appletvos/RokuFocus Sample.app"
xcrun devicectl device process launch --device <udid> --terminate-existing --console com.rokufocus.sample<TEAM_ID>
```

`--console` streams the app's stdout, so the `[roku] …` lines (pan distance/velocity, each move, frame
stats) can be read from the Mac while someone swipes on the remote.

## Verification modules

| Module | Purpose |
|---|---|
| `consumer-kmp/` | KMP library whose `commonMain` uses `RokuLazyRow` / `RokuLazyColumn` via `project(":roku-focus-list")`. Proves commonMain consumption compiles for android + desktop + iOS. |
| `verification/published-consumer/` | **Standalone** Gradle build (own `settings.gradle.kts`, not in root settings). Resolves `io.github.souravnoobcoder:roku-focus-list:2.3.0` from `mavenLocal()` in `commonMain`, and applies the `compose-tvos` settings plugin so the tvOS leg proves an Apple TV consumer resolves too. Run with `./gradlew -p verification/published-consumer verifyCommonMainConsumption`. Proves the published Gradle module metadata works. |

## Build

- AGP 9.2.1, Gradle 9.4.1, Kotlin 2.4.10, Compose Multiplatform 1.12.0
- Compose BOM 2026.08.00 in the demo app — pins androidx Compose to 1.12.0, which is exactly what CMP 1.12.0 resolves to on Android. Do not desync these.
- minSdk 24 (app) / 23 (library), compileSdk 37, jvmTarget 11. compileSdk 37 + AGP 9.1 are the minimum CMP 1.12.0's Android artifacts declare in aar-metadata; lower fails the manifest merge.
- **Targets: android, jvm("desktop"), iosArm64, iosSimulatorArm64, tvosArm64, tvosSimulatorArm64, wasmJs.** No Apple x86_64 (`iosX64`/`tvosX64`) — CMP 1.11+ ships none.
- **tvOS needs the `dev.sajidali.compose-tvos` settings plugin**, declared in a `plugins { }` block directly after `pluginManagement { }` in `settings.gradle.kts`. JetBrains publishes no tvOS Compose artifacts; the plugin redirects the official `org.jetbrains.compose.*` coordinates onto the `sajidalidev/compose-multiplatform-core` fork (`dev.sajidali.*`) at resolution time, for tvOS configurations only. Verified: the **published** metadata keeps the official coordinates, so consumers are not hard-wired to the fork. Do NOT enable `composeTvos { strictMode }` — false positives on iOS-only platform leaves and conflict-resolution losers.
- The fork publishes tvOS klibs for CMP **1.12.0 and 1.12.0-beta01 only**. Downgrading Compose Multiplatform breaks tvOS.
- `kotlin.native.enableKlibsCrossCompilation=true` in gradle.properties lets the Linux release runner emit Apple klibs. Klib cross-compilation is host-agnostic; only final binaries and cinterop/CocoaPods need macOS, and this library has neither.
- The wasmJs test tasks and `checkComposeUiTestConfigurationForWasmJs` are disabled in the library build: that check wants a Skiko runtime bundled via `binaries.executable()`, which a library should not declare, and these tests are pure `kotlin.test` state maths that never touch Skiko.
- Library depends only on CMP runtime/runtime-saveable/foundation/ui/animation (no Material)
- Demo app adds Coil 3 (`coil-compose` + `coil-network-okhttp`), Material3
- **The demo app must NOT apply `org.jetbrains.kotlin.android`** — AGP 9 has built-in Kotlin support and hard-errors if KGP's android plugin is applied. It picks up KGP 2.2.21 from the root buildscript classpath.
- Library module uses `com.android.kotlin.multiplatform.library` with the `kotlin { android { } }` block. `androidLibrary { }` is the deprecated alias.
- CMP 1.10 deprecates the `compose.foundation` / `compose.ui` shorthand accessors. Dependencies are declared as explicit `org.jetbrains.compose.*` coordinates in `gradle/libs.versions.toml` (`compose-mp-*` aliases).
- CMP 1.10 also deprecates `org.jetbrains.compose.ui.tooling.preview.Preview` in favour of `androidx.compose.ui.tooling.preview.Preview` from `org.jetbrains.compose.ui:ui-tooling-preview`.

## Publishing

- Group `io.github.souravnoobcoder`, artifact `roku-focus-list`, version `2.3.0`, published to Maven Central via the Sonatype Central Portal (`com.vanniktech.maven.publish`).
- **JitPack cannot serve this library.** Six KMP publications trip its multi-module handling: it re-groups everything under `com.github.owner.repo` and rewrites the metadata, after which a `commonMain` dependency fails on `Could not find roku-focus-list-iosarm64-<v>.jar`. Verified against the real 2.0.0 tag it built. Do not go back.
- KMP `maven-publish` creates 8 publications: `kotlinMultiplatform` (root, carries the commonMain metadata variant and redirects), `android`, `desktop`, `iosArm64`, `iosSimulatorArm64`, `tvosArm64`, `tvosSimulatorArm64`, `wasmJs`.
- Consumers only ever reference the root coordinate.
- `consumer-rules.pro` is published inside the AAR as `proguard.txt` via `optimization { consumerKeepRules.apply { publish = true; file(...) } }`.
- Signing is applied only when `signingInMemoryKey` is present, so `publishToMavenLocal` works without a GPG key. The release workflow refuses to upload unsigned.

## Known issues / future work

- `headerHeight` in `RokuColumnRowConfig` (state-based overload) must still be specified manually — only the DSL auto-measures
- Vertical `focusSlot` is hardcoded to 0 (top-aligned) — could be made configurable like horizontal
- `RokuLazyRow` standalone doesn't know `itemHeight`, so highlight overflow works on width only (height uses `fillMaxHeight`)
- An empty row still leaves the row spacing on either side of it, because `LazyColumn` allocates spacing around a zero-height item
- `RokuFocusEscape.start` / `.end` map to LEFT / RIGHT; nothing mirrors for RTL yet
- Public `data class`es (`RokuFocusConfig`, `RokuFocusEscape`, `RokuColumnRowConfig`) make the ABI hard to evolve; there is no binary-compatibility validator yet
- iOS klibs compile but the library has not been exercised on an iOS runtime (tvOS, by contrast, is verified on a simulator and on real Apple TV hardware)
- Accessibility was verified from the emitted node tree (`uiautomator dump` on an API 31 TV emulator), not end to end with TalkBack
- `wasmJs` compiles and is published, but has not been exercised on a real Tizen TV; `js` is still not declared
- Tizen 8.0 (2024 TVs, Chromium M108) cannot run the wasm build — WebAssembly GC shipped in Chromium 119
