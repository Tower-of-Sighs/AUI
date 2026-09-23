# Source record

This is ApricityUI's only built-in Ore UI. The mcui integration example is
`apricityui/theme/ore/mcui-example.html`; `example.html` remains the pure-CSS
theme showcase.

- Upstream: <https://github.com/ShenYuanOR/mcui-oreui>
- Upstream version: `1.2.2`
- Pinned upstream commit: `ec87d29a9516a741e5bd4ac707dcabc704409cb2`
- Upstream license: MIT, preserved in `license.txt`
- Upstream ancestry: `Spectrollay-OreUI/OreUI`
- Runtime integrity manifest: `provenance.sha256`
- Reproducible runtime refresh: `scripts/ore/refresh-runtime.ps1`
- Complete resource verification: `scripts/ore/refresh-integrity.ps1 -Mode Verify`
## Adaptation

- Preserved the upstream OreUI CSS, fonts, component class names and
  DOM anatomy needed by AUI-authored pages.
- Scoped all selectors under `.ore-theme` so built-in styles cannot leak into
  unrelated documents or developer tools.
- Preserved the six upstream `:has(...)` appbar rules. AUI implements the
  relational selector generically so the divider appears only when the
  corresponding appbar side actually contains a control.
- Kept font resources as separate files so AUI can use its normal resource
  loader and cache. Runtime icons and short UI sounds remain embedded in the
  pinned mcui bundle.
- Bundled the syntax-adapted Vue 3.5.34 global as `runtime/vue.aui.js` and the
  mcui runtime as `runtime/mcui-oreui.aui.js`; pages register it with
  `app.use(McUIVue.default)`.
- Minimal page integration loads both bundled runtime resources:

  ```html
  <script src="runtime/vue.aui.js"></script>
  <script src="runtime/mcui-oreui.aui.js"></script>
  <script>
    var app = Vue.createApp({ template: '<mc-button>Example</mc-button>' });
    app.use(McUIVue.default);
    app.mount('#app');
  </script>
  ```
- The 32 retained Vue components remain the behavior source: `McAppbar`,
  `McAppbarButton`, `McAppbarIcon`, `McButton`, `McButtonTabs`, `McCard`,
  `McCheckbox`, `McConfirm`, `McDrawer`, `McDropdown`, `McFormField`,
  `McFormattedText`, `McHeader`, `McIcon`, `McLayout`, `McList`, `McListItem`,
  `McLoadingMask`, `McModal`, `McPanel`, `McPopHost`, `McProgress`, `McRadio`,
  `McRadioGroup`, `McScrollView`, `McSlider`, `McSpinner`,
  `McSwitch`, `McTabs`, `McTcode`, `McTextField`, and `McTooltip`.
- AUI's Java core implements only the generic ECMAScript, DOM, CSSOM, event, and
  media closure. There is no component-specific Java and no Chromium, MCEF, JCEF,
  WebView, WebView2, or WebKit.
- Rhino is a required Java runtime dependency on every loader target. KubeJS
  remains optional and is not used to execute built-in pages.

## Rebuild and verify

From the repository root, point the refresh script at a checkout of the pinned
commit. It runs the upstream locked npm build, applies the pinned Babel ES5
transform used by AUI's Rhino runtime, and refreshes the complete resource
manifest:

```powershell
.\scripts\ore\refresh-runtime.ps1 -UpstreamRoot C:\path\to\mcui-oreui
.\scripts\ore\refresh-integrity.ps1 -Mode Verify -UpstreamRoot C:\path\to\mcui-oreui
```

The manifest covers every file in this directory except the manifest itself, so
missing, extra, or modified Ore resources fail verification.
