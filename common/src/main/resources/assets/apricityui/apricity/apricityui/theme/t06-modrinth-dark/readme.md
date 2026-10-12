# t06-modrinth-dark

The dark sibling of `t06-modrinth-light`: the same Modrinth design language -
Inter only, a card-based application shell, generously rounded surfaces under
soft shadows, fully-round pills and circle icon buttons, green filled primaries
and a rem-based spacing rhythm - with **Modrinth's own dark palette** swapped
in. Geometry, shape, spacing, type and component structure are carried over
from the light sibling unchanged; only the colour layer differs.

The point of the exercise is that the palette is **measured, not designed**.
Every colour below is a value read off `modrinth.com` with the site's own
`html.dark-mode` class applied, so "what colour is the card in dark mode" was
not a decision this theme got to make.

Where it sits next to its siblings: `t01-ore` is the dark MC pixel original,
`t05-curseforge` is the other dark theme in the repository (CurseForge's near
black + orange), `t03-claude-square` / `t04-claude-round` are the light Claude
pair and `t06-modrinth-light` is this theme's light half. Against `t05` this
one differs in **11.2 of 29 computed fields per component, in non-colour fields
for 66 of 66 components** - it is not CurseForge recoloured.

## Where it comes from

Every value was read off the live computed styles, not guessed from a
screenshot. The raw evidence is `.tmp/mr-resolved.json`: the site's `:root`
block resolved with `getComputedStyle` twice - once with the OS in light mode
and once in dark, both with the canonical `html.dark-mode` class applied -
**304 custom properties each**. 301 of those names exist in both schemes and
**211 of them change value**; 3 exist only in dark (`--hover-filter`,
`--active-filter`, `--shadow-raised`) and 3 only in light (the
`--color-grey-link*` trio). This theme is that delta table applied to the light
sibling's stylesheet.

- **A dark surface stack that is not a mirror.** Page `#16181c`
  (`--surface-1`), raised cards **lighter** than it at `#27292e`
  (`--surface-3`), super-raised controls at `#34363c` (`--surface-4`), recessed
  wells at `#1a1c20` (`--surface-1-5`), and `#42444a` (`--surface-5`) for the
  divider/border rung. In the light scheme the border token and the divider
  token happened to be the same value (`#ddd`); in dark mode
  `--color-divider` collapses onto `#34363c`, which is also the control fill,
  so `--edge` takes `--surface-5` instead - otherwise every input and button
  outline would be painted in its own background.
- **Text is the site's own dark triplet**: `#b0bac5` (`--color-text-default`),
  `#96a2b0` (`--color-text-tertiary`), `#ecf9fb` (`--color-text-dark`, the
  colour used on raised chips), with `#fff` (`--color-text-primary`) for the
  strongest labels.
- **The accent gets brighter, not darker.** `--color-green` goes `#00af5c` ->
  `#1bd96a` and the link blue `#1f68c0` -> `#4f9cff`. Because white on `#1bd96a`
  is 1.75:1, the site flips `--color-accent-contrast` from `#fff` to `#000` in
  dark mode, and so does every accent fill in this theme (see limitations).
- **Shadows do not disappear; they lose their blue tint.** `--shadow-card`
  `rgba(50,50,100,.1) 0 2px 4px 0` -> `rgba(0,0,0,.25) 0 2px 4px 0`;
  `--shadow-floating` keeps its geometry and goes pure black;
  `--shadow-inset*` becomes `rgba(17,24,39,.05/.1/.25)`. The light sibling's
  navy-tinted `rgba(17,24,39,a)` values were split by role first - the ones
  used as a *wash* (`.button-tertiary` hover, spinner track, skeleton sweep)
  become light-on-dark, the rest become pure black.
- **Hover goes lighter, not darker.** `--color-button-bg` `#34363c` ->
  `--color-button-bg-hover` `#494f58` -> `--color-button-bg-active` `#616570`,
  and the site's dark mode carries `--hover-filter: brightness(120%)` /
  `--hover-brightness: 1.25`.
- **Every colour ramp is the site's own dark end**: `--color-gray-50..950`,
  `--color-green-50..950`, `--color-red-*`, `--color-orange-*`,
  `--color-blue-*`, `--color-purple-*`, plus the platform hues
  (`--color-platform-fabric` `#8a7b71` -> `#dbb69b` and friends).
