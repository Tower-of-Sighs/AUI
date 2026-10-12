# t03-claude-square

A Claude / Anthropic-styled theme for ApricityUI, sampled from the real Claude
Market site (claudemarket.ai): mono interface text under serif headings,
square corners, white cards on a warm near-white page, hairline borders and no
shadows. Same class contract as t01-ore, the opposite end of the room from t01-ore's
dark MC pixel look.

Sibling theme: **`t04-claude-round`** is the same Claude palette in its earlier,
rounded form - Georgia headings, a system sans body, a 6/12px radius scale and
a ring-shadow depth system. It is a frozen snapshot, so the two can be compared
side by side; this one is the site-faithful redesign.

## Where it comes from

The design language is taken from claudemarket.ai, measured off the live
computed styles rather than guessed from a screenshot:

- **near-white page `#fdfdf9` under pure-white cards**, with warm hairlines
  (`#e6e5e1` / `#cdcbc2`) - no cool gray anywhere;
- **JetBrains Mono for every piece of interface text** and **Crimson Pro for
  headings** (`h1` 700, `h2`-`h6` 600);
- **`border-radius: 0` on essentially everything** - the live site sets 0 on
  ~1500 elements and only a handful of round controls are the exception;
- **no shadows**: depth is a hairline, not an elevation. `--elev-whisper` is
  `none` and every gradient/inset bevel from t01-ore is gone;
- the terracotta brand accent `#c96442`, a coral link tone `#ee7e60`, and a
  peach chip (`#fff1ea` fill, `#e7c9bc` outline) for secondary actions.

Per `../theme-spec.md` §8 it was built the documented way: copy t01-ore's
stylesheet, swap the token values, then adapt the shapes. The generator
(`.tmp/mk-t03-claude-square.py`) lives in the session scratch; the resulting
file carries t01-ore's rules plus a "Claude shape layer", a "light-surface
corrections" section, a "Structural differentiation from t01-ore" pass and a final
"Claude Market alignment" layer - all commented, so they can be read as part of
the theme.

## Contract token mapping

The token *names* are the shared contract; here is what each one carries in
this theme, which is the part worth copying into the next theme:

| Contract token | Claude Market role |
| --- | --- |
| `--canvas` | Page `#fdfdf9` |
| `--surface` | White `#ffffff` (cards, panels, dropdowns, icon chips) |
| `--surface-deep` | Recessed `#f5f4ef` (inputs, table heads, code, hover rows) |
| `--surface-soft` | White `#ffffff` |
| `--edge` / `--edge-light` | Hairline `#e6e5e1` / Stronger `#cdcbc2` |
| `--ink` / `--ink-muted` / `--ink-faint` | `#141413` / `#6e6d68` / `#8d846e` |
| `--green` (+hover/shadow) | **Terracotta `#c96442`** - the primary action |
| `--purple` (+hover/shadow) | Coral `#d97757` |
| `--focus` | Focus Blue `#338fe0`, the only cool colour in the system (site value `#3898ec` darkened one step for ring contrast) |
| `--gold`, `--red`, `--blue`, `--success`, `--warning`, `--danger`, `--info` | Warm tones darkened enough to read as text on the near-white page |
| `--gray-10..100` | Warm ramp `#faf9f5` → `#141413`, no cool steps |
| `--overlay` / `--overlay-soft` | Warm black scrims, `rgba(20,20,19,.55)` / `.35` |
| `--radius-sm` / `--radius-md` | `0` / `0` (this theme is square) |
| `--motion-fast` | 100ms |
| extras: `--ink-on-dark`, `--deep-dark`, `--dark-surface`, `--ring`, `--elev-ring`, `--elev-whisper: none`, `--accent-soft`, `--accent-soft-edge`, `--link`, `--font-display`, `--font-body`, `--font-mono` | Theme-private helpers, generic names, no theme prefix |

## Shape and type language

- **Square by default.** Buttons, cards, panels, inputs, menus, dropdowns,
  tags, badges, checkboxes, icon buttons, tables, modals, tooltips and the code
  block are all `border-radius: 0`. A final block-level pass over the generated
  CSS zeroes every radius except the round controls, so the square look cannot
  be lost one rule at a time.
- **Round only where the control is round.** Avatar, radio, step number and
  spinner stay circles; the slider track/knob, the switch track/thumb and the
  scrollbar thumb stay pills. Full-width text areas and inputs are square even
  though the site's own inputs are too.
- **The spinner is a ring, not a box.** t01-ore's is a square with a 4px ring that
  turns in eight discrete frames (`steps(8)`) - a pixel artefact that survived
  the first pass. Here it is a 2px round ring on a warm track with a terracotta
  arc, turning smoothly (`linear`); the button spinner keeps each variant's own
  border colours so it stays legible on a filled slab.
- **1px hairlines only.** Every 2-4px border in t01-ore became 1px; thick borders
  are not part of this language.
- **Mono for interface, serif for display.** Headings use `--font-display`
  (Crimson Pro → Georgia); everything else - body, buttons, labels, table
  heads - uses `--font-body` (JetBrains Mono).
- **Tracked uppercase for anything label-like.** Buttons, tags, badges, nav
  items, tabs, segmented items, menu shortcuts, dropdown labels, breadcrumbs
  and table heads get `text-transform: uppercase` plus a small
  `letter-spacing`. Chinese text is unaffected by the casing and only picks up
  the tracking.
