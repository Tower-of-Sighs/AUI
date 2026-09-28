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
events. `screen(path)` (and the deprecated client `openScreen(path)` alias) is UI-only. Open real containers with
`menu(player, path).bind(...)`; the server-side binding declaration determines the data sources:

```javascript
ApricityUI.menu(player, "demo/index.html")
    .bind(bindings => bindings.blockEntity(pos).player())
```

The simplified binding builder uses fixed top-level container ids: `player`, `saved_data`, `block_entity`, and `entity`.
Use the lower-level declaration API only when custom ids or multiple containers of the same type are required.

Common container declaration examples in templates:

```html
<!-- Block entity inventory -->
<container id="block_entity" bind="block_entity" size="9"></container>
<container id="player" bind="player"></container>

<!-- Entity inventory -->
<container id="entity" bind="entity" size="27"></container>
<container id="player" bind="player"></container>
```

The framework does not include trigger logic for you. Right-clicking items, hotkeys, right-clicking blocks, or opening block entities should still be handled in your own events before calling these APIs.

For `bind="entity"`, the target entity must expose a usable item capability such as `ForgeCapabilities.ITEM_HANDLER`, otherwise binding fails.

For `bind="player"`:

- Use `<slot>` as the shell with an explicit nested `<item>` or `<ingredient>` node
- An empty `player` container injects 36 player slots automatically; hand-written slots bind real menu slots only when the container id, local index, and direct `<item>` content all match
- Slot background rendering is controlled by the `slot` CSS `background-image`; if not configured, it stays transparent

`container` has no built-in title mechanism:

- It does not read a `title` attribute
- It does not infer a title from the first child element text
- If you need a title, write and lay it out as an ordinary DOM node

Unified slot semantics:

- Only a `<slot>` with a direct `<item>` inside a top-level `container` attempts to bind a real menu slot by local index, and the container id must match the server binding
- Slots outside `container`, or inside `<recipe>` previews, are virtual
- A `<slot>` with direct `<ingredient>` content is display-only and never binds a real menu slot
- `mode` exists mainly for legacy compatibility and should not be relied on in new templates
- Virtual item sources are read from nested `<item>` text or nested `<ingredient>` candidate expressions
- `repeat` expands into consecutive independent slots
- `<recipe type="...">recipe_id</recipe>` always generates virtual slots and can be placed inside a container or in normal HTML; supported types include `crafting_shaped`, `crafting_shapeless`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing`, and `fallback`
- Recipe inputs use `<ingredient>` and recipe outputs use `<item>`; recipe previews never occupy real menu slots
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
