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

## Scope

- Theme scope: `.ore-theme`
- Custom properties: `--ore-*`
- Display font: `OreDisplay`
- Body font: `OreRegular`
- Entry stylesheet: `ore.css`
