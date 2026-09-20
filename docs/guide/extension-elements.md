# 扩展元素

AUI 在标准 HTML 之外注册了一组扩展标签，都是普通 DOM 元素，能正常参与 CSS、布局、命中测试和脚本操作。它们解决一类共同问题：**把游戏资源和动画画进页面**。

标准元素的能力边界见 [HTML/CSS 覆盖面](html-css-coverage)，容器/槽位/配方见[容器文档](container)，注册自己的元素见[二次开发文档](secondary-development)，这里都不重复。

## 怎么选

| 需求 | 用 |
| --- | --- |
| 页面目录里的静态图（PNG/GIF/WebP） | 标准 `<img>` / CSS `background-image` |
| Minecraft 注册表纹理（物品、方块贴图） | `<texture>` |
| 图集序列帧动画 | `<sprite>` |
| 本地化文本 | `<translation>` |
| 矢量图标、线条、曲线 | `<svg>` |
| 逐像素、图表、每帧重算的画面 | `<canvas>` |
| 跑一个真正的网页 / 第三方 Web 页面 | `<iframe>` |
| 物品槽、背包、配方预览 | `<container>` / `<slot>` / `<recipe>`（容器文档） |

所有自定义绘制元素都没有固有尺寸，记得用 CSS 或属性给稳定的 `width`/`height`，否则资源异步就绪后布局会跳。别在脚本里每帧 `refresh()` 等资源——资源就绪后框架会标记重绘。

## texture：Minecraft 纹理

```html
<texture src="minecraft:textures/item/diamond.png" style="width: 32px; height: 32px;"></texture>
<texture src="examplemod:textures/block/machine.png" blur="true"></texture>
```

- `src` 是 `namespace:path` 形式的 ResourceLocation，直接走 MC 纹理系统，**不是**页面逻辑路径，也不是磁盘路径——页面目录里的图请用 `<img>` 或 `<sprite>`；
- `blur` 只有精确写 `true` 才开启模糊绘制；
- src 无效或元素尺寸为零时不画纹理，但 CSS 背景、边框照常绘制；改 `src` 立即生效。

## sprite：图集序列帧

把一张横向或纵向图集播成 `steps()` 帧动画，适合加载圈、按钮状态、粒子、待机动作：

```html
<sprite class="coin"
        src="images/coin-strip.png"
        steps="8"
        direction="right"
        duration="640ms"
        loop="infinite"></sprite>
```

`src` 按当前 HTML 的**逻辑路径**解析（`screens/home.html` 里的 `images/x.png` 解析为 `screens/images/x.png`，回资源根用 `/images/x.png`），也可以是 HTTPS 图片。这和 texture 的 ResourceLocation 正好相反，别搞混。

| 属性 | 默认 | 说明 |
| --- | --- | --- |
| `steps` | 无 | 正整数帧数；缺失或非法时按静态背景处理 |
| `direction` | `right` | `right`/`left`/`up`/`down`，同时决定图集排布和帧移动方向 |
| `duration` | `1s` | CSS 时间值（`250ms`、`1.5s`） |
| `loop` | `infinite` | `infinite` 或正整数播放次数 |
| `steps-mode` | `end` | `start` / `end`，对应 CSS `steps()` 的时序模式 |
| `autoplay` | `true` | `false`/`0`/`no`/`off` 关闭；关闭时仍显示 `initialframe` 那一帧 |
| `initialframe` | `0` | 起始帧，越界钳到最后一帧 |
| `fit` | `none` | `none`/`contain`/`cover`/`stretch`，映射到 background-size |

横向图集单帧尺寸 = 宽/steps × 高，纵向反之——图集每帧尺寸必须一致，否则推导会错。

**注意**：Sprite 会在 inline style 里托管 `background-image/position/size/repeat` 和 `animation` 系属性，覆盖你手写的同名 inline 声明（其他 CSS 动画会合并保留）。想完全自己控制背景动画，别用 sprite，用普通 div + CSS。

## translation：本地化文本

```html
<translation>container.apricityui.title</translation>
```

文本内容就是 MC 翻译键，按当前语言渲染。textContent 是键本身。没有参数插值——带参数的翻译在脚本侧拼好再用普通文本元素。

## svg / path：矢量图形

SVG 子集，4x 超采样栅格化成位图，适合图标和简单装饰：

