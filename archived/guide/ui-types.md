## 快速开始

### UI 类型

晴雪UI 现在主要有三种常用玩法：

1. 叠加层，也就是 Overlay。
2. 界面，也就是 Screen。
3. 世界内影像，也就是把 UI 当成一块渲染在世界里的平面。

如果只看使用频率，前两个最常见，第三个更偏展示和特殊玩法。

---

### 一、Overlay

Overlay 是最轻的一种。

你只需要创建一个 Document，它就会直接进入绘制队列。

KJS 或客户端脚本常用入口：

```javascript
let doc = ApricityUI.createDocument("demo/index.html")
ApricityUI.removeDocument("demo/index.html")
```

Java 侧现在也可以直接走主类入口：

```java
ApricityUI.createDocument("demo/index.html");
ApricityUI.removeDocument("demo/index.html");
```

如果你更喜欢直接操作底层对象，也还是可以：

```java
Document.create("demo/index.html");
Document.remove("demo/index.html");
```

这种形式特别适合：

- HUD
- 状态提示
- 小地图边栏
- 临时浮层
- 调试面板

它的优点很明显：

1. 开关简单。
2. 可以同时存在多个。
3. 不需要切走当前界面。

当然，多了也会互相挡住，所以位置和层级还是得自己管。

---

### 二、Screen

Screen 就是更标准的“打开一个界面”。

如果你只是预览 UI，不需要真实容器绑定，那客户端直接开就行。

KJS：

```javascript
ApricityUI.openScreen("demo/index.html")
ApricityUI.closeScreen()
```

Java：

```java
ApricityUI.openScreen("demo/index.html");
ApricityUI.closeScreen();
```

这适合：

- 纯展示型面板
- 配置页
- 帮助页
- 假数据原型预览

但如果你要接真实槽位、真实容器、真实背包数据，就别只走客户端开屏了。

这种情况应该走服务端权威入口。容器信息由模板中的 `<container>` 元素声明，客户端 `openScreen` 会自动提取容器声明并发送到服务端：

```javascript
// 容器信息由模板中的 <container> 元素声明
// 客户端 openScreen 会自动提取并发送到服务端
ApricityUI.openScreen("demo/index.html")
```

Java 写法也是同一套接口：

```java
// 容器信息由模板中的 <container> 元素声明
// 客户端 openScreen 会自动提取并发送到服务端
ApricityUI.openScreen("demo/index.html");
```

这里有个关键点：

模板里的顶层 `container id`，必须和容器声明里的名字对上。

比如你写了：

```html
<container id="main"></container>
<container id="player"></container>
```

那绑定计划里就也得叫 `main`、`player`。

---

### Screen 里最常见的几种容器声明

容器声明由模板中的 `<container>` 元素属性（`id`、`bind`、`size`、`primary`）驱动。以下是常见的模板声明示例：

#### 1. 玩家背包

```html
<container id="player" bind="player"></container>
```

#### 2. SavedData 容器

```html
<container id="main" bind="saved_data" size="27" primary="true"></container>
<container id="player" bind="player"></container>
```

#### 3. 方块实体背包

```html
<container id="machine" bind="block_entity" size="9" primary="true"></container>
<container id="player" bind="player"></container>
```

#### 4. 实体背包

```html
<container id="entity_inv" bind="entity" size="27" primary="true"></container>
<container id="player" bind="player"></container>
```

要注意，实体绑定需要目标实体真的提供物品能力，不然开不起来。

---

### 三、世界内影像

这个模式适合把 UI 渲染成世界里的一块平面，比如：

- 牌子式信息板
- 机器外屏
- 世界提示面板
- 漂浮说明卡

这里不能只停在 `Document.createInWorld(path)` 这一步，因为它只是创建了“世界内 Document”。

真正要显示出来，还需要挂进 `WorldWindow`：

```java
WorldWindow window = ApricityUI.createWorldWindow("demo/world.html", position, 180, 100, 16);
window.setMaxDisplayDistance(32);

// 需要时移除
ApricityUI.removeWorldWindow(window);
```

如果你是在 KJS 客户端脚本里用，现在也有对应接口：

```javascript
let window = ApricityUI.createWorldWindow("demo/world.html", 0, 65, 0, 180, 100, 16)
window.setMaxDisplayDistance(32)

// 需要时移除
ApricityUI.removeWorldWindow(window)
```

这个平面支持：

1. 世界坐标定位。
2. 旋转。
3. 缩放。
4. 深度测试和遮挡。
5. 独立的最大显示距离。

