# Changelog

## Unreleased

### Performance

- Removed a redundant shared `BufferSource` flush per painted item node. The item backend already ends on `BufferSource.endBatch()` (shared batches first, then fixed ones), so the flush the paint node performed beforehand could only repeat work without affecting paint order.
- Skip the whole decoration path when an item has nothing to draw (no durability bar, no cooldown, no count/overlay text and no registered item decorator). This drops a per-item `GuiGraphics` allocation and an unconditional shared-buffer flush for every plain item.
- Reuse the item element's private stack copy on the paint path instead of deep-copying the `ItemStack` (and, on 1.20.1, its NBT and capabilities) once per item per frame.
- Frame-timing HUD now reports shared-buffer flush counts (`sb`) and per-item draw timing (`item`), the two numbers needed to profile pages with many `<item>` nodes. The never-incremented `imm` counter was dropped.
- Transform-only style changes no longer force a full-document relayout. A `:hover` rule that only touches `transform`/`transform-origin` marks `COMMIT_LAYOUT` without `RELAYOUT`, but the geometry commit treated both flags alike and rebuilt every element's rect (re-running layout measurement for the whole document). Such batches now take the targeted `commitTransforms` path that motion already used, so only the affected subtrees are refreshed.
- Text measurement no longer allocates a cache key and a boxed `Double` per line-width lookup. The `LinkedHashMap<LineMeasureKey, Double>` (synchronized, and with the key record rebuilt on every probe) is replaced by a set-associative table with zero-allocation hits, the per-instance memo maps are gone, and single-code-point strings are reused. Measured on a 120-node text page: 7.45 MB/frame → 0.14 MB/frame, with bit-identical layout output.
- Dropped per-element work that was computed and thrown away: the wrap branch of `Flex.computeContentSize`/`getOrComputeLayout` no longer builds participants it never reads, `getScrollbarPseudoStyles` no longer allocates a capturing lambda on every call, the gradient paths in `Graph` no longer allocate a capturing `Runnable`, and `FontDrawer` no longer reads the environment on every drawn run.
- Frame-timing HUD additionally reports full layout commits (`ly`) and targeted transform commits (`tf`), making it possible to tell a perpetual relayout apart from a transform-only change.

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
