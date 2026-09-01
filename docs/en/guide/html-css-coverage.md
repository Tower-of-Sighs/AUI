# HTML / CSS Coverage

AUI is a self-built HTML/CSS engine, not an embedded browser. This page answers one question: **which web idioms can be carried over directly, and which will be silently degraded or ignored**.

Three support tiers:

| Mark | Meaning |
| --- | --- |
| ✅ | Behavior is essentially the same as a browser |
| 🟡 | Partial support: parses and behaves, but with clear gaps versus the spec |
| ❌ | Unsupported: ignored when set (CSS properties warn once) |

Overall profile: the selector layer is the most complete; layout is a "common subset + key omissions" (no float, no table layout, no sticky); the painting layer covers a lot (shadows, filters, clip-path, transforms, animations all work); the HTML layer targets UI rather than documents — **there is no UA default styling**, so `h1` and `div` are visually indistinguishable.

## Migration Cheat Sheet

If you're short on time, read only this section. Before moving a browser page into AUI, check in priority order:

1. **UA styles are zero**: write all styles for h1-h6, p, ul/li yourself; don't use br/hr to express layout;
2. **Layout to avoid**: float, sticky, grid's areas/auto-flow/named lines, negative margins, table layout;
3. **Value parsing to avoid**: calc multiplication/division, border styles like dashed (solid only), currentColor, named colors beyond the 26, radial/conic gradients, skew/matrix transforms;
4. **Cascade caveat**: the cascade follows browser ordering — normal inline > normal stylesheet, stylesheet `!important` > normal inline, inline `!important` wins over everything;
5. **Text caveats**: italic has no effect (use oblique), justify has no effect, vertical-align only works with baseline, text-decoration only has underline/line-through;
6. **Form caveats**: types like date/email/url degrade to plain text boxes; form submission only fires an event, no request;
7. **transition whitelist**: properties outside the list (gap, grid-template, font-size, etc.) jump instantly.

## HTML Parser

A regex-based tokenizer, not a standard tree builder.

- ✅ All attribute syntaxes, comments, doctype stripping, self-closing and void tags, script/style raw text, always synthesizing html/head/body;
- 🟡 Only six named entities — `amp apos gt lt nbsp quot` — plus numeric entities; there is pop-stack error recovery, but **no browser-style implicit tag generation** — `<p>` is not auto-closed, `<tbody>` is not inserted;
- 🟡 A `<script>` with both src and inline content executes **both** (non-standard); no defer/async/module;
- `<head>` children (title, etc.) don't enter the DOM; there is no `document.title`; `<meta>` only reads the two aui-* specific configs aui-viewport and aui-mouse-events (see the [meta section](apricity-screen#page-meta-configuration)); charset is fixed to UTF-8.

## HTML Elements

**Tags with dedicated implementations**: html/head/body, div/span, pre, textarea (multi-line editing + drag resize), select/option/optgroup (popup layer + full keyboard operation), canvas (2d only), img (async loading, load/error; **no alt rendering, no srcset**), audio (HTMLAudioElement semantics, OGG/WAV, self-drawn controls bar; **no video, no MP3**), a (click only opens href in the system browser), svg/path (subset).

**input, tiered by type**:

| type | Support |
| --- | --- |
| text (including unrecognized fallback) | ✅ Editing, selection, placeholder, maxlength, undo |
| number / range | ✅ Spinner, slider track, min/max/step |
| checkbox / radio | ✅ Custom-drawn controls, group mutual exclusion |
| color | ✅ Framework ColorPicker |
| password | 🟡 Only replaces rendering with `*` |
| file | 🟡 System file dialog, but you only get a path string |
| button / submit / reset / image | 🟡 Unified button mode; image does not display a picture |
| hidden | ✅ |
| date / email / url / tel / search / time, etc. | ❌ All degrade to text; no per-type UI or validation |

**Forms**: submit/requestSubmit/reset, constraint validation, FormData collection, label association, fieldset disabled cascading, and external association via `form=id` are all ✅; action submission and navigation are ❌ (only an event fires).

**Tags without a dedicated class**: p/h1-h6/ul/ol/li/table, etc. are handled as generic block/inline with **no UA styles**; table has no table layout, and `display:table` also degrades to block; basically avoid br/hr; iframe/video/object/embed are unimplemented.

The entire **UA default stylesheet**: about 30 tags are inline (a, b, i, code, img, input, etc.), head/script/style/title/meta/option, etc. are display:none, and everything else is block. That's all.

For extension tags (texture, sprite, container, slot, recipe, translation, etc.), see the [extension elements doc](extension-elements). Unknown tags render as generic Elements without warnings.

## CSS Selectors

✅ Nearly everything common: basic selectors, the four combinators, attribute selectors (including the `i`/`s` flags), the full family of structural pseudo-classes (nth-child including An+B), state pseudo-classes (hover/active/focus/focus-within/disabled/checked, etc.), form pseudo-classes, `:not()/:is()/:where()`, `::before/::after`, and correct specificity and `!important` cascading.

