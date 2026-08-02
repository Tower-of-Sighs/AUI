# ApricityUI `container` / `slot` / `recipe` 使用说明

最后更新：2026-07-22

## 1. 总体模型

当前只使用一个槽位标签：`<slot>`。

- 运行时由 `SlotDataBinder` 将容器内按索引匹配的 `<slot>` 绑定到真实菜单槽位；`Slot.isBound()` 可用于判断这个内部绑定状态。
- 不在 `container` 内、没有匹配到菜单槽位、或由 `<recipe>` 生成的 `<slot>` 都是展示槽位，通过 `innerText` 解析展示物品。
- `isBound()` 不是模板可声明的 HTML 属性；它只反映真实菜单绑定，避免模板伪造绑定态。
- `container` 使用 `layout` 描述槽位布局；标题请作为普通 DOM 节点自行编写和布局。
- `recipe` 生成展示槽位，不参与真实菜单绑定。

## 2. `container` 属性

### 2.1 `bind`

- 绑定数据源，例如 `player`、`saved_data`、`block_entity` 或 `entity`。

### 2.2 `layout`

- 槽位布局规则（网格或预设）。
- 示例：
  - `layout="[27,3,9]"`
  - `layout="preset:player"`

说明：`layout` 仅影响布局，不会在普通场景自动创建缺失槽位实例。

### 2.3 标题

`container` 没有内建标题机制，不会读取 `title` 属性，也不会从首个子元素文本自动推断标题。
如果需要标题，请作为普通 DOM 节点自行编写和布局。

```html
<div class="demo-title">我的标题</div>
<container primary="true" bind="player" layout="preset:player">
  <slot slot-index="0" repeat="36"></slot>
</container>
```

## 3. `slot` 属性

### 3.1 `repeat`

- 批量生成，总数语义。
- `repeat="36"` 表示总共 36 个槽位（模板本身算第 0 个）。

### 3.2 `slot-index`

- 本地槽位索引。
- 在 `container` 内用于指定绑定到数据源的哪个位置。

### 3.3 `interactive`

`interactive` 独立控制 tooltip 和真实菜单槽位操作；它不控制物品或背景是否渲染。

- `tooltip`：允许显示当前物品 tooltip。
- `slot`：允许绑定菜单槽位的取放、拖拽、shift-click、热键等原版槽位操作。
- `tooltip slot`：同时启用两项能力。
- `none`：关闭两项能力。
- token 可用空格或逗号分隔，例如 `interactive="tooltip slot"`。
- 兼容旧布尔写法：`true` / `1` 等价 `tooltip slot`，`false` / `0` 等价 `none`；新模板请使用 token 写法。

未声明时：

- 已绑定 slot 默认 `tooltip slot`。
- 展示 slot 和 recipe slot 默认 `tooltip`，可查看物品信息但不能取放。

也可通过 CSS 自定义属性 `--aui-slot-interactive` 覆盖；优先级为 CSS 变量 > `interactive` 属性 > 绑定态默认值。

`slot` 不再把 `disabled` 当作物品交互开关。若需禁用能力，请写 `interactive="none"`。

### 3.4 展示物品属性

- `innerText`：物品字面量（唯一入口）
    - 物品 id：`minecraft:diamond`
    - 标签：`#minecraft:planks`
    - id+NBT：`minecraft:diamond_sword{Damage:12}`
    - ItemStack NBT：`{id:"minecraft:diamond",Count:1b,tag:{display:{Name:'{"text":"展示"}'}}}`
    - 管道分隔多候选：`minecraft:iron_ingot|minecraft:gold_ingot|minecraft:copper_ingot`
    - JSON Ingredient 数组：`[{"item":"minecraft:oak_log"},{"item":"minecraft:birch_log"}]`
- `cycle-interval`（别名 `rotate-interval`）：候选轮播间隔（ms），默认 1000ms，最小 200ms。
- `cycle`：是否启用轮播（`true` / `false`），默认 `true`。

### 3.5 渲染控制

`render` 只控制视觉层：

- `all`（默认）：渲染背景、物品模型和装饰。
- `item`：仅渲染物品模型、附魔层、数量、耐久和冷却装饰。
- `bg`：仅渲染背景。
- `none`：不渲染背景或物品。
- 缺省、空值或非法值均按 `all` 处理。

任何 `render` 值都不会改变 `interactive` 的 tooltip 或菜单取放能力。

标准 Item / BlockItem 由 AUI 内容节点提交模型顶点。Forge 自定义 renderer（ISTER/BEWLR）由 `ItemDrawer` 在相同 GUI
PoseStack、深度和裁剪上下文中委托绘制；动态标准模型只在当前帧缓存 mesh，避免跨帧复用陈旧 quad。模型解析或第三方 renderer
实际抛错时显示 AUI 紫黑棋盘格，不会回退到原版 `GuiGraphics.renderItem`。

Forge `ItemDecorator` 仍依赖原版 `GuiGraphics` 装饰路径，因此不会执行。Tooltip 本身仍由 Minecraft 绘制。

### 3.6 尺寸与样式

- `size`（别名 `slot-size`）：槽位像素尺寸。
- `iconScale`：物品图标缩放比例。
- CSS 自定义属性：`--aui-slot-size`、`--aui-slot-icon-scale`、`--aui-slot-interactive`。
- 物品纹理始终在 slot 内自动居中；若需拉开 slot 间距，请使用容器或 recipe 的 `gap`。
- CSS（`position/top/left/...`）可用于手动布局。

## 4. 玩家容器默认 36 格

当 `container.bind="player"` 且容器内没有任何槽位时，系统会隐式注入玩家背包槽位：

- `inv`：27 格（3×9）。
- `hotbar`：9 格（1×9）。
- 默认间距：`4px`。

若已显式声明槽位，则不会触发上述隐式注入。

## 5. `recipe` 规则

- `<recipe>` 可独立用于普通 HTML 展示。
- 在容器内可放多个 `<recipe>`，每个 recipe 独立子布局。
- recipe 生成槽位默认 `interactive="tooltip"`：可查看物品信息，但不参与真实菜单绑定或取放。
- 语法：`<recipe type="...">recipe_id</recipe>`。
- `type` 为必填且严格校验，推荐值：
  - `crafting_shaped`
  - `crafting_shapeless`
  - `smelting` / `blasting` / `smoking` / `campfire_cooking`
  - `stonecutting`
  - `smithing`

## 6. 示例与触发入口

- `minecraft:diamond` -> `run/apricity/test/index.html`
- `minecraft:emerald` -> `run/apricity/test/saveddata_player.html`（saveddata + playerinv）
- `minecraft:amethyst_shard` -> `run/apricity/test/virtual_container.html`（展示容器）
- `minecraft:nether_star` -> `run/apricity/test/recipe_showcase.html`（recipe 展示）

```html
<div class="demo-title">SavedData 仓库（9 格，primary）</div>
<container primary="true" bind="saved_data" layout="[9,3,3]">
  <slot repeat="9" slot-index="0"></slot>
</container>

<div class="demo-title">PlayerInv（36 格）</div>
<container bind="player" layout="preset:player">
  <slot repeat="36" slot-index="0"></slot>
</container>

<slot interactive="tooltip">{id:"minecraft:diamond",Count:12b,tag:{display:{Name:'{"text":"示例物品"}'}}}</slot>
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```