```html
<svg viewBox="0 0 64 64" width="64" height="64" style="color: #7dd3fc;">
    <circle cx="32" cy="32" r="28" fill="none" stroke="currentColor" stroke-width="4"></circle>
    <path d="M18 34 L28 44 L47 22 Z" fill="currentColor"></path>
</svg>
```

- 子图形：`circle`、`ellipse`、`rect`、`line`、`polyline`、`polygon`、`path`；
- 绘制属性：`fill`/`stroke`（含 `none`）、`stroke-width`、`stroke-linecap/join`、`opacity` 三件套、`fill-rule`（支持 evenodd）、`currentColor`（读元素或祖先的 `color`）；属性可从父级继承；
- path 命令：M/L/H/V/C/S/Q/T/A/Z，大小写都支持；
- `viewBox` 是内部坐标系，元素尺寸用 SVG 属性或 CSS 给；
- **没有** defs/use、渐变、滤镜、遮罩、文本、transform、外部 SVG。复杂图标导出时拍平成纯 path，或换 canvas。

SVG 会缓存栅格结果，属性或子树变化才重画。大尺寸复杂路径的栅格化成本不低，配合 frameTimingHud 观察。

## canvas：脚本绘制

```html
<canvas id="chart" width="320" height="160"></canvas>
```

标准 Canvas 2D 子集（Java2D 后端）。`width`/`height` 属性设位图尺寸，CSS 设显示尺寸，两者不同会缩放——别只改 CSS 就当坐标系变了。完整的 API 支持度和限制见 [Web API 文档](web-api)。

大画布频繁重绘有上传成本。静态矢量用 svg，游戏纹理用 texture——canvas 是这几个里最贵的，只留给真正需要逐帧计算的画面。

## iframe：操作系统 WebView

`<iframe>` 用**系统自带的 WebView**（Windows 上是 WebView2 / Edge Runtime）渲染，不是框架内嵌的浏览器。宿主页运行在一个用户看不见的离屏窗口里，只把像素交回框架当纹理用——所以布局、裁剪、transform、层叠、命中测试全都和 canvas 这类贴图元素一样。

```html
<iframe src="https://example.com/panel" style="width: 480px; height: 320px; border: 0;"></iframe>
<iframe src="file:///C:/pages/tool.html" style="width: 400px; height: 300px;"></iframe>
```

- `src` 必须是**绝对 URL**（`https:`、`file:` 等）。引擎没有文档 base URL，相对路径原样交给浏览器，解析不出结果；
- 只有写了 `src` 属性才会真的启动浏览器实例；没有 `src` 的 `<iframe>` 不占进程，当占位符；

**尺寸规则和浏览器一致**（按 CSS 2.1 的替换元素规则实现）：

```html
<iframe src="..."></iframe>                        <!-- 300x150，浏览器默认对象尺寸 -->
<iframe src="..." width="400" height="200"></iframe><!-- 属性 = presentational hint -->
<iframe src="..." style="width:400px"></iframe>     <!-- 400x150，缺的一边取默认值 -->
<iframe src="..." style="width:100%;height:240px"></iframe> <!-- 想撑满要显式写 -->
```

- UA 默认 `display: inline`（和浏览器一致）。想要块级布局自己写 `display: block`；
- `width`/`height` 属性和浏览器一样是 **presentational hint**：任何作者 CSS 都能覆盖它，没被覆盖时就是尺寸来源。只给一边时**另一边取默认值**（300 或 150），**不存在等比换算**——iframe 没有内在宽高比；
- 块级（或绝对定位）且 `width:auto` 时取**固有宽度，不会撑满父容器**，`inset:0` 也拉不开它。这是替换元素的标准行为，要撑满写 `width:100%`；
- **内部页面的 CSS 视口 = 元素的内容盒**（CSS px），和浏览器里一样：`innerWidth`、`vw/vh`、媒体查询都按内容盒算。纹理分辨率取的是内容盒的**设备像素**（内容盒 × 设备缩放），页面侧 `devicePixelRatio` 等于该缩放，所以是 1:1 实分辨率，非 100% 屏幕缩放下也不糊；
- 纹理上限 4096 px：超过时分辨率被截断，但 zoom 会同步补偿，**页面的 CSS 视口仍然正确**，只是清晰度下降；
- 鼠标移动/按下/滚轮会转发给页面，页面自己有滚轮监听时 AUI 不再滚动父容器；键盘在 iframe 获得焦点后转发给页面，并且**吞掉**这些按键，Minecraft 快捷键不会同时触发；
- `overflow: hidden` 自动生效，超出的部分被裁掉；
- 后端不可用时（非 Windows、或系统没装 WebView2 Runtime）元素什么都不画，CSS 背景和边框照常显示。

