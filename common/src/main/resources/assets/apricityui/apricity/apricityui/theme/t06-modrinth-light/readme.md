# t06-modrinth-light

A Modrinth-styled theme for ApricityUI, sampled from the live site
(modrinth.com and a project page, `/mod/sodium`): Inter only, a light
card-based application shell, generously rounded surfaces under soft shadows,
fully-round pills and circle icon buttons, green filled primaries and a
rem-based spacing rhythm. Same class contract as t01-ore; the opposite end of the
room from t01-ore's dark MC pixel look.

Where it sits next to its siblings: `t03-claude-square` is a square, shadowless,
mono-under-serif editorial theme and `t04-claude-round` is the same warm Claude
palette in an earlier rounded form. Both are light. What separates this one is
Modrinth's own geometry - a three-step surface stack with **white hairline
cards**, a **pill track with a sliding green pill** for tabs/segmented controls,
**12px controls and 16px containers**, sentence-case Inter at 600/700 with no
uppercase anywhere, and depth that is always a **soft shadow**, never a ring.

## Where it comes from

Every value below was read off the live computed styles, not guessed from a
screenshot:

- **A three-step light surface stack**, not the usual page/card pair: page
  `#ebebeb` (`--surface-1`), raised cards `#f8f8f8` (`--surface-3`), and
  super-raised white chips `#fff` (`--surface-4`) for buttons, inputs and pills.
  Dividers are `#ddd` (`--surface-5`), recessed wells `#ededed`
  (`--surface-1-5`), and the button hover/active steps are `#d9dce0` / `#c3c6cb`.
- **Cards are outlined in white**, not in grey: `border: 1px solid #fff` around
  the `#f8f8f8` surface, with `--shadow-card: rgba(50,50,100,.1) 0 2px 4px 0`
  reserved for interactive/hover cards.
- **Text is the site's own triplet**: `#2c2e31` (`--color-text-default`),
  `#484d54` (`--color-text-tertiary`), `#1a202c` (`--color-text-dark`, the
  colour used on white chips), page body `#111827`.
- **Inter is the only typeface** (`--font-standard`), with headings at the
  site's heavy weights: its `--font-weight-heading` is 800, body text sits at
  400/500, controls at 600.
- **Its own radius scale**, quoted in rem in the site's own tokens:
  `--radius-xs .25rem`, `--radius-sm .5rem`, `--radius-md .75rem`,
  `--radius-lg 1rem`, `--radius-xl 1.25rem`, `--radius-max 999999999px`, plus
  `--radius-card 1rem`, `--radius-btn .75rem`, `--radius-btn-large 1rem`,
  `--radius-btn-circle --radius-max`. Measured usage on a project page: 16px
  (cards, project tiles), 12px (buttons, inputs), 8px (small chips) and 9999px
  (pills, icon buttons - 41 elements in the counted set).
- **Its own spacing rhythm is rem-based**: `--gap-xs .25rem` … `--gap-xl
  1.5rem`, with a numeric ladder `--gap-2/4/6/8/12/16/24`. Control geometry here
  is therefore quoted in rem too, instead of t01-ore's 2px pixel grid.
