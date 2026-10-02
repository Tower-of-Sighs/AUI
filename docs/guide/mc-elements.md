# MC 物品与配方元素

`<item>`、`<ingredient>` 和 `<recipe>` 是用于显示 Minecraft 物品与配方的扩展元素。它们可以放进 `<slot>`，也可以单独用于展示；它们本身不会创建可操作的菜单槽位。脱离 `<slot>` 使用时，`<item>` 默认是 `16×16`，`<ingredient>` 本身没有默认尺寸，需要自行设置宽高；`<recipe>` 使用网格排列生成的配方槽位。真实背包和机器槽位的绑定、点击与权限规则见[容器文档](container)。其他游戏资源元素见[扩展元素文档](extension-elements)。

## `<item>`：显示单个 ItemStack

把物品 ID 写在元素文本中：

```html
<slot><item>minecraft:diamond</item></slot>
```

`<item>` 解析一个物品栈，支持注册表物品 ID，以及 Minecraft 对应版本的 ItemStack SNBT 表达式。物品 ID 使用 `namespace:path`；复杂物品栈的字段和组件格式随 Minecraft 版本变化，应采用目标版本的 ItemStack 数据格式。空内容、`minecraft:air` 或无效表达式显示为空。

例如，`minecraft:diamond_sword{Damage:12}` 是传统 ItemStack NBT 写法的示例；在较新的版本中，物品属性可能序列化为组件，需按目标版本调整。

`<item>` 适合固定物品展示。需要候选轮播或物品标签展开时用 `<ingredient>`。

## `<ingredient>`：展示 Ingredient 候选

`<ingredient>` 的文本既可以是单个物品，也可以描述一组候选；框架会创建并更新内部 `<item>` 来绘制当前候选。常见写法：

```html
<!-- 固定展示一个物品 -->
<slot><ingredient>minecraft:iron_ingot</ingredient></slot>

<!-- 展开 Minecraft 物品标签中的候选 -->
<slot><ingredient>#minecraft:planks</ingredient></slot>

<!-- 直接列出候选 -->
<slot><ingredient>minecraft:iron_ingot|minecraft:gold_ingot</ingredient></slot>

<!-- 使用 Minecraft Ingredient JSON -->
<slot><ingredient>[{"item":"minecraft:oak_log"},{"tag":"minecraft:birch_logs"}]</ingredient></slot>
```

文本支持以下形式：

| 写法 | 含义 |
| --- | --- |
| `minecraft:diamond` | 单个物品 ID |
| `#namespace:item_tag` | 物品标签；标签成员按物品 ID 排序 |
| `item-expression-a|item-expression-b` | 多个物品表达式，可混合物品 ID 与标签 |
| Minecraft Ingredient JSON | 使用 Minecraft 的 Ingredient JSON 表达式，如 `{"item":"minecraft:apple"}` 或 `{"tag":"minecraft:logs"}` |
| ItemStack SNBT | 一个物品栈；字段格式按目标 Minecraft 版本 |

候选最多保留 128 个，重复项会合并。`#apricityui:furnace_fuels` 是内置的燃料候选标签，会按 Minecraft 燃烧时间收集物品。

有多个候选时默认轮播；鼠标悬停在 `<ingredient>` 或当前物品上时暂停。

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| `cycle` | 开启 | 设为 `false`、`0`、`no`、`off`、`disabled` 或 `none` 关闭轮播；也接受 `true`、`1`、`yes`、`on`、`enabled` |
| `cycle-interval` | `1000` | 正整数毫秒；最小 `200`。旧别名：`rotate-interval` |
| `--aui-ingredient-cycle` | 未设置 | CSS 轮播开关；也识别 `--aui-slot-cycle` |
| `--aui-ingredient-cycle-interval` | 未设置 | 正整数毫秒；也识别 `--aui-slot-cycle-interval` |

CSS 自定义属性优先于 HTML 属性。单候选不会轮播。无效表达式或没有匹配物品时显示空位。

## `<recipe>`：配方预览

`<recipe>` 按配方 ID 从当前世界的配方管理器生成只读预览：

```html
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
```

配方 ID 写在元素文本中，`type` 必须填写并与配方类别相符：

| `type` | 预览内容 |
| --- | --- |
| `crafting_shaped` | 3×3 合成输入和输出 |
| `crafting_shapeless` | 3×3 无序合成输入和输出 |
| `smelting`、`blasting`、`smoking`、`campfire_cooking` | 输入、燃料候选和输出 |
| `stonecutting` | 输入和最多三个匹配的切石输出 |
| `smithing` | 锻造模板、输入、添加物和输出 |
| `fallback` | 其他配方：最多八个输入和输出 |

预览生成的 `<slot>` 是展示用途，不占用菜单槽位，也不能点击或取放物品。它使用普通 `<slot>` 的 CSS 属性；配方本身默认使用 CSS Grid，间距、槽位尺寸和列数可通过 `--aui-recipe-gap`、`--aui-recipe-slot-size`、`--aui-recipe-columns` 调整。生成槽位带有 `aui-recipe-input`、`aui-recipe-output` 等角色类，可按角色覆写样式。

无效或缺失的 `type`、找不到配方、类型不匹配时会记录日志，并把错误写入 `data-recipe-error`；成功时该属性为空。布局类型写入 `data-recipe-layout`，可用于 CSS 选择器。当前没有可用世界或配方管理器时无法生成预览。

## 与 `<slot>` 的关系

展示槽位的物品内容应写成 `<slot>` 的直接子元素 `<item>` 或 `<ingredient>`。不要把表达式直接写在 `<slot>` 的文本节点里。槽位尺寸、背景、物品缩放、渲染开关和真实菜单交互见[容器文档的 slot 章节](container#slot-元素)。