```js
const frame = document.getElementById("panel");
frame.getAttribute("src");
frame.setAttribute("src", "https://example.com/other");  // 换页，复用已有的浏览器实例
frame.removeAttribute("src");                            // 关掉浏览器实例，元素退回占位
```

几个必须知道的限制：

- **抓帧瓶颈是编码**。WebView2 只提供无损（PNG）和有损（JPEG）两种截图格式，同一张 900×700 的重页面：PNG 往返约 110ms（≈9fps），JPEG 约 31ms（≈28–33fps）。默认 `capture` 属性是**自动**：页面不动时用无损（内容不变时按压缩字节去重，解码/拷贝/上传全部跳过，代价约 0），一旦持续变化就自动切快速编码。想钉死用 `capture="lossless"` 或 `capture="fast"`；
- 取帧发生在**渲染阶段**（每个渲染帧一次），所以内嵌页面不受 20Hz 逻辑 tick 限制；元素连续 2 秒没被绘制时会暂停抓帧，避免后台页面白烧 CPU；
- `CapturePreview` 这条路本身的上限约 **30fps**（往返最少 15–30ms）。绕开它走"原始像素流"的路子**试过并且当前用不了**：`Windows.Graphics.Capture`（DWM 合成帧推送）能启动、也能拿到帧，但拿到的每一帧都是**单色**——WebView2 的内容挂在 `IDCompositionTarget` 上（`CreateTargetForHwnd`），无论 WGC 还是 `PrintWindow(PW_RENDERFULLCONTENT)` 都看不到这部分内容。代码留在 `native/webview/src/frame_stream.{h,cpp}`，运行时会自动尝试、发现看不到内容就自我禁用并回退到编解码路径，所以不影响使用；哪天这个呈现方式变了它会自动生效。补充两点实测：① 默认 auto **不再**自动尝试它（避免每创建一个视图白抓一帧），要试就显式写 `capture="stream"`；② 30fps 与分辨率**无关**——抓帧面积缩到 1/4（450×350，载荷 40KB vs 149KB）只从 28fps 升到 33fps，所以别指望靠降分辨率提帧率；想省 CPU/带宽用 `capture-scale="0.5"`（CSS 视口仍然正确，画面变软）；
- **没有原生键盘注入 API**，按键是在页面里合成 DOM 事件实现的：`keydown`/`keyup` 会正常派发到页面监听器，但浏览器的**默认动作不会自动发生**，所以要靠框架显式补上（输入框里退格/删除/回车/方向键已处理）。IME 组合输入目前直接以「已上屏文本」的形式送入；
- 相对路径、`srcdoc`、`sandbox` 都还没有；页面里的 `window.parent` / `postMessage` 指向的是浏览器内部，**没有**接到 AUI 上，`contentWindow`/`contentDocument` 也没有暴露；
- 首次创建实例要几百毫秒（在后台线程完成，不卡帧），浏览器数据存在 `游戏目录/apricity/webview` 下并在重启后保留；
- 调试：Java 侧 `Iframe.status()` 返回原生宿主的抓帧/导航计数，`AuiServices.webView().unavailableReason()` 说明后端为什么不可用。

## 常见问题

**texture 不显示**：`src` 写成逻辑路径或文件路径了，必须是 `namespace:path`。要显示页面目录里的图用 img。

**sprite 不动**：steps 和图集实际帧数对不上、direction 和图集排布方向不符、元素没有尺寸。图集没加载完前会先显示静态背景，属正常。

**sprite 的样式被我写的 background 覆盖了 / 反过来**：托管属性只覆盖同名 inline style，样式表里的不受影响。排查时先想到托管清单。

**translation 显示原键**：键不存在或语言文件没这条，和原版行为一致。

**svg 的渐变/分组没画**：不支持。拍平成 path 或换方案。

**刷新后 canvas 内容没了**：刷新重建 DOM，位图跟着清。绘制逻辑放初始化函数，`DOMContentLoaded`/`load` 里重跑。
