# Ore

Ore is a pure CSS theme for ApricityUI. It is adapted from
[Minecraft-CSS](https://github.com/Jiyath5516F/Minecraft-CSS) and distributed
under the Mozilla Public License 2.0. See `license.txt` for the complete terms.

## Usage

Load the stylesheet and scope the document with `ore-theme`:

```html
<link rel="stylesheet" href="/apricityui/theme/ore/ore.css">
<body class="ore-theme">
  <button class="button button-primary">Create world</button>
</body>
```

Paths are resolved relative to the current Apricity document, so a document
beside the theme can use `href="ore.css"`. The two bundled fonts are local and
do not require network access.

Open `example.html` for the complete six-page component showcase.

## Opt-in variants

The original classes are the default visual baseline. Numbered classes opt in
to a component-level variant without changing the rest of the page:

```html
<button class="button button-primary-2">Flat action</button>
<article class="card card-2">...</article>
<input class="form-input form-input-2">
<button class="tab tab-2" aria-selected="true">Selected</button>
```

Available variants are `.button-primary-2`, `.button-secondary-2`,
`.button-danger-2`, `.button-tertiary-2`, `.button-normal-2`,
`.button-primary-3`, `.card-2`, `.card-3`, `.panel-2`,
`.form-input-2`, `.form-select-2`, `.form-textarea-2`, and `.tab-2`. The
number identifies appearance only; existing page logic still owns interaction.

The missing static surfaces are available as `.tooltip`, `.dropdown`,
`.toast`/`.pop`, `.loading-mask`/`.spinner`, `.drawer`, `.icon-button`,
`.switch`, `.slider`, `.checkbox`, `.radio`, `.sidebar-2`, and `.scrollbar-2`.
Use `.open`, `data-state`, `aria-*`, or native pseudo-classes to expose their
states. CSS does not implement opening, closing, selection, or loading logic.

New semantic aliases such as `--ore-color-primary`, `--ore-color-surface`,
`--ore-color-border`, and `--ore-color-focus` map to the existing `--ore-*`
tokens. Variant rules also expose component tokens such as
`--ore-button-primary-2-background`, `--ore-card-2-background`,
`--ore-panel-2-background`, `--ore-input-2-focus-border`, and
`--ore-tab-2-active-background` for local overrides. Existing token names and
their values remain supported.

New stateful components include restrained CSS motion: `.spinner` rotates,
loading buttons show a rotating indicator, Toast/Pop and loading overlays enter
with a short fade/slide, and Drawer/Tooltip/control states transition between
faces. Motion timing is controlled by `--ore-motion-fast`,
`--ore-motion-normal`, `--ore-motion-slow`, and `--ore-motion-play-state`.
Add `ore-motion-reduced` to the theme root (for example
`class="ore-theme ore-motion-reduced"`) when motion should be reduced.

## Scope

- Theme scope: `.ore-theme`
- Custom properties: `--ore-*`
- Display font: `OreDisplay`
- Body font: `OreRegular`
- Entry stylesheet: `ore.css`
