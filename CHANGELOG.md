# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Publishing moved from JitPack to Maven Central, and the group id from `com.github.souravnoobcoder` to `io.github.souravnoobcoder`. JitPack puts a Kotlin Multiplatform build into multi-module mode, which re-groups every publication under `com.github.owner.repo` and rewrites the Gradle metadata; `commonMain` resolution then fails looking for artifacts that were never published. Verified against the 2.0.0 tag JitPack actually built.

### Added

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
