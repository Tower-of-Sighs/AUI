# WorldWindow 世界内窗口使用文档

最后更新：2026-08-02

`WorldWindow` 用于把 ApricityUI 的 HTML `Document` 渲染成 Minecraft 世界中的一块平面。它适合信息牌、机器外屏、实体头顶状态、漂浮提示、世界内调试面板等场景。

世界内窗口不是 `Screen`，也不是普通 Overlay：它有世界坐标、朝向、透视缩放、方块遮挡和基于准心的鼠标命中。页面仍然使用 ApricityUI 的 HTML/CSS/JavaScript 运行时，因此可以复用普通 Document 的 DOM、事件和表单能力。

本文描述当前实现。普通 Screen 的创建方式见 [ApricityScreen 使用文档](apricity-screen.md)，浏览器式缩放、文字复制和 Meta 的通用行为见 [浏览器辅助功能文档](browser-features.md)。

## 1. 和其他页面类型的区别

| 类型 | 渲染位置 | 交互坐标 | 典型用途 |
| --- | --- | --- | --- |
| `ApricityScreen` | Minecraft Screen | GUI 坐标 | 设置页、纯 UI、调试工具 |
| Overlay Document | 屏幕 Overlay | GUI 坐标 | HUD、Toast、全局浮层 |
| `ApricityContainerScreen` | Minecraft 菜单 | GUI 坐标 | 真实容器、槽位和菜单操作 |
| `WorldWindow` | Minecraft 世界平面 | 世界平面投影后的 Document 坐标 | 信息牌、机器显示、实体标签 |

`WorldWindow` 的 Document 会被标记为 `inWorld=true`，不会进入普通 Overlay 的全局绘制流程。真正的世界渲染由 `WorldWindow` 在世界渲染阶段执行；只调用 `Document.createInWorld(path)` 只会得到一个 Document，不会自动显示平面。

## 2. 最小可运行示例

### 2.1 HTML 模板

把资源保存到：

~~~text
src/main/resources/assets/apricityui/apricity/world/notice.html
~~~

世界窗口推荐使用 `mode=fixed`，显式声明逻辑宽高。这样页面尺寸和世界平面的布局尺寸稳定，不会把浏览器页面常见的 1920 CSS 像素直接变成一个过大的世界面板。

~~~html
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="aui-viewport"
          content="mode=fixed,width=240,height=96,scale=1">
    <meta name="aui-font-mode" content="web">
    <meta name="aui-mouse-events" content="intercept">
    <style>
        html,
        body {
            width: 100%;
            height: 100%;
            margin: 0;
            padding: 0;
            box-sizing: border-box;
            background: rgba(12, 24, 34, 0.86);
            color: #e7f2ff;
            font-family: "Consolas", "Courier New", monospace;
        }

        body {
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 8px;
        }

        .panel {
            width: 100%;
            padding: 8px;
            border: 1px solid #6fb4d6;
            background: rgba(20, 42, 58, 0.9);
        }

        .title {
            color: #f1fbff;
            font-size: 15px;
            font-weight: 700;
        }

        .hint {
            margin-top: 6px;
            color: #c8e3f5;
            font-size: 10px;
        }

        button {
            margin-top: 8px;
            padding: 4px 8px;
        }
    </style>
</head>
<body>
    <div class="panel">
        <div class="title">世界内面板</div>
        <div id="status" class="hint">等待交互</div>
        <button id="action" type="button">点击测试</button>
    </div>

    <script>
        document.getElementById("action").addEventListener("click", function () {
            document.getElementById("status").textContent = "已点击 " + Date.now();
        });
    </script>
</body>
</html>
~~~

### 2.2 Java 创建窗口

`ApricityUI.createWorldWindow(...)` 会创建 Document 并自动注册 WorldWindow：

~~~java
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.instance.WorldWindow;
import net.minecraft.world.phys.Vec3;

WorldWindow window = ApricityUI.createWorldWindow(
        "world/notice.html",
        new Vec3(10.5, 65.0, -4.0),
        32
);

window.setMaxDisplayDistance(64);
window.setDepthTest(true);

// 不再需要时移除，同时移除它绑定的 Document。
ApricityUI.removeWorldWindow(window);
~~~

构造器 `new WorldWindow(...)` 不会自动注册。手动构造时必须显式调用：

~~~java
WorldWindow window = new WorldWindow("world/notice.html", position, 32);
WorldWindow.addWindow(window);
~~~

通常优先使用 `ApricityUI.createWorldWindow(...)`，因为它会完成创建和注册的完整流程。

### 2.3 客户端 KubeJS

WorldWindow 的 KubeJS 客户端绑定名称是 `ApricityUI`。坐标使用世界坐标，资源路径仍然是逻辑路径，不要写 `assets/apricityui/apricity/` 前缀：

~~~javascript
let window = ApricityUI.createWorldWindow(
    "world/notice.html",
    10.5, 65.0, -4.0,
    32
);

window.setMaxDisplayDistance(64);
window.setDepthTest(true);

// 需要清理时调用
ApricityUI.removeWorldWindow(window);
~~~

这些接口是客户端 API，应放在客户端脚本中使用。服务端不能直接创建客户端 WorldWindow；如果位置来自服务端状态，需要先通过网络或同步数据把位置传到客户端。

