# Container Usage Documentation

A container page has two layers, and both are required:

- The `<container>` and `<slot>` elements in the HTML handle page structure, slot positions, and styling;
- The server-side Menu and data sources handle real items, clicks, shift-clicks, permissions, and persistence.

Only when both sides match up can slots actually hold and transfer items. Writing a `<slot>` on the client alone will not conjure up a real slot.

## Opening Methods

| Entry point | What it opens | Real slots |
| --- | --- | --- |
| `new ApricityScreen(path)` (client) | A pure UI Screen | None |
| `ApricityUI.screen(path)` (client) | A UI-only container Screen | None — even if the HTML declares `bind="player"` |
| `ApricityUI.menu(player, path).bind(...)` (server) | ApricityContainerScreen | Yes |

The key point: **for a real container you must go through `menu(...).bind(...)` on the server**. `screen(path)` only requests opening a UI page that looks like a container.

## Minimal Example

`screens/inventory.html`:

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport" content="mode=browser">
    <style>
        body { margin: 0; padding: 12px; color: #e2e8f0; background: #1e293b; }
        container { display: grid; gap: 2px; width: max-content; }
    </style>
</head>
<body>
    <div>Machine Inventory</div>
    <container id="saved_data" bind="saved_data" primary="true" size="9"></container>

    <div>Player Inventory</div>
    <container id="player" bind="player" layout="preset:player"></container>
</body>
</html>
```

Opening from the server (KubeJS server scripts use the same pattern):

```java
public static void open(ServerPlayer player) {
    ApricityUI.menu(player, "screens/inventory.html")
            .bind(binding -> binding
                    .saveddata("machine_data", 9)
                    .player());
}
```

`bind(...)` immediately resolves the data sources and opens the menu. Note that the first argument of `saveddata("machine_data", 9)` is the SavedData data name, not the HTML container id.

**Container ids are fixed** — this is the easiest pitfall to hit:

| Binding method | Required HTML id |
| --- | --- |
| `player()` | `player` |
| `saveddata(...)` | `saved_data` |
| `blockEntity(pos)` | `block_entity` |
| `entity(entityId)` | `entity` |

If the HTML uses `id="machine"` while the server calls `blockEntity(pos)`, the two sides don't match and all slots degrade into display-only slots.

## Binding API

`ApricityUI.menu(player, path)` returns a PendingMenu; calling `bind(...)` actually opens it. If the callback declares no data source at all, a UI-only menu opens — in which case you might as well use `screen(path)` directly.

| Method | Capacity | Description |
| --- | --- | --- |
| `player()` | Fixed 36 | Player inventory + hotbar; local indexes 0-8 are the hotbar, 9-35 the inventory |
| `saveddata()` / `saveddata(name)` / `saveddata(name, cap)` | Default 9 | World-level persistent inventory; data name defaults to `apricityui_data` |
| `blockEntity(pos)` / `blockEntity(pos, cap)` | capability capacity | The block entity's Forge ITEM_HANDLER |
| `entity(id)` / `entity(id, cap)` | capability capacity | The entity's ITEM_HANDLER |

Passing capacity 0 means using the data source's full capacity (SavedData has at least 1 slot).

You can chain multiple bindings of different types: `binding.blockEntity(pos).saveddata("cache", 9).player()`. The first non-player binding automatically becomes primary (which decides the shift-click direction), even if `player()` is written first.

Only one binding per type is allowed — for two machines you need the advanced declaration API: `ApricityScreenNetworkHandler.openScreen(player, path, declarations, argsById)`. Each `ContainerDeclaration` carries its own id, type, capacity, and primary flag; parameters are passed through argsById (block_entity requires `x/y/z`, entity requires `entity_id`, saved_data optionally takes `data_name`). The HTML container ids must match the declaration ids. This is meant for Java mod code; KubeJS scripts generally won't need it.

Legacy entry points (`openScreen`, etc.) still exist, but new code should not use them. Things like `ApricityUI.bind()` and `primarySavedData()` from old examples are no longer current API.

## The container Element

| Attribute | Purpose |
| --- | --- |
| `id` | ID for the DOM and menu layout; always write it explicitly and keep it consistent with the server-side declaration |
| `bind` | `player` / `saved_data` / `block_entity` / `entity`; expresses the type and participates in automatic slot generation |
| `size` | Desired number of generated slots |
| `primary` | Shift-click primary container (only effective on the low-level declaration path, see below) |
| `layout` | Only controls DOM layout; does not create a data source |

Containers have no title mechanism; the `title` attribute does not draw a title. Use a plain div for titles.

IDs may only contain lowercase letters, digits, and `_ . / -`. When the ID is missing or invalid, ids like `c0`, `c1` are generated in order. A container nested inside another container does not count as an independent server-side declaration.

**Automatic slots**: when a container has a non-empty `bind` (or `primary="true"`), contains no handwritten plain slots, and `size` is a positive integer (or `bind="player"`), the expander automatically generates slots with proper indexes. So an empty container with `bind="player"` directly gets 36 slots. As soon as you handwrite one slot, nothing is auto-filled — write them all or none.

## Data Sources

**Player inventory**: binds the real inventory, not a copy.

**SavedData**: a world-level persistent inventory stored in the overworld data storage; distinguished by container id under the same dataName. Changing capacity rebuilds the inventory — expanding preserves items, **shrinking physically truncates them**. It is world data, not a personal inventory; to isolate per player, embed the UUID into the data name yourself.

**Block entity**: takes the ITEM_HANDLER from `Direction.UP` first, then the directionless one. On open it checks that the chunk is loaded, the block entity exists, a capability is present, and the requested capacity is within bounds. If the block entity is removed or the player moves more than about 8 blocks from the block center while the menu is open, the menu closes.

**Entity**: resolves the capability by server-side entity ID, with the same liveness and roughly 8-block distance checks. Entity resolution always happens on the server; never use client coordinates or HTML attributes for permission checks.

## Slot Mapping

The `slot-index` in the HTML is a **local index within the container**. Its relation to the server-side global menu index is:

```text
global index = container's baseIndex + slot's local slot-index
```

`slot-index` takes priority over the legacy `index` attribute. When neither is written, the expander fills in the smallest unoccupied index and logs a warning — for real slots, write `slot-index` explicitly, or simply use an empty container with automatic generation.

Binding rules (SlotDataBinder scans every slot):

1. In a UI-only menu all slots are display-only;
2. In a real menu, a slot inside a container whose local index is valid and whose container id matches a server-side declaration binds a real slot;
3. Slots outside containers, slots that can't be mapped, and recipe-generated ones are all display-only.

Don't let two slots in the same container use the same `slot-index`; the later-scanned one overwrites the earlier one.

After binding, the framework syncs each HTML slot's coordinates, size, and disabled state to the Minecraft menu slot, and rebinds after `refresh()`.

## The slot Element

**Real slots** display the ItemStack from a data source and support clicks, drags, and shift-clicks following MC menu rules. **Display slots** parse items from their text content, are not connected to any data source, and suit use cases like encyclopedias, recipe previews, and decoration. The innerText of a real slot does not override the real item.

**Interaction control** (highest to lowest priority): recipe-generated slots are never interactive → CSS `--aui-slot-interactive` → HTML `interactive` → HTML `pointer` → real bindings are interactive by default. For display slots, explicitly writing `interactive="0" pointer="0"` is recommended to keep the semantics stable. `disabled="true"` likewise rejects menu operations.

**Render control**:

| Syntax | Purpose |
| --- | --- |
| `render="all" / "item" / "bg" / "none"` | Master switch |
| `render-bg` / `render-item` | Individual control |
| `--aui-slot-render-bg` / `--aui-slot-render-item` | CSS version, takes priority over HTML attributes |

**Size and appearance**: `size` (or `slot-size` / `--aui-slot-size`) controls the logical size and participates in hit-testing and item centering; `iconScale` (`--aui-slot-icon-scale`) scales the item; `zIndex` (`--aui-slot-z`) controls draw order.

**Display item expressions** (written in the slot's text):

```text
minecraft:diamond                              single item
#minecraft:planks                              item tag (up to 128 candidates)
minecraft:diamond_sword{Damage:12}             with NBT
{id:"minecraft:diamond",Count:1b}              full ItemStack NBT
minecraft:iron_ingot|minecraft:gold_ingot      multiple candidates separated by |
[{"item":"minecraft:oak_log"},...]             Ingredient JSON
```

Multiple candidates cycle by default; `cycle-interval="750"` sets the interval (default 1000ms, minimum 200ms), and `cycle="0"` disables cycling; cycling pauses on hover. Invalid expressions leave the slot empty and are logged.

**The repeat pitfall**: `repeat="9"` only participates in capacity inference; it does **not** clone one DOM slot into nine. For bulk slots, use an empty container with `size` and automatic generation.

## Player Inventory Preset

```html
<container id="player" bind="player" layout="preset:player"></container>
```

Empty container + preset = 36 slots arranged in the vanilla style: indexes 9-35 form three inventory rows on top, 0-8 the hotbar at the bottom. Handwriting slots works too (they must be direct children of the container with correct slot-index), but there's no reason to make life hard for yourself.

## recipe Recipe Preview

```html
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```

A preview generated client-side from the recipe manager; it is not a real input slot, occupies no menu slot, and holds no items. Supported types: `crafting_shaped`, `crafting_shapeless`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing`. The recipe ID is read from innerText.

When the type is missing, the recipe doesn't exist, or the type doesn't match, an error is logged and `data-recipe-error` is set on the element, which scripts can query directly.

## Lifecycle and Quick Move

Open flow: server resolves data sources and capacities → constructs the SlotLayout and menu → client creates the Document → the expander generates DOM slots → SlotDataBinder binds and syncs coordinates → afterwards clicks and drags all go through the MC menu.

After opening, changing `bind`, `size`, or `slot-index` on the client cannot alter the server-side binding. Slots are rebound after `refresh()`, so don't keep old slot objects. On close: the body receives unload, the Document is removed, and SavedData is marked dirty.

**Shift-click direction** is decided by primary: shift-clicking from the player inventory prioritizes the primary non-player container, and vice versa back to the player inventory. Primary is determined by the server-side binding order (or the advanced declaration's `ContainerDeclaration.primary`) — `primary="true"` in the HTML does **not** override the server-computed result under the recommended menu entry point.

## Viewport and Coordinates

Container pages use the same meta configuration as ordinary Screens (see the [ApricityScreen meta section](apricity-screen#page-meta-configuration)). Slot DOM coordinates are logical coordinates; the framework reads the viewport scale and syncs them to the menu — don't multiply by renderScale manually in scripts.

Also, `ApricityUI.getCurrentScreenDocument()` returning null for a container Screen is normal; it only recognizes ApricityScreen.

## Security

- Changing DOM attributes on the client cannot gain access to new data sources;
- Distance, capability, and stillValid checks all happen on the server;
- But distance checks only govern the menu lifecycle — **business permissions (whether this machine is yours) must be checked yourself before opening**.

## FAQ

**The page opened but all slots are empty**: did you use `screen(path)` instead of server-side `menu(...).bind(...)`? Do the container ids match the fixed IDs? Does the target have an ITEM_HANDLER?

**repeat didn't expand**: expected behavior; use an empty container + size.

**A slot is visible but not clickable**: is it inside a container? Is the id correct? Does slot-index exceed the capacity? Is it recipe-generated? Any `interactive="0"` / `pointer="0"` / `disabled` / an ancestor's `--aui-slot-interactive:0`?

**Block entity won't open**: is the chunk loaded? Does the block entity exist? Does it have a capability? Is the distance over 8 blocks? Did you request an oversized capacity?

**Inventory layout is messed up**: use `layout="preset:player"` with indexes 0-35; don't lay out a grid in natural order.

**Shift-click goes the wrong way**: primary is determined by server-side binding order; changing the HTML primary attribute has no effect.

**SavedData items disappeared**: did you change the capacity? Shrinking truncates.

## Performance Tips

- For the player inventory use an empty container + preset; don't handwrite 36 slots;
- Real containers should declare only the number of slots that need to be shown;
- Don't put high-frequency animations and cycling on every slot;
- Use CSS for bulk styling instead of per-slot script changes.
