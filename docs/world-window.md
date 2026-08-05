# WorldWindow 世界内窗口

WorldWindow 把 HTML Document 渲染成 Minecraft 世界里的一块平面：信息牌、机器外屏、实体头顶标签、漂浮提示。它不是 Screen 也不是 Overlay——有世界坐标、朝向、透视缩放、方块遮挡，交互靠准心射线。页面本身还是普通的 AUI 页面，DOM、事件、表单能力都能用。

## 最小示例

世界窗口推荐 `mode=fixed`，显式声明逻辑尺寸，否则浏览器常见的 1920 宽会直接变成一块巨大的世界面板：

```html
<meta name="aui-viewport" content="mode=fixed,width=240,height=96,scale=1">
<meta name="aui-font-mode" content="web">
<meta name="aui-mouse-events" content="intercept">
```

Java 创建（KubeJS 客户端脚本同 API，坐标直接传 x, y, z）：

```java
WorldWindow window = ApricityUI.createWorldWindow(
        "world/notice.html",
        new Vec3(10.5, 65.0, -4.0),
        32                          // 交互射线距离
);
window.setMaxDisplayDistance(64);   // 相机显示距离
window.setDepthTest(true);

ApricityUI.removeWorldWindow(window);   // 不用时移除，Document 一起销毁
```

`ApricityUI.createWorldWindow(...)` 会创建 Document **并注册**。手动 `new WorldWindow(...)` 只创建不注册，得自己调 `WorldWindow.addWindow(window)`——只调 `Document.createInWorld(path)` 更是只得到一个 Document，什么都不会显示。这是"资源存在但世界里没窗口"的标准答案。

这些是客户端 API。位置数据在服务端的话，先自己同步到客户端。

## 生命周期

- 刷新（END 重载）会重建 DOM、重跑脚本，但**保留 WorldWindow 实例**——位置、朝向、距离等设置不会清零；
- 旧 Element 引用刷新后失效，老规矩；
- 改位置、旋转、深度这些空间属性不需要重建 Document，直接 set 就行；
- 同一实例别重复 `addWindow`，会重复绘制、重复收事件。

## 尺寸：viewport、自动缩放、手动缩放

窗口逻辑尺寸来自 meta 里的 viewport（`getWidth()/getHeight()` 读的就是它），别用构造器传宽高——带 `width/height` 的旧构造器已过时，而且会覆盖 viewport 配置。

| viewport 模式 | 世界内适用性 |
| --- | --- |
| `fixed` | 首选，逻辑尺寸稳定，物理大小好控制 |
| `browser` / `window` | 默认宽度很大，世界平面会离谱地大 |
| `gui` | 随 GUI 设置变，只在兼容旧页面时用 |

**自动缩放**：没调过 `setScale` 时，框架根据相机投影和距离算一个保守的世界缩放并缓存，面板不会每帧跳大小。viewport 变了、`setPosition` 了会重新算。`getScale()` 在窗口首次成功渲染前可能返回回退值，别一创建就读。

**手动缩放**：`window.setScale(0.02f)` 表示 1 逻辑像素 = 0.02 世界单位，同时关闭自动缩放；`clearScaleOverride()` 恢复自动。它只改物理比例，不动 DOM 布局。

## 位置与朝向

位置是世界坐标，平面以该点为中心：

```java
window.setPosition(entity.position().add(0, entity.getBbHeight() + 0.25, 0));  // 实体头顶
```

旋转推荐 `setRotation(yaw, pitch, roll)`，单位度。**坑**：`setRotation(Vec3)` 的参数顺序是 `(pitch, yaw, roll)`，不是 yaw 在前——这是历史兼容。KubeJS 的三参重载绑定层已经帮你转好了，正常传 yaw, pitch, roll 即可。四元数用 `setOrientation(Quaternionf)`（会复制一份）。

`setFacing(true)` 每帧面向摄像机，适合标签；要固定朝向的牌子就别开。

