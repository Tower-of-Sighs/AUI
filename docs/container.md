# 容器使用文档

容器页面有两个层次，缺一不可：

- HTML 里的 `<container>` 和 `<slot>` 负责页面结构、槽位位置和样式；
- 服务端的 Menu 和数据源负责真实物品、点击、shift-click、权限和保存。

只有两边对上，槽位才能真正取放物品。只在客户端写一个 `<slot>` 不会变出真实槽位。

## 打开方式

| 入口 | 打开的是什么 | 真实槽位 |
| --- | --- | --- |
| `new ApricityScreen(path)`（客户端） | 纯 UI Screen | 没有 |
| `ApricityUI.screen(path)`（客户端） | UI-only 的容器 Screen | 没有——哪怕 HTML 里写了 `bind="player"` |
| `ApricityUI.menu(player, path).bind(...)`（服务端） | ApricityContainerScreen | 有 |

重点：**要真实容器就必须从服务端走 `menu(...).bind(...)`**。`screen(path)` 只是请求打开一个长得像容器的 UI 页面。

## 最小示例

`screens/inventory.html`：

```html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <style>
        body { margin: 0; padding: 12px; color: #e2e8f0; background: #1e293b; }
        container { display: grid; gap: 2px; width: max-content; }
    </style>
</head>
<body>
    <div>机器库存</div>
    <container id="saved_data" bind="saved_data" primary="true" size="9"></container>

    <div>玩家背包</div>
    <container id="player" bind="player" layout="preset:player"></container>
</body>
</html>
```

服务端打开（KubeJS 服务端脚本写法一样）：

```java
public static void open(ServerPlayer player) {
    ApricityUI.menu(player, "screens/inventory.html")
            .bind(binding -> binding
                    .saveddata("machine_data", 9)
                    .player());
}
```

`bind(...)` 立即解析数据源并打开菜单。注意 `saveddata("machine_data", 9)` 的第一个参数是 SavedData 的数据名，不是 HTML 的容器 id。

**容器 id 是固定的**，这是最容易踩的坑：

| 绑定方法 | HTML 必须用的 id |
| --- | --- |
| `player()` | `player` |
| `saveddata(...)` | `saved_data` |
| `blockEntity(pos)` | `block_entity` |
| `entity(entityId)` | `entity` |

HTML 写 `id="machine"` 而服务端调 `blockEntity(pos)`，两边对不上，槽位全部退化成展示槽位。

## 绑定 API

`ApricityUI.menu(player, path)` 返回 PendingMenu，调 `bind(...)` 才真正打开。回调里一个数据源都不声明的话会打开 UI-only 菜单——那还不如直接用 `screen(path)`。

| 方法 | 容量 | 说明 |
| --- | --- | --- |
| `player()` | 固定 36 | 玩家背包+快捷栏，本地索引 0-8 快捷栏、9-35 背包 |
| `saveddata()` / `saveddata(name)` / `saveddata(name, cap)` | 默认 9 | 世界级持久库存，数据名默认 `apricityui_data` |
| `blockEntity(pos)` / `blockEntity(pos, cap)` | capability 容量 | 方块实体的 Forge ITEM_HANDLER |
| `entity(id)` / `entity(id, cap)` | capability 容量 | 实体的 ITEM_HANDLER |

容量传 0 表示用数据源的完整容量（SavedData 至少 1 格）。

可以链式绑多个不同类型：`binding.blockEntity(pos).saveddata("cache", 9).player()`。第一个非玩家绑定自动成为 primary（决定 shift-click 方向），哪怕 `player()` 写在前面。

同类型只能绑一个——要两台机器就得用高级声明 API：`ApricityScreenNetworkHandler.openScreen(player, path, declarations, argsById)`，每个 `ContainerDeclaration` 自带 id、类型、容量和 primary，参数通过 argsById 传（block_entity 要 `x/y/z`，entity 要 `entity_id`，saved_data 可选 `data_name`）。HTML 的容器 id 必须和声明 id 一致。这是给 Java 模组代码用的，KubeJS 脚本一般用不到。

旧入口（`openScreen` 等）还在，新代码别用。旧示例里的 `ApricityUI.bind()`、`primarySavedData()` 之类已不是现行 API。

## container 元素