- **The focus ring is unchanged**: `--color-focus-ring` is `#ea80ff` in both
  schemes, and on `#16181c` it clears 3:1 on its own, so unlike the light
  sibling there is no darkened variant. It is drawn as an `outline` (3px at a
  2px offset outward, 2px at `-2px` inward for rows), the same geometry the
  light sibling uses - `outline` is property-per-element, so the ring no longer
  has to replace a focused component's own `box-shadow`.
- **The `oled-mode` and `retro-mode` variants are not this.** Modrinth ships
  several user-selectable dark schemes; the numbers here are the canonical
  `dark-mode` one. Anything read live must be read with `html.dark-mode` set.

Per `../theme-spec.md` §8 it was built the documented way for a derived theme:
copy the sibling's stylesheet, swap the namespace, swap the token values, then
correct the roles that a value-for-value swap gets wrong. The generator is
`.tmp/mk-t06-modrinth-dark.py` and is re-runnable; it reads
`t06-modrinth-light/t06-modrinth-light.css`, renames the namespace and applies
five commented layers - a **dark token block**, a **role-collision
pre-pass**, a **literal map**, a **comment pass** and a **dark corrections
layer** - so the layers can be read as part of the theme.

## Contract token mapping

The token *names* are the shared contract; this is what each one carries here:

| Contract token | Modrinth dark role |
| --- | --- |
| `--canvas` | Page `#16181c` (`--surface-1`) |
| `--surface` | Raised card `#27292e` (`--surface-3`) |
| `--surface-deep` | Recessed well `#1a1c20` (`--surface-1-5`) |
| `--surface-soft` | Super-raised `#34363c` (`--surface-4`) |
| `--edge` / `--edge-light` | `#42444a` (`--surface-5`) / `#646c75` (`--color-divider-dark`) |
| `--ink` / `--ink-muted` / `--ink-dark` | `#b0bac5` / `#96a2b0` / `#ecf9fb` |
| `--green` (+hover/shadow) | **`#1bd96a`** (`--color-green`) / `#0faa4f` / `#0f8642` |
| `--purple` (+hover/shadow) | `#c78aff` / `#ac51fb` / `#972eef` |
| `--gold` (+shadow) | `#ffa347` (`--color-orange`) / `#c64808` |
| `--red` (+hover/shadow) | `#ff496e` / `#ed1148` / `#c8083d` |
| `--blue` | `#4f9cff` (`--color-blue`), also the link colour |
| `--success` / `--warning` / `--danger` / `--info` | `#1bd96a` / `#ffa347` / `#ff496e` / `#4f9cff` |
| `--focus` | `#ea80ff` - the site's own ring, which clears 3:1 on a dark page |
| `--gray-10..100` | The site's dark `--color-gray-50..950`, `#f5f7f8` … `#303136` |
| `--green-30..70` | `#42e686` `#1bd96a` `#0faa4f` `#0f8642` `#126937` (green-400..800) |
| `--red-10..80`, `--blue-10..30`, `--yellow-*`, `--orange-20`, `--purple-10` | The site's own dark `--color-*-*` steps |
| `--disabled-*` | `#494f58` / `#616570` / `#494f58` / `#9fa4b3` (grey on grey, 3.24:1) |
| `--overlay` / `--overlay-soft` | `rgba(0,0,0,.55)` / `.32` - the scrim goes pure black in dark mode |
| `--radius-sm` / `--radius-md` | `.5rem` / `.75rem` (unchanged from the light sibling) |
| `--space-1..5`, `--font-sm/md/lg/xl` | `4/8/16/24/32px`, `14/16/20/24px` (unchanged) |
| `--motion-fast` | `150ms`, eased with the site's `--ease-out-expo` (unchanged) |
| `--size-unit` | `4px` (unchanged) |
| new here: `--ink-on-accent` `--hover-surface` `--active-surface` `--track` `--code-bg` `--code-text` `--skeleton-bg` | `#000` (`--color-accent-contrast`) / `#494f58` / `#616570` / `#42444a` / `#34363c` / `#ecf9fb` / `#303136` |
| extras carried over: `--ink-on-dark`, `--deep-dark`, `--dark-surface`, `--surface-raised`, `--surface-2`, `--surface-veil`, `--edge-card`, `--brand-bright`, `--brand-ring`, `--accent-soft`, `--accent-soft-edge`, `--blue-dark`, `--green-dark`, `--elev-ring`, `--elev-whisper`, `--elev-button`, `--elev-floating`, `--focus-ring`, `--focus-ring-offset`, `--focus-ring-inset`, `--focus-ring-inset-offset` (ring widths and offsets; the ring is an `outline`, so there is no shadow token to keep), `--ease`, `--gap-*`, `--radius-xs/lg/xl/card/btn/btn-large/pill`, `--font-display/body/mono`, `--rarity-*` | Theme-private helpers, generic names, no theme prefix |

