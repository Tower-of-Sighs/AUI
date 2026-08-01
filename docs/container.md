# Apricity 容器使用文档

最后更新：2026-08-02

本文介绍 ApricityUI 中的 container、slot、recipe，以及服务端菜单绑定。容器页面同时包含两个层次：

- HTML 的 container 和 slot 负责页面结构、槽位位置、样式和展示。
- 服务端的 Menu 和数据源负责真实物品、点击操作、shift-click、权限检查和数据保存。

只有把这两个层次正确对应起来，槽位才会成为可以真正取放物品的 Minecraft 菜单槽位。

## 1. 容器和普通 Screen 的区别

ApricityUI 有三种容易混淆的页面类型：

| 类型 | 打开方式 | 真实 Minecraft 菜单 |
| --- | --- | --- |
| ApricityScreen | 客户端直接创建 ApricityScreen | 没有 |
| ApricityContainerScreen | 服务端调用 ApricityUI.menu(...).bind(...) | 可以有 |
| WorldWindow | 创建世界内 HTML 窗口 | 没有，除非自行实现业务同步 |

ApricityUI.menu(...) 打开的是 ApricityContainerScreen。它继承 Minecraft 的 AbstractContainerScreen，但不使用原版固定背景，而是把 HTML Document 渲染到菜单区域，并把 HTML slot 的坐标同步给 Minecraft 菜单槽位。

ApricityUI.screen(path) 和旧的 ApricityUI.openScreen(path) 只请求打开一个 UI-only 页面。即使 HTML 中写了 bind="player" 或 bind="saved_data"，也不会因此获得服务端真实库存。需要真实容器时，必须从服务端调用 menu(...).bind(...).

普通 ApricityScreen 的使用方式见 [ApricityScreen 使用文档](apricity-screen.md)。

## 2. 最小可运行示例

### 2.1 HTML 模板

把下面的文件保存为：

~~~text
src/main/resources/assets/apricityui/apricity/screens/inventory.html
~~~

这个示例使用一个 SavedData 仓库和玩家背包。两个 container 都没有直接写 slot，因此会由容器扩展器自动生成槽位。

