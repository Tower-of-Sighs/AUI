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

Open `example.html` for the complete seven-page component showcase.

## Variants

Every base class keeps its original look. New visuals are opt-in through
numbered suffixes and additional component classes, all scoped under
`.ore-theme` and driven by `--*` tokens:

- Buttons: `.button-primary-2/-secondary-2/-danger-2/-purple-2` (flat),
  `.button-primary-3/-secondary-3/-danger-3` (unit bevel),
  `[data-state="loading"]`, `.icon-button` / `.icon-button-2`
- Surfaces: `.card-2`, `.panel-2`, `.ore-divider-2`
- Forms: `.form-input-2`, `.form-select-2`, `.form-textarea-2`, `.form-help-2`
- Choice controls: `.switch` / `.switch-2` / `.switch-3`, `.checkbox` /
  `.checkbox-2`, `.radio` / `.radio-2`, `.slider` / `.slider-2`
- Feedback: `.tooltip` (+ `.tooltip-2`), `.dropdown` (+ `.dropdown-2`),
  `.toast` (+ `.toast-2`), `.loading-mask` + `.spinner`, `.banner`,
  `.progress-2`, `.tag` / `.tag-2`, `.badge-2`, `.list-group-2`
- Structure: `.drawer`, `.sidebar` / `.sidebar-2`, `.scrollbar` /
  `.scrollbar-2`, `.tab-2` / `.tab-3`

State hooks: `:hover` `:active` `:focus-visible` `:checked` `[disabled]`
`[aria-disabled]` `[aria-pressed]` `[aria-selected]` `[aria-expanded]`
`[aria-invalid]` `[aria-checked]` `[data-state]` `details[open]`, plus the
class equivalents `.on` `.active` `.show` `.open` `.disabled` `.hidden`.
Disabled always wins over hover/active.

## Scope

- Theme scope: `.ore-theme`
- Custom properties: `--*`
- Display font: `OreDisplay`
- Body font: `OreRegular`
- Entry stylesheet: `ore.css`

## mcui-oreui Vue runtime

The same Ore scope also includes the pinned
[ShenYuanOR/mcui-oreui](https://github.com/ShenYuanOR/mcui-oreui) 1.2.2
Vue runtime. Load the generated resources after `ore.css`:

```html
<div id="app"></div>
<script src="runtime/vue.aui.js"></script>
<script src="runtime/mcui-oreui.aui.js"></script>
<script>
  var app = Vue.createApp({ template: '<mc-button>Create</mc-button>' });
  app.use(McUIVue.default);
  app.mount('#app');
</script>
```

- `mcui-example.html` is the in-game 32-element Vue integration example.
- `example.html` remains the pure-CSS Ore showcase.
- SkinViewer is excluded; the remaining 32 components run through AUI's generic
  Java/Rhino DOM, CSS, event, media, and layout implementation.
- No Chromium, MCEF, JCEF, WebView, WebView2, or WebKit runtime is distributed.
- The repository-root `mcui-oreui-customer-demo.html` is the self-contained
  customer preview and is not packaged as a mod resource.