- **The navbar follows the site header.** Sticky at the top with a 1px bottom
  edge, a translucent cream fill (`--surface-veil`) plus an 8px backdrop blur,
  a coral wordmark set in tracked uppercase mono at 16px, and small (12px)
  uppercase links 20px apart that mark hover and the current page with a 1px
  terracotta underline instead of a filled pill. The reference bar is a
  three-group layout (wordmark / links / button pair); this contract's markup
  only carries the first two, so `space-between` leaves the links on the right
  rather than centred.
- **Primary buttons are solid slabs** whose border matches the fill, so the
  button does not get ringed in pale cream.
- **Secondary buttons are the site's peach chip** (`--accent-soft` fill,
  `--accent-soft-edge` outline, terracotta text).
- **The slider is a thin pill track with a white knob ringed in terracotta**,
  optionally filled from the left edge to the knob. `.slider-value` is the
  capsule readout, `.slider-scale` the two end labels, `.slider-plain` keeps
  the knob without the fill. Hover grows the knob (`scale(1.18)`) and expands
  its shadow without changing its colour.
- **Fenced code sits on the light recessed tone**, like the site's command box.
  Only the overlay containers - drawer, sidebar, toast, tooltip - stay dark, so
  their ivory text still holds.
- **Focus rings are `outline`, not `box-shadow`** - the whole family lives in one
  layer at the end of the stylesheet (`Focus rings as outline`), one line per
  component: `outline: 2px solid var(--focus); outline-offset: 0`. The engine has
  `outline` now, so the ring paints outside the border box, costs no layout, and no
  longer replaces a component's own depth - the old rules had to restate every
  bevel because a `box-shadow` declaration replaces the whole list.
  - Every ring points **outward**, including the full-row controls (menu / list /
    tree / accordion / settings row). `outline-offset: -2px` would put the ring on
    the control's own fill, and in this palette a mid-tone blue over terracotta is
    1.28:1 - well under the 3:1 a non-text indicator needs. Outward over the page
    (3.35:1), a card (3.42:1) or the recessed tone (3.10:1) holds everywhere.
  - `.toggle-item` (the CSS says it is the same component as `.segmented-item`)
    and `.drawer-close` (a real button that had no ring at all) are covered too.
  - Both `:focus` and `:focus-visible` are listed for every component; the engine
    degrades `:focus-visible` to `:focus`.

## Known limitations

- **The wordmark coral is darkened one step from the site's.** claudemarket.ai
  sets the brand at `#ee7e60` on `rgba(253,253,247,.88)`, which only reaches
  2.64:1 - under the 3:1 that `../theme-spec.md` §9 flags for large text. This
  theme uses `#e06b47` (3.24:1): visibly the same coral, and it clears the bar
  at the 20px the wordmark is set in. The site's exact value is one token away
  if you want it back.
- **The navbar is scaled up from the reference.** The site's bar runs on
  11-12px links and a 16px wordmark; here they are 14px and 20px, with the
  tracking pulled in to 0.05em / 0.08em because 0.12em reads sparse over CJK
  glyphs. Everything else - sticky blurred cream fill, hairline bottom edge,
  coral caps wordmark, underline instead of a filled pill - follows the site.
- **The webfonts are not bundled.** Chrome on this machine has neither
  JetBrains Mono nor Crimson Pro, so the preview falls back to Consolas and
  Georgia. The stacks list both, so a machine with them installed gets the
  real thing; shipping them in a `fonts/` directory (as t01-ore does) would make it
  deterministic.
- **CJK falls back to a system face.** Neither stack has Chinese glyphs; the
  stacks list Sarasa/Noto Mono CJK and Noto Serif SC / Source Han Serif SC /
  Songti SC, and on a machine with none of them Chinese renders in the system
  CJK sans or serif.
- The `-2` and `-3` numbered generations keep their original geometry; in this
  theme they read as further variants of the same flat language rather than a
  distinct second design.
- The two `-3` "unit bevel" buttons lost their top highlight band, which is the
  intended consequence of flattening every inset shadow.
- **Focus Blue is darkened one step from the site's.** The ring is a non-text
  indicator and has to hold 3:1 over the surface it is painted on; at `#3898ec`
  it read 3.06:1 on white but only 2.78:1 on the recessed `--surface-deep` where
  buttons and segmented controls sit. `--focus` is `#338fe0` (3.10 / 3.35 / 3.42),
  the same blue one step deeper. `--blue` and `--info` keep the site's `#3898ec`.
- **An outward ring is partly covered by an opaque neighbour in tight groups.**
  The engine paints each element's outline in that element's own phase, so a later
  sibling with a background draws over the shared 2px band: measured on the
  keyboard walk, a vertical tab loses its bottom edge to the next tab, the first
  segmented item its right edge to the next segment, and the members of an
  `.input-group` (input next to button) lose the edge they share. 8 of 59 focused
  component instances are affected; the ring stays closed on its other sides. The
  alternative - `outline-offset: -2px` like t01-ore - cannot be used here: t01-ore's
  focus colour is white, so an inset ring works on its green slabs, while a
  mid-tone blue on this theme's terracotta fills is 1.28:1. Lifting the focused
  element above its siblings (`z-index`) would need engine-side verification.
- **Disabled controls still take two hover styles they should not.** `.button:hover`
  and `.form-input:hover` sit after the disabled guards in the cascade, so a
  disabled button picks up the 1px `--ring` shadow on hover and a disabled input
  also swaps its border colour. Both predate the outline change (verified against
  the pre-change stylesheet); the fix is a disabled-over-hover guard repeating
  `box-shadow` / `border-color`, which was left out of this pass.

## Preview

`../t00-example.html` already lists this theme: pick `t03-claude-square` in the
toolbar. Everything in the harness is rendered by this stylesheet alone.
