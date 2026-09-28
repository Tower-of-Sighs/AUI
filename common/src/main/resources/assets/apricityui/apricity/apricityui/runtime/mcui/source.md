# mcui-oreui 2.0.0 for ApricityUI

- Source: <https://github.com/ShenYuanOR/mcui-oreui>
- Commit: `d3344a6cec68ce97eb990c125ed3e050c69d7d4c` (`main`)
- License: MIT; see `LICENSE.txt`.
- Built with `scripts/ore/refresh-mcui2-runtime.ps1` and the shared Vue 3.5 runtime.

The AUI bundle exports the upstream `createMcUI()` plugin and 68 public components.
`McSkinViewer` is excluded at the user's request. `components.css` contains only
component styles and tokens; `fonts.css`, icon sets and sounds remain optional.
`gallery.aui.js` and `gallery.css` are adapted from the upstream visual-regression
gallery with only the SkinViewer fixture removed. Load Vue, then the component
bundle, followed by any optional icon set and the gallery script.
