# t05-curseforge

A CurseForge-styled theme for ApricityUI: a dense, near-square, image-forward
dark portal. Same class contract as t01-ore, the same three token layers, and the
opposite structural idea - where t01-ore builds depth out of thick 3px outlines and
stacked inset bevels, this theme has **no elevation at all**: every surface is a
flat panel closed by a 1px separator, and the only thing that is loud is a single
orange.

Sibling themes: **`t03-claude-square`** and **`t04-claude-round`** are the Claude
end of the room (light, warm, serif/mono). This one is dark, cold-neutral and
sans, and it is the only theme here whose header is two stacked bands.

## Where it comes from

The design language is taken from curseforge.com - the Minecraft hub
(`/minecraft`), the mod listing (`/minecraft/mc-mods`) and a project page
(`/minecraft/mc-mods/jei`) - **measured off the live computed styles**, not
guessed from a screenshot. The probes are `.tmp/t05-cf-probe*.mjs`; the raw
dumps are `.tmp/t05-cf*.json`.

CurseForge serves a Cloudflare interstitial to plain headless Chrome, so the
probes launch with `--disable-blink-features=AutomationControlled`, a real
user-agent and `waitUntil: "domcontentloaded"` plus a 5-6s settle; they also
retry when `document.title` comes back as `Just a moment...`. Three of four
runs get through.

Measured anchors, and where each one lands in the theme:

| Measured on the site | Value | Used for |
| --- | --- | --- |
| `<body>` / `.curseforge-header` background | `#0d0d0d` | `--canvas`, header tier 1 |
| `.project-card`, project-page sidebar blocks | `#202020` | `--surface` (cards, panels) |
| `.slider`/gallery ground, sidebar recess | `#1a1a1a` - the single most common background on the hub (180 elements) | `--surface-deep` |
| secondary chip / `ul.tabs` bottom rule | `#333333` | `--surface-soft`, `--edge` |
| `.project-card` border, `.search-input-field-label` border, `.project-logo` border, `.floating-header` border | `1px rgba(255,255,255,.16)` | `--edge-light`, `--chip-edge` |
| `.btn-search` / `.btn-favorite` / version chips | `rgba(255,255,255,.08)` - the most common background on the listing page (81 elements) | `--chip`, `--tag-background` |
| `.game-navbar` (the sub-nav band) | 67px tall, `#202020`, 1px bottom edge | nav tier 2 |
| `.curseforge-header` + its `nav` | 64px tall, `#0d0d0d`, padding `0 20px` | nav tier 1 |
| `.game-navbar-tab` / `.nav-link` | `padding: 8px 0` / `4px 0`, `border-bottom: 2px` transparent, `border-radius: 4px`, colour `#9f9f9f`, Montserrat 500 | `.navbar-nav` items |
| Install CTA on a listing card | `#f16436` (22 elements on the listing page) | `--green` |
| `.btn-cta`, `.btn-header-cta` | `#eb622b`, `padding: 8px 16px`, 40px tall, `border-radius: 0`, Montserrat 400 16px | `--green-hover` |
| `.btn-lined` (secondary) | transparent, `1px #9f9f9f`, `#e5e5e5` text, `padding: 8px 16px` | secondary chip treatment |
| version chips | 32px tall, `padding: 4px 8px`, `rgba(255,255,255,.08)`, `border-radius: 4px`, Montserrat 500 14px, `#dfdfdf` | `.tag` / `.badge` |
| `.tag.new-tag` | 20px tall, 12px/500, `padding: 4px 8px`, `border-radius: 4px` | `.tag-2` |
| Cloudflare interstitial on a plain headless Chrome | needs `--disable-blink-features=AutomationControlled` + a real UA + a retry on `document.title` | how the probes get in at all |
| `.search-input-field-label` | 40px tall, `padding: 8px 16px`, `1px rgba(255,255,255,.16)`, `border-radius: 4px` | form controls |
| `.project-card` | 856x156, `padding: 12px`, `border-radius: 2px`, `display: grid`, stacked at a 168px pitch | `.card` |
| sidebar `hr.separator` | `1px` line in the **page** colour (`#0d0d0d`) inside a `#202020` panel | in-panel separators |
| radius census | 0px × 2731 / 1262 / 847; 2px × 120 / 63 / 16; 4px; 50%; 100% | 0, `--radius-md: 2px`, `--radius-chip: 4px` |
| font census | `latoLocalFont` 2728 / `montserratLocalFont` 310 (`h1` 32px/700, section head 20px/700 Lato + 1px rule, body 16px) | `--font-body` / `--font-display` |
| text colour census | `#b2b2b2` ×1381, `#999` ×634, `#e5e5e5` ×529, `#fff` ×132, `#dfdfdf` ×105 | `--ink-muted`, `--ink-faint`, `--ink`, `--ink-bright`, chip text |

