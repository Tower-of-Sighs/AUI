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

The showcase page `apricityui/theme/ore/example.html` can be opened directly with `new ApricityScreen(...)` — its seven pages demonstrate every component — **look at it before reading the class list in this document**; it's more intuitive than reading tables.

## Design Tokens

All tunable parameters are `--ore-*` CSS variables on `.ore-theme`. Business pages should reference tokens instead of hard-coding colors, so they can be adjusted uniformly:

| Group | Tokens |
| --- | --- |
| Text | `--ore-ink` (primary text #f4f5f7), `--ore-ink-muted`, `--ore-ink-dark` |
| Surfaces | `--ore-canvas` (page background #202124), `--ore-surface`, `--ore-surface-deep`, `--ore-surface-soft`, `--ore-edge`, `--ore-edge-light`, `--ore-focus` |
| Action colors | `--ore-green` (primary action) + `-hover`/`-shadow`, `--ore-purple` (secondary) + same, `--ore-gold`, `--ore-red` (danger) + same, `--ore-blue` |
| Status colors | `--ore-success` / `--ore-warning` / `--ore-danger` / `--ore-info` |
| Semantic aliases | `--ore-color-foreground` / `--ore-color-primary`, `--ore-size-unit` (2px), `--ore-motion-fast` (100ms) |
| Gray scale | `--ore-gray-10..100` (#f4f6f9 → #1e1e1f, ten steps) |
| Hue scales | `--ore-green-30..70`, `--ore-red-10..80`, `--ore-blue-10..30`, `--ore-yellow-10/20`, `--ore-orange-20`, `--ore-purple-10`, `--ore-gold-vip` |
| Disabled set | `--ore-disabled-background` / `-border` / `-shadow` / `-foreground` |
| Overlays | `--ore-overlay` (0.7 black), `--ore-overlay-soft` (0.55 black) |
| Spacing | `--ore-space-1..5` = 4/8/16/24/32px |
| Font sizes | `--ore-font-sm/md/lg/xl` = 13/16/20/28px |

Each numbered variant component also has its own token set (e.g. `--ore-button-primary-2-background` / `-hover` / `-active` / `-shadow`, `--ore-switch-track-background`, `--ore-tooltip-background`, ...), named `--ore-<component>-<property>`; look them up in the `.ore-theme` block at the top of ore.css.

How to override (attach to the theme root or your own class):

```css
.custom-screen {
    --ore-green: #4b9f32;
    --ore-space-3: 18px;
}
```

**The public contract is only `.ore-theme`, `--ore-*`, and the component classes listed below**. The old editor-only `ore-edit.css` has been deprecated and deleted; `ore.css` is the single theme entry point.

## Component Class Cheat Sheet

**Layout**: `.container` (centered, max 1180px) / `.container-fluid`; `.grid` twelve columns + `.col-1..12` / `.col-full`; `.stack` (vertical 12px), `.cluster` (horizontal wrapping 10px), `.split` (justify-between).

**Navigation**: `.navbar` + `.navbar-brand` + `.navbar-nav` (maintain `.active` on the active item yourself); `.breadcrumb` (automatically adds `>` separators).

**Buttons**: `.button` defaults to the green primary action; variants `.button-primary/-secondary` (purple)/`tertiary` (light)/`-danger` (red)/`-normal` (vanilla gray)/`-small`/`-wide`. `:hover`/`:active`/`[disabled]` states are all covered, but disabling requires the real `disabled` attribute. Numbered variants: `-2` flat set (`.button-primary-2/-secondary-2/-danger-2/-purple-2`, 2px border + bottom inner shadow), `-3` unit-bevel set (`.button-primary-3/-secondary-3/-danger-3`, top highlight band + thick bottom shadow); `data-state="loading"` renders a stepped square spinner. `.icon-button` / `.icon-button-2` are 36px square icon buttons.

**Cards/panels**: `.card` / `.panel` are equivalent, split into `.card-header` / `-body` / `-footer`; top accent bars `.card-accent-green/-purple/-gold`. Variants: `.card-2` (katorlys bevel + offset ground shadow, with `.card-description`), `.panel-2` (mcui panel, with `.panel-subtitle`).

**Forms**: `.form-group` / `.form-label` / `.form-help` / `.form-input` / `.form-select` / `.form-textarea` / `.input-group` (input + button in a row); validation states `.is-valid` / `.is-invalid` (only change the border — write the error text yourself); wrap radio/checkbox with `.choice-list` + `.choice`. Numbered variants `.form-input-2` / `.form-select-2` / `.form-textarea-2` / `.form-help-2` (flat dark face, white focus ring, `[aria-invalid]` red border).

**Choice controls** (drawn in pure CSS, no image assets):

- The switch is a faithful port of katorlys' `ore-switch`: a `.switch` host wrapping `.switch-control` (56×30) with `.switch-status` (26×26 track) and `.switch-button` (30×30 thumb) inside. The on state is any of `.on`, `:checked`, `[checked]`, `[aria-checked="true"]`, or `[data-state="on"]` (the thumb flips sides via flex `order`); `variant="icons"` (or `.switch-icons`) draws the on/off glyphs on the track, `color="secondary|destructive|dungeons|legends|realms|gold"` recolors the checked track; disable with `.disabled` or `[disabled]`. `.switch-bounce-left/-right` are bounce-animation modifiers.
- `.checkbox` (20px) / `.checkbox-2` (24px outlined), `.radio` (dot) / `.radio-2` (diamond rotated 45°); checked via `.on` / `:checked` / `[aria-checked="true"]`; disable with `.disabled` or `[disabled]`.
- `.slider` (8px track + `.slider-process` + `.slider-thumb`, optional `.slider-segment` ticks) / `.slider-2` (12px segmented track); set progress and position yourself with inline `style="width:..%"` / `style="left:..%"`.

**Data display**:

- `.table-wrap` + `.table` — **the built-in table lays out as a four-column grid**; if your column count differs, override `grid-template-columns` on `tr` in your own CSS, keeping thead/tbody consistent;
- `.badge` + `.badge-success/-warning/-danger/-purple`; dot style `.badge-2` + `.badge-2-green/-blue/-yellow/-red`;
- Tags `.tag` + `.tag-primary/-informative/-notice/-warning/-realms` (optionally `.tag-outlined`); color-block style `.tag-2` + `.tag-2-green/-blue/-yellow/-red/-black`;
- `.alert` + `.alert-success/-warning/-danger/-info` (only a left color bar, no icon, no close button); banners `.banner` + `.banner-information/-important`;
- `.progress` > `.progress-bar` (set the width yourself via style), `.progress-purple` variant; variant `.progress-2` (dug-out track) + `.progress-2-danger` / `.progress-2-indeterminate` (animated stripes);
- `.list-group` > `.list-group-item` (`.active` is green); variant `.list-group-2` > `.list-group-2-item` (mcui beveled rows);
- Dividers `.ore-divider` / `.ore-divider-2` (inset pair line).

**Feedback components**:

- Tooltip `.tooltip` > `.tooltip-content`, revealed by `:hover` / `:focus-within` / `[data-state="open"]`, positions `.tooltip-bottom/-left/-right` (default top); blue variant `.tooltip-2`;
- Dropdown `.dropdown` > `.dropdown-label` + `.dropdown-options` > `.dropdown-option` (`.selected` shows a check), opened via `.open` / `[data-state="open"]` / `:focus-within` / `details.dropdown[open]`, the chevron flips on `[aria-expanded="true"]`; dark menu variant `.dropdown-2`;
- Toasts `.toast` (stack them in `.toast-area`; show a single toast with `.show` / `[data-state="open"]`) + `.toast-success/-warning/-danger/-info/-vip/-debug`; bordered style `.toast-2` + `.toast-2-secondary/-primary/-informative/-notice/-warning/-realms`;
- Loading `.loading-mask` (fullscreen overlay, dismissed with `.hidden` / `[data-state="closed"]`) > `.spinner` (`.spinner-small/-large`) + `.spinner-text` / `.loading-error-text`;
- Drawer `.drawer` + `.drawer-left/-right/-top/-bottom` (`.open` / `[data-state="open"]` slides it in, pair with `.drawer-overlay`) > `.drawer-header` (`.drawer-title` + `.drawer-close`) / `-body` / `-footer`.

**MC style**: `.inventory-grid` (nine 44px-cell columns) + `.slot`. **This is only a visual grid** — for real slots use the container system's `<slot>`, see the [Container doc](container).

**Tabs / Modal / Pagination**: `.tabs` + `.tab` (`.active` has a purple border), `.modal-backdrop.open` + `.modal` + `-header/-body/-footer`, `.pagination` + `.page-button`. Again **styling only**: switching panels, opening/closing modals, Escape/backdrop clicks, and page-number logic are all yours to write. For ready-made dialog behavior use the Java-side [DialogWindow](ui-library). Tab variants: `.tab-2` (dark bevel, `.active` / `[aria-selected="true"]` / `[data-state="active"]` turns green), `.tab-3` (bottom 2px indicator bar).

**Sidebar / Scrollbar**: `.sidebar` (238px left slide-in, `.open` reveals it, pair with `.sidebar-mask`) > `.sidebar-title` / `.sidebar-divider` / `.sidebar-item`; variant `.sidebar-2` (240px lighter surface, `.active` green text, with `.sidebar-2-mask` / `.sidebar-2-header` / `.sidebar-2-item`); `.sidebar-button` is the beveled sidebar button. Custom scrollbars `.scrollbar` (22px beveled thumb) / `.scrollbar-2` (18px thin translucent), built from `.scrollbar-track` + `.scrollbar-thumb` (same names for -2); set the thumb height with inline style.

**Utilities**: `.text-left/-center/-right`, `.text-success/-warning/-danger/-info/-muted`, `.font-sm/-lg/-display`, `.hidden`, `.invisible`, `.w-full`, `.m-0`, spacing `mt/mb/p-1..4` (4/8/16/24px).

**Responsive**: two built-in breakpoints at 900px and 560px (grid collapses, navbar goes vertical, buttons go full width, etc.). Check complex tables and fixed-width modals yourself in small windows.

## State Contract

New components accept two equivalent ways of expressing state — pick whichever fits your host environment:

- Attribute states: `:checked`, `[disabled]` / `[aria-disabled="true"]`, `[aria-pressed]`, `[aria-selected="true"]`, `[aria-expanded="true"]`, `[aria-invalid="true"]`, `[aria-checked="true"]`, `[data-state="open|closed|on|off|active|loading"]`, `details[open]`;
- Class states: `.on` / `.active` / `.show` / `.open` / `.disabled` / `.hidden`, for purely static markup or places where attributes are awkward in AUI.

The disabled state (`[disabled]`, `.disabled`, `[aria-disabled]`) has the highest priority and overrides `:hover` / `:active`.

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
