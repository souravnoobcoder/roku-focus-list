# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- A key press no longer does O(rows) work in the column: all per-row pixel geometry is precomputed into arrays inside a `derivedStateOf` whose only observable inputs are the rows' item counts, and the per-rail `visibleCount` sync is keyed on the rows and viewport. Navigation now reads cached arrays and allocates nothing — previously every press rebuilt and value-compared a metrics list across all rows (100 allocations per press on a 100-row screen).

- A D-pad move now recomposes only the rows and items whose focus actually changed, instead of every visible row and card wrapper. The column's row content is one remembered lambda (the row list is unstable, so the compiler was recreating it every pass and invalidating everything under it), selection is read per item through `derivedStateOf`, and the row scroll follows `windowStart` through a `snapshotFlow` instead of a composition read. Measured on a TV emulator: five horizontal presses went from 78 item-wrapper and 15 row recompositions to 14 and 0. Cards with unstable parameters — which cannot self-skip — stop re-running wholesale on every press.

- Releases publish to Maven Central straight from the workflow, with no manual approval in the Portal. The workflow then waits for the artifact to appear on `repo1.maven.org` and resolves the coordinate back out of Central through the standalone consumer build, because a successful publish and a usable artifact are not the same thing.

- Publishing moved from JitPack to Maven Central, and the group id from `com.github.souravnoobcoder` to `io.github.souravnoobcoder`. JitPack puts a Kotlin Multiplatform build into multi-module mode, which re-groups every publication under `com.github.owner.repo` and rewrites the Gradle metadata; `commonMain` resolution then fails looking for artifacts that were never published. Verified against the 2.0.0 tag JitPack actually built.

### Added

- Auto-measured row sizes in the `RokuLazyColumn` DSL: `row(...)` no longer requires `itemWidth` / `itemHeight` / `headerHeight`. Omitted dimensions are measured by composing the first item (and the header) invisibly once, the way the DSL `RokuLazyRow` already measures its item width, so any composable fits without size bookkeeping. A row waiting on measurement behaves exactly like an empty row — skipped, zero height — and appears through the same machinery that handles late-arriving rows. Explicit sizes still win and skip the measuring pass; the state-based overload stays fully explicit.

- `RokuFocusMode` — per-axis choice between `Static` (Roku-style: the highlight parks at a fixed slot and the content scrolls behind it — the previous behaviour, still the default) and `Floating` (leanback-style: the highlight walks the visible window and the list scrolls only when the selection would leave it, by the minimum needed to keep it visible). Horizontal mode is `focusMode` on `RokuFocusListState` / `rememberRokuFocusListState`, on the DSL `RokuLazyRow` and on `row(...)`; vertical mode is `verticalFocusMode` on both `RokuLazyColumn` overloads. The two axes are independent, so any combination works. `focusSlot` only applies in `Static`. The floating window anchors join both `Saver`s, so a restored screen comes back with the window where it was, not just the selection.

- Repository scaffolding: CHANGELOG, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, issue/PR templates, CI workflow, funding config.

### Changed

### Deprecated

### Removed

### Fixed

### Security

### BREAKING

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

[Unreleased]: https://github.com/souravnoobcoder/roku-focus-list/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/souravnoobcoder/roku-focus-list/compare/v1.0.0...v2.0.0
[1.0.0]: https://github.com/souravnoobcoder/roku-focus-list/releases/tag/v1.0.0
