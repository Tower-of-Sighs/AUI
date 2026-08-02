# ApricityUI 扩展元素文档

最后更新：2026-08-02

ApricityUI 在标准 HTML 元素之外注册了一组面向 Minecraft 资源、动画、翻译、矢量图和容器的扩展元素。它们仍然是 `Document`/`Element` 树中的节点，会参与 AUI 的 CSS、布局、命中测试和生命周期，但不代表 Chromium 或浏览器标准标签。

本文重点介绍：

| 标签 | 用途 |
| --- | --- |
| `<texture>` | 直接绘制 Minecraft `ResourceLocation` 纹理 |
| `<sprite>` | 把横向/纵向图集转换为 CSS 背景动画 |
| `<translation>` | 将 Minecraft 翻译 key 渲染为本地化文本 |
| `<svg>`、`<path>` | 用基础 SVG 图形和路径生成高分辨率位图 |
| `<canvas>` | 使用 Canvas 2D API 绘制动态位图 |
| `<container>`、`<slot>`、`<recipe>` | 容器 Screen 的 Minecraft 数据扩展 |

第三方模组如何注册自己的元素，见 [二次开发文档](secondary-development.md#2-注册第三方元素)。普通 HTML、CSS 属性和 Web API 的支持范围见 [HTML/CSS 覆盖面](html-css-coverage.md) 和 [Web API](web-api.md)。

## 1. 共同规则

### 1.1 标签和资源路径

扩展标签在内部以大写名字注册，HTML 中大小写不敏感：

~~~html
<texture src="minecraft:textures/item/diamond.png"></texture>
<sprite src="images/coin-strip.png" steps="8"></sprite>
<translation>container.apricityui.title</translation>
~~~

资源 URL 是否使用 AUI 逻辑路径，取决于元素：

- `texture.src` 是 Minecraft `ResourceLocation`，不是相对于 HTML 的文件路径；
- `sprite.src` 按当前 Document 的逻辑路径解析，可以指向本地 AUI 图片或受限的 HTTPS 图片；
- `svg` 和 `canvas` 不通过 `src` 自动加载外部图片；Canvas 中的图片读取遵循 Web API 和资源管线规则。

### 1.2 尺寸和异步状态

自定义绘制元素如果没有可用的固有尺寸，必须通过 CSS 或 HTML 属性给出尺寸。建议给图片、动画和 SVG 同时设置稳定的 `width`、`height`，避免资源异步完成后布局突然改变：

~~~css
.icon,
.coin-animation,
.vector-mark {
    display: block;
    width: 32px;
    height: 32px;
}
~~~

远程图片、Sprite 图集和字体可能在异步任务完成后才有可用尺寸。页面应允许元素先处于空白/静态状态，并在资源就绪后由 AUI 标记重绘；不要在页面脚本中每帧调用 `refresh()` 等待图片。

## 2. `<texture>`：Minecraft 纹理

`Texture` 直接把已经由 Minecraft 纹理系统管理的资源绘制到元素的 body rectangle。它不会经过 AUI 图片文件扫描，也不会把 `src` 当成 HTML/CSS 的相对 URL。

### 2.1 属性

| 属性 | 必填 | 说明 |
| --- | --- | --- |
| `src` | 是 | Minecraft `ResourceLocation`，格式通常为 `namespace:path` |
| `blur` | 否 | 只有值为精确字符串 `true` 时启用模糊绘制，默认关闭 |

`path` 使用 Minecraft 资源路径。例如物品纹理通常写成：

~~~text
minecraft:textures/item/diamond.png
examplemod:textures/block/machine.png
~~~

不要写：

~~~text
images/diamond.png
src/main/resources/assets/examplemod/textures/item/diamond.png
~~~

上面后两种是文件路径或 AUI 逻辑路径，不是 `Texture` 所需的 `ResourceLocation`。如果需要加载 AUI 页面目录里的图片，应使用普通 `<img>` 或 `<sprite>`。

### 2.2 示例

~~~html
<div class="item-icon">
    <texture class="diamond-icon"
             src="minecraft:textures/item/diamond.png">
    </texture>
    <texture class="machine-icon"
             src="examplemod:textures/block/machine.png"
             blur="true">
    </texture>
</div>
~~~

~~~css
.item-icon {
    display: flex;
    gap: 8px;
}

.diamond-icon,
.machine-icon {
    width: 32px;
    height: 32px;
}
~~~

没有有效的 `src`、`ResourceLocation` 无法解析或元素布局尺寸为零时，元素不会绘制纹理，但它仍然可以绘制 CSS 背景、边框和阴影。修改 `src` 会立即同步新的资源位置；Java 侧的 `Texture#getCurrentSrc()` 返回当前解析后的字符串，未解析成功时为空字符串。

## 3. `<sprite>`：图集动画

`Sprite` 把一张横向或纵向图集映射为 `background-image`，再用 CSS `steps()` 动画切换帧。它适合粒子、按钮状态、角色动作和 Minecraft 风格图标动画。

### 3.1 基本示例

假设 `images/coin-strip.png` 的实际尺寸是 `256x32`，其中有 8 个 `32x32` 横向帧：

~~~html
<sprite class="coin-animation"
        src="images/coin-strip.png"
        steps="8"
        direction="right"
        duration="640ms"
        loop="infinite"
        steps-mode="end"
        autoplay="true"
        initialframe="0"
        fit="none">
</sprite>
~~~

~~~css
.coin-animation {
    width: 32px;
    height: 32px;
}
~~~

`src` 相对于当前 HTML 文件解析。比如 HTML 位于 `screens/home.html`，`images/coin-strip.png` 会解析为 `screens/images/coin-strip.png`；需要返回资源根时使用 `/images/coin-strip.png`。

### 3.2 属性参考

| 属性 | 默认值 | 支持值和行为 |
| --- | --- | --- |
| `src` | 空 | 本地 AUI 图片逻辑路径或 HTTPS 图片 URL |
| `steps` | 无 | 正整数帧数；缺失或非法时按静态背景处理 |
| `direction` | `right` | `right`、`left`、`up`、`down` |
| `duration` | `1s` | CSS 时间值，例如 `250ms`、`1.5s`；非法值回退 `1s` |
| `loop` | `infinite` | `infinite` 或正整数播放次数 |
| `steps-mode` | `end` | `start` 或 `end`，对应 CSS `steps()` 的 timing mode |
| `autoplay` | `true` | `false`、`0`、`no`、`off` 会关闭动画 |
| `initialframe` | `0` | 初始帧索引，超出范围会限制到最后一帧；`initial-frame` 也可被宽松解析 |
| `fit` | `none` | `none`、`contain`、`cover`、`stretch`，映射到 `background-size` |

`direction` 同时决定图集布局和帧移动方向：

| direction | 图集排列 | 单帧尺寸 |
| --- | --- | --- |
| `right` / `left` | 从左到右的一行 | `textureWidth / steps` x `textureHeight` |
| `up` / `down` | 从上到下的一列 | `textureWidth` x `textureHeight / steps` |

图片宽度或高度不能被 `steps` 正确分割时，推导的帧尺寸可能不符合预期；制作图集时应保证每一帧尺寸一致。元素至少需要 CSS 的布局宽高，否则背景有帧但看不到绘制结果。

### 3.3 加载和样式托管

Sprite 通过 AUI 图片异步管线读取图集。图片尚未 ready 时会先使用静态背景，资源可用后在后续 tick 重新推导帧尺寸并生成动画。

为保证动画可用，Sprite 会在 `style` 属性中托管以下声明：

~~~text
background-image
background-repeat
background-position
background-size
animation 及 animation-* 相关属性
~~~

这些属性会覆盖同名的 Sprite 用户 inline style；其他 inline style 会保留。用户自己的 CSS 动画不会被静默删除，Sprite 动画会作为一个 animation segment 与外部动画合并。需要完全控制背景或动画时，不要使用 Sprite 的托管模式，改用普通 `div` + CSS。

`autoplay="false"` 仍会设置图集背景和 `initialframe`，只是不会注入 Sprite keyframes。没有 `steps` 时，元素也会作为静态背景处理。

## 4. `<translation>`：Minecraft 翻译文本

`Translation` 的文本内容不是要显示的字面字符串，而是 Minecraft 翻译 key：

~~~html
<translation class="screen-title">
    container.apricityui.title
</translation>
~~~

框架会调用 Minecraft 的 `Component.translatable(key)`，按照当前语言环境渲染结果。CSS 可以像处理 `span` 一样设置字体、颜色、间距和文本布局：

~~~css
.screen-title {
    color: #f4d58b;
    font-size: 18px;
    font-weight: bold;
}
~~~

元素的 `textContent` 是 key，Java 侧 `Translation#getTranslatedText()` 返回当前语言下的字符串。该元素目前只接收 key，不提供浏览器式的参数插值对象；需要复杂参数时在 Java/KubeJS 侧先生成合适的 Minecraft Component，或使用普通文本节点。

## 5. `<svg>` 和 `<path>`：矢量描述的位图实现

AUI 的 `Svg` 不是完整的浏览器 SVG 引擎。它把支持的基础图形和路径绘制到 Java2D 位图表面，再将位图作为 Canvas 纹理显示。当前表面使用约 4 倍超采样后缩放到 CSS 布局尺寸，适合图标和简单装饰图形。

### 5.1 基本示例

~~~html
<svg class="vector-mark" viewBox="0 0 64 64" width="64" height="64">
    <circle cx="32" cy="32" r="28"
            fill="none"
            stroke="currentColor"
            stroke-width="4">
    </circle>
    <path d="M18 34 L28 44 L47 22 Z"
          fill="currentColor"
          opacity="0.9">
    </path>
</svg>
~~~

~~~css
.vector-mark {
    width: 48px;
    height: 48px;
    color: #7dd3fc;
}
~~~

`viewBox` 是内部坐标系，格式为 `minX minY width height`，允许空格或逗号分隔。`width`、`height` 既可以是 SVG 属性，也可以由 CSS 布局提供；没有合理尺寸时，SVG 只能得到最小的位图表面。

### 5.2 支持的图形和属性

`<svg>` 子树当前识别这些基础图形：

| 子元素 | 主要属性 |
| --- | --- |
| `circle` | `cx`、`cy`、`r` |
| `ellipse` | `cx`、`cy`、`rx`、`ry` |
| `rect` | `x`、`y`、`width`、`height` |
| `line` | `x1`、`y1`、`x2`、`y2` |
| `polyline` | `points` |
| `polygon` | `points` |
| `path` | `d`、可选 `fill-rule` |

图形支持的绘制属性包括：

| 属性 | 说明 |
| --- | --- |
| `fill` | 填充颜色，`none` 表示不填充 |
| `stroke` | 描边颜色，`none` 表示不描边 |
| `stroke-width` | 描边宽度 |
| `stroke-linecap` | `butt`、`round`、`square` |
| `stroke-linejoin` | `miter`、`round`、`bevel` |
| `opacity` | 整体透明度 |
| `fill-opacity` | 填充透明度 |
| `stroke-opacity` | 描边透明度 |
| `fill-rule` | 支持 `evenodd` 以改变路径填充规则 |
| `color` / `currentColor` | 当前颜色及其引用 |

颜色可写成 AUI `Color` 支持的颜色值；`fill="currentColor"` 会读取元素或祖先的 `color`。默认填充为黑色，默认描边为 `none`。绘制属性可以从父级 SVG/分组节点向子树继承。

### 5.3 Path 命令和边界

`path d` 使用 AUI 的 SVG path 解析器，支持绝对和相对形式的：

~~~text
M m   L l   H h   V v   C c   S s   Q q   T t   A a   Z z
~~~

其中包含直线、二次/三次贝塞尔曲线和椭圆弧。`<path>` 类本身也是注册过的 Element，但只有作为 `<svg>` 子元素时才由 SVG 渲染器按路径绘制；单独放在普通页面中不会自动变成独立 SVG 画布。

目前不要把它当成完整 SVG 标准实现来使用。复杂的 `defs/use`、滤镜、渐变、遮罩、裁剪路径、SVG 文本、外部 SVG 文档和完整 SVG transform 管线不属于这里列出的支持范围。需要这些能力时，优先预渲染为 Minecraft 纹理，或使用 Canvas/普通 CSS 分步实现。

SVG 会根据子树属性和布局尺寸缓存栅格结果；属性或子树变化会使位图重新绘制。大尺寸、复杂路径或每帧变化的 SVG 会产生对应的 Java2D 栅格化成本，建议控制 viewBox 和 CSS 尺寸，并结合 [frameTimingHud](secondary-development.md#5-frametiminghud-帧耗时-hud) 观察开销。

## 6. `<canvas>`：动态位图入口

`Canvas` 提供 `getContext("2d")` 和 AUI 的 Canvas 2D 实现。默认位图尺寸是 `300x150`，也可以通过 `width`、`height` 属性设置；CSS 尺寸控制最终布局显示尺寸。位图尺寸和 CSS 尺寸不同会产生缩放，不要只修改 CSS 就假设绘图坐标系改变。

~~~html
<canvas id="chart" width="320" height="160" class="chart"></canvas>
~~~

~~~javascript
var canvas = document.getElementById("chart");
var context = canvas.getContext("2d");
context.fillStyle = "#38bdf8";
context.fillRect(12, 12, 120, 24);
~~~

完整的 2D 方法、ImageData、渐变、Path2D、导出和限制见 [Web API 文档的 Canvas 章节](web-api.md#111-canvas-2d)。Canvas 会把 Java2D surface 上传为动态纹理；频繁改变大画布时应关注上传和重绘成本。

## 7. 容器相关扩展元素

`<container>`、`<slot>` 和 `<recipe>` 不是普通图片或装饰标签，而是容器 Screen 的数据声明：

~~~html
<container id="main" bind="player">
    <slot index="0">minecraft:diamond</slot>
    <recipe type="crafting_shaped">minecraft:crafting_table</recipe>
</container>
~~~

- `container` 声明服务端需要绑定的容器数据源；
- `slot` 描述客户端槽位位置、索引和重复槽位；
- `recipe` 提供配方区域或配方扩展所需的视图节点。

服务端容量、绑定类型、玩家背包、自动补槽和 KubeJS/Java 打开方式由容器扩展器统一处理。只在客户端添加一个 `<slot>` 并不能创建真实 Minecraft 槽位，也不能绕过服务端菜单同步。请按 [Apricity 容器文档](container.md) 声明绑定。

## 8. 和普通 HTML 元素的选择

| 需求 | 推荐元素 |
| --- | --- |
| AUI 资源目录中的 PNG/GIF/WebP | `<img>` 或 `<sprite>` |
| Minecraft 注册表中的纹理 | `<texture>` |
| 多帧图集动画 | `<sprite>` |
| Minecraft 本地化文本 | `<translation>` |
| 少量图标、线条和曲线 | `<svg>` / `<path>` |
| 需要逐像素或 Canvas 2D API | `<canvas>` |
| 真实物品槽位和配方视图 | `<container>` / `<slot>` / `<recipe>` |

扩展元素本身不改变资源宿主。Screen、Overlay、Container 和 WorldWindow 的生命周期仍由各自宿主负责；`END` 重载会重建普通 Document 的扩展元素实例，旧的 Java 引用和页面监听器不能跨刷新复用。

## 9. 源码位置

~~~text
src/main/java/com/sighs/apricityui/element/Texture.java
src/main/java/com/sighs/apricityui/element/Sprite.java
src/main/java/com/sighs/apricityui/instance/element/Translation.java
src/main/java/com/sighs/apricityui/element/Svg.java
src/main/java/com/sighs/apricityui/element/Path.java
src/main/java/com/sighs/apricityui/element/Canvas.java
src/main/java/com/sighs/apricityui/instance/element/Container.java
src/main/java/com/sighs/apricityui/instance/element/Slot.java
src/main/java/com/sighs/apricityui/instance/element/Recipe.java
~~~

相关专题：

- [二次开发文档](secondary-development.md)
- [资源管理文档](resource-manager.md)
- [容器文档](container.md)
- [内置 UI 库](ui-library.md)
- [浏览器辅助功能](browser-features.md)