如果没有调用 `setMaxDisplayDistance()`，窗口使用客户端配置文件
`config/apricityui-client.toml` 中 `[worldWindow] maxDisplayDistance` 的全局默认值；
默认值为 `128` 格。需要不限制距离时可设置为 `2147483647`。
实例调用 `setMaxDisplayDistance(distance)` 后会覆盖全局值。调用
`clearMaxDisplayDistanceOverride()` 可以恢复跟随全局配置。

默认缩放是 `0.02f`，也就是大约 50 像素对应 1 格方块。

LOD 默认关闭。需要全局启用时，在 `config/apricityui-client.toml` 的
`[worldWindow]` 中设置：

```toml
lodEnabled = true
fullDetailDistance = 16
reducedDetailDistance = 48
```

启用后，不超过 16 格为 `FULL`，超过 16 且不超过 48 格为 `REDUCED`，
超过 48 格为 `MINIMAL`。如果场景里只想让某个窗口启用 LOD，可以直接调用：

```java
window.setDisplayPrecisionDistances(16, 48);
```

也可以强制指定档位：

```java
window.setDisplayPrecision(WorldWindowDisplayPrecision.REDUCED);
// 或 window.setDisplayPrecision("minimal");
```

`REDUCED` 保留文字和主要内容，但跳过阴影、滤镜、背景滤镜和复杂裁剪；
`MINIMAL` 只保留基础背景和边框。该设置只影响渲染成本，不改变布局、动画、
命中检测或 DOM 事件；`maxDisplayDistance` 超出后仍会完全隐藏窗口。

所以你在设计世界内 UI 时，也别拿浏览器那种超大面板尺寸直接套。

---

### 四、FollowFacingWorldWindow

这个类是 `WorldWindow` 的一个特殊扩展。

它的作用不是单纯把一块平面钉死在世界里，而是让窗口在保留基准位置的同时，按一定强度跟随玩家视角，并且始终朝向摄像机。

比较适合这些场景：

- 实体头顶说明牌
- 漂浮信息卡
- 观察型测试面板
- 需要“始终能被看清”的世界内提示

Java：

```java
FollowFacingWorldWindow window = ApricityUI.createFollowFacingWorldWindow(
    "demo/follow.html",
    position,
    180,
    100,
    16,
    0.3f
);
```

KJS：

```javascript
let window = ApricityUI.createFollowFacingWorldWindow(
    "demo/follow.html",
    0, 65, 0,
    180, 100,
    16,
    0.3
)
```

最后一个参数 `followFactor` 会被限制在 `0.0 ~ 1.0`。

你可以把它大致理解成：

1. `0` 几乎不跟随，只是正常朝向玩家。
2. `1` 会完全追到视线投影位置，动感最强。
3. 常用区间一般在 `0.2 ~ 0.5`，比较自然。

---

### 什么时候选哪一种

如果你只是想在屏幕角落挂点东西，用 Overlay。

如果你要做一个正经可交互界面，用 Screen。

如果你想让 UI 出现在世界里，用 WorldWindow。

如果你还希望它跟着视角轻微漂移并始终面向玩家，就用 FollowFacingWorldWindow。

大部分项目里，Overlay 和 Screen 已经够用了。

---

### 容器语义的几个重点

这部分跟 UI 类型关系很大，尤其是 Screen。

1. `slot` 现在统一一个标签，容器内默认绑定真实槽位，容器外默认 virtual。
2. `bind="player"` 的容器在没有显式 bound 槽位时，会自动补玩家 36 格。
3. `recipe` 始终只负责展示，不参与真实容器绑定。
4. `container` 没有内建标题机制；标题请作为普通 DOM 节点自行编写和布局。

---

### 一个很实用的尺寸提醒

Minecraft 默认 GUI 缩放下，常见可用的 GUI 像素尺寸大约是 `427 * 240`。

它和浏览器里看到的同尺寸稿子比起来，会有明显“放大了”的感觉。

所以做 UI 时建议这样想：

1. 面板比浏览器稿再收一点。
2. 字号、内边距、圆角别放太大。
3. 小而清楚，通常比大而铺满更适合 Minecraft。

这条对 Overlay 和 Screen 都很有用。

---

### 最后一句

如果你还在犹豫从哪开始，最建议的顺序是：

1. 先用 Overlay 预览视觉。
2. 再把它改成 Screen。
3. 真要世界内展示，再接 WorldWindow。
4. 需要更强提示感时，再换成 FollowFacingWorldWindow。

这样迭代起来最省事。