## Shape and type language

Unchanged, because the shape layer is not touched: one radius scale (12px
controls, 16px containers, 8px slots, 4px chips, pills for tag/badge/tab/
segmented/page button/progress/slider), pill tracks for tabs and segmented
controls, circle icon buttons, banded-headerless cards with a hairline and
elevation on hover, sentence case at 600/700 with no `text-transform` anywhere,
and the same type ladder. What changes is how the surfaces read:

- **Depth is carried by tone, not by shadow.** On `#16181c` a black shadow is
  nearly invisible, so the stack itself does the work: page `#16181c` -> card
  `#27292e` -> chip `#34363c` -> handle `#616570`, and the card hairline
  becomes the site's translucent `rgba(193,190,209,.12)` instead of a white
  one. This mirrors the light sibling, whose cards are told apart by a *white*
  hairline on a `#f8f8f8` surface rather than by a shadow.
- **The chrome stays dark and the chips rise above it.** In the light scheme
  the tooltip, `kbd`, code block and slot count were the *darkest* things on
  the page. In dark mode a chip is *raised* instead: tooltip and code block
  `#34363c` (`--color-code-bg` / `--surface-4`), page-toned `kbd` shadow, and
  the slot count drops to the page tone `#16181c` so it still reads as a badge
  on a `#34363c` slot.
- **Tracks and handles keep their relationship.** The light sibling's white
  knob on a `#d9dce0` track (1.35:1) becomes a `#34363c` knob on a `#42444a`
  track (1.25:1) - the outline still comes from the knob's own border and
  shadow, exactly as in the light scheme.
- **Accent fills carry black labels.** See limitations; a green primary, an
  active tab, an active menu row and a current page number are all `#1bd96a`
  with `#000` text (`--color-accent-contrast`).

## Verification

`../t00-example.html` does not list this theme yet (adding a `<link>` plus a
`THEMES` entry wires it in); it was verified by injecting the stylesheet at
runtime and putting `t06-modrinth-dark-theme` on `<body>` **in addition to** the
harness's own `t00-shell` class, which carries the page background, with the
preview's theme storage cleared so its script cannot re-apply another scope.
Scripts: `.tmp/t06-modrinth-dark-audit.mjs` (class reconciliation, contrast,
surface visibility, states, focus, cross-theme signatures, screenshots),
`.tmp/t06-modrinth-dark-geom.mjs` (clipping, centring, per-page inventory),
`.tmp/t06-modrinth-dark-structural-diff.py` (the palette-only proof),
`.tmp/t06-modrinth-dark-closeup.mjs` (component close-ups). All measurements
were taken with transitions disabled.

**Class reconciliation (§3).** 141 core classes; **141/141 have rules** in the
stylesheet, 924 brace pairs balance, and Chrome's CSS parser accepts
**892 top-level rules + 3 `@media` blocks + 6 `@keyframes` with 0
empty/unparsed selectors** - no rule is silently dropped. A runtime
test removes each class and compares 33 computed fields on the element, its
`::before`/`::after` and its first child: **131 classes change the rendering**.
Of the remaining 10, 7 are not used by the harness (`badge-2`, `scrollbar`,
`scrollbar-2`, `toggle-group`, `toggle-item`, `modal-lg`, `drawer-sm`) and 3
are inert by design (`button-primary` is the same skin as `.button`,
`text-common` is the rarity colour that equals body ink, and the `is-invalid`
sample also carries `aria-invalid="true"`).

**Palette-only proof (§8: rename the namespace first, then re-tune the
tokens).**
`.tmp/t06-modrinth-dark-structural-diff.py` erases every colour literal *and*
every `var()` reference to a colour-valued custom property from both files,
normalises whitespace, and diffs. Result: **0 selectors exist in only one of
the two files, 1 selector's declaration-property set differs (the scope root,
whose entire delta is the 7 new colour-valued tokens listed above), and the
remaining text diff is a single line pair.** Geometry, spacing, type and
component structure are therefore carried over verbatim - the change really is
the palette layer.