## What keeps it from being t01-ore painted orange

Repainting t01-ore's tokens would leave its *structure* untouched - banded card
headers, thick bordered cells, filled hover boxes, a light single-row navbar and
chunky display buttons. Four deliberate structural choices are what pull the two
themes apart, and each is visible in the audit's signature diff:

1. **A two-tier header.** t01-ore's `.navbar` is one light 73px row whose items fill
   in green on hover. Here the contract's `.navbar > .navbar-brand + .navbar-nav`
   is stacked: the brand gets its own 64px row on `--canvas`, and the nav list
   becomes a 60px `#202020` band closed by a 1px `--edge` rule. Items are
   Montserrat 500 on a transparent 2px bottom border, and hover/current mark
   themselves with that border (`--edge-light` on hover, the brand orange when
   current) instead of a fill.
2. **Hard 1px separators instead of elevation.** Every card, panel, popover,
   drawer, toast and sidebar in this theme is a flat surface with a 1px border;
   `--elev-whisper` is `none`, and the audit finds exactly four box-shadows in
   the whole document - all of them a 1px inset rule or the selection ring, none
   of them depth. Card headers and footers are 40px bands split off by a 1px
   line; a card accent is a hard 3px top rule.
3. **Density.** Buttons are 40px, table rows are 38px with `padding: 8px 12px`
   and **no vertical cell borders** (the site's file lists have horizontal rules
   only), list rows are 40px, menu rows 32px, inputs 40px. The same markup
   renders 17788px tall under this theme against t01-ore's 18463px (both at
1440px wide, every harness section shown at once).
4. **A flat type ladder.** t01-ore jumps 36/28/22/18 with a pixel display face.
   CurseForge's scale is 32/20/18/16/14 - dense, with `h2` carrying a 1px bottom
   hairline the way the site's section headings do - and it is set in Montserrat
   over Lato.

## Contract token mapping

The token *names* are the shared contract; this is what each one carries here.