## Follow 和 Facing

两个独立开关：

| 配置 | 效果 | 场景 |
| --- | --- | --- |
| 都关 | 位置固定、朝向固定 | 牌子、机器外屏 |
| 只 Follow | 位置沿视线方向部分跟随 | 想有点方向感又要易读的面板 |
| 只 Facing | 位置固定、始终面向你 | 固定点位标签 |
| 都开 | 部分跟随 + 面向你 | 实体头顶信息、漂浮提示 |

Follow 不是绑定到摄像机：它把基础位置投影到视线方向，再按 `followFactor`（0~1）靠拢。0.3 是实体标签的常用值。基础位置在摄像机身后时不会硬拉到身前，避免转身时面板穿脸。旧的 `FollowFacingWorldWindow` 子类已过时，就是普通窗口开这两个开关。

## 两个距离

| 参数 | 管什么 |
| --- | --- |
| `maxDistance`（构造参数 / `setMaxDistance`） | 准心/鼠标射线能摸到多远 |
| `maxDisplayDistance`（`setMaxDisplayDistance`） | 相机超过这个距离就不渲染、不命中 |

没设实例级显示距离时用全局配置 `config/apricityui-client.toml` 的 `[worldWindow] maxDisplayDistance`（默认 128，设 `2147483647` 表示不限）。`clearMaxDisplayDistanceOverride()` 恢复全局。开 Follow 时距离按本帧跟随位置算。

## 遮挡与 Z 冲突

`setDepthTest(true)`（默认）：方块能挡住窗口，墙后的窗口不渲染也不响应交互——命中时会从相机向命中点做方块可视性射线检查。关掉就是永远浮在世界表面的信息层，但仍受显示距离和视锥裁剪限制。

整个 Document 被裁剪在窗口矩形内，子元素的阴影、滤镜不会画出去。

多个窗口共面或贴着方块表面会 Z-fighting。先给窗口位置留点小偏移；还不行再调动态深度步进：

```java
window.setDynamicDepthStep(0.00035f, 0.003f, 2.0f, 32.0f);  // near步进, far步进, near距离, far距离
```

全局还有 `[worldWindow] depthOffsetScale`。默认优先，别一上来调大，否则窗口会相对世界表面明显漂移。

## 距离 LOD

三档精度加一个自动档：`FULL`（全画）、`REDUCED`（留文字和主要内容）、`MINIMAL`（只剩背景边框）、`AUTO`（按距离自动，默认）。

LOD 全局默认关（AUTO 等于 FULL）。开启：

```toml
[worldWindow]
lodEnabled = true
fullDetailDistance = 16
reducedDetailDistance = 48
```

16 格内 FULL，16~48 REDUCED，超过 48 MINIMAL；超过 `maxDisplayDistance` 直接不显示（不是降到 MINIMAL）。

单窗口覆盖：`window.setDisplayPrecisionDistances(16, 48)`，或强制档位 `window.setDisplayPrecision("reduced")`（字符串支持 auto/full/reduced/minimal）。设回 `AUTO` 会清掉实例阈值。

**LOD 只影响绘制**：布局、动画、事件、命中全都还在跑。它不是"禁用远处窗口"的机制——远处不想交互就用 `maxDisplayDistance` 砍掉。

## 输入：准心、事件、消费

每次渲染框架会保存当帧的投影矩阵，输入来了用同一组变换反投影到 Document 坐标再 hitTest——所以旋转、透视、跟随中的窗口命中都是准的。

**第一人称抓鼠标时，交互点是屏幕中心的准心**，不是 GLFW 虚拟光标。想让玩家点到面板，就把面板放在准心射线能够到的地方。

事件类型就是常见的那套（mousemove/down/up/click/dblclick/contextmenu/wheel/over/out/enter/leave + pointer 兼容）。`clientX/clientY` 已经反投影成 Document 逻辑坐标，**别再乘世界缩放、renderScale 或 devicePixelRatio**。

