# Vue runtime source

- Framework: Vue 3.5.34 global production build, MIT license in `vue-license.txt`.
- Source: `node_modules/vue/dist/vue.global.prod.js` from the pinned
  [mcui-oreui 1.2.2 checkout](https://github.com/ShenYuanOR/mcui-oreui)
  at `ec87d29a9516a741e5bd4ac707dcabc704409cb2`.
- Adaptation: the pinned Babel ES5 transform and AUI Rhino semantics plugin
  in `scripts/ore/refresh-runtime.ps1` produce `vue.aui.js`.
- This resource belongs to the shared page runtime; themes and optional
  component libraries reference it without shipping their own Vue copy.
- `example.html` mounts a Vue counter with no theme or component-library dependency.
