# Changelog

## 1.2.4 - 2026-08-31

### Added

- Added CSS `mask` support, multiple mask layers, mask compositing, and related clipping behavior.
- Added `contrast()`, `saturate()`, and `sepia()` filters.
- Added `mix-blend-mode`, `isolation`, HDR colors, and linear-light color handling.
- Expanded the Canvas 2D API with common drawing methods, conic gradients, path clipping, `ImageData`, and hit testing.
- Added slot item filters and composable `FilterUtil` rules across supported loaders.
- Expanded the Ore theme, documented the theme token convention, and unified the built-in `ore.css`.
- Added `word-break` support and improved text selection, context-menu selection, and word/paragraph dragging.

### Performance

- Improved canvas dirty-region tracking and texture uploads.
- Changed `mousemove` dispatch to a fixed 60 Hz schedule.
- Optimized page startup, transition warm-up, layout commits, font loading, hit testing, and caches.
- Avoided text-layout cache invalidation on every frame of hover color animations.
- Resolved animated inherited styles in depth order and changed `Size.getScaleHeight` to iterative ancestor resolution to avoid recursive calls and repeated work.

### Fixed

- Fixed a one-frame black flash when mask clipping is initialized.
- Fixed inherited animated text colors not being applied consistently while preserving text layout caches.
- Fixed several mask, stacking, blend-mode, isolation, selection, and Ore theme edge cases.