要消费原生输入（可点击的世界按钮通常要），HTML 里加 `<meta name="aui-mouse-events" content="intercept">`，规则见 [ApricityScreen 的 meta 章节](apricity-screen.md#页面-meta-配置)。

两个限制：

- 开着 Minecraft Screen 时，世界窗口的鼠标/滚轮分发被 Screen 输入优先级阻断。世界窗口不是 Overlay，`reloadPersistent` 也不能让它盖在 Screen 上；
- 多个窗口投影重叠时没有 topmost 排序，**每个命中的窗口都会收到事件**。设计上别让可交互区域重叠。

## 坐标转换 API

调试用，别自己造二维比例换算：

| 方法 | 作用 |
| --- | --- |
| `getDocumentPositionAtScreen(pos)` | GUI 坐标 → Document 坐标；未渲染、出界、超距、被挡、矩阵不可逆都返回 null |
| `projectDocumentPosition(pos)` | Document 坐标 → GUI 坐标（GUI-scaled，不是 framebuffer 像素） |
| `projectDocumentRect(x,y,w,h)` | Document 矩形 → 保守 GUI 包围盒（旋转后比实际大，不能当精确命中） |
| `getRealPos()` / `getRealPos(screenPos)` | 当前鼠标/准心映射到该 Document 的事件坐标，未命中返回 null |

## 动态更新

`window.document` 是公开字段，直接改 DOM：

```java
Element status = window.document.getElementById("status");
status.setTextContent("HP: " + health);
```

高频更新只改必要元素，别每 tick 重建 Document。

## 完整模式

**固定信息牌**：fixed viewport + `setRotation` 固定朝向 + 深度测试开 + 合适的显示距离。

**实体头顶标签**：位置放头顶、`setFollow(true)` + `setFollowFactor(0.3f)` + `setFacing(true)`；实体移动时在客户端 tick 更新位置，实体没了立刻 `removeWorldWindow`。

**大量窗口**：限数量、`maxDisplayDistance` 别给太大、开 LOD。LOD 只省绘制不省布局，远了就主动移除实例。

**调试**：游戏内命令 `/aui worldwindow` 会在准心前方创建测试窗口，可实时调距离、深度、LOD、缩放、Follow/Facing。另有验收页面：世界里放个名叫 `auitest` 的盔甲架，测试生成器会在它头顶建一个 Follow/Facing 窗口。

## 常见问题

**世界里没有窗口**：多半只调了 `Document.createInWorld` 或忘了 `addWindow`。

**面板太大/太小**：查 viewport meta 的逻辑宽高；是不是用了 browser/window 模式；是不是调过 `setScale`；是不是还在用旧的宽高构造器。

**被墙挡住/不响应**：深度测试默认开着。`setDepthTest(false)` 试试能交互了，说明就是遮挡。顺便分清 `maxDistance`（射线）和 `maxDisplayDistance`（显示）。

**鼠标和准心对不上**：第一人称认准心。事件坐标别乘缩放。

**旋转方向不对**：用 `setRotation(yaw, pitch, roll)`；用 Vec3 重载就记住顺序是 `(pitch, yaw, roll)`；开了 Facing 的话固定朝向本来就不生效。

**远处窗口变空白但逻辑还在**：LOD 的 MINIMAL 档。要全画就强制 FULL，要隐藏就调显示距离或移除——MINIMAL 不是隐藏机制。

**多个窗口同时响应点击**：没有 topmost 排序，错开可交互区域。

**贴面闪烁**：Z-fighting，先挪位置，再小幅调 `setDynamicDepthStep`。

## 性能建议

- 固定 viewport + 小逻辑尺寸，别把网页大页面直接糊进世界；
- 大量窗口务必配显示距离和 LOD；
- 高频数据只改文本/属性；
- 实体销毁、区块卸载就立刻移除窗口；
- 少用大面积透明、复杂滤镜、阴影和高频动画；
- 纯展示窗口不用开 intercept。