**WCAG contrast (§9.2).** 649 visible text nodes measured against their
composited backdrop, computed by starting from white and folding the ancestor
background chain inward (`acc = layer.rgb*layer.a + acc*(1-layer.a)`), so
translucent surfaces such as `rgba(27,217,106,.25)` are measured as what they
actually paint: **0 of this theme's own text nodes fall below 3.0:1** and 6 fall
below 4.5:1 (the tinted `badge-success` 3.14:1, the progress label 3.75:1, the
red alert/badge 4.25:1 and the tinted active row 4.37:1). The one element below
3.0:1 is the preview shell's own `.t00-tab.active` at **1.88:1** - see
limitations; a naive swap would have produced *hundreds* here, because the
light sibling's `#fff` labels on accent fills are 1.75:1 on the brightened
green.

**Surface visibility (§9.2, non-text).** Because a chip can be invisible
without containing any text, every visible element's painted background and
borders were also composited against its parent's and compared: 19 elements sit
under 1.12:1, all intentionally - the checkbox/radio whose border *is* its fill
(same as the light sibling), the loading mask and the modal/drawer/sidebar
scrims (translucent page-tone and black veils), the icon buttons and step
numbers whose outline is an inset `box-shadow` rather than a border, and
`.toolbar-spacer` (a deliberately empty flex spacer).

**Cross-theme signature diff (§9.3).** 66 components x 29 fields:

- against **`t06-modrinth-light`**: 2.8 fields differ on average, **0 components
  differ in any non-colour field** - every component differs in colour only,
  which is the intended result for a palette swap. Pixel diff of the same five
  page screenshots: **99.8-99.9% of pixels differ** (dominated by the page-tone
  flip).
- against **`t05-curseforge`** (the other dark theme): 11.2 fields differ on
  average and **66/66 components differ in non-colour fields** (heights,
  padding, radii, fonts, radii, borders, line-height). This is not CurseForge
  recoloured.
- against **`t01-ore`**: 11.1 fields differ on average, 66/66 in non-colour
  fields.

**States (§9.4).** 10 disabled families hovered with a real pointer: **0
reacted** - the guards list all three disabled spellings plus their `:hover`
forms and cover colour, background, border, shadow and transform, so a disabled
control cannot light up. 21 enabled families were confirmed to react on hover,
so the no-feedback result is meaningful. A real `Tab` walk reaches 31 elements
and **all 31 draw a ring**; the focus CSS covers 34 of the 36 interactive
classes, the two exceptions (`stepper`, `input-group`) being non-focusable
containers whose inner button/input are covered. Re-measured after the move to
`outline` (§4 rule 2): the keyboard walk over all five preview sections reaches
**138 elements and all 138 draw a solid `--focus` outline** (`#ea80ff`, 123
outward at 3px/2px and 15 inset at 2px/-2px), a focused accent button now keeps
its elevation and halo (`box-shadow` focused == unfocused, where the old rule
replaced the list), the ring's contrast against the surface behind it is
**5.06:1 at worst** (7.64:1 on the page tone, pixel-sampled), 13 disabled
controls are none focusable and none react to hover, no focused element changes
its `border-radius` any more, and the five section heights are byte-identical to
the pre-change ones. The pixel-level caveats - a focused plain tab flush above
the accent-filled active tab, the slot's green selection frame inside the ring's
gap, and the accent button's 1px halo in that same gap - are listed in the light
sibling's limitations, and hold here at the same values.

**Layout.** 0 controls with off-centre labels, 0 elements whose content is
actually clipped, 0 zero-size visible elements inside sections, 0 empty active
sections. The only element flagged for overflow is
`.tooltip.tooltip-right`, whose absolutely-positioned bubble legitimately
extends past its host (the host does not clip). The five preview pages were
rendered and looked at page by page, plus component close-ups of the navbar,
tabs, card, switch, slider, progress, inputs, table, tooltip, inline code,
code block, hotbar, accordion, menu, modal, sidebar and tree.

## Known limitations

