# ApricityUI `container` / `slot` / `item` / `ingredient` Guide

Last updated: 2026-08-06

> This archived guide has been synchronized with the explicit content-node model. See [`docs/container.md`](../../../docs/container.md) for the complete current reference.

## 1. Overall Model

Container UI uses three layers:

- `<slot>`: background shell, geometry hit area, local index, and menu interaction capability.
- `<item>`: parsing, rendering, and tooltip state for one ItemStack.
- `<ingredient>`: a tag, pipe, or JSON candidate set that drives its controlled inner `<item>`.

Valid markup:

```html
<slot slot-index="0"><item>minecraft:diamond</item></slot>
<slot><ingredient>#minecraft:planks</ingredient></slot>
```

A `slot` may directly contain exactly one `item` or `ingredient`. The old `<slot>minecraft:diamond</slot>` syntax is unsupported; an empty Slot is normalized to `<item>minecraft:air</item>`.

## 2. `container` and Binding

`bind` identifies a source such as `player`, `saved_data`, `block_entity`, or `entity`. In a real menu, only a Slot inside a container with a mapped `slot-index` and a direct `item` child can bind a real Minecraft menu slot.

An `ingredient` is always display-only and never binds to or operates a real menu slot.

An empty `bind="player"` container creates 36 Slots. Other empty bound containers with `size` create their requested number of Slots. Auto-generated Slots include a direct `<item>minecraft:air</item>`.

## 3. `slot` Attributes

### 3.1 `slot-index` and `repeat`

- `slot-index` is the local slot index in its container.
- `repeat="36"` materializes 36 separate Slots during expansion, including the template Slot.
- Deep clones retain their item/ingredient subtree, remove cloned `id` and `repeat`, and receive consecutive indexes.

```html
<container bind="saved_data" size="9">
  <slot slot-index="0" repeat="9"><item>minecraft:air</item></slot>
</container>
```

### 3.2 `interactive`, `pointer`, and `disabled`

Slot interaction capability resolves in this order: recipe-generated Slot → `interactive` → `pointer` → CSS `--aui-slot-interactive` → binding default.

- `tooltip`: tooltip only.
- `slot`: menu operation only.
- `none`: neither tooltip nor menu operation.
- Unbound Slots default to `tooltip`; real bound Slots default to `tooltip,slot`.
- `disabled="true"` disables menu interaction and item display for a bound Slot.

The default CSS sets `pointer-events: none` on Slot. Real menu interaction still uses the Screen/Binder geometry path.

### 3.3 Rendering and Size

`render`, `render-bg`, `render-item`, `size`, `iconScale`, and `zIndex` belong to the Slot and are inherited by its nested Item.

```html
<slot class="icon-only" interactive="tooltip" render="item">
  <item>minecraft:emerald</item>
</slot>
```

## 4. `item` and `ingredient`

`item` supports one item ID, item ID plus NBT, or full ItemStack NBT:

```html
<slot><item>minecraft:diamond_sword{Damage:12}</item></slot>
<slot><item>{id:"minecraft:diamond",Count:12b}</item></slot>
```

`ingredient` supports tags, pipe-separated candidates, and Minecraft Ingredient JSON. It displays at most 128 deduplicated candidates:

```html
<slot><ingredient>#minecraft:planks</ingredient></slot>
<slot><ingredient cycle-interval="750">
  minecraft:iron_ingot|minecraft:gold_ingot|minecraft:copper_ingot
</ingredient></slot>
<slot><ingredient>[{"item":"minecraft:oak_log"},{"item":"minecraft:birch_log"}]</ingredient></slot>
```

Use `cycle="0"` to disable rotation. `cycle-interval` and `rotate-interval` are aliases; the default is 1000ms and the minimum is 200ms. Rotation attributes belong on `ingredient`.

To update display content from a script:

```javascript
var item = slot.querySelector("item");
if (item) item.innerText = "minecraft:diamond";
```

## 5. `recipe` Rules

`<recipe type="...">recipe_id</recipe>` creates non-interactive preview Slots. Outputs and air cells use item; inputs, fuel, templates, and additions use ingredient. They never consume real menu slots.

Supported types are `crafting_shaped`, `crafting_shapeless`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing`, and `fallback`.

## 6. Example

```html
<div class="demo-title">SavedData Storage</div>
<container primary="true" bind="saved_data" size="9">
  <slot repeat="9" slot-index="0"><item>minecraft:air</item></slot>
</container>

<div class="demo-title">Candidate Display</div>
<slot interactive="tooltip">
  <ingredient>minecraft:iron_ingot|minecraft:gold_ingot</ingredient>
</slot>

<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```
