# Built-in Ore Theme

Ore is the framework's built-in pure-CSS theme: MC-style pixel borders, dark stone surfaces, and green/purple/gold accents, suited for action-oriented UIs like settings pages, editors, and container screens. **It only handles styling** — clicking, toggling, submitting, and state management are your own JS/Java's job.

## Getting Started

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
    <button class="button button-primary">Apply</button>
</body>
```

- The leading `/` in href is the AUI logical resource root, not the disk root;
- All rules are scoped under `.ore-theme` and do not affect UI outside the root node;
- It ships two local fonts (`OreRegular` for body text, `OreDisplay` for headings/controls), requires no network, and falls back to system fonts if loading fails;
- It provides a dark canvas background, 16px font size, and box-sizing propagation by default, but it won't fill the screen for you — add `min-height:100vh` yourself for fullscreen;
- Licensed under MPL-2.0; keep the `license.txt` in the theme directory when redistributing with your mod.

The showcase page `apricityui/theme/ore/example.html` can be opened directly with `new ApricityScreen(...)` — its six pages demonstrate every component — **look at it before reading the class list in this document**; it's more intuitive than reading tables.

## Design Tokens

All tunable parameters are `--ore-*` CSS variables on `.ore-theme`. Business pages should reference tokens instead of hard-coding colors, so they can be adjusted uniformly in the Ore editor:

| Group | Tokens |
| --- | --- |
| Text | `--ore-ink` (primary text #f4f5f7), `--ore-ink-muted`, `--ore-ink-dark` |
| Surfaces | `--ore-canvas` (page background #202124), `--ore-surface`, `--ore-surface-deep`, `--ore-surface-soft`, `--ore-edge`, `--ore-edge-light`, `--ore-focus` |
| Action colors | `--ore-green` (primary action) + `-hover`/`-shadow`, `--ore-purple` (secondary) + same, `--ore-gold`, `--ore-red` (danger) + same, `--ore-blue` |
| Status colors | `--ore-success` / `--ore-warning` / `--ore-danger` / `--ore-info` |
| Spacing | `--ore-space-1..5` = 4/8/16/24/32px |
| Font sizes | `--ore-font-sm/md/lg/xl` = 13/16/20/28px |

How to override (attach to the theme root or your own class):

```css
.custom-screen {
    --ore-green: #4b9f32;
    --ore-space-3: 18px;
}
```

**The public contract is only `.ore-theme`, `--ore-*`, and the component classes listed below**. The `--ore-edit-*` variables in `ore-edit.css` are generation details — don't depend on them.

## Component Class Cheat Sheet

**Layout**: `.container` (centered, max 1180px) / `.container-fluid`; `.grid` twelve columns + `.col-1..12` / `.col-full`; `.stack` (vertical 12px), `.cluster` (horizontal wrapping 10px), `.split` (justify-between).

**Navigation**: `.navbar` + `.navbar-brand` + `.navbar-nav` (maintain `.active` on the active item yourself); `.breadcrumb` (automatically adds `>` separators).

**Buttons**: `.button` defaults to the green primary action; variants `.button-primary/-secondary` (purple)/`tertiary` (light)/`-danger` (red)/`-normal` (vanilla gray)/`-small`/`-wide`. `:hover`/`:active`/`[disabled]` states are all covered, but disabling requires the real `disabled` attribute.

**Cards/panels**: `.card` / `.panel` are equivalent, split into `.card-header` / `-body` / `-footer`; top accent bars `.card-accent-green/-purple/-gold`.

**Forms**: `.form-group` / `.form-label` / `.form-help` / `.form-input` / `.form-select` / `.form-textarea` / `.input-group` (input + button in a row); validation states `.is-valid` / `.is-invalid` (only change the border — write the error text yourself); wrap radio/checkbox with `.choice-list` + `.choice`.

**Data display**:

- `.table-wrap` + `.table` — **the built-in table lays out as a four-column grid**; if your column count differs, override `grid-template-columns` on `tr` in your own CSS, keeping thead/tbody consistent;
- `.badge` + `.badge-success/-warning/-danger/-purple`;
- `.alert` + `.alert-success/-warning/-danger/-info` (only a left color bar, no icon, no close button);
- `.progress` > `.progress-bar` (set the width yourself via style), `.progress-purple` variant;
- `.list-group` > `.list-group-item` (`.active` is green).

**MC style**: `.inventory-grid` (nine 44px-cell columns) + `.slot`. **This is only a visual grid** — for real slots use the container system's `<slot>`, see the [Container doc](container).

**Tabs / Modal / Pagination**: `.tabs` + `.tab` (`.active` has a purple border), `.modal-backdrop.open` + `.modal` + `-header/-body/-footer`, `.pagination` + `.page-button`. Again **styling only**: switching panels, opening/closing modals, Escape/backdrop clicks, and page-number logic are all yours to write. For ready-made dialog behavior use the Java-side [DialogWindow](ui-library).

**Utilities**: `.text-left/-center/-right`, `.text-success/-warning/-danger/-info/-muted`, `.font-sm/-lg/-display`, `.hidden`, `.invisible`, `.w-full`, `.m-0`, spacing `mt/mb/p-1..4` (4/8/16/24px).

**Responsive**: two built-in breakpoints at 900px and 560px (grid collapses, navbar goes vertical, buttons go full width, etc.). Check complex tables and fixed-width modals yourself in small windows.

## Ore Editor

`ore-edit.css` is a tokenized theme variant (generated from ore.css) meant for use with the visual editor. **Normal pages just use ore.css** — don't include both.

Java-side entry point `OreEditor`: `open()` / `toggle()` / `close()` / `isOpen()` / `getDocument()` / `openHtml(Path)` (only accepts local files; resource-pack HTML cannot be edited and saved directly).

The editor's THEME panel organizes 35 editable tokens into five groups (Typography/Surfaces/Actions/Feedback/Spacing), the color picker has an Alpha slider, and invalid values are marked `is-invalid` and not applied; it supports resetting a single token / a group / everything, all tracked in the Undo/Redo history.

**Saving and exporting**:

- Projects are stored at `<game directory>/apricity/ore-projects/untitled.ore.json` (theme overrides + document metadata; editor decoration nodes are not written);
- Exported HTML goes to the same directory: token overrides are written into the body's inline style, and a `ore-edit.css` reference is automatically added to the head; the export is **a normal AUI page with no editor dependency**;
- `openHtml` strips `<script>` on import (they are not executed as editor code), and `--ore-*` properties in the body's inline style are read as theme overrides — if you want the importer to recognize your theme values, put them in the body inline style.

## Customization Tips

- Put business styles under the `.ore-theme` scope and keep using tokens;
- When overriding components, also cover `:hover`/`:active`/`[disabled]` — changing only the default background leads to inconsistent states;
- Load business CSS after ore.css so same-specificity rules override;
- Use valid CSS values when overriding tokens (any CSS color expression for colors, units required for spacing).

## FAQ

**No effect**: check three things — is the link path correct, does the top-level element have `class="ore-theme"`, and have resources been rescanned.

**Font doesn't look like MC**: the theme directory's `fonts/` resources failed to resolve — check the logs. If colors work but the font is wrong, it's almost certainly this.

**Button clicks do nothing / Modal won't close / Tabs don't switch**: expected behavior — Ore is pure CSS. Write the interactions yourself, or use the [built-in UI library](ui-library).

**Table columns misaligned**: the built-in table is a four-column grid — see the override notes above.

**Colors changed in the editor disappear after export**: don't manually delete the exported HTML's body inline style and ore-edit.css reference, and don't replace the `ore-theme` class.