## 3. 资源和 Document 生命周期

### 3.1 路径和创建失败

`WorldWindow` 内部调用 `Document.createInWorld(path)`：

1. 按逻辑路径读取 HTML 模板；
2. 创建 `inWorld=true` 的 Document；
3. 应用 `aui-viewport` 和 `aui-font-mode`；
4. 解析 HTML、CSS 和 JavaScript；
5. 注册生命周期事件；
6. 由 WorldWindow 在世界渲染阶段绘制。

资源不存在时，Document 创建会返回 `null` 并输出错误日志。创建窗口前应确认资源位于正确的资源包路径。不要把物理文件路径、`file:` URL 或 `assets/apricityui/apricity/` 前缀传给 API。

### 3.2 注册和移除

创建入口的注册规则如下：

| 操作 | 是否创建 Document | 是否加入 WorldWindow 全局列表 |
| --- | --- | --- |
| `new WorldWindow(...)` | 是 | 否 |
| `WorldWindow.addWindow(window)` | 否 | 是 |
| `ApricityUI.createWorldWindow(...)` | 是 | 是 |
| `ApricityUI.removeWorldWindow(window)` | 否 | 移除并销毁 Document |
| `ApricityUI.clearWorldWindows()` | 否 | 移除并销毁全部窗口 |

同一个实例只能注册一次。重复调用 `addWindow` 会导致重复绘制和重复输入分发；清理时也不要只从业务列表删除而忘记调用 `removeWorldWindow`。

### 3.3 重载行为

客户端资源重载时，WorldWindow 中的 Document 也会重新执行 `Document.refresh()`：

~~~text
读取最新 HTML/CSS/JS
    -> 重建 DOM
    -> 重新执行页面脚本
    -> 重新计算布局和绘制列表
    -> 保留同一个 WorldWindow 实例
~~~

刷新会重建 Element 对象。脚本或 Java 代码保存的旧 Element 引用不能继续当作当前 DOM 使用；应在页面脚本重新执行后重新查询元素。WorldWindow 的位置、朝向、深度、距离和 LOD 设置属于窗口实例，不会因为页面刷新自动清零。

## 4. HTML viewport 与世界平面尺寸

### 4.1 逻辑尺寸来自 `aui-viewport`

WorldWindow 的 `getWidth()` 和 `getHeight()` 默认读取 Document viewport 的 `layoutWidth()` 和 `layoutHeight()`。推荐通过 Meta 配置，而不是在 Java 构造器中传宽高：

~~~html
<meta name="aui-viewport"
      content="mode=fixed,width=240,height=96,scale=1">
~~~

此时窗口逻辑平面的尺寸是 `240 x 96`。CSS 中的 `width: 100%`、`height: 100%` 只是在这个逻辑 viewport 内布局，不会单独决定世界平面的物理尺寸。

`aui-viewport` 还可以使用 `mode=browser`、`mode=window` 等模式，但世界内 UI 通常应优先使用 `fixed`：

| 配置 | 适合场景 | 注意事项 |
| --- | --- | --- |
| `mode=fixed,width=...,height=...` | 信息板、实体标签、机器面板 | 逻辑尺寸稳定，最容易控制物理大小 |
| `mode=browser` | 需要复用网页布局的调试界面 | 默认 CSS 宽度较大，必须确认世界尺寸 |
| `mode=window` | 需要跟随显示器 CSS 尺寸的兼容页面 | 世界平面可能过大，不适合普通标签 |
| `mode=gui` | 兼容 Minecraft 风格的小界面 | 尺寸随 GUI 设置变化 |

