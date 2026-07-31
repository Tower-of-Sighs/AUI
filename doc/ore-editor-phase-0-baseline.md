# Ore Editor Phase 0 Baseline

Date: 2026-07-27

## Frozen visual baseline

The existing Ore browser/MC comparison is recorded in `doc/ore-browser-mc-comparison.md`.
It covers the unmodified `ore.css` example at a 1463 x 843 browser viewport and
maximized Minecraft window. The recorded maximum bottom-edge difference across 15
pages is 0.07 CSS px. The report also records the Ore fonts used by both renderers.

The editor must not alter `ore.css`. Its visual regression baseline is therefore
the existing Ore example plus the dedicated layout fixture below.

## Minimal editor layout fixture

`assets/apricityui/apricity/tests/ore-editor-layout-baseline.html` is a static,
script-free fixture for browser and Minecraft capture. It isolates:

- wrapped flex layout with `gap` and `order`;
- an absolute child in a relative containing block; and
- a drag ghost with `pointer-events: none`, outside normal layout.

Capture settings must be recorded with every future comparison: resource revision,
viewport size, GUI scale, locale, window size, and font-load state.

## Editable theme regression

`ore-edit.css` is generated from `ore.css` by `tools/generate-ore-edit.mjs`.
The generator preserves media-query breakpoints and replaces every non-media
color, alpha color, positive length, and negative length literal with an editable
custom property. `tools/check-ore-edit-tokens.mjs` rejects non-tokenized values.

`tools/compare-ore-edit-visuals.mjs` renders the same Ore example twice in
Chrome at 1463 x 843: once with `ore.css` and once with `ore-edit.css`. On
2026-07-27 its RMS pixel difference was `0`. `OreEditThemeTest` additionally
compares computed AUI styles for representative controls. The existing
browser/MC comparison remains applicable because the generated default output
is pixel-identical to the frozen browser baseline.

## Capability inventory

| Capability | Current evidence | Editor decision |
| --- | --- | --- |
| CSS custom properties | `CssCompatibilityTest` and `LayoutPositionTest` cover parsing, inheritance, and layout use. | Use canvas-scoped `--ore-*` variables; add nested fallback coverage before theme editing. |
| Flex wrap and gap | `LayoutPositionTest` covers wrap, row/column gaps, and cross-axis alignment. | Build insertion from final boxes, then add reverse and order matrix tests. |
| Absolute positioning | `LayoutPositionTest` covers containing blocks and four-sided offsets. | Keep overlays absolutely positioned and non-participating. Add padding/border and dual-edge tests in Phase 8. |
| Overflow and scroll | `ElementBindingTest` and `LayoutPositionTest` cover `overflow: auto` and `hidden`. | The canvas viewport may scroll; overlay coordinates must account for scroll. |
| Select | `SelectCompatibilityTest` and `ElementBindingTest` cover value, keyboard, popup, and disabled state. | Reuse the native AUI select element; do not create an editor-only replacement. |
| Cursor | Cursor resources exist, but no focused cursor-style compatibility test was found. | Do not make cursor appearance an editor acceptance gate until a framework test exists. |
| Drag events | Mouse and pointer compatibility events are dispatched by `MouseEvent`; no HTML drag-and-drop API was found. | Implement editor drag from AUI mouse/pointer events only. Never simulate system input. |

## Open framework risks before dependent phases

1. Flex `row-reverse`, `column-reverse`, `wrap-reverse`, and visual-to-model
   insertion mapping need dedicated tests before Phase 5 is accepted.
2. Theme editing needs nested custom-property fallback and invalidation coverage
   before Phase 9 is accepted.
3. Overlay and drop hit testing need scroll-offset and stacking-order coverage
   before Phase 4 is accepted.