🟡 `:focus-visible` degrades to `:focus`; `:nth-child()` doesn't support `of S`; unsupported pseudo-classes warn once and then never match.

❌ `:link :visited :target :lang() :dir() :has()`, `::first-line ::first-letter ::marker ::selection`.

## @-Rules and Style Sources

- ✅ Cascading across the three sources (style/link/inline), `@keyframes`, `@import` (recursively inlined, with a depth cap and cycle detection), shorthand expansion, css-wide keywords, CSS variables `var()` (with fallback, nesting depth 8); inline style keeps the full declaration list (duplicates are not collapsed — `cssText`/`getPropertyValue` read the last declaration, `setProperty` replaces all declarations of the property then appends — CSSOM semantics);
- 🟡 `@media` only supports min/max-width/height, orientation + `and`; `@font-face` only takes font-family and the first src url(), ignoring format(), multiple srcs, and other descriptors;
- ❌ `@supports @layer @page @container @scope`.

## Layout

**display**: block/inline/inline-block/flex/inline-flex/grid/inline-grid/none ✅; table/list-item/flow-root degrade to block; contents and other unknown values are all block.

**Box model**:

- ✅ margin/padding (including auto centering), margin collapsing, border shorthand, border-radius (including elliptical dual radii), border-image nine-slice, box-shadow (multiple, inset), box-sizing, width/height min/max, aspect-ratio, px/%/em/rem/vw/vh, min()/max()/clamp();
- 🟡 **Negative margins are clamped to 0**; calc() **only supports addition and subtraction** — no multiplication/division or nesting;
- ❌ border-style (everything draws as solid), border-width keywords (thin/medium/thick), fit-content/min-content/max-content, vmin/vmax/ch/ex, physical units.