- **Accent fills carry black text, and the preview shell does not know that.**
  Modrinth's dark accents are *brighter* than its light ones (`--color-green`
  `#00af5c` -> `#1bd96a`), so white on them measures 1.75:1; the site's own
  answer is to flip `--color-accent-contrast` to `#000`, and every fill this
  theme paints in an accent therefore takes `--ink-on-accent`. A *page* that
  pairs `color: #fff` with `var(--green)` by hand - which is what
  `t00-example.html`'s own toolbar does in `.t00-tab.active`, hardcoded and
  deliberately theme-independent - will get 1.88:1. The theme cannot fix that
  without styling the preview's private `.t00-*` classes, which no theme in
  this repository does; the harness-side fix is one line
  (`color: var(--ink-on-accent, #fff)`). Rendering the same theme in the light
  sibling does not have this problem because its accent is dark enough for
  white.
- **The preview shell is the only thing below 3.0:1.** Everything inside the
  theme's own scope clears it. The 6 elements between 3.0 and 4.5 are all
  "bright hue as text on a 20% tint of itself", which is how the site renders
  its own dark alerts and badges; raising them would mean leaving the measured
  palette.
- **The chip tone is one step up from the measured tooltip token.** The site's
  dark `--color-tooltip-bg` is `#27292e`, the same tone as a card. This theme's
  tooltip is a borderless chip (as in the light sibling), so at that tone it
  would vanish against the very card it pops over; it takes `--surface-4`
  `#34363c` - the site's own `--color-code-bg` and its raised-chip tone -
  instead. Same for the code block's own `--deep-dark`.
- **`--edge` is not `--color-divider`.** In dark mode `--color-divider` is
  `#34363c`, which is also the control fill, so using it for hairlines would
  erase every input and button outline. The border token takes `--surface-5`
  `#42444a`, which still reads on the page, the card and the chip.
- **`--hover-surface` exists because the grey ramp and the hover step split.**
  In the light scheme `--gray-10` was both "rung 10 of the ramp" and "the step a
  control lifts to". In dark mode rung 10 is `#f5f7f8`, nearly white, so the
  ramp and the hover surface are separate tokens
  (`--hover-surface: #494f58`, the site's `--color-button-bg-hover`).
- **Two theme-private helper names describe the light scheme.** `--green-dark`
  and `--blue-dark` are the "readable step of the hue on a tint of itself"
  slots; on a dark tint the readable step is the *bright* end, so they carry
  `#1bd96a` / `#4f9cff`. The names were kept so the layer decomposition can be
  diffed against the light sibling one-for-one.
- **`--deep-dark`, `--surface-raised` and `--deep-dark`-adjacent helpers are
  reused, not re-derived.** In a dark scheme a "dark chip" and a "raised chip"
  are the same idea, so both land on `#34363c`.
- **The `-2` / `-3` numbered generations keep the light sibling's geometry.**
  They inherit the dark palette, radii and typography, but their structure is
  the light sibling's; they read as further variants of this language rather
  than as a distinct second design.
- **Inter is not bundled**, exactly as in the light sibling: the stack lists
  Inter first and falls back to `-apple-system`, `Segoe UI`, Roboto,
  `Noto Sans SC`, `Microsoft YaHei`; the mono stack lands on Consolas on this
  machine and CJK on Microsoft YaHei.
- **An accent-filled row rings outward.** Inset rings sit on the row's own
  fill, and the selected menu row is solid `--green`; magenta on it measures
  1.0:1, so that row (`.menu-item.active` / `[aria-selected="true"]` /
  `[data-state="active"]`) takes the outer ring instead. Everything else keeps
  the inset ring, as the spec's §4 note recommends for rows.
- **Native controls are untouched**, per the spec: `<select>` arrows, number
  spinners, range tracks and unchecked native checkboxes keep the engine's
  hardcoded look. The theme provides `.form-select`, `.stepper`, `.slider`,
  `.checkbox` instead.

## Preview

`../t00-example.html` renders everything in this file's scope once the theme is
listed there. To eyeball it before that, load the harness, add
`t06-modrinth-dark/t06-modrinth-dark.css` as a stylesheet and add
`t06-modrinth-dark-theme` to `<body>`'s existing classes (do not replace them:
`.t00-shell` carries the page background that `var(--canvas)` then fills) -
that is exactly what the verification scripts do. The generator is re-runnable:
`python .tmp/mk-t06-modrinth-dark.py` rewrites this theme's stylesheet from the
light sibling's, byte for byte.
