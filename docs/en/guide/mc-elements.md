# Minecraft Item and Recipe Elements

`<item>`, `<ingredient>`, and `<recipe>` are extension elements for displaying Minecraft items and recipes. They can be placed inside a `<slot>` or used on their own; they do not create interactive menu slots. Outside a `<slot>`, `<item>` defaults to `16×16`, while `<ingredient>` has no default size and needs explicit dimensions. `<recipe>` lays out its generated slots in a grid. For real inventory and machine slots, including binding, clicks, and permissions, see the [Container guide](container). Other game-resource elements are covered in the [Extension Elements guide](extension-elements).

## `<item>`: Display one ItemStack

Write the item expression as the element's text:

```html
<slot><item>minecraft:diamond</item></slot>
```

`<item>` parses one item stack. It accepts a registered item ID and Minecraft ItemStack SNBT expressions for the target game version. Use `namespace:path` for item IDs. Fields and components in a full item-stack expression vary by Minecraft version, so follow that version's ItemStack data format. Empty content, `minecraft:air`, and invalid expressions render empty.

For example, `minecraft:diamond_sword{Damage:12}` illustrates the traditional ItemStack NBT form; on newer versions, item properties may be serialized as components, so adjust the expression to the target version.

Use `<item>` for a fixed display. For candidate cycling or item-tag expansion, use `<ingredient>`.

## `<ingredient>`: Display Ingredient candidates

The text of `<ingredient>` can describe one item or a set of candidates. A controlled inner `<item>` is created and updated to draw the current candidate. Common forms:

```html
<!-- Display one fixed item -->
<slot><ingredient>minecraft:iron_ingot</ingredient></slot>

<!-- Expand candidates from a Minecraft item tag -->
<slot><ingredient>#minecraft:planks</ingredient></slot>

<!-- List candidates directly -->
<slot><ingredient>minecraft:iron_ingot|minecraft:gold_ingot</ingredient></slot>

<!-- Use Minecraft Ingredient JSON -->
<slot><ingredient>[{"item":"minecraft:oak_log"},{"tag":"minecraft:birch_logs"}]</ingredient></slot>
```

Supported text forms:

| Syntax | Meaning |
| --- | --- |
| `minecraft:diamond` | One item ID |
| `#namespace:item_tag` | An item tag; members are sorted by item ID |
| `item-expression-a|item-expression-b` | Multiple item expressions; item IDs and tags can be mixed |
| Minecraft Ingredient JSON | Minecraft's Ingredient JSON expression, such as `{"item":"minecraft:apple"}` or `{"tag":"minecraft:logs"}` |
| ItemStack SNBT | One item stack; fields follow the target Minecraft version |

Up to 128 candidates are kept, with duplicates removed. `#apricityui:furnace_fuels` is a built-in fuel candidate tag that collects items according to Minecraft burn time.

Multiple candidates cycle by default. Cycling pauses while the pointer is over the `<ingredient>` or its current item.

| Setting | Default | Description |
| --- | --- | --- |
| `cycle` | On | Set to `false`, `0`, `no`, `off`, `disabled`, or `none` to stop cycling; `true`, `1`, `yes`, `on`, and `enabled` are also accepted |
| `cycle-interval` | `1000` | Positive integer milliseconds; minimum `200`. Legacy alias: `rotate-interval` |
| `--aui-ingredient-cycle` | Unset | CSS cycle toggle; `--aui-slot-cycle` is also recognized |
| `--aui-ingredient-cycle-interval` | Unset | Positive integer milliseconds; `--aui-slot-cycle-interval` is also recognized |

CSS custom properties take precedence over HTML attributes. A single candidate does not cycle. Invalid expressions or expressions with no matching items render empty.

## `<recipe>`: Recipe preview

`<recipe>` creates a read-only preview by looking up the recipe ID in the current world's recipe manager:

```html
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```

Write the recipe ID as the element's text. `type` is required and must match the recipe category:

| `type` | Preview |
| --- | --- |
| `crafting_shaped` | 3×3 crafting inputs and output |
| `crafting_shapeless` | 3×3 shapeless crafting inputs and output |
| `smelting`, `blasting`, `smoking`, `campfire_cooking` | Input, fuel candidates, and output |
| `stonecutting` | Input and up to three matching stonecutter outputs |
| `smithing` | Smithing template, input, addition, and output |
| `fallback` | Other recipes: up to eight inputs and the output |

Generated `<slot>` elements are for display only: they occupy no menu slots and cannot be clicked or manipulated. They use the regular `<slot>` CSS properties. The recipe uses CSS Grid by default; adjust its gap, slot size, and column count with `--aui-recipe-gap`, `--aui-recipe-slot-size`, and `--aui-recipe-columns`. Generated slots carry role classes such as `aui-recipe-input` and `aui-recipe-output`, which can be styled separately.

If `type` is missing or invalid, the recipe cannot be found, or the type does not match, an error is logged and written to `data-recipe-error`; on success that attribute is empty. The layout type is written to `data-recipe-layout` and can be used in CSS selectors. A preview cannot be generated when no client world or recipe manager is available.

## Relationship to `<slot>`

Write display content as a direct `<slot>` child: `<item>` or `<ingredient>`. Do not put the item expression directly in the `<slot>` text node. For slot size, background, item scaling, render controls, and real menu interaction, see [the slot section of the Container guide](container#the-slot-element).