**Positioning**: static/relative/fixed/absolute ✅ (absolute containing block rules are normal; known deviations: both-sides-auto anchors to the containing block origin instead of the static position, and transform/filter ancestors don't establish a containing block); z-index ✅; **sticky ❌, float/clear ❌**.

**Flexbox**: row/column, row-reverse/column-reverse, wrap/wrap-reverse (**including column-direction wrapping**: a definite-height container breaks into columns when the accumulated height overflows; justify-content within a column, align-content between columns, and align-items inside each column all apply; an auto-height container stays a single column per spec), the six justify-content values plus the `start`/`end`/`left`/`right`/`normal` aliases (`left`/`right` fall back to flex-start in column direction per spec), grow/shrink/basis (**`flex-basis: content` uses the natural size instead of collapsing**; an omitted basis in the `flex` shorthand is `0%` per spec), **min/max respected during distribution** (the flex base size is first clamped to the hypothetical main size, min winning conflicts; a grow item that hits its max freezes and redistributes the remainder to siblings, and once everything is frozen the leftover goes to justify-content), gap, auto margins, anonymous items, order, align-content (including `end`/`normal`, the latter behaving as stretch per spec) all ✅; anonymous text items (direct text) **soft-wrap** at the row container's content width ✅ (known MVP deviation: in mixed layouts text wraps at the full container width rather than its shrunken share, and multiple anonymous items each wrap at the full width independently); align-items/align-self **baseline ✅**: row direction aligns within the baseline-sharing group (with mixed align-self, only items whose computed value is baseline join the group — the rest keep their own alignment), wrap containers align per line, and column direction equals flex-start per spec; the `first baseline`/`last baseline` aliases equal `baseline` per spec.

**Grid** (MVP): template-columns/rows (px/auto/fr/minmax/repeat including auto-fill/fit), gap, items/self alignment, `grid-row/column`'s `N`, `span N`, `N / M`, auto placement ✅; ❌ named lines, template-areas, auto-flow, implicit tracks, place-* shorthands, subgrid.

**Inline**: inline/inline-block line breaking, baseline alignment ✅; **vertical-align only has a real effect with baseline** — all other keywords are silently inert.

**Scrolling**: the five overflow values ✅ (clip clips without scrolling), custom-drawn scrollbars ✅, `scrollbar-gutter: stable`/`stable both-edges` ✅ (reserves a dedicated gutter so the scrollbar does not overlap content shadows; `auto` is the default); the `scroll-behavior` property is not parsed (smooth scrolling is built in); scrollbar-width/color, scroll-snap, etc. ❌.

## Painting and Visuals

| Property | Support | Notes |
| --- | --- | --- |
| color, background-color, opacity | ✅ | |
| background-image | 🟡 | url(), linear-gradient, layered multiple backgrounds; no radial/conic |
| background-repeat | 🟡 | `space` is treated as plain repeat |
| background-size/position/shorthand | ✅ | |
| background-attachment/origin/clip/blend-mode | ❌ | |
| object-fit / object-position | ✅ | |
| visibility | 🟡 | collapse is equivalent to hidden |
| clip-path | 🟡 | polygon/circle/ellipse/inset; inset's round radii are ignored |
| mask | 🟡 | mask shorthand + mask-image/mode/repeat/position/size/clip/origin/composite: url(), linear-gradient, per-layer compositing (add/subtract/intersect/exclude ≈ source-over/source-out/source-in/xor), alpha and luminance modes (mixed-mode layer stacks fall back to alpha), mask-clip/origin with border-box/padding-box/content-box/no-clip (margin-box/fill-box etc. treated as border-box); mask layers that fail to load are skipped (content stays visible, unlike browsers' "mask everything out"); like filter, has no effect inside world windows |
| filter / backdrop-filter | 🟡 | blur/brightness/contrast/saturate/sepia/grayscale/invert/hue-rotate/opacity/drop-shadow, animatable; functions apply in a fixed order (brightness→contrast→saturate→sepia→grayscale→invert→hue-rotate), not the written order |
| transform | 🟡 | translate/rotate/scale on each axis, all angle units; **no skew, matrix, perspective** |
| transform-origin | ✅ | |
| rotate as an independent property | ✅ | translate/scale as independent properties ❌ |
| mix-blend-mode | 🟡 | 17 CSS Compositing and Blending Level 1 operators, including `plus-lighter`; rendered through an offscreen FBO and backdrop snapshot. |
| isolation | 🟡 | `auto` and `isolate` establish an independent compositing group; nested groups are supported. |
| contain / will-change | ❌ | |

## Text

- ✅ font-family (@font-face + fallback chain), font-size, line-height, text-indent, letter-spacing, the six white-space values, **word-break (normal/break-all/keep-all, with basic CJK detection)**, text-overflow:ellipsis, line-clamp, direction;
- 🟡 font-weight (bolder/lighter map fixed to 700/300, not computed from the parent's weight); **font-style only honors oblique — italic does not trigger italics**; text-align's **justify is equivalent to start**; text-decoration only has underline/line-through;
- ❌ font shorthand, text-shadow, text-transform, overflow-wrap, word-spacing;
- Non-standard extensions: `selection-color` (selection color), `text-stroke` (text outline);
- The default font size is fixed at **16px** (web semantics: font-size equals the rendered pixel size, and em/rem are based on 16px as well).

## Color, Interaction, Animation

**Color**: the whole hex family, rgb/rgba old and new syntaxes, hsl/hsla, transparent ✅; `color(srgb-linear r g b / a)` is parsed into a linear-light working color (components are retained through the renderer's 16x SDR bound); `dynamic-range-limit` accepts `standard`, `constrained`, `no-limit`, numbers, and percentages and maps the working range in the filter shader. Ordinary 8-bit framebuffers still clamp presentation above SDR white. Only 26 named colors (CSS has 148); **currentColor resolves to black** (except inside SVG); hwb/lab/lch/oklch ❌.

**Interaction**: common cursor values + `url()` custom cursors ✅ (move/wait/grab, etc. fall back to the arrow); user-select, pointer-events, accent-color, appearance, resize (textarea) ✅; outline ❌.

**Pseudo-element content**: only a single quoted string; attr()/counter()/url()/multi-part concatenation ❌.

**transition**: shorthand, comma lists, all, delays, interruption and reverse redirection ✅; but **animatable properties are a whitelist** — opacity, width/height, transform, filter, color, background-color, the four position edges, margin/padding, border-width, border-radius, box-shadow; anything outside the list jumps instantly.

**animation**: the full shorthand family, multiple animations, the four direction/fill-mode values, steps/cubic-bezier ✅; for interpolation, transform/filter/box-shadow have dedicated interpolation, the rest use generic numeric/color interpolation, and discrete properties don't animate.

## Rendering Layer

- Text: AWT rasterization + font atlas, dual paths for custom fonts and MC fonts;
- Images: UV windows, multi-layer tiling, nine-slice, batching;
- Masking: stencil template masks (including rounded corners), scissor, scroll clipping;
- Filters: offscreen FBO, separable blur;
- Canvas 2D: full path API, gradients/patterns, ImageData, Path2D (all SVG path commands), OffscreenCanvas; no conicGradient, no WebGL;
- SVG: basic shapes + path (including arcs, evenodd) + fill/stroke family + currentColor, output as a bitmap at 4x supersampling; no g/defs/use/text, gradients, filter, transform attributes, or nested svg.

## About Pixel-Level Parity

Internally the framework validates layout with WPT geometry snapshot comparison (mechanism in [wpt.md](wpt)). Currently very few pages are pixel-identical to Chromium, and the differences come almost entirely from the gaps listed above (no UA styles, negative margins, vertical-align, etc.). This doesn't affect its target scenario — hand-written UI — but it does mean **don't expect arbitrary third-party web pages to be presentable when moved over**.