~~~html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-viewport" content="mode=browser">
    <style>
        body {
            margin: 0;
            padding: 12px;
            color: #e2e8f0;
            background: #1e293b;
        }

        .page {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .panel {
            padding: 8px;
            background: #334155;
        }

        container {
            display: grid;
            gap: 2px;
            width: max-content;
        }
    </style>
</head>
<body>
    <main class="page">
        <div>机器库存</div>
        <container id="saved_data"
                   bind="saved_data"
                   primary="true"
                   size="9"></container>

        <div>玩家背包</div>
        <container id="player"
                   bind="player"
                   layout="preset:player"></container>
    </main>
</body>
</html>
~~~

这里的两个 id 不是任意文字。当前推荐的 BindingBuilder 使用固定容器 ID：

| Java/KubeJS 绑定方法 | HTML 容器 ID |
| --- | --- |
| player() | player |
| saveddata(...) | saved_data |
| blockEntity(...) | block_entity |
| entity(...) | entity |

如果 HTML 使用 id="machine"，而服务端调用 blockEntity(pos)，当前菜单布局中的 ID 仍然是 block_entity，二者不会绑定，HTML 槽位会退化成展示型槽位。

### 2.2 Java 服务端打开

~~~java
import com.sighs.apricityui.ApricityUI;
import net.minecraft.server.level.ServerPlayer;

public final class InventoryMenu {
    private InventoryMenu() {
    }

    public static void open(ServerPlayer player) {
        ApricityUI.menu(player, "screens/inventory.html")
                .bind(binding -> binding
                        .saveddata("machine_data", 9)
                        .player());
    }
}
~~~

bind(...) 会立即解析数据源并打开菜单。saveddata(...) 的第一个参数是 SavedData 的数据名，不是 HTML 的 container id。

### 2.3 KubeJS 服务端打开

KubeJS 服务端脚本使用相同的链式 API：

~~~javascript
ApricityUI.menu(player, "screens/inventory.html")
    .bind(binding => binding
        .saveddata("machine_data", 9)
        .player());
~~~

block entity 的示例：

~~~java
ApricityUI.menu(player, "screens/machine.html")
        .bind(binding -> binding
                .blockEntity(pos)
                .player());
~~~

对应 HTML 应使用 id="block_entity" 和 id="player"：

~~~html
<container id="block_entity"
           bind="block_entity"
           primary="true"
           size="9"></container>
<container id="player"
           bind="player"
           layout="preset:player"></container>
~~~

## 3. 推荐 API

### 3.1 PendingMenu

Java 和 KubeJS 的推荐入口都是：

~~~java
ApricityUI.menu(player, "screens/example.html")
        .bind(binding -> binding.player());
~~~

ApricityUI.menu(player, templatePath) 返回 PendingMenu。PendingMenu 只有在 bind(...) 被调用后才会把绑定关系交给服务端菜单处理器并打开 Screen。

如果回调不声明任何数据源，最终会打开 UI-only 菜单。这种用法通常没有意义；纯 UI 页面应使用 ApricityUI.screen(path)。

### 3.2 BindingBuilder 方法

| 方法 | 容器 ID | 默认或实际容量 | 说明 |
| --- | --- | ---: | --- |
| player() | player | 36 | 玩家背包和快捷栏 |
| saveddata() | saved_data | 9 | 数据名默认为 apricityui_data |
| saveddata(dataName) | saved_data | 9 | 使用自定义 SavedData 数据名 |
| saveddata(dataName, capacity) | saved_data | 指定容量 | 世界级持久库存 |
| blockEntity(pos) | block_entity | capability 的完整容量 | 从方块实体获取 Forge ITEM_HANDLER |
| blockEntity(pos, capacity) | block_entity | 不超过 capability 容量 | 只暴露前 capacity 个槽位 |
| entity(entityId) | entity | capability 的完整容量 | 从实体获取 Forge ITEM_HANDLER |
| entity(entityId, capacity) | entity | 不超过 capability 容量 | 只暴露前 capacity 个槽位 |

player() 始终声明 36 格。玩家库存的本地索引是 0 到 35，其中 0 到 8 是快捷栏，9 到 35 是普通背包。

容量为 0 对 blockEntity 和 entity 表示自动使用数据源的完整容量。SavedData 会把容量规范化为至少 1 格。

### 3.3 多个不同类型的容器

可以把不同类型的数据源链式绑定：

~~~java
ApricityUI.menu(player, "screens/combined.html")
        .bind(binding -> binding
                .blockEntity(machinePos)
                .saveddata("temporary_cache", 9)
                .player());
~~~

此时可用的 HTML ID 是 block_entity、saved_data 和 player。第一个非玩家绑定默认成为 primary；上例中 block_entity 是 primary。

如果先绑定 player，再绑定 SavedData，SavedData 仍会成为 primary：

~~~java
ApricityUI.menu(player, "screens/combined.html")
        .bind(binding -> binding
                .player()
                .saveddata("temporary_cache", 9));
~~~

当前 BindingBuilder 为每一种绑定类型使用固定 ID，因此不适合声明两个 blockEntity 或两个 entity。若需要两个同类型数据源，使用下面的高级服务端 API 为每个容器提供不同 ID 和参数。

### 3.4 高级声明 API

ApricityScreenNetworkHandler 提供了带声明和参数的服务端入口。它更接近内部实现，适合 Java 模组代码，不是普通 KubeJS 脚本的首选接口。

~~~java
import com.sighs.apricityui.instance.element.Container.ContainerDeclaration;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;

import java.util.List;
import java.util.Map;

List<ContainerDeclaration> declarations = List.of(
        new ContainerDeclaration(
                "machine_a",
                ContainerBindType.BLOCK_ENTITY,
                9,
                true
        ),
        new ContainerDeclaration(
                "machine_b",
                ContainerBindType.BLOCK_ENTITY,
                9,
                false
        )
);

Map<String, Map<String, String>> argsById = Map.of(
        "machine_a", Map.of(
                "x", String.valueOf(firstPos.getX()),
                "y", String.valueOf(firstPos.getY()),
                "z", String.valueOf(firstPos.getZ())
        ),
        "machine_b", Map.of(
                "x", String.valueOf(secondPos.getX()),
                "y", String.valueOf(secondPos.getY()),
                "z", String.valueOf(secondPos.getZ())
        )
);

ApricityScreenNetworkHandler.openScreen(
        player,
        "screens/combined.html",
        declarations,
        argsById
);
~~~

高级参数名如下：

| 绑定类型 | 必要参数 |
| --- | --- |
| saved_data | data_name，可省略，默认 apricityui_data |
| block_entity | x、y、z |
| entity | entity_id |
| player | 不需要参数 |

高级 API 的 HTML ID 必须与 ContainerDeclaration.id() 相同。使用它时，服务端声明的容量和 primary 才是最终值。

### 3.5 旧式兼容入口

以下入口仍然存在，但新代码应优先使用 menu(...).bind(...)：

| 入口 | 当前用途 |
| --- | --- |
| ApricityUI.openScreen(path) | 客户端旧名称，使用 ApricityUI.screen(path) 替代 |
| KubeJS ApricityUI.openScreen(player, path) | 服务端打开 UI-only 页面 |
| KubeJS ApricityUI.openScreen(player, path, declarations) | 兼容旧式服务端声明列表 |

旧式 KubeJS openScreen(player, path, declarations) 没有绑定参数映射。它可以处理 player 和默认 SavedData 声明，但 block_entity 和 entity 需要 x、y、z 或 entity_id，通常应改用 menu(...).bind(...)，或者在 Java 中调用带 argsById 的高级 API。

旧示例中可能出现 ApricityUI.bind()、primarySavedData() 或 containerIndexPlayer() 等写法。当前源码没有这些推荐入口，不要把它们当作现行 API。

## 4. container 元素

### 4.1 属性

| 属性 | 作用 |
| --- | --- |
| id | 容器在 DOM 和菜单布局中的 ID。推荐始终显式写出，并与服务端声明一致 |
| bind | player、saved_data、block_entity 或 entity。用于表达容器类型，也参与自动槽位生成 |
| size | 容器视图希望生成的槽位数量 |
| primary | 在低层声明路径中标记快捷移动物品的主容器 |
| layout | 只控制 DOM 槽位布局，不创建服务端数据源 |

容器没有内建标题机制。title 属性不会自动绘制标题，标题应使用普通的 div、span 或 heading 元素。

### 4.2 ID 和声明提取

容器 ID 只能使用小写字母、数字、下划线、点、斜线和短横线。缺少 ID 或 ID 非法时，Container.extractDeclarations(...) 会按照顶层容器顺序生成 c0、c1 等 ID。

Container.extractDeclarations(...) 只收集不嵌套在其他 container 中的顶层容器。嵌套容器里的 slot 仍然可以被 DOM 扫描，但嵌套容器不会作为独立服务端声明。

需要特别注意：当前 ApricityUI.screen(path) 发出的请求不携带 HTML 声明，服务端会创建 UI-only SlotLayout。HTML 中的 bind 属性不会自动把它变成真实库存。推荐的 menu(...).bind(...) 会在服务端显式建立 SlotLayout，再由客户端用 HTML ID 查找对应 container。

### 4.3 size 和自动槽位

以下条件同时满足时，容器扩展器会自动生成槽位：

1. container 是可绑定容器，即存在非空 bind，或者 primary 为 true。
2. 容器中没有属于它的普通 slot。recipe 内生成的 slot 不计入普通 slot。
3. size 是正整数，或者 bind="player" 且没有 size。

示例：

~~~html
<container id="saved_data" bind="saved_data" size="9"></container>
<container id="player" bind="player"></container>
~~~

第一个容器生成 9 个槽位，第二个容器生成 36 个玩家槽位。自动生成的槽位会同时写入 index 和 slot-index，并按照 0 开始的本地索引排列。

如果容器中已经有一个或多个普通 slot，即使 size 更大，也不会补齐剩余槽位。此时应显式写出全部槽位，或者删除已有 slot，改用空容器自动生成。

没有自定义网格列数时，扩展器会按槽位数量注入最多 9 列的默认网格布局。想要稳定布局时，应在 CSS 中显式设置列数和 gap。

### 4.4 layout

当前内置的玩家背包布局是：

~~~html
<container id="player"
           bind="player"
           layout="preset:player"></container>
~~~

layout="preset:player" 把玩家槽位排列为：

- slot-index 9 到 35：三行九列的普通背包。
- slot-index 0 到 8：最后一行的快捷栏。

其他 layout 值不会自动连接服务端数据源。普通网格、间距、列数和特殊位置应使用 CSS 或显式的 grid-column、grid-row。

## 5. 服务端数据源

### 5.1 玩家背包

player() 绑定 ServerPlayer 的 Inventory，容量固定为 36。它不是复制品，玩家在容器中取放的物品就是玩家自己的库存。

玩家槽位的服务端菜单索引通常位于所有非玩家容器之后，但 HTML 使用的是 container 内的本地 slot-index，不要把服务端全局索引写进 HTML。

### 5.2 SavedData

saveddata(dataName, capacity) 使用世界 SavedData 存储 ItemStackHandler：

- 数据保存到主世界的数据存储中。
- 同一个 dataName 下，使用 container ID 作为库存 key。
- 默认 dataName 是 apricityui_data。
- 容量改变时会按新容量重建库存。
- 新容量变小时，超出部分的物品会被截断并丢失。
- 关闭菜单或槽位内容变化时会标记 SavedData dirty。

SavedData 是世界级数据，不是按玩家自动隔离的个人背包。如果需要每个玩家独立库存，应在数据 key 或自定义数据源中加入玩家 UUID。

### 5.3 方块实体

blockEntity(pos) 从目标方块实体获取 Forge 的 ITEM_HANDLER capability。解析顺序是先尝试 Direction.UP，再尝试无方向 capability。

打开时服务端会检查：

- 坐标所在区块已加载。
- 目标方块实体存在。
- 方块实体提供 ITEM_HANDLER。
- 请求容量不超过 handler 的槽位数量。

菜单存续期间还会检查方块实体未被移除，且玩家到方块中心的距离平方不超过 64，也就是约 8 格。

### 5.4 实体

entity(entityId) 根据服务端实体 ID 获取实体的 ITEM_HANDLER capability。服务端会检查实体存在、具有 capability，且菜单存续期间实体仍然存活、玩家距离不超过约 8 格。

不要把客户端显示的实体坐标或 HTML 属性当作权限判断。真正的实体 ID 和数据源解析始终在服务端完成。

## 6. 槽位映射和菜单索引

### 6.1 本地索引和全局索引

HTML 中的 slot-index 是某个 container 内的本地索引：

~~~html
<container id="saved_data" bind="saved_data" size="9">
    <slot slot-index="0"></slot>
    <slot slot-index="1"></slot>
</container>
~~~

服务端会为每个容器分配一个全局起始索引。非玩家容器按照服务端声明顺序连续排列，玩家库存池追加在所有非玩家容器之后。映射关系是：

~~~text
全局菜单索引 = 容器的 baseIndex + slot 的本地 slot-index
~~~

因此，不要根据当前屏幕坐标或玩家槽位的全局位置填写 slot-index。

### 6.2 slot-index 和 index

slot-index 优先于 index：

~~~html
<slot slot-index="4"></slot>
<slot index="5"></slot>
~~~

没有任何索引时，ContainerExpander 会按照未占用的最小索引补齐 index 和 slot-index，并写入警告日志。为了让模板稳定，推荐所有真实容器槽位都显式指定 slot-index，或直接使用空容器自动生成。

如果本地索引超出服务端容器容量，或者 container ID 找不到对应的服务端声明，该 slot 不会绑定真实菜单槽位，而会被当作展示型 slot。

### 6.3 绑定规则

SlotDataBinder 扫描 Document 中的每个 slot：

1. UI-only 菜单中的所有 slot 都是展示型 slot。
2. 真实菜单中，container 内且有合法本地索引、同时 ID 能映射到菜单布局的 slot 会绑定真实菜单槽位。
3. container 外的 slot 是展示型 slot。
4. 没有合法映射的 slot 也是展示型 slot。
5. recipe 生成的 slot 永远是展示型 slot，不参与真实菜单绑定。

绑定后，系统会把 HTML slot 的位置、尺寸、禁用状态和物品渲染属性同步到 Minecraft 菜单槽位。Document.refresh() 重建 DOM 后，槽位绑定会重新执行。

### 6.4 重复的全局索引

同一个 container 内不要让多个 DOM slot 使用相同的 slot-index。一个全局菜单索引只能稳定对应一个 DOM slot；重复声明会造成后扫描的绑定覆盖前一个绑定，并可能出现两个元素显示或交互状态不一致。

## 7. slot 元素

### 7.1 真实槽位和展示槽位

真实绑定槽位显示数据源当前的 ItemStack，用户可以按照 Minecraft 菜单规则点击、拖拽、快速移动或合成。

展示槽位通过 slot 的文本内容解析物品，不连接真实数据源。展示槽位适合做物品图鉴、配方输入预览、说明面板和装饰性库存。

绑定态 slot 的 innerText 不会覆盖真实 ItemStack。如果想在真实库存旁边显示固定物品，应另外放置一个 container 外的展示型 slot。

### 7.2 交互控制

交互解析优先级如下：

1. recipe 生成的 slot 永远不可交互。
2. CSS 自定义属性 --aui-slot-interactive。
3. HTML 属性 interactive。
4. HTML 属性 pointer。
5. 绑定了真实 SlotView 的 slot 默认可以交互。
6. 没有真实绑定的 slot 不应依赖默认值来获得菜单操作。

常用写法：

~~~html
<slot slot-index="0" interactive="true"></slot>
<slot slot-index="1" interactive="false"></slot>
<slot slot-index="2" pointer="0"></slot>
<slot slot-index="3" disabled="true"></slot>
~~~

recipe slot、disabled slot 和 interactive=false 的 slot 都不会接受菜单指针操作。为了让虚拟展示槽位的语义稳定，建议显式写 interactive="0" pointer="0"。

默认 global.css 在 slot 上设置 pointer-events: none。真实菜单槽位的命中和点击由 ApricityContainerScreen、SlotDataBinder 和 Minecraft 菜单处理，不应把这个 CSS 属性与真实菜单槽位是否可操作直接等同。

### 7.3 渲染控制

| 属性 | 作用 |
| --- | --- |
| render="all" | 渲染背景和物品，默认行为 |
| render="item" | 只渲染物品 |
| render="bg" | 只渲染背景 |
| render="none" | 不渲染背景和物品 |
| render-bg | 单独控制背景 |
| render-item | 单独控制物品 |
| --aui-slot-render-bg | CSS 版本的背景开关 |
| --aui-slot-render-item | CSS 版本的物品开关 |

CSS 自定义属性优先于同类 HTML 属性。展示一个无底板的虚拟物品：

~~~html
<slot class="icon-only"
      interactive="0"
      cycle="0"
      render="item">minecraft:emerald</slot>
~~~

### 7.4 尺寸、图标和层级

| 属性 | CSS 属性 | 说明 |
| --- | --- | --- |
| size 或 slot-size | --aui-slot-size | 槽位的逻辑像素尺寸 |
| iconScale | --aui-slot-icon-scale | 物品图标缩放比例 |
| zIndex 或 z | --aui-slot-z | 物品绘制层级 |

尺寸会参与 Minecraft 菜单槽位的命中区域和物品居中计算。改变尺寸后，应同时检查网格 gap、viewport 缩放和相邻元素是否重叠。

### 7.5 展示物品表达式

展示型 slot 从直接文本节点或 innerText 读取表达式。当前支持：

~~~text
minecraft:diamond
#minecraft:planks
minecraft:diamond_sword{Damage:12}
{id:"minecraft:diamond",Count:1b,...}
minecraft:iron_ingot|minecraft:gold_ingot|minecraft:copper_ingot
[{"item":"minecraft:oak_log"},{"item":"minecraft:birch_log"}]
~~~

含义如下：

- minecraft:diamond 是单个物品 ID。
- #minecraft:planks 是 Item 标签，最多取 128 个候选物品。
- item ID 后的花括号是 ItemStack NBT。
- 以花括号开始的是完整 ItemStack NBT。
- 竖线分隔多个候选物品。
- JSON 数组使用 Minecraft Ingredient 格式。

无效的物品 ID、NBT、JSON 或标签不会创建物品，客户端会保留空槽位并输出相关日志。

### 7.6 轮播

多个候选物品默认轮播：

~~~html
<slot cycle-interval="750">
    minecraft:iron_ingot|minecraft:gold_ingot|minecraft:copper_ingot
</slot>
~~~

cycle="0" 可以关闭轮播。cycle-interval 和 rotate-interval 是同义属性，默认间隔为 1000 毫秒，实际最小间隔为 200 毫秒。鼠标悬停展示型 slot 时会暂时保持当前候选物品。

### 7.7 repeat 的当前边界

当前实现中，repeat 会参与容器容量推导，但不会把一个 DOM slot 复制成多个 DOM slot：

~~~html
<container id="saved_data" bind="saved_data" size="9">
    <slot slot-index="0" repeat="9"></slot>
</container>
~~~

上例仍然只有一个 DOM slot，实际最多只绑定本地索引 0。repeat="9" 会让容量推导看到索引范围，但不会自动生成索引 1 到 8 的可见槽位。

需要批量槽位时，优先使用空容器自动生成：

~~~html
<container id="saved_data" bind="saved_data" size="9"></container>
~~~

这样会得到 9 个独立的 DOM slot。玩家容器则可以使用没有子 slot 的 bind="player" 容器自动生成 36 个槽位。

## 8. 玩家背包预设

最简单的玩家背包页面：

~~~html
<container id="player"
           bind="player"
           layout="preset:player"></container>
~~~

当它没有子 slot 时，容器扩展器会生成 36 个槽位，并为每个槽位写入 0 到 35 的索引。内置 CSS 会把它们排成：

~~~text
9  10 11 12 13 14 15 16 17
18 19 20 21 22 23 24 25 26
27 28 29 30 31 32 33 34 35
0  1  2  3  4  5  6  7  8
~~~

如果使用显式 slot，slot 必须是 container 的直接子元素，且必须使用正确的 slot-index，才能匹配 preset:player 的 CSS 选择器：

~~~html
<container id="player" bind="player" layout="preset:player">
    <slot slot-index="9"></slot>
    <slot slot-index="10"></slot>
    <slot slot-index="0"></slot>
</container>
~~~

实际项目中通常不需要手写 36 个槽位，空容器预设更不容易漏索引。

## 9. recipe 配方预览

recipe 是客户端根据当前配方管理器生成的预览元素，不是服务端真实菜单输入槽位。

基本语法：

~~~html
<recipe type="crafting_shaped">minecraft:crafting_table</recipe>
~~~

支持的 type：

| type | 预览 |
| --- | --- |
| crafting_shaped | 有形合成，通常是 3x3 输入和输出 |
| crafting_shapeless | 无形合成输入和输出 |
| smelting | 熔炉输入、燃料和输出 |
| blasting | 高炉输入、燃料和输出 |
| smoking | 烟熏炉输入、燃料和输出 |
| campfire_cooking | 营火烹饪输入、燃料和输出 |
| stonecutting | 切石输入及可见输出列表 |
| smithing | 锻造模板、输入、材料和输出 |

recipe 的 ID 从 innerText 读取：

~~~html
<recipe type="smelting">minecraft:glass</recipe>
<recipe type="stonecutting">minecraft:stone_slab</recipe>
~~~

配方生成的 slot 会带有 recipe 元数据，并固定设置为：

- interactive=0
- pointer=0
- --aui-slot-interactive:0

即使 recipe 位于真实 container 内，也不会占用真实菜单槽位。recipe 只用于展示配方，不会把物品放入玩家背包或容器。

type 缺失、配方 ID 无效、配方不存在或声明的 type 与实际配方类型不匹配时，RecipeExpander 会写日志，并在 recipe 上设置 data-recipe-error。开发时可以直接检查该属性：

~~~javascript
const recipe = document.querySelector("#recipe");
if (recipe) {
    console.log(recipe.getAttribute("data-recipe-error"));
}
~~~

## 10. 容器 Screen 生命周期

服务端到客户端的大致流程如下：

~~~text
ApricityUI.menu(...).bind(...)
        |
        v
服务端解析数据源和容量
        |
        v
构造 SlotLayout 和 ApricityContainerMenu
        |
        v
NetworkHooks 打开 ApricityContainerScreen
        |
        v
客户端创建 Document
        |
        v
ContainerExpander / RecipeExpander 生成 DOM 槽位
        |
        v
SlotDataBinder 绑定 slot 并同步坐标
        |
        v
菜单点击、拖拽、快速移动由 Minecraft Menu 处理
~~~

打开时 HTML 只负责提供模板。真实数据源已经在服务端解析完成，客户端不能通过修改 container 的 bind、size 或 slot-index 来改变服务端绑定对象。

Document.refresh() 后，如果 refresh generation 或 slot 数量变化，SlotDataBinder 会重新扫描和绑定槽位。不要保存旧 DOM slot 对象并假设它们在 refresh 后仍然有效。

关闭菜单时：

- Screen 会触发 body 的 unload 生命周期事件。
- Document 被移除。
- SlotDataBinder 清理 SlotView。
- SavedData 数据源会标记 dirty。
- 方块实体或实体数据源不负责替业务代码关闭外部对象。

### 10.1 快速移动物品

primary 容器用于决定 shift-click 的主要转移方向：

- 从玩家背包 shift-click 时，优先移动到 primary 非玩家容器。
- 从 primary 容器或其他非玩家容器 shift-click 时，有玩家池则移动到玩家背包。
- 如果没有显式 primary，布局中的第一个容器会作为 fallback。

HTML 的 primary="true" 在推荐的 PendingMenu 入口中不会覆盖 BindingBuilder 已经计算出的 primary。要改变真实快速移动方向，应调整服务端绑定顺序，或使用高级声明 API 设置 ContainerDeclaration.primary。

### 10.2 槽位点击和 CSS

ApricityContainerScreen 会取消原版槽位背景和原版槽位绘制，改为：

1. 先绘制 Document。
2. 按绑定 slot 的 DOM 坐标绘制真实 ItemStack。
3. 绘制展示型 slot 的虚拟 ItemStack。
4. 根据 SlotDataBinder 的状态处理命中、hover、tooltip 和拖拽。

因此不要给真实槽位额外写一套独立的 leftPos、topPos 或 Minecraft 全局槽位坐标。HTML 的逻辑坐标会经过 aui-viewport 的缩放，再转换为菜单坐标。

## 11. 视口和缩放

容器页面与普通 Screen 使用同一套 aui-viewport 配置：

~~~html
<meta name="aui-viewport"
      content="mode=browser,zoom=1,min-zoom=0.75,max-zoom=2,zoom-step=0.1,user-scalable=true">
~~~

槽位的 DOM 位置是逻辑坐标，SlotDataBinder 会读取 Document 的 viewportScaleX 和 viewportScaleY 后同步到 Minecraft 菜单。通常不需要在 JavaScript 中手动乘 renderScale。

容器页面支持普通 Screen 的 viewport 模式、Ctrl 加滚轮缩放和 Ctrl+0 重置。具体模式和限制见 [ApricityScreen 使用文档中的 Viewport 章节](apricity-screen.md#6-viewport-配置)。

ApricityUIClientUtil.getCurrentScreenDocument() 只返回当前 ApricityScreen 绑定的 Document。ApricityContainerScreen 是独立的容器 Screen，不属于 ApricityScreen；在容器页面中使用该方法得到 null 是正常行为。

## 12. 安全和有效性

容器数据必须由服务端绑定，不能把 HTML 当作权限配置：

- 客户端可以修改 DOM 属性，但不能因此访问新的方块实体或实体。
- block_entity 和 entity 会在服务端解析目标并检查 capability。
- 方块实体必须仍存在且距离玩家不超过约 8 格。
- 实体必须仍存活且距离玩家不超过约 8 格。
- 菜单的 stillValid 失败后，Minecraft 会关闭菜单。
- 所有物品移动最终由服务端 Menu 和数据源执行。

业务代码仍应在打开菜单之前检查玩家权限、方块类型、方块实体所属关系和业务状态。距离检查只解决菜单生命周期安全，不替代业务权限检查。

## 13. 常见问题

### 页面打开了，但所有槽位都是空的

依次检查：

1. 是否使用了客户端 ApricityUI.screen(path)，而不是服务端 ApricityUI.menu(...).bind(...)。
2. HTML 的 container id 是否与 BindingBuilder 的固定 ID 一致。
3. container 是否有 size，或者是否是没有子 slot 的 player 容器。
4. 是否把展示型 slot 误认为真实菜单槽位。
5. 服务端目标是否没有 ITEM_HANDLER capability。

### 只有一个槽位，repeat 没有展开

这是当前实现的预期行为。repeat 不复制 DOM。删除子 slot，使用带 size 的空 container，或者显式写出多个不同 slot-index 的 slot。

### 槽位可见但不能点击

检查：

- slot 是否在 container 内。
- container id 是否匹配服务端声明。
- slot-index 是否在数据源容量范围内。
- slot 是否属于 recipe。
- 是否设置了 interactive="0"、pointer="0" 或 disabled="true"。
- 是否有祖先 CSS 继承 --aui-slot-interactive:0。

### 方块实体容器无法打开

检查目标区块是否加载、方块实体是否存在、是否暴露 Forge ITEM_HANDLER，以及玩家与方块中心距离是否不超过约 8 格。显式 capacity 也不能大于 capability 的实际槽位数。

### 玩家背包位置错乱

使用 layout="preset:player"，并确保槽位使用 0 到 35 的玩家本地索引。不要把玩家背包按 0 到 35 的自然顺序直接用普通网格排列后，再期待它自动变成原版背包布局。

### shift-click 方向不符合预期

primary 由服务端绑定顺序或高级 ContainerDeclaration.primary 决定，不由普通 HTML 的 primary 属性覆盖。检查是否绑定了 player 池，以及是否把希望接收物品的非玩家容器放在第一个非玩家绑定位置。

### SavedData 中的物品消失

检查是否更改了同一个 dataName 和容器 ID 的容量。SavedData 扩容可以保留原槽位，缩容会物理截断超出部分。

## 14. 性能建议

- 不要每帧调用 Document.refresh()。修改元素属性或文本后，让正常 dirty/layout 流程处理即可。
- 玩家背包优先使用空 container 的自动 36 槽位和 preset:player，避免手写重复结构。
- 真实容器只声明实际需要显示的槽位数量，避免把大量无用槽位映射到菜单。
- 不要在每个槽位上使用高频动画、轮播或复杂阴影。轮播展示应使用合理的 cycle-interval。
- 需要展示物品时优先使用少量独立的虚拟 slot，不要为每个说明文字创建完整 recipe 结构。
- 尽量使用 CSS 控制批量样式，减少脚本逐槽位反复修改并触发重新布局。
- 容器内的 slot 坐标会在渲染周期中同步到菜单，集中修改布局后再刷新比逐属性刷新更稳定。

## 15. 示例和源码

可直接参考的示例：

- run/apricity/test/saveddata_player.html：SavedData 和玩家背包页面。
- run/apricity/test/virtual_container.html：虚拟展示槽位、标签和轮播。
- run/apricity/test/recipe_showcase.html：recipe 预览和虚拟 slot。
- src/main/resources/assets/apricityui/apricity/tests/container-slot-recipe-test.html：自动补槽、玩家预设、slot 表达式和 recipe 回归页面。

核心源码：

- src/main/java/com/sighs/apricityui/instance/network/handler/PendingMenu.java：推荐菜单入口。
- src/main/java/com/sighs/apricityui/instance/network/handler/BindingBuilder.java：Java/KubeJS 绑定构建器。
- src/main/java/com/sighs/apricityui/instance/network/handler/ApricityScreenNetworkHandler.java：服务端数据源解析和菜单创建。
- src/main/java/com/sighs/apricityui/instance/ApricityContainerMenu.java：真实菜单槽位、快速移动和有效性检查。
- src/main/java/com/sighs/apricityui/instance/ApricityContainerScreen.java：Document 渲染、交互和槽位同步。
- src/main/java/com/sighs/apricityui/instance/screen/SlotDataBinder.java：DOM slot 与菜单 slot 的映射。
- src/main/java/com/sighs/apricityui/instance/dom/expander/ContainerExpander.java：自动槽位和索引规范化。
- src/main/java/com/sighs/apricityui/instance/dom/expander/RecipeExpander.java：recipe 预览槽位生成。
- src/main/java/com/sighs/apricityui/instance/element/Container.java：container 声明和容量推导。
- src/main/java/com/sighs/apricityui/instance/element/Slot.java：slot 属性、交互和展示物品解析。