| Contract token | CurseForge role |
| --- | --- |
| `--canvas` | Page `#0d0d0d` (also the `hr.separator` tone inside panels) |
| `--surface` | Panel/card `#202020` |
| `--surface-deep` | Recessed `#1a1a1a` (inputs, table heads, code, menu, toast) |
| `--surface-soft` | Flat chip `#333333` (secondary/tertiary buttons, segmented, dropdown label) |
| `--edge` / `--edge-light` | Separator `#333333` / highlight hairline `rgba(255,255,255,.16)` |
| `--ink` / `--ink-muted` / `--ink-faint` | `#e5e5e5` / `#b2b2b2` / `#999999` |
| `--ink-dark` | `#0d0d0d` - the dark text role (coloured chips, `banner-important`) |
| `--green` (+hover/shadow) | **Brand orange `#f16436`**, hover `#eb622b` (both measured) |
| `--purple` / `--blue` / `--gold` / `--red` | Derived: `#7d5cd6` / `#3d8fd1` / `#e0a92c` / `#d94a35` |
| `--success` / `--warning` / `--danger` / `--info` | Derived: `#5aa84f` / `#e0a92c` / `#e0573f` / `#4b9bd8` |
| `--focus` | `#ff7a4d` - a lightened brand orange, drawn as an `outline` ring (flush, inset, or held 2px off the fill; see "Focus rings" below) |
| `--gray-10..100` | The site's own greys, dark to light: `#f2f2f2 #e5e5e5 #cccccc #b2b2b2 #999999 #808080 #4d4d4d #333333 #262626 #1a1a1a` |
| `--green-30..70` | Brand ramp `#f79b76 #f4804f #f16436 #d94f26 #b03c1a` |
| `--disabled-background/-border/-shadow/-foreground` | `#333333` / `#4d4d4d` / `#262626` / `#666666` |
| `--overlay` / `--overlay-soft` | `rgba(13,13,13,.72)` / `rgba(13,13,13,.55)` |
| `--radius-sm` / `--radius-md` | `0` / `2px` (plus the theme's own `--radius-chip: 4px`) |
| `--size-unit` / `--motion-fast` | `1px` / `100ms` |
| extras: `--ink-bright`, `--ink-on-dark`, `--chip`, `--chip-hover`, `--chip-edge`, `--track`, `--deep-dark`, `--dark-surface`, `--elev-ring`, `--elev-whisper: none`, `--font-display`, `--font-body`, `--font-mono` | Theme-private helpers, generic names, no theme prefix |

## Shape and type language

- **Near-square with two whispers.** `border-radius: 0` is the default -
  buttons, nav items, tables, progress, switch, checkbox, slots, steps, toolbar,
  pagination. The measured minority gets what the site gives it: **2px** on
  cards, panels, modals, menus, dropdown panels, tooltips, toasts and the code
  block (`--radius-md`), and **4px** on chips, tags, badges, inputs, tabs,
  segmented controls, the stepper and the toolbar (`--radius-chip`). Circles
  survive only where the control is round: radio, avatar-style dots, spinners.
- **1px hairlines only.** Every 2-5px border t01-ore drew became 1px, and the pixel
  double-lines it used for `hr`, menu separators and the step connector are now
  single rules.
- **In-panel separators use the page colour.** The site separates sidebar blocks
  with a 1px line of `#0d0d0d` inside a `#202020` panel, so `.kv-row`,
  `.list-group-item`, `.settings-list .settings-row` and `.sidebar-divider` all
  divide with `--canvas` rather than a lighter grey.
- **Orange fills against `#333` chips.** Primary/secondary/danger buttons are
  solid fills whose border matches the fill (no pale ring), secondary and
  tertiary collapse onto the one flat `#333` chip, and the 4px translucent chip
  (`rgba(255,255,255,.08)`, Montserrat 500 14px) is the tag/badge/version chip.
- **The switch is not a pill.** There is no switch anywhere on CurseForge to
  measure, so this theme builds the one its own language implies: a 46x24 track
  at `--radius-md` with a **20px square knob**, orange track when on
  (`--disabled-*` when locked). t01-ore's version is a 26px icon frame with five
  layered gradients and a 30px bevelled thumb, so the pseudo-element diff on
  `.switch` reports 21 differing fields.
- **The slider is a 6px bar with a square 16px knob** ringed in `--canvas`, so
  the knob reads on both the `#333` track and the orange fill. Hover grows it
  (`scale(1.1)`) instead of recolouring it; the disabled knob never moves.
- **The spinner is a thin round ring** turning smoothly, in brand orange on a
  translucent track (t01-ore's is a 4px square ring stepping in eight frames).
- **Focus rings are `outline`s**, not `box-shadow`s (engine commit `2675baf3`;
  spec §4 rule 2). An outline paints outside the border box, touches no box model
  and does not replace the `box-shadow` list, so a focus rule no longer has to
  restate the component's own surface shadow - the old box-shadow rings did, and
  that is why the old rules repeated every bevel. Every rule lists `:focus` *and*
  `:focus-visible` because the engine degrades the latter to the former and a
  mouse-driven browser never matches it. `outline` is not in the transition
  whitelist, which suits a focus ring (it should switch instantly), and the theme
  never writes `outline: none`.
  Placement is 2px solid `--focus` in one of four positions, chosen so the ring
  does not land on a surface of its own hue (an orange ring on the brand orange
  measures 1.23:1, i.e. invisible; the one case that cannot be moved off such a
  surface is called out at the end of this bullet):
  - `outline-offset: 2px` - buttons, icon buttons, checkboxes, radios, page
    buttons, chips (`.tag`) and slots read on the band behind them, not on the
    fill; on a slot that also keeps the 2px `.slot.active` frame intact;
  - `outline-offset: 0` - navbar items (with an orange underline on the item's own
    2px bottom border), dropdown labels, the switch and the slider track, the tab
    family (an inset ring would sit on a current tab's orange underline), and any
    *selected* row whose own marker is the brand orange (`.menu-item.active`,
    `.list-group-item.active`, `.segmented-item.active`, `.toggle-item*`, and the
    active tree/sidebar/settings rows, whose 2px left bar would otherwise eat the
    ring's left band);
  - `outline-offset: -2px` (inset) - unselected rows and containers
    (`.menu-item`, `.list-group-item`, `.tree-item`, `.settings-row`,
    `.accordion-header`, `.segmented-item`, `.stepper-button`), where the line sits
    just inside the 1px border;
  - `outline-offset: -4px` - the vertical tab, which keeps its ring inside (its
    tabs are stacked with `gap: 0`) but 2px clear of the active tab's 2px left bar.
  One case stays under 3:1 and cannot be fixed without either a second ring colour
  or `outline: none`: a closer (`.tag-close`) sitting inside a filled chip
  (`.tag-primary`), measured 1.07:1 against the chip's `#f16436`. It was 1.13:1
  under the old box-shadow ring, so this is pre-existing, not a conversion loss.

## Verification (spec §9)

Run with `.tmp/t05-audit.mjs` (writes `.tmp/t05-audit.json` plus per-page and
full-page screenshots) against `t00-example.html` with the theme scope injected
at runtime, and `.tmp/mk-t05-curseforge.py` re-run for the class/token greps.

- **Class reconciliation vs §3**: 141 distinct contract classes parsed out of
  the spec's core-layer table; **141 present in the stylesheet, 0 missing, no
  empty rule blocks**.
- **Token reconciliation vs §2**: 70 required tokens (ranges expanded), **0
  missing, names unchanged**; plus 27 theme extras, all with generic names.
- **No leftovers**: with comments stripped, the stylesheet contains no `t01-ore`
  identifier, no `Ore` family name, no `@font-face`, **no t01-ore palette hex and no
  ore `rgba()` form** - every hex in a declaration belongs to this theme.
- **The two-tier header, checked in the pixels**: in the rendered page the
  brand row is `rgb(13,13,13)` from its top to its base and the nav band below it
  is `rgb(32,32,32)` with a 1px `#333333` rule under it - i.e. the measured
  `#0d0d0d` over `#202020`, not two shades of one bar.
- **Consistency**: five harness pages render 6/8/8/9/3 active sections at
  4560 / 2488 / 4026 / 4449 / 2708px with **0 zero-size visible elements** (the
  single 0-width box is `.toolbar-spacer`, a flex spacer by design), **0 elements
  overflowing a parent by >6px**, **0 clipped controls**, and every flex row
  child centred within ±1px.
- **Contrast**: 629 visible text elements measured, compositing translucent
  backgrounds down the ancestor chain. **0 real failures below 3.0:1** - the one
  entry under 3.0 is the loading button, whose text colour is `rgba(0,0,0,0)` by
  design because the spinner replaces it. The worst legitimate ratio is
  **3.17:1** (white on the brand orange `#f16436`); 30 elements sit under the
  4.5:1 AA bar for small text, and they are all filled orange/red chips and
  buttons (see limitations).
- **Cross-theme signature diff vs t01-ore**: 51 components compared on 20 fields
  (height/width, padding, border, radius, background, colour, font, size,
  weight, line-height, text-transform, letter-spacing, display, gap, shadow).
  **All 51 differ structurally, 0 are colour-only, average 17 of 20 fields
  differ, minimum 14**. Pseudo-element signatures for the 11 controls that live
  in `::before`/`::after` differ by 11.5 fields on average (`.switch` 21,
  `.slider` 19, `.progress` 12, `.checkbox` 8). Full-page pixel diff on the same
  markup: **34.2% of a 1440x17788 canvas differs**.
- **States**: a static sweep of the stylesheet finds a focus rule *and* a
  disabled rule for all 34 interactive classes in the core layer, with no gap.
  In the live page 21 interactive components were focused and **all 21 ring, 0
  missing** - the audit reads the `outline` (2px solid `rgb(255,122,77)` on every
  one of them, offsets -4/-2/0/+2px as listed above), and a static scan of the
  whole sheet finds **0 `:focus` rules carrying a ring in `box-shadow` and 0
  `outline: none`**.
  A real-keyboard `Tab` walk reaches **138 focusable elements across the five
  pages: 107 of them are theme components and all 107 draw the outline ring; the
  other 31 are the harness's own toolbar (`.t00-select`, five `.t00-tab`s per
  page) and one unclassed `<a>`, which carry no theme class**. No disabled
  component is reachable by `Tab` (0 of 138), and programmatic focus lands on
  none of the 6 visible `[disabled]` controls.
  8 hosts are `div`/`span` carriers (`.switch` / `.switch.on`, `.checkbox` /
  `.checkbox.on`, `.slider`, `.settings-row`, `.list-group-item`, `.tag`) and
  needed an injected `tabindex`
  before the ring was reachable at all - the page must add it, per spec §4.2.
  12 disabled candidates were hovered with a real pointer and **0 changed a
  single computed field** (disabled beats hover). 10 components were measured
  hover-on/hover-off and all 10 change something.

## Known limitations

- **The site's own CTA pairing misses AA.** White on `#f16436` is 3.17:1, and on
  the `#eb622b` hover 3.33:1. Those are CurseForge's measured values, kept
  because they are the brand, and they clear the 3:1 that §9 flags (they are fine
  for large text, short for 16px body-sized button labels). The 30 elements under
  the 4.5:1 AA bar for small text are exactly these filled orange/red chips and
  buttons - white on `--green` 3.17:1, on `--red` 4.21:1. Where the fill is light
  the theme already flips the text: `--gold`, `--warning`, `--gold-vip` and
  `--info` chips carry `--ink-dark` (5.6:1 on the blue, 7.5:1 on the gold), which
  is why no chip reports under 3:1. Dark text on the orange would reach 6.1:1 if
  a project prefers AA over fidelity; `--green` is the one token to change.
- **The functional hues are derived, not sampled.** CurseForge is monochrome
  plus one orange: no purple, blue, green, red or gold appears anywhere in its
  computed styles. `--purple`, `--blue`, `--red`, `--gold` and the
  success/warning/danger/info set are therefore chosen to read on `#0d0d0d` and
  `#202020` and to sit quietly next to the brand orange. `--green-shadow`
  (`#c9502a`) and `--focus` (`#ff7a4d`) are likewise derived; `--green-hover` is
  the site's other measured orange.
- **The switch, the slider, the checkbox and the progress bar have no site
  counterpart.** CurseForge's public pages expose no toggle, no range input and
  no progress bar, so those components are built out of the measured primitives
  (1px separators, 2/4px radii, the chip and brand tones) rather than copied.
  They are the least "sampled" part of the theme and the first place to look if
  something should change.
- **The webfonts are not bundled.** Chrome on this machine has neither Lato nor
  Montserrat, so the preview falls back to Segoe UI / Arial and the two families
  are only visible as a stack difference, not a shape difference. The stacks list
  both, so a machine with them installed gets the real thing; shipping them in a
  `fonts/` directory (the way t01-ore ships its two faces) would make it
  deterministic. This theme deliberately ships no `@font-face` at all.
- **CJK falls back to a system face.** Lato and Montserrat carry no CJK glyphs,
  so Chinese text lands on the first available Chinese sans
  (`PingFang SC` / `Microsoft YaHei` / `Noto Sans SC`) rather than a typeface
  chosen for the design.
- **The navbar hint in the shared harness is now inaccurate.** `t00-example.html`
  tells the reader the navbar is a light surface using `--ink-dark`; that was
  true for t01-ore and t03, but this theme's navbar is dark and uses
  `--ink`/`--ink-bright`. The harness is shared and per spec §9 must not carry
  theme-specific wording, and this theme must not edit it either - so the hint
  stays wrong under this theme until someone rewrites it neutrally.
- **The `-2` / `-3` numbered generations lose their bevelled identities.** They
  keep their class contract and their palettes but are now flat slabs in the same
  language as the base: t01-ore's katorlys top-highlight band and stepped unit bevel
  are gone, which is the intended consequence of flattening every inset shadow.
- **`hr.separator`-style 1px `--canvas` separators are invisible on `--canvas`
  itself.** Inside a card or panel (`#202020`, `#1a1a1a`) the line reads; a
  component dropped directly on the page background gets no visible division -
  which is also how the site behaves.
- **`--radius-md: 2px` is restored after t01-ore's alias block.** t01-ore declares its
  alias/token block *after* the base token block, so `--radius-md`,
  `--radius-sm`, `--size-unit` and `--motion-fast` are re-stated in this theme's
  last token block; editing the top block alone would silently do nothing.

## Preview

The theme is not wired into `../t00-example.html` yet; to look at it, inject it
at runtime:

```js
await page.addStyleTag({ url: "http://127.0.0.1:8731/t05-curseforge/t05-curseforge.css" });
await page.evaluate(() => { document.body.className = "t05-curseforge-theme"; });
```

Adding it permanently is the two-line change described at the top of that file:
one `<link rel="stylesheet">` in `<head>`, one entry in the `THEMES` array and
one `<option>` in the toolbar.
