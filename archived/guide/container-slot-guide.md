# ApricityUI `container` / `slot` / `item` / `ingredient` 使用说明

最后更新：2026-08-06

> 此归档指南已同步当前的显式内容节点模型；完整的中文说明见 [`docs/container.md`](../../docs/container.md)。

## 1. 总体模型

容器 UI 使用三层结构：

- `<slot>`：背景壳、几何命中、本地索引和菜单交互能力。
- `<item>`：一个 ItemStack 的文本解析、渲染和 tooltip 状态。
- `<ingredient>`：标签、管道或 JSON 候选集合；它会驱动内部受控的 `<item>` 轮播显示。

合法模板示例：

```html
<slot slot-index="0"><item>minecraft:diamond</item></slot>
<slot><ingredient>#minecraft:planks</ingredient></slot>
```

`slot` 只能直接包含一个 `item` 或 `ingredient`。旧语法 `<slot>minecraft:diamond</slot>` 已不支持；空 slot 会规范化为 `<item>minecraft:air</item>`。

## 2. `container` 和绑定

`bind` 指向 `player`、`saved_data`、`block_entity` 或 `entity` 等数据源。真实菜单中，只有 container 内、具有可映射 `slot-index` 且直接内容为 `item` 的 slot 才会绑定真实 Minecraft 菜单槽位。

`ingredient` 始终是展示内容，绝不会绑定或操作真实菜单槽位。

空的 `bind="player"` container 会自动生成 36 个 Slot；其他带 `size` 的空绑定容器会自动生成对应数量的 Slot。自动生成的 Slot 都有直接 `<item>minecraft:air</item>` 内容。

## 3. `slot` 属性

### 3.1 `slot-index` 和 `repeat`

- `slot-index` 是 container 内的本地槽位索引。
- `repeat="36"` 会在展开阶段物化为 36 个独立的 Slot，模板自身计为第一个。
- 深克隆会保留 item/ingredient 子树，移除克隆的 `id` 和 `repeat`，并补全连续索引。

```html
<container bind="saved_data" size="9">
  <slot slot-index="0" repeat="9"><item>minecraft:air</item></slot>
</container>
```

### 3.2 `interactive`、`pointer` 和 `disabled`

Slot 的交互能力优先级为：recipe 生成槽位 → `interactive` → `pointer` → CSS `--aui-slot-interactive` → 绑定默认值。

- `tooltip`：只显示 tooltip。
- `slot`：只允许菜单操作。
- `none`：禁用 tooltip 和菜单操作。
- 未绑定 Slot 默认仅为 `tooltip`；真实绑定 Slot 默认是 `tooltip,slot`。
- `disabled="true"` 会禁用绑定 Slot 的菜单操作和物品显示。

默认 CSS 将 `slot` 设为 `pointer-events: none`；真实菜单槽位依然由 Screen/Binder 的 Slot 几何命中路径处理。

### 3.3 渲染和尺寸

`render`、`render-bg`、`render-item`、`size`、`iconScale` 和 `zIndex` 均写在 Slot 上，嵌套 Item 会继承它们。

```html
<slot class="icon-only" interactive="tooltip" render="item">
  <item>minecraft:emerald</item>
</slot>
```

## 4. `item` 与 `ingredient`

`item` 支持单物品 ID、物品 ID + NBT 和完整 ItemStack NBT：

```html
<slot><item>minecraft:diamond_sword{Damage:12}</item></slot>
<slot><item>{id:"minecraft:diamond",Count:12b}</item></slot>
```

`ingredient` 支持标签、管道候选和 Minecraft Ingredient JSON，最多展示 128 个去重候选：

```html
<slot><ingredient>#minecraft:planks</ingredient></slot>
<slot><ingredient cycle-interval="750">
  minecraft:iron_ingot|minecraft:gold_ingot|minecraft:copper_ingot
</ingredient></slot>
<slot><ingredient>[{"item":"minecraft:oak_log"},{"item":"minecraft:birch_log"}]</ingredient></slot>
```

`cycle="0"` 可关闭轮播；`cycle-interval` 与 `rotate-interval` 为同义属性，默认 1000ms、最小 200ms。轮播属性应写在 `ingredient` 上。

脚本修改展示内容时使用：

```javascript
var item = slot.querySelector("item");
if (item) item.innerText = "minecraft:diamond";
```

## 5. `recipe` 规则

`<recipe type="...">recipe_id</recipe>` 会生成不可交互的预览 Slot：输出和空气格使用 item，输入、燃料、模板和附加材料使用 ingredient。它们不占用真实菜单槽位。

支持 `crafting_shaped`、`crafting_shapeless`、`smelting`、`blasting`、`smoking`、`campfire_cooking`、`stonecutting`、`smithing` 和 `fallback`。

## 6. 示例

```html
<div class="demo-title">SavedData 仓库</div>
<container primary="true" bind="saved_data" size="9">
  <slot repeat="9" slot-index="0"><item>minecraft:air</item></slot>
</container>

<div class="demo-title">候选展示</div>
<slot interactive="tooltip">
  <ingredient>minecraft:iron_ingot|minecraft:gold_ingot</ingredient>
</slot>

<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```
