# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [2.3.0] - 2026-09-16

### Added

- **Velocity-scaled multi-step navigation for touchpad remotes.** `RokuFocusListState.moveBy(steps)` and `RokuColumnState.moveRowsBy(steps)` move the selection N items or rows as **one logical move**: one selection change, one `onItemSelected`, one highlight animation and one scroll, however many items it covers — the coalescing a consumer cannot build from outside by calling `moveNext()` in a loop. Moves clamp at the ends; `wrapAround` applies only when already parked on the edge being pushed, exactly like single steps; `moveBy(0)` is a no-op. `moveNext()` / `movePrevious()` are now implemented on top of the same core and behave identically to `moveBy(±1)`. `moveRowsBy` steps over rows with nothing to select the way UP/DOWN do.

- `rokuMoveBy` / `rokuMoveRowsBy`: edge-aware variants that apply `focusEscape` and `onBoundaryHit` **once per move**, never per step. A partly-consumable move (two items left, three asked for) consumes what it can and then applies the edge policy once; a closed edge clamps without escaping.

- **`RokuFocusGrid`.** A wall of equal-size cells, N columns wide, scrolling vertically, with one highlight overlay — for "all titles" screens, guides and settings grids. Cell width is derived from the viewport (padding and gaps subtracted, split across the columns), rows are `itemHeight` tall. LEFT/RIGHT move along the row and stop at its ends (with `wrapAround` they flow into the neighbouring row in reading order); UP/DOWN move by whole rows keeping the column, and a shorter last row hands out its last cell. **It floats by default** — the highlight walks the visible cells and the grid scrolls only when the selection would leave them — with `RokuFocusMode.Static` available on `rememberRokuGridState`. `RokuGridState` follows the rails' rules (derived selection from a raw requested index, raw window anchor row contained at write time, `Saver`), and offers `moveColumnsBy` / `moveRowsBy` / `moveBy` as coalesced multi-step moves; `rokuMoveColumnsBy` / `rokuMoveRowsBy(gridState, …)` are the edge-aware variants for touchpad input. Same key-repeat throttle and acceleration, same per-edge escape, same velocity-carrying scroll animation as the rails.

- **Every entry point can be driven by a touchpad, not only the state-based column.** `RokuColumnState.activeRowState` is the selected rail's state, published by the column on every pass; `RokuColumnState.moveItemsBy(steps)` and `rokuMoveItemsBy(columnState, config, steps)` move within it, so a host that only holds the column state — the `row { }` DSL never hands out its rows' states — wires horizontal swipes the same way for either `RokuLazyColumn` overload. The DSL `RokuLazyRow` gains an optional hoisted `state` (appended before `content`, default null, so nothing changes for existing callers) for the same reason, keeping its auto-measured item width.

- **`RokuTouchpad`: touchpad remotes handled by the library.** Provide one through `LocalRokuTouchpad` and every `RokuLazyRow`, `RokuLazyColumn` and `RokuFocusGrid` follows the thumb the way the native tvOS focus engine does, with no per-screen code: each 0.65 of an item pitch of travel moves one item (a row pitch up or down), coalesced when travel arrives faster than one item per report; a smooth velocity gain (×1 up to 2,500 pt/s, ×2 from 12,000) lets a fast thumb cover more ground; nothing moves after lift-off; and travel short of a step is the focus-movement hint: a soft light slides across the focused card toward the thumb and brightens with pull (blended onto the card's own pixels, so any shape keeps its corners), while the card and its highlight give a small wiggle (4 dp, 2° tilt, 1 % lift, the highlight leaning further than the card for depth), following the thumb directly while it is down and springing back with a bounce when it lifts. At a row end the lean pins at full pull so pushing further is dropped and one step back moves back. Moves go through the `rokuMoveBy` family, so `onItemSelected`, `wrapAround` and `focusEscape` behave identically for a swipe and a key, and a `customRow` receives each step as a `RokuNavKey`. `RokuTouchpadConfig` holds the pacing and hint tuning. On tvOS, `RokuTouchpad.attachSiriRemote(view)` (a new `tvosMain` source set, the library's first platform code) installs the `UIPanGestureRecognizer` and suppresses the Compose tvOS fork's own one-key swipe; the rare key the fork still leaks is dropped by the components within 120 ms of touch-driven movement. Any other platform feeds the same object from its own input source. Without a touchpad in the composition none of this exists at runtime, so D-pad TVs run exactly the code they ran before.

