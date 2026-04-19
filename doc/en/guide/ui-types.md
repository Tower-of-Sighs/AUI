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

If you need real container and data-source binding, use the server-authoritative entry from Java or KubeJS server events:

```javascript
OpenBindPlan plan = ApricityUI.bind()
    .primaryBind("main").savedData("apricityui_demo", "demo_key", 27)
    .bind("player").player()
    .build()

ApricityUI.openScreen(ServerPlayer player, String path, OpenBindPlan plan)
```

The names such as `main` and `player` must match the top-level `<container id="...">` values in the template.

Other common bindings:

```javascript
// Block entity inventory
OpenBindPlan blockEntityPlan = ApricityUI.bind()
    .primaryBind("machine").blockEntity(100, 64, 200, "up")
    .bind("player").player()
    .build()

// Entity inventory by UUID
OpenBindPlan entityPlan = ApricityUI.bind()
    .primaryBind("entity_inv").entity("00000000-0000-0000-0000-000000000000")
    .bind("player").player()
    .build()
```

The framework does not include trigger logic for you. Right-clicking items, hotkeys, right-clicking blocks, or opening block entities should still be handled in your own events before calling these APIs.

For `bind="entity"`, the target entity must expose a usable item capability such as `ForgeCapabilities.ITEM_HANDLER`, otherwise binding fails.

For `bind="player"`:

- Use the unified `<slot>` tag
- If there are no bound slots inside the container, the system injects 36 player slots automatically
- Slot background rendering is controlled by the `slot` CSS `background-image`; if not configured, it stays transparent

Container title rules:

- The title is rendered inside the container, not fixed in the top-left corner of the screen
- It only reads the text from the first child element, such as `div` or `span`
- `container.title` is no longer supported
- If the first child is missing or empty, no title area is rendered and no placeholder is kept

Unified slot semantics:

- `<slot>` inside a top-level `container` binds real menu slots by index
- Slots outside `container`, or inside `<recipe>` previews, are virtual
- `mode` exists mainly for legacy compatibility and should not be relied on in new templates
- Virtual item sources are read only from `slot.innerText`
- `<recipe type="...">recipe_id</recipe>` always generates virtual slots and can be placed inside a container or in normal HTML
- `recipe` reads the recipe id only from `innerText`
- `recipe.type` is required and strictly validated; if invalid, no preview is rendered and `data-recipe-error` is written

Default `global.css` variables:

- `--aui-slot-size`: slot size in pixels
- `--aui-slot-render-bg`: whether to render slot background (`1/0`)
- `--aui-slot-render-item`: whether to render item (`1/0`)
- `--aui-slot-icon-scale`: icon scale
- `--aui-slot-padding`: icon padding
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