| 属性 | 作用 |
| --- | --- |
| `id` | DOM 和菜单布局的 ID，永远显式写，和服务端声明一致 |
| `bind` | `player` / `saved_data` / `block_entity` / `entity`，表达类型并参与自动槽位生成 |
| `size` | 希望生成的槽位数 |
| `primary` | shift-click 主容器（仅低层声明路径有效，见下文） |
| `layout` | 只控制 DOM 布局，不创建数据源 |

容器没有标题机制，`title` 属性不会画标题，要标题用普通 div。

ID 只允许小写字母、数字、`_ . / -`。缺 ID 或非法时会按顺序生成 `c0`、`c1`。嵌套在其他 container 里的容器不作为独立服务端声明。

**自动槽位**：容器有非空 `bind`（或 `primary="true"`）、内部没有手写的普通 slot、且 `size` 是正整数（或 `bind="player"`）时，扩展器自动生成槽位并写好索引。所以 `bind="player"` 的空容器直接得到 36 个槽位。一旦手写了一个 slot，就不会自动补齐——要么全写，要么全不写。

## 数据源

**玩家背包**：绑的是真库存，不是副本。

**SavedData**：世界级持久库存，存主世界数据存储；同一 dataName 下按容器 id 区分。改容量会重建库存——扩容保留物品，**缩容物理截断**。它是世界数据不是个人背包，要按玩家隔离就自己把 UUID 编进数据名。

**方块实体**：取 `Direction.UP` 然后无方向的 ITEM_HANDLER。打开时检查区块已加载、方块实体存在、有 capability、请求容量不超标；菜单存续期间方块实体被移除或玩家离方块中心超过约 8 格，菜单关闭。

**实体**：按服务端实体 ID 取 capability，同样检查存活和约 8 格距离。实体解析永远在服务端，别拿客户端坐标或 HTML 属性当权限判断。

## 槽位映射

HTML 里的 `slot-index` 是**容器内的本地索引**，和服务端全局菜单索引的关系是：

```text
全局索引 = 容器的 baseIndex + slot 的本地 slot-index
```

`slot-index` 优先于旧属性 `index`。都不写时扩展器按最小未占用索引补齐并记警告——真实槽位建议显式写 `slot-index`，或者干脆用空容器自动生成。

绑定规则（SlotDataBinder 扫描每个 slot）：

1. UI-only 菜单里所有 slot 都是展示型；
2. 真实菜单里，在 container 内、本地索引合法、容器 id 能对上服务端声明的 slot 绑定真实槽位；
3. container 外的、映射不上的、recipe 生成的——全是展示型。

同一个 container 里别让两个 slot 用同一个 `slot-index`，后扫描的会覆盖前一个。

绑定后框架会把 HTML slot 的坐标、尺寸、禁用状态同步给 Minecraft 菜单槽位，`refresh()` 后重新绑定。

## slot 元素

**真实槽位**显示数据源的 ItemStack，按 MC 菜单规则点击、拖拽、shift-click。**展示槽位**从文本内容解析物品，不连数据源，适合做图鉴、配方预览、装饰。真实槽位的 innerText 不会覆盖真实物品。

**交互控制**（优先级从高到低）：recipe 生成的永远不可交互 → CSS `--aui-slot-interactive` → HTML `interactive` → HTML `pointer` → 真实绑定默认可交互。展示槽位建议显式写 `interactive="0" pointer="0"` 让语义稳定。`disabled="true"` 同样拒绝菜单操作。

**渲染控制**：

| 写法 | 作用 |
| --- | --- |
| `render="all" / "item" / "bg" / "none"` | 整体开关 |
| `render-bg` / `render-item` | 单独控制 |
| `--aui-slot-render-bg` / `--aui-slot-render-item` | CSS 版，优先于 HTML 属性 |

**尺寸和外观**：`size`（或 `slot-size` / `--aui-slot-size`）控制逻辑尺寸，参与命中和物品居中；`iconScale`（`--aui-slot-icon-scale`）物品缩放；`zIndex`（`--aui-slot-z`）绘制层级。

**展示物品表达式**（写在 slot 文本里）：

