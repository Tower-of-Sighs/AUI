# ApricityUI `container` / `slot` / `recipe` 使用说明

最后更新：2026-02-18

## 1. 总体模型

当前统一语义如下：

- 只使用一个槽位标签：`<slot>`
- `slot` 通过 `mode` 分为：
  - `bound`：绑定真实菜单槽位
  - `virtual`：纯展示槽位
- `container` 使用 `layout` 描述槽位布局（仅布局，不负责一般性自动补齐实例）
- `container` 标题只从容器首个子元素文本读取（如 `div` / `span`）
- `recipe` 生成的槽位始终是 `virtual`

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
  <slot mode="bound" slot-index="0" repeat="36"></slot>
</container>
```

## 3. `slot` 属性

### 3.1 `mode`

- `bound|virtual`
- 未显式声明时：
  - 在 `container` 内默认 `bound`
  - 在 `container` 外默认 `virtual`

### 3.2 `repeat`

- 批量生成，总数语义
- `repeat="36"` 表示总共 36 个槽位（模板本身算第 0 个）

### 3.3 `bound` 常用属性

- `slot-index`：本地槽位索引
- `disabled`：禁用交互
- CSS（`position/top/left/...`）可用于手动布局

### 3.4 `virtual` 常用属性

- `innerText`：物品字面量（仅此入口）
  - 支持物品 id：`minecraft:diamond`
  - 支持标签：`#minecraft:planks`
  - 支持 id+NBT：`minecraft:diamond_sword{Damage:12}`
  - 支持 ItemStack NBT：`{id:"minecraft:diamond",Count:1b,tag:{display:{Name:'{"text":"展示"}'}}}`
- `rotate-interval`：标签候选轮播间隔（ms）

## 4. 玩家容器默认 36 格

当 `container.bind="player"` 且容器内没有任何 `bound` 槽位时，系统会隐式注入玩家背包槽位：

- `inv`：27 格（3x9）
- `hotbar`：9 格（1x9）
- 默认间距：`4px`

若你已经显式声明 `bound` 槽位，则不会触发上述隐式注入。

## 5. `recipe` 规则

- `<recipe>` 可独立用于普通 HTML 展示
- 在容器内可放多个 `<recipe>`，每个 recipe 独立子布局
- recipe 生成槽位固定为 `virtual`，不参与真实菜单绑定
- 语法为：`<recipe type=\"...\">recipe_id</recipe>`
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
  <slot mode="bound" repeat="9" slot-index="0"></slot>
</container>

<container bind="player" layout="preset:player">
  <div class="demo-title">PlayerInv（36 格）</div>
  <slot mode="bound" repeat="36" slot-index="0"></slot>
</container>

<slot mode="virtual">{id:"minecraft:diamond",Count:12b,tag:{display:{Name:'{"text":"示例物品"}'}}}</slot>
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```
