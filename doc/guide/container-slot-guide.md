# ApricityUI `container` / `slot` / `recipe` 使用说明

最后更新：2026-03-07

## 1. 总体模型

当前统一语义如下：

- 只使用一个槽位标签：`<slot>`
- slot 是否可交互由以下机制决定（优先级从高到低）：
  - recipe 生成的槽位始终不可交互
  - CSS 自定义属性 `--aui-slot-interactive`
  - HTML 属性 `interactive`
  - 是否被数据源绑定（在 container 内且被 SlotView 注入时自动可交互）
- 在 container 内且被数据源绑定的 slot 自动获得交互能力，显示真实菜单物品
- 在 container 外或未被绑定的 slot 为展示用途，通过 innerText 设置展示物品
- `container` 使用 `layout` 描述槽位布局（仅布局，不负责一般性自动补齐实例）
- `container` 标题只从容器首个子元素文本读取（如 `div` / `span`）
- `recipe` 生成的槽位始终不可交互

## 2. `container` 属性

### 2.1 `bind`

- 绑定数据源（如 `player` / `saved_data` / `block_entity` / `entity`）

### 2.2 `layout`

- 槽位布局规则（网格或预设）
- 示例：
  - `layout="[27,3,9]"`
  - `layout="preset:player"`

说明：`layout` 仅影响布局，不会在普通场景自动创建缺失槽位实例。

### 2.3 标题规则

- 标题渲染位置：容器内部
- 标题来源规则：
  - 仅读取容器第一个子元素（元素节点）的文本
  - 首个子元素缺失或文本为空时，不渲染标题区域且不保留占位

示例（首子元素作为标题）：
```html
<container primary="true" bind="player" layout="preset:player">
  <div class="demo-title">我的标题（跟随容器布局）</div>
  <slot slot-index="0" repeat="36"></slot>
</container>
```

## 3. `slot` 属性

### 3.1 `repeat`

- 批量生成，总数语义
- `repeat="36"` 表示总共 36 个槽位（模板本身算第 0 个）

### 3.2 `slot-index`

- 本地槽位索引
- 在 container 内用于指定绑定到数据源的哪个位置

### 3.3 `interactive`

- 显式控制槽位是否可交互
- 值：`true` / `false`（或等价的 `1` / `0`、`yes` / `no`）
- 未声明时的默认行为：
  - 被数据源绑定的 slot → 可交互
  - 未绑定的 slot → 不可交互
  - recipe 生成的 slot → 始终不可交互
- 也可通过 CSS 自定义属性 `--aui-slot-interactive` 控制

### 3.4 `disabled`

- 禁用交互（优先级高于 `interactive`）

### 3.5 展示物品属性

- `innerText`：物品字面量（仅此入口）
  - 支持物品 id：`minecraft:diamond`
  - 支持标签：`#minecraft:planks`
  - 支持 id+NBT：`minecraft:diamond_sword{Damage:12}`
  - 支持 ItemStack NBT：`{id:"minecraft:diamond",Count:1b,tag:{display:{Name:'{"text":"展示"}'}}}`
  - 支持管道分隔多候选：`minecraft:iron_ingot|minecraft:gold_ingot|minecraft:copper_ingot`
  - 支持 JSON Ingredient 数组：`[{"item":"minecraft:oak_log"},{"item":"minecraft:birch_log"}]`
- `cycle-interval`（别名 `rotate-interval`）：标签候选轮播间隔（ms），默认 1000ms，最小 200ms
- `cycle`：是否启用轮播（`true` / `false`），默认 `true`

### 3.6 渲染控制

- `render`：控制渲染内容
  - `all`（默认）：渲染背景 + 物品
  - `item`：仅渲染物品
  - `bg`：仅渲染背景
  - `none`：不渲染
- `render-bg` / `render-item`：单独控制背景/物品渲染
- CSS 自定义属性：`--aui-slot-render-bg`、`--aui-slot-render-item`

### 3.7 尺寸与样式

- `size`（别名 `slot-size`）：槽位像素尺寸
- `iconScale`：物品图标缩放比例
- `padding`：物品内边距
- CSS 自定义属性：`--aui-slot-size`、`--aui-slot-icon-scale`、`--aui-slot-padding`
- CSS（`position/top/left/...`）可用于手动布局

## 4. 玩家容器默认 36 格

当 `container.bind="player"` 且容器内没有任何槽位时，系统会隐式注入玩家背包槽位：

- `inv`：27 格（3x9）
- `hotbar`：9 格（1x9）
- 默认间距：`4px`

若你已经显式声明槽位，则不会触发上述隐式注入。

## 5. `recipe` 规则

- `<recipe>` 可独立用于普通 HTML 展示
- 在容器内可放多个 `<recipe>`，每个 recipe 独立子布局
- recipe 生成槽位固定不可交互，不参与真实菜单绑定
- 语法为：`<recipe type="...">recipe_id</recipe>`
- `type` 为必填且严格校验，推荐值：
  - `crafting_shaped`
  - `crafting_shapeless`
  - `smelting` / `blasting` / `smoking` / `campfire_cooking`
  - `stonecutting`
  - `smithing`

## 6. 示例与触发入口

- `minecraft:diamond` -> `run/apricity/test/index.html`
- `minecraft:emerald` -> `run/apricity/test/saveddata_player.html`（saveddata + playerinv）
- `minecraft:amethyst_shard` -> `run/apricity/test/virtual_container.html`（虚拟容器）
- `minecraft:nether_star` -> `run/apricity/test/recipe_showcase.html`（recipe 展示）

示例片段（saveddata + playerinv）：

```html
<container primary="true" bind="saved_data" layout="[9,3,3]">
  <div class="demo-title">SavedData 仓库（9 格，primary）</div>
  <slot repeat="9" slot-index="0"></slot>
</container>

<container bind="player" layout="preset:player">
  <div class="demo-title">PlayerInv（36 格）</div>
  <slot repeat="36" slot-index="0"></slot>
</container>

<slot>{id:"minecraft:diamond",Count:12b,tag:{display:{Name:'{"text":"示例物品"}'}}}</slot>
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```