```text
minecraft:diamond                              单个物品
#minecraft:planks                              物品标签（最多 128 个候选）
minecraft:diamond_sword{Damage:12}             带 NBT
{id:"minecraft:diamond",Count:1b}              完整 ItemStack NBT
minecraft:iron_ingot|minecraft:gold_ingot      竖线分隔多个候选
[{"item":"minecraft:oak_log"},...]             Ingredient JSON
```

多个候选默认轮播，`cycle-interval="750"` 设间隔（默认 1000ms，最小 200ms），`cycle="0"` 关闭；悬停时暂停轮播。无效表达式留空槽位并记日志。

**repeat 的坑**：`repeat="9"` 只参与容量推导，**不会**把一个 DOM slot 复制成九个。要批量槽位就用带 `size` 的空容器自动生成。

## 玩家背包预设

```html
<container id="player" bind="player" layout="preset:player"></container>
```

空容器 + 预设 = 36 个槽位排成原版样式：9-35 三行背包在上，0-8 快捷栏在底。手写 slot 也行（必须是 container 直接子元素、slot-index 正确），但没必要自找麻烦。

## recipe 配方预览

```html
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```

客户端根据配方管理器生成的预览，不是真实输入槽位，不占菜单槽位、不放物品。支持的 type：`crafting_shaped`、`crafting_shapeless`、`smelting`、`blasting`、`smoking`、`campfire_cooking`、`stonecutting`、`smithing`。配方 ID 从 innerText 读。

type 缺失、配方不存在或类型不匹配时会记日志并在元素上设 `data-recipe-error`，脚本里可以直接查这个属性。

## 生命周期与快速移动

打开流程：服务端解析数据源和容量 → 构造 SlotLayout 和菜单 → 客户端创建 Document → 扩展器生成 DOM 槽位 → SlotDataBinder 绑定并同步坐标 → 之后点击拖拽都走 MC 菜单。

打开后客户端改 `bind`、`size`、`slot-index` 都改变不了服务端绑定。`refresh()` 后槽位重新绑定，别保存旧 slot 对象。关闭时：body 收到 unload、Document 移除、SavedData 标 dirty。

**shift-click 方向**由 primary 决定：从玩家背包 shift-click 优先去 primary 非玩家容器，反之回玩家背包。primary 由服务端绑定顺序（或高级声明的 `ContainerDeclaration.primary`）决定——HTML 里的 `primary="true"` 在推荐的 menu 入口下**不会**覆盖服务端算出的结果。

## 视口与坐标

容器页面和普通 Screen 用同一套 meta（见 [ApricityScreen 的 meta 章节](apricity-screen.md#页面-meta-配置)）。槽位 DOM 坐标是逻辑坐标，框架读取 viewport 缩放后同步给菜单——别在脚本里手动乘 renderScale。

另外，`ApricityUI.getCurrentScreenDocument()` 对容器 Screen 返回 null 是正常的，它只认 ApricityScreen。

## 安全

- 客户端改 DOM 属性换不来新的数据源访问权；
- 距离、capability、stillValid 检查都在服务端；
- 但距离检查只管菜单生命周期，**业务权限（这机器是不是你的）要自己在打开前查**。

## 常见问题

**页面开了但槽位全空**：是不是用了 `screen(path)` 而不是服务端 `menu(...).bind(...)`？容器 id 和固定 ID 对上没？目标有没有 ITEM_HANDLER？

**repeat 没展开**：预期行为，用空容器 + size。

**槽位看得见点不动**：在 container 内吗？id 对吗？slot-index 超容量了吗？是不是 recipe 生成的？有没有 `interactive="0"` / `pointer="0"` / `disabled` / 祖先的 `--aui-slot-interactive:0`？

**方块实体打不开**：区块加载了吗？方块实体在吗？有 capability 吗？距离超 8 格了吗？容量写超了吗？

**背包位置乱**：用 `layout="preset:player"`，索引 0-35，别按自然顺序排网格。

**shift-click 方向不对**：primary 由服务端绑定顺序决定，改 HTML 的 primary 属性没用。

**SavedData 物品消失**：是不是改了容量？缩容会截断。

## 性能建议

- 玩家背包用空容器 + preset，别手写 36 个 slot；
- 真实容器只声明需要显示的槽位数；
- 别给每个槽位上高频动画和轮播；
- 批量样式用 CSS，别用脚本逐槽位改。