- **Controls**: buttons are 36px tall, `border-radius: 12px`, `padding: 0 10px`,
  16px/600 (the site's large button is 48px/16px/800); the secondary button is a
  white chip with `inset 0 0 0 1px #ddd` plus a `1px` shadow, and the primary is
  a green slab with a `0 0 0 1px rgba(0,175,92,.3)` halo. Icon buttons are
  circles (36px and 48px measured).
- **Category pills** are 24-26px tall, fully round, white, `1px #ddd`, 14px/400
  secondary text - and the coloured variants tint only the **label**
  (e.g. Fabric `#8a7b71`, NeoForge `#dc895c`, Quilt `#8b61b4`), never the fill.
- **The nav tab group** (`navtabs`) is the signature piece: a `46px` pill,
  `#f8f8f8` fill, `padding: 4px`, a white hairline and the card shadow, with the
  active item drawn as a **green pill inside the track** rather than an
  underline or a box.
- **Inputs**: white, `1px #ddd`, `12px` radius, 36px tall, `padding: 0 12px`,
  16px/500.
- **The focus ring is `outline: 4px solid #ea80ff` at `2px` offset** - a magenta
  halo, not a browser ring.
- **Scrollbars are blue-grey `#96a2b0`**, and keyboard chips are dark
  (`--color-kbd-shadow: rgba(0,0,0,.25)`).

Per `../theme-spec.md` §8 it was built the documented way: copy t01-ore's
stylesheet, swap the namespace, swap the token values, then adapt the shapes.
The generator is `.tmp/mk-t06-modrinth-light.py` and is re-runnable; the stylesheet it
writes carries t01-ore's rules plus a "Modrinth token retune", a "shape layer", a
"component skin", a "light-surface corrections" pass, a "focus rings" pass and a
"disabled states" pass - each commented, so the layers can be read as part of
the theme. Between the inherited rules and those passes there is also a
normalisation step: whatever the source file's own focus rules draw a ring with
(`box-shadow` or `outline`), their ring declarations are dropped before the
theme's own layer is appended, so the ring geometry in the delivered stylesheet
comes from this theme alone and not from whichever state t01-ore happens to be
in.

## Contract token mapping

The token *names* are the shared contract; this is what each one carries here:

| Contract token | Modrinth role |
| --- | --- |
| `--canvas` | Page `#ebebeb` (`--surface-1`) |
| `--surface` | Raised card `#f8f8f8` (`--surface-3`) |
| `--surface-deep` | Recessed well `#ededed` (inside a card; not the input fill) |
| `--surface-soft` | Super-raised white `#fff` (inputs, chips, popovers) |
| `--edge` / `--edge-light` | Divider `#ddd` (`--surface-5`) / stronger `#c9ccd1` |
| `--ink` / `--ink-muted` / `--ink-dark` | `#2c2e31` / `#484d54` / `#1a202c` |
| `--green` (+hover/shadow) | **`#04914f`** / `#0a7141` / `#0a5d38` (the site's green-700/800/900) |
| `--purple` (+hover/shadow) | `#8e32f3` / `#761ad6` / `#651bae` |
| `--gold` (+shadow) | `#a44419` / `#86351a` - the site has no gold; its warning hue is orange, and orange-700 is the step that reads as text |
| `--red` (+hover/shadow) | `#cb2245` / `#b7193d` / `#9a1739` |
| `--blue` | `#1f68c0` (`--color-blue-600`), also the link colour |
| `--success` / `--warning` / `--danger` / `--info` | Text-safe steps: `#0a7141` / `#a44419` / `#cb2245` / `#1f68c0` |
| `--focus` | `#b44bf0` - the site's `#ea80ff` hue, darkened until it clears 3:1 on its own page tone |
| `--gray-10..100` | The site's `--color-gray-50..950` ramp, `#f5f5f6` … `#252627` |
| `--green-30..70` | `#33f598` `#09de78` `#00af5c` `#04914f` `#0a7141` (green-400..800) |
| `--red-10..80`, `--blue-10..30`, `--yellow-*`, `--orange-20`, `--purple-10` | The site's own `--color-*-*` steps |
| `--disabled-*` | `#d9dce0` / `#c3c6cb` / `#d9dce0` / `#686a72` (grey on grey, still 3.9:1) |
| `--overlay` / `--overlay-soft` | `rgba(17,24,39,.55)` / `.32`, i.e. the site's `#111827` scrim base |
| `--radius-sm` / `--radius-md` | `.5rem` / `.75rem` (the site's own `--radius-sm` / `--radius-md`) |
| `--space-1..5`, `--font-sm/md/lg/xl` | `4/8/16/24/32px`, `14/16/20/24px` |
| `--motion-fast` | `150ms`, eased with the site's `--ease-out-expo` |
| `--size-unit` | `4px` (control geometry is on a 4px / 0.25rem grid) |
| extras: `--ink-on-dark`, `--deep-dark`, `--dark-surface`, `--surface-raised`, `--surface-2`, `--surface-veil`, `--edge-card`, `--brand-bright`, `--brand-ring`, `--accent-soft`, `--accent-soft-edge`, `--blue-dark`, `--green-dark`, `--elev-ring`, `--elev-whisper`, `--elev-button`, `--elev-floating`, `--focus-ring`, `--focus-ring-offset`, `--focus-ring-inset`, `--focus-ring-inset-offset` (the two focus tokens carry the ring *width*, the offsets the gap - the ring itself is an `outline`, so there is no shadow token to keep), `--ease`, `--gap-*`, `--radius-xs/lg/xl/card/btn/btn-large/pill`, `--font-display/body/mono`, `--rarity-*` | Theme-private helpers, generic names, no theme prefix |

## Shape and type language

- **One radius scale, nothing sharp.** 12px for controls (buttons, inputs,
  selects, nav items), 16px for every container (card, panel, modal, drawer,
  sidebar, menu, dropdown, table sheet, code block), 8px for slots and
  skeletons, 4px for checkboxes and chips, and a full pill for anything that
  reads as a chip or a segment (tag, badge, tab, segmented item, page button,
  pagination, toolbar, pagination, slider value, progress and slider tracks).
- **Tabs and segmented controls are pill tracks.** `display: inline-flex`, a
  `#f8f8f8` fill, `4px` padding, a white inset hairline and the card shadow; the
  active item is a green pill with the brand halo. Vertical tabs keep the same
  track and turn into a column.
- **Icon buttons are circles**, matching the site's 36px/48px round buttons.
- **Cards: raised grey with a white hairline, elevation only on hover.** The
  header lives in the card's own padding (no banded header), the footer is
  separated by a single hairline.
- **Sentence case everywhere.** There is no `text-transform` in the whole file:
  Modrinth writes sentence case at weight 600/700 and reserves the pill for the
  control, not for the word - the opposite of the tracked-uppercase sibling.
- **Type ladder**: `h1` 40px/700, `h2` 30px/700, `h3` 24px/700, `h4` 20px/600,
  `h5` 17px/600, `h6` 15px/600, `.font-display` 24px/700, body 16px/1.6 with
  `-0.02em` tracking on the display sizes. Inter for everything, `ui-monospace`
  for `code`, `kbd` and the code block.
- **The navbar follows the site's shell**: sticky, page tone with a blur veil
  and a hairline bottom edge, a green 800-weight wordmark, nav items that are
  36px/12px and turn into **white chips** on hover (the site's secondary
  button), with the current page as a **green pill**.
- **Buttons**: primary is a green slab with a `1px` brand halo and the site's
  `--shadow-button`; secondary/normal is the white chip (`inset 1px #ddd` +
  `1px` shadow); tertiary is text-only and fills with an ink wash on hover.
- **Alerts, banners and toasts are tinted**, not filled: a `.1` tint of their
  own hue, a `.25` tint hairline, and text from the darker step of the same hue.
  There is no left colour bar anywhere.
- **Small chips stay dark, big containers go light.** Tooltips, `kbd`, the code
  block and the slot count keep the site's inverted tone (`#1a202c` /
  `#111827`, white text); drawer, sidebar, modal, toast, menu, dropdown and
  every card are light with a soft shadow. That is one decision, not half of
  each.
- **Depth is a soft shadow.** `--elev-whisper` (the site's `--shadow-card`),
  `--elev-button` (its `--shadow-button`), `--elev-floating` (its
  `--shadow-floating`) - plus a green halo ring on filled primaries. Every
  inset bevel, 8px spread and `steps(8)` timing inherited from t01-ore is gone.
- **Focus is a magenta halo**, drawn with `outline` (the engine implements it,
  spec §4): a 3px `--focus` ring at `outline-offset: 2px`, i.e. the site's own
  halo. Because an outline is painted outside the border box and is its own
  property, a focus rule no longer restates - or blanks out - the component's
  `box-shadow`, so a focused primary button keeps its green halo. Rows (menu
  item, list item, tree item, accordion header, settings row, sidebar item) take
  the ring inside the box (`outline-offset: -2px`), buttons and blocks the outer
  one; an accent-filled row is the exception and rings outward, because magenta
  on `--green` is 1.0:1.

## Verification

`../t00-example.html` now wires this theme in (`<link>` + a `THEMES` entry + an
`<option>`), so it was verified through the preview's own theme selector, with
its stored theme cleared first (`localStorage["t00-theme"]`) because the harness
re-applies a stored scope at load; the focus work below additionally puts
`t06-modrinth-light-theme` on `<body>` directly for the pixel probes. Scripts:
`.tmp/t06-modrinth-light-render.mjs` (render + computed-style signatures),
`.tmp/t06-modrinth-light-audit.mjs` (class reconciliation, contrast, states, focus),
`.tmp/t06-modrinth-light-diff.py` (signature diff), `.tmp/t06-modrinth-light-geom.mjs`
(clipping and centring), `.tmp/t06-modrinth-light-probe*.mjs` (site measurements),
and for the focus-ring conversion `.tmp/t06-modrinth-outline-walk.mjs` (real-`Tab`
walk: outline fields, elevation, disabled), `.tmp/t06-modrinth-outline-geom.mjs`
(old/new page-height A/B), `.tmp/t06-modrinth-outline-contrast-all.mjs` (pixel
and composited backdrop contrast) and `.tmp/t06-modrinth-outline-static.py`
(no ring left in `box-shadow`, no `outline: none`).
All measurements were taken with transitions disabled, because a 150ms
`box-shadow` transition otherwise reports the *starting* value - which made
every focus ring look missing until it was fixed.

**Class reconciliation (§3).** 141 core classes; 141/141 have rules in the
stylesheet (0 missing), and 885/885 top-level blocks survive the CSS parser
(no rule silently dropped). A runtime test removes each class and compares 33
computed fields on the element, its `::before`/`::after` and its first child:
131 classes change the rendering. Of the remaining 10, 7 are not used by the
harness (`badge-2`, `scrollbar`, `scrollbar-2`, `toggle-group`, `toggle-item`,
`modal-lg`, `drawer-sm`) and 3 are inert by design: `button-primary` is the same
skin as `.button`, `text-common` is the rarity colour that equals body ink in a
light theme, and the `is-invalid` sample also carries `aria-invalid="true"`.

**WCAG contrast (§9).** 641 visible text nodes measured against their composited
backdrop: **0 below 3.0**. The worst non-exempt result is the navbar wordmark at
**3.41:1** (22px/800, so it clears 3:1 for large text), then `#fff` on the brand
green at **4.06:1** (see limitations) and the tinted badge/alert hues at
3.9-5.5:1. Disabled controls are exempt and sit at 3.9:1.

**Cross-theme signature diff (§9.3).** Against t01-ore, 65 components, 24 fields
each: **average 9.7 differing fields per component, and no component differs in
colour alone** (fontFamily differs everywhere; geometry, radii, borders,
shadows, padding and letter-spacing differ nearly everywhere). Pixel diff of the
same section screenshots: 97.5-99.5% of pixels differ (dominated by the
page-tone flip).

**States (§9.4).** 10 disabled families hovered with a real pointer: **0 reacted**
(the guards duplicate every hover form and cover colour, background, border,
shadow and transform). 22 enabled families confirmed to react on hover, so the
no-feedback result is meaningful. A real `Tab` walk reaches 31 elements and
**all 31 draw a ring**; the focus CSS covers 34 of the 36 interactive classes,
the two exceptions (`stepper`, `input-group`) being non-focusable containers
whose inner button/input are covered. Layout: 0 elements clip their content and
every control's text sits within 0.8px of its box centre.

**Focus rings, after the move to `outline` (§4 rule 2).** Re-measured with
`.tmp/t06-modrinth-outline-*.mjs` (walk, geometry A/B, pixel contrast) and
`.tmp/t06-modrinth-outline-static.py`:

- static: of the 12 `:focus` rules left in the stylesheet, **0 carry a ring in
  `box-shadow`** and the file contains no `outline: none`; every ring is one of
  two shapes - `outline: var(--focus-ring) solid var(--focus)` (3px, offset 2px)
  or the inset `var(--focus-ring-inset)` (2px, offset -2px);
- keyboard: a real `Tab` walk over all five preview sections reaches **155
  elements (125 of them theme components, the rest the preview harness's own
  controls) and all 155 draw a solid `--focus` outline** - 140 outward
  (3px/2px), 15 inset (2px/-2px), colour `rgb(180,75,240)` = `--focus` on every
  one. In the dark scheme the same walk reaches 138 and **all 138 ring**
  (`#ea80ff`, 123 outward / 15 inset). The walk before the change found two
  elements that drew no ring at all (`tab.active`, `slot.active`, whose own
  `box-shadow` outranked the ring); they ring now, because `outline` is a
  separate property;
- elevation: a focused `.button-primary` reports the *same* `box-shadow` as an
  unfocused one (`rgba(0,175,92,.3) 0 0 0 1px, …`) - before, the ring replaced
  the whole list and the halo vanished. Same for `.button-secondary` /
  `.icon-button` (inset hairline + elevation kept);
- contrast: the ring against the surface it is painted over is **3.08:1 at worst
  in the light scheme (5.06:1 dark)**, measured by compositing the ancestor
  backgrounds per element, and cross-checked from rendered pixels (3.38:1 on the
  page tone `#ebebeb`, 4.03:1 on white chips, 7.64:1 on the dark page). Nothing
  is below the 3:1 bar for a non-text indicator;
- disabled: 13 disabled controls in the preview, **none focusable and none
  changed by a hover**;
- geometry: full-page heights of all five sections, plus a per-element rect
  signature, are **byte-identical before and after** the change (10/10
  measurements) - an outline costs no layout;
- one more thing the conversion fixed: no focused element changes its
  `border-radius` any more. The old bare-element focus rule carried
  `border-radius: var(--radius-xs)`, which turned a focused `.button` from 12px
  corners into 4px (30 elements in the walk); the ring follows the element's own
  radius now, so that declaration is gone.

## Known limitations

- **The brand green is one step down the site's own ramp.** modrinth.com paints
  white on `#00af5c`, which measures 2.88:1 - below even the 3:1 large-text
  bar. This theme uses the site's next step, `#04914f` (4.06:1 with white), and
  keeps `#00af5c` as `--brand-bright` for tints and the logo-adjacent accent.
  If you want the site's exact fill, `--green: #00af5c` is the one-token change,
  at the cost of that contrast.
- **The focus ring is darkened one step too.** The site's `outline: 4px solid
  #ea80ff` measures only 1.95:1 against `#ebebeb`, under WCAG 1.4.11's 3:1 for
  non-text indicators. The theme keeps the hue and uses `#b44bf0` (3.38:1 on the
  page tone, 4.03:1 on white, 3.08:1 worst case over a tinted active row).
- **The navbar is scaled up from the reference.** The site's links are 11-16px;
  here the wordmark is 22px/800 and the links 15px/600, because the contract's
  markup has no avatar/button cluster to fill a 68px bar.
- **Inter is not bundled.** The stack lists Inter first and falls back to
  `-apple-system`, `Segoe UI`, Roboto, `Noto Sans SC`, `Microsoft YaHei`.
  Measured with CDP `CSS.getPlatformFontsForNode` on this machine: Inter is not
  installed, so the preview actually rasterises Segoe UI (Segoe UI Black for the
  800-weight wordmark), the mono stack lands on Consolas and CJK on Microsoft
  YaHei. Bundling Inter in a `fonts/` directory (as t01-ore does) would make the
  preview deterministic.
- **The code block is the theme's own decision, not a measurement.** The live
  project page had no rendered `pre` when probed, so the dark inverted slab
  follows the site's exposed `--color-bg-inverted: #111827` and its dark `kbd`
  chip rather than a measurement.
- **A focused ring can sit next to ink that is not its backdrop.** The ring is
  painted outside the border box, so in a tightly packed vertical list it can
  overlap the neighbouring element's fill: the vertical tab group has a focused
  plain tab flush above the accent-filled active tab, and the ring band that
  crosses that green pill reads 1.0:1 locally (`1.24:1` in the dark scheme),
  and a focused `.slot.active` has the slot's own green selection frame inside
  its 2px gap. Both are unchanged from the box-shadow ring, which occupied
  exactly the same band; the ring's contrast against the surface it is actually
  painted over stays above 3:1 throughout (see Verification).
- **A focused accent button's halo shares the ring's gap.** `--brand-ring` is a
  1px outer halo, so on a focused `.button-primary` / `.button-danger` it sits
  between the ring and the button: the ring's outer neighbour is the page tone
  (3.38:1) but the neighbour across the 2px gap is the halo, where the local
  reading drops to 2.47:1 (2.83:1 dark). That halo is the component's own
  decoration, which the change deliberately keeps on focus - the old rule
  deleted it, and with it the button's elevation. A 3px ring offset would clear
  the halo at the cost of the site's own 2px gap.
- **The `-2` / `-3` numbered generations keep t01-ore's geometry.** They inherit the
  new palette, radii and typography, but their bevel structure was flattened
  rather than redesigned, so they read as further variants of this language
  rather than as a distinct second design.
- **`transition: ... visibility` survives from t01-ore** (tooltip/dropdown/modal
  fade). The engine drops `visibility` from the whitelist, so in AUI the element
  simply snaps; in a browser it delays the hide until the opacity fade ends,
  which is the intent.
- **Native controls are untouched**, per the spec: `<select>` arrows, number
  spinners, range tracks and unchecked native checkboxes keep the engine's
  hardcoded look. The theme provides `.form-select`, `.stepper`, `.slider`,
  `.checkbox` instead.

## Preview

`../t00-example.html` renders everything in this file's scope once the theme is
listed there. To eyeball it before that, load the harness, add
`t06-modrinth-light/t06-modrinth-light.css` as a stylesheet and give `<body>` the
`t06-modrinth-light-theme` class - that is exactly what the verification scripts do.
