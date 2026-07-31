# Ore Editor manual visual acceptance matrix

This checklist is the remaining runtime evidence required by roadmap Phase 12.
It complements automated tests; it does not replace them.

## Capture setup

Record these values beside every capture:

- build/revision and resource-pack revision;
- browser viewport or Minecraft window raster size and GUI scale;
- language (`zh_cn` for normal-flow acceptance, then `en_us` for text-fit checks);
- font mode and whether all Ore font resources have loaded; and
- the source HTML file selected from DevTools.

Use the same canvas content in Chrome and Minecraft. Store browser images under
`run/ore-comparison/browser-editor/` and Minecraft images under
`run/ore-comparison/mc-editor/`, with the same base filename.

## Required state matrix

| ID | State | Required evidence |
| --- | --- | --- |
| E01 | DevTools entry | Palette icon opens the global HTML picker; non-HTML and resource-pack entries are absent. |
| E02 | File open | Select a writable HTML file, verify the document name, hierarchy and source attributes appear in Ore. |
| E03 | ADD | Capture container and component palettes; drag a component into the root and a nested container. |
| E04 | Flex | Capture row, column, reverse, wrap and gap insertion overlays with ordered children. |
| E05 | Container inspector | Capture direction/wrap controls, alignment previews, dimensions, spacing and overflow fields. |
| E06 | Component inspector | Capture content, Flex-item fields, state selector, color/alpha and box-model fields. |
| E07 | Absolute | Capture Flex-to-absolute, direct drag, resize, dual-edge constraints and return-to-Flex. |
| E08 | Theme | Capture one token override, token reset, group reset and global reset; editor shell must stay readable. |
| E09 | History | Capture undo/redo after content, drag and theme edits; undoing to the saved revision clears the dirty marker. |
| E10 | Save/export | Save selected HTML and reopen it; verify head/body metadata, classes, data attributes and scripts are retained. |
| E11 | Dialogs | Capture unsaved-change confirmation and save/export feedback. |
| E12 | English | Reopen in `en_us`; verify toolbar, tabs, long inspector labels and tooltips do not clip or display keys. |

## Comparison criteria

- Overlays are outside normal layout: selection, axis, gap and resize layers do
  not change element box sizes, wrapping, scrolling or hit targets.
- Buttons, inputs, selects, panels and dialogs retain Ore's border, inset-depth
  and state appearance; no editor-local imitation skin is visible.
- In Chrome and Minecraft, compare borders, inset shadows, active displacement,
  font baseline, line height, ellipsis, scrollbar and tooltip placement.
- Any visible divergence requires a minimal HTML reproduction and a framework
  regression test before an editor-specific workaround is considered.

## Sign-off

Do not mark the roadmap visual gate complete until every E01–E12 row has a
paired Chrome/Minecraft capture or a linked, independently reproducible
framework issue explaining the difference.
