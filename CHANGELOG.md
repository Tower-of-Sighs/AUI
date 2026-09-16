# Changelog

## Unreleased

### Performance

- Removed a redundant shared `BufferSource` flush per painted item node. The item backend already ends on `BufferSource.endBatch()` (shared batches first, then fixed ones), so the flush the paint node performed beforehand could only repeat work without affecting paint order.
- Skip the whole decoration path when an item has nothing to draw (no durability bar, no cooldown, no count/overlay text and no registered item decorator). This drops a per-item `GuiGraphics` allocation and an unconditional shared-buffer flush for every plain item.
- Reuse the item element's private stack copy on the paint path instead of deep-copying the `ItemStack` (and, on 1.20.1, its NBT and capabilities) once per item per frame.
- Frame-timing HUD now reports shared-buffer flush counts (`sb`) and per-item draw timing (`item`), the two numbers needed to profile pages with many `<item>` nodes. The never-incremented `imm` counter was dropped.

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