- **Spatial row entry (`RokuFocusConfig.rowEntry`, default `RokuRowEntry.Spatial`).** When a vertical move — D-pad, `moveRowsBy` or a touchpad swipe — enters a floating row of a `RokuLazyColumn`, the highlight lands on the card physically under it (the card whose frame contains the highlight's centre, else the nearest), the way the tvOS focus engine does, instead of on the card that row selected last. Going down and back up returns to the card you were above. The entered row is never scrolled sideways to line anything up: the card is always one already inside its window. Static rows are unaffected (their remembered card is already under the slot), a `customRow` keeps its own selection, and `RokuFocusGrid` already kept the column. `RokuRowEntry.Remembered` restores the 2.x behaviour. `moveToRow` stays a plain programmatic jump.

- **Vertical speed, separately.** `RokuFocusConfig.verticalAnimationSpec` paces a vertical move in `RokuLazyColumn` and `RokuFocusGrid` — the highlight's Y and the content scroll between rows on one curve, so they travel together — and `verticalKeyRepeatDelayMs` spaces held UP/DOWN presses before acceleration. Both null by default, which keeps the 2.x pacing; horizontal moves are untouched.

- Key-repeat arbitration: a multi-step move resets the acceleration streak, so a swipe landing mid-D-pad-repeat cannot compound with repeat acceleration into a runaway scroll. The D-pad path is unchanged and still accelerates.

- `sample-tvos/`: a runnable Apple TV sample with the Xcode project to host it, showing the library in all four layouts (the `row { }` DSL column, the state-based column, a standalone DSL `RokuLazyRow` with a hoisted state, and `RokuFocusGrid`), each in both focus modes — Play/Pause on the remote steps through the eight scenes — with the one-line `RokuTouchpad` wiring and nothing gesture-related in the screens themselves. An on-screen readout shows the last selection and the frame timing the move produced against the panel's real refresh rate.

- 84 new tests: `RokuGridStateTest` (shape, row-end walls, reading-order flow with `wrapAround`, column-keeping row moves into a shorter last row, floating containment in one hop, static parking, shrink-and-regrow, column changes, key-repeat reset, saver), floating multi-step containment for rails and columns, a far vertical jump, `RokuMoveByTest` (bounds, wrap-around, single-step parity, the coalescing assertion by *counting* `onItemSelected`, edge policy once, partly-consumable moves, the key-repeat arbiter on both paths, and `moveItemsBy` / `rokuMoveItemsBy` through the column's active row) and `RokuTouchpadTest` (a brush leans without moving, one step moves one and leaves the remainder as lean, coalescing, no coast after lift-off, screen direction on both axes, axis lock, velocity gain, edge pin and one-step reversal, a clean new contact, host units via `pxPerUnit`, per-axis step fractions, a missing `panBegan`, no bound component, the key-leak guard window, and the gain curve), `RokuRowEntryTest` (the slot maths, no sideways scroll for every slot and for a short row, the entry hook's timing and once-per-move behaviour, wrap-around, the programmatic jump, the default) and the vertical key-repeat delay in `RokuKeyRepeatTrackerTest`.

### Changed

- **Scroll animations carry their velocity across retargets.** `animateScrollToItem` starts every call from rest, so a move that interrupted an in-flight scroll — a key repeat, or the run of single steps a touchpad swipe produces — made the content stop dead and ease in again, a visible pulse per item. Both renderers now drive their lazy list through one spring per list that hands its velocity to the next target, so chained moves read as one continuous scroll. The spring is the same `spring()` `animateScrollToItem` uses, so a lone D-pad step is timed exactly as before; far jumps and not-yet-measured lists still go through `animateScrollToItem`. Everything remains animated.

- README: a "Touchpad remotes" section covering `RokuTouchpad`, its configuration and the moves underneath, and two facts about the Compose tvOS fork: it already synthesises one D-pad key per touchpad swipe at lift-off (which `attachSiriRemote` suppresses; a host recogniser of your own must keep `cancelsTouchesInView = true`), and its published build lays a real Apple TV HD out at density 1.0.

## [2.2.0] - 2026-09-14

### Added

- **Apple TV (`tvosArm64`, `tvosSimulatorArm64`).** The library compiles for tvOS with no source changes at all, and the full shared test suite runs on a tvOS simulator in CI alongside desktop. Fixed focus, per-row focus memory, key-repeat throttling and the end-of-row highlight walk were verified on an Apple TV 4K simulator and on real Apple TV HD hardware. JetBrains publishes no tvOS artifacts for Compose Multiplatform, so a consumer building for Apple TV applies the `dev.sajidali.compose-tvos` settings plugin, which maps the official coordinates onto the `sajidalidev/compose-multiplatform-core` fork's tvOS builds. This library's own published metadata stays on `org.jetbrains.compose.*`, so depending on it never forces the fork on anyone — the substitution happens in the consumer's build and disappears the day JetBrains ships tvOS themselves.

- **Web / Samsung Tizen TV (`wasmJs`).** A Tizen TV app is a web app, so Tizen support is the `wasmJs` target with nothing Tizen-specific in the library. Compose Multiplatform's web target compiles to WebAssembly GC, so Tizen 9.0 (2025 TVs, Chromium M120) and 10.0 (2026 TVs, M130) can run it and Tizen 8.0 (2024 TVs, M108) cannot; consumers should set `required_version="9.0"` in their Tizen `config.xml`. The README carries the compatibility table.

- README: the two traps that cost the most debugging time when consuming this library — `Modifier.clickable` on item content silently breaking focus tracking (it adds a second focus target; use `pointerInput`/`detectTapGestures` instead), and `LocalDensity` differing across TV hardware (a real Apple TV HD reports 1.0 for a 1080p screen, laying the app out on a 1920×1080 dp canvas at roughly half the intended size and about double the per-frame work).

### Changed

- Toolchain: Kotlin 2.2.21 → 2.4.10, Compose Multiplatform 1.10.3 → 1.12.0, AGP 9.0.1 → 9.2.1, Gradle 9.1.0 → 9.4.1, and the demo app's Compose BOM 2026.03.00 → 2026.08.00 (which pins Jetpack Compose 1.12.0, exactly what Compose Multiplatform 1.12.0 resolves to on Android — these must not desync). The bump is not cosmetic: the fork publishes tvOS klibs for Compose Multiplatform 1.12.0 only, and nothing for 1.10.3.

- CI runs the tvOS simulator test suite and the wasm compile on every PR, so multi-target claims are carried by CI rather than by local runs.

### BREAKING

- **`iosX64` is gone.** Compose Multiplatform 1.11+ ships no Apple x86_64 artifacts at all, so the target has nothing to link against. Apple silicon Macs are unaffected — they use `iosSimulatorArm64`. Only a build targeting an Intel-Mac simulator is affected, and it cannot be fixed by pinning an older version of this library alone.

- **Android consumers need AGP 9.1+ and compileSdk 37.** Compose Multiplatform 1.12.0's Android artifacts declare that minimum in their aar-metadata, so an older AGP or compileSdk fails the manifest merge rather than degrading gracefully.

## [2.1.0] - 2026-08-20

### Changed

- `row(...)`'s `headerHeight` default changed from `0.dp` to auto-measure. A 2.0 caller passing a `header` without `headerHeight` used to get a highlight Y computed against a zero-height header (visibly misaligned); the header is now measured and the highlight lands below it. Callers that passed an explicit `headerHeight` are unaffected.

- A key press no longer does O(rows) work in the column: all per-row pixel geometry is precomputed into arrays inside a `derivedStateOf` whose only observable inputs are the rows' item counts, and the per-rail `visibleCount` sync is keyed on the rows and viewport. Navigation now reads cached arrays and allocates nothing — previously every press rebuilt and value-compared a metrics list across all rows (100 allocations per press on a 100-row screen).

- A D-pad move now recomposes only the rows and items whose focus actually changed, instead of every visible row and card wrapper. The column's row content is one remembered lambda (the row list is unstable, so the compiler was recreating it every pass and invalidating everything under it), selection is read per item through `derivedStateOf`, and the row scroll follows `windowStart` through a `snapshotFlow` instead of a composition read. Measured on a TV emulator: five horizontal presses went from 78 item-wrapper and 15 row recompositions to 14 and 0. Cards with unstable parameters — which cannot self-skip — stop re-running wholesale on every press.

- Releases publish to Maven Central straight from the workflow, with no manual approval in the Portal. The workflow then waits for the artifact to appear on `repo1.maven.org` and resolves the coordinate back out of Central through the standalone consumer build, because a successful publish and a usable artifact are not the same thing.

- Publishing moved from JitPack to Maven Central, and the group id from `com.github.souravnoobcoder` to `io.github.souravnoobcoder`. JitPack puts a Kotlin Multiplatform build into multi-module mode, which re-groups every publication under `com.github.owner.repo` and rewrites the Gradle metadata; `commonMain` resolution then fails looking for artifacts that were never published. Verified against the 2.0.0 tag JitPack actually built.

### Added

- Auto-measured row sizes in the `RokuLazyColumn` DSL: `row(...)` no longer requires `itemWidth` / `itemHeight` / `headerHeight`. Omitted dimensions are measured by composing the first item (and the header) invisibly once, the way the DSL `RokuLazyRow` already measures its item width, so any composable fits without size bookkeeping. A row waiting on measurement behaves exactly like an empty row — skipped, zero height — and appears through the same machinery that handles late-arriving rows. Explicit sizes still win and skip the measuring pass; the state-based overload stays fully explicit.

- `RokuFocusMode` — per-axis choice between `Static` (Roku-style: the highlight parks at a fixed slot and the content scrolls behind it — the previous behaviour, still the default) and `Floating` (leanback-style: the highlight walks the visible window and the list scrolls only when the selection would leave it, by the minimum needed to keep it visible). Horizontal mode is `focusMode` on `RokuFocusListState` / `rememberRokuFocusListState`, on the DSL `RokuLazyRow` and on `row(...)`; vertical mode is `verticalFocusMode` on both `RokuLazyColumn` overloads. The two axes are independent, so any combination works. `focusSlot` only applies in `Static`. The floating window anchors join both `Saver`s, so a restored screen comes back with the window where it was, not just the selection.

- Repository scaffolding: CHANGELOG, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, issue/PR templates, CI workflow, funding config.

### Fixed

- Enter / D-pad center on a column with nothing selectable (every row empty or still loading) no longer reports a phantom `onItemClicked` for a row that cannot be interacted with. The press is left unconsumed, mirroring how the directional keys fall through to focus escape at the same dead end.

## [2.0.1] - 2026-08-19

### Changed

- Android `minSdk` lowered from 24 to 23. The library is pure Compose Multiplatform — no `androidMain` source set, no `android.*` API usage — so the 24 floor was a build setting, not a real requirement, and it forced consumers shipping minSdk 23 into `tools:overrideLibrary`.

### Fixed

- The selected item is now drawn above its siblings (`zIndex`) inside `RokuLazyRow` / `RokuLazyColumn` rows. Consumers that scale the selected card or decorate it beyond its bounds saw the next item's leading edge drawn over it, because LazyRow paints items in placement order.

## [2.0.0] - 2026-08-18

### Added

- `RokuColumnState` + `rememberRokuColumnState` — the column's selected row is now public, readable and writable, and both `RokuLazyColumn` variants take it as a `state` parameter defaulting to an internally remembered one.
- State restoration: `RokuFocusListState.Saver` and `RokuColumnState.Saver`, with both `remember*` factories switching to `rememberSaveable`. Selection now survives configuration changes, process recreation and navigation back-stack restoration.
- Pending selection targets: an index that is not valid yet is remembered and applied once the list grows to include it, instead of being clamped away. Applies to `RokuColumnState.selectedRowIndex` and to `RokuFocusListState` when `updateItemCount` grows a row.
- `RokuFocusEscape(start, end, up, down)` — per-edge control over whether focus may leave the list, replacing the all-or-nothing `allowFocusEscape`. Presets: `All`, `None`, `Horizontal`, `Vertical`.
- `RokuLazyColumnScope.customRow` — a row the column does not lay out (hero pager, chip strip, grid). The column keeps UP/DOWN, vertical scrolling and highlight Y; LEFT/RIGHT/ENTER are delegated through `onKeyEvent: (RokuNavKey) -> Boolean`, and the global highlight is suppressible per row.
- `key: Any?` on `row(...)` and `customRow(...)`, following `LazyColumn`'s contract, so per-row selection state follows row identity across insert, remove, filter and reorder. Appended after the 1.x parameters so positional calls keep their meaning.
- `initialIndex` on the DSL `RokuLazyRow` and on the column DSL's `row { }`.
- `RokuHighlightScope` — the `focusHighlight` lambda gained a receiver carrying `rowIndex` and `itemIndex`, so one lambda can shape the highlight per row. `isFocused` stays its parameter, so 1.x highlight lambdas are unaffected.
- Focus control and observability: `requestFocus()` and observable `hasFocus` on both state objects, and `onFocusEnter` / `onFocusExit` on `RokuLazyColumn`.
- Accessibility semantics: `CollectionInfo` on the container, a node per item carrying `CollectionItemInfo` and `selected`, and an opt-in `contentDescription` for the selected item surfaced on the container in a polite live region. Fully mapped on Android; partial on iOS and absent on desktop in Compose Multiplatform 1.10.3.
- Item keys and content descriptions in the `items { }` DSL and on the state-based overloads.
- `LICENSE` — the Apache 2.0 text the project has always claimed.

### Changed

- Maven group id restored to `com.github.souravnoobcoder`.
- Rows with zero items are never selectable: UP/DOWN steps over them, the highlight never parks on them, and they render nothing and occupy no height.
- The column re-scrolls when its row set changes, so a selection made while the list was still loading no longer leaves the column at a clamped scroll offset.
- `Modifier.rokuKeyHandler` no longer uses `Modifier.composed`; key-repeat bookkeeping moved onto the state objects.
- README rewritten for 2.0, with a 1.x to 2.0 migration table.

### Deprecated

- `RokuFocusConfig(..., allowFocusEscape: Boolean)` and the `RokuFocusConfig.allowFocusEscape` read path, both mapping to all edges.

### Removed

- `RokuLazyColumn(initialRowIndex = ...)` — use `rememberRokuColumnState(initialRowIndex = ...)`.

### Fixed

- The standalone `RokuLazyRow` consumed LEFT/RIGHT at its edges unconditionally, so focus could never leave it sideways. Edge presses now follow `RokuFocusConfig.focusEscape`.

### BREAKING

- `RokuFocusConfig.allowFocusEscape` is replaced by `focusEscape`; `copy(allowFocusEscape = ...)` has no equivalent.
- `RokuLazyColumn`'s `initialRowIndex` parameter is gone — pass `state = rememberRokuColumnState(initialRowIndex = ...)`.
- `focusHighlight` gained a `RokuHighlightScope` receiver. Lambdas are unaffected; a highlight stored in a `val` of the old function type must be retyped.

## [1.0.0] - 2024-04-24

### Added

- Initial public release.
- `RokuLazyRow` — horizontal fixed-focus D-pad row with DSL and state variants.
- `RokuLazyColumn` — vertical + horizontal OTT grid layout with mixed row sizes, row headers, and a single animated highlight overlay.
- `rememberRokuFocusListState` — remembered state with `selectedIndex`, `scrollTo`, `moveNext` / `movePrevious`.
- `RokuFocusConfig` — configurable animation spec, key-repeat throttling, acceleration, wrap-around, haptic feedback, focus escape.
- `RokuAnimationSpec` presets: Default, Fast, Smooth.
- `DefaultFocusHighlight` — `BoxScope` extension drawing a white rounded border outside card bounds via `graphicsLayer`.
- Configurable `focusSlot` — choose which visible position the highlight sits at.
- Scroll overflow correction — highlight shifts to track actual item position at list edges.
- Key-repeat acceleration — speeds up after N consecutive presses.
- Callbacks: `onItemSelected`, `onItemClicked`, `onFocusEnter`, `onFocusExit`.
- Demo app with 10 rows, 6 card types, 308 items.

[Unreleased]: https://github.com/souravnoobcoder/roku-focus-list/compare/2.3.0...HEAD
[2.3.0]: https://github.com/souravnoobcoder/roku-focus-list/compare/2.2.0...2.3.0
[2.0.0]: https://github.com/souravnoobcoder/roku-focus-list/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/souravnoobcoder/roku-focus-list/releases/tag/v1.0.0
