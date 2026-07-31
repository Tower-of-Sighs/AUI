# Ore browser / Minecraft visual baseline

This is the reproducibility index for the pre-editor Ore theme baseline. It is
not evidence that the Ore Editor interaction matrix itself has been manually
accepted.

## Captures

| Renderer | Directory | Pages | Native size |
| --- | --- | ---: | --- |
| Chrome | `run/ore-comparison/browser` | 15 | 1463 x 843 |
| Minecraft (original) | `run/ore-comparison/mc-after` | 15 | 2560 x 1476 |
| Minecraft (comparison scale) | `run/ore-comparison/mc-after-scaled` | 15 | 1463 x 843 |
| Side-by-side sheets | `run/ore-comparison/pairs-after` | 15 | 2926 x 843 |

The capture set includes `page-01.png` through `page-15.png` plus
`modal-open.png`. The scaled Minecraft images exist solely to make each
browser/Minecraft pair directly inspectable at the same pixel dimensions; they
must not replace the original Minecraft images for font or raster-quality
inspection.

Additional focused captures are retained at `run/ore-comparison/` for Ore brand
hover and scroll clipping. The `*-after*` variants are the post-framework-fix
references used by the Phase 0 baseline.

## Capture conditions

- Browser viewport: 1463 x 843 CSS pixels.
- Minecraft: maximized window capture at 2560 x 1476 raster pixels, with the
  corresponding comparison-scaled copy noted above.
- The screenshots are the frozen Ore-theme reference described by
  `ore-editor-phase-0-baseline.md`; they predate the editor-specific manual
  acceptance pass.

## Scope boundary

The image set proves that the existing Ore example can be compared across the
two renderers. It does not prove the following editor states, which must be
captured in both renderers before Ore Editor 1.0 can be signed off:

- ADD palette, container INSPECT, component INSPECT, and THEME;
- palette drag/reorder/cross-container drop and Flex overlays;
- absolute move/resize and Flex restoration; and
- save/export and unsaved-change dialogs in Chinese and English.

Until that matrix exists, roadmap Phase 12 and the visual-comparison item in
the 1.0 completion definition remain open.

The exact state-by-state capture procedure is in
`doc/ore-editor-manual-acceptance.md`.
