# t04-claude-round

The earlier Claude / Anthropic-styled theme for ApricityUI: warm parchment
surfaces, terracotta accents, serif headlines, hairline borders and ring
shadows. Same class contract as t01-ore, the opposite end of the room from t01-ore's
dark MC pixel look.

Sibling theme: **`t03-claude-square`** is the same Claude palette and the same
contract, redesigned against claudemarket.ai - JetBrains Mono for interface
text, Crimson Pro headings, `border-radius: 0` on essentially everything,
white cards and no shadows. This one is the rounded half of the pair.

## Where it comes from

The design language is taken from the Claude design system: parchment
(`#f5f4ed`) and ivory (`#faf9f5`) surfaces, near-black text (`#141413`), the
terracotta brand accent (`#c96442`), warm neutrals only (**never a cool
blue-gray**), 1px hairline borders over a ring-shadow depth system, a radius
scale of 8 / 12 / 16 / 24px, serif headlines pinned to weight 500, and a
generous 1.60 body line-height.

## It is a frozen snapshot

This file is the pre-square output of the Claude generator, kept as-is so the
two design languages can be compared. It was first parked as `t02-claude-round`
next to the older `t02-claude` slot, then renumbered to `t04-claude-round` so
the number follows the order the themes were written rather than the order the
generations were named. When it was renamed it also picked up a handful of
**defect fixes** - none of them are design decisions, and all of them apply
equally to a round theme:

- the scope class moved to `.t04-claude-round-theme` and every private name to
  `t04-claude-round-*`, so both Claude themes can load on one page;
- the `t01-ore-skeleton-sweep` keyframe had never been renamed and **collided with
  t01-ore's own keyframes** whenever the two were loaded together - it is now
  `t04-claude-round-skeleton-sweep`;
- the loading sweep and the showcase stripes still faded through **ore green
  `rgba(60,133,39)` and ore purple `rgba(115,69,229)`** (the hex colour map only
  covered `#rrggbb`, not `rgba()`);
- the spinner was still t01-ore's **square ring turned in eight discrete frames**
  (`steps(8)`); it is now a round 2px ring with a terracotta arc, turning
  smoothly, and the button spinner's 2px off-centre margin was corrected;
- the skeleton shimmer was white on what is now a light surface, so it was
  invisible; it is a warm ink tint now;
- the icon button's glyph was ivory on the ivory chip it had become, which is a
  contrast of 1.0 - completely invisible. It is ink now, and its hover tone
  moved off the dark ramp to match.

Regenerating it is `python .tmp/mk-t04-claude-round.py` (the generator and its
input snapshot both live in the session scratch). It deliberately does **not**
share a pipeline with `mk-t03-claude-square.py` - the Market alignment and
square layers are specific to that theme.

## Contract token mapping

The token *names* are the shared contract; here is what each one carries in
this theme:

| Contract token | Claude role |
| --- | --- |
| `--canvas` | Parchment `#f5f4ed` |
| `--surface` | Ivory `#faf9f5` (cards, panels, inputs' neighbours) |
| `--surface-deep` | Warm Sand `#e8e6dc` (recessed: inputs, table heads, code) |
| `--surface-soft` | Pure White `#ffffff` (the lightest surface only) |
| `--edge` / `--edge-light` | Border Cream `#f0eee6` / Ring Warm `#d1cfc5` |
| `--ink` / `--ink-muted` | Near Black `#141413` / Olive Gray `#5e5d59` |
| `--green` (+hover/shadow) | **Terracotta Brand `#c96442`** - the primary action |
| `--purple` (+hover/shadow) | Coral `#b55536` - the secondary action |
| `--focus` | Focus Blue `#3898ec`, the only cool colour in the system |
| `--gold`, `--red`, `--blue`, `--success`, `--warning`, `--danger`, `--info` | Muted warm tones darkened enough to read as text on parchment |
| `--gray-10..100` | The ten warm tones straight from the design system, light to dark |
| `--overlay` / `--overlay-soft` | Warm black scrims, `rgba(20,20,19,.55)` / `.35` |
| `--radius-sm` / `--radius-md` | 0 / 0 (the components set their own radius) |
| `--motion-fast` | 100ms |
| extras: `--ink-on-dark`, `--deep-dark`, `--dark-surface`, `--ring`, `--elev-ring`, `--elev-whisper`, `--font-display`, `--font-body`, `--font-mono` | Theme-private helpers, generic names, no theme prefix |

## Shape language

- **1px hairlines everywhere.** Every 2-4px border in t01-ore became 1px; thick
  borders are simply not part of this language.
- **Depth is a ring, not a bevel.** All inset bevels were dropped; the depth
  language is `0 0 0 1px` rings, plus a whisper shadow
  (`rgba(0,0,0,.05) 0 4px 24px`) on elevated surfaces, and the switch, slider,
  progress bar and loading bar became pills.
- **Serif for authority, sans for utility.** Headings and other display text
  use `--font-display` (Georgia and friends) at weight 500; everything else
  uses `--font-body` (system sans).
- **The slider is a thin pill track with a white knob ringed in terracotta**,
  optionally filled from the left edge to the knob. `.slider-value` is the
  capsule readout, `.slider-scale` the two end labels, and `.slider-plain`
  keeps the knob without the fill. Hover grows the knob (`scale(1.18)`) and
  expands its shadow without changing its colour.
- **Containers Claude keeps dark stay dark** - drawer, sidebar, toast,
  tooltip, code block - so their ivory text still holds; everything else is a
  light surface with ink text.
- **Focus rings are `outline`, not `box-shadow`.** A ring drawn with `outline`
  paints outside the border box and leaves `box-shadow` alone, so a focus rule
  no longer has to restate - or wipe - the component's own shadow list: the
  switch knob keeps its hairline and drop shadow while focused, the switch keeps
  the whisper elevation its focus state asks for, and nothing else loses a
  shadow it had. Buttons and box controls ring outward (`outline-offset: 0`),
  full-row controls (menu / list / tree / settings rows / sidebar items) ring
  inward (`outline-offset: -2px`) so the ring stays inside the row, and `.slot`
  rings inward as well so the hotbar's selection frame survives next to it.
  `--focus` stays the ring colour everywhere.

## Known limitations

- **CJK headlines fall back to a sans font unless a serif CJK face is
  installed.** Georgia has no Chinese glyphs; the stack lists Noto Serif SC /
  Source Han Serif SC / Songti SC / SimSun / STSong, and on a machine with none
  of them Chinese headings render in the system CJK sans.
- The `-2` and `-3` numbered generations keep their original geometry; in this
  theme they read as further variants of the same flat language rather than a
  distinct second design.
- The two `-3` "unit bevel" buttons lost their top highlight band, which is the
  intended consequence of flattening every inset shadow.
- **The focus ring misses 3:1 on every light surface.** `--focus` `#3898ec`
  measures 2.77:1 on parchment, 2.90:1 on ivory and 2.44:1 on sand, and roughly
  1.25:1 where a ring lands on the terracotta accent (a selected `.menu-item` /
  `.tree-item` / `.segmented-item`). This is not new - the old `0 0 0 2px`
  box-shadow ring measured the same, because the geometry and the colour are
  unchanged - but it is under the 3:1 a non-text indicator needs. The theme's
  own tuned blue `#2f6f9c` (`--blue` / `--blue-30`) would give 4.3-5.2:1; the
  fix is one line (`--focus: #2f6f9c`), left undone because `--focus` is part of
  the frozen snapshot's design language.

## Preview

`../t00-example.html` lists this theme: pick `t04-claude-round` in the toolbar.
