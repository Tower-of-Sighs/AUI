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

`<iframe>` 用**系统自带的 WebView**（Windows 上是 WebView2 / Edge Runtime）渲染，不是框架内嵌的浏览器。宿主页运行在一个用户看不见的离屏窗口里，只把**变化的那部分像素**流式交回框架当纹理用——所以布局、裁剪、transform、层叠、命中测试全都和 canvas 这类贴图元素一样。

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

- **单次抓帧有约 20ms 的固定成本**。WebView2 只提供无损（PNG）和有损（JPEG）两种截图格式，`CapturePreview` 每次往返最少 ~21ms（400×300 和 1280×720 都测过：小图 21ms、1280×720 约 28ms；页面静止时也是这个数，所以这部分不是「等新合成帧」，而是浏览器进程里的回读 + 编码 + IPC 成本）。一张 900×700 的重页面 PNG 往返约 110ms（≈9fps），JPEG 约 31ms。默认 `capture` 属性是**自动**：页面不动时用无损（内容不变时按压缩字节去重，解码/传输/上传全部跳过，代价约 0），一旦持续变化就自动切快速编码。想钉死用 `capture="lossless"` 或 `capture="fast"`；
- **抓帧是流水线的**：上面那 ~21ms 大部分是「等」，所以宿主最多同时挂 4 次 `CapturePreview`，一次在途时就开始下一次，吞吐因此约等于单次延迟的 4 倍；回调乱序返回时只发布最新的那一帧（旧帧丢弃，`status()` 里记在 `stale=`），画布因此始终单调向前。实测（800×600、页面 60fps 重绘）：PNG 33→**61fps**、JPEG 39→**61fps**；1280×720 从 30→**60fps**，1600×900 约 **59fps**；
- **解码不在 UI 线程上**。抓帧回调只负责入队，WIC 解码、通道字节交换、瓦片比对和写共享内存都在一条独立的解码线程上（优先级低于普通线程）。这条线程就是 WebView2 的 UI 线程，它每被解码占住 1ms，消息泵就晚 1ms 派发鼠标和滚轮 —— 实测 1200×900 下每帧解码 2~20ms，正是拖动和滚动「黏」的来源。现在 `status()` 里的 `cmd=`（最近一条命令在队列里等了多久）稳定在 **0~1ms**；
- **指针移动会合并**：宿主只保留最新的鼠标位置，每次循环转发一次。Chromium 只需要「现在在哪」，把每个中间采样都跨进程发一遍纯属增加延迟；拖拽时实测一半以上的采样被合并掉（`coalesced=`）。按键、滚轮、移出这些有顺序含义的事件不合并；
- **抓帧率跟随元素的绘制率**：抓帧要把合成结果从 GPU 读回来，而游戏正在用同一块 GPU。实测游戏 60fps 时抓 57 帧/s、30fps 时抓 27 帧/s、20fps 时抓 20 帧/s —— 画不到的速度抓了也显示不出来，只会和游戏抢 GPU，所以请求频率跟着实测帧时间走（下限 50ms，保证页面不会看起来冻住）；
- **在途抓帧深度自适应**：单次抓帧延迟低（<45ms）时最多挂 4 个，延迟高（>60ms）时降到 2 个，避免在 GPU 吃紧时堆一排回读把游戏也拖下去；
- **超大 iframe 会按面积降采样**：抓帧成本随面积线性增长（实测 ≈ 20ms + 28ms/百万像素），所以 raster 面积超过 120 万像素时自动等比缩小（`zoom` 跟着重算，**页面 CSS 视口仍然精确**，只是画面变软），`status()` 会标出 `raster capped by area`。这样全屏级 iframe 不会把一个视图的帧率吃光；想要更小可以再叠 `capture-scale`；
- **传输是增量图像流，不是整帧轮询**。宿主把每次抓到的画面和「渲染器手上那张画布」按 32×32 瓦片比对，只把变化的矩形（合并成尽量少的几块）写进一块共享内存；渲染器按包读，写进纹理的也只有这些矩形区域，**成本跟变化面积成正比，跟画布大小无关**。实测一个 800×600 的页面里只有一个 48×48 的小方块在动：每帧脏区约 **1.5%** 画布，同样的页面用 JPEG 抓帧时平均每次上传约 **4%** 画布（JPEG 噪声会让更多瓦片变脏）；页面完全静止时**一个包都不发**，抓帧本身也被按压缩字节去重挡掉。真·全画布传输只发生在首次、元素尺寸变化、以及读取端主动要求重刷时；
- 取帧/取更新发生在**渲染阶段**（每个渲染帧一次），所以内嵌页面不受 20Hz 逻辑 tick 限制；元素连续 2 秒没被绘制时会暂停抓帧，避免后台页面白烧 CPU；
- 分辨率不是帧率的开关：`capture-scale="0.5"` 能让单次抓帧便宜一点（800×600 → 400×300 省约 5ms），但流水线已经把这点延迟藏住了，所以它现在的定位是**省 CPU / 省带宽**（CSS 视口仍然正确，画面变软），不是提帧率；
- 绕开 `CapturePreview` 走"原始像素流"的路子**试过并且当前用不了**：`Windows.Graphics.Capture`（DWM 合成帧推送）能启动、也能拿到帧，但拿到的每一帧都是**单色**——WebView2 的内容挂在 `IDCompositionTarget` 上（`CreateTargetForHwnd`），无论 WGC 还是 `PrintWindow(PW_RENDERFULLCONTENT)` 都看不到这部分内容。代码留在 `native/webview/src/frame_stream.{h,cpp}`，运行时会自动尝试、发现看不到内容就自我禁用并回退到编解码路径，所以不影响使用；哪天这个呈现方式变了它会自动生效。补充：默认 auto **不再**自动尝试它（避免每创建一个视图白抓一帧），要试就显式写 `capture="stream"`；
- **没有原生键盘注入 API**，按键是在页面里合成 DOM 事件实现的：`keydown`/`keyup` 会正常派发到页面监听器，但浏览器的**默认动作不会自动发生**，所以要靠框架显式补上（输入框里退格/删除/回车/方向键已处理）。IME 组合输入目前直接以「已上屏文本」的形式送入；
- **弹窗被接管**：页面里 `window.open` / `target="_blank"` 以前会真的开一个弹窗（就出现在桌面上，来自一个你看不见也动不了的浏览器实例）。现在这类请求被当前视图接管并原地导航 —— 点链接就是在这个 iframe 里跳转；
- **关闭页面会销毁 webview**：删除元素时框架一直会通知元素（`onDisconnectedFromDocument`），但**关闭整个文档**这条路漏了，于是 iframe 的离屏浏览器、宿主线程、解码线程和共享内存段全都留着。现在 `Document.disposeLifecycle()` 会通知全部元素，`Iframe` 另外加了一道"文档已销毁"的防御；
- **拖拽语义补全**：鼠标移动事件以前不带"哪个键按着"（Win32 的 `WM_MOUSEMOVE` 必须在 wParam 里带 `MK_LBUTTON`），Chromium 因此把拖拽判成悬停 —— 网页里的滚动条拖不动、拖选也断。现在按下状态会随移动事件一起发；拖拽期间指针移出内容盒仍继续转发（坐标夹到盒内），松开也一定送达，`mouseLeave` 在按住期间不发；
- **键盘**：输入要落到页面里，得让**网页自己的可编辑元素拿到 DOM 焦点**（先在网页里点一下输入框），这一点和真浏览器一致。如果点了还是打不进字，看 `Iframe.status()` 里的 `focus=`：`no` 表示这个 iframe 不是文档的焦点元素（AUI 就不会把字符转给它），`yes` 但还打不进，就是网页里没有元素持有焦点；
- 相对路径、`srcdoc`、`sandbox` 都还没有；页面里的 `window.parent` / `postMessage` 指向的是浏览器内部，**没有**接到 AUI 上，`contentWindow`/`contentDocument` 也没有暴露；
- 首次创建实例要几百毫秒（在后台线程完成，不卡帧），浏览器数据存在 `游戏目录/apricity/webview` 下并在重启后保留；
- 调试：打开 DevTools（F12）里的 **frame timing HUD**，末尾会多出一段 `stream=WxH packets=… rects=… payload=…KB` 以及宿主那行的 `fps/period/roundTrip/cmd/raster`，可以在游戏里直接看抓帧率和输入排队；Java 侧 `Iframe.status()` 返回原生宿主的抓帧/导航计数，再接上增量流的收发统计（画布尺寸、包数、矩形数、载荷字节、重同步次数），`AuiServices.webView().unavailableReason()` 说明后端为什么不可用。判断卡顿先看这几个数：`fps=` 抓帧率、`period=` 相邻抓帧间隔（远大于 `roundTrip=` 说明是调度问题，接近则说明抓帧本身到顶）、`cmd=` 输入排队时长（大就是 UI 线程被占）、`decode=` 解码耗时、`stale=`/`dropped=` 丢帧。

## 常见问题

**texture 不显示**：`src` 写成逻辑路径或文件路径了，必须是 `namespace:path`。要显示页面目录里的图用 img。

**sprite 不动**：steps 和图集实际帧数对不上、direction 和图集排布方向不符、元素没有尺寸。图集没加载完前会先显示静态背景，属正常。

**sprite 的样式被我写的 background 覆盖了 / 反过来**：托管属性只覆盖同名 inline style，样式表里的不受影响。排查时先想到托管清单。

**translation 显示原键**：键不存在或语言文件没这条，和原版行为一致。

**svg 的渐变/分组没画**：不支持。拍平成 path 或换方案。

**刷新后 canvas 内容没了**：刷新重建 DOM，位图跟着清。绘制逻辑放初始化函数，`DOMContentLoaded`/`load` 里重跑。
