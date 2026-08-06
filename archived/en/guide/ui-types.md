## Quick Start

### UI Types

ApricityUI mainly has three common UI forms:

1. Overlay
2. Screen
3. In-world image UI

If you only look at frequency of use, the first two are the most common. The third is more presentation-oriented and suited to special cases.

---

### 1. Overlay

Overlay is the simplest type.

You only need to create or remove a `Document`.

Java and JS use the same style of entry points:

```javascript
Document ApricityUI.createDocument(String path)
Document ApricityUI.removeDocument(String path)
```

These methods take the string path of an HTML file, create a `Document`, and once loading completes, it joins the render queue immediately.

You can also use other methods on `ApricityUI` to inspect existing documents or interact with UI created by another module.

Multiple documents can exist at the same time. As long as they do not overlap too badly, that is usually fine. If they do, you can still adjust their positions manually.

Even documents created from the same path can coexist, though that is generally not recommended.

---

### 2. Screen

A Screen is effectively an ApricityUI-managed blank screen with a bound `Document`.

You can manage it with:

```javascript
ApricityUI.openScreen(String path)
ApricityUI.closeScreen()
```

If you only need UI preview without real server-side container binding, `openScreen(path)` is enough.

If you need real container and data-source binding, use the server-authoritative entry from Java or KubeJS server
events. Container information is driven by `<container>` element attributes in the template, and the client `openScreen`
automatically extracts container declarations and sends them to the server:

```javascript
// Container info is declared by <container> elements in the template
// Client openScreen automatically extracts and sends declarations to the server
ApricityUI.openScreen("demo/index.html")
```

The names such as `main` and `player` must match the top-level `<container id="...">` values in the template.

Common container declaration examples in templates:

```html
<!-- Block entity inventory -->
<container id="machine" bind="block_entity" size="9" primary="true"></container>
<container id="player" bind="player"></container>

<!-- Entity inventory -->
<container id="entity_inv" bind="entity" size="27" primary="true"></container>
<container id="player" bind="player"></container>
```

The framework does not include trigger logic for you. Right-clicking items, hotkeys, right-clicking blocks, or opening block entities should still be handled in your own events before calling these APIs.

For `bind="entity"`, the target entity must expose a usable item capability such as `ForgeCapabilities.ITEM_HANDLER`, otherwise binding fails.

For `bind="player"`:

- Use `<slot>` as the shell with an explicit nested `<item>` or `<ingredient>` node
- If there are no bound slots inside the container, the system injects 36 player slots automatically
- Slot background rendering is controlled by the `slot` CSS `background-image`; if not configured, it stays transparent

`container` has no built-in title mechanism:

- It does not read a `title` attribute
- It does not infer a title from the first child element text
- If you need a title, write and lay it out as an ordinary DOM node

Unified slot semantics:

- A `<slot>` with a direct `<item>` inside a top-level `container` binds real menu slots by index
- Slots outside `container`, or inside `<recipe>` previews, are virtual
- `mode` exists mainly for legacy compatibility and should not be relied on in new templates
- Virtual item sources are read from nested `<item>` text or nested `<ingredient>` candidate expressions
- `<recipe type="...">recipe_id</recipe>` always generates virtual slots and can be placed inside a container or in normal HTML
- `recipe` reads the recipe id only from `innerText`
- `recipe.type` is required and strictly validated; if invalid, no preview is rendered and `data-recipe-error` is written

Default `global.css` variables:

- `--aui-slot-size`: slot size in pixels
- `--aui-slot-render-bg`: whether to render slot background (`1/0`)
- `--aui-slot-render-item`: whether to render item (`1/0`)
- `--aui-slot-icon-scale`: icon scale
- `--aui-slot-z`: slot z-index
- `--aui-slot-interactive`: whether interaction is allowed (`1/0`)
- `--aui-slot-cycle` / `--aui-slot-cycle-interval`: virtual slot cycling toggle and interval
- `--aui-container-columns`: optional explicit column count; if omitted, runtime injects `min(9, slotCount)`

Useful examples:

- `run/kubejs/server_scripts/example.js`
- `run/apricity/test/index.html`
- `run/apricity/test/saveddata_player.html`
- `run/apricity/test/virtual_container.html`
- `run/apricity/test/recipe_showcase.html`

To be continued.