viewport 改变后，WorldWindow 会在后续帧重新读取 viewport，并让自动世界缩放重新计算。有关 Meta 解析、字体模式和缩放值的通用规则见 [浏览器辅助功能文档](browser-features.md#4-meta-元素扩展)。

### 4.2 自动世界缩放

没有调用 `setScale(...)` 时，WorldWindow 会根据当前相机投影、窗口逻辑宽高和窗口与相机的距离计算一个世界缩放值。它会让初次显示的平面以一个保守比例填入可见区域，并缓存该世界物理尺寸，避免相机移动时面板每帧跳变大小。

以下情况会让自动缩放重新计算：

- Document viewport 发生变化；
- 调用 `setPosition(...)` 改变世界位置；
- 相关跟随配置导致窗口需要重新解析尺寸；
- 清除手动缩放后进行下一次有效渲染。

`getScale()` 返回当前解析出的世界缩放。窗口还没有成功渲染过时，它可能返回内部的安全回退值；不要在创建后立即把它当成最终物理尺寸。

### 4.3 手动缩放

`setScale(float)` 设置每个逻辑 Document 单位对应的世界单位，并关闭自动世界缩放：

~~~java
window.setScale(0.02f);
// 1 个逻辑像素约等于 0.02 个世界单位。
~~~

恢复自动缩放：

~~~java
window.clearScaleOverride();
~~~

手动缩放是兼容旧代码和需要精确物理尺寸时的接口。它不会修改 Document 的 viewport，也不会改变 DOM 的 `layoutWidth/layoutHeight`；它只改变 Document 平面被放入世界时的物理比例。

### 4.4 旧的宽高构造器

以下构造器仍然存在，但已被标记为过时：

~~~java
new WorldWindow(path, position, width, height, maxDistance);
ApricityUI.createWorldWindow(path, position, width, height, maxDistance);
~~~

它们会覆盖 Document viewport 的宽高，并使用旧的固定缩放路径。新代码应改为：

~~~html
<meta name="aui-viewport" content="mode=fixed,width=240,height=96,scale=1">
~~~

然后使用：

~~~java
new WorldWindow(path, position, maxDistance);
~~~

这样同一个 HTML 模板在 Screen、Overlay 和 WorldWindow 中可以共享一致的 viewport 合约。

## 5. 世界坐标与朝向

### 5.1 位置

位置使用 Minecraft 世界坐标 `Vec3`，单位是方块：

~~~java
window.setPosition(new Vec3(x, y, z));
~~~

窗口平面以该位置为中心进行渲染。若需要把面板放在实体头顶，通常使用实体位置加上高度偏移：

~~~java
Vec3 labelPosition = entity.position().add(0.0, entity.getBbHeight() + 0.25, 0.0);
window.setPosition(labelPosition);
~~~

更新实体跟踪窗口时，应在客户端 tick 中更新位置，并在窗口不再对应实体时移除它。位置移动会让自动缩放在下一次有效渲染时重新评估。

### 5.2 欧拉角

推荐使用明确的 `yaw/pitch/roll` 重载：

~~~java
window.setRotation(
        180.0f, // yaw，水平旋转，单位：度
        0.0f,   // pitch，俯仰，单位：度
        0.0f    // roll，滚转，单位：度
);
~~~

也可以省略 roll：

~~~java
window.setRotation(yaw, pitch);
~~~

特殊注意：`setRotation(Vec3 eulerDegrees)` 的参数顺序是 `(pitch, yaw, roll)`，不是 `(yaw, pitch, roll)`：

~~~java
window.setRotation(new Vec3(pitch, yaw, roll));
~~~

这是为了兼容现有的 Vec3 欧拉角调用。JavaScript/KubeJS 的三角度创建重载由绑定层把 `yaw, pitch, roll` 转换为 WorldWindow 所需的 `(pitch, yaw, roll)` 顺序，直接使用参数重载即可。

### 5.3 Quaternion

Java 代码可以传入 JOML `Quaternionf`：

~~~java
Quaternionf orientation = new Quaternionf()
        .rotateY((float) Math.toRadians(180.0f));
window.setOrientation(orientation);

Quaternionf current = window.getOrientation();
~~~

`setOrientation` 会复制传入的四元数，之后修改外部对象不会直接改变窗口朝向。

### 5.4 面向摄像机

`setFacing(true)` 会在每一帧根据活动摄像机生成面向摄像机的旋转：

~~~java
window.setFacing(true);
~~~

它是一个 frame-local 的面向行为。启用后，渲染时使用面向摄像机的旋转，适合实体标签和漂浮说明；如果需要严格固定的牌面朝向，应保持 `facing=false` 并使用 `setRotation` 或 `setOrientation`。

## 6. Follow 和 Facing

### 6.1 Follow 的含义

`setFollow(true)` 不会把窗口绑定到摄像机位置，也不会改变保存的基础位置。它会把基础位置沿摄像机视线方向投影，然后按 `followFactor` 向投影点移动：

~~~java
window.setFollow(true);
window.setFollowFactor(0.3f);
~~~

`followFactor` 会被限制在 `0.0` 到 `1.0`：

| 值 | 行为 |
| ---: | --- |
| `0` | 不跟随，保持基础世界位置 |
| `0.3` | 轻度跟随，常用于实体标签 |
| `0.5` | 中等跟随 |
| `1` | 完全移动到当前视线平面的投影位置 |

如果基础位置在摄像机后方，跟随投影不会强行把它移到摄像机前方，而是保留基础位置。这避免了窗口在镜头转身时突然穿过摄像机。

### 6.2 Follow 与 Facing 是两个独立开关

两者可以组合，也可以单独使用：

~~~java
window.setFollow(true);       // 调整位置
window.setFollowFactor(0.3f);
window.setFacing(true);       // 调整朝向
~~~

| 配置 | 位置 | 朝向 | 适用场景 |
| --- | --- | --- | --- |
| 都关闭 | 固定 | 固定 | 牌子、机器外屏 |
| 只 Follow | 视线平面方向部分跟随 | 固定 | 有方向感但希望稍微易读的面板 |
| 只 Facing | 固定 | 始终面向摄像机 | 固定点位的标签 |
| 都开启 | 部分跟随 | 始终面向摄像机 | 实体头顶信息、漂浮提示 |

### 6.3 旧的 `FollowFacingWorldWindow`

`FollowFacingWorldWindow` 是早期的专用子类，现在已经标记为过时。新代码应创建普通 `WorldWindow` 后显式设置：

~~~java
WorldWindow window = ApricityUI.createWorldWindow(path, position, 32);
window.setFollow(true);
window.setFollowFactor(0.3f);
window.setFacing(true);
~~~

旧入口仍可使用：

~~~java
FollowFacingWorldWindow window = ApricityUI.createFollowFacingWorldWindow(
        path, position, 32, 0.3f
);
~~~

它的行为等价于普通 WorldWindow 的 `follow=true`、`facing=true` 和指定的跟随系数。旧构造器中带 `width/height` 的版本同样不建议用于新代码。

## 7. 显示距离和交互距离

WorldWindow 有两个容易混淆的距离参数：

| 参数 | API | 影响 |
| --- | --- | --- |
| 交互射线距离 | `maxDistance`、`setMaxDistance` | 鼠标/准心射线到窗口平面的最大距离 |
| 相机显示距离 | `maxDisplayDistance`、`setMaxDisplayDistance` | 超过该距离后不渲染，也不参与命中 |

例如：

~~~java
WorldWindow window = ApricityUI.createWorldWindow(path, position, 32);
window.setMaxDisplayDistance(128);
~~~

这里表示交互射线最多检查 32 格，而窗口只在相机距离不超过 128 格时显示和可交互。两者应按不同目的设置。

### 7.1 全局显示距离

如果实例没有设置 `maxDisplayDistance` 覆盖值，它会使用客户端配置：

~~~toml
[worldWindow]
maxDisplayDistance = 128
~~~

配置文件是：

~~~text
config/apricityui-client.toml
~~~

开发运行目录通常是：

~~~text
run/config/apricityui-client.toml
~~~

设置为 `2147483647` 可以表达不限制显示距离：

~~~toml
[worldWindow]
maxDisplayDistance = 2147483647
~~~

实例级设置优先于全局配置：

~~~java
window.setMaxDisplayDistance(32);
window.clearMaxDisplayDistanceOverride(); // 恢复全局默认
~~~

距离检查使用相机到当前渲染位置的距离。启用 Follow 时，判断使用本帧解析出的跟随位置，而不是始终使用基础位置。

### 7.2 距离为零和窗口隐藏

所有距离值都会被限制为不小于零。`maxDisplayDistance=0` 基本上只允许相机与窗口位置重合时显示；正常页面不应使用这个值。窗口超出显示距离后，整个 Document 不绘制，命中测试也返回空。

## 8. 深度测试、遮挡和 Z 冲突

WorldWindow 注册在 `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS` 阶段绘制。这个阶段只决定它进入世界渲染管线的时机，并不等于窗口永远显示在所有方块或实体前面；最终可见性仍由深度测试、方块遮挡、视锥裁剪和显示距离共同决定。

### 8.1 方块遮挡

默认启用深度测试：

~~~java
window.setDepthTest(true);
~~~

启用时：

- 世界几何可以遮挡窗口；
- 交互命中会从摄像机向命中点做方块可视性射线检查；
- 窗口位于墙后时不会接受鼠标/准心事件；
- 渲染使用 Minecraft 深度测试和 `LEQUAL` 比较。

关闭深度测试：

~~~java
window.setDepthTest(false);
~~~

关闭后窗口不再根据方块遮挡隐藏，更像一个始终浮在世界表面的信息层；交互也不会因为中间的方块被遮挡检查拒绝。它仍然受 `maxDisplayDistance`、视锥裁剪和窗口自身矩形范围限制。

### 8.2 平面裁剪

WorldWindow 会为整个 Document 建立平面裁剪区域。即使某个子元素有阴影、滤镜或变换，也不会绘制到窗口矩形之外。窗口完全位于相机视锥外时，会在进入昂贵的 Document 绘制和 stencil 路径前跳过。

### 8.3 动态深度偏移

多个世界窗口或窗口贴近方块表面时，可能出现 Z-fighting。WorldWindow 会根据窗口距离计算一个深度预算，并为绘制节点分配局部深度步进。可以通过实例参数调节：

~~~java
window.setDynamicDepthStep(
        0.00035f, // nearDepthStep：近距离步进
        0.00300f, // farDepthStep：远距离步进
        2.0f,     // nearDistance：开始插值的距离
        32.0f     // farDistance：结束插值的距离
);
~~~

距离在 `nearDistance` 到 `farDistance` 之间时，步进在 near/far 两个值之间插值；远近边界会被规范化，far 步进不会小于 near 步进。窗口内部的绘制节点会分摊这段预算，目的是保持节点层级和减少共面冲突，不是改变 CSS 布局。

全局还可以调整深度偏移比例：

~~~toml
[worldWindow]
depthOffsetScale = 0.01
~~~

调节建议：先使用默认值；只有在窗口与方块或其他窗口共面闪烁、层级顺序不稳定时再小幅调整。值过大可能让窗口绘制层相对真实世界表面产生明显漂移。

## 9. 距离 LOD 和显示精度

WorldWindow 支持三档绘制精度和一个自动档：

| 值 | 绘制行为 |
| --- | --- |
| `AUTO` | 跟随全局 LOD 开关和距离阈值 |
| `FULL` | 完整绘制 Document，包括昂贵视觉效果 |
| `REDUCED` | 保留文字和主要内容，跳过昂贵效果 |
| `MINIMAL` | 保留基础背景和边框，跳过主要内容绘制 |

### 9.1 全局启用 LOD

LOD 默认关闭。关闭时，`AUTO` 会按 `FULL` 绘制。需要全局启用时：

~~~toml
[worldWindow]
lodEnabled = true
fullDetailDistance = 16
reducedDetailDistance = 48
~~~

距离区间是包含边界的：

| 相机距离 | `AUTO` 精度 |
| --- | --- |
| `<= fullDetailDistance` | `FULL` |
| `> fullDetailDistance` 且 `<= reducedDetailDistance` | `REDUCED` |
| `> reducedDetailDistance` | `MINIMAL` |

例如默认阈值下，16 格处仍是 `FULL`，48 格处仍是 `REDUCED`，超过 48 格才进入 `MINIMAL`。`maxDisplayDistance` 优先级更高，超过显示距离时不是降为 `MINIMAL`，而是完全不显示。

### 9.2 单个窗口覆盖

只为某一个窗口启用距离 LOD：

~~~java
window.setDisplayPrecisionDistances(16, 48);
~~~

这会把该实例设置为 `AUTO`，但使用实例自己的距离阈值。也可以强制一个固定档位：

~~~java
window.setDisplayPrecision(WorldWindowDisplayPrecision.REDUCED);
// 或使用字符串重载：
window.setDisplayPrecision("minimal");
~~~

字符串支持 `auto`、`full`、`reduced`、`minimal`，大小写不敏感。传入无法识别的值会回退到 `AUTO`。

调用 `setDisplayPrecision(WorldWindowDisplayPrecision.AUTO)` 会恢复跟随全局 LOD 策略，并清除该实例通过 `setDisplayPrecisionDistances(...)` 设置的阈值覆盖：

~~~java
window.setDisplayPrecision(WorldWindowDisplayPrecision.AUTO);
~~~

### 9.3 LOD 不改变交互和布局

显示精度只包围绘制阶段，不会把 Document 拆成不同的 DOM：

- CSS 布局仍然使用完整 Document；
- 动画和状态仍然由完整 Document 维护；
- 命中测试仍然使用完整绘制队列；
- DOM 事件和表单逻辑不会因为 `REDUCED` 或 `MINIMAL` 自动注销。

因此 LOD 主要降低远处窗口的绘制成本，不应被当作事件禁用或业务状态切换机制。若远处窗口完全不需要交互，使用合理的 `maxDisplayDistance` 可以进一步跳过命中和渲染。

## 10. 鼠标、准心和事件分发

### 10.1 屏幕位置如何映射到世界平面

每次 WorldWindow 成功渲染时，框架会保存这一帧实际使用的投影矩阵、世界变换、旋转、缩放和深度偏移。输入到来时，使用同一组变换把 GUI 坐标反投影到窗口 Document：

~~~text
GUI 鼠标/准心位置
    -> 当前帧世界平面反投影
    -> Document 本地坐标
    -> hitTest
    -> mouse/pointer 事件
~~~

这比用一个固定二维矩形估算更重要：窗口可以旋转、透视缩放、跟随摄像机，命中必须使用和绘制相同的矩阵。

### 10.2 鼠标抓取时使用准心

当 Minecraft 鼠标被抓取时，GLFW 光标的坐标是虚拟视角移动坐标，而屏幕上的准心保持在 GUI 中心。WorldWindow 会在这种情况下使用 GUI 中心作为世界交互点；未抓取鼠标时才使用实际 GUI 光标位置。

因此，世界窗口交互应以准心为准，而不是以调试时看到的 GLFW 虚拟光标坐标为准。对于第一人称世界内窗口，必须把面板放在准心射线能够命中的位置。

### 10.3 事件类型

世界窗口会分发常用鼠标事件：

~~~text
mousemove
mousedown
mouseup
click
dblclick
contextmenu
wheel
mouseover
mouseout
mouseenter
mouseleave
~~~

同时提供对应的 Pointer 兼容事件。事件中的 `clientX/clientY`、`pageX/pageY` 是 Document 事件坐标，已经经过世界平面反投影和 Document viewport 变换；不要再次乘世界缩放、`renderScale` 或 `devicePixelRatio`。

页面示例：

~~~javascript
document.addEventListener("mousemove", function (event) {
    // 这是页面逻辑坐标，不是世界坐标，也不是原始屏幕像素。
    console.log("document point", event.clientX, event.clientY);
});

document.addEventListener("mousedown", function (event) {
    console.log("button", event.button, "trusted", event.isTrusted);
});
~~~

### 10.4 输入消费

`<meta name="aui-mouse-events" content="intercept">` 会让命中窗口的原生 Minecraft 鼠标输入被 AUI 消费：

~~~html
<meta name="aui-mouse-events" content="intercept">
~~~

它不负责创建事件，也不是事件监听器开关。它控制的是事件结束后是否继续交给原生 Minecraft 输入处理。页面脚本仍然可以用 `preventDefault()` 阻止 wheel 默认滚动、click 默认激活等页面级默认行为。

没有设置 `intercept` 时，WorldWindow 仍会收到 AUI 的页面事件，但原生 Minecraft 的点击、滚轮或其他输入可能继续处理。需要可点击的世界内按钮时，通常设置 `intercept`，并在页面中处理 `click` 或 `contextmenu`。

### 10.5 Overlay 和 WorldWindow 的关系

当 Minecraft Screen 存在时，鼠标按下/释放和滚轮的世界窗口分发会被屏幕输入优先级阻断。世界窗口不是 Screen Overlay，不能通过 `reloadPersistent` 让它盖在 Screen 菜单之上。

没有 Minecraft Screen 时，客户端会把当前输入分别尝试分发给已注册的 WorldWindow。多个窗口投影重叠时，当前实现没有建立独立的世界窗口 topmost 排序；每个能够命中的窗口都可能收到事件。设计多个重叠面板时，应避免重叠交互区域，或在业务层自行保证只有一个窗口可交互。

## 11. WorldWindow 坐标转换 API

WorldWindow 提供了用于调试、准星提示和自定义交互的坐标方法。

### 11.1 `getDocumentPositionAtScreen`

把 GUI 坐标转换为窗口本地 Document 坐标：

~~~java
Position screen = new Position(mouseX, mouseY);
Position local = window.getDocumentPositionAtScreen(screen);
if (local != null) {
    double x = local.x;
    double y = local.y;
}
~~~

返回 `null` 的情况包括：

- 窗口本帧没有成功渲染或没有可用的交互矩阵；
- 坐标不在平面矩形内；
- 超出 `maxDisplayDistance`；
- 命中点超过 `maxDistance`；
- 启用深度测试且中间有方块遮挡；
- 投影矩阵不可逆或输入不是有限数。

### 11.2 `projectDocumentPosition`

把 Document 本地坐标投影到当前 GUI 坐标：

~~~java
Position screen = window.projectDocumentPosition(new Position(120, 48));
~~~

它适合在屏幕 HUD 或调试工具中显示“世界窗口内某个元素对应的屏幕位置”。返回值是 GUI-scaled 坐标，不是 framebuffer 原始像素。

### 11.3 `projectDocumentRect`

把一个 Document 矩形投影成保守的 GUI 包围盒：

~~~java
WorldWindow.ScreenRect rect = window.projectDocumentRect(0, 0, 240, 96);
if (rect != null) {
    double left = rect.x();
    double top = rect.y();
    double width = rect.width();
    double height = rect.height();
}
~~~

旋转后的平面会返回四个角投影后的轴对齐包围盒，因此这个矩形可能比实际平面更大，不能用它替代精确命中测试。

### 11.4 `getRealPos`

~~~java
Position eventPosition = window.getRealPos();
Position eventPositionAt = window.getRealPos(screenPosition);
~~~

它返回的是当前鼠标/准心映射到该 Document 的事件坐标空间，不是世界 `Vec3`。如果当前点没有命中窗口，返回 `null`。

## 12. 动态更新和页面状态

WorldWindow 的 `document` 字段是公开的，可以直接更新 DOM：

~~~java
Document document = window.document;
if (document != null && document.body != null) {
    Element status = document.getElementById("status");
    if (status != null) {
        status.setTextContent("HP: " + health);
        document.markDirty(status, Drawer.REPAINT);
    }
}
~~~

页面脚本也可以使用 `textContent`、属性、表单值和事件监听器更新状态。高频更新时应只修改必要元素，并使用已有的脏标记机制，不要每 tick 重新创建整个 Document。

WorldWindow 的空间属性可以在运行时更新：

~~~java
window.setPosition(newPosition);
window.setRotation(yaw, pitch, roll);
window.setDepthTest(false);
window.setMaxDistance(48);
window.setMaxDisplayDistance(96);
~~~

这些设置不需要重建 Document。只有 HTML/CSS/JS 资源改变或需要重新解析 Meta 时，才调用 `document.refresh()` 或使用客户端资源重载。

## 13. Java 与 KubeJS API 参考

### 13.1 创建和销毁

| 方法 | 说明 |
| --- | --- |
| `ApricityUI.createWorldWindow(path, Vec3, maxDistance)` | 创建并自动注册，尺寸来自 viewport |
| `ApricityUI.createWorldWindow(path, x, y, z, maxDistance)` | 坐标参数版本 |
| `ApricityUI.createWorldWindow(path, Vec3, maxDistance, maxDisplayDistance)` | 创建并设置实例显示距离 |
| `ApricityUI.createWorldWindow(path, Vec3, maxDistance, yaw, pitch)` | 创建并设置欧拉角 |
| `ApricityUI.createWorldWindow(path, Vec3, maxDistance, yaw, pitch, roll)` | 创建并设置完整欧拉角 |
| `ApricityUI.createWorldWindow(path, Vec3, maxDistance, Vec3 eulerDegrees)` | 使用 `(pitch, yaw, roll)` |
| `ApricityUI.createWorldWindow(path, Vec3, maxDistance, Quaternionf)` | 使用四元数 |
| `ApricityUI.removeWorldWindow(window)` | 移除实例并销毁 Document |
| `ApricityUI.clearWorldWindows()` | 移除全部世界窗口 |

Java 直接构造时，对应类构造器的参数规则相同，但需要手动 `WorldWindow.addWindow(window)`。

### 13.2 位置和朝向

| 方法 | 说明 |
| --- | --- |
| `setPosition(Vec3)` | 修改基础世界位置 |
| `setRotation(yaw, pitch)` | 设置角度，单位为度 |
| `setRotation(yaw, pitch, roll)` | 设置完整欧拉角，单位为度 |
| `setRotation(Vec3)` | 参数顺序为 `(pitch, yaw, roll)` |
| `setOrientation(Quaternionf)` | 设置四元数朝向 |
| `getOrientation()` | 获取当前朝向副本 |
| `setFollow(boolean)` | 开关视线平面跟随 |
| `setFollowFactor(float)` | 设置 `0..1` 的跟随比例 |
| `setFacing(boolean)` | 开关面向摄像机 |
| `isFollowEnabled()`、`isFacingEnabled()` | 查询开关状态 |

### 13.3 尺寸、距离和深度

| 方法 | 说明 |
| --- | --- |
| `getWidth()`、`getHeight()` | 当前 Document 逻辑尺寸 |
| `setScale(float)` | 设置手动物理缩放 |
| `getScale()` | 获取当前缩放或最近一次解析值 |
| `hasScaleOverride()` | 是否使用手动缩放 |
| `clearScaleOverride()` | 恢复自动缩放 |
| `setMaxDistance(int)` | 设置交互射线距离 |
| `setMaxDisplayDistance(int)` | 设置实例显示距离 |
| `clearMaxDisplayDistanceOverride()` | 恢复全局显示距离 |
| `setDepthTest(boolean)` | 开关世界几何遮挡 |
| `setDynamicDepthStep(...)` | 设置动态深度步进 |

### 13.4 LOD

| 方法 | 说明 |
| --- | --- |
| `setDisplayPrecision(WorldWindowDisplayPrecision)` | 强制或恢复自动精度 |
| `setDisplayPrecision(String)` | KubeJS 友好的字符串重载 |
| `setDisplayPrecisionDistances(full, reduced)` | 为实例设置自动 LOD 阈值 |
| `getDisplayPrecision()` | 获取配置档位 |
| `getEffectiveDisplayPrecision()` | 获取当前相机下实际档位 |
| `getFullDetailDistance()` | 获取生效的 FULL 阈值 |
| `getReducedDetailDistance()` | 获取生效的 REDUCED 阈值 |

`getDisplayPrecision()` 返回配置值；当配置为 `AUTO` 时，实际绘制档位应使用 `getEffectiveDisplayPrecision()` 判断。

## 14. 完整使用模式

### 14.1 固定世界信息牌

适合机器、建筑或固定位置的信息板：

~~~java
WorldWindow sign = ApricityUI.createWorldWindow(
        "world/sign.html",
        new Vec3(100.5, 64.2, -30.5),
        12
);
sign.setRotation(180.0f, 0.0f, 0.0f);
sign.setMaxDisplayDistance(64);
sign.setDepthTest(true);
sign.setDisplayPrecision(WorldWindowDisplayPrecision.AUTO);
~~~

HTML 使用固定 viewport：

~~~html
<meta name="aui-viewport" content="mode=fixed,width=320,height=120,scale=1">
~~~

### 14.2 实体头顶标签

实体标签通常需要跟随实体位置，并且始终面向摄像机：

~~~java
WorldWindow label = ApricityUI.createWorldWindow(
        "world/entity-label.html",
        entity.position().add(0.0, entity.getBbHeight() + 0.35, 0.0),
        24
);
label.setFollow(true);
label.setFollowFactor(0.3f);
label.setFacing(true);
label.setMaxDisplayDistance(48);
~~~

实体每次移动时更新基础位置；实体失效、死亡或离开跟踪范围时调用 `ApricityUI.removeWorldWindow(label)`。

### 14.3 远处大量窗口

大量世界窗口应同时限制数量、显示距离和绘制精度：

~~~java
window.setMaxDisplayDistance(96);
window.setDisplayPrecisionDistances(16, 48);
window.setDepthTest(true);
~~~

再通过全局配置打开 LOD：

~~~toml
[worldWindow]
lodEnabled = true
fullDetailDistance = 16
reducedDetailDistance = 48
~~~

LOD 不会减少布局和 DOM 维护成本。如果窗口数量很大，业务层仍应主动移除远离玩家或已经失效的实例。

### 14.4 世界内调试窗口

项目内置一个可调节参数的测试命令：

~~~text
/aui worldwindow
~~~

它会在玩家视线前方创建 `tests/world-window-command.html`，可实时测试：

- 最大显示距离和交互射线距离；
- 深度测试；
- `AUTO/FULL/REDUCED/MINIMAL`；
- 实例 LOD 阈值；
- 自动/手动世界缩放；
- 动态深度步进；
- Follow、Facing 和跟随系数。

另一个验收页面是 `tests/world-window-acceptance.html`。客户端测试生成器会查找名称为 `auitest` 的 ArmorStand，并在它上方创建一个启用 Follow/Facing 的窗口。测试世界内渲染、准心命中和方块遮挡时可以使用该入口。

## 15. 常见问题

### 页面资源存在但世界里没有窗口

确认是否只调用了 `Document.createInWorld(path)`。该方法只创建 Document，不负责加入 `WorldWindow.windows`。使用 `ApricityUI.createWorldWindow(...)`，或手动调用 `WorldWindow.addWindow(window)`。

### 构造窗口时报空指针或没有显示

先检查 HTML 逻辑路径和资源包位置。资源不存在时 `Document.createInWorld` 会返回空 Document；不要传物理路径、`file:` 路径或带 `assets/...` 前缀的路径。

### 面板尺寸太大或太小

优先检查：

1. `<meta name="aui-viewport">` 的逻辑宽高；
2. 是否使用了 `mode=browser/window` 导致 viewport 宽度很大；
3. 是否调用过 `setScale(...)`；
4. 是否仍使用已过时的宽高构造器；
5. 自动缩放是否还没有完成第一次有效渲染。

固定面板通常从 `mode=fixed,width=...,height=...,scale=1` 开始调试。

### 窗口被墙挡住或完全不响应

默认启用了深度测试。确认窗口是否真的位于墙后，或者暂时测试：

~~~java
window.setDepthTest(false);
~~~

如果关闭后可以交互，说明是方块遮挡判断生效。还要区分 `maxDistance` 和 `maxDisplayDistance`：前者限制交互射线，后者限制窗口是否存在于当前渲染/交互范围。

### 鼠标位置和准心不一致

第一人称抓取鼠标时，WorldWindow 使用 GUI 中心的准心位置，不使用 GLFW 虚拟光标。检查窗口是否处在准心射线上，并避免在页面事件中再次乘 viewport zoom、世界缩放或 `devicePixelRatio`。

### 旋转方向不对

优先使用 `setRotation(yaw, pitch, roll)`，确认角度单位是度。若使用 `setRotation(Vec3)`，记住它的顺序是 `(pitch, yaw, roll)`。如果启用了 `setFacing(true)`，当前帧会使用面向摄像机的旋转，不应再把固定朝向结果当作最终渲染方向。

### 远处窗口变成空白但仍有逻辑

检查是否启用了 LOD。`MINIMAL` 会跳过主要内容绘制，但仍保留 Document 的布局和事件状态。若需要完整绘制，调用：

~~~java
window.setDisplayPrecision(WorldWindowDisplayPrecision.FULL);
~~~

如果完全不想显示，则调整 `maxDisplayDistance` 或移除窗口，而不是依赖 `MINIMAL` 作为隐藏机制。

### 多个窗口同时响应点击

当前世界窗口输入分发没有按照窗口列表建立唯一 topmost。重叠的可命中窗口可能都收到事件。调整位置、尺寸、深度或业务交互状态，避免多个可交互平面重叠。

### 资源重载后 JavaScript 状态丢失

这是 `Document.refresh()` 的预期结果。HTML、CSS、JS 和 DOM 节点会重建，页面脚本会重新执行。把需要恢复的状态放在 Java/持久化数据中，刷新后重新注入；不要依赖旧 Element 引用。

### 窗口与方块表面闪烁

这是典型的共面 Z-fighting。先调整窗口位置，使它离表面有少量偏移；仍有问题时再小幅调整：

~~~java
window.setDynamicDepthStep(0.00035f, 0.003f, 2.0f, 32.0f);
~~~

全局 `depthOffsetScale` 也会影响结果，但不要直接设置过大的值，否则可能看到窗口相对世界表面明显漂移。

## 16. 设计和性能建议

- 世界 UI 优先使用固定 viewport 和较小逻辑尺寸；
- 不要把普通浏览器的超大页面直接作为世界平面；
- 为大量窗口设置合理的 `maxDisplayDistance`；
- 远处窗口启用 LOD，近处重要窗口再使用 `FULL`；
- 高频数据只更新必要的文本、属性或样式，不要每 tick 重建 Document；
- 实体销毁、区块卸载或页面失效时立即移除对应 WorldWindow；
- 减少透明大背景、复杂滤镜、阴影、`clip-path` 和高频动画；
- 多个窗口共面时，使用空间偏移和动态深度步进控制层级；
- 对可交互窗口设置 `aui-mouse-events=intercept`，纯展示窗口可以不拦截原生输入；
- 不要让多个可交互 WorldWindow 投影重叠；
- 调试命中时优先使用 `getDocumentPositionAtScreen`，不要自行实现一套二维比例换算。

## 17. 相关源码和测试

WorldWindow 的主要实现：

~~~text
src/main/java/com/sighs/apricityui/instance/WorldWindow.java
src/main/java/com/sighs/apricityui/instance/FollowFacingWorldWindow.java
src/main/java/com/sighs/apricityui/instance/WorldWindowDisplayPrecision.java
src/main/java/com/sighs/apricityui/instance/WorldWindowVisibility.java
src/main/java/com/sighs/apricityui/render/WorldWindowRenderContext.java
src/main/java/com/sighs/apricityui/ApricityUI.java
src/main/java/com/sighs/apricityui/util/kjs/ApricityUIClientUtil.java
src/main/java/com/sighs/apricityui/instance/Client.java
src/main/java/com/sighs/apricityui/init/Document.java
src/main/java/com/sighs/apricityui/instance/ApricityUIConfig.java
~~~

测试和调试入口：

~~~text
src/main/java/com/sighs/apricityui/instance/WorldWindowTestSpawner.java
src/main/java/com/sighs/apricityui/instance/ApricityUIClientCommands.java
src/test/java/com/sighs/apricityui/instance/WorldWindowViewportContractTest.java
src/main/resources/assets/apricityui/apricity/tests/world-window-acceptance.html
src/main/resources/assets/apricityui/apricity/tests/world-window-command.html
~~~

WorldWindow 的通用浏览器行为，包括 `aui-viewport`、`aui-font-mode`、`aui-mouse-events`、文字选择和事件字段，参见 [浏览器辅助功能文档](browser-features.md)。
