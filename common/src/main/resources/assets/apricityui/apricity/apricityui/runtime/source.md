# Vue runtime source

- Framework: Vue 3.5.34 global production build, MIT license in `vue-license.txt`.
 Source: `vue@3.5.34`, `dist/vue.global.prod.js` from the official npm package.
 Adaptation: `scripts/runtime/refresh-vue.ps1` applies pinned Babel ES5
  versions and `scripts/runtime/rhino-semantics.cjs` to produce `vue.aui.js`.
- This resource belongs to the shared page runtime; themes and optional
  component libraries reference it without shipping their own Vue copy.
- `example.html` mounts a Vue counter with no theme or component-library dependency.
