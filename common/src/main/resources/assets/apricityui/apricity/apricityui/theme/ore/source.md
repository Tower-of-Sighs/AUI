# Source record

This directory includes ApricityUI's Ore theme and an optional mcui-oreui Vue component library. The component integration example is
`apricityui/theme/ore/mcui-example.html`; `example.html` remains the pure-CSS
theme showcase.

- Upstream: <https://github.com/ShenYuanOR/mcui-oreui>
- Upstream version: `1.2.2`
- Pinned upstream commit: `ec87d29a9516a741e5bd4ac707dcabc704409cb2`
- Upstream mcui-oreui license: MIT, preserved in `mcui-oreui-license.txt`
- The base Ore theme remains under MPL-2.0 in `license.txt`.
- Upstream ancestry: `Spectrollay-OreUI/OreUI`
- Runtime integrity manifest: `provenance.sha256`
- Reproducible runtime refresh: `scripts/ore/refresh-runtime.ps1`
- Complete resource verification: `scripts/ore/refresh-integrity.ps1 -Mode Verify`
## Adaptation

- Preserved the upstream OreUI CSS, fonts, component class names and
  DOM anatomy needed by AUI-authored pages.
- Scoped the optional component styles to `.ore-theme` and `.mcui-theme` so they
  work with either built-in theme without leaking into unrelated documents.
- Preserved the six upstream `:has(...)` appbar rules. AUI implements the
  relational selector generically so the divider appears only when the
  corresponding appbar side actually contains a control.
- Kept font resources as separate files so AUI can use its normal resource
  loader and cache. Runtime icons and short UI sounds remain embedded in the
  pinned mcui bundle.
- The syntax-adapted Vue 3.5.34 global is shared at
  `apricityui/runtime/vue.aui.js`; this directory only bundles the optional
  mcui runtime as `runtime/mcui-oreui.aui.js`. Pages register it with
  `app.use(McUIVue.default)`.
- Minimal page integration loads both bundled runtime resources:

  ```html
  <script src="/apricityui/runtime/vue.aui.js"></script>
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
- AUI's Java core implements the generic ECMAScript, DOM, CSSOM, event, and
  media closure without component-specific Java. Vue components use AUI's
  renderer; the separate iframe feature may use the system WebView.
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
